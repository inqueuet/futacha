package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.buildThreadTreeNodes
import com.valoser.futacha.shared.ui.board.canonicalThreadSearchText
import com.valoser.futacha.shared.ui.board.messageHtmlToLines
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import com.valoser.futacha.shared.ui.board.normalizePosterIdValue

/**
 * スレッド画面の設定（原作 Setting_Sure の既定値と範囲。docs/ftutter-mode-research-round6-2026-10-13.md §6・§11）。
 */
internal enum class FtutterImagePosition(val stored: Int) {
    TOP(0), LEFT(1), RIGHT(2), BOTTOM(3);

    companion object {
        fun fromStored(value: Int?): FtutterImagePosition = entries.firstOrNull { it.stored == value } ?: BOTTOM
    }
}

internal data class FtutterThreadSettings(
    val showDeleted: Boolean = false,
    val deletedChain: Boolean = false,
    val quoteShorten: Boolean = false,
    val paddingTop: Int = 5,
    val paddingLeft: Int = 5,
    val paddingRight: Int = 5,
    val paddingBottom: Int = 5,
    val paddingHeaderText: Int = 3,
    val paddingTextFile: Int = 8,
    val opImageMaxHeight: Double = 500.0,
    val imagePosition: FtutterImagePosition = FtutterImagePosition.BOTTOM,
    val imageMaxHeight: Double = 300.0,
    val imageMaxWidth: Double = 200.0,
    val imageFixedWidth: Boolean = false,
    val pullTop: Boolean = false,
    val pullBottom: Boolean = true,
    val showNewCount: Boolean = false,
    val showReplyCount: Boolean = true,
    val popularReplies: Int = 3,
    val popularSoudane: Int = 10,
    val isTree: Boolean = false,
    val showTab: Boolean = false,
    val isPrivacy: Boolean = false
)

private val PADDING_RANGE = 0..30
private val IMAGE_SIZE_RANGE = 0.0..1000.0

internal fun parseFtutterThreadSettings(p: Map<String, String>) = FtutterThreadSettings(
    showDeleted = p.ftutterBool(FtutterKeys.SHOW_DELETED, false),
    deletedChain = p.ftutterBool(FtutterKeys.DELETED_CHAIN, false),
    quoteShorten = p.ftutterBool(FtutterKeys.QUOTE_SHORTEN, false),
    paddingTop = p.ftutterInt(FtutterKeys.PADDING_TOP, 5, PADDING_RANGE),
    paddingLeft = p.ftutterInt(FtutterKeys.PADDING_LEFT, 5, PADDING_RANGE),
    paddingRight = p.ftutterInt(FtutterKeys.PADDING_RIGHT, 5, PADDING_RANGE),
    paddingBottom = p.ftutterInt(FtutterKeys.PADDING_BOTTOM, 5, PADDING_RANGE),
    paddingHeaderText = p.ftutterInt(FtutterKeys.PADDING_HEADER_TEXT, 3, PADDING_RANGE),
    paddingTextFile = p.ftutterInt(FtutterKeys.PADDING_TEXT_FILE, 8, PADDING_RANGE),
    opImageMaxHeight = p.ftutterDouble(FtutterKeys.OP_IMAGE_MAX_HEIGHT, 500.0, IMAGE_SIZE_RANGE),
    imagePosition = FtutterImagePosition.fromStored(p[FtutterKeys.RES_IMAGE_POSITION]?.trim()?.toIntOrNull()),
    imageMaxHeight = p.ftutterDouble(FtutterKeys.RES_IMAGE_MAX_HEIGHT, 300.0, IMAGE_SIZE_RANGE),
    imageMaxWidth = p.ftutterDouble(FtutterKeys.RES_IMAGE_MAX_WIDTH, 200.0, IMAGE_SIZE_RANGE),
    imageFixedWidth = p.ftutterBool(FtutterKeys.RES_IMAGE_FIXED_WIDTH, false),
    pullTop = p.ftutterBool(FtutterKeys.THREAD_PULL_TOP, false),
    pullBottom = p.ftutterBool(FtutterKeys.THREAD_PULL_BOTTOM, true),
    showNewCount = p.ftutterBool(FtutterKeys.SHOW_NEW_COUNT, false),
    showReplyCount = p.ftutterBool(FtutterKeys.SHOW_REPLY_COUNT, true),
    popularReplies = p.ftutterInt(FtutterKeys.POPULAR_REPLIES, 3, 1..10),
    popularSoudane = p.ftutterInt(FtutterKeys.POPULAR_SOUDANE, 10, 0..100),
    isTree = p.ftutterBool(FtutterKeys.IS_TREE, false),
    showTab = p.ftutterBool(FtutterKeys.SHOW_TAB, false),
    isPrivacy = p.ftutterBool(FtutterKeys.IS_PRIVACY, false)
)

