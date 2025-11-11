package com.valoser.futacha.shared.util

import android.content.Context
import android.net.Uri

fun readImageDataFromUri(context: Context, uri: Uri): ImageData? {
    return try {
        // Fix: Use 'use' to properly close InputStream even if an exception occurs
        val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: return null

        // Fix: Use projection to query only the column we need, instead of all columns
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        val fileName = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
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
