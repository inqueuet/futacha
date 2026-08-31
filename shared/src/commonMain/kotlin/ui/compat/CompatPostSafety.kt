package com.valoser.futacha.shared.ui.compat

private val compatImgBoardUrl = Regex("^https?://img\\.2chan\\.net/b/", RegexOption.IGNORE_CASE)
private val compatMayBoardUrl = Regex("^https?://may\\.2chan\\.net/b/", RegexOption.IGNORE_CASE)
private val compatImgRoleWord = Regex("としあき|スレあき")
private val compatEmptyJapaneseQuote = Regex("「[\\s　]*」")

internal fun isCompatImgBoard(boardUrl: String): Boolean = compatImgBoardUrl.containsMatchIn(boardUrl)

/**
 * Optional destination-specific confirmation requested by the compatibility
 * report. The warning is deliberately opt-in because ordinary quoted text can
 * contain these words in legitimate discussions.
 */
internal fun compatPostDestinationWarning(
    boardUrl: String,
    comment: String,
    enabled: Boolean
): String? {
    if (!enabled) return null
    val warnings = buildList {
        if (compatImgBoardUrl.containsMatchIn(boardUrl) && compatImgRoleWord.containsMatchIn(comment)) {
            add("img板で「としあき」「スレあき」を含む投稿です。投稿先を確認してください。")
        }
        if (compatMayBoardUrl.containsMatchIn(boardUrl) && compatEmptyJapaneseQuote.containsMatchIn(comment)) {
            add("may板で「」を含む投稿です。投稿先を確認してください。")
        }
    }
    return warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
}
