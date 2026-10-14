package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.canonicalThreadSearchText

/**
 * タブ・ドロワー（原作 TabsProvider・Drawer。docs/ftutter-mode-research-round6-2026-10-13.md §3）。
 *
 * タブはスレッドURLの列（`compat.ftutter.tabs`、改行区切り）と、開いているURL（`compat.ftutter.opening`）だけを持つ。
 * 題名・サムネ・件数は共有の履歴から引く。他モードのタブには書かない。
 */
internal data class FtutterTabs(val urls: List<String>, val opening: String)

internal fun parseFtutterTabs(p: Map<String, String>): FtutterTabs {
    val urls = decodeFtutterList(p[FtutterKeys.TABS]).orEmpty().map { ftutterCanonicalThreadUrl(it) }.filter { it.isNotBlank() }.distinct()
    val opening = p[FtutterKeys.OPENING]?.let { ftutterCanonicalThreadUrl(it) }.orEmpty()
    return FtutterTabs(urls, if (opening in urls) opening else urls.firstOrNull().orEmpty())
}

/** 開いたスレッドは、まだ無ければ先頭へ入れ、あれば位置を変えない（原作の`pushAndOpen`）。 */
internal fun ftutterPushTab(urls: List<String>, url: String): List<String> =
    if (url in urls) urls else listOf(url) + urls

/** 閉じたタブ（メモリだけ。件数の上限なし、再起動で消える）。 */
internal data class FtutterClosedTab(val url: String, val index: Int)

internal data class FtutterCloseResult(val urls: List<String>, val closed: List<FtutterClosedTab>)

/** 1つ閉じる。閉じた位置を覚える。 */
internal fun ftutterCloseTab(urls: List<String>, url: String): FtutterCloseResult {
    val index = urls.indexOf(url)
    if (index < 0) return FtutterCloseResult(urls, emptyList())
    return FtutterCloseResult(urls - url, listOf(FtutterClosedTab(url, index)))
}

/**
 * 一括で閉じる（全て・これより下／右・落ちた）。保護したタブは確認なしで残す。閉じる順は並びの先頭から
 * （原作どおり。記録する位置は閉じた時点の位置なので、Undoを繰り返すと元の順に戻る）。
 */
internal fun ftutterCloseMany(urls: List<String>, targets: List<String>, protected: Set<String>): FtutterCloseResult {
    var current = urls
    val closed = mutableListOf<FtutterClosedTab>()
    targets.filter { it !in protected }.forEach { url ->
        val result = ftutterCloseTab(current, url)
        current = result.urls
        closed += result.closed
    }
    return FtutterCloseResult(current, closed)
}

/** 最後に閉じたものから戻す。位置は min(元の位置, 今の件数)。既にあれば何もしない。 */
internal fun ftutterRestoreTab(urls: List<String>, closed: FtutterClosedTab): List<String> =
    if (closed.url in urls) urls else urls.toMutableList().apply { add(minOf(closed.index, size), closed.url) }

/**
 * 「これより下（タブ帯からは右）をすべて閉じる」の対象。ドロワーで「タブ（更新順）」を見ている時は、その並びで後ろのもの。
 */
internal fun ftutterTabsAfter(order: List<String>, url: String): List<String> {
    val at = order.indexOf(url)
    return if (at < 0) emptyList() else order.drop(at + 1)
}

/** 保護したスレッドのURL（`compat.ftutter.protected`）。 */
internal fun parseFtutterProtected(p: Map<String, String>): Set<String> =
    decodeFtutterList(p[FtutterKeys.PROTECTED]).orEmpty().map { ftutterCanonicalThreadUrl(it) }.toSet()

/** 起動時、履歴に無いタブを捨てる（原作。保護したスレッドは履歴にあるので残る）。 */
internal fun ftutterPruneTabs(urls: List<String>, historyUrls: Set<String>): List<String> = urls.filter { it in historyUrls }

// ---- 履歴との対応 ----

/** 履歴の行のスレッドURL（ふたちゃ・ふたばー風は`boardUrl`にスレッド自身のURLを入れる）。 */
internal fun ftutterHistoryThreadUrl(entry: ThreadHistoryEntry, boards: List<BoardSummary>): String? {
    if (entry.boardUrl.contains("/res/")) return ftutterCanonicalThreadUrl(entry.boardUrl)
    val board = boards.firstOrNull { it.id == entry.boardId } ?: return null
    return ftutterCanonicalThreadUrl(ftutterThreadUrl(board, entry.threadId))
}

internal fun ftutterHistoryByUrl(history: List<ThreadHistoryEntry>, boards: List<BoardSummary>): Map<String, ThreadHistoryEntry> {
    val map = LinkedHashMap<String, ThreadHistoryEntry>()
    history.forEach { entry -> ftutterHistoryThreadUrl(entry, boards)?.let { url -> if (url !in map) map[url] = entry } }
    return map
}

// ---- ドロワー ----

internal enum class FtutterDrawerContent(val stored: Int, val label: String) {
    HISTORY(0, "履歴"), TABS(1, "タブ"), TABS_BY_ACCESS(2, "タブ（更新順）");

    companion object {
        fun fromStored(value: Int?): FtutterDrawerContent = entries.firstOrNull { it.stored == value } ?: HISTORY
    }
}

