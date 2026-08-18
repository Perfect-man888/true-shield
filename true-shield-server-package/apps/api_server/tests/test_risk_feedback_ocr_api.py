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

TEST_PASSWORD = "OCRFeedbackTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    """注册并登录一个随机测试用户。"""

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
    """创建测试用 PNG 图片。"""

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


async def create_text_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> str:
    """创建普通文本风险事件。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={
            "text": "请立即给对方转账。",
        },
    )

    assert response.status_code == 200, response.text

    return response.json()["event_id"]


async def create_image_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    monkeypatch: pytest.MonkeyPatch,
) -> str:
    """通过模拟 OCR 创建图片风险事件。"""

    fake_result = OCRExtractionResult(
        text=(
            "我是公安局工作人员。\n"
            "请马上把钱转到安全账户。"
        ),
        lines=(
            OCRTextLine(
                text="我是公安局工作人员。",
                confidence=0.98,
                box=(
                    (10, 10),
                    (300, 10),
                    (300, 40),
                    (10, 40),
                ),
            ),
            OCRTextLine(
                text="请马上把钱转到安全账户。",
                confidence=0.96,
                box=(
                    (10, 50),
                    (350, 50),
                    (350, 90),
                    (10, 90),
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


async def test_user_can_save_ocr_correction_feedback(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """图片事件可以保存 OCR 纠错内容。"""

    headers = await register_and_login(
        client,
        name="OCRCorrectionOwner",
    )

    event_id = await create_image_risk_event(
        client,
        headers=headers,
        monkeypatch=monkeypatch,
    )

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "ocr_text_accurate": False,
            "corrected_text": (
                "  我是公安局工作人员。\n"
                "请马上把钱转到安全账户。  "
            ),
            "comment": "OCR 原文存在少量错误。",
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["event_id"] == event_id
    assert body["ocr_text_accurate"] is False

    # Schema 应去除纠错文本首尾空白。
    assert body["corrected_text"] == (
        "我是公安局工作人员。\n"
        "请马上把钱转到安全账户。"
    )

    assert body["comment"] == (
        "OCR 原文存在少量错误。"
    )

    read_response = await client.get(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
    )

    assert read_response.status_code == 200, (
        read_response.text
    )

    read_body = read_response.json()

    assert read_body["id"] == body["id"]
    assert read_body["ocr_text_accurate"] is False
    assert read_body["corrected_text"] == (
        "我是公安局工作人员。\n"
        "请马上把钱转到安全账户。"
    )


async def test_second_submission_can_confirm_ocr_is_accurate(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """再次提交时可以更新并清除旧纠错文本。"""

    headers = await register_and_login(
        client,
        name="OCRCorrectionUpdater",
    )

    event_id = await create_image_risk_event(
        client,
        headers=headers,
        monkeypatch=monkeypatch,
    )

    first_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "ocr_text_accurate": False,
            "corrected_text": "第一次人工修正文本。",
        },
    )

    assert first_response.status_code == 200, (
        first_response.text
    )

    first_body = first_response.json()

    second_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "ocr_text_accurate": True,
            "comment": "重新查看后确认 OCR 正确。",
        },
    )

    assert second_response.status_code == 200, (
        second_response.text
    )

    second_body = second_response.json()

    # ID 不变，说明更新的是原反馈。
    assert second_body["id"] == first_body["id"]

    assert second_body["ocr_text_accurate"] is True
    assert second_body["corrected_text"] is None
    assert second_body["comment"] == (
        "重新查看后确认 OCR 正确。"
    )


async def test_text_event_rejects_ocr_feedback(
    client: AsyncClient,
) -> None:
    """普通文本事件不能提交 OCR 纠错反馈。"""

    headers = await register_and_login(
        client,
        name="TextEventOCRUser",
    )

    event_id = await create_text_risk_event(
        client,
        headers=headers,
    )

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "ocr_text_accurate": True,
        },
    )

    assert response.status_code == 422, response.text

    assert response.json()["detail"] == (
        "只有图片风险事件可以提交 OCR 纠错反馈。"
    )


async def test_image_event_allows_normal_feedback_without_ocr_fields(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """图片事件仍然可以只提交普通风险反馈。"""

    headers = await register_and_login(
        client,
        name="ImageNormalFeedbackUser",
    )

    event_id = await create_image_risk_event(
        client,
        headers=headers,
        monkeypatch=monkeypatch,
    )

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "comment": "风险判断结果正确。",
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["ocr_text_accurate"] is None
    assert body["corrected_text"] is None
    assert body["comment"] == "风险判断结果正确。"