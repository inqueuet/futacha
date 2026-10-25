package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * 閉じたタブ（メモリだけ。件数の上限なし、再起動で消える）。[entry] は閉じた時の履歴の行で、戻す時に履歴に無ければ
 * その行を戻す（L17：以前は題名もサムネイルも無い行を作り、開くまで題名の無い行が残った）。
 */
internal data class FtutterClosedTab(val url: String, val index: Int, val entry: ThreadHistoryEntry? = null)

/** 戻したタブの履歴の行（原作どおり履歴にも戻す）。閉じた時の行があれば、その題名・サムネイルで今の時刻に戻す。 */
internal fun ftutterRestoredHistoryEntry(closed: ThreadHistoryEntry?, board: BoardSummary, threadId: String, now: Long): ThreadHistoryEntry =
    closed?.takeIf { it.threadId == threadId }?.copy(lastVisitedEpochMillis = now)
        ?: com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry(
            null, board, com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(board.id, threadId, "", "", 0), now)

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

// ---- 履歴の保存数（原作 rirekiLimit。docs/ftutter-mode-research-round6-2026-10-13.md §3） ----

internal const val FTUTTER_HISTORY_LIMIT_DEFAULT = 200
private val FTUTTER_HISTORY_LIMIT_RANGE = 0..1000

/** 設定「履歴の保存数（保護したタブは除く）」。原作のバックアップの実数（"200.0"）も読む。 */
internal fun ftutterHistoryLimit(p: Map<String, String>): Int =
    p[FtutterKeys.HISTORY_LIMIT]?.trim()?.toDoubleOrNull()?.takeIf { !it.isNaN() }?.toInt()
        ?.takeIf { it in FTUTTER_HISTORY_LIMIT_RANGE } ?: FTUTTER_HISTORY_LIMIT_DEFAULT

/**
 * タブを原作の履歴の上限に合わせる。原作は、開くたびに履歴の先頭へ移し、保護していないスレッドが上限を超えると古いものを
 * 履歴から外し、起動時に履歴に無いタブを捨てる（タブは履歴の中にしか残らない）。ここでは履歴を他のモードと共有しているので、
 * 共有の履歴は消さず、タブの側で同じ結果にする。
 *
 * - 保護したタブ（[protected]）は数えずに残す。[keep]（今開いた・戻したタブ。履歴の書き込みがまだ届いていないことがある）も
 *   残し、最新として数える。
 * - 残りは履歴の並び（[historyOrder]、最後に開いた順）で新しいものから [limit] 件まで。並びは変えない。
 * - 履歴に無いタブは、[dropMissing]（起動時）なら捨てる。そうでなければ（起動中に「タブに追加」したもの等）最新として数える。
 */
internal fun ftutterLimitTabs(
    urls: List<String>,
    historyOrder: List<String>,
    protected: Set<String>,
    limit: Int,
    keep: Set<String>,
    dropMissing: Boolean
): List<String> {
    val rank = HashMap<String, Int>(historyOrder.size * 2).apply { historyOrder.forEachIndexed { i, u -> if (u !in this) put(u, i) } }
    val fixed = urls.filterTo(HashSet()) { it in protected || it in keep }
    val candidates = urls.filter { it !in fixed && (!dropMissing || it in rank) }
    val room = (limit - urls.count { it in keep && it !in protected }).coerceAtLeast(0)
    val kept = candidates.sortedBy { rank[it] ?: -1 }.take(room).toHashSet()
    return urls.filter { it in fixed || it in kept }
}

/** 上限 [limit] で閉じられるタブの数（[ftutterLimitTabs] と同じ数え方。保護したタブと開いているタブは数えない。L130）。 */
internal fun ftutterTabsClosedByLimit(tabs: FtutterTabs, protected: Set<String>, limit: Int): Int {
    val keep = setOf(tabs.opening).filter { it.isNotEmpty() }.toSet()
    return tabs.urls.size - ftutterLimitTabs(tabs.urls, emptyList(), protected, limit, keep, dropMissing = false).size
}

