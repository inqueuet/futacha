package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal const val FUTABER_MAX_TABS = 50

/**
 * A tab is only a registered (board, thread) key: the title, thumbnail and reply count shown
 * come from the shared history, so no thread data is copied. Tabs exist only when the user
 * adds them; opening a thread never adds one.
 */
internal data class FutaberTabKey(val boardId: String, val threadId: String)

private const val KEY_SEPARATOR = '\n'

internal fun encodeFutaberTabs(keys: List<FutaberTabKey>): String =
    Json.encodeToString(
        ListSerializer(String.serializer()),
        keys.map { "${it.boardId}$KEY_SEPARATOR${it.threadId}" }
    )

/** A damaged or unknown stored value reads as no tabs rather than failing. */
internal fun decodeFutaberTabs(stored: String?): List<FutaberTabKey> {
    if (stored.isNullOrBlank()) return emptyList()
    val raw = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), stored) }
        .getOrNull() ?: return emptyList()
    return raw.mapNotNull { entry ->
        val parts = entry.split(KEY_SEPARATOR)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) null
        else FutaberTabKey(parts[0], parts[1])
    }.distinct().take(FUTABER_MAX_TABS)
}

/** Adds the tab at the end, or removes it when already registered. A full list refuses new tabs. */
internal fun futaberToggleTab(keys: List<FutaberTabKey>, key: FutaberTabKey): List<FutaberTabKey> =
    when {
        key in keys -> keys - key
        keys.size >= FUTABER_MAX_TABS -> keys
        else -> keys + key
    }

/** What a press of "タブに追加" / "タブから外す" does to [keys]; a full list is the one case that changes nothing. */
internal enum class FutaberTabToggleOutcome { Added, Removed, Full }

internal fun futaberTabToggleOutcome(keys: List<FutaberTabKey>, key: FutaberTabKey): FutaberTabToggleOutcome =
    when {
        key in keys -> FutaberTabToggleOutcome.Removed
        keys.size >= FUTABER_MAX_TABS -> FutaberTabToggleOutcome.Full
        else -> FutaberTabToggleOutcome.Added
    }

/** What the person is told when the tab list is full and "タブに追加" has nothing to add to. */
internal const val FUTABER_TAB_LIMIT_NOTICE = "タブは${FUTABER_MAX_TABS}件までです。不要なタブを閉じてから追加してください"

internal fun futaberRemoveTab(keys: List<FutaberTabKey>, key: FutaberTabKey): List<FutaberTabKey> = keys - key

/** The tab itself and everything before it stay; the tabs to its right go. */
internal fun futaberRemoveTabsToTheRight(keys: List<FutaberTabKey>, key: FutaberTabKey): List<FutaberTabKey> {
    val index = keys.indexOf(key)
    return if (index < 0) keys else keys.take(index + 1)
}

/** Only [key] stays (it stays only if it was a tab). */
internal fun futaberKeepOnlyTab(keys: List<FutaberTabKey>, key: FutaberTabKey): List<FutaberTabKey> =
    if (key in keys) listOf(key) else keys

/** The tabs of threads that fell off the board are dropped. */
internal fun futaberRemoveFallenTabs(keys: List<FutaberTabKey>, fallen: Set<FutaberTabKey>): List<FutaberTabKey> =
    keys.filterNot { it in fallen }

/**
 * Drops the keys whose thread is no longer in the shared history or whose board was removed,
 * so deleting a thread cleans its tab up. [keep] is never dropped: the thread being opened
 * may not have reached the history yet.
 */
internal fun futaberPruneTabs(
    keys: List<FutaberTabKey>,
    history: List<ThreadHistoryEntry>,
    boards: List<BoardSummary>,
    keep: FutaberTabKey? = null
): List<FutaberTabKey> {
    val boardIds = boards.mapTo(HashSet()) { it.id }
    val historyKeys = history.mapTo(HashSet()) { FutaberTabKey(it.boardId, it.threadId) }
    return keys.filter { key -> key == keep || (key.boardId in boardIds && key in historyKeys) }
}

