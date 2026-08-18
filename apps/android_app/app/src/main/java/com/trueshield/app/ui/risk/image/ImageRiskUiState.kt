package com.trueshield.app.ui.risk.image

import android.graphics.Bitmap
import com.trueshield.app.data.model.risk.ImageRiskResponse

/**
 * 图片风险检测页面状态。
 */
data class ImageRiskUiState(

    /**
     * 已选择图片的文件名。
     */
    val fileName: String? = null,

    /**
     * 图片 MIME 类型。
     *
     * 例如：
     * image/jpeg
     * image/png
     */
    val contentType: String? = null,

    /**
     * 图片大小，单位为字节。
     */
    val fileSizeBytes: Long = 0L,

    /**
     * 经过缩小处理的图片预览。
     *
     * 不直接保存原始大图，避免预览时占用过多内存。
     */
    val previewBitmap: Bitmap? = null,

    /**
     * 是否正在读取系统选择的图片。
     */
    val isReadingImage: Boolean = false,

    /**
     * 是否正在上传图片并等待 OCR。
     */
    val isLoading: Boolean = false,

    /**
     * 图片 OCR 与风险检测结果。
     */
    val result: ImageRiskResponse? = null,

    /**
     * 页面错误信息。
     */
    val errorMessage: String? = null,

    /**
     * Token 是否已经失效。
     */
    val sessionExpired: Boolean = false,
) {

    /**
     * 当前是否已经成功选择图片。
     */
    val hasSelectedImage: Boolean
        get() =
            !fileName.isNullOrBlank() &&
                    fileSizeBytes > 0L

    /**
     * 当前是否允许提交检测。
     */
    val canAnalyze: Boolean
        get() =
            hasSelectedImage &&
                    !isReadingImage &&
                    !isLoading
}