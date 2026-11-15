package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.PostAttachment
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

// ---- The parts of the JSON the app reads (unknown keys are ignored). ----

@Serializable
internal data class AimogeAttachmentDto(
    val id: String? = null,
    val kind: String? = null,
    val mime: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val originalUrl: String? = null,
    val thumbnailUrl: String? = null,
    val isOekaki: Boolean = false
)

@Serializable
internal data class AimogePostDto(
    /** Null on a `/state` row for a post that cannot be read now (`{"seq":8,"status":"unavailable"}`). */
    val id: String? = null,
    val threadId: String? = null,
    val seq: Long = 0,
    val postSequenceNo: Long? = null,
    val status: String? = null,
    val body: String = "",
    val createdAt: String? = null,
    val attachment: AimogeAttachmentDto? = null,
    val attachments: List<AimogeAttachmentDto> = emptyList(),
    val sodaneCount: Int = 0,
    val displayId: String? = null,
    val sage: Boolean = false,
    val poll: AimogePollDto? = null,
    /** The votes, from `/state` `postStates[].poll` (never in `/thread`); filled by the repository. */
    @kotlinx.serialization.Transient val pollState: AimogePollStateDto? = null
)

@Serializable
internal data class AimogePollCountDto(val optionId: String = "", val voteCount: Long = 0)

@Serializable
internal data class AimogePollResultsDto(val totalVotes: Long = 0, val options: List<AimogePollCountDto> = emptyList())

@Serializable
internal data class AimogePollStateDto(
    val isClosed: Boolean = false,
    val selectedOptionId: String? = null,
    val results: AimogePollResultsDto? = null
)

@Serializable
internal data class AimogePollOptionDto(val id: String = "", val label: String = "")

/** A poll's definition (the votes are only in `/state`). Shown as lines; no answer button (J15). */
@Serializable
internal data class AimogePollDto(val closesAt: String? = null, val options: List<AimogePollOptionDto> = emptyList())

@Serializable
internal data class AimogeTagDto(val name: String = "", val source: String? = null)

@Serializable
internal data class AimogeThreadDto(
    val id: String,
    val opPost: AimogePostDto? = null,
    val replyCount: Int = 0,
    val createdAt: String? = null,
    val bumpedAt: String? = null,
    val expiresAt: String? = null,
    val isPermanent: Boolean = false,
    val allowImageReplies: Boolean? = null,
    val threadNgWords: List<String> = emptyList(),
    val closedAt: String? = null,
    val tags: List<AimogeTagDto> = emptyList(),
    val posts: List<AimogePostDto> = emptyList()
)

/** Whether the thread carries the R18 tag (set by its poster or a moderator; the AI's genres are not shown). */
internal fun AimogeThreadDto.isR18(): Boolean = tags.any { it.name.equals("R18", ignoreCase = true) }

/** The mark put before an R18 thread's title in the catalog. */
internal const val AIMOGE_R18_MARK = "[R18] "

@Serializable
internal data class AimogeCatalogDto(val sort: String? = null, val threads: List<AimogeThreadDto> = emptyList())

@Serializable
internal data class AimogeBoardPresentationDto(
    val defaultVisible: Boolean = true,
    val defaultEnabled: Boolean = true,
    val imageUrl: String? = null
)

@Serializable
internal data class AimogeBoardDto(
    val slug: String,
    val boardName: String? = null,
    val allowR18ThreadCreation: Boolean = false,
    val presentation: AimogeBoardPresentationDto? = null
)

@Serializable
internal data class AimogeBoardsDto(val boards: List<AimogeBoardDto> = emptyList())

@Serializable
internal data class AimogeSnapshotDto(val thread: AimogeThreadDto? = null, val posts: List<AimogePostDto> = emptyList())

@Serializable
internal data class AimogeReactionsDto(val up: Int? = null, val `super`: Int? = null)

@Serializable
internal data class AimogePostStateDto(
    val seq: Long,
    val status: String? = null,
    val reactions: AimogeReactionsDto? = null,
    val poll: AimogePollStateDto? = null
)