/**
 * 保存値の上限（[MAX_COMPAT_PREFERENCE_VALUE_CHARS]）に収める。履歴の上限を大きくした時など、収まらなければ
 * 保護していない・[keep]でないタブを古い順（履歴の並びで後ろ、履歴に無いものはその後）に外す。保護したタブだけで
 * 収まらない時は、勝手に消さずに null（呼ぶ側が知らせる）。
 */
internal fun ftutterFitTabs(
    urls: List<String>,
    historyOrder: List<String>,
    protected: Set<String>,
    keep: Set<String>,
    maxChars: Int = com.valoser.futacha.shared.compat.MAX_COMPAT_PREFERENCE_VALUE_CHARS
): List<String>? {
    var length = encodeFtutterList(urls).length
    if (length <= maxChars) return urls
    val rank = HashMap<String, Int>(historyOrder.size * 2).apply { historyOrder.forEachIndexed { i, u -> if (u !in this) put(u, i) } }
    val removable = urls.filter { it !in protected && it !in keep }.sortedByDescending { rank[it] ?: Int.MAX_VALUE }
    val dropped = HashSet<String>()
    for (url in removable) {
        if (length <= maxChars) break
        dropped += url
        // 改行1文字ぶんも一緒に減る。
        length -= url.length + 1
    }
    val fitted = urls.filter { it !in dropped }
    return fitted.takeIf { encodeFtutterList(it).length <= maxChars }
}

// ---- 履歴との対応 ----

/** 履歴の行のスレッドURL（ふたちゃ・ふたばー風は`boardUrl`にスレッド自身のURLを入れる）。 */
internal fun ftutterHistoryThreadUrl(entry: ThreadHistoryEntry, boards: List<BoardSummary>): String? =
    ftutterHistoryThreadUrl(entry) { id -> boards.firstOrNull { it.id == id } }

private inline fun ftutterHistoryThreadUrl(entry: ThreadHistoryEntry, boardOf: (String) -> BoardSummary?): String? {
    if (entry.boardUrl.contains("/res/")) return ftutterCanonicalThreadUrl(entry.boardUrl)
    val board = boardOf(entry.boardId) ?: return null
    return ftutterCanonicalThreadUrl(ftutterThreadUrl(board, entry.threadId))
}

/**
 * スレッドのURL→履歴の行（新しい順、同じスレは先の行）。履歴は他モードと共有で最大2万件あるので、板は表で引く（監査M9：
 * 以前は行ごとに板の一覧を頭から探していた）。画面では最初の1回の後はメインスレッドの外で作る（[rememberFtutterHistoryByUrl]）。
 */
internal fun ftutterHistoryByUrl(history: List<ThreadHistoryEntry>, boards: List<BoardSummary>): Map<String, ThreadHistoryEntry> {
    val boardById = HashMap<String, BoardSummary>(boards.size * 2).apply { boards.forEach { if (it.id !in this) put(it.id, it) } }
    val map = LinkedHashMap<String, ThreadHistoryEntry>()
    history.forEach { entry -> ftutterHistoryThreadUrl(entry) { boardById[it] }?.let { url -> if (url !in map) map[url] = entry } }
    return map
}

/**
 * [ftutterHistoryByUrl]を画面で持つ。最初の1回はその場で作る（起動時のタブの整理が空の表で全部のタブを閉じないように）。
 * その後に履歴・板が変わった時は解析用のスレッドで作り直し、できるまでは前の表を使う（監査M9：履歴の書き込みのたびに、
 * 画面全体が一瞬止まっていた）。
 */
@androidx.compose.runtime.Composable
internal fun rememberFtutterHistoryByUrl(history: List<ThreadHistoryEntry>, boards: List<BoardSummary>): Map<String, ThreadHistoryEntry> =
    rememberFtutterOffMain(history, boards, ::ftutterHistoryByUrl)

