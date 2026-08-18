# 真信盾数据库设计

数据库由异步 SQLAlchemy 模型和 Alembic 迁移共同定义。修改模型时必须同时生成、审查并测试迁移，不能依赖运行时自动建表。

## 核心实体

- `users`：账户、联系方式、状态和认证版本。
- `password_reset_codes`：限时、一次性的密码重置验证码摘要。
- `risk_events`：用户风险分析事件、来源、分数、等级和分析元数据。
- `risk_signals`：事件的规则或分析信号、标签及文本位置。
- `risk_feedbacks`：用户反馈、OCR 修正与重新分析关联。
- `families`、`family_members`、`family_invitations`：家庭关系与邀请状态。
- `trusted_contacts`：家庭成员维护的可信联系人及通知偏好。
- `family_alerts`、`family_alert_recipients`：家庭告警及接收状态。
- `family_alert_delivery_attempts`：每次通知投递的渠道、结果与失败信息。
- `family_alert_policies`：自动创建和分发告警的家庭策略。
- `push_devices`：用户设备安装标识、FCM Token 与撤销状态。
- `call_guard_reports`：用户号码举报或误报反馈，仅保存号码 HMAC 指纹、脱敏值、类型、备注和复核状态。

## 一致性原则

1. 用户资源查询必须包含 `user_id` 所有权约束。
2. 家庭资源操作前必须验证有效成员身份和角色。
3. 告警状态更新与投递记录写入应在明确事务边界内完成。
4. 删除业务关系优先使用状态或撤销字段，保留必要审计链路。
5. 测试使用独立 SQLite 内存数据库；生产以 PostgreSQL 行为为准，迁移仍需在 PostgreSQL 验证。

## 迁移命令

```powershell
Set-Location apps/api_server
uv run alembic upgrade head
uv run alembic current
uv run alembic history
```
