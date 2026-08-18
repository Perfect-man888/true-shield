"""提前从国内 ModelScope 下载并加载真信盾 FunASR 模型。

运行：uv run python scripts/preload_voice_model.py
"""

import sys
from pathlib import Path

API_SERVER_ROOT = Path(__file__).resolve().parents[1]
if str(API_SERVER_ROOT) not in sys.path:
    sys.path.insert(0, str(API_SERVER_ROOT))

from app.core.config import PROJECT_ROOT, settings
from app.services.voice_transcription import voice_transcription_service


if __name__ == "__main__":
    cache_root = Path(settings.voice_funasr_cache_root)
    if not cache_root.is_absolute():
        cache_root = PROJECT_ROOT / cache_root

    print("正在从国内 ModelScope 准备本地语音模型：")
    print(f"  主模型：{settings.voice_funasr_model}")
    print(f"  VAD 模型：{settings.voice_funasr_vad_model}")
    print(f"  推理设备：{settings.voice_funasr_device}")
    print(f"  缓存目录：{cache_root}")
    print("下载过程中会显示每个模型文件的进度，请保持窗口开启。")

    voice_transcription_service.warm_up()
    print("FunASR 本地语音模型已准备完成。")
