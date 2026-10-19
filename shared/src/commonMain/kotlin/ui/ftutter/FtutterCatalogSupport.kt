package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.futaber.futaberCatalogTitleKeys

/**
 * 原作の並び順7種。すべてふたばのサーバー側の並び（`mode=cat&sort=…`）で、アプリでは並べ替えない。
 * 保存値（`previousOrderMode`／`defaultOrderMode`）は原作と同じ番号。
 */
internal enum class FtutterOrder(val stored: Int, val label: String, val mode: CatalogMode) {
    CATALOG(0, "カタ", CatalogMode.Catalog),
    NEW(1, "新順", CatalogMode.New),
    OLD(2, "古順", CatalogMode.Old),
    MANY(3, "多順", CatalogMode.Many),
    FEW(4, "少順", CatalogMode.Few),
    MOMENTUM(5, "勢順", CatalogMode.Momentum),
    SOUDANE(6, "そ順", CatalogMode.So);

    companion object {
        /** 設定の「デフォルトの並び順」の7番目＝「前回の並び順を保存する」。 */
        const val PREVIOUS = 7
        fun fromStored(value: Int?): FtutterOrder? = entries.firstOrNull { it.stored == value }
    }
}

/** 原作の「数字の位置」（`numOnThumb`）。 */
internal enum class FtutterNumberPosition { ABOVE, OVERLAY, BELOW, UNDER_TITLE, HIDDEN }

/** 画質の設定（`catalogHighRes` ほか）：0低画質、1 Wi-Fi時に高画質、2高画質。 */
internal enum class FtutterHighRes { LOW, WIFI, HIGH }

internal data class FtutterCatalogSettings(
    val columnsPortrait: Int = 5,
    val columnsLandscape: Int = 8,
    val maxThreadCount: Int = 500,
    val sameHeight: Boolean = true,
    val defaultOrder: Int = FtutterOrder.PREVIOUS,
    val previousOrder: FtutterOrder = FtutterOrder.CATALOG,
    val highRes: FtutterHighRes = FtutterHighRes.WIFI,
    val maxThumbHeight: Double = 1.2,
    val minThumbHeight: Double = 0.8,
    val useZoom: Boolean = true,
    val numberPosition: FtutterNumberPosition = FtutterNumberPosition.OVERLAY,
    val kitaShorten: Boolean = true,
    val titleLines: Int = 2,
    val limitTitleLetters: Boolean = false,
    val maxTitleLetters: Int = 4,
    val pullTop: Boolean = true,
    val pullBottom: Boolean = true,
    val showTab: Boolean = false,
    val privacy: Boolean = false,
    val scrollbar: FtutterScrollbarSettings = FtutterScrollbarSettings(true, false, false, 8)
) {
    /** 起動時の並び：「前回の並び順を保存する」なら前回の値、それ以外はその並び。 */
    val startOrder: FtutterOrder
        get() = if (defaultOrder == FtutterOrder.PREVIOUS) previousOrder else FtutterOrder.fromStored(defaultOrder) ?: previousOrder
}

internal fun parseFtutterCatalogSettings(p: Map<String, String>) = FtutterCatalogSettings(
    scrollbar = parseFtutterScrollbar(p, catalog = true),
    columnsPortrait = p.ftutterInt(FtutterKeys.CATALOG_ROW, 5, 1..20),
    columnsLandscape = p.ftutterInt(FtutterKeys.CATALOG_ROW_LANDSCAPE, 8, 1..20),
    maxThreadCount = p.ftutterInt(FtutterKeys.MAX_CATALOG_THREAD_COUNT, 500, 50..2000),
    sameHeight = p.ftutterBool(FtutterKeys.SAME_HEIGHT_GRID, true),
    defaultOrder = p.ftutterInt(FtutterKeys.DEFAULT_ORDER_MODE, FtutterOrder.PREVIOUS, 0..FtutterOrder.PREVIOUS),
    previousOrder = FtutterOrder.fromStored(p[FtutterKeys.PREVIOUS_ORDER_MODE]?.trim()?.toIntOrNull()) ?: FtutterOrder.CATALOG,
    highRes = FtutterHighRes.entries[p.ftutterInt(FtutterKeys.CATALOG_HIGH_RES, 1, 0..2)],
    maxThumbHeight = p.ftutterDouble(FtutterKeys.MAX_THUMB_HEIGHT, 1.2, 1.0..5.0),
    minThumbHeight = p.ftutterDouble(FtutterKeys.MIN_THUMB_HEIGHT, 0.8, 0.0..1.0),
    useZoom = p.ftutterBool(FtutterKeys.USE_ZOOM, true),
    numberPosition = FtutterNumberPosition.entries[p.ftutterInt(FtutterKeys.NUMBER_ON_THUMB, 1, 0..4)],
    kitaShorten = p.ftutterBool(FtutterKeys.KITA_SHORTEN, true),
    titleLines = p.ftutterInt(FtutterKeys.MAX_TITLE_LINES, 2, 0..10),
    limitTitleLetters = p.ftutterBool(FtutterKeys.LIMIT_TITLE_LETTERS, false),
    maxTitleLetters = p.ftutterInt(FtutterKeys.MAX_TITLE_LETTERS, 4, 0..20),
    pullTop = p.ftutterBool(FtutterKeys.CATALOG_PULL_TOP, true),
    pullBottom = p.ftutterBool(FtutterKeys.CATALOG_PULL_BOTTOM, true),
    showTab = p.ftutterBool(FtutterKeys.SHOW_TAB, false),
    privacy = p.ftutterBool(FtutterKeys.IS_PRIVACY, false)
)

