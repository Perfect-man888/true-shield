# 真信盾系统架构

## 运行结构

```text
Android Compose
  -> ViewModel / StateFlow
  -> Repository
  -> Retrofit + Bearer Token
  -> FastAPI Route
  -> Domain Service / Analyzer
  -> SQLAlchemy Repository
  -> PostgreSQL
```

Redis、MinIO 已包含在本地基础设施中，但当前核心业务主要依赖 PostgreSQL；不可把规划中的缓存和对象存储能力误认为已经在所有链路启用。

## 后端模块

- `app/api/routes`：HTTP 协议、鉴权依赖和响应映射。
- `app/services`：风险分析、OCR、ASR、报告、通知和家庭告警编排。
- `app/repositories`：查询、持久化和所有权约束。
- `app/models`：SQLAlchemy 数据模型。
- `app/schemas`：输入输出协议与校验。
- `app/rules`：版本化的可解释规则。

## 风险分析

文本规则引擎支持关键词、组合条件、正则、上下文、排除条件和否定语义抑制。图片和语音先转换为文本，URL 和号码使用各自的信号分析器。启用 AI 时，语义结果与规则结果按置信度和安全边界融合；规则证据仍保留以支持解释和审计。

## 家庭告警

风险事件可以手动或按家庭策略创建告警。告警服务选择符合条件的可信联系人，通过配置的通知提供方发送，并记录每一次投递状态、失败原因、重试次数与冷却时间。

## 通话护航一期

服务端从维护规则生成带版本、更新时间、有效期、SHA-256 摘要、来源和置信度的规则包。Android 登录后同步并缓存规则，系统来电回调只读取未过期的本地缓存，绝不等待网络请求，因此能够满足 `CallScreeningService` 的五秒响应窗口。

默认策略只提醒。用户可以主动开启高风险静音，或开启“拦截已确认风险号码”；后者只匹配服务端标记为已复核的精确号码规则，不会根据陌生号码、号段规则或待审核举报自动拦截。举报只保存号码不可逆指纹和脱敏展示值，进入待复核队列。

## 边界

当前后端是模块化单体。`services/ai_gateway`、`media_worker` 和 `notification_worker` 仅为未来拆分预留；在有明确吞吐量和隔离需求前，不应复制现有业务到这些目录。