@Serializable
internal data class AimogeStateDto(
    val archivedAt: String? = null,
    val newPosts: List<AimogePostDto> = emptyList(),
    val postStates: List<AimogePostStateDto> = emptyList()
)

@Serializable
internal data class AimogeSummaryDto(val threads: List<AimogeThreadDto> = emptyList())

internal val aimogeJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

// ---- Private-use markup (dice results, hidden text). ----

private const val PUA_OPEN = ''
private const val PUA_CLOSE = ''
private const val PUA_HIDDEN = ''

/** A body with its markup taken apart: [shown] for the screen, [plain] for search, NG and copy, [spoken] for read-aloud. */
data class AimogeBodyText(val shown: String, val plain: String, val spoken: String)

/**
 * Splits [body]. Well-formed ranges (open + kind … close + same kind) lose their markers; a hidden range is kept in
 * [AimogeBodyText.plain] (NG matches it) and left out of [AimogeBodyText.spoken]. A broken body loses every
 * private-use character and is shown as plain text.
 */
fun aimogeBodyText(body: String): AimogeBodyText {
    if (body.none { it in ''..'' }) return AimogeBodyText(body, body, body)
    val plain = StringBuilder()
    val spoken = StringBuilder()
    val kinds = ArrayDeque<Char>()
    var broken = false
    var i = 0
    while (i < body.length) {
        val c = body[i]
        when {
            c == PUA_OPEN || c == PUA_CLOSE -> {
                val kind = body.getOrNull(i + 1)
                if (kind == null || kind !in ''..'') { broken = true; break }
                if (c == PUA_OPEN) kinds.addLast(kind)
                else if (kinds.removeLastOrNull() != kind) { broken = true; break }
                i += 2
                continue
            }
            c in ''..'' -> { broken = true; break }
            else -> {
                plain.append(c)
                if (PUA_HIDDEN !in kinds) spoken.append(c)
            }
        }
        i += 1
    }
    if (broken || kinds.isNotEmpty()) {
        val stripped = body.filterNot { it in ''..'' }
        return AimogeBodyText(stripped, stripped, stripped)
    }
    val text = plain.toString()
    return AimogeBodyText(text, text, spoken.toString())
}

/** The thread title: the first non-blank line of the OP body without markup, not cut. Null when there is none. */
fun aimogeThreadTitle(opBody: String?): String? =
    opBody?.let { aimogeBodyText(it).plain }?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

private fun escapeHtml(text: String): String = buildString(text.length + 16) {
    for (c in text) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
    }
}

private const val PUA_DICE = '\uE100'

/** One run of body text and whether it is a dice result and/or hidden text. */
internal data class AimogeBodyRun(val text: String, val dice: Boolean, val hidden: Boolean)

/**
 * [body] cut into runs by its decorations. A broken body (see [aimogeBodyText]) is one plain run without any
 * private-use character, so it is shown undecorated.
 */
internal fun aimogeBodyRuns(body: String): List<AimogeBodyRun> {
    if (body.none { it in '\uE000'..'\uF8FF' }) return listOf(AimogeBodyRun(body, dice = false, hidden = false))
    val runs = mutableListOf<AimogeBodyRun>()
    val kinds = ArrayDeque<Char>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) runs += AimogeBodyRun(current.toString(), PUA_DICE in kinds, PUA_HIDDEN in kinds)
        current.clear()
    }
    var i = 0
    while (i < body.length) {
        val c = body[i]
        if (c == PUA_OPEN || c == PUA_CLOSE) {
            val kind = body.getOrNull(i + 1)
            if (kind == null || kind !in '\uE000'..'\uF8FF' || (c != PUA_OPEN && kinds.lastOrNull() != kind)) return listOf(AimogeBodyRun(body.filterNot { it in '\uE000'..'\uF8FF' }, dice = false, hidden = false))
            flush()
            if (c == PUA_OPEN) kinds.addLast(kind) else kinds.removeLast()
            i += 2
            continue
        }
        if (c in '\uE000'..'\uF8FF') return listOf(AimogeBodyRun(body.filterNot { it in '\uE000'..'\uF8FF' }, dice = false, hidden = false))
        current.append(c)
        i += 1
    }
    if (kinds.isNotEmpty()) return listOf(AimogeBodyRun(body.filterNot { it in '\uE000'..'\uF8FF' }, dice = false, hidden = false))
    flush()
    return runs
}

