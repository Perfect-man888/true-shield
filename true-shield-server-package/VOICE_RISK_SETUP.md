# 真信盾语音风险检测补丁

## 第一版实现方式

Android 调用系统语音识别，将讲话转换为文字；用户可在提交前人工核对和修改。
后端接收转写文本，执行已有反诈规则分析，并以 `source_type=voice` 保存到统一风险历史。

第一版不会将原始录音上传或长期保存，减少隐私和存储风险。
如果设备没有可用的系统语音识别服务，仍可手动输入转写文字完成检测。

## 覆盖位置

将本补丁中的 `apps` 复制到项目根目录 `D:\Projects\true-shield`，选择替换同名文件。

## 后端测试

```powershell
cd D:\Projects\true-shield\apps\api_server
uv run pytest tests/test_risk_voice_api.py tests/test_risk_dashboard_api.py -q
uv run uvicorn app.main:app --reload
```

本阶段没有修改数据库结构，不需要执行 Alembic。

## Android 编译

```powershell
cd D:\Projects\true-shield\apps\android_app
.\gradlew.bat :app:compileDebugKotlin
```

## 验证流程

首页 → 语音风险检测 → 开始语音输入 → 允许麦克风权限 → 说出内容 → 核对转写文字 → 开始语音风险检测。

检测成功后：
- 可进入风险事件详情；
- 历史记录中显示“语音风险检测”；
- 风险统计仪表盘中增加“语音检测”数量；
- 高风险事件仍可触发家庭自动告警策略。