/** What one tab shows. */
internal data class FutaberTabView(
    val key: FutaberTabKey,
    val title: String,
    val thumbnailUrl: String,
    val replyCount: Int,
    val boardName: String = "",
    /** The thread fell off the board (the refresh found it gone). */
    val fallen: Boolean = false
)

internal fun futaberTabViews(keys: List<FutaberTabKey>, history: List<ThreadHistoryEntry>): List<FutaberTabView> {
    val byKey = history.associateBy { FutaberTabKey(it.boardId, it.threadId) }
    return keys.mapNotNull { key ->
        val entry = byKey[key] ?: return@mapNotNull null
        FutaberTabView(
            key, entry.title.ifBlank { "(無題)" }, entry.titleImageUrl, entry.replyCount, entry.boardName,
            fallen = entry.isAutoRefreshDisabled
        )
    }
}

/** The thread to open for a history row, or null when its board is no longer registered. */
internal fun futaberRefForHistory(entry: ThreadHistoryEntry, boards: List<BoardSummary>): FutaberThreadRef? {
    val board = boards.firstOrNull { it.id == entry.boardId }
        ?: boards.firstOrNull { entry.boardId.isBlank() && it.url == entry.boardUrl }
        ?: return null
    return FutaberThreadRef(board.id, entry.threadId, entry.title.ifBlank { "(無題)" }, entry.titleImageUrl, entry.replyCount)
}

/** How many closed tabs "閉じたタブを元に戻す" remembers. */
internal const val FUTABER_MAX_CLOSED_TABS = 20

/**
 * The closed tabs that can come back: still not open, with their thread in the history and their board registered.
 * [closed] is the latest first; they return in the order they were opened (oldest first).
 */
internal fun futaberRestorableTabs(
    closed: List<FutaberTabKey>,
    open: List<FutaberTabKey>,
    history: List<ThreadHistoryEntry>,
    boards: List<BoardSummary>
): List<FutaberTabKey> =
    futaberPruneTabs(closed.filter { it !in open }, history, boards).asReversed()

/** The tabs after [restorable] were put back, and which of them were actually written (a full list takes only some). */
internal data class FutaberTabRestore(val tabs: List<FutaberTabKey>, val restored: List<FutaberTabKey>)

internal fun futaberRestoreTabs(open: List<FutaberTabKey>, restorable: List<FutaberTabKey>): FutaberTabRestore {
    val tabs = (open + restorable.filter { it !in open }).distinct().take(FUTABER_MAX_TABS)
    return FutaberTabRestore(tabs, restorable.filter { it in tabs && it !in open })
}

/**
 * The closed-tab memory after the tabs [removed] left the list, now [next] (the latest closed first, at most
 * [FUTABER_MAX_CLOSED_TABS]); a key that is open again is not "closed".
 */
internal fun futaberRememberClosedTabs(
    closed: List<FutaberTabKey>,
    removed: List<FutaberTabKey>,
    next: List<FutaberTabKey>
): List<FutaberTabKey> =
    (removed.asReversed() + closed).distinct().filter { it !in next }.take(FUTABER_MAX_CLOSED_TABS)

/**
 * Key of one history row of the manage panel. An old row may have no board id, so the address is part of the key; the
 * rows of [futaberDistinctHistoryRows] never share one.
 */
internal fun futaberHistoryRowKey(entry: ThreadHistoryEntry): String =
    "${entry.boardId}\n${entry.boardUrl}\n${entry.threadId}"

/** [rows] without a second row of the same key (a lazy list refuses two items with one key). */
internal fun futaberDistinctHistoryRows(rows: List<ThreadHistoryEntry>): List<ThreadHistoryEntry> =
    rows.distinctBy(::futaberHistoryRowKey)