/** The class of the tag around a dice result in [aimogeMessageHtml]. */
const val AIMOGE_DICE_CLASS = "aimoge-dice"
/** The class of the tag around hidden text in [aimogeMessageHtml]. */
const val AIMOGE_SPOILER_CLASS = "aimoge-spoiler"

/**
 * The body as the Futaba-shaped HTML the screens read: escaped, quote lines green, lines joined with `<br>`. A dice
 * result is wrapped in `<b class="aimoge-dice">` and hidden text in `<span class="aimoge-spoiler">` (per line), so
 * the text without tags (search, NG, copy) is unchanged and the screens can decorate those ranges.
 */
/** Where a tap on a poll option goes: handled inside the app ([AimogePollLinks]), never opened in a browser. */
const val AIMOGE_POLL_LINK_PREFIX = "https://nijiurachan.net/#futacha-poll/"

/**
 * The poll under the body, laid out as the Web version does (checked 2026-11-12, thread 01a1cd5d #72):
 * 「アンケート」, then 「残りN分 · 締切 M/d HH:mm」 (「回答受付は終了しました」 once closed). While it can be answered each
 * option is a link that answers it (the delete key is asked for at the tap). Once answered here or closed it shows
 * 「現在の結果」/「最終結果」: each option with 「N件 / P%」, 「あなたの回答」 on the chosen one, and 「合計 N件」.
 */
