# 39 服务器 Docker 部署指南

目标机：`39.107.60.241`（阿里云 ECS）。在 **MobaXterm 已登录的终端**里操作即可。

FunASR 只绑本机回环，**不要**把 `10095`、Nacos `8848` 开到公网。浏览器只访问 H5 / 网关。

## 0. 端口与安全组

阿里云控制台 → 该实例安全组，入方向只放行：

| 端口 | 用途 | 是否对公网 |
|------|------|------------|
| 22 | SSH（MobaXterm） | 是（建议限定你的办公 IP） |
| 8181 | H5（nginx） | 是 |
| 8080 | 网关（可选，调试用） | 建议仅调试时开 |
| 10095 | FunASR WebSocket | **否**（compose 已绑 `127.0.0.1`） |
| 8848 / 9848 | Nacos | **否** |

H5 对外端口是 **8181**（容器内仍是 nginx 80）。安全组放行 8181，不要用 80。

## 1. 服务器准备

```bash
# 系统与 Docker
cat /etc/os-release
docker --version
docker compose version
```

若没有 Docker（以 Alibaba Cloud Linux / CentOS 为例）：

```bash
curl -fsSL https://get.docker.com | bash
systemctl enable --now docker
docker compose version
# 若提示没有 compose 插件：
# yum install -y docker-compose-plugin   # 或 apt install docker-compose-plugin
```

资源建议：CPU ≥ 4 核，内存 **≥ 8GB**（FunASR CPU 双模型 + Java 栈），磁盘 **≥ 40GB** 空闲（PyTorch CPU 镜像 + 模型缓存）。

```bash
free -h
df -h
```

## 2. 把代码放到服务器

在 **本机** 用 MobaXterm 左侧 SFTP 把整个 `yl-care` 拖到服务器，例如：

```text
/opt/yl-care
```

或在服务器上 git clone（若已有远程仓库）。不要把含密码的文件、`.env` 提交进 git。

```bash
sudo mkdir -p /opt/yl-care
# 上传完成后：
cd /opt/yl-care
ls docker-compose.yml backend/funasr frontend
```

Windows 拖文件时注意：不要只传一部分；必须带上 `backend/`、`frontend/`、根目录 `docker-compose.yml`。

## 3. 环境变量（可选）

```bash
cd /opt/yl-care
cat > .env << 'EOF'
YL_ASR_PROVIDER=funasr
FUNASR_WS_URL=ws://funasr:10095
# 可选：给 FunASR WebSocket 加口令，与 Java 侧一致
# FUNASR_TOKEN=请换成随机长串
EOF
chmod 600 .env
```

同机 Docker 网络内，`yl-asr` 连 `ws://funasr:10095`，**不要**写成 `ws://39.107.60.241:10095`。

## 4. 先只起 FunASR（推荐）

首次构建会拉 PyTorch CPU + `paraformer-zh-streaming` + `paraformer-zh`，**可能 20～60 分钟**，不要中断。

```bash
cd /opt/yl-care

# 只构建并启动 FunASR
docker compose up -d --build funasr

# 看下载/加载模型（出现 models ready 即成功）
docker logs -f yl-funasr
```

另开一个 SSH 窗口检查：

```bash
docker ps | grep yl-funasr
ss -lntp | grep 10095
# 应看到 127.0.0.1:10095，而不是 0.0.0.0:10095 对公网
```

只测 FunASR、暂不起 Java 时，也可以进目录单独起（与整栈 **不要同时** 起两份，容器名都是 `yl-funasr`）：

```bash
cd /opt/yl-care/backend/funasr
docker compose up -d --build
```

整栈部署请用仓库**根目录**的 `docker-compose.yml`，不要两个 compose 一起跑。

## 5. 再起整栈

FunASR 日志出现 `models ready` 之后：

```bash
cd /opt/yl-care
docker compose up -d --build
docker compose ps
```

预期容器：`yl-nacos`、`yl-funasr`、`yl-asr`、`yl-nursing`、`yl-gateway`、`yl-frontend`。

Nacos 第一次 `healthy` 大约 1 分钟；Java 镜像首次 Maven 构建可能再要几分钟。

## 6. 验证

在**服务器本机**：

```bash
# FunASR 端口（仅本机）
python3 -c "import socket;s=socket.create_connection(('127.0.0.1',10095),3);s.close();print('funasr ok')"

# 网关 / ASR 健康
curl -sS http://127.0.0.1:8080/api/asr/health
# 期望含 "provider":"funasr"、"pcm":true、"model":"paraformer-zh-streaming"

curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8181/
```

在**你的电脑浏览器**（安全组已放行 8181）：

| 入口 | 地址 |
|------|------|
| H5 Demo | http://39.107.60.241:8181/ |
| 网关直连（若开了 8080） | http://39.107.60.241:8080/ |

Nacos 控制台不要映射公网。若要看：在 MobaXterm 建 SSH 隧道 `8848` → `127.0.0.1:8848`，再打开 http://127.0.0.1:8848/nacos （`nacos` / `nacos`）。

## 7. 日常命令

```bash
cd /opt/yl-care
docker compose ps
docker compose logs -f --tail=100 yl-funasr yl-asr yl-gateway
docker compose restart yl-asr
docker compose down          # 停栈（数据卷还在）
docker compose up -d --build # 改代码后重建
```

数据卷：`nacos-data`、`nursing-data`（H2 + 原声/影像）、`funasr-models`（模型缓存，删了要重新下）。

## 8. 常见问题

**FunASR 一直 unhealthy / 日志还在下模型**  
`start_period` 最长 10 分钟。首次下模型更久是正常的，看 `docker logs yl-funasr`，不要反复 `compose down`。

**内存不够被 OOM Kill**  
`dmesg | tail` 或 `docker inspect yl-funasr --format '{{.State.OOMKilled}}'`。加内存或先停其它进程。

**H5 能开但说话没字**  
`curl http://127.0.0.1:8080/api/asr/health` 是否 `pcm: true`。浏览器需 HTTPS 或 localhost 才稳定给麦克风；HTTP 公网 IP 在部分浏览器会拦 `getUserMedia`。可用网关页调试，或后续上 HTTPS。

**构建拉取 Docker Hub 超时**  
配置镜像加速（阿里云容器镜像服务控制台会给地址），写入 `/etc/docker/daemon.json` 后 `systemctl restart docker`。

**不要**让本机或公网去连 `ws://39.107.60.241:10095`。识别链路是：浏览器 → 8181/8080 → `yl-asr` → Docker 网络内 `funasr:10095`。
