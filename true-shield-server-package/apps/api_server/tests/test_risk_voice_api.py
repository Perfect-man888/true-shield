from __future__ import annotations

import uuid
from pathlib import Path

import pytest
from httpx import AsyncClient

import app.api.routes.risk as risk_routes
from app.services.voice_transcription import VoiceTranscriptionResult

TEST_PASSWORD = "VoiceRiskTest_123!"

pytestmark = pytest.mark.asyncio


@pytest.fixture(autouse=True)
def isolate_voice_side_effects(monkeypatch: pytest.MonkeyPatch) -> None:
    """语音接口测试不加载真实模型，也不创建家庭告警。"""

    async def no_op_trigger(db, *, event):
        return None

    def fake_transcribe(
        audio_path: Path,
        *,
        requested_language: str | None = "zh-CN",
    ) -> VoiceTranscriptionResult:
        assert audio_path.exists()
        assert audio_path.stat().st_size > 0
        return VoiceTranscriptionResult(
            transcript="我是公安局工作人员，请马上转账到安全账户。",
            language="zh",
            language_probability=0.99,
            duration_seconds=4.2,
            provider="funasr_modelscope",
            model_name="iic/SenseVoiceSmall",
            segment_count=1,
            recognition_confidence=0.91,
        )

    monkeypatch.setattr(
        risk_routes,
        "trigger_family_alert_automation_safely",
        no_op_trigger,
    )
    monkeypatch.setattr(
        risk_routes.voice_transcription_service,
        "transcribe",
        fake_transcribe,
    )


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    email = f"{name.lower()}-{uuid.uuid4().hex}@example.com"

    register_response = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "phone": None,
            "display_name": name,
            "password": TEST_PASSWORD,
        },
    )
    assert register_response.status_code in {200, 201}

    login_response = await client.post(
        "/api/v1/auth/login",
        data={
            "username": email,
            "password": TEST_PASSWORD,
        },
    )
    assert login_response.status_code == 200

    return {
        "Authorization": f"Bearer {login_response.json()['access_token']}",
    }


async def test_user_can_upload_audio_transcribe_and_analyze(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        name="VoiceAudioOwner",
    )

    response = await db_client.post(
        "/api/v1/risk/voice/audio/analyze",
        headers=headers,
        data={"language": "zh-CN"},
        files={
            "audio": (
                "voice-risk.m4a",
                b"fake-m4a-audio-content" * 30,
                "audio/mp4",
            ),
        },
    )
    assert response.status_code == 200, response.text

    body = response.json()
    assert body["source_type"] == "voice"
    assert body["recognition_provider"] == "funasr_modelscope"
    assert body["transcription_model"] == "iic/SenseVoiceSmall"
    assert body["language"] == "zh"
    assert body["language_probability"] == 0.99
    assert body["duration_seconds"] == 4.2
    assert body["audio_size_bytes"] > 0
    assert body["event_id"]
    assert body["score"] > 0

    detail_response = await db_client.get(
        f"/api/v1/risk/events/{body['event_id']}",
        headers=headers,
    )
    assert detail_response.status_code == 200

    detail = detail_response.json()
    assert detail["source_type"] == "voice"
    assert detail["source_text"] == body["transcript"]
    voice_metadata = detail["source_metadata"]["voice"]
    assert voice_metadata["recognition_provider"] == "funasr_modelscope"
    assert voice_metadata["transcription_model"] == "iic/SenseVoiceSmall"
    assert voice_metadata["raw_audio_stored"] is False
    assert voice_metadata["audio_content_type"] == "audio/mp4"

    dashboard_response = await db_client.get(
        "/api/v1/risk/dashboard",
        headers=headers,
        params={"period_days": 7},
    )
    assert dashboard_response.status_code == 200
    assert dashboard_response.json()["source_types"]["voice"] == 1


async def test_user_can_reanalyze_corrected_voice_transcript(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        name="VoiceCorrectionOwner",
    )
    transcript = "客服说不要转账，也不要提供验证码。"

    response = await db_client.post(
        "/api/v1/risk/voice/analyze",
        headers=headers,
        json={
            "transcript": transcript,
            "language": "zh-CN",
            "recognition_provider": "manual_correction",
            "transcription_model": "small",
        },
    )
    assert response.status_code == 200, response.text
    assert response.json()["transcript"] == transcript
    assert response.json()["recognition_provider"] == "manual_correction"


async def test_voice_audio_rejects_unsupported_media_type(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        name="UnsupportedVoiceOwner",
    )

    response = await db_client.post(
        "/api/v1/risk/voice/audio/analyze",
        headers=headers,
        files={
            "audio": (
                "voice.txt",
                b"not audio" * 50,
                "text/plain",
            ),
        },
    )
    assert response.status_code == 415


async def test_voice_audio_requires_login(
    db_client: AsyncClient,
) -> None:
    response = await db_client.post(
        "/api/v1/risk/voice/audio/analyze",
        files={
            "audio": (
                "voice.m4a",
                b"fake-audio" * 50,
                "audio/mp4",
            ),
        },
    )
    assert response.status_code == 401
