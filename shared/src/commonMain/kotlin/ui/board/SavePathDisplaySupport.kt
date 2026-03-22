package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.isDefaultManualSaveRoot
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal enum class ManualSaveInputKind {
    ABSOLUTE,
    DOWNLOAD,
    DOCUMENTS,
    RELATIVE
}

internal data class ManualSaveInputResolution(
    val normalizedInput: String,
    val kind: ManualSaveInputKind,
    val relativePath: String? = null
)

internal fun resolveManualSaveInputResolution(raw: String): ManualSaveInputResolution {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return ManualSaveInputResolution(
            normalizedInput = MANUAL_SAVE_DIRECTORY,
            kind = ManualSaveInputKind.RELATIVE,
            relativePath = MANUAL_SAVE_DIRECTORY
        )
    }
    if (trimmed.startsWith("/")) {
        return ManualSaveInputResolution(
            normalizedInput = trimmed,
            kind = ManualSaveInputKind.ABSOLUTE
        )
    }

    val lower = trimmed.lowercase()
    return when {
        lower == "download" || lower == "downloads" -> {
            ManualSaveInputResolution(
                normalizedInput = "Download",
                kind = ManualSaveInputKind.DOWNLOAD
            )
        }
        lower.startsWith("download/") || lower.startsWith("downloads/") -> {
            val suffix = trimmed.substringAfter('/')
            ManualSaveInputResolution(
                normalizedInput = "Download/$suffix",
                kind = ManualSaveInputKind.DOWNLOAD,
                relativePath = suffix
            )
        }
        lower == "documents" -> {
            ManualSaveInputResolution(
                normalizedInput = "Documents",
                kind = ManualSaveInputKind.DOCUMENTS
            )
        }
        lower.startsWith("documents/") -> {
            val suffix = trimmed.substringAfter('/')
            ManualSaveInputResolution(
                normalizedInput = "Documents/$suffix",
                kind = ManualSaveInputKind.DOCUMENTS,
                relativePath = suffix
            )
        }
        else -> {
            ManualSaveInputResolution(
                normalizedInput = trimmed,
                kind = ManualSaveInputKind.RELATIVE,
                relativePath = trimmed
            )
        }
    }
}

internal fun normalizeManualSaveInputValue(raw: String): String {
    return resolveManualSaveInputResolution(raw).normalizedInput
}

internal fun resolveFallbackManualSavePathValue(manualSaveDir: String): String {
    val resolution = resolveManualSaveInputResolution(manualSaveDir)
    return when (resolution.kind) {
        ManualSaveInputKind.ABSOLUTE -> resolution.normalizedInput
        ManualSaveInputKind.DOWNLOAD ->
            resolution.relativePath?.let { "Download/$it" }
                ?: "Download/futacha/$MANUAL_SAVE_DIRECTORY"
        ManualSaveInputKind.DOCUMENTS ->
            resolution.relativePath?.let { "$DEFAULT_MANUAL_SAVE_ROOT/$it" }
                ?: "$DEFAULT_MANUAL_SAVE_ROOT/futacha/$MANUAL_SAVE_DIRECTORY"
        ManualSaveInputKind.RELATIVE ->
            "$DEFAULT_MANUAL_SAVE_ROOT/futacha/${resolution.relativePath ?: resolution.normalizedInput}"
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

internal fun resolveDefaultAndroidSaveWarningText(
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    saveDirectorySelection: SaveDirectorySelection,
    isAndroidPlatform: Boolean
): String? {
    if (!isAndroidPlatform || saveDirectorySelection != SaveDirectorySelection.MANUAL_INPUT) {
        return null
    }
    if (manualSaveLocation !is SaveLocation.Path || !isDefaultManualSaveRoot(manualSaveDirectory)) {
        return null
    }
    return "Android のデフォルト保存先はアプリ専用の Documents 系領域に解決され、端末上で見失いやすい状態です。保存前に「ファイラーで選ぶ」か、手入力で Download など分かる場所へ変更してください。"
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
