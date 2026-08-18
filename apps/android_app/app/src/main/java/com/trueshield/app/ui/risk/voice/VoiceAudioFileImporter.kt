package com.trueshield.app.ui.risk.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

private const val MAX_IMPORTED_AUDIO_BYTES = 20L * 1024L * 1024L
private const val MIN_IMPORTED_AUDIO_BYTES = 256L
private const val IMPORT_ROOT_DIRECTORY = "voice-imports"

/**
 * 从 Android 系统文件选择器导入一份音频副本到应用缓存目录。
 *
 * 只复制用户本次选择的文件，不读取整个存储空间，因此不需要申请
 * READ_MEDIA_AUDIO 或传统外部存储权限。上传成功、清空页面或 ViewModel
 * 销毁后，该缓存副本会被删除；用户原文件不会被修改。
 */
object VoiceAudioFileImporter {

    private val allowedExtensions = setOf(
        "m4a",
        "mp4",
        "aac",
        "mp3",
        "wav",
        "ogg",
    )

    private val extensionByMimeType = mapOf(
        "audio/mp4" to "m4a",
        "audio/m4a" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/aac" to "aac",
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/ogg" to "ogg",
    )

    fun import(
        context: Context,
        uri: Uri,
    ): ImportedVoiceAudio {
        val resolver = context.contentResolver
        val metadata = queryMetadata(context, uri)
        val mimeType = resolver.getType(uri)
            ?.trim()
            ?.lowercase()

        if (
            metadata.sizeBytes != null &&
            metadata.sizeBytes > MAX_IMPORTED_AUDIO_BYTES
        ) {
            error("录音文件不能超过 20 MB。")
        }

        val extension = resolveExtension(
            displayName = metadata.displayName,
            mimeType = mimeType,
        )
        val safeDisplayName = sanitizeDisplayName(
            rawName = metadata.displayName,
            extension = extension,
        )

        val importRoot = File(
            context.cacheDir,
            IMPORT_ROOT_DIRECTORY,
        ).apply { mkdirs() }
        val importDirectory = File(
            importRoot,
            UUID.randomUUID().toString(),
        ).apply { mkdirs() }
        val outputFile = File(
            importDirectory,
            safeDisplayName,
        )

        try {
            val input = resolver.openInputStream(uri)
                ?: error("无法读取所选录音，请重新选择。")

            var totalBytes = 0L
            input.use { source ->
                outputFile.outputStream().buffered().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue

                        totalBytes += read
                        if (totalBytes > MAX_IMPORTED_AUDIO_BYTES) {
                            error("录音文件不能超过 20 MB。")
                        }
                        target.write(buffer, 0, read)
                    }
                }
            }

            if (totalBytes < MIN_IMPORTED_AUDIO_BYTES) {
                error("所选录音为空或内容过短。")
            }

            return ImportedVoiceAudio(
                file = outputFile,
                displayName = safeDisplayName,
                sizeBytes = totalBytes,
            )
        } catch (exception: Exception) {
            importDirectory.deleteRecursively()
            throw exception
        }
    }

    fun isImportedFile(file: File): Boolean {
        return file.parentFile
            ?.parentFile
            ?.name == IMPORT_ROOT_DIRECTORY
    }

    fun deleteImportedFile(file: File) {
        if (!isImportedFile(file)) {
            file.delete()
            return
        }

        val importDirectory = file.parentFile
        file.delete()
        importDirectory?.deleteRecursively()
    }

    private fun resolveExtension(
        displayName: String?,
        mimeType: String?,
    ): String {
        val filenameExtension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in allowedExtensions }

        if (filenameExtension != null) {
            return filenameExtension
        }

        return extensionByMimeType[mimeType]
            ?: error(
                "暂不支持该音频格式，请选择 m4a、mp3、wav、aac、mp4 或 ogg 文件。",
            )
    }

    private fun sanitizeDisplayName(
        rawName: String?,
        extension: String,
    ): String {
        val fallback = "voice-audio.$extension"
        val original = rawName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallback

        val baseName = original
            .substringBeforeLast('.', missingDelimiterValue = original)
            .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            .trim()
            .take(80)
            .ifBlank { "voice-audio" }

        return "$baseName.$extension"
    }

    private fun queryMetadata(
        context: Context,
        uri: Uri,
    ): VoiceAudioDocumentMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null

        context.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME,
                )
                val sizeIndex = cursor.getColumnIndex(
                    OpenableColumns.SIZE,
                )

                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        return VoiceAudioDocumentMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
        )
    }
}

data class ImportedVoiceAudio(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
)

private data class VoiceAudioDocumentMetadata(
    val displayName: String?,
    val sizeBytes: Long?,
)
