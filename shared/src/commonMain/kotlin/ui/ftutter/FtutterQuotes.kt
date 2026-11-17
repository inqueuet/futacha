package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.board.messageHtmlToLines

/**
 * ふたったー風だけの引用の結び付け（原作の`searchInnyou`の規則。docs/ftutter-mode-research-round6-2026-10-13.md §9）。
 *
 * - 連続した引用行を1つの塊にし、塊ごとに探す。見つからない時は、行の範囲を縮めて一番長く一致する範囲を探し、
 *   残りの行も同じようにする（2行の塊は行ごとに別々に結ばれる）。
 * - 引用行は先頭の`>`を1つだけ外して探す（`>>本文`は「>本文」を含むレスへ結ぶ）。
 * - 探す順：`ID:`／`IP:`（どのレスにも結ばない）→ No.の完全一致 → 添付のファイル名（これだけは後のレスも対象）→
 *   本文の完全一致 → 引用行を除いた本文の部分一致 → 本文全体の部分一致。文字の正規化はしない。
 * - 探すのは自分より前のレスだけで、新しい方（直前）から。自分自身とOPの引用は結ばない。
 *
 * 共有の[QuoteReference]の形はそのまま使い、このモードで表示するレスの`quoteReferences`だけを作り直す。
 */
internal fun ftutterRelinkQuotes(posts: List<Post>, checkCancelled: () -> Unit = {}): List<Post> {
    if (posts.isEmpty()) return posts
    val texts = posts.map { FtutterQuoteText.of(it) }
    var changed = false
    val linked = posts.mapIndexed { index, post ->
        checkCancelled()
        val references = ftutterResolvePostQuotes(posts, texts, index, checkCancelled)
        if (post.quoteReferencesParsed && post.quoteReferences == references) post
        else {
            changed = true
            post.copy(quoteReferences = references, quoteReferencesParsed = true)
        }
    }
    // 何も変わらない時は元の一覧のまま返す（写しを作らない）。
    return if (changed) linked else posts
}

internal fun ThreadPage.ftutterRelinked(checkCancelled: () -> Unit = {}): ThreadPage = copy(posts = ftutterRelinkQuotes(posts, checkCancelled))

private class FtutterQuoteText(
    val lines: List<String>,
    val body: String,
    val bodyWithoutQuotes: String,
    val fileName: String?
) {
    companion object {
        fun of(post: Post): FtutterQuoteText {
            val lines = messageHtmlToLines(post.messageHtml).map { it.trimEnd() }
            return FtutterQuoteText(
                lines = lines,
                body = lines.joinToString("\n"),
                bodyWithoutQuotes = lines.filterNot(::ftutterIsQuoteLine).joinToString("\n"),
                fileName = post.imageUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            )
        }
    }
}

private val ftutterNoQuotePattern = Regex("^No\\.(\\d+)$")

private fun ftutterIsIdentityQuote(query: String): Boolean =
    query.startsWith("ID:", ignoreCase = true) || query.startsWith("IP:", ignoreCase = true)

/** 引用行から探す文（先頭の`>`を1つ外す）。 */
private fun ftutterQuoteQuery(line: String): String = line.trim().drop(1)

private fun ftutterResolvePostQuotes(
    posts: List<Post>,
    texts: List<FtutterQuoteText>,
    index: Int,
    checkCancelled: () -> Unit = {}
): List<QuoteReference> {
    val lines = texts[index].lines
    val blocks = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    lines.forEach { line ->
        if (ftutterIsQuoteLine(line)) current += line.trim()
        else if (current.isNotEmpty()) { blocks += current; current = mutableListOf() }
    }
    if (current.isNotEmpty()) blocks += current
    if (blocks.isEmpty()) return emptyList()
    val out = mutableListOf<QuoteReference>()
    val resolver = FtutterRangeResolver(posts, texts, index, out, checkCancelled)
    blocks.forEach { block ->
        if (index == 0) {
            out += QuoteReference(block.joinToString("\n"), emptyList())
            return@forEach
        }
        // ID・IPの行は塊を区切り、どのレスにも結ばない。
        var run = mutableListOf<String>()
        fun flush() {
            if (run.isNotEmpty()) resolver.resolve(run)
            run = mutableListOf()
        }
        block.forEach { line ->
            if (ftutterIsIdentityQuote(ftutterQuoteQuery(line))) {
                flush()
                out += QuoteReference(line, emptyList())
            } else run += line
        }
        flush()
    }
    return out
}

/**
 * 1レスで範囲を探す回数の上限。普通の引用（31行まで）は全部の範囲を試せる。超えた残りは行ごとに結ぶ
 * （一致しない引用行を数百行並べた荒らしのレスで、読み込みが数十秒止まっていた。監査C1）。
 */
internal const val FTUTTER_QUOTE_RANGE_LOOKUP_BUDGET = 512