/**
 * [a]・[b]から[compute]で作る値。最初の1回はその場で作り、その後に[a]・[b]が変わった時は解析用のスレッドで作り直す
 * （できるまでは前の値）。履歴（最大2万件）のように大きい入力から作る表に使う（監査M9）。
 */
@androidx.compose.runtime.Composable
internal fun <A, B, T> rememberFtutterOffMain(a: A, b: B, compute: (A, B) -> T): T {
    val first = androidx.compose.runtime.remember { Triple(compute(a, b), a, b) }
    val state = androidx.compose.runtime.produceState(first.first, a, b) {
        if (first.second === a && first.third === b) return@produceState
        value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) { compute(a, b) }
    }
    return state.value
}

/** [urls]のスレッドの履歴の行（同じスレの行が複数あれば全部）。 */
internal fun ftutterHistoryEntriesFor(
    history: List<ThreadHistoryEntry>, boards: List<BoardSummary>, urls: Set<String>
): List<ThreadHistoryEntry> = history.filter { entry -> ftutterHistoryThreadUrl(entry, boards)?.let { it in urls } == true }

/** 「すべて削除」で消す履歴の行：保護したスレッド（[keep]）の行だけを残す。URLを決められない行も消す。 */
internal fun ftutterHistoryEntriesExcept(
    history: List<ThreadHistoryEntry>, boards: List<BoardSummary>, keep: Set<String>
): List<ThreadHistoryEntry> = history.filterNot { entry -> ftutterHistoryThreadUrl(entry, boards)?.let { it in keep } == true }

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
    // 履歴は最大2万件あるので、集合で調べる（再監査M7：リストのままでは数百万回の比較になった）。
    FtutterDrawerContent.TABS_BY_ACCESS -> {
        val tabSet = tabs.toHashSet()
        val historySet = historyUrls.toHashSet()
        historyUrls.filter { it in tabSet } + tabs.filter { it !in historySet }
    }
}

/**
 * ドロワーの行（再監査M7）。履歴が変わるたびに全件を作るので、画面は解析用のスレッドで呼ぶ。タブ・保護の判定は集合で行う。
 */
internal fun ftutterDrawerRows(
    content: FtutterDrawerContent,
    historyUrls: List<String>,
    tabs: List<String>,
    protected: Set<String>,
    entries: Map<String, ThreadHistoryEntry>,
    readCounts: Map<String, Int>,
    filter: FtutterDrawerFilter,
    titleKeys: Map<String, String>?
): List<FtutterTabView> {
    val tabSet = tabs.toHashSet()
    return ftutterFilterDrawer(ftutterDrawerUrls(content, historyUrls, tabs), entries, readCounts, emptySet(), filter, titleKeys).map { url ->
        val entry = entries[url]
        FtutterTabView(url, entry, ftutterTabNumbers(entry, readCounts[url]), url in protected, url in tabSet)
    }
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
    filter: FtutterDrawerFilter,
    /** 題名の正規化を先に作った表（[ftutterDrawerTitleKeys]）。無ければその場で正規化する。 */
    titleKeys: Map<String, String>? = null
): List<String> {
    val wanted = canonicalThreadSearchText(filter.query.trim().lowercase())
    return urls.filter { url ->
        val entry = entries[url]
        if (filter.unreadOnly) {
            if (entry == null || ftutterTabNumbers(entry, readCounts[url]).unread == null) return@filter false
        }
        if (filter.writtenOnly && url !in writtenUrls && entry?.hasSelfPost != true) return@filter false
        if (wanted.isNotEmpty()) {
            if (entry == null) return@filter false
            val key = titleKeys?.get(url) ?: canonicalThreadSearchText(entry.title.lowercase())
            if (!key.contains(wanted)) return@filter false
        }
        true
    }
}

