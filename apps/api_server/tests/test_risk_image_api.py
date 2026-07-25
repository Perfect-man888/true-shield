from __future__ import annotations

import uuid
from io import BytesIO

import pytest
from httpx import AsyncClient
from PIL import Image

import app.api.routes.risk as risk_routes
from app.services.ocr_service import (
    OCRExtractionResult,
    OCRImageTooLargeError,
    OCRInvalidImageError,
    OCRNoTextError,
    OCRTextLine,
)

TEST_PASSWORD = "RiskImageTest_123!"

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
    """创建测试用 PNG 图片。"""

    image = Image.new(
        "RGB",
        (300, 200),
        color="white",
    )

    output = BytesIO()

    image.save(
        output,
        format="PNG",
    )

    return output.getvalue()


async def test_user_can_analyze_chat_image(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """用户可以上传聊天截图并执行风险分析。"""

    headers = await register_and_login(
        client,
        name="ImageRiskOwner",
    )

    extracted_text = (
        "我是公安局工作人员，你涉嫌洗钱，"
        "不能告诉家人，必须马上把钱转到安全账户。"
    )

    fake_result = OCRExtractionResult(
        text=extracted_text,
        lines=(
            OCRTextLine(
                text="我是公安局工作人员",
                confidence=0.98,
                box=(
                    (10, 10),
                    (260, 10),
                    (260, 40),
                    (10, 40),
                ),
            ),
            OCRTextLine(
                text=(
                    "你涉嫌洗钱，不能告诉家人，"
                    "必须马上把钱转到安全账户"
                ),
                confidence=0.96,
                box=(
                    (10, 50),
                    (290, 50),
                    (290, 100),
                    (10, 100),
                ),
            ),
        ),
        width=300,
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
    assert body["extracted_text"] == extracted_text
    assert body["image_width"] == 300
    assert body["image_height"] == 200

    assert len(body["ocr_lines"]) == 2

    assert body["ocr_quality"] == {
        "line_count": 2,
        "average_confidence": 0.97,
        "minimum_confidence": 0.96,
        "needs_manual_review": False,
        "review_reason": None,
    }

    assert body["ocr_lines"][0]["text"] == (
        "我是公安局工作人员"
    )

    assert body["ocr_lines"][0][
        "confidence"
    ] == pytest.approx(0.98)

    assert body["event_id"]
    assert body["score"] > 0
    assert body["evidence"]

    event_id = body["event_id"]

    detail_response = await client.get(
        f"/api/v1/risk/events/{event_id}",
        headers=headers,
    )

    assert detail_response.status_code == 200

    detail = detail_response.json()

    assert detail["source_type"] == "image"
    assert detail["source_text"] == extracted_text


async def test_image_analysis_rejects_wrong_content_type(
    client: AsyncClient,
) -> None:
    """非图片 MIME 类型应被拒绝。"""

    headers = await register_and_login(
        client,
        name="WrongImageType",
    )

    response = await client.post(
        "/api/v1/risk/image/analyze",
        headers=headers,
        files={
            "image": (
                "message.txt",
                b"not an image",
                "text/plain",
            ),
        },
    )

    assert response.status_code == 415

    assert response.json()["detail"] == (
        "仅支持 JPEG、PNG、WEBP 和 BMP 图片。"
    )


async def test_image_analysis_returns_422_when_no_text(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """图片没有文字时应返回 422。"""

    headers = await register_and_login(
        client,
        name="EmptyOCRImage",
    )

    def fake_extract_text(
        image_bytes: bytes,
    ) -> OCRExtractionResult:
        raise OCRNoTextError(
            "图片中未识别到有效文字。"
        )

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
                "empty.png",
                create_png_bytes(),
                "image/png",
            ),
        },
    )

    assert response.status_code == 422

    assert response.json()["detail"] == (
        "图片中未识别到有效文字。"
    )


async def test_image_analysis_rejects_invalid_image(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """损坏图片应返回 400。"""

    headers = await register_and_login(
        client,
        name="InvalidOCRImage",
    )

    def fake_extract_text(
        image_bytes: bytes,
    ) -> OCRExtractionResult:
        raise OCRInvalidImageError(
            "上传内容不是有效图片。"
        )

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
                "broken.png",
                b"broken image",
                "image/png",
            ),
        },
    )

    assert response.status_code == 400

    assert response.json()["detail"] == (
        "上传内容不是有效图片。"
    )


async def test_image_analysis_rejects_large_image(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """图片过大时应返回 413。"""

    headers = await register_and_login(
        client,
        name="LargeOCRImage",
    )

    def fake_extract_text(
        image_bytes: bytes,
    ) -> OCRExtractionResult:
        raise OCRImageTooLargeError(
            "图片文件过大。"
        )

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
                "large.png",
                create_png_bytes(),
                "image/png",
            ),
        },
    )

    assert response.status_code == 413

    assert response.json()["detail"] == (
        "图片文件过大。"
    )

async def test_image_analysis_marks_low_quality_ocr_for_review(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """低质量 OCR 结果应提示用户人工核对。"""

    headers = await register_and_login(
        client,
        name="LowQualityOCRUser",
    )

    fake_result = OCRExtractionResult(
        text=(
            "我是公安局工作人员\n"
            "请马上把钱转到安全账户"
        ),
        lines=(
            OCRTextLine(
                text="我是公安局工作人员",
                confidence=0.95,
                box=(
                    (10, 10),
                    (260, 10),
                    (260, 40),
                    (10, 40),
                ),
            ),
            OCRTextLine(
                text="请马上把钱转到安全账户",
                confidence=0.55,
                box=(
                    (10, 50),
                    (290, 50),
                    (290, 90),
                    (10, 90),
                ),
            ),
        ),
        width=300,
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
                "low-quality.png",
                create_png_bytes(),
                "image/png",
            ),
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()
    quality = body["ocr_quality"]

    assert quality["line_count"] == 2
    assert quality["average_confidence"] == 0.75
    assert quality["minimum_confidence"] == 0.55
    assert quality["needs_manual_review"] is True

    assert quality["review_reason"] is not None
    assert "平均识别置信度较低" in (
        quality["review_reason"]
    )
    assert "部分文字行识别置信度过低" in (
        quality["review_reason"]
    )

    # 即使 OCR 质量较低，也仍然返回风险分析结果。
    assert body["risk_level"] == "high"
    assert body["score"] > 0
    assert body["event_id"]