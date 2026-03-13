package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY

internal fun resolveIosAbsolutePath(
    relativePath: String,
    appDataDirectory: String,
    privateAppDataDirectory: String
): String {
    if (relativePath.startsWith("/")) {
        return relativePath
    }

    val usePrivateDirectory =
        relativePath.startsWith(AUTO_SAVE_DIRECTORY) || relativePath.startsWith("private/")
    val baseDir = if (usePrivateDirectory) {
        privateAppDataDirectory
    } else {
        appDataDirectory
    }
    val cleaned = if (relativePath.startsWith("private/")) {
        relativePath.removePrefix("private/").ifBlank { "" }
    } else {
        relativePath
    }

    val normalizedBase = baseDir.trimEnd('/')
    val normalizedChild = cleaned.trimStart('/')
    return if (normalizedChild.isBlank()) normalizedBase else "$normalizedBase/$normalizedChild"
}