internal fun aimogePollHtml(
    poll: AimogePollDto,
    postId: String? = null,
    state: AimogePollStateDto? = null,
    now: Instant = kotlin.time.Clock.System.now()
): String {
    val options = poll.options.filter { it.label.isNotBlank() }
    if (options.isEmpty()) return ""
    val closesInstant = poll.closesAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val closed = state?.isClosed == true || closesInstant?.let { it <= now } == true
    val answered = state?.selectedOptionId != null
    val results = state?.results
    val showResults = results != null && (closed || answered)
    val canVote = postId != null && !closed && !answered && options.any { it.id.isNotBlank() }
    val deadline = closesInstant?.let { instant ->
        val t = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        "締切 ${t.month.ordinal + 1}/${t.day} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }
    val status = when {
        closed -> "回答受付は終了しました"
        closesInstant != null -> aimogePollRemaining((closesInstant - now).inWholeSeconds)
        else -> null
    }
    val sb = StringBuilder("<br><br>アンケート")
    listOfNotNull(status, deadline).takeIf { it.isNotEmpty() }?.let { sb.append("<br>").append(escapeHtml(it.joinToString(" · "))) }
    if (showResults) {
        val counts = results!!.options.associate { it.optionId to it.voteCount }
        val total = maxOf(0L, results.totalVotes)
        sb.append("<br>").append(if (closed) "最終結果" else "現在の結果")
        for (option in options) {
            val n = maxOf(0L, counts[option.id] ?: 0L)
            val percent = if (total == 0L) 0L else kotlin.math.round(n * 100.0 / total).toLong()
            val mine = if (state?.selectedOptionId == option.id) "（あなたの回答）" else ""
            sb.append("<br>・").append(escapeHtml(option.label.replace('\n', ' '))).append(mine).append("　${n}件 / ${percent}%")
        }
        sb.append("<br>合計 ${total}件")
    } else {
        for (option in options) {
            val label = escapeHtml(option.label.replace('\n', ' '))
            val mine = if (state?.selectedOptionId == option.id) "（あなたの回答）" else ""
            sb.append("<br>・")
            if (canVote && option.id.isNotBlank()) sb.append("<a href=\"$AIMOGE_POLL_LINK_PREFIX$postId/${option.id}\">$label</a>") else sb.append(label).append(mine)
        }
        if (canVote) sb.append("<br>（選択肢を押すと回答できます）")
    }
    return sb.toString()
}

private val aimogePollAnchorRegex = Regex("""<a href="(${Regex.escape(AIMOGE_POLL_LINK_PREFIX)}[^"]+)">(.*?)</a>""")

/**
 * The poll options' links in the text shown from [html] ([text]), as (range, url). ふたちゃ links only URLs written in
 * the text, so the options looked like plain text and could not be pressed (thread 01a1cd5d #74). Each label is
 * searched on its own 「・」 line after the 「アンケート」 line, so the same word earlier in the body is not taken.
 */
fun aimogePollOptionLinks(html: String, text: String): List<Pair<IntRange, String>> {
    if (AIMOGE_POLL_LINK_PREFIX !in html) return emptyList()
    var from = text.lastIndexOf("\nアンケート").takeIf { it >= 0 } ?: return emptyList()
    val out = mutableListOf<Pair<IntRange, String>>()
    for (m in aimogePollAnchorRegex.findAll(html)) {
        val label = com.valoser.futacha.shared.parser.HtmlEntityDecoder.decode(m.groupValues[2])
        if (label.isEmpty()) continue
        val at = text.indexOf("\n・$label", from).takeIf { it >= 0 } ?: continue
        val start = at + 2
        out += (start until start + label.length) to m.groupValues[1]
        from = start + label.length
    }
    return out
}

/** The Web version's remaining time: 残りN秒／分／N時間M分／N日M時間. */
internal fun aimogePollRemaining(seconds: Long): String = when {
    seconds <= 0 -> "終了"
    seconds < 60 -> "残り${seconds}秒"
    else -> {
        val minutes = (seconds + 59) / 60
        if (minutes < 60) "残り${minutes}分" else {
            val hours = minutes / 60; val m = minutes % 60
            if (hours < 24) "残り${hours}時間" + (if (m > 0) "${m}分" else "") else {
                val days = hours / 24; val h = hours % 24
                "残り${days}日" + (if (h > 0) "${h}時間" else "")
            }
        }
    }
}

/** A vote's answer as the `/state` shape. */
internal fun AimogePollState.toDto(): AimogePollStateDto = AimogePollStateDto(
    isClosed = isClosed,
    selectedOptionId = selectedOptionId,
    results = totalVotes?.let { total -> AimogePollResultsDto(total.toLong(), counts.map { AimogePollCountDto(it.key, it.value.toLong()) }) }
)

/**
 * The fetched state with the answer given here laid over it: the public `/state` has no answer and, while the poll
 * is open, no results. Newer results from the server win; the answer and the closing are kept.
 */
internal fun aimogeMergePollState(fetched: AimogePollStateDto?, mine: AimogePollStateDto): AimogePollStateDto = AimogePollStateDto(
    isClosed = (fetched?.isClosed == true) || mine.isClosed,
    selectedOptionId = fetched?.selectedOptionId ?: mine.selectedOptionId,
    results = fetched?.results ?: mine.results
)

internal fun aimogeMessageHtml(body: String): String {
    val lines = mutableListOf(StringBuilder())
    val quoteLines = mutableListOf(false)
    for (run in aimogeBodyRuns(body.replace("\r\n", "\n"))) {
        run.text.split('\n').forEachIndexed { index, piece ->
            if (index > 0) { lines += StringBuilder(); quoteLines += false }
            if (piece.isEmpty()) return@forEachIndexed
            if (lines.last().isEmpty()) quoteLines[quoteLines.lastIndex] = piece.startsWith(">") || piece.startsWith("＞")
            var html = escapeHtml(piece)
            if (run.hidden) html = "<span class=\"$AIMOGE_SPOILER_CLASS\">$html</span>"
            if (run.dice) html = "<b class=\"$AIMOGE_DICE_CLASS\">$html</b>"
            lines.last().append(html)
        }
    }
    return lines.indices.joinToString("<br>") { i ->
        if (quoteLines[i]) "<font color=\"#789922\">${lines[i]}</font>" else lines[i].toString()
    }
}

// ---- Dates. ----

private val jst = FixedOffsetTimeZone(UtcOffset(hours = 9))
private val weekdays = listOf("月", "火", "水", "木", "金", "土", "日")

internal fun aimogeEpochMillis(iso: String?): Long? =
    iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

/** `yy/MM/dd(曜)HH:mm:ss` in JST, as Futaba writes it; the raw text when it cannot be read. */
internal fun aimogeTimestamp(iso: String?): String {
    val instant = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return iso.orEmpty()
    val t = instant.toLocalDateTime(jst)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(t.year % 100)}/${two(t.month.ordinal + 1)}/${two(t.day)}(${weekdays[t.dayOfWeek.ordinal]})" +
        "${two(t.hour)}:${two(t.minute)}:${two(t.second)}"
}

