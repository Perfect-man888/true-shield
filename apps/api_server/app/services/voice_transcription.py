from __future__ import annotations

import os
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.core.config import PROJECT_ROOT, settings


class VoiceTranscriptionError(RuntimeError):
    """语音转写基础异常。"""


class VoiceTranscriptionUnavailableError(VoiceTranscriptionError):
    """本地语音模型未安装、未启用或无法加载。"""


class VoiceNoSpeechError(VoiceTranscriptionError):
    """音频中没有识别到有效讲话。"""


class VoiceAudioTooLongError(VoiceTranscriptionError):
    """音频时长超过服务端允许上限。"""


@dataclass(frozen=True, slots=True)
class VoiceTranscriptionResult:
    """本地语音模型的规范化转写结果。"""

    transcript: str
    language: str
    language_probability: float | None
    duration_seconds: float | None
    provider: str
    model_name: str
    segment_count: int
    recognition_confidence: float | None


_LANGUAGE_TAG_PATTERN = re.compile(r"<\|(?P<language>zh|en|yue|ja|ko|nospeech)\|>")


def _normalize_language(language: str | None) -> str:
    """把 Android/BCP-47 语言代码转换为 FunASR 语言代码。"""

    if language is None:
        return "auto"

    cleaned = language.strip().lower().replace("_", "-")
    if not cleaned or cleaned in {"auto", "automatic"}:
        return "auto"

    short_code = cleaned.split("-", maxsplit=1)[0]
    supported = {
        "zh": "zh",
        "en": "en",
        "yue": "yue",
        "ja": "ja",
        "ko": "ko",
    }
    return supported.get(short_code, "auto")


def _resolve_cache_root(raw_path: str) -> Path:
    path = Path(raw_path).expanduser()
    if not path.is_absolute():
        path = PROJECT_ROOT / path
    path.mkdir(parents=True, exist_ok=True)
    return path


def _resolve_model_path(model_or_path: str, cache_root: Path) -> str:
    """从 ModelScope 下载模型，或直接使用已存在的本地模型目录。"""

    possible_path = Path(model_or_path).expanduser()
    if not possible_path.is_absolute():
        project_relative = PROJECT_ROOT / possible_path
        if project_relative.exists():
            possible_path = project_relative

    if possible_path.exists():
        return str(possible_path.resolve())

    try:
        from modelscope import snapshot_download
    except ImportError as exc:
        raise VoiceTranscriptionUnavailableError(
            "服务器尚未安装 modelscope，请先在 api_server 执行 uv sync。"
        ) from exc

    try:
        return str(
            snapshot_download(
                model_or_path,
                cache_dir=str(cache_root),
            )
        )
    except Exception as exc:
        raise VoiceTranscriptionUnavailableError(
            f"无法从 ModelScope 下载语音模型：{model_or_path}。"
            "请检查网络连接后重新执行模型预加载脚本。"
        ) from exc


def _probe_audio_duration(audio_path: Path) -> float | None:
    """使用 PyAV 获取 M4A/MP3/WAV 等音频时长。"""

    try:
        import av
    except ImportError:
        return None

    try:
        with av.open(str(audio_path)) as container:
            if container.duration is not None:
                return round(float(container.duration) / 1_000_000.0, 3)

            for stream in container.streams.audio:
                if stream.duration is not None and stream.time_base is not None:
                    return round(float(stream.duration * stream.time_base), 3)
    except Exception:
        return None

    return None


def _extract_detected_language(raw_text: str, requested_language: str) -> str:
    match = _LANGUAGE_TAG_PATTERN.search(raw_text)
    if match is not None:
        detected = match.group("language")
        return "unknown" if detected == "nospeech" else detected
    return "unknown" if requested_language == "auto" else requested_language


def _extract_confidence(result: dict[str, Any]) -> float | None:
    """兼容不同 FunASR 模型可能返回的置信度字段。"""

    for key in ("confidence", "score", "probability"):
        raw_value = result.get(key)
        if isinstance(raw_value, int | float):
            value = float(raw_value)
            if 0.0 <= value <= 1.0:
                return round(value, 4)
    return None


