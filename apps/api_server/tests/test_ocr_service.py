from __future__ import annotations

from io import BytesIO
from types import SimpleNamespace
from typing import Any

import numpy as np
import pytest
from numpy.typing import NDArray
from PIL import Image

from app.services.ocr_service import (
    OCRImageTooLargeError,
    OCRInvalidImageError,
    OCRNoTextError,
    OCRService,
)


def create_png_bytes(
    *,
    width: int = 200,
    height: int = 100,
) -> bytes:
    """创建用于测试的有效 PNG 图片。"""

    image = Image.new(
        "RGB",
        (width, height),
        color="white",
    )

    output = BytesIO()
    image.save(
        output,
        format="PNG",
    )

    return output.getvalue()


class FakeOCREngine:
    """避免单元测试真正运行 OCR 模型。"""

    def __init__(
        self,
        result: Any,
    ) -> None:
        self.result = result
        self.received_image: (
            NDArray[np.uint8] | None
        ) = None

    def __call__(
        self,
        image: NDArray[np.uint8],
    ) -> Any:
        self.received_image = image
        return self.result


def test_extracts_text_scores_and_boxes() -> None:
    """能够提取文字、置信度和文字框坐标。"""

    fake_result = SimpleNamespace(
        txts=(
            "请立即给对方转账",
            "不要告诉家人",
        ),
        scores=(
            0.98,
            0.91,
        ),
        boxes=(
            (
                (10, 10),
                (180, 10),
                (180, 30),
                (10, 30),
            ),
            (
                (10, 40),
                (150, 40),
                (150, 60),
                (10, 60),
            ),
        ),
    )

    engine = FakeOCREngine(fake_result)

    service = OCRService(
        engine=engine,
    )

    result = service.extract_text(
        create_png_bytes()
    )

    assert result.text == (
        "请立即给对方转账\n不要告诉家人"
    )

    assert result.width == 200
    assert result.height == 100
    assert len(result.lines) == 2

    assert result.lines[0].text == (
        "请立即给对方转账"
    )
    assert result.lines[0].confidence == 0.98

    assert result.lines[0].box == (
        (10, 10),
        (180, 10),
        (180, 30),
        (10, 30),
    )

    assert engine.received_image is not None
    assert engine.received_image.shape == (
        100,
        200,
        3,
    )


def test_filters_low_confidence_text() -> None:
    """低置信度 OCR 噪声不应进入最终文本。"""

    fake_result = SimpleNamespace(
        txts=(
            "请立即转账",
            "识别噪声",
        ),
        scores=(
            0.95,
            0.12,
        ),
        boxes=(
            (),
            (),
        ),
    )

    service = OCRService(
        engine=FakeOCREngine(fake_result),
        min_confidence=0.50,
    )

    result = service.extract_text(
        create_png_bytes()
    )

    assert result.text == "请立即转账"
    assert len(result.lines) == 1


def test_rejects_invalid_image() -> None:
    """非图片内容应被拒绝。"""

    service = OCRService(
        engine=FakeOCREngine(None),
    )

    with pytest.raises(
        OCRInvalidImageError,
        match="不是有效图片",
    ):
        service.extract_text(
            b"this is not an image"
        )


def test_rejects_image_file_that_is_too_large() -> None:
    """超过文件大小限制的图片应被拒绝。"""

    service = OCRService(
        engine=FakeOCREngine(None),
        max_image_bytes=10,
    )

    with pytest.raises(
        OCRImageTooLargeError,
        match="图片文件过大",
    ):
        service.extract_text(
            b"01234567890"
        )


def test_rejects_image_with_too_many_pixels() -> None:
    """超过像素限制的图片应被拒绝。"""

    service = OCRService(
        engine=FakeOCREngine(None),
        max_image_pixels=100,
    )

    with pytest.raises(
        OCRImageTooLargeError,
        match="图片分辨率过高",
    ):
        service.extract_text(
            create_png_bytes(
                width=20,
                height=20,
            )
        )


def test_raises_error_when_no_text_is_found() -> None:
    """没有识别到文字时应返回明确异常。"""

    fake_result = SimpleNamespace(
        txts=(),
        scores=(),
        boxes=(),
    )

    service = OCRService(
        engine=FakeOCREngine(fake_result),
    )

    with pytest.raises(
        OCRNoTextError,
        match="未识别到有效文字",
    ):
        service.extract_text(
            create_png_bytes()
        )