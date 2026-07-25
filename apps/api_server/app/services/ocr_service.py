from __future__ import annotations

import warnings
from dataclasses import dataclass
from io import BytesIO
from typing import Any, Protocol

import numpy as np
from numpy.typing import NDArray
from PIL import Image, ImageOps, UnidentifiedImageError
from rapidocr import RapidOCR

DEFAULT_MAX_IMAGE_BYTES = 10 * 1024 * 1024
DEFAULT_MAX_IMAGE_PIXELS = 20_000_000
DEFAULT_MIN_CONFIDENCE = 0.35

ImageArray = NDArray[np.uint8]
Point = tuple[int, int]
TextBox = tuple[Point, ...]


class OCREngine(Protocol):
    """OCR 引擎需要实现的调用接口。"""

    def __call__(
        self,
        image: ImageArray,
    ) -> Any:
        """识别图像并返回 OCR 结果。"""


class OCRServiceError(ValueError):
    """OCR 服务基础异常。"""


class OCRImageTooLargeError(OCRServiceError):
    """上传的图片文件或图片像素过大。"""


class OCRInvalidImageError(OCRServiceError):
    """上传内容不是有效图片。"""


class OCRNoTextError(OCRServiceError):
    """图片中没有识别到有效文字。"""


@dataclass(
    frozen=True,
    slots=True,
)
class OCRTextLine:
    """单行 OCR 识别结果。"""

    text: str
    confidence: float
    box: TextBox


@dataclass(
    frozen=True,
    slots=True,
)
class OCRExtractionResult:
    """完整 OCR 提取结果。"""

    text: str
    lines: tuple[OCRTextLine, ...]
    width: int
    height: int

@dataclass(
    frozen=True,
    slots=True,
)
class OCRQualityResult:
    """OCR 识别质量评估结果。"""

    line_count: int
    average_confidence: float
    minimum_confidence: float
    needs_manual_review: bool
    review_reason: str | None

