package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.MixScreenHost
import com.valoser.futacha.shared.ui.board.firstMixIdOrCreate

internal suspend fun compatFirstMixId(store: CompatibilityStore, boards: List<CompatBoard>, nowMillis: Long, confirmMembers: suspend (List<String>) -> Boolean): com.valoser.futacha.shared.ui.board.MixOpenResult =
    firstMixIdOrCreate(store, boards.map { it.originalUrl }, nowMillis, confirmMembers)

/** The MIX screen shown over the main screen. A thread opened from it returns to it (the overlay is shown whenever the host is Main). */
@Composable
internal fun CompatMixHost(
    mixId: String,
    store: CompatibilityStore,
    repository: BoardRepository,
    boards: List<CompatBoard>,
    onExit: () -> Unit,
    onOpen: (com.valoser.futacha.shared.mix.MixEntry) -> Unit,
    httpClient: io.ktor.client.HttpClient? = null,
    onSwitch: (String) -> Unit,
    stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null
) {
    val summaries = remember(boards) { boards.map { it.toBoardSummary() } }
    MixScreenHost(mixId, store, repository, summaries, onExit, onOpen, onSwitch, stateHolder, fileSystem, stateStore = stateStore, httpClient = httpClient, modePolicy = com.valoser.futacha.shared.ui.board.MixModePolicy.COMPAT)
}

/** Unknown counts (SO) must not replace reply counts or raise the read baseline. */
internal fun buildCompatMixTab(
    entry: com.valoser.futacha.shared.mix.MixEntry,
    board: CompatBoard,
    existing: com.valoser.futacha.shared.compat.CompatTab?,
    nowMillis: Long,
    previousHistory: com.valoser.futacha.shared.compat.CompatHistoryEntry? = null
): com.valoser.futacha.shared.compat.CompatTab? {
    val parsed = com.valoser.futacha.shared.compat.canonicalizeSiteThreadUrlAsLegacy(entry.item.threadUrl) ?: return null
    val count = entry.replyCountOrNull
    val candidate = com.valoser.futacha.shared.compat.CompatTab(
        key = com.valoser.futacha.shared.compat.compatTabKey(parsed.canonicalUrl),
        canonicalUrl = parsed.canonicalUrl, originalUrl = entry.item.threadUrl,
        boardKey = board.key, boardName = board.name, threadNo = parsed.threadNo,
        title = entry.item.title.orEmpty().ifBlank { "No.${parsed.threadNo}" }, thumbnailUrl = entry.item.thumbnailUrl,
        replyCount = count ?: maxOf(existing?.replyCount ?: 0, previousHistory?.replyCount ?: 0), insertedAtEpochMillis = nowMillis, contentUpdatedAtEpochMillis = nowMillis
    )
    return com.valoser.futacha.shared.compat.mergeCompatCatalogTab(existing, candidate, markCatalogCountRead = count != null)
}

/** Read the store again at persistence time: the screen's history projection may still be loading. */
internal suspend fun persistCompatMixTab(
    store: CompatibilityStore, entry: com.valoser.futacha.shared.mix.MixEntry, board: CompatBoard, nowMillis: Long
): com.valoser.futacha.shared.compat.CompatTab? {
    val parsed = com.valoser.futacha.shared.compat.canonicalizeSiteThreadUrlAsLegacy(entry.item.threadUrl) ?: return null
    val key = com.valoser.futacha.shared.compat.compatTabKey(parsed.canonicalUrl)
    val tab = buildCompatMixTab(entry, board, store.tabs.first().firstOrNull { it.key == key }, nowMillis,
        store.history.first().firstOrNull { it.canonicalUrl == parsed.canonicalUrl }) ?: return null
    store.openTab(tab, com.valoser.futacha.shared.compat.CompatHistoryEntry(
        canonicalUrl = tab.canonicalUrl, originalUrl = tab.originalUrl, boardKey = tab.boardKey, boardName = tab.boardName,
        threadNo = tab.threadNo, title = tab.title, thumbnailUrl = tab.thumbnailUrl, replyCount = tab.replyCount,
        contentUpdatedAtEpochMillis = nowMillis, lastVisitedEpochMillis = nowMillis))
    return tab
}
