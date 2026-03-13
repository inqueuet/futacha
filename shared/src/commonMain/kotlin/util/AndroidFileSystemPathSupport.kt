package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY

internal fun resolveAndroidAbsolutePath(
    relativePath: String,
    appDataDirectory: String,
    privateAppDataDirectory: String,
    publicDocumentsDirectory: String,
    publicDownloadsDirectory: String
): String {
    if (relativePath.startsWith("/")) {
        return relativePath
    }

    val cleanedPath = relativePath.removePrefix("./")
    val lower = cleanedPath.lowercase()

    if (cleanedPath.startsWith("private/")) {
        val remainder = cleanedPath.removePrefix("private/").ifBlank { "" }
        return joinPath(privateAppDataDirectory, remainder)
    }

    if (cleanedPath.startsWith(AUTO_SAVE_DIRECTORY)) {
        return joinPath(privateAppDataDirectory, cleanedPath)
    }

    val isDownload = lower == "download" || lower == "downloads"
    val isDownloadSubPath = lower.startsWith("download/") || lower.startsWith("downloads/")
    val isDocuments = lower == "documents"
    val isDocumentsSubPath = lower.startsWith("documents/")

    return when {
        isDownload -> joinPath(publicDownloadsDirectory, MANUAL_SAVE_DIRECTORY)
        isDownloadSubPath -> {
            val remainder = cleanedPath.substringAfter('/').removePrefix("futacha/").ifBlank { MANUAL_SAVE_DIRECTORY }
            joinPath(publicDownloadsDirectory, remainder)
        }
        isDocuments -> joinPath(publicDocumentsDirectory, MANUAL_SAVE_DIRECTORY)
        isDocumentsSubPath -> {
            val remainder = cleanedPath.substringAfter('/').removePrefix("futacha/").ifBlank { MANUAL_SAVE_DIRECTORY }
            joinPath(publicDocumentsDirectory, remainder)
        }
        else -> joinPath(appDataDirectory, cleanedPath)
    }
}

private fun joinPath(base: String, child: String): String {
    val normalizedBase = base.trimEnd('/')
    val normalizedChild = child.trimStart('/')
    return if (normalizedChild.isBlank()) normalizedBase else "$normalizedBase/$normalizedChild"
}
