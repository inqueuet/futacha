package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal fun normalizeManualSaveInputValue(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return MANUAL_SAVE_DIRECTORY
    if (trimmed.startsWith("/")) return trimmed

    val lower = trimmed.lowercase()
    return when {
        lower == "download" || lower == "downloads" -> "Download"
        lower.startsWith("download/") || lower.startsWith("downloads/") -> "Download/${trimmed.substringAfter('/')}"
        lower == "documents" -> "Documents"
        lower.startsWith("documents/") -> "Documents/${trimmed.substringAfter('/')}"
        else -> trimmed
    }
}

internal fun resolveFallbackManualSavePathValue(manualSaveDir: String): String {
    val normalized = normalizeManualSaveInputValue(manualSaveDir)
    if (normalized.startsWith("/")) return normalized

    val lower = normalized.lowercase()
    return when {
        lower == "download" || lower == "downloads" -> "Download/futacha/$MANUAL_SAVE_DIRECTORY"
        lower.startsWith("download/") || lower.startsWith("downloads/") -> "Download/${normalized.substringAfter('/')}"
        lower == "documents" -> "$DEFAULT_MANUAL_SAVE_ROOT/futacha/$MANUAL_SAVE_DIRECTORY"
        lower.startsWith("documents/") -> "$DEFAULT_MANUAL_SAVE_ROOT/${normalized.substringAfter('/')}"
        else -> "$DEFAULT_MANUAL_SAVE_ROOT/futacha/$normalized"
    }
}

internal fun buildSaveDestinationModeLabelValue(
    selection: SaveDirectorySelection,
    isAndroidPlatform: Boolean
): String {
    return when (selection) {
        SaveDirectorySelection.MANUAL_INPUT -> "手入力の保存先"
        SaveDirectorySelection.PICKER ->
            if (isAndroidPlatform) "選択フォルダへの保存先" else "選択フォルダの保存先"
    }
}

internal fun buildSaveDestinationHintValue(
    selection: SaveDirectorySelection,
    isAndroidPlatform: Boolean
): String {
    return when (selection) {
        SaveDirectorySelection.MANUAL_INPUT ->
            "この場所にスレ保存と画像・動画の単体保存をまとめて保存します。"
        SaveDirectorySelection.PICKER ->
            if (isAndroidPlatform) {
                "SAF で選んだフォルダ配下に保存します。画像・動画の単体保存も同じフォルダ系統です。"
            } else {
                "選んだフォルダ配下に保存します。画像・動画の単体保存も同じフォルダ系統です。"
            }
    }
}

internal fun buildDisplayedSavePathValue(
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?,
    relativePath: String
): String {
    val normalizedRelativePath = relativePath.trim().trimStart('/')
    if (normalizedRelativePath.isBlank()) {
        return resolvedManualSaveDirectory ?: manualSaveDirectory
    }
    val normalizedBase = (resolvedManualSaveDirectory
        ?: (manualSaveLocation as? SaveLocation.Path)?.path
        ?: manualSaveDirectory)
        .trim()
        .trimEnd('/')
    return when (manualSaveLocation) {
        is SaveLocation.TreeUri, is SaveLocation.Bookmark -> "選択フォルダ/$normalizedRelativePath"
        else -> "$normalizedBase/$normalizedRelativePath"
    }
}