class FunASRTranscriptionService:
    """基于 FunASR + ModelScope 的本地语音转写服务。

    模型从国内 ModelScope 模型库下载，并缓存在项目 models/modelscope
    目录。模型按需加载并在进程内复用；推理由互斥锁串行化，避免
    开发机同时处理多段录音时出现较高的内存峰值。
    """

    provider_name = "funasr_modelscope"

    def __init__(self) -> None:
        self._model: Any | None = None
        self._model_lock = threading.Lock()
        self._inference_lock = threading.Lock()
        self._resolved_model_path: str | None = None

    @property
    def model_name(self) -> str:
        return settings.voice_funasr_model

    def _get_model(self) -> Any:
        if not settings.voice_transcription_enabled:
            raise VoiceTranscriptionUnavailableError(
                "本地语音转写尚未启用，请检查 VOICE_TRANSCRIPTION_ENABLED。"
            )

        if self._model is not None:
            return self._model

        with self._model_lock:
            if self._model is not None:
                return self._model

            try:
                from funasr import AutoModel
            except ImportError as exc:
                raise VoiceTranscriptionUnavailableError(
                    "服务器尚未安装 FunASR，请先在 api_server 执行 uv sync。"
                ) from exc

            cache_root = _resolve_cache_root(settings.voice_funasr_cache_root)
            # ModelScope 与 FunASR 都会读取该变量。设置后可确保模型缓存
            # 留在项目目录，而不是写入系统盘用户缓存目录。
            os.environ.setdefault("MODELSCOPE_CACHE", str(cache_root))

            model_path = _resolve_model_path(
                settings.voice_funasr_model,
                cache_root,
            )
            vad_model_path = None
            if settings.voice_funasr_vad_model.strip():
                vad_model_path = _resolve_model_path(
                    settings.voice_funasr_vad_model,
                    cache_root,
                )

            model_kwargs: dict[str, Any] = {
                "model": model_path,
                "device": settings.voice_funasr_device,
                "hub": settings.voice_funasr_hub,
                "disable_update": True,
                "disable_pbar": True,
            }
            if vad_model_path is not None:
                model_kwargs["vad_model"] = vad_model_path
                model_kwargs["vad_kwargs"] = {
                    "max_single_segment_time": 30_000,
                }
            if settings.voice_funasr_cpu_threads > 0:
                model_kwargs["ncpu"] = settings.voice_funasr_cpu_threads

            try:
                self._model = AutoModel(**model_kwargs)
                self._resolved_model_path = model_path
            except Exception as exc:
                raise VoiceTranscriptionUnavailableError(
                    "本地 FunASR 模型加载失败。请检查模型目录、PyTorch、"
                    "CPU/GPU 配置，或重新执行模型预加载脚本。"
                ) from exc

        return self._model

    def warm_up(self) -> None:
        """提前从 ModelScope 下载并加载模型。"""

        self._get_model()

    def transcribe(
        self,
        audio_path: Path,
        *,
        requested_language: str | None = "zh-CN",
    ) -> VoiceTranscriptionResult:
        """转写本地音频文件；原始文件由调用方负责删除。"""

        duration = _probe_audio_duration(audio_path)
        if (
            duration is not None
            and duration > settings.voice_audio_max_duration_seconds
        ):
            raise VoiceAudioTooLongError(
                f"录音时长不能超过 {settings.voice_audio_max_duration_seconds} 秒。"
            )

        model = self._get_model()
        funasr_language = _normalize_language(requested_language)

        try:
            from funasr.utils.postprocess_utils import rich_transcription_postprocess
        except ImportError as exc:
            raise VoiceTranscriptionUnavailableError(
                "FunASR 后处理组件不可用，请重新执行 uv sync。"
            ) from exc

        try:
            with self._inference_lock:
                raw_results = model.generate(
                    input=str(audio_path),
                    cache={},
                    language=funasr_language,
                    use_itn=True,
                    batch_size_s=settings.voice_funasr_batch_size_seconds,
                    merge_vad=True,
                    merge_length_s=15,
                )
        except VoiceTranscriptionError:
            raise
        except Exception as exc:
            raise VoiceTranscriptionError(
                "本地语音转写失败，请确认录音文件有效后重试。"
            ) from exc

        if not raw_results or not isinstance(raw_results[0], dict):
            raise VoiceNoSpeechError(
                "录音中未识别到有效讲话，请靠近麦克风后重新录制。"
            )

        first_result: dict[str, Any] = raw_results[0]
        raw_text = str(first_result.get("text") or "").strip()
        transcript = rich_transcription_postprocess(raw_text).strip()
        if not transcript:
            raise VoiceNoSpeechError(
                "录音中未识别到有效讲话，请靠近麦克风后重新录制。"
            )

        sentence_info = first_result.get("sentence_info")
        segment_count = (
            len(sentence_info)
            if isinstance(sentence_info, list) and sentence_info
            else 1
        )
        detected_language = _extract_detected_language(
            raw_text,
            funasr_language,
        )

        return VoiceTranscriptionResult(
            transcript=transcript,
            language=detected_language,
            language_probability=None,
            duration_seconds=duration,
            provider=self.provider_name,
            model_name=settings.voice_funasr_model,
            segment_count=segment_count,
            recognition_confidence=_extract_confidence(first_result),
        )


voice_transcription_service = FunASRTranscriptionService()
