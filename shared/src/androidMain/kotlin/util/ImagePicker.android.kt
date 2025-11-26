package com.valoser.futacha.shared.util

import android.content.Context
import android.net.Uri

fun readImageDataFromUri(context: Context, uri: Uri): ImageData? {
    return try {
        // FIX: 画像サイズを事前にチェック
        val fileSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
            } else -1L
        } ?: -1L

        // 最大ファイルサイズ: 10MB
        val maxFileSize = 10 * 1024 * 1024L

        // FIX: fileSizeが不明(-1)の場合は実際に読み込んでサイズをチェック
        if (fileSize > 0 && fileSize > maxFileSize) {
            Logger.w("ImagePicker", "Image file too large: ${fileSize / 1024}KB (max: ${maxFileSize / 1024}KB)")
            return null
        }

        if (fileSize == -1L) {
            Logger.w("ImagePicker", "Unable to determine file size beforehand, will check after reading")
        }

        // FIX: InputStreamをバッファリングして読み込み、サイズ制限をチェック
        val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(chunk).also { bytesRead = it } != -1) {
                totalRead += bytesRead

                // FIX: 読み込み中にサイズ超過をチェック
                if (totalRead > maxFileSize) {
                    Logger.w("ImagePicker", "Image file exceeded size limit during read: ${totalRead / 1024}KB")
                    return null
                }

                buffer.write(chunk, 0, bytesRead)
            }

            buffer.toByteArray()
        } ?: run {
            Logger.w("ImagePicker", "Failed to open InputStream for URI: $uri")
            return null
        }

        // FIX: 最終サイズチェック
        if (bytes.size > maxFileSize) {
            Logger.w("ImagePicker", "Image file too large after reading: ${bytes.size / 1024}KB")
            return null
        }

        // Fix: Use projection to query only the column we need, instead of all columns
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        val fileName = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        } ?: "image.jpg"

        Logger.d("ImagePicker", "Successfully read image: $fileName (${bytes.size / 1024}KB)")
        ImageData(bytes, fileName)
    } catch (e: Exception) {
        // FIX: 適切なログ記録とエラーハンドリング
        when (e) {
            is java.io.FileNotFoundException -> {
                Logger.e("ImagePicker", "File not found for URI: $uri", e)
            }
            is SecurityException -> {
                Logger.e("ImagePicker", "Permission denied to access URI: $uri", e)
            }
            is OutOfMemoryError -> {
                Logger.e("ImagePicker", "Out of memory reading image from URI: $uri", e)
            }
            else -> {
                Logger.e("ImagePicker", "Failed to read image data from URI: $uri", e)
            }
        }
        null
    }
}

actual suspend fun pickImage(): ImageData? {
    // Not used on Android - handled via Compose ActivityResultContracts
    return null
}