// ---- 見出しの色（原作 ResuHeader。投稿の意味色として扱い、操作文字の4.5:1の規則とは別） ----

private val RED_ACCENT = Color(0xFFFF5252)
private val PINK_ACCENT = Color(0xFFFF4081)
private val PURPLE_ACCENT = Color(0xFFE040FB)
private val BLUE_ACCENT = Color(0xFF448AFF)
private val SOUDANE_RED = Color(0xFFF44336)
private val PURE_RED = Color(0xFFFF0000)

/** 返信数の色：7以上は赤、5〜6はピンク、3〜4は紫、1〜2は青、0は既定（null）。 */
internal fun ftutterReplyCountColor(count: Int): Color? = when {
    count >= 7 -> RED_ACCENT
    count >= 5 -> PINK_ACCENT
    count >= 3 -> PURPLE_ACCENT
    count >= 1 -> BLUE_ACCENT
    else -> null
}

/** 同じIDの件数の色：8以上は赤、6〜7はピンク、4〜5は紫、2〜3は青、1は既定（null）。 */
internal fun ftutterIdCountColor(total: Int): Color? = when {
    total >= 8 -> RED_ACCENT
    total >= 6 -> PINK_ACCENT
    total >= 4 -> PURPLE_ACCENT
    total >= 2 -> BLUE_ACCENT
    else -> null
}

/** そうだねの色：数に応じて既定の色から赤へ7段階で近づく。 */
internal fun ftutterSoudaneColor(count: Int, base: Color): Color = when {
    count <= 4 -> lerp(base, SOUDANE_RED, 0.25f)
    count <= 10 -> lerp(base, SOUDANE_RED, 0.5f)
    count <= 20 -> lerp(base, SOUDANE_RED, 0.75f)
    count <= 30 -> SOUDANE_RED
    count <= 50 -> lerp(SOUDANE_RED, PURE_RED, 0.33f)
    count <= 80 -> lerp(SOUDANE_RED, PURE_RED, 0.66f)
    else -> PURE_RED
}

/** ツリーの字下げ（dp）：深さ4までは15×深さ、5以上は60＋7×(深さ−4)。 */
internal fun ftutterIndentDp(depth: Int): Int = when {
    depth <= 0 -> 0
    depth <= 4 -> depth * 15
    else -> 60 + (depth - 4) * 7
}

/** レスの左の縦線の種類（原作は見出しに印を出さず、幅2の線で示す）。 */
internal enum class FtutterPostMark { NONE, SELF, REPLY_TO_SELF, CHILD }

internal fun ftutterPostMark(post: Post, selfIds: Set<String>, depth: Int): FtutterPostMark = when {
    post.id in selfIds -> FtutterPostMark.SELF
    post.quoteReferences.any { ref -> ref.targetPostIds.any { it in selfIds } } -> FtutterPostMark.REPLY_TO_SELF
    depth > 0 -> FtutterPostMark.CHILD
    else -> FtutterPostMark.NONE
}

// ---- 引用・返信・ID ----

/** そのレスを引用している（返信の）レスの番号。自分より後のレスだけ。 */
internal fun ftutterReplyIndex(posts: List<Post>): Map<String, List<Int>> {
    val position = posts.withIndex().associate { (i, p) -> p.id to i }
    val index = LinkedHashMap<String, MutableList<Int>>()
    posts.forEachIndexed { i, source ->
        source.quoteReferences.flatMap { it.targetPostIds }.distinct().forEach { target ->
            val at = position[target] ?: return@forEach
            if (at < i) index.getOrPut(target) { mutableListOf() }.add(i)
        }
    }
    return index
}

