package com.valoser.futacha.shared.service

internal const val PREVIEW_MEDIA_DIRECTORY = "preview_media"

internal fun buildSingleMediaRelativePath(
    storageId: String,
    targetSubDirectory: String,
    fileName: String
): String {
    return "$storageId/$PREVIEW_MEDIA_DIRECTORY/$targetSubDirectory/$fileName"
}
