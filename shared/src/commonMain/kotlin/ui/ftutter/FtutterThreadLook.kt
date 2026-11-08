package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear

/**
 * T-B4：スレッドの見た目を原作（Resu・スレッドの上部バー）に合わせる決まり。
 *
 * - 上部バーの題名は小さな2行：スレの題名の全文と「板名 (返信の数)」（H8）。
 * - 1レス目の画像は見出しの上に全幅（H7）。
 * - 今までの形（大きな1行の題名・画像は本文の下）は「従来の動き」のスイッチで残す。
 */
internal val FTUTTER_LEGACY_THREAD_LOOK_KEY: String get() = FtutterKeys.PREFIX + "legacyThreadLook"
internal const val FTUTTER_LEGACY_THREAD_LOOK_LABEL = "スレッドの見た目を前の形にする"
internal const val FTUTTER_LEGACY_THREAD_LOOK_NOTE =
    "ONにすると、スレッドの題名を大きな1行で、1レス目の画像を本文の下に出します。OFFは原作と同じ、題名は小さな2行（題名と「板名 (返信の数)」）で、1レス目の画像は見出しの上に全幅です。ONの時は、画面の下で少しだけ見えているレスも既読に数えます（OFFは原作と同じ、最後まで見えたレスまで）"

internal fun ftutterLegacyThreadLook(preferences: Map<String, String>): Boolean =
    preferences.ftutterBool(FTUTTER_LEGACY_THREAD_LOOK_KEY, false)

/**
 * スレの題名（H8・M65・M83）。原作はカタログの題名（全文）を使う。無い時（URLから開いた等）は1レス目の本文の行を
 * つなげた物、それも無ければ題名の欄、最後は「無題」。
 */
internal fun ftutterThreadTitle(storedTitle: String?, firstPost: Post?): String {
    storedTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    ftutterJoinedBody(firstPost)?.let { return it }
    firstPost?.subject?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return "無題"
}

/** 本文の行を改行なしでつなげた物（制御文字は除く）。空なら null。 */
internal fun ftutterJoinedBody(post: Post?): String? {
    val html = post?.messageHtml ?: return null
    val text = HtmlEntityDecoder.decode(stripHtmlTagsLinear(replaceHtmlBreakTags(html)))
    val joined = text.lineSequence()
        .map { line -> line.filterNot { it.category == CharCategory.CONTROL || it.category == CharCategory.FORMAT }.trim() }
        .filter(String::isNotEmpty)
        .joinToString("")
    return joined.takeIf { it.isNotEmpty() }
}

/** 上部バーの2行目（原作「ねこ (19)」）。返信の数は1レス目を除いた数。 */
internal fun ftutterThreadSubtitle(boardName: String, postCount: Int): String =
    "$boardName (${(postCount - 1).coerceAtLeast(0)})"

/**
 * 読んだ後の履歴の行。題名は今の題名を保ち（原作はカタログの題名）、無い時だけ本文から作る。件数・画像は他モードと同じ。
 */
internal fun ftutterHistoryAfterLoad(
    history: List<ThreadHistoryEntry>,
    boardId: String,
    threadId: String,
    posts: List<Post>
): List<ThreadHistoryEntry> = com.valoser.futacha.shared.ui.futaber.futaberHistoryAfterLoad(history, boardId, threadId, posts)
    .mapIndexed { index, entry ->
        if (entry.threadId != threadId || entry.boardId != boardId) entry
        else entry.copy(title = ftutterThreadTitle(history[index].title, posts.firstOrNull()))
    }

/** 更新の後の「新着 N 件」（M7。原作は濃い青のカプセルで出す）。 */
internal fun ftutterIsNewCountNotice(text: String): Boolean = text.startsWith("新着 ") && text.contains(" 件")

/** 本文の`<a href>`の行き先（M8：原作は`<a>`のある物だけをリンクにする）。 */
internal fun ftutterAnchorTargets(html: String): Set<String> =
    FTUTTER_ANCHOR_HREF.findAll(html)
        .map { HtmlEntityDecoder.decode(it.groupValues[1]) }.toSet()

/** リンクを出すか（M8）。従来の動きは本文のURLを全部、原作の形は`<a>`の行き先に当たる物だけ。 */
internal fun ftutterKeepsLink(url: String, anchors: Set<String>, legacy: Boolean): Boolean =
    legacy || anchors.any { it == url || it.endsWith(url.substringAfter("://")) || url.endsWith(it.substringAfter("://")) }

/**
 * 赤字の行（M11）：`<font color>`が赤の物を含む行の番号。行の分け方は`<br>`（本文の行と同じ数の時だけ使う）。
 */
internal fun ftutterRedLines(html: String): Set<Int> {
    if (!html.contains("<font", ignoreCase = true)) return emptySet()
    val red = FTUTTER_RED_FONT
    return html.split(FTUTTER_BR_TAG).withIndex()
        .filter { red.containsMatchIn(it.value) }.map { it.index }.toSet()
}

/**
 * 引用の色の行（M12）：原作は`>`ではなくHTMLの`<font color="#789922">`で緑にする。緑のfontを含む行の番号を返す。
 * `<br>`で分けた数が本文の行の数（[lineCount]）と違う時はnull（`>`で始まる行を緑にする今までの決め方に任せる）。
 */
