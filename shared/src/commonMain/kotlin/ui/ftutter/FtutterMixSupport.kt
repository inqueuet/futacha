package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.mix.MixEntry
import com.valoser.futacha.shared.model.normalizeCatalogSearchText

/** Apply this mode's filters without pruning its saved hidden URLs or changing the MIX's order. */
internal fun ftutterMixEntries(
    entries: List<MixEntry>, rules: List<FtutterNgRule>, hidden: Set<String>, commonWords: List<String>,
    ensureActive: () -> Unit = {}
): List<MixEntry> {
    val matchers = HashMap<String, FtutterNgMatcher>()
    val words = commonWords.map(::normalizeCatalogSearchText).filter { it.isNotEmpty() }
    return entries.filter { entry ->
        ensureActive()
        val matcher = matchers.getOrPut(entry.key.boardUrl) { FtutterNgMatcher(rules, entry.board.url) }
        val title = entry.item.title.orEmpty()
        val normalizedTitle = if (words.isEmpty()) "" else normalizeCatalogSearchText(title)
        ftutterCanonicalThreadUrl(entry.item.threadUrl) !in hidden && !matcher.hidesTitle(title, ensureActive) &&
            words.none { normalizedTitle.contains(it) }
    }
}

internal fun ftutterTabBack(current: FtutterScreen, mixId: String?, boardId: String): FtutterScreen =
    if (mixId != null && (current == FtutterScreen.Boards || (current as? FtutterScreen.Thread)?.back == FtutterScreen.Boards))
        FtutterScreen.Boards else FtutterScreen.Catalog(boardId)

internal fun ftutterShowsMixPane(screen: FtutterScreen, mixId: String?): Boolean =
    mixId != null && (screen == FtutterScreen.Boards || (screen as? FtutterScreen.Thread)?.back == FtutterScreen.Boards)
