package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.aimoge.AIMOGE_DICE_CLASS
import com.valoser.futacha.shared.network.aimoge.AIMOGE_SPOILER_CLASS

enum class AimogeDecorationKind { DICE, SPOILER }

/** A decorated range of one line of [messageHtmlToLines] (end exclusive). */
data class AimogeDecoration(
    val start: Int,
    val end: Int,
    val kind: AimogeDecorationKind,
    /** The decorated characters, checked when applied: the shown text can differ from the one counted (audit P2-L2). */
    val text: String? = null
)

private const val DICE_OPEN = ''
private const val DICE_CLOSE = ''
private const val SPOILER_OPEN = ''
private const val SPOILER_CLOSE = ''
private val decorationTagRegex = Regex("<(/?)(b|span|font)(\\s[^>]*)?>", RegexOption.IGNORE_CASE)

/** Futaba's dice result: `dice2d100=<font color="#ff0000">73 75 (148)</font>` (the font right after `diceNdM=`). */
private val futabaDiceBeforeFont = Regex("""dice\d+d\d+=\s*$""", RegexOption.IGNORE_CASE)

/**
 * The dice and hidden-text ranges of each line of [messageHtmlToLines]`(html)`, or null when [html] has none (every
 * Futaba body). Lines and offsets match [messageHtmlToLines] exactly: the markers only ride along while the tags are
 * stripped and are taken out again.
 */
fun aimogeLineDecorations(
    html: String,
    toLines: (String) -> List<String> = ::messageHtmlToLines
): List<List<AimogeDecoration>>? {
    if (!html.contains(AIMOGE_DICE_CLASS) && !html.contains(AIMOGE_SPOILER_CLASS) && !html.contains("dice", ignoreCase = true)) return null
    // Each open tag with its name: a closing tag closes the latest open tag of its own name, so a stray </font> (or
    // </b>) does not close another kind of marker early (audit 2026-11-17 L4).
    val stack = ArrayList<Pair<String, Char?>>()
    val marked = decorationTagRegex.replace(html) { match ->
        val closing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        val attributes = match.groupValues[3]
        if (!closing) {
            val marker = when {
                name == "b" && attributes.contains("\"$AIMOGE_DICE_CLASS\"") -> DICE_OPEN
                name == "span" && attributes.contains("\"$AIMOGE_SPOILER_CLASS\"") -> SPOILER_OPEN
                name == "font" && futabaDiceBeforeFont.containsMatchIn(html.substring(maxOf(0, match.range.first - 24), match.range.first)) -> DICE_OPEN
                else -> null
            }
            stack.add(name to marker)
            marker?.toString() ?: match.value
        } else {
            val index = stack.indexOfLast { it.first == name }
            when (if (index < 0) null else stack.removeAt(index).second) {
                DICE_OPEN -> DICE_CLOSE.toString()
                SPOILER_OPEN -> SPOILER_CLOSE.toString()
                else -> match.value
            }
        }
    }
    if (marked == html) return null
    return toLines(marked).map { line ->
        val ranges = mutableListOf<AimogeDecoration>()
        val open = mutableMapOf<AimogeDecorationKind, Int>()
        val plain = StringBuilder()
        for (c in line) {
            val offset = plain.length
            when (c) {
                DICE_OPEN -> open[AimogeDecorationKind.DICE] = offset
                SPOILER_OPEN -> open[AimogeDecorationKind.SPOILER] = offset
                DICE_CLOSE -> open.remove(AimogeDecorationKind.DICE)?.let { if (offset > it) ranges += AimogeDecoration(it, offset, AimogeDecorationKind.DICE) }
                SPOILER_CLOSE -> open.remove(AimogeDecorationKind.SPOILER)?.let { if (offset > it) ranges += AimogeDecoration(it, offset, AimogeDecorationKind.SPOILER) }
                else -> plain.append(c)
            }
        }
        open.forEach { (kind, start) -> if (plain.length > start) ranges += AimogeDecoration(start, plain.length, kind) }
        ranges.map { it.copy(text = plain.substring(it.start, it.end)) }
    }
}

/** The annotation tag over hidden text; its item is the spoiler's index in the body. */
const val AIMOGE_SPOILER_TAG = "aimoge-spoiler"

/**
 * The absolute ranges of [aimogeLineDecorations] in a text built from those lines with `\n` between them, each line
 * cut to the length [lineLength] gives (for example `trimEnd`). Spoilers are numbered in body order.
 */
fun aimogeTextDecorations(
    html: String,
    toLines: (String) -> List<String> = ::messageHtmlToLines,
    lineLength: (index: Int, line: String) -> Int = { _, line -> line.trimEnd().length }
): List<AimogeDecoration> {
    val perLine = aimogeLineDecorations(html, toLines) ?: return emptyList()
    val lines = toLines(html)
    val result = mutableListOf<AimogeDecoration>()
    var base = 0
    lines.forEachIndexed { index, line ->
        val length = lineLength(index, line)
        perLine.getOrNull(index)?.forEach { d ->
            val end = minOf(d.end, length)
            if (end > d.start) result += AimogeDecoration(base + d.start, base + end, d.kind, d.text?.take(end - d.start))
        }
        base += length + 1
    }
    return result
}

