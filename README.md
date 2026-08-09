# 真信盾 True Shield

真信盾是面向家庭场景的反诈风险识别与可信联系系统。Android 客户端可分析文本、聊天截图、录音、网址和电话号码，并通过家庭组、可信联系人和告警策略协助用户暂停高风险操作、独立核验信息。

> 系统只提供风险提示与核验建议，不承诺百分之百识别诈骗，也不代替公安、银行或平台作出权威结论。

## 已实现能力

- 注册、登录、JWT 鉴权、密码重置和账户安全
- 文本规则分析及可选的本地 AI 语义复核
- 图片 OCR、语音转写、URL 重定向及号码风险分析
- 风险事件、证据、历史、详情、仪表盘和 PDF 报告
- 用户反馈、OCR 修正和重新分析
- 家庭组、成员邀请、可信联系人和通话求助
- 家庭告警、自动触发策略、邮件/FCM 通知与失败重试
- 版本化号码规则同步、离线来电筛查、号码举报与可选静音/拦截

## 技术架构

- Android：Kotlin、Jetpack Compose、Navigation、ViewModel、StateFlow、Retrofit
- API：Python 3.12、FastAPI、Pydantic、异步 SQLAlchemy、Alembic
- 分析：YAML 可解释规则、RapidOCR、FunASR、可选 Ollama 语义模型
- 基础设施：PostgreSQL、Redis、MinIO、Docker Compose
- 测试：pytest、pytest-asyncio、JUnit

当前是模块化单体：主要业务位于 `apps/api_server`，`services` 和 `packages` 是未来拆分的预留目录，不代表已经部署为微服务。

## 本地启动

### 1. 配置

```powershell
Copy-Item .env.example .env
```

至少替换数据库密码、MinIO 密钥和 `JWT_SECRET_KEY`。真实 `.env`、Firebase 配置和服务账号文件不得提交。

### 2. 启动基础设施

```powershell
docker compose up -d postgres redis minio
```

### 3. 启动 API

```powershell
Set-Location apps/api_server
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

API 文档位于 `http://localhost:8000/docs`，健康检查为 `GET /api/v1/health`。

也可在配置完成后执行 `docker compose up --build`，同时启动 API 和基础设施。

### 4. 启动 Android

用 Android Studio 打开 `apps/android_app`。开发机或真机必须能够访问 API；服务地址目前由 `ApiClient.kt` 的开发配置决定。Firebase 推送需要本地提供 `app/google-services.json`。

## 质量检查

```powershell
Set-Location apps/api_server
uv run ruff check app tests
uv run pytest -m unit
uv run pytest -m integration
uv run pytest -m slow
```

```powershell
Set-Location apps/android_app
./gradlew testDebugUnitTest compileDebugKotlin
```

pytest 测试被分为 `unit`、`integration` 和 `slow`，便于快速反馈与完整回归。

## 文档

- [范围与状态](docs/00_v0.1_scope.md)
- [系统架构](docs/01_architecture.md)
- [数据库设计](docs/02_database.md)
- [API 设计](docs/03_api.md)
- [安全设计](docs/04_security.md)
- [FCM 配置](FCM_SETUP_GUIDE.md)
- [语音转写配置](VOICE_LOCAL_TRANSCRIPTION_SETUP.md)
