# 口述护后端（Spring Cloud + Nacos）

Java 基础设施只有网关：`yl-gateway`（Spring Cloud Gateway + Nacos 发现客户端）。注册中心使用独立 **Nacos** 进程（Docker 或本机安装），不再提供 `yl-registry` / Eureka。

整栈（含 H5）请用仓库根目录：`docker compose up -d --build`。本目录 `docker-compose.yml` 只起 Nacos，给本机 Maven / IDE 联调用。

## 模块

| 模块 | 端口 | 职责 |
|------|------|------|
| Nacos（非 Java 模块） | 8848 | 服务发现（可选配置中心） |
| yl-gateway | 8080 | 网关，`lb://` 路由到 Nacos 中的服务 |
| yl-asr | 8081 | 流式 ASR（默认 mock；可切 FunASR / DashScope） |
| yl-nursing | 8082 | 护理记录、原声/影像、交接、H5 代理 |
| yl-common | — | DTO 与规则解析 |

## 整栈 Docker

在仓库根目录（不要在本目录）：

```text
docker compose up -d --build
```

容器内通过 `NACOS_ADDR=nacos:8848` 与 `NACOS_DISCOVERY_IP=<服务名>` 注册到同一 Docker 网络。H2 与媒体文件挂在 `nursing-data` 卷的 `/data`。

## 本地启动（仅 Nacos 容器 + 本机 Java）

1. 启动 Nacos：

```text
cd backend
docker compose up -d
```

控制台：http://127.0.0.1:8848/nacos （默认 `nacos` / `nacos`）。无 Docker 时可自行安装 Nacos 2.x 单机，地址改为 `NACOS_ADDR`。

2. 启动业务与网关（顺序：asr、nursing、gateway）：

```text
mvn -pl yl-asr,yl-nursing,yl-gateway -am spring-boot:run
```

或分别在 IDE 中运行三个 `*Application`。地址默认 `127.0.0.1:8848`，可用环境变量 `NACOS_ADDR` 覆盖。

3. 前端：`cd ../frontend && npm install && npm run dev`，打开 http://127.0.0.1:5181/ 。若要让网关托管静态页，先 `npm run build`，再打开 http://localhost:8080/ 。

FunASR 与 Java 同机时：`YL_ASR_PROVIDER=funasr FUNASR_WS_URL=ws://127.0.0.1:10095`。镜像在 `backend/funasr`。

单元测试关闭发现：`spring.cloud.nacos.discovery.enabled=false`。