/**
 * ドロワーの題名の検索に使う、正規化した題名（URL→題名）。履歴は最大2万件あるので、履歴が変わった時に1回だけ、解析用のスレッドで
 * 作る（監査M11：以前は1文字打つたびに全件を正規化し直していた）。
 */
internal fun ftutterDrawerTitleKeys(entries: Map<String, ThreadHistoryEntry>): Map<String, String> =
    entries.mapValues { (_, entry) -> canonicalThreadSearchText(entry.title.lowercase()) }

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
    val privacy: Boolean = false,
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
    privacy = p.ftutterBool(FtutterKeys.IS_PRIVACY, false),
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

internal class FtutterTabState(initial: FtutterTabs, initialProtected: Set<String>, private val save: (String, String) -> Unit) {
    var urls by mutableStateOf(initial.urls)
        private set
    var protected by mutableStateOf(initialProtected)
        private set
    val closed = mutableStateListOf<FtutterClosedTab>()

    /** 履歴の並び（最後に開いた順のスレッドURL）と、設定「履歴の保存数」。画面の側が組み立てのたびに入れる。 */
    var historyOrder: List<String> = emptyList()
    var limit: Int = FTUTTER_HISTORY_LIMIT_DEFAULT
    /** 閉じる時に、そのタブの履歴の行を覚える（L17）。画面の側が組み立てのたびに入れる。 */
    var entryOf: (String) -> ThreadHistoryEntry? = { null }

    /** 開いているタブ。上限で外さない。 */
    private var openingUrl: String = initial.opening

    // 保存は非同期で、通知は遅れて届く。自分が書いてまだ届いていない値をすべて覚えておき、それが届いた時は取り込まない
    // （監査M1。以前は最後の値だけを覚えていたので、2回続けて変えると1回目の通知で手元が巻き戻った）。
    private val writtenTabs = FtutterEchoGuard()
    private val writtenProtected = FtutterEchoGuard()

    fun sync(p: Map<String, String>) {
        // Only the value that changed is looked at (re-audit L25: a notice where only the protection changed checked the
        // tabs' stored value again, an older one while this screen's latest write was on its way, and a tab just closed
        // came back for a moment).
        val tabsRaw = p[FtutterKeys.TABS]
        if (tabsRaw != null && tabsRaw != seenTabsRaw) {
            seenTabsRaw = tabsRaw
            if (!writtenTabs.isOwnEcho(tabsRaw)) urls = parseFtutterTabs(p).urls
        }
        val protectedRaw = p[FtutterKeys.PROTECTED]
        if (protectedRaw != null && protectedRaw != seenProtectedRaw) {
            seenProtectedRaw = protectedRaw
            if (!writtenProtected.isOwnEcho(protectedRaw)) protected = parseFtutterProtected(p)
        }
    }
    private var seenTabsRaw: String? = null
    private var seenProtectedRaw: String? = null

    private fun limited(vararg keep: String, dropMissing: Boolean = false): List<String> =
        ftutterLimitTabs(urls, historyOrder, protected, limit, (keep.toList() + openingUrl).filter { it.isNotEmpty() }.toSet(), dropMissing)

    private fun persist(opening: String? = null) {
        // 保存値の上限（2万字）に入らなければ、保護していない古いタブから外す。保護したタブだけで入らない時は、そのまま
        // 保存を試み、保存の入口が「保存できませんでした」と知らせる（勝手に保護を外さない）。
        ftutterFitTabs(urls, historyOrder, protected, setOfNotNull(opening, openingUrl.takeIf { it.isNotEmpty() }))?.let { urls = it }
        val encoded = encodeFtutterList(urls)
        writtenTabs.wrote(encoded)
        save(FtutterKeys.TABS, encoded)
        opening?.let { save(FtutterKeys.OPENING, it) }
    }

    private fun persistProtected() {
        val encoded = encodeFtutterList(protected.toList())
        writtenProtected.wrote(encoded)
        save(FtutterKeys.PROTECTED, encoded)
    }