/**
 * [this] with dice results in bold and hidden text covered until its index is in [revealed]. [decorations] are
 * absolute ranges in [this] ([aimogeTextDecorations]). A Futaba body (no decorations) is returned as it is.
 */
fun androidx.compose.ui.text.AnnotatedString.withAimogeDecorations(
    decorations: List<AimogeDecoration>,
    revealed: Set<Int>,
    coverColor: androidx.compose.ui.graphics.Color,
    firstSpoilerIndex: Int = 0,
    diceColor: androidx.compose.ui.graphics.Color? = DiceColorState.color
): androidx.compose.ui.text.AnnotatedString {
    if (decorations.isEmpty()) return this
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(this)
    var spoilerIndex = firstSpoilerIndex
    decorations.forEach { original ->
        val d = aimogeCheckedRange(text, original)
        // A dice range that no longer matches the shown text gets no bold (it would land on other characters).
        if (d == null && original.kind == AimogeDecorationKind.DICE) return@forEach
        // A spoiler that no longer matches is covered on the safe side: from its start to the end of that line.
        val range = d ?: aimogeSafeSpoilerRange(text, original) ?: return@forEach
        val end = minOf(range.end, length)
        if (end <= range.start) return@forEach
        when (range.kind) {
            AimogeDecorationKind.DICE -> builder.addStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = diceColor ?: androidx.compose.ui.graphics.Color.Unspecified,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ), range.start, end
            )
            AimogeDecorationKind.SPOILER -> {
                val index = spoilerIndex++
                builder.addStringAnnotation(AIMOGE_SPOILER_TAG, index.toString(), range.start, end)
                if (index !in revealed) {
                    builder.addStyle(androidx.compose.ui.text.SpanStyle(color = coverColor, background = coverColor), range.start, end)
                }
            }
        }
    }
    return builder.toAnnotatedString()
}

/**
 * [d] when the shown [shown] still has its characters at its place; else the same characters searched nearest to
 * the place; null when they are not there.
 */
internal fun aimogeCheckedRange(shown: String, d: AimogeDecoration): AimogeDecoration? {
    val expected = d.text ?: return d.takeIf { it.end <= shown.length && it.end > it.start }
    if (expected.isEmpty()) return null
    if (d.end <= shown.length && shown.regionMatches(d.start, expected, 0, expected.length)) return d
    var best = -1
    var from = 0
    while (true) {
        val at = shown.indexOf(expected, from)
        if (at < 0) break
        if (best < 0 || kotlin.math.abs(at - d.start) < kotlin.math.abs(best - d.start)) best = at
        from = at + 1
    }
    return if (best < 0) null else d.copy(start = best, end = best + expected.length)
}

/** The safe cover of a spoiler whose characters are not found: from its place to the end of that line. */
internal fun aimogeSafeSpoilerRange(shown: String, d: AimogeDecoration): AimogeDecoration? {
    if (d.kind != AimogeDecorationKind.SPOILER) return null
    val start = d.start.coerceIn(0, shown.length)
    val lineEnd = shown.indexOf('\n', start).let { if (it < 0) shown.length else it }
    val end = maxOf(lineEnd, minOf(d.end, shown.length))
    return if (end > start) d.copy(start = start, end = end) else null
}

/** The spoiler index at [offset] of [text], or null. */
fun androidx.compose.ui.text.AnnotatedString.aimogeSpoilerAt(offset: Int): Int? =
    getStringAnnotations(AIMOGE_SPOILER_TAG, offset, offset).firstOrNull()?.item?.toIntOrNull()

/** [revealed] with [index] turned the other way (a tap shows hidden text, another tap hides it). */
fun toggleAimogeSpoiler(revealed: Set<Int>, index: Int): Set<Int> = if (index in revealed) revealed - index else revealed + index

/** For each line of [aimogeLineDecorations], the index of its first spoiler in the whole body. */
fun aimogeFirstSpoilerIndexes(perLine: List<List<AimogeDecoration>>): List<Int> {
    var count = 0
    return perLine.map { line -> count.also { count += line.count { it.kind == AimogeDecorationKind.SPOILER } } }
}

private val spoilerSpanRegex = Regex("<span class=\"${AIMOGE_SPOILER_CLASS}\">[^<]*</span>")

/** [html] without its hidden text, for read-aloud (hidden text is never spoken; a Futaba body is unchanged). */
fun aimogeHtmlForSpeech(html: String): String =
    if (!html.contains(AIMOGE_SPOILER_CLASS)) html else html.replace(spoilerSpanRegex, "")