/** そのレスが引用しているレスの番号（自分より前だけ）。 */
internal fun ftutterQuoteTargets(posts: List<Post>, index: Int): List<Int> {
    val position = posts.withIndex().associate { (i, p) -> p.id to i }
    return posts[index].quoteReferences.flatMap { it.targetPostIds }.mapNotNull { position[it] }
        .filter { it < index }.distinct()
}

/** IDの (n/m)：何番目と全体の件数。IDが1回しか出ないレスは null。 */
internal data class FtutterIdCount(val id: String, val nth: Int, val total: Int)

internal fun ftutterIdCounts(posts: List<Post>): Map<String, FtutterIdCount> {
    val totals = HashMap<String, Int>()
    posts.forEach { post -> normalizePosterIdValue(post.posterId)?.let { totals[it] = (totals[it] ?: 0) + 1 } }
    val running = HashMap<String, Int>()
    val result = HashMap<String, FtutterIdCount>()
    posts.forEach { post ->
        val id = normalizePosterIdValue(post.posterId) ?: return@forEach
        val nth = (running[id] ?: 0) + 1
        running[id] = nth
        result[post.id] = FtutterIdCount(id, nth, totals.getValue(id))
    }
    return result
}

/** そうだねの数（ラベルの数字）。無ければ0。 */
internal fun ftutterSoudaneCount(label: String?): Int = label.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0

// ---- 人気レス（§9） ----

/** 会話の木（引用でつながったレスの集まり）を、レスの番号の集合で返す。 */
internal fun ftutterConversationGroups(posts: List<Post>): List<Set<Int>> {
    val parent = IntArray(posts.size) { it }
    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
        return r
    }
    posts.indices.forEach { i -> ftutterQuoteTargets(posts, i).forEach { t -> parent[find(i)] = find(t) } }
    return posts.indices.groupBy { find(it) }.values.map { it.toSet() }
}

internal data class FtutterPopular(val main: Set<Int>, val related: Set<Int>)

/**
 * 本体は「返信の数 ≥ 閾値」または「そうだね ≥ 閾値」。関連は、本体を含む会話の木の残り全員と、
 * 3件以上の会話の木の全員（本体が無くても）。
 */
internal fun ftutterPopular(posts: List<Post>, replyThreshold: Int, soudaneThreshold: Int): FtutterPopular {
    val replies = ftutterReplyIndex(posts)
    val main = posts.indices.filter { i ->
        (replies[posts[i].id]?.size ?: 0) >= replyThreshold || ftutterSoudaneCount(posts[i].saidaneLabel) >= soudaneThreshold.coerceAtLeast(1)
    }.toSet()
    val related = mutableSetOf<Int>()
    ftutterConversationGroups(posts).forEach { group ->
        if (group.size >= 3 || group.any { it in main }) related += group.filter { it !in main }
    }
    return FtutterPopular(main, related)
}

// ---- 行の組み立て ----

internal sealed interface FtutterRow {
    val key: String

    /** レス1件。[index]は0始まりの通し番号（OPが0）。 */
    data class PostRow(val index: Int, val depth: Int) : FtutterRow {
        override val key: String get() = "p$index"
    }

    /** 隠したレスの置換行（「N  削除」「N  NG」）。 */
    data class HiddenRow(val index: Int, val depth: Int, val isNg: Boolean = false) : FtutterRow {
        override val key: String get() = "h$index"
    }

    /** 「ここまで読んだ」の帯。 */
    data object ReadMark : FtutterRow {
        override val key: String get() = "kokomade"
    }
}

/** 表示の絞り込み。人気・画像はタブごと（保存しない）。 */
internal data class FtutterThreadFilter(
    val popular: Boolean = false,
    val imagesOnly: Boolean = false,
    val query: String = ""
)

