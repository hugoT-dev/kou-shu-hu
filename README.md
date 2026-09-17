# yl-care（口述护）

养老院口述护理记录：说话 → 结构化记录 + 交接清单。流式 ASR（FunASR paraformer-zh-streaming 自建 / DashScope 云端），原声与影像留痕。

- `docs/` 需求、选型与可点击原型
- `backend/` Spring Cloud（网关 + Nacos 发现，无独立 Eureka）
- `frontend/` H5（Sprint 1 先做 Demo 联调页；后续小程序）

## 整栈 Docker 部署

仓库根目录一键启动 Nacos、网关、ASR、护理服务与 H5：

```text
docker compose up -d --build
```

| 入口 | 地址 |
|------|------|
| H5 Demo | http://127.0.0.1:8181/ |
| 网关 | http://127.0.0.1:8080/ |
| Nacos 控制台 | 仅本机 http://127.0.0.1:8848/nacos（compose 未对公网暴露） |

39 服务器手动部署见 `docs/39服务器Docker部署指南.md`。FunASR 单独目录见 `backend/funasr`。本机 Java 联调见 `backend/README.md`。可点击原型见 `docs/prototype/`。
