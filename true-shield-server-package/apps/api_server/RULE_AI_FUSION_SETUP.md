# 真信盾规则库与本地 AI 融合配置

## 本阶段能力

- 文本规则库升级到 `2.0.0`，新增刷单返利、培训退费、虚假投资、贷款前置费、账号接管、远程控制、社保医保、快递理赔等规则。
- 规则支持关键词组合、正则表达式、排除词与最低命中数量。
- 图片 OCR、语音转写和人工文本统一经过同一套融合分析器。
- URL 规则升级到 `url-2.0.0`，增加安装包、嵌套跳转、高比例编码和混合字符域名检查。
- 可选使用本机 Ollama 模型做语义复核，识别同义改写和隐晦诈骗链条。
- AI 不会降低规则引擎已经判定的风险；AI 不可用时自动回退到规则库。
- 分析结果会记录 `analysis_mode`、引擎版本、AI 模型与置信度。

## 一、只启用增强规则库

默认配置中：

```env
RISK_AI_ENABLED=false
```

此时无需安装任何模型，后端直接使用增强规则库。

## 二、开启本地 AI 语义复核

### 1. 安装 Ollama

Windows 使用 Ollama 官方安装程序安装。安装后在 PowerShell 验证：

```powershell
ollama --version
```

### 2. 下载本地中文模型

```powershell
ollama pull qwen3:1.7b
```

确认模型存在：

```powershell
ollama list
```

### 3. 配置后端

将项目根目录 `RISK_AI_ENV_APPEND.txt` 中的配置追加到：

```text
apps/api_server/.env
```

默认只允许访问 `127.0.0.1` 或 `localhost` 上的模型服务，避免分析文本被发送到远程服务器。

### 4. 检查引擎

启动后端后登录 Swagger，调用：

```http
GET /api/v1/risk/engine/status
```

正常开启时应看到：

```json
{
  "analysis_mode": "rules_ai",
  "rule_version": "2.0.0",
  "ai_enabled": true,
  "ai_model": "qwen3:1.7b",
  "ai_available": true,
  "privacy_mode": "local_only"
}
```

也可以运行：

```powershell
uv run python scripts/check_risk_ai.py
```

## 三、测试

```powershell
uv run pytest tests/test_risk_analyzer.py tests/test_hybrid_risk_analyzer.py tests/test_url_risk_analyzer.py -q
```

Android 编译：

```powershell
cd ..\android_app
.\gradlew.bat :app:compileDebugKotlin
```

## 四、隐私与降级策略

- 规则库始终在后端本地执行。
- 默认拒绝远程 Ollama 地址。
- 模型调用失败、超时或返回格式错误时，自动使用规则结果。
- AI 只做二次复核，不允许降低规则引擎已确认的风险等级。
- 原有数据库字段没有变化，本阶段不需要 Alembic 迁移。
