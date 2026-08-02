package com.trueshield.app.ui.risk.image

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.ImageRiskAnalysisResult
import com.trueshield.app.data.repository.RiskRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图片 OCR 与风险检测 ViewModel。
 */
class ImageRiskViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val riskRepository =
        RiskRepository(
            riskApi =
                trueShieldApplication
                    .apiClient
                    .riskApi,
        )

    /**
     * 原始图片数据只保存在 ViewModel 内部，
     * 不直接放入 Compose 页面状态。
     */
    private var selectedImage:
            SelectedImagePayload? = null

    private val _uiState =
        MutableStateFlow(
            ImageRiskUiState(),
        )

    val uiState: StateFlow<ImageRiskUiState> =
        _uiState.asStateFlow()

    /**
     * 用户从系统选择器中选择了一张图片。
     */
    fun onImageSelected(
        uri: Uri?,
    ) {
        if (uri == null) {
            return
        }

        /*
         * 开始读取新图片时清除上一张图片。
         */
        selectedImage = null

        _uiState.update {
            ImageRiskUiState(
                isReadingImage = true,
            )
        }

        viewModelScope.launch {
            try {
                val payload =
                    withContext(Dispatchers.IO) {
                        readSelectedImage(
                            uri = uri,
                        )
                    }

                selectedImage = payload

                _uiState.update {
                    it.copy(
                        fileName =
                            payload.fileName,
                        contentType =
                            payload.contentType,
                        fileSizeBytes =
                            payload.bytes.size.toLong(),
                        previewBitmap =
                            payload.previewBitmap,
                        isReadingImage = false,
                        isLoading = false,
                        result = null,
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }
            } catch (
                exception: CancellationException,
            ) {
                throw exception
            } catch (
                exception: SecurityException,
            ) {
                showImageReadError(
                    message =
                        "没有权限读取所选择的图片，" +
                                "请重新选择。",
                )
            } catch (
                exception: IOException,
            ) {
                showImageReadError(
                    message =
                        exception.message
                            ?: "读取图片失败，请重新选择。",
                )
            } catch (
                exception: IllegalArgumentException,
            ) {
                showImageReadError(
                    message =
                        exception.message
                            ?: "选择的图片不符合要求。",
                )
            } catch (
                exception: Exception,
            ) {
                showImageReadError(
                    message =
                        exception.message
                            ?: "处理所选择的图片时发生未知错误。",
                )
            }
        }
    }

    /**
     * 上传图片并执行 OCR 与风险检测。
     */
    fun analyzeImage() {
        val payload =
            selectedImage

        if (payload == null) {
            showError(
                message =
                    "请先选择需要检测的聊天截图。",
            )
            return
        }

        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                result = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    riskRepository.analyzeImage(
                        imageBytes =
                            payload.bytes,
                        fileName =
                            payload.fileName,
                        contentType =
                            payload.contentType,
                    )
            ) {
                is ImageRiskAnalysisResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            result =
                                result.response,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is ImageRiskAnalysisResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            result = null,
                            errorMessage =
                                result.message,
                            sessionExpired =
                                result.requiresLogin,
                        )
                    }
                }
            }
        }
    }

    /**
     * 关闭检测结果，但保留已经选择的图片。
     */
    fun clearResult() {
        _uiState.update {
            it.copy(
                result = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 清除所选图片和检测结果。
     */
    fun clearAll() {
        selectedImage = null

        _uiState.value =
            ImageRiskUiState()
    }

    /**
     * 标记登录失效事件已经处理。
     */
    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    /**
     * 从 content URI 中读取图片信息和图片数据。
     */
    private fun readSelectedImage(
        uri: Uri,
    ): SelectedImagePayload {
        val contentResolver =
            getApplication<Application>()
                .contentResolver

        val queriedFileName =
            queryFileName(
                uri = uri,
            )

        val queriedFileSize =
            queryFileSize(
                uri = uri,
            )

        /*
         * 能提前获取文件大小时，先进行快速拦截。
         */
        if (
            queriedFileSize != null &&
            queriedFileSize > MAX_IMAGE_BYTES
        ) {
            throw IllegalArgumentException(
                "图片不能超过 10 MB。",
            )
        }

        val rawContentType =
            contentResolver.getType(uri)

        val normalizedContentType =
            normalizeContentType(
                contentType =
                    rawContentType,
            )

        val inferredContentType =
            inferContentTypeFromFileName(
                fileName =
                    queriedFileName,
            )

        val finalContentType =
            when {
                normalizedContentType in
                        SUPPORTED_CONTENT_TYPES -> {
                    normalizedContentType
                }

                inferredContentType in
                        SUPPORTED_CONTENT_TYPES -> {
                    inferredContentType
                }

                else -> {
                    null
                }
            }
                ?: throw IllegalArgumentException(
                    "仅支持 JPEG、PNG、WEBP 和 BMP 图片。",
                )

        val finalFileName =
            queriedFileName
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: createDefaultFileName(
                    contentType =
                        finalContentType,
                )

        val imageBytes =
            contentResolver
                .openInputStream(uri)
                ?.use {
                        inputStream ->

                    readBytesWithLimit(
                        inputStream =
                            inputStream,
                    )
                }
                ?: throw IOException(
                    "无法打开所选择的图片。",
                )

        if (imageBytes.isEmpty()) {
            throw IOException(
                "选择的图片内容为空。",
            )
        }

        val previewBitmap =
            decodePreviewBitmap(
                imageBytes =
                    imageBytes,
            )

        return SelectedImagePayload(
            bytes = imageBytes,
            fileName = finalFileName,
            contentType =
                finalContentType,
            previewBitmap =
                previewBitmap,
        )
    }

    /**
     * 查询系统提供的文件名。
     */
    private fun queryFileName(
        uri: Uri,
    ): String? {
        val contentResolver =
            getApplication<Application>()
                .contentResolver

        val projection =
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
            )

        return contentResolver
            .query(
                uri,
                projection,
                null,
                null,
                null,
            )
            ?.use {
                    cursor ->

                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val columnIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME,
                    )

                if (columnIndex < 0) {
                    null
                } else {
                    cursor.getString(
                        columnIndex,
                    )
                }
            }
    }

    /**
     * 查询系统提供的文件大小。
     *
     * 某些内容提供者可能不返回大小，
     * 所以返回值允许为 null。
     */
    private fun queryFileSize(
        uri: Uri,
    ): Long? {
        val contentResolver =
            getApplication<Application>()
                .contentResolver

        val projection =
            arrayOf(
                OpenableColumns.SIZE,
            )

        return contentResolver
            .query(
                uri,
                projection,
                null,
                null,
                null,
            )
            ?.use {
                    cursor ->

                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val columnIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE,
                    )

                if (
                    columnIndex < 0 ||
                    cursor.isNull(columnIndex)
                ) {
                    null
                } else {
                    cursor.getLong(
                        columnIndex,
                    )
                }
            }
    }

    /**
     * 读取图片，但不允许超过后端限制。
     *
     * 即使系统没有提供文件大小，
     * 这里仍然能够阻止读取超大文件。
     */
    private fun readBytesWithLimit(
        inputStream: InputStream,
    ): ByteArray {
        val outputStream =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(8 * 1024)

        var totalBytes = 0

        while (true) {
            val readCount =
                inputStream.read(buffer)

            if (readCount < 0) {
                break
            }

            totalBytes += readCount

            if (
                totalBytes >
                MAX_IMAGE_BYTES
            ) {
                throw IllegalArgumentException(
                    "图片不能超过 10 MB。",
                )
            }

            outputStream.write(
                buffer,
                0,
                readCount,
            )
        }

        return outputStream.toByteArray()
    }

    /**
     * 生成用于页面显示的缩略图。
     *
     * 最大边约为 1200 像素，
     * 避免直接解码超大原图。
     */
    private fun decodePreviewBitmap(
        imageBytes: ByteArray,
    ): Bitmap? {
        val boundsOptions =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            boundsOptions,
        )

        if (
            boundsOptions.outWidth <= 0 ||
            boundsOptions.outHeight <= 0
        ) {
            return null
        }

        var sampleSize = 1

        while (
            boundsOptions.outWidth /
            sampleSize >
            PREVIEW_MAX_SIZE ||
            boundsOptions.outHeight /
            sampleSize >
            PREVIEW_MAX_SIZE
        ) {
            sampleSize *= 2
        }

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }

        return BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            decodeOptions,
        )
    }

    /**
     * 规范化系统返回的 MIME 类型。
     */
    private fun normalizeContentType(
        contentType: String?,
    ): String? {
        val cleaned =
            contentType
                ?.substringBefore(";")
                ?.trim()
                ?.lowercase()
                ?: return null

        return when (cleaned) {
            "image/jpg",
            "image/pjpeg" -> {
                "image/jpeg"
            }

            "image/x-png" -> {
                "image/png"
            }

            "image/x-bmp",
            "image/x-ms-bmp" -> {
                "image/bmp"
            }

            else -> {
                cleaned
            }
        }
    }

    /**
     * 根据文件扩展名推断 MIME 类型。
     */
    private fun inferContentTypeFromFileName(
        fileName: String?,
    ): String? {
        val extension =
            fileName
                ?.substringAfterLast(
                    delimiter = ".",
                    missingDelimiterValue = "",
                )
                ?.lowercase()

        return when (extension) {
            "jpg",
            "jpeg" -> {
                "image/jpeg"
            }

            "png" -> {
                "image/png"
            }

            "webp" -> {
                "image/webp"
            }

            "bmp" -> {
                "image/bmp"
            }

            else -> {
                null
            }
        }
    }

    /**
     * 系统没有返回文件名时生成默认文件名。
     */
    private fun createDefaultFileName(
        contentType: String,
    ): String {
        val extension =
            when (contentType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/bmp" -> "bmp"
                else -> "image"
            }

        return "chat_image.$extension"
    }

    private fun showImageReadError(
        message: String,
    ) {
        selectedImage = null

        _uiState.value =
            ImageRiskUiState(
                errorMessage = message,
            )
    }

    private fun showError(
        message: String,
    ) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
            )
        }
    }

    /**
     * ViewModel 内部保存的原始图片。
     */
    private data class SelectedImagePayload(
        val bytes: ByteArray,
        val fileName: String,
        val contentType: String,
        val previewBitmap: Bitmap?,
    )

    private companion object {

        /**
         * 后端最大允许 10 MB。
         */
        const val MAX_IMAGE_BYTES =
            10L * 1024L * 1024L

        /**
         * 预览图最大边长。
         */
        const val PREVIEW_MAX_SIZE =
            1200

        val SUPPORTED_CONTENT_TYPES =
            setOf(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/bmp",
            )
    }
}