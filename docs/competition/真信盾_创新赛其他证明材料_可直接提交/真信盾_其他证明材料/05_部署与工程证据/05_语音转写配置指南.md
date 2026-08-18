# 真信盾本地语音转写配置

本阶段将原来的 Android/Google 语音识别改为：

1. Android 使用 `MediaRecorder` 录制 `m4a` 音频；
2. 通过 Multipart 上传到真信盾 FastAPI 后端；
3. 后端使用 `faster-whisper` 在本机转写；
4. 转写文本进入现有风险规则引擎；
5. 请求结束后立即删除服务端临时音频，不长期保存原录音。

## 一、安装依赖

```powershell
cd D:\Projects\true-shield\apps\api_server
uv sync
```

## 二、配置 `.env`

在项目根目录 `.env` 末尾加入：

```env
VOICE_TRANSCRIPTION_ENABLED=true
VOICE_WHISPER_MODEL=small
VOICE_WHISPER_DEVICE=cpu
VOICE_WHISPER_COMPUTE_TYPE=int8
VOICE_WHISPER_DOWNLOAD_ROOT=models/faster-whisper
VOICE_WHISPER_CPU_THREADS=0
VOICE_WHISPER_BEAM_SIZE=5
VOICE_AUDIO_MAX_BYTES=20971520
VOICE_AUDIO_MAX_DURATION_SECONDS=180
```

配置说明：

- `small`：中文识别效果和开发机资源占用较均衡；
- 内存较小或只想先联调时，可以改为 `base`；
- 有兼容的 NVIDIA CUDA 环境时，可以将设备改为 `cuda`，并按实际环境设置计算类型；
- 模型会保存到项目根目录的 `models/faster-whisper`，该目录已加入 `.gitignore`。

## 三、提前下载模型

模型首次下载需要访问模型仓库。建议在启动 API 前执行：

```powershell
cd D:\Projects\true-shield\apps\api_server
uv run python scripts/preload_voice_model.py
```

看到：

```text
本地语音模型已准备完成。
```

之后模型文件保存在本地，日常语音检测不再依赖 Google 语音服务。

## 四、测试并启动后端

```powershell
uv run pytest tests/test_risk_voice_api.py tests/test_risk_dashboard_api.py -q
uv run uvicorn app.main:app --reload
```

## 五、编译 Android

```powershell
cd D:\Projects\true-shield\apps\android_app
.\gradlew.bat :app:compileDebugKotlin
```

## 六、验证

```text
首页
→ 语音风险检测
→ 开始录音
→ 说出需要检测的内容
→ 停止录音
→ 上传、转写并检测风险
```

转写完成后可以直接修正错字，再点击“使用修正后的文字重新检测”。

## 常见问题

### 模型下载很慢

先将 `VOICE_WHISPER_MODEL` 改为 `base` 完成功能联调，后续再切换到 `small`。

### 返回 503

检查是否已经执行 `uv sync` 和 `preload_voice_model.py`，并确认 `.env` 中模型配置正确。

### 返回 422

录音可能过短、声音太小或只有静音。靠近麦克风，至少录制 2～3 秒后重试。

### 首次检测较慢

首次模型加载会比后续请求慢。提前运行预加载脚本可避免用户第一次请求等待模型下载。
