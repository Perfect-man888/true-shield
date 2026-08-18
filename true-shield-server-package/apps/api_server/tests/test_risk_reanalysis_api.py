from __future__ import annotations

import uuid
from io import BytesIO

import pytest
from httpx import AsyncClient
from PIL import Image

import app.api.routes.risk as risk_routes
from app.services.ocr_service import (
    OCRExtractionResult,
    OCRTextLine,
)

TEST_PASSWORD = "RiskReanalysisTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    """注册并登录随机测试用户。"""

    random_part = uuid.uuid4().hex

    email = (
        f"{name.lower()}-{random_part}@example.com"
    )

    register_response = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "phone": None,
            "display_name": name,
            "password": TEST_PASSWORD,
        },
    )

    assert register_response.status_code in {
        200,
        201,
    }, register_response.text

    login_response = await client.post(
        "/api/v1/auth/login",
        data={
            "username": email,
            "password": TEST_PASSWORD,
        },
    )

    assert login_response.status_code == 200, (
        login_response.text
    )

    access_token = login_response.json()[
        "access_token"
    ]

    return {
        "Authorization": f"Bearer {access_token}",
    }


def create_png_bytes() -> bytes:
    """生成测试用 PNG 图片。"""

    image = Image.new(
        "RGB",
        (400, 200),
        color="white",
    )

    output = BytesIO()

    image.save(
        output,
        format="PNG",
    )

    return output.getvalue()


async def create_image_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    monkeypatch: pytest.MonkeyPatch,
    extracted_text: str = "今天下午一起吃饭。",
) -> str:
    """通过模拟 OCR 创建图片风险事件。"""

    fake_result = OCRExtractionResult(
        text=extracted_text,
        lines=(
            OCRTextLine(
                text=extracted_text,
                confidence=0.99,
                box=(
                    (10, 10),
                    (350, 10),
                    (350, 50),
                    (10, 50),
                ),
            ),
        ),
        width=400,
        height=200,
    )

    def fake_extract_text(
        image_bytes: bytes,
    ) -> OCRExtractionResult:
        assert image_bytes

        return fake_result

    monkeypatch.setattr(
        risk_routes.ocr_service,
        "extract_text",
        fake_extract_text,
    )

    response = await client.post(
        "/api/v1/risk/image/analyze",
        headers=headers,
        files={
            "image": (
                "chat.png",
                create_png_bytes(),
                "image/png",
            ),
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["source_type"] == "image"

    return body["event_id"]


async def create_text_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> str:
    """创建普通文本风险事件。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={
            "text": "今天下午一起吃饭。",
        },
    )

    assert response.status_code == 200, response.text

    return response.json()["event_id"]


async def save_ocr_correction(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    event_id: str,
    corrected_text: str,
) -> dict:
    """保存图片 OCR 人工修正文本。"""

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "comment": "OCR 识别错误，已人工修正。",
            "ocr_text_accurate": False,
            "corrected_text": corrected_text,
        },
    )

    assert response.status_code == 200, response.text

    return response.json()


async def test_corrected_text_can_raise_risk_level(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """
    原始 OCR 为普通文本，人工修正为诈骗话术后，
    风险应从 low 升高到 high。
    """

    headers = await register_and_login(
        client,
        name="RiskReanalysisOwner",
    )

    event_id = await create_image_event(
        client,
        headers=headers,
        monkeypatch=monkeypatch,
        extracted_text="今天下午一起吃饭。",
    )

    corrected_text = (
        "我是公安局的，你涉嫌洗钱。\n"
        "这件事不能告诉家人。\n"
        "必须马上把钱转到安全账户。"
    )

    await save_ocr_correction(
        client,
        headers=headers,
        event_id=event_id,
        corrected_text=corrected_text,
    )

    response = await client.post(
        (
            f"/api/v1/risk/events/{event_id}"
            "/reanalyze-corrected"
        ),
        headers=headers,
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["event_id"] == event_id
    assert body["source_type"] == "image"

    assert body["original_text"] == (
        "今天下午一起吃饭。"
    )

    assert body["corrected_text"] == corrected_text

    original_analysis = body["original_analysis"]
    corrected_analysis = body["corrected_analysis"]
    comparison = body["comparison"]

    assert original_analysis["risk_level"] == "low"
    assert original_analysis["score"] == 0
    assert original_analysis["evidence"] == []

    assert corrected_analysis["risk_level"] == "high"
    assert corrected_analysis["score"] == 100
    assert corrected_analysis["evidence"]

    assert comparison["original_risk_level"] == "low"
    assert comparison["corrected_risk_level"] == "high"

    assert comparison["original_score"] == 0
    assert comparison["corrected_score"] == 100
    assert comparison["score_delta"] == 100

    assert comparison["risk_level_changed"] is True

    added_rule_ids = set(
        comparison["added_rule_ids"]
    )

    assert "R-COMBO-001" in added_rule_ids
    assert "R-TEXT-007" in added_rule_ids

    assert comparison["removed_rule_ids"] == []
    assert comparison["retained_rule_ids"] == []


async def test_image_event_without_corrected_text_returns_422(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """图片事件没有纠错文本时不能重新分析。"""

    headers = await register_and_login(
        client,
        name="NoCorrectedTextUser",
    )

    event_id = await create_image_event(
        client,
        headers=headers,
        monkeypatch=monkeypatch,
    )

    # 只提交普通反馈，不提交 corrected_text。
    feedback_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "comment": "风险判断正确。",
        },
    )

    assert feedback_response.status_code == 200, (
        feedback_response.text
    )

    response = await client.post(
        (
            f"/api/v1/risk/events/{event_id}"
            "/reanalyze-corrected"
        ),
        headers=headers,
    )

    assert response.status_code == 422, response.text

    assert response.json()["detail"] == (
        "当前反馈中没有可用于重新分析的 "
        "corrected_text。"
    )


async def test_text_event_cannot_use_ocr_reanalysis(
    client: AsyncClient,
) -> None:
    """普通文本事件不能调用 OCR 修正重新分析。"""

    headers = await register_and_login(
        client,
        name="TextReanalysisUser",
    )

    event_id = await create_text_event(
        client,
        headers=headers,
    )

    response = await client.post(
        (
            f"/api/v1/risk/events/{event_id}"
            "/reanalyze-corrected"
        ),
        headers=headers,
    )

    assert response.status_code == 422, response.text

    assert response.json()["detail"] == (
        "只有图片风险事件可以使用 "
        "OCR 修正文本重新分析。"
    )


async def test_user_cannot_reanalyze_another_users_event(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """用户不能重新分析其他用户的风险事件。"""

    owner_headers = await register_and_login(
        client,
        name="ReanalysisEventOwner",
    )

    other_headers = await register_and_login(
        client,
        name="UnauthorizedReanalysisUser",
    )

    event_id = await create_image_event(
        client,
        headers=owner_headers,
        monkeypatch=monkeypatch,
    )

    await save_ocr_correction(
        client,
        headers=owner_headers,
        event_id=event_id,
        corrected_text=(
            "我是公安局的，"
            "请马上把钱转到安全账户。"
        ),
    )

    response = await client.post(
        (
            f"/api/v1/risk/events/{event_id}"
            "/reanalyze-corrected"
        ),
        headers=other_headers,
    )

    assert response.status_code == 404, response.text

    assert response.json()["detail"] == (
        "风险事件不存在或无权访问。"
    )