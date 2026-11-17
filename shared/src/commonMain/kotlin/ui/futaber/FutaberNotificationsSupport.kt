package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.COMPAT_WATCH_ENABLED_KEY
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.compatWatchRules
import com.valoser.futacha.shared.model.BoardSummary

/** Keys under which the shared patrol keeps its results (one row per slot). */
internal const val FUTABER_WATCH_RESULT_PREFIX = "compat.watcher.result."

/**
 * Only the result rows of the stored preferences. The list is rebuilt when these change,
 * not when an unrelated setting does.
 */
internal fun futaberWatchResultSlots(preferences: Map<String, String>): Map<String, String> =
    preferences.filterKeys { it.startsWith(FUTABER_WATCH_RESULT_PREFIX) }

/** Why the list may stay empty: patrol switched off, no keyword yet, or running. */
internal fun futaberNotificationsStatus(preferences: Map<String, String>): String = when {
    preferences[COMPAT_WATCH_ENABLED_KEY] == "OFF" -> "自動巡回が無効です。キーワード管理で有効にできます"
    compatWatchRules(preferences).none { it.enabled && it.word.isNotBlank() } ->
        "キーワードが登録されていません。キーワード管理で追加できます"
    else -> "キーワードに一致したスレッド"
}

/**
 * The thread to open for a patrol result. The board is matched by the shared board key
 * (the canonical address), so a result found by another mode opens here too; null when
 * that board is not registered.
 */
internal fun futaberRefForWatchResult(result: CompatWatchResult, boards: List<BoardSummary>): FutaberThreadRef? {
    val entry = result.history
    val board = boards.firstOrNull { futaberCompatBoardKey(it) == entry.boardKey } ?: return null
    return FutaberThreadRef(board.id, entry.threadNo, entry.title.ifBlank { "(無題)" }, entry.thumbnailUrl.orEmpty(), entry.replyCount)
}

/** Second line of a result row: the word it matched, the board, and whether the thread has gone. */
internal fun futaberWatchSubtitle(result: CompatWatchResult, openable: Boolean): String =
    "「${result.keyword}」  ${result.history.boardName}" +
        (if (result.active) "" else "（落ちました）") +
        (if (openable) "" else "（板が未登録）")

/**
 * Provider IDs and internal thread URLs have separate key spaces. A malformed provider may omit/repeat an ID;
 * keep every displayed row without letting that become a duplicate LazyColumn key. The ID still governs deletion.
 */
internal fun futaberNotificationRowKeys(results: List<CompatWatchResult>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return results.mapIndexed { index, result ->
        val external = result.externalEntryKey
        val base = when {
            external == null -> "internal:url:${result.history.canonicalUrl}"
            external.isBlank() -> "external:missing:$index"
            else -> "external:id:${external.length}:$external"
        }
        val occurrence = occurrences[base] ?: 0
        occurrences[base] = occurrence + 1
        "$base:$occurrence"
    }
}
