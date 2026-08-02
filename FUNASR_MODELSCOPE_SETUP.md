# 真信盾：FunASR + ModelScope 本地语音转写

本补丁把原来的 faster-whisper/Hugging Face 下载链路替换为：

Android 录音 → 后端上传 → 国内 ModelScope 下载的 SenseVoiceSmall → 风险分析。

## 默认模型

- 主模型：`iic/SenseVoiceSmall`
- VAD：`iic/speech_fsmn_vad_zh-cn-16k-common-pytorch`
- 推理设备：CPU
- 模型缓存：项目根目录 `models/modelscope`

## 环境变量

```env
VOICE_TRANSCRIPTION_ENABLED=true
VOICE_FUNASR_MODEL=iic/SenseVoiceSmall
VOICE_FUNASR_VAD_MODEL=iic/speech_fsmn_vad_zh-cn-16k-common-pytorch
VOICE_FUNASR_HUB=ms
VOICE_FUNASR_DEVICE=cpu
VOICE_FUNASR_CACHE_ROOT=models/modelscope
VOICE_FUNASR_CPU_THREADS=4
VOICE_FUNASR_BATCH_SIZE_SECONDS=60
VOICE_AUDIO_MAX_BYTES=20971520
VOICE_AUDIO_MAX_DURATION_SECONDS=180
```

## 安装与下载

```powershell
cd D:\Projects\true-shield\apps\api_server
uv sync
uv run python scripts/preload_voice_model.py
```

ModelScope 下载器会直接显示每个文件的下载进度。完成标志：

```text
FunASR 本地语音模型已准备完成。
```

## 清理旧 Whisper 缓存

确认 FunASR 正常后，可删除此前未完成的缓存：

```powershell
Remove-Item "D:\Projects\true-shield\models\faster-whisper" -Recurse -Force
```

## 启动与测试

```powershell
uv run pytest tests/test_risk_voice_api.py tests/test_risk_dashboard_api.py -q
uv run uvicorn app.main:app --reload
```

Android 不需要重新修改录音页面，只需要重新编译安装即可。