/** ドロワーに並べるURL。「タブ（更新順）」は履歴の並び（最後に開いた順）でタブだけを残したもの。 */
internal fun ftutterDrawerUrls(content: FtutterDrawerContent, historyUrls: List<String>, tabs: List<String>): List<String> = when (content) {
    FtutterDrawerContent.HISTORY -> historyUrls
    FtutterDrawerContent.TABS -> tabs
    FtutterDrawerContent.TABS_BY_ACCESS -> historyUrls.filter { it in tabs } + tabs.filter { it !in historyUrls }
}

/** ドロワー・タブの数字：返信の数（OPを除く）と未読（0以下なら出さない）。 */
internal data class FtutterTabNumbers(val replies: Int, val unread: Int?)

internal fun ftutterTabNumbers(entry: ThreadHistoryEntry?, storedRead: Int?): FtutterTabNumbers {
    val replies = ((entry?.replyCount ?: 1) - 1).coerceAtLeast(0)
    val unread = storedRead?.let { replies - it }?.takeIf { it > 0 }
    return FtutterTabNumbers(replies, unread)
}

internal data class FtutterDrawerFilter(val unreadOnly: Boolean = false, val writtenOnly: Boolean = false, val query: String = "")

/**
 * 未読（返信の数−既読 > 0）、書込（自分の書き込みがある）、題名の部分一致。題名の検索は全角半角・かなの種類を同一視する
 * （カタログ・スレの検索と同じ。原作は正規化しない）。履歴の無いURLは題名で探せないので、検索中は除く（原作どおり）。
 */
internal fun ftutterFilterDrawer(
    urls: List<String>,
    entries: Map<String, ThreadHistoryEntry>,
    readCounts: Map<String, Int>,
    writtenUrls: Set<String>,
    filter: FtutterDrawerFilter
): List<String> {
    val wanted = canonicalThreadSearchText(filter.query.trim().lowercase())
    return urls.filter { url ->
        val entry = entries[url]
        if (filter.unreadOnly) {
            if (entry == null || ftutterTabNumbers(entry, readCounts[url]).unread == null) return@filter false
        }
        if (filter.writtenOnly && url !in writtenUrls && entry?.hasSelfPost != true) return@filter false
        if (wanted.isNotEmpty()) {
            if (entry == null || !canonicalThreadSearchText(entry.title.lowercase()).contains(wanted)) return@filter false
        }
        true
    }
}

/** 行スワイプの向き（原作の表示「左：閉じる　右：保護」は背景の位置。既定は右へ動かすと閉じる）。 */
internal enum class FtutterDismissDirection(val stored: Int, val label: String) {
    CLOSE_TO_RIGHT(0, "右へスワイプで閉じる・左で保護"),
    CLOSE_TO_LEFT(1, "左へスワイプで閉じる・右で保護"),
    NONE(3, "無効");

    companion object {
        fun fromStored(value: Int?): FtutterDismissDirection = entries.firstOrNull { it.stored == value } ?: CLOSE_TO_RIGHT
    }
}

/** ドロワーの位置（左／右／両方／無効。既定は左）。両方は左右どちらの端からも開く。 */
internal enum class FtutterDrawerSide(val stored: Int) {
    LEFT(0), RIGHT(1), BOTH(2), NONE(3);

    companion object {
        fun fromStored(value: Int?): FtutterDrawerSide = entries.firstOrNull { it.stored == value } ?: LEFT
    }
}

internal data class FtutterTabSettings(
    val showTab: Boolean = false,
    val tabOnTop: Boolean = false,
    val tabHeight: Int = 40,
    val tabWidth: Int = 60,
    val backWhenNoTab: Boolean = true,
    val drawerSide: FtutterDrawerSide = FtutterDrawerSide.LEFT,
    val drawerOpenByDrag: Boolean = true,
    val dismissDirection: FtutterDismissDirection = FtutterDismissDirection.CLOSE_TO_RIGHT,
    val drawerContent: FtutterDrawerContent = FtutterDrawerContent.HISTORY
)

private val TAB_SIZE_RANGE = 10..200

internal fun parseFtutterTabSettings(p: Map<String, String>) = FtutterTabSettings(
    showTab = p.ftutterBool(FtutterKeys.SHOW_TAB, false),
    tabOnTop = p.ftutterBool(FtutterKeys.TAB_ON_TOP, false),
    tabHeight = p.ftutterInt(FtutterKeys.TAB_HEIGHT, 40, TAB_SIZE_RANGE),
    tabWidth = p.ftutterInt(FtutterKeys.TAB_WIDTH, 60, TAB_SIZE_RANGE),
    backWhenNoTab = p.ftutterBool(FtutterKeys.BACK_WHEN_NO_TAB, true),
    drawerSide = FtutterDrawerSide.fromStored(p[FtutterKeys.DRAWER_SIDE]?.trim()?.toIntOrNull()),
    drawerOpenByDrag = p.ftutterBool(FtutterKeys.DRAWER_OPEN_BY_DRAG, true),
    dismissDirection = FtutterDismissDirection.fromStored(p[FtutterKeys.TAB_DISMISS_DIRECTION]?.trim()?.toIntOrNull()),
    drawerContent = FtutterDrawerContent.fromStored(p[FtutterKeys.DRAWER_CONTENT]?.trim()?.toIntOrNull())
)

/** 開いているタブを閉じた時に次に見せるタブ：同じ位置（末尾を越えれば1つ前）。無ければ null。 */
internal fun ftutterNextOpening(before: List<String>, after: List<String>, closed: String): String? {
    if (after.isEmpty()) return null
    val at = before.indexOf(closed).coerceAtLeast(0)
    return after[minOf(at, after.lastIndex)]
}
