package com.trueshield.app.data.model.risk

/**
 * OCR 识别出的单行文字。
 */
data class OcrTextLineDto(
    /**
     * 识别出的文字。
     */
    val text: String,

    /**
     * OCR 置信度，范围 0～1。
     */
    val confidence: Double,

    /**
     * 文字框在原图中的坐标点。
     *
     * 后端格式示例：
     * [[10, 20], [100, 20], [100, 50], [10, 50]]
     */
    val box: List<List<Int>> =
        emptyList(),
)