internal fun ftutterQuoteFontLines(html: String, lineCount: Int): Set<Int>? {
    val segments = html.split(FTUTTER_BR_TAG)
    if (segments.size != lineCount) return null
    val green = FTUTTER_GREEN_FONT
    return segments.withIndex().filter { green.containsMatchIn(it.value) }.map { it.index }.toSet()
}

/** 動く画像（M10）：GIF・WebM・MP4。 */
internal fun ftutterIsMovingImage(url: String?): Boolean {
    val path = url?.lowercase()?.substringBefore('?') ?: return false
    return path.endsWith(".gif") || path.endsWith(".webm") || path.endsWith(".mp4")
}

/** そうだねを送った後の数（M14）：応答が数ならそれ、読めなければ今の数＋1。 */
internal fun ftutterSoudaneAfterVote(body: String, label: String?): Int =
    body.trim().toIntOrNull()?.takeIf { it > 0 } ?: (ftutterSoudaneCount(label) + 1)

internal val FTUTTER_LEGACY_DEL_CONFIRM_KEY: String get() = FtutterKeys.PREFIX + "legacyDelConfirm"
internal const val FTUTTER_LEGACY_DEL_CONFIRM_LABEL = "delの前に確かめる"
internal const val FTUTTER_LEGACY_DEL_CONFIRM_NOTE =
    "ONにすると、レスの長押しの「del」で送る前に確かめます。OFFは原作と同じ、押すとすぐ送ります（「delの確認でNGにも登録」の拡張がONの時は確かめます）"

/** delの前に確かめるか（M40）。 */
internal fun ftutterDelAsks(preferences: Map<String, String>, offerNg: Boolean): Boolean =
    offerNg || preferences.ftutterBool(FTUTTER_LEGACY_DEL_CONFIRM_KEY, false)

/**
 * 既読にする最後の行（2026-11-08に原作で確かめた）：原作は画面の中で最後まで見えている行までを既読にし、
 * 下で少しだけ見えている行は数えない。最後まで見えている行が無い（1つの行が画面より大きい）時は、
 * 上で見えている行を使う。従来の動き（[legacy]）は少しでも見えている行まで。
 * [items] は (行の番号, 上端, 高さ)、[bottom] は表示の下端。
 */
internal fun ftutterLastSeenRow(items: List<Triple<Int, Int, Int>>, bottom: Int, legacy: Boolean): List<Int> {
    val visible = items.filter { it.second < bottom }
    if (legacy) return visible.map { it.first }
    val whole = visible.filter { it.second + it.third <= bottom }
    return (whole.ifEmpty { visible.take(1) }).map { it.first }
}

internal val FTUTTER_LEGACY_KEEP_ON_FAILURE_KEY: String get() = FtutterKeys.PREFIX + "legacyKeepOnFailure"
internal const val FTUTTER_LEGACY_KEEP_ON_FAILURE_LABEL = "更新に失敗しても本文を残す"
internal const val FTUTTER_LEGACY_KEEP_ON_FAILURE_NOTE =
    "ONにすると、通信できずにスレッドの更新に失敗した時も本文を残し、理由を知らせます。OFFは原作と同じ、本文を消して「！」の印だけを出します（更新し直すと本文が戻ります）"

/**
 * 更新に失敗した時に本文を消して「！」を出すか（2026-11-08に原作で確かめた：通信を切って更新すると、約40秒後に
 * 本文と題名が消えて上部バーと画面の中央に「！」だけが出る。通信を戻して更新すると本文が戻る）。
 * 1度も読めていないスレを開いた時も同じく「！」だけ（題名は読み込みの間だけ出る）。落ち（404・410）は従来どおり。
 */
internal fun ftutterBlanksOnFailure(preferences: Map<String, String>, status: Int?): Boolean =
    status != 404 && status != 410 && !preferences.ftutterBool(FTUTTER_LEGACY_KEEP_ON_FAILURE_KEY, false)

/**
 * 見出しの添付のファイル名（M9）。原作は本物の添付（サムネイルのある物）だけを出し、本文の「あぷ」のファイル名
 * （共有の解析が添付の無いレスの画像として拾う`fu1234567.jpg`など）は見出しに出さない。[legacy]なら今までどおり。
 */
internal fun ftutterHeaderFileName(post: com.valoser.futacha.shared.model.Post, legacy: Boolean): String? {
    if (!legacy && post.thumbnailUrl.isNullOrBlank()) return null
    return post.imageUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

// Made once (audit C6: every row built these again).
private val FTUTTER_ANCHOR_HREF = Regex("""<a\s[^>]*href\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
private val FTUTTER_RED_FONT = Regex("""<font[^>]*color\s*=\s*["']?(#?ff0000|#?f00|red|#?cc0000|#?e00|#?dd0000)["'\s>]""", RegexOption.IGNORE_CASE)
private val FTUTTER_GREEN_FONT = Regex("""<font[^>]*color\s*=\s*["']?#?789922["'\s>]""", RegexOption.IGNORE_CASE)
private val FTUTTER_BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
