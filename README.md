# 真信盾 True Shield

家庭级 AI 反诈与可信联系系统。

## 项目目标

真信盾用于在用户付款、提供验证码或泄露敏感信息之前，分析可疑聊天内容并提供可解释的风险提示、可信核验建议和家庭协助能力。

系统不承诺百分之百判断诈骗，也不代替公安、银行或平台作出权威结论。

## 当前版本

当前开发版本：V0.1

当前阶段重点：

- 用户注册与登录
- 文本风险分析
- 规则引擎
- 风险解释
- 事件记录
- 历史事件查询

## 技术栈

### 用户端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus

### 后端

- Python
- FastAPI
- Pydantic
- SQLAlchemy
- Alembic

### 基础设施

- PostgreSQL
- Redis
- MinIO
- Docker Compose

### 测试

- pytest
- pytest-asyncio
- FastAPI TestClient

## 项目结构

```text
true-shield/
├─ apps/
│  ├─ user_web/
│  ├─ admin_web/
│  └─ api_server/
├─ services/
│  ├─ ai_gateway/
│  ├─ media_worker/
│  └─ notification_worker/
├─ packages/
│  ├─ contracts/
│  ├─ risk_rules/
│  └─ shared_utils/
├─ infrastructure/
├─ tests/
├─ docs/
├─ .env.example
├─ docker-compose.yml
└─ README.md
```
