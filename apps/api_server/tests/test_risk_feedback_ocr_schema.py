from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.risk_feedback import (
    RiskFeedbackCreate,
)


def test_accepts_accurate_ocr_feedback() -> None:
    """OCR 识别准确时不需要修正文本。"""

    payload = RiskFeedbackCreate(
        feedback_type="accurate",
        ocr_text_accurate=True,
    )

    assert payload.ocr_text_accurate is True
    assert payload.corrected_text is None


def test_accepts_corrected_ocr_text() -> None:
    """OCR 不准确时可以提交修正后的文字。"""

    payload = RiskFeedbackCreate(
        feedback_type="accurate",
        ocr_text_accurate=False,
        corrected_text=(
            "  我是公安局的，请马上转账。  "
        ),
    )

    assert payload.ocr_text_accurate is False

    assert payload.corrected_text == (
        "我是公安局的，请马上转账。"
    )


def test_inaccurate_ocr_requires_corrected_text() -> None:
    """标记 OCR 不准确时必须提供修正文本。"""

    with pytest.raises(
        ValidationError,
        match="必须提供 corrected_text",
    ):
        RiskFeedbackCreate(
            feedback_type="accurate",
            ocr_text_accurate=False,
        )


def test_accurate_ocr_rejects_corrected_text() -> None:
    """OCR 已确认准确时不应再提交修正文本。"""

    with pytest.raises(
        ValidationError,
        match="不应提供 corrected_text",
    ):
        RiskFeedbackCreate(
            feedback_type="accurate",
            ocr_text_accurate=True,
            corrected_text="修正文本",
        )


def test_corrected_text_requires_accuracy_flag() -> None:
    """提供纠错文本时必须明确标记 OCR 不准确。"""

    with pytest.raises(
        ValidationError,
        match="ocr_text_accurate=false",
    ):
        RiskFeedbackCreate(
            feedback_type="accurate",
            corrected_text="修正文本",
        )