private const val FTUTTER_KITA_IMG = "ｷﾀ━━━━━━(ﾟ∀ﾟ)━━━━━━ !!!!!"
private const val FTUTTER_KITA_DEFAULT = "ｷﾀ━━━(ﾟ∀ﾟ)━━━!!"
private const val FTUTTER_KITA_SHORT = "ｷﾀ━(ﾟ∀ﾟ)━!"

/** img/b かどうか（原作は板URL `https://img.2chan.net/b/` との比較）。 */
internal fun ftutterIsImgB(boardUrl: String): Boolean {
    val lower = boardUrl.lowercase().substringAfter("://")
    return lower.startsWith("img.2chan.net/b/") || lower == "img.2chan.net/b"
}

/**
 * カタログに出す題名。原作どおり、文字数制限（既定OFF）で切ってから、板ごとの既定題名と完全に一致する時だけ「ｷﾀ…」を短くする。
 * 題名が無いスレは「(無題)」ではなく空にする（原作は空のまま）。
 */
internal fun ftutterCatalogTitle(rawTitle: String?, boardUrl: String, settings: FtutterCatalogSettings): String {
    var title = rawTitle?.trim().orEmpty()
    if (title.startsWith("No.") && title.drop(3).all(Char::isDigit)) title = ""
    if (settings.limitTitleLetters) title = title.take(settings.maxTitleLetters)
    if (settings.kitaShorten) {
        val kita = if (ftutterIsImgB(boardUrl)) FTUTTER_KITA_IMG else FTUTTER_KITA_DEFAULT
        if (title == kita) title = FTUTTER_KITA_SHORT
    }
    return title
}

/**
 * 原作の「行高を揃える」OFF（石積み表示）の画像の縦横比：幅÷高さを [1/最大高さ比, 1/最小高さ比] に収める。
 * 寸法が分からない時は 1。最小高さ比が0の時は上限なし。
 */
internal fun ftutterThumbAspectRatio(width: Int?, height: Int?, settings: FtutterCatalogSettings): Float {
    if (width == null || height == null || width <= 0 || height <= 0) return 1f
    val ratio = width.toFloat() / height
    val low = (1.0 / settings.maxThumbHeight).toFloat()
    val high = if (settings.minThumbHeight <= 0.0) Float.MAX_VALUE else (1.0 / settings.minThumbHeight).toFloat()
    return ratio.coerceIn(low, high)
}

/** 非表示にしたスレのURL（原作の `hihyouji`）。https にそろえ、重複を除く。 */
internal fun parseFtutterHiddenThreads(raw: String?): List<String> =
    decodeFtutterList(raw).orEmpty().map(::ftutterCanonicalThreadUrl).filter { it.isNotEmpty() }.distinct()

internal fun ftutterCanonicalThreadUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://", ignoreCase = true)) "https://" + trimmed.substring(7) else trimmed
}

/** 原作どおり、その板のURLで始まる非表示記録のうち、取得したカタログに無いもの（落ちたスレ）を消す。 */
internal fun ftutterPruneHiddenThreads(hidden: List<String>, boardUrl: String, loaded: List<CatalogItem>): List<String> {
    val prefix = ftutterCanonicalThreadUrl(ftutterBoardBaseUrl(boardUrl))
    val alive = loaded.map { ftutterCanonicalThreadUrl(it.threadUrl) }.toSet()
    return hidden.filter { !it.startsWith(prefix) || it in alive }
}

/** 保存されている非表示の一覧[stored]から、この板で落ちたスレを除いた値。変わらなければ null（書かない）。 */
internal fun ftutterPrunedHiddenValue(stored: String?, boardUrl: String, loaded: List<CatalogItem>): String? {
    val hidden = parseFtutterHiddenThreads(stored)
    val pruned = ftutterPruneHiddenThreads(hidden, boardUrl, loaded)
    return if (pruned.size == hidden.size) null else encodeFtutterList(pruned)
}

/** 板のディレクトリのURL（`…/b/`）。`futaba.php` などは取り除く。 */
internal fun ftutterBoardBaseUrl(boardUrl: String): String {
    val withoutQuery = boardUrl.substringBefore('?')
    return if (withoutQuery.endsWith("/")) withoutQuery else withoutQuery.substringBeforeLast('/') + "/"
}

/**
 * URL（板・カタログ・スレッドのどれでも）の板を、登録した板から探す。スレッドのURLは`/res/`の手前が板。
 * http と https、ホストの大文字小文字は区別しない。
 */