/**
 * 引用行の連なりを、一番長く一致する範囲から順に結ぶ（左から。どれにも一致しない行は結ばずに、続く行とまとめて残す）。
 *
 * 結果は全部の範囲を長い順に試すのと同じで、試す範囲だけを減らす：
 * - 1行で見つからない行を含む範囲は、どれも見つからない（部分一致・完全一致とも、ファイル名とNo.は1行だけ）。その行で区切る。
 * - 長さLで見つかった範囲の左右には、Lより長い一致は無い（あれば先に見つかっている）。
 */
private class FtutterRangeResolver(
    private val posts: List<Post>,
    private val texts: List<FtutterQuoteText>,
    private val index: Int,
    private val out: MutableList<QuoteReference>,
    private val checkCancelled: () -> Unit
) {
    private var budget = FTUTTER_QUOTE_RANGE_LOOKUP_BUDGET
    private lateinit var lines: List<String>
    private lateinit var singles: List<Int?>

    private fun find(range: List<String>): Int? {
        checkCancelled()
        return ftutterFindQuoteTarget(posts, texts, index, range.joinToString("\n") { ftutterQuoteQuery(it) })
    }

    fun resolve(run: List<String>) {
        lines = run
        singles = run.map { find(listOf(it)) }
        var start = 0
        while (start < run.size) {
            val unmatched = singles[start] == null
            var end = start
            while (end < run.size && (singles[end] == null) == unmatched) end++
            if (unmatched) out += QuoteReference(run.subList(start, end).joinToString("\n"), emptyList())
            else resolveRange(start, end, end - start)
            start = end
        }
    }

    /** [from]〜[until]の行はどれも1行では見つかる。[maxLength]より長い範囲は試さない。 */
    private fun resolveRange(from: Int, until: Int, maxLength: Int) {
        if (from >= until) return
        for (length in minOf(maxLength, until - from) downTo 2) {
            for (start in from..(until - length)) {
                if (budget <= 0) { linkEachLine(from, until); return }
                budget--
                val range = lines.subList(start, start + length)
                val target = find(range)
                if (target != null) {
                    resolveRange(from, start, length)
                    out += QuoteReference(range.joinToString("\n"), listOf(posts[target].id))
                    resolveRange(start + length, until, length)
                    return
                }
            }
        }
        linkEachLine(from, until)
    }

    private fun linkEachLine(from: Int, until: Int) {
        for (i in from until until) out += QuoteReference(lines[i], listOf(posts[singles[i]!!].id))
    }
}

private fun ftutterFindQuoteTarget(posts: List<Post>, texts: List<FtutterQuoteText>, index: Int, query: String): Int? {
    if (query.isBlank()) return null
    val earlier = (index - 1) downTo 0
    ftutterNoQuotePattern.matchEntire(query)?.let { match ->
        val id = match.groupValues[1]
        earlier.firstOrNull { posts[it].id == id }?.let { return it }
    }
    earlier.firstOrNull { texts[it].fileName == query }?.let { return it }
    ((index + 1) until posts.size).firstOrNull { texts[it].fileName == query }?.let { return it }
    earlier.firstOrNull { texts[it].body == query }?.let { return it }
    earlier.firstOrNull { texts[it].bodyWithoutQuotes.contains(query) }?.let { return it }
    earlier.firstOrNull { texts[it].body.contains(query) }?.let { return it }
    return null
}

/** 「従来の動き」：引用・返信・IDのポップアップを、前の角丸のカードで出す（既定OFF。T-B2）。 */
internal val FTUTTER_LEGACY_POPUPS_KEY: String get() = FtutterKeys.PREFIX + "legacyPopups"
internal const val FTUTTER_LEGACY_POPUPS_LABEL = "引用のポップアップを角丸のカードで出す"
internal const val FTUTTER_LEGACY_POPUPS_NOTE =
    "ONにすると、引用・返信・IDのポップアップを、押した所の近くの角丸のカードで出し、2枚目も押した所に出します。OFFは原作と同じ全幅の表示です"

internal fun ftutterLegacyPopups(preferences: Map<String, String>): Boolean = preferences.ftutterBool(FTUTTER_LEGACY_POPUPS_KEY, false)

/**
 * ポップアップの上端（px）。原作：1枚目は押した所のすぐ上（収まらなければ上部のバーの下）、返信数の一覧と2枚目からは
 * 上部のバーのすぐ下。従来の動き（[legacy]）は、どの1枚も押した所のすぐ上。
 */
internal fun ftutterPopupTop(
    level: Int,
    anchorTop: Float?,
    height: Int,
    topMargin: Float,
    pinTop: Boolean,
    legacy: Boolean
): Float {
    if (!legacy && (level > 0 || pinTop)) return topMargin
    val anchor = anchorTop ?: return topMargin
    val candidate = anchor - height
    return if (candidate >= topMargin) candidate else topMargin
}

/** 原作のポップアップの下の暗さ（1枚ごとに重なる）。 */
internal const val FTUTTER_POPUP_SCRIM_ALPHA = 0.35f
