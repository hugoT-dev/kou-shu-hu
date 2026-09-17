# 口述护 H5（Sprint 1 · Demo）

第一步只做 **Demo 联调页**，用来测主流程，不是完整产品界面。可点击原型已移到 `docs/prototype/`。

## 覆盖流程

识别（麦克风 / WAV / 样本文本）→ 生成护理记录 → 确认与影像留痕 → 交班清单 → 接班确认 → 院长查询。

页面上的「一键跑通」会用样本文本连真实接口跑完上述路径，不依赖麦克风。

## 访问方式

- **整栈 Docker**：仓库根目录 `docker compose up -d --build`，打开 http://127.0.0.1:8181/
- **本机前端联调**：后端网关已在 8080 时，`cd frontend && npm install && npm run dev`，打开 http://127.0.0.1:5181/
- **本机 Java 托管静态页**：先 `npm run build`，护理服务代理 `frontend/dist`，打开 http://localhost:8080/