    /**
     * 履歴の保存数に合わせる（原作どおり、保護したタブは数えない）。[dropMissing]は起動時：履歴に無いタブも捨てる。
     * 外れたタブは「閉じたタブ」には入れない（原作でも戻せない）。
     */
    fun enforceLimit(dropMissing: Boolean) {
        val next = limited(dropMissing = dropMissing)
        if (next != urls) {
            urls = next
            persist()
        }
    }

    fun open(url: String) {
        openingUrl = url
        urls = ftutterPushTab(urls, url)
        urls = limited(url)
        persist(opening = url)
    }

    /** 裏でタブに足す（開かない）。 */
    fun push(url: String) {
        urls = ftutterPushTab(urls, url)
        urls = limited(url)
        persist()
    }

    /** 閉じる時は保護を外す（原作どおり。戻しても保護は戻らない）。 */
    fun close(targets: List<String>) {
        if (targets.isEmpty()) return
        val result = ftutterCloseMany(urls, targets, emptySet())
        urls = result.urls
        closed += result.closed.map { it.copy(entry = entryOf(it.url)) }
        if (targets.any { it in protected }) {
            protected = protected - targets.toSet()
            persistProtected()
        }
        persist()
    }

    fun restore(): String? = restoreTab()?.url

    fun restoreTab(): FtutterClosedTab? {
        val last = closed.removeLastOrNull() ?: return null
        openingUrl = last.url
        urls = ftutterRestoreTab(urls, last)
        urls = limited(last.url)
        persist(opening = last.url)
        return last
    }

    fun move(from: Int, to: Int) {
        if (from !in urls.indices || to !in urls.indices || from == to) return
        urls = urls.toMutableList().apply { add(to, removeAt(from)) }
        persist()
    }

    fun toggleProtect(url: String) {
        protected = if (url in protected) protected - url else protected + url
        persistProtected()
    }
}

/**
 * 自分が保存してまだ通知の届いていない値の列（古い順）。届いた値が列にあれば、それより前の分と一緒に外して「自分の書いた
 * 値」と答える。列に無い値は他から書かれた値なので、列を空にして取り込ませる。
 */
internal class FtutterEchoGuard {
    private val pending = ArrayDeque<String>()

    fun wrote(value: String) {
        pending.addLast(value)
        // 届かなかった通知（同じ値が続いて通知が省かれた等）で列が伸び続けないようにする。
        while (pending.size > FTUTTER_ECHO_PENDING_MAX) pending.removeFirst()
    }

    fun isOwnEcho(value: String): Boolean {
        val index = pending.indexOf(value)
        if (index < 0) {
            pending.clear()
            return false
        }
        repeat(index + 1) { pending.removeFirst() }
        return true
    }
}

private const val FTUTTER_ECHO_PENDING_MAX = 64

/**
 * タブのドラッグを離した時の並べ替え（元の位置, 移す位置）。位置は離した時の並び[urls]から引く。タブの幅の3分の1より動かして
 * いなければ、または動かした先が同じなら null（長押しのメニューを出す）。
 */
internal fun ftutterTabDragMove(urls: List<String>, url: String, dragOffset: Float, tabWidthPx: Float): Pair<Int, Int>? {
    val from = urls.indexOf(url)
    if (from < 0 || tabWidthPx <= 0f || kotlin.math.abs(dragOffset) <= tabWidthPx / 3) return null
    val to = (from + kotlin.math.round(dragOffset / tabWidthPx).toInt()).coerceIn(0, urls.lastIndex)
    return if (to == from) null else from to to
}

/** Limit this mode's visible history without deleting records belonging to the other modes. */
internal fun ftutterLimitedHistoryUrls(urls: List<String>, protected: Set<String>, limit: Int): List<String> {
    var ordinary = 0
    return urls.filter { it in protected || ordinary++ < limit.coerceAtLeast(0) }
}