/** What a closed thread says in place of its end time. */
internal const val AIMOGE_CLOSED_LABEL = "このスレは閉じられています"

/** How near the end a thread says 「もうすぐ消えます」 instead of the time. */
internal const val AIMOGE_EXPIRY_SOON_MILLIS = 10 * 60_000L

/**
 * The end of the thread in the device's time zone, 「M/d(E) HH:mm頃消えます」, or 「もうすぐ消えます」 within
 * [AIMOGE_EXPIRY_SOON_MILLIS]; null with no time or once it has fallen.
 */
internal fun aimogeExpiryLabel(
    expiresAtMillis: Long?,
    nowMillis: Long,
    zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault()
): String? {
    val expires = expiresAtMillis ?: return null
    if (expires <= nowMillis) return null
    if (expires - nowMillis <= AIMOGE_EXPIRY_SOON_MILLIS) return "もうすぐ消えます"
    val t = Instant.fromEpochMilliseconds(expires).toLocalDateTime(zone)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.month.ordinal + 1}/${t.day}(${weekdays[t.dayOfWeek.ordinal]}) ${two(t.hour)}:${two(t.minute)}頃消えます"
}

// ---- Conversion. ----

internal fun AimogePostDto.number(): Long = postSequenceNo ?: seq

internal fun AimogePostDto.allAttachments(): List<AimogeAttachmentDto> =
    attachments.ifEmpty { listOfNotNull(attachment) }.filter { !it.originalUrl.isNullOrBlank() }

private fun AimogeAttachmentDto.toModel(): PostAttachment = PostAttachment(
    url = originalUrl.orEmpty(),
    thumbnailUrl = thumbnailUrl,
    width = width,
    height = height,
    mimeType = mime
)

/** Post statuses are public | shadowed (spec 2026-11-11); only a `/state` row says "unavailable" (no "deleted"). */
internal fun AimogePostDto.isRemoved(): Boolean = status == "unavailable"

internal fun AimogePostDto.toPost(order: Int): Post {
    val files = allAttachments()
    val first = files.firstOrNull()
    val idText = displayId?.takeIf { it.isNotBlank() }?.let { "ID:$it" }
    return Post(
        id = number().toString(),
        order = order,
        author = null,
        subject = null,
        timestamp = aimogeTimestamp(createdAt) + (idText?.let { " $it" } ?: ""),
        posterId = idText,
        messageHtml = aimogeMessageHtml(body) + (poll?.let { aimogePollHtml(it, id, pollState) } ?: ""),
        imageUrl = first?.originalUrl,
        thumbnailUrl = first?.thumbnailUrl,
        saidaneLabel = if (sodaneCount > 0) "そうだねx$sodaneCount" else null,
        isDeleted = isRemoved(),
        mail = if (sage) "sage" else null,
        thumbnailWidth = first?.let { aimogeThumbnailBox(it.width, it.height)?.first },
        thumbnailHeight = first?.let { aimogeThumbnailBox(it.width, it.height)?.second },
        // Every file, the first included (the contract of Post.attachments). The readers take the 2nd and later from
        // here, so giving only the extras hid the 2nd of two pictures and the 2nd of three (2026-11-12 report).
        attachments = if (files.size > 1) files.map { it.toModel() } else emptyList(),
        siteUuid = id,
        displayNumber = seq.toInt()
    )
}

