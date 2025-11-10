package com.valoser.futacha.shared.util

import android.content.Context
import android.net.Uri

fun readImageDataFromUri(context: Context, uri: Uri): ImageData? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()

        // ファイル名を取得
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val fileName = cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
        } ?: "image.jpg"

        ImageData(bytes, fileName)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

actual suspend fun pickImage(): ImageData? {
    // Not used on Android - handled via Compose ActivityResultContracts
    return null
}