/**
 * 隠すレス。[deleted]・[ng]は当たったレス本人、[removed]は連鎖（削除の連鎖・連鎖NG）で行ごと消すレス。
 */
internal data class FtutterHidden(
    val deleted: Set<Int> = emptySet(),
    val ng: Set<Int> = emptySet(),
    val removed: Set<Int> = emptySet()
) {
    fun isHidden(index: Int): Boolean = index in deleted || index in ng || index in removed
}

/** 削除（設定に従う）とNG（[ngPosts]は連鎖を含まない当たったレス）をまとめる。 */
internal fun ftutterHidden(posts: List<Post>, settings: FtutterThreadSettings, ngPosts: Set<Int>, ngChain: Boolean): FtutterHidden {
    val (deleted, deletedChained) = ftutterHiddenDeleted(posts, settings)
    val ngOnly = ngPosts - deleted
    val ngChained = if (ngChain && ngOnly.isNotEmpty()) {
        val replies = ftutterReplyIndex(posts)
        val all = mutableSetOf<Int>()
        val queue = ArrayDeque(ngOnly)
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            replies[posts[at].id].orEmpty().forEach { reply -> if (reply !in ngOnly && all.add(reply)) queue.addLast(reply) }
        }
        all
    } else emptySet()
    val removed = (deletedChained + ngChained) - deleted - ngOnly
    return FtutterHidden(deleted, ngOnly, removed)
}

/** 削除レスを隠す時の対象（削除レス本体と、連鎖がONならそれを引用したレスを段ごとに）。 */
internal fun ftutterHiddenDeleted(posts: List<Post>, settings: FtutterThreadSettings): Pair<Set<Int>, Set<Int>> {
    if (settings.showDeleted) return emptySet<Int>() to emptySet()
    val deleted = posts.indices.filter { posts[it].isDeleted }.toSet()
    if (!settings.deletedChain || deleted.isEmpty()) return deleted to emptySet()
    val chained = mutableSetOf<Int>()
    val replies = ftutterReplyIndex(posts)
    val queue = ArrayDeque(deleted)
    while (queue.isNotEmpty()) {
        val at = queue.removeFirst()
        replies[posts[at].id].orEmpty().forEach { reply ->
            if (reply !in deleted && chained.add(reply)) queue.addLast(reply)
        }
    }
    return deleted to chained
}

/** スレ内検索の文字列（本文。全角半角・かなの種類は同一視：カタログの検索と同じ、原作との意図した違い）。 */
internal fun ftutterPostSearchText(post: Post): String =
    canonicalThreadSearchText(messageHtmlToPlainText(post.messageHtml).lowercase())

internal fun ftutterMatchesQuery(post: Post, query: String): Boolean {
    val wanted = canonicalThreadSearchText(query.trim().lowercase())
    return wanted.isEmpty() || ftutterPostSearchText(post).contains(wanted)
}

/**
 * 画面に並べる行。ツリー表示では、既読（[readCount]件）までの木と、その後に来たレスの木を分け、
 * 後から来た返信は古い木へ戻さず「ここまで読んだ」の後ろへ、枝の先頭を深さ0にして並べる（原作の`_rebuildTree`）。
 * 帯は既読の最後のレスの後ろ、未読のスレ（[readCount]が0）では先頭の前。
 *
 * 削除・NGのレス（原作 getAvailableIndexes）：通常表示では行ごと消し、返信は残す。ツリー表示では、見えている返信を
 * 持つものだけ「N  削除」「N  NG」の1行に置き換えて残す（返信はその下に字下げされる）。連鎖で隠したレスは行ごと消す。
 */
