# 口述护 H5（Sprint 1）

源码只放本目录，不作为 Java `resources/static` 的长期归属。

## 访问方式

- **整栈 Docker**：根目录 `docker compose up -d --build` 后，nginx 提供本目录静态页，`/api/`、`/ws/` 反代到网关。打开 http://127.0.0.1:8181/ 。FunASR 演示：http://127.0.0.1:8181/asr-demo.html 。
- **本机联调**：护理服务通过 `yl.frontend.dir` 代理本目录，网关再把 `/`、`/*.html`、`/css/**`、`/js/**` 转到护理服务。打开 http://localhost:8080/caregiver.html 。

- 护理员：`caregiver.html`（流式出字、原声、影像、交接）
- 院长：`admin.html`
- 站点图：`start.html`