/** The posts in seq order with references rebuilt; removed posts the server skipped are simply not there. */
internal fun aimogePosts(posts: List<AimogePostDto>): List<Post> {
    // A row without an id is an unreadable post: treated as a missing seq (no row).
    val sorted = posts.filter { it.id != null }.sortedBy { it.seq }
    return ThreadHtmlParserCore.rebuildReferences(sorted.mapIndexed { index, dto -> dto.toPost(index) })
}

internal fun aimogeThreadPage(thread: AimogeThreadDto, posts: List<AimogePostDto>, nowMillis: Long, boardTitle: String? = null): ThreadPage {
    val expires = aimogeEpochMillis(thread.expiresAt)
    val closed = aimogeEpochMillis(thread.closedAt)
    val archived = !thread.isPermanent && expires != null && expires <= nowMillis
    return ThreadPage(
        threadId = thread.id,
        boardTitle = boardTitle,
        // A closed thread says so where every mode shows the end of the thread (no new header row, so no list index moves).
        expiresAtLabel = if (closed != null && !archived) AIMOGE_CLOSED_LABEL else if (thread.isPermanent) null else aimogeExpiryLabel(expires, nowMillis),
        deletedNotice = null,
        posts = aimogePosts(posts),
        expiresAtEpochMillis = expires,
        closedAtEpochMillis = closed,
        isPermanent = thread.isPermanent,
        archived = archived,
        allowImageReplies = thread.allowImageReplies,
        threadNgWords = thread.threadNgWords,
        maxSeq = posts.maxOfOrNull { it.seq }
    )
}

internal fun AimogeThreadDto.toCatalogItem(threadUrl: String): CatalogItem {
    val op = opPost
    val first = op?.allAttachments()?.firstOrNull()
    return CatalogItem(
        id = id,
        threadUrl = threadUrl,
        title = aimogeThreadTitle(op?.body)?.let { if (isR18()) AIMOGE_R18_MARK + it else it },
        thumbnailUrl = first?.thumbnailUrl,
        fullImageUrl = first?.originalUrl,
        thumbnailWidth = first?.let { aimogeThumbnailBox(it.width, it.height)?.first },
        thumbnailHeight = first?.let { aimogeThumbnailBox(it.width, it.height)?.second },
        replyCount = replyCount,
        expiresAtEpochMillis = if (isPermanent) null else aimogeEpochMillis(expiresAt),
        siteOrder = op?.number()
    )
}

internal fun decodeAimogeCatalog(body: String): AimogeCatalogDto = aimogeJson.decodeFromString(AimogeCatalogDto.serializer(), body)
internal fun decodeAimogeThread(body: String): AimogeThreadDto = aimogeJson.decodeFromString(AimogeThreadDto.serializer(), body)
internal fun decodeAimogeSnapshot(body: String): AimogeSnapshotDto = aimogeJson.decodeFromString(AimogeSnapshotDto.serializer(), body)

internal fun decodeAimogeState(body: String): AimogeStateDto = aimogeJson.decodeFromString(AimogeStateDto.serializer(), body)

/** `summary` answers a list of catalog-shaped threads; both a bare array and `{"threads":[…]}` are read. */
internal fun decodeAimogeSummary(body: String): List<AimogeThreadDto> =
    if (body.trimStart().startsWith("[")) {
        aimogeJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AimogeThreadDto.serializer()), body)
    } else {
        aimogeJson.decodeFromString(AimogeSummaryDto.serializer(), body).threads
    }


/** あいもげ's thumbnails fit in a 250×250 box, the same as Futaba's (checked on the live server, 2026-11-12). */
internal const val AIMOGE_THUMBNAIL_BOX_PX = 250

/**
 * The size of the thumbnail of an [width]×[height] picture. The API gives the original's size; passing that as the
 * thumbnail's made ふたばー風 (thumbnail × 0.7) stretch a 250px thumbnail to the screen (thread 01a1cd5d #68).
 */
internal fun aimogeThumbnailBox(width: Int?, height: Int?): Pair<Int, Int>? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val scale = minOf(1.0, AIMOGE_THUMBNAIL_BOX_PX.toDouble() / maxOf(width, height))
    return maxOf(1, kotlin.math.round(width * scale).toInt()) to maxOf(1, kotlin.math.round(height * scale).toInt())
}