class OCRService:
    """聊天截图 OCR 识别服务。"""

    def __init__(
        self,
        *,
        engine: OCREngine | None = None,
        max_image_bytes: int = DEFAULT_MAX_IMAGE_BYTES,
        max_image_pixels: int = DEFAULT_MAX_IMAGE_PIXELS,
        min_confidence: float = DEFAULT_MIN_CONFIDENCE,
    ) -> None:
        if max_image_bytes <= 0:
            raise ValueError(
                "max_image_bytes 必须大于 0。"
            )

        if max_image_pixels <= 0:
            raise ValueError(
                "max_image_pixels 必须大于 0。"
            )

        if not 0 <= min_confidence <= 1:
            raise ValueError(
                "min_confidence 必须位于 0 到 1 之间。"
            )

        self._engine = engine or RapidOCR()
        self._max_image_bytes = max_image_bytes
        self._max_image_pixels = max_image_pixels
        self._min_confidence = min_confidence

    def extract_text(
        self,
        image_bytes: bytes,
    ) -> OCRExtractionResult:
        """
        从图片字节中识别文字。

        返回：
        1. 合并后的完整文字
        2. 每行文字及置信度
        3. 每行文字在图片中的坐标
        4. 图片宽高
        """

        if not image_bytes:
            raise OCRInvalidImageError(
                "图片内容不能为空。"
            )

        if len(image_bytes) > self._max_image_bytes:
            raise OCRImageTooLargeError(
                "图片文件过大。"
            )

        image = self._decode_image(image_bytes)

        width, height = image.size
        pixel_count = width * height

        if pixel_count > self._max_image_pixels:
            raise OCRImageTooLargeError(
                "图片分辨率过高。"
            )

        image_array = np.asarray(
            image,
            dtype=np.uint8,
        )

        try:
            raw_result = self._engine(image_array)
        except Exception as exc:
            raise OCRServiceError(
                "OCR 文字识别失败。"
            ) from exc

        raw_texts = getattr(
            raw_result,
            "txts",
            None,
        )
        raw_scores = getattr(
            raw_result,
            "scores",
            None,
        )
        raw_boxes = getattr(
            raw_result,
            "boxes",
            None,
        )

        texts = (
            tuple(raw_texts)
            if raw_texts is not None
            else ()
        )
        scores = (
            tuple(raw_scores)
            if raw_scores is not None
            else ()
        )
        boxes = (
            tuple(raw_boxes)
            if raw_boxes is not None
            else ()
        )

        lines: list[OCRTextLine] = []

        for index, raw_text in enumerate(texts):
            text = str(raw_text).strip()

            if not text:
                continue

            confidence = (
                float(scores[index])
                if index < len(scores)
                else 1.0
            )

            if confidence < self._min_confidence:
                continue

            raw_box = (
                boxes[index]
                if index < len(boxes)
                else None
            )

            lines.append(
                OCRTextLine(
                    text=text,
                    confidence=confidence,
                    box=self._normalize_box(raw_box),
                )
            )

        if not lines:
            raise OCRNoTextError(
                "图片中未识别到有效文字。"
            )

        combined_text = "\n".join(
            line.text
            for line in lines
        )

        return OCRExtractionResult(
            text=combined_text,
            lines=tuple(lines),
            width=width,
            height=height,
        )

    @staticmethod
    def _decode_image(
        image_bytes: bytes,
    ) -> Image.Image:
        """读取、校验并规范化图片。"""

        try:
            with warnings.catch_warnings():
                warnings.simplefilter(
                    "error",
                    Image.DecompressionBombWarning,
                )

                with Image.open(
                    BytesIO(image_bytes)
                ) as opened_image:
                    opened_image.load()

                    image = ImageOps.exif_transpose(
                        opened_image
                    ).convert("RGB")

        except (
            UnidentifiedImageError,
            OSError,
            Image.DecompressionBombError,
            Image.DecompressionBombWarning,
        ) as exc:
            raise OCRInvalidImageError(
                "上传内容不是有效图片。"
            ) from exc

        return image

    @staticmethod
    def _normalize_box(
        raw_box: Any,
    ) -> TextBox:
        """把 OCR 返回的坐标转换为整数坐标。"""

        if raw_box is None:
            return ()

        points: list[Point] = []

        try:
            for raw_point in raw_box:
                if len(raw_point) < 2:
                    continue

                x = int(
                    round(float(raw_point[0]))
                )
                y = int(
                    round(float(raw_point[1]))
                )

                points.append((x, y))

        except (
            TypeError,
            ValueError,
            IndexError,
        ):
            return ()

        return tuple(points)

def evaluate_ocr_quality(
    result: OCRExtractionResult,
    *,
    average_threshold: float = 0.80,
    minimum_threshold: float = 0.60,
) -> OCRQualityResult:
    """
    根据 OCR 每行置信度评估识别质量。

    average_threshold:
        平均置信度低于该值时建议人工核对。

    minimum_threshold:
        任意一行置信度低于该值时建议人工核对。
    """

    if not 0 <= average_threshold <= 1:
        raise ValueError(
            "average_threshold 必须位于 0 到 1 之间。"
        )

    if not 0 <= minimum_threshold <= 1:
        raise ValueError(
            "minimum_threshold 必须位于 0 到 1 之间。"
        )

    if not result.lines:
        return OCRQualityResult(
            line_count=0,
            average_confidence=0.0,
            minimum_confidence=0.0,
            needs_manual_review=True,
            review_reason="图片中没有可用于评估的文字行。",
        )

    confidences = [
        max(
            0.0,
            min(
                1.0,
                float(line.confidence),
            ),
        )
        for line in result.lines
    ]

    average_confidence = round(
        sum(confidences) / len(confidences),
        4,
    )

    minimum_confidence = round(
        min(confidences),
        4,
    )

    review_reasons: list[str] = []

    if average_confidence < average_threshold:
        review_reasons.append(
            "OCR 平均识别置信度较低"
        )

    if minimum_confidence < minimum_threshold:
        review_reasons.append(
            "部分文字行识别置信度过低"
        )

    needs_manual_review = bool(review_reasons)

    review_reason = (
        "；".join(review_reasons) + "，建议对照原图核对文字。"
        if review_reasons
        else None
    )

    return OCRQualityResult(
        line_count=len(result.lines),
        average_confidence=average_confidence,
        minimum_confidence=minimum_confidence,
        needs_manual_review=needs_manual_review,
        review_reason=review_reason,
    )