internal suspend fun ftutterBuildRows(
    posts: List<Post>,
    settings: FtutterThreadSettings,
    filter: FtutterThreadFilter,
    readCount: Int,
    hidden: FtutterHidden = ftutterHidden(posts, settings, emptySet(), ngChain = false)
): List<FtutterRow> {
    if (posts.isEmpty()) return emptyList()
    val position = posts.withIndex().associate { (i, p) -> p.id to i }
    val chained = hidden.removed
    val popular = if (filter.popular) ftutterPopular(posts, settings.popularReplies, settings.popularSoudane) else null
    fun visible(i: Int): Boolean {
        if (i in chained) return false
        if (popular != null) {
            val keep = i in popular.main || (settings.isTree && i in popular.related)
            if (!keep) return false
        }
        if (filter.imagesOnly && posts[i].thumbnailUrl.isNullOrBlank() && posts[i].imageUrl.isNullOrBlank()) return false
        if (filter.query.isNotBlank() && !ftutterMatchesQuery(posts[i], filter.query)) return false
        return true
    }
    val read = readCount.coerceIn(0, posts.size)
    val ordered: List<Pair<Int, Int>> = if (settings.isTree) {
        val old = buildThreadTreeNodes(posts.subList(0, read)).map { (position.getValue(it.post.id)) to it.depth }
        val new = buildThreadTreeNodes(posts.subList(read, posts.size)).map { (position.getValue(it.post.id)) to it.depth }
        old + new
    } else {
        posts.indices.map { it to 0 }
    }
    val rows = mutableListOf<FtutterRow>()
    var markPlaced = read == 0
    if (markPlaced) rows += FtutterRow.ReadMark
    ordered.forEach { (i, depth) ->
        if (!markPlaced && i >= read) {
            rows += FtutterRow.ReadMark
            markPlaced = true
        }
        if (!visible(i)) return@forEach
        rows += when {
            i in hidden.deleted -> FtutterRow.HiddenRow(i, depth)
            i in hidden.ng -> FtutterRow.HiddenRow(i, depth, isNg = true)
            else -> FtutterRow.PostRow(i, depth)
        }
    }
    if (!markPlaced) rows += FtutterRow.ReadMark
    // 置換行は、ツリー表示で直後に字下げされた（見えている）返信がある時だけ残す。置換行の下の置換行が消えると
    // 親も残す理由が無くなるので、変わらなくなるまで繰り返す。
    if (!settings.isTree) return rows.filter { it !is FtutterRow.HiddenRow }
    var current: List<FtutterRow> = rows
    while (true) {
        val next = current.filterIndexed { at, row ->
            if (row !is FtutterRow.HiddenRow) return@filterIndexed true
            val following = current.drop(at + 1).firstOrNull { it !is FtutterRow.ReadMark }
            val depth = (following as? FtutterRow.PostRow)?.depth ?: (following as? FtutterRow.HiddenRow)?.depth
            depth != null && depth > row.depth
        }
        if (next.size == current.size) return next
        current = next
    }
}

/** 会話の木のポップアップの中身（そのレスを含む木を、ツリーの深さつきで）。2件未満なら空。 */
internal suspend fun ftutterConversationOf(posts: List<Post>, index: Int): List<Pair<Int, Int>> {
    val group = ftutterConversationGroups(posts).firstOrNull { index in it } ?: return emptyList()
    if (group.size < 2) return emptyList()
    val position = posts.withIndex().associate { (i, p) -> p.id to i }
    return buildThreadTreeNodes(group.sorted().map { posts[it] }).map { position.getValue(it.post.id) to it.depth }
}

/** 「単純な引用を省略」：ツリーの子のレスで、引用の塊が1つだけ解決できている時、引用行を「>....」1行にする。 */
internal fun ftutterBodyLines(post: Post, shorten: Boolean): List<String> {
    val lines = messageHtmlToLines(post.messageHtml)
    if (!shorten) return lines
    val resolved = post.quoteReferences.filter { it.targetPostIds.isNotEmpty() }
    if (resolved.size != 1 || post.quoteReferences.size != 1) return lines
    val out = mutableListOf<String>()
    var inQuote = false
    lines.forEach { line ->
        if (ftutterIsQuoteLine(line)) {
            if (!inQuote) out += ">...."
            inQuote = true
        } else {
            inQuote = false
            out += line
        }
    }
    return out
}

internal fun ftutterIsQuoteLine(line: String): Boolean {
    val first = line.trimStart().firstOrNull() ?: return false
    return first == '>' || first == '＞'
}