internal fun ftutterBoardForUrl(boards: List<BoardSummary>, url: String): BoardSummary? {
    fun key(u: String): String {
        val base = if (u.contains("/res/")) u.substringBefore("/res/") + "/" else ftutterBoardBaseUrl(u)
        return base.substringAfter("://").lowercase()
    }
    val wanted = key(url.trim())
    return boards.firstOrNull { key(it.url) == wanted }
}

/**
 * 画面に出すスレ。原作の順（検索→非表示→NG題名→NG正規表現→ピックアップ→上限）のうち、この段階では検索・非表示・上限。
 * 検索は原作（大文字小文字・全角半角・かなの種類を区別する単純な部分一致）と違い、ふたちゃの方針で正規化して比べる
 * （第5次調査§3の推奨。原作との差として記録）。
 */
internal fun ftutterVisibleCatalog(
    items: List<CatalogItem>,
    query: String,
    hidden: Set<String>,
    maxCount: Int
): List<CatalogItem> = ftutterVisibleCatalog(FtutterCatalogSearchIndex.of(items), query, hidden, maxCount)

/**
 * 検索・非表示の判定に使う、カタログごとに1回だけ作るもの（監査M10：以前は1文字打つたびに、全スレの重複を除き、題名を正規化し、
 * URLを正規化していた）。[items]は重複を除いたもので、[titleKeys]・[canonicalUrls]は同じ位置。
 */
internal class FtutterCatalogSearchIndex private constructor(
    val source: List<CatalogItem>,
    val items: List<CatalogItem>,
    val titleKeys: List<String>,
    val canonicalUrls: List<String>
) {
    companion object {
        fun of(items: List<CatalogItem>): FtutterCatalogSearchIndex {
            val distinct = items.distinctBy { it.threadUrl }
            return FtutterCatalogSearchIndex(items, distinct, futaberCatalogTitleKeys(distinct), distinct.map { ftutterCanonicalThreadUrl(it.threadUrl) })
        }
    }
}

internal fun ftutterVisibleCatalog(
    index: FtutterCatalogSearchIndex,
    query: String,
    hidden: Set<String>,
    maxCount: Int
): List<CatalogItem> {
    val needle = if (query.isBlank()) "" else com.valoser.futacha.shared.model.normalizeCatalogSearchText(query)
    val out = ArrayList<CatalogItem>()
    for (i in index.items.indices) {
        if (out.size >= maxCount) break
        if (needle.isNotEmpty() && !index.titleKeys[i].contains(needle)) continue
        if (index.canonicalUrls[i] in hidden) continue
        out += index.items[i]
    }
    return out
}

/** カタログの数字：レス数と未読数。 */
internal data class FtutterCatalogNumbers(val replies: Int, val unread: Int?)

/**
 * 原作の数字（Catalog.dart Thumbnail）：履歴にあるスレは max(読み込み済みのレス数−1, カタログのレス数)、無ければカタログのレス数。
 * 未読はそれから「ここまで読んだ」の件数を引いた値で、履歴にある時だけ・0以下なら出さない。
 * 履歴の件数（[ThreadHistoryEntry.replyCount]）はOPを含む投稿数、カタログのレス数はOPを含まない。
 */
internal fun ftutterCatalogNumbers(item: CatalogItem, history: ThreadHistoryEntry?, readCount: Int?): FtutterCatalogNumbers {
    if (history == null) return FtutterCatalogNumbers(item.replyCount, null)
    val replies = maxOf(history.replyCount - 1, item.replyCount)
    val unread = readCount?.let { replies - it }?.takeIf { it > 0 }
    return FtutterCatalogNumbers(replies, unread)
}

/** 履歴をスレの番号と板で引けるようにする。 */
internal fun ftutterHistoryIndex(history: List<ThreadHistoryEntry>, board: BoardSummary): Map<String, ThreadHistoryEntry> {
    val base = ftutterBoardBaseUrl(board.url).lowercase().substringAfter("://")
    return history.filter { entry ->
        entry.boardId == board.id || ftutterBoardBaseUrl(entry.boardUrl).lowercase().substringAfter("://") == base ||
            entry.boardUrl.lowercase().substringAfter("://").startsWith(base)
    }.associateBy { it.threadId }
}

/**
 * スレごとの「ここまで読んだ」の件数（返信の数＝OPを除く）。保存は `compat.ftutter.seen` に1行1件
 * 「スレURL<TAB>件数」。スレッド画面（第3段階）が書く。
 */
internal fun parseFtutterReadCounts(raw: String?): Map<String, Int> =
    decodeFtutterList(raw).orEmpty().mapNotNull { line ->
        val url = line.substringBefore('\t').trim()
        val count = line.substringAfter('\t', "").trim().toIntOrNull()
        if (url.isEmpty() || count == null || count < 0) null else ftutterCanonicalThreadUrl(url) to count
    }.toMap()

internal fun encodeFtutterReadCounts(counts: Map<String, Int>): String =
    encodeFtutterList(counts.entries.map { "${it.key}\t${it.value}" })
