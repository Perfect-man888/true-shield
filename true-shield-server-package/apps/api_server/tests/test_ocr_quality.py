from __future__ import annotations

import pytest

from app.services.ocr_service import (
    OCRExtractionResult,
    OCRTextLine,
    evaluate_ocr_quality,
)


def make_line(
    *,
    text: str,
    confidence: float,
) -> OCRTextLine:
    """创建测试用 OCR 文字行。"""

    return OCRTextLine(
        text=text,
        confidence=confidence,
        box=(),
    )


def make_result(
    *lines: OCRTextLine,
) -> OCRExtractionResult:
    """创建测试用 OCR 提取结果。"""

    return OCRExtractionResult(
        text="\n".join(
            line.text
            for line in lines
        ),
        lines=tuple(lines),
        width=300,
        height=200,
    )


def test_high_quality_ocr_does_not_need_review() -> None:
    """高置信度识别结果不需要人工核对。"""

    result = make_result(
        make_line(
            text="我是公安局工作人员",
            confidence=0.98,
        ),
        make_line(
            text="请不要向陌生人转账",
            confidence=0.92,
        ),
    )

    quality = evaluate_ocr_quality(result)

    assert quality.line_count == 2
    assert quality.average_confidence == 0.95
    assert quality.minimum_confidence == 0.92
    assert quality.needs_manual_review is False
    assert quality.review_reason is None


def test_low_average_confidence_needs_review() -> None:
    """平均置信度较低时应建议人工核对。"""

    result = make_result(
        make_line(
            text="第一行文字",
            confidence=0.70,
        ),
        make_line(
            text="第二行文字",
            confidence=0.72,
        ),
    )

    quality = evaluate_ocr_quality(result)

    assert quality.line_count == 2
    assert quality.average_confidence == 0.71
    assert quality.minimum_confidence == 0.70
    assert quality.needs_manual_review is True

    assert quality.review_reason is not None
    assert "平均识别置信度较低" in (
        quality.review_reason
    )


def test_single_low_confidence_line_needs_review() -> None:
    """存在单行低置信度文字时也应建议人工核对。"""

    result = make_result(
        make_line(
            text="第一行",
            confidence=0.95,
        ),
        make_line(
            text="第二行",
            confidence=0.95,
        ),
        make_line(
            text="第三行",
            confidence=0.95,
        ),
        make_line(
            text="第四行",
            confidence=0.95,
        ),
        make_line(
            text="可能识别错误的一行",
            confidence=0.55,
        ),
    )

    quality = evaluate_ocr_quality(result)

    assert quality.line_count == 5
    assert quality.average_confidence == 0.87
    assert quality.minimum_confidence == 0.55
    assert quality.needs_manual_review is True

    assert quality.review_reason is not None
    assert "部分文字行识别置信度过低" in (
        quality.review_reason
    )


def test_empty_ocr_result_needs_review() -> None:
    """没有文字行时应返回需要人工核对。"""

    result = make_result()

    quality = evaluate_ocr_quality(result)

    assert quality.line_count == 0
    assert quality.average_confidence == 0.0
    assert quality.minimum_confidence == 0.0
    assert quality.needs_manual_review is True

    assert quality.review_reason == (
        "图片中没有可用于评估的文字行。"
    )


def test_invalid_quality_threshold_is_rejected() -> None:
    """质量阈值必须位于 0 到 1 之间。"""

    result = make_result(
        make_line(
            text="测试文字",
            confidence=0.90,
        ),
    )

    with pytest.raises(
        ValueError,
        match="average_threshold",
    ):
        evaluate_ocr_quality(
            result,
            average_threshold=1.1,
        )

    with pytest.raises(
        ValueError,
        match="minimum_threshold",
    ):
        evaluate_ocr_quality(
            result,
            minimum_threshold=-0.1,
        )