/** 引用行が指すレスの番号（その行を含む引用の塊の解決先）。 */
internal fun ftutterQuoteLineTargets(posts: List<Post>, index: Int, line: String): List<Int> {
    val wanted = line.trim()
    if (wanted.isEmpty()) return emptyList()
    val position = posts.withIndex().associate { (i, p) -> p.id to i }
    return posts[index].quoteReferences
        .filter { ref -> ref.text.trim() == wanted || ref.text.lineSequence().any { it.trim() == wanted } }
        .flatMap { it.targetPostIds }.mapNotNull { position[it] }.distinct()
}

// ---- 既読・新着 ----

/**
 * 既読の保存値（`compat.ftutter.seen`）は原作の`kokomade`と同じ「読んだ返信の数」（OPを含まない）で、カタログの未読数と
 * 同じ数え方。スレッド画面は「読んだレスの数」（OPを含む）で帯の位置を決めるので、ここで1つずらす。
 */
internal fun ftutterPostsReadFromStored(stored: Int): Int = stored + 1
internal fun ftutterStoredFromPostsRead(postsRead: Int): Int = (postsRead - 1).coerceAtLeast(0)

/** 既読の件数は小さい値で上書きしない（原作の`kokomade`）。 */
internal fun ftutterReadCountAfter(previous: Int?, seenUpTo: Int): Int = maxOf(previous ?: 0, seenUpTo)

/** 更新の後のお知らせ：「新着 N 件」（設定ON時）と、そのうち自分の書き込みへの返信「返信 N 件」（既定ON）。 */
internal fun ftutterRefreshNotice(
    before: List<Post>,
    after: List<Post>,
    selfIds: Set<String>,
    settings: FtutterThreadSettings
): String? {
    val known = before.map { it.id }.toSet()
    val added = after.filter { it.id !in known }
    if (added.isEmpty()) return null
    val replies = added.count { post -> post.quoteReferences.any { ref -> ref.targetPostIds.any { it in selfIds } } }
    val parts = buildList {
        if (settings.showNewCount) add("新着 ${added.size} 件")
        if (settings.showReplyCount && replies > 0) add("返信 $replies 件")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** 末尾の帯の文と色の種類。 */
internal enum class FtutterEndState { NORMAL, DANGER, ARCHIVED }

internal data class FtutterEndBand(val text: String, val state: FtutterEndState)

internal fun ftutterEndBand(expiresAtLabel: String?, fallen: Boolean, archived: Boolean): FtutterEndBand = when {
    archived -> FtutterEndBand("過去ログ取得済み", FtutterEndState.ARCHIVED)
    fallen -> FtutterEndBand("スレッドは落ちています", FtutterEndState.DANGER)
    else -> {
        val label = expiresAtLabel.orEmpty().trim()
        // 「もうすぐ消えます」の赤い太字は解析器が別に持たないため、文言で判断する。
        FtutterEndBand(label, if (label.contains("もうすぐ")) FtutterEndState.DANGER else FtutterEndState.NORMAL)
    }
}

/** そのレスの画像の表示枠（dp）。OPは最大高さ500、他は300。幅はOP以外で「幅を固定」の時だけ制限する。 */
internal data class FtutterImageBox(val maxWidth: Float?, val maxHeight: Float, val aspect: Float)

internal fun ftutterImageBox(post: Post, isOp: Boolean, settings: FtutterThreadSettings): FtutterImageBox {
    val w = post.thumbnailWidth?.takeIf { it > 0 }
    val h = post.thumbnailHeight?.takeIf { it > 0 }
    val maxHeight = (if (isOp) settings.opImageMaxHeight else settings.imageMaxHeight).toFloat()
    val height = if (h != null) minOf(maxHeight, h.toFloat()) else minOf(maxHeight, 125f)
    val maxWidth = if (!isOp && settings.imageFixedWidth) {
        minOf(settings.imageMaxWidth.toFloat(), (w ?: 125).toFloat())
    } else null
    val aspect = if (w != null && h != null) (w.toFloat() / h).coerceIn(0.1f, 10f) else 1f
    return FtutterImageBox(maxWidth, height, aspect)
}
