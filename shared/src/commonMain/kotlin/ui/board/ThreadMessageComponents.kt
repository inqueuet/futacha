package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import kotlinx.coroutines.sync.Mutex

private const val QUOTE_ANNOTATION_TAG = "quote"
private const val URL_ANNOTATION_TAG = "url"
private const val THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES = 512
private const val THREAD_MESSAGE_ANNOTATION_CACHE_MAX_BYTES = 2 * 1024 * 1024
private const val THREAD_MESSAGE_URL_ANNOTATION_MAX_MATCHES = 64
private const val THREAD_MESSAGE_QUOTE_REFERENCE_MAX_COUNT = 512
private const val THREAD_MESSAGE_QUOTE_TARGET_MAX_COUNT = 256

private val URL_REGEX = Regex("""https?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val SCHEMELESS_URL_REGEX = Regex("""ttps?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val URL_LINK_TEXT_REGEX = Regex("""URL(?:ﾘﾝｸ|リンク)\(([^)]+)\)""", RegexOption.IGNORE_CASE)

private data class UrlMatch(
    val url: String,
    val range: IntRange
)

private data class ThreadMessageQuoteCacheKey(
    val text: String,
    val targetPostIds: List<String>
)

private class ThreadMessageTextLayoutHolder {
    var value: TextLayoutResult? = null
}

private data class ThreadMessageAnnotationCacheKey(
    val messageHtml: String,
    val quoteReferences: List<ThreadMessageQuoteCacheKey>,
    val quoteColor: Color,
    val linkColor: Color
)

private class ThreadMessageAnnotationCache(
    private val maxEntries: Int,
    private val maxBytes: Int
) {
    private val entries = LinkedHashMap<ThreadMessageAnnotationCacheKey, AnnotatedString>()
    private var estimatedBytes = 0L
    private val mutex = Mutex()

    fun peek(key: ThreadMessageAnnotationCacheKey): AnnotatedString? {
        if (!mutex.tryLock()) return null
        // Move a hit to the end so eviction stays least-recently-used.
        return try { entries.remove(key)?.also { entries[key] = it } } finally { mutex.unlock() }
    }

    fun getOrBuild(key: ThreadMessageAnnotationCacheKey, build: () -> AnnotatedString): AnnotatedString {
        peek(key)?.let { return it }
        val value = build()
        if (mutex.tryLock()) {
            try { putLocked(key, value) } finally { mutex.unlock() }
        }
        return value
    }

    private fun putLocked(key: ThreadMessageAnnotationCacheKey, value: AnnotatedString) {
        entries.remove(key)?.let { removed ->
            estimatedBytes -= estimateEntryBytes(key, removed)
        }
        entries[key] = value
        estimatedBytes += estimateEntryBytes(key, value)
        while (entries.size > maxEntries || estimatedBytes > maxBytes) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            // Read the entry before removing it: Kotlin/Native entries
            // throw ConcurrentModificationException once removed.
            val removed = iterator.next()
            val removedBytes = estimateEntryBytes(removed.key, removed.value)
            iterator.remove()
            estimatedBytes -= removedBytes
        }
    }

    private fun estimateEntryBytes(
        key: ThreadMessageAnnotationCacheKey,
        value: AnnotatedString
    ): Long {
        val quoteChars = key.quoteReferences.fold(0L) { total, quote ->
            quote.targetPostIds.fold(total + quote.text.length.toLong()) { nestedTotal, id ->
                (nestedTotal + id.length.toLong()).coerceAtMost(Long.MAX_VALUE / 2L)
            }
        }
        val totalChars = (key.messageHtml.length.toLong() + value.text.length.toLong() + quoteChars)
            .coerceAtMost(Long.MAX_VALUE / 2L)
        return totalChars * 2L
    }
}

private val threadMessageAnnotationBaseCache = ThreadMessageAnnotationCache(
    maxEntries = THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES,
    maxBytes = THREAD_MESSAGE_ANNOTATION_CACHE_MAX_BYTES
)

@Composable
internal fun ThreadMessageText(
    messageHtml: String,
    isDeleted: Boolean,
    quoteReferences: List<QuoteReference>,
    onQuoteClick: (QuoteReference) -> Unit,
    onUrlClick: (String) -> Unit,
    highlightRanges: List<IntRange> = emptyList(),
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    // The body's tap detector consumes every down, so the post card's own
    // detector never sees presses over the text. Presses that don't land on a
    // link or quote are handed back through these instead.
    onPlainLongPress: (() -> Unit)? = null,
    onPlainTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val latestPlainLongPress = rememberUpdatedState(onPlainLongPress)
    val latestPlainTap = rememberUpdatedState(onPlainTap)
    val threadColors = LocalFutabaThreadColors.current
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f)
    )
    val annotationCacheKey = remember(
        messageHtml,
        quoteReferences,
        threadColors.quote,
        threadColors.link
    ) {
        buildThreadMessageAnnotationCacheKey(
            messageHtml,
            quoteReferences,
            quoteColor = threadColors.quote,
            linkColor = threadColors.link
        )
    }
    val baseAnnotated = remember(annotationCacheKey) {
        threadMessageAnnotationBaseCache.getOrBuild(annotationCacheKey) {
            buildAnnotatedMessageBase(messageHtml,
                quoteReferences.take(THREAD_MESSAGE_QUOTE_REFERENCE_MAX_COUNT),
                quoteColor = threadColors.quote, linkColor = threadColors.link)
        }
    }
    val annotated = remember(baseAnnotated, isDeleted, highlightRanges, highlightStyle) {
        val withNotices = if (isDeleted) AnnotatedString.Builder(baseAnnotated).apply {
            com.valoser.futacha.shared.model.postDeletionNoticeRanges(baseAnnotated.text).forEach { range ->
                addStyle(SpanStyle(color = Color.Red), range.first, range.last + 1)
            }
        }.toAnnotatedString() else baseAnnotated
        applyHighlightsToAnnotatedMessage(withNotices, highlightRanges, highlightStyle)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val baseTextStyle = MaterialTheme.typography.bodyMedium
    val textSizeTokens = remember(bodyTextSize, baseTextStyle.fontSize, baseTextStyle.lineHeight) {
        resolveThreadTextSizeTokens(
            size = bodyTextSize,
            standardFontSize = baseTextStyle.fontSize,
            standardLineHeight = baseTextStyle.lineHeight,
            fallbackFontSize = 14.sp,
            fallbackLineHeight = 20.sp
        )
    }
    val textLayoutResult = remember { ThreadMessageTextLayoutHolder() }
    Text(
        modifier = modifier.pointerInput(annotated, quoteReferences, onUrlClick) {
            detectTapGestures(
                onLongPress = { position ->
                    val layout = textLayoutResult.value
                    val offset = layout?.getOffsetForPosition(position)
                    val quote = offset?.let {
                        annotated
                            .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                            .firstOrNull()
                            ?.item
                            ?.toIntOrNull()
                    }
                        ?.let { index -> quoteReferences.getOrNull(index) }
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                    if (quote != null) {
                        AnalyticsTracker.uiControl("thread_message", "レス内の引用を長押しで開く")
                        onQuoteClick(quote)
                    } else {
                        latestPlainLongPress.value?.invoke()
                    }
                },
                onTap = { position ->
                    val layout = textLayoutResult.value
                    if (layout == null) {
                        latestPlainTap.value?.invoke()
                        return@detectTapGestures
                    }
                    val offset = layout.getOffsetForPosition(position)
                    val url = annotated
                        .getStringAnnotations(URL_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                    if (url != null) {
                        AnalyticsTracker.uiControl("thread_message", "レス本文のURLを選択")
                        onUrlClick(url)
                        return@detectTapGestures
                    }
                    val quote = annotated
                        .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                        ?.let { index -> quoteReferences.getOrNull(index) }
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                    if (quote != null) {
                        AnalyticsTracker.uiControl("thread_message", "レス内の引用を開く")
                        onQuoteClick(quote)
                    } else {
                        latestPlainTap.value?.invoke()
                    }
                }
            )
        },
        text = annotated,
        style = baseTextStyle.copy(
            color = textColor,
            fontSize = textSizeTokens.fontSize,
            lineHeight = textSizeTokens.lineHeight
        ),
        onTextLayout = { textLayoutResult.value = it }
    )
}

internal data class ThreadTextSizeTokens(
    val fontSize: TextUnit,
    val lineHeight: TextUnit
)

internal fun resolveThreadTextSizeTokens(
    size: ThreadBodyTextSize,
    standardFontSize: TextUnit,
    standardLineHeight: TextUnit,
    fallbackFontSize: TextUnit,
    fallbackLineHeight: TextUnit
): ThreadTextSizeTokens {
    val fontSizeDelta = when (size) {
        ThreadBodyTextSize.Small -> -1f
        ThreadBodyTextSize.Standard -> 0f
        ThreadBodyTextSize.Large -> 3f
        ThreadBodyTextSize.ExtraLarge -> 5f
    }
    val lineHeightDelta = when (size) {
        ThreadBodyTextSize.Small -> -2f
        ThreadBodyTextSize.Standard -> 0f
        ThreadBodyTextSize.Large -> 4f
        ThreadBodyTextSize.ExtraLarge -> 7f
    }
    return ThreadTextSizeTokens(
        fontSize = standardFontSize.applyThreadTextSizeDelta(fallbackFontSize, fontSizeDelta, minValue = 10f),
        lineHeight = standardLineHeight.applyThreadTextSizeDelta(fallbackLineHeight, lineHeightDelta, minValue = 12f)
    )
}

internal fun TextStyle.withThreadTextSize(
    bodyTextSize: ThreadBodyTextSize,
    fallbackFontSize: TextUnit,
    fallbackLineHeight: TextUnit
): TextStyle {
    val tokens = resolveThreadTextSizeTokens(
        size = bodyTextSize,
        standardFontSize = fontSize,
        standardLineHeight = lineHeight,
        fallbackFontSize = fallbackFontSize,
        fallbackLineHeight = fallbackLineHeight
    )
    return copy(fontSize = tokens.fontSize, lineHeight = tokens.lineHeight)
}

private fun TextUnit.applyThreadTextSizeDelta(
    fallback: TextUnit,
    delta: Float,
    minValue: Float
): TextUnit {
    val base = takeOrFallback(fallback)
    return (base.value + delta).coerceAtLeast(minValue).sp
}

private fun TextUnit.takeOrFallback(fallback: TextUnit): TextUnit {
    return if (this == TextUnit.Unspecified) fallback else this
}

private fun buildThreadMessageAnnotationCacheKey(
    messageHtml: String,
    quoteReferences: List<QuoteReference>,
    quoteColor: Color,
    linkColor: Color
): ThreadMessageAnnotationCacheKey {
    return ThreadMessageAnnotationCacheKey(
        messageHtml = messageHtml,
        quoteReferences = quoteReferences.take(THREAD_MESSAGE_QUOTE_REFERENCE_MAX_COUNT).map { reference ->
            ThreadMessageQuoteCacheKey(
                text = reference.text,
                targetPostIds = reference.targetPostIds.take(THREAD_MESSAGE_QUOTE_TARGET_MAX_COUNT)
            )
        },
        quoteColor = quoteColor,
        linkColor = linkColor
    )
}

private fun buildAnnotatedMessageBase(
    html: String,
    quoteReferences: List<QuoteReference>,
    quoteColor: Color,
    linkColor: Color
): AnnotatedString {
    val lines = messageHtmlToLines(html)
    var referenceIndex = 0
    val urlMatches = mutableListOf<UrlMatch>()
    val built = buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val content = line.trimEnd()
            val isQuote = content.startsWith(">") || content.startsWith("＞")
            if (isQuote) {
                val spanStyle = SpanStyle(color = quoteColor, fontWeight = FontWeight.SemiBold)
                val reference = quoteReferences.getOrNull(referenceIndex)
                if (reference != null && reference.targetPostIds.isNotEmpty()) {
                    pushStringAnnotation(QUOTE_ANNOTATION_TAG, referenceIndex.toString())
                    appendStyledText(content, spanStyle)
                    pop()
                    referenceIndex += 1
                } else {
                    appendStyledText(content, spanStyle)
                }
            } else {
                appendStyledText(content, SpanStyle())
            }
            if (index != lines.lastIndex) {
                append("\n")
            }
        }
    }
    val builtText = built.toString()
    urlMatches.addThreadMessageUrlMatches(URL_REGEX.findAll(builtText)) { match ->
        UrlMatch(url = match.value, range = match.range)
    }
    urlMatches.addThreadMessageUrlMatches(SCHEMELESS_URL_REGEX.findAll(builtText)) { match ->
        UrlMatch(url = "h${match.value}", range = match.range)
    }
    urlMatches.addThreadMessageUrlMatches(URL_LINK_TEXT_REGEX.findAll(builtText)) { match ->
        val target = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: return@addThreadMessageUrlMatches null
        val normalized = if (target.startsWith("http")) target else "https://$target"
        UrlMatch(url = normalized, range = match.range)
    }
    val distinct = urlMatches
        .sortedBy { it.range.first }
        .distinctBy { it.range.first to it.range.last }
    val builder = AnnotatedString.Builder(built)
    distinct.forEach { match ->
        builder.addStringAnnotation(
            tag = URL_ANNOTATION_TAG,
            annotation = match.url,
            start = match.range.first,
            end = match.range.last + 1
        )
        builder.addStyle(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
    }
    return builder.toAnnotatedString()
}

private inline fun MutableList<UrlMatch>.addThreadMessageUrlMatches(
    matches: Sequence<MatchResult>,
    transform: (MatchResult) -> UrlMatch?
) {
    for (match in matches) {
        if (size >= THREAD_MESSAGE_URL_ANNOTATION_MAX_MATCHES) return
        transform(match)?.let(::add)
    }
}

private fun applyHighlightsToAnnotatedMessage(
    base: AnnotatedString,
    highlightRanges: List<IntRange>,
    highlightStyle: SpanStyle
): AnnotatedString {
    if (highlightRanges.isEmpty()) return base
    val normalizedHighlights = highlightRanges
        .filter { it.first >= 0 && it.last >= it.first }
        .sortedBy { it.first }
    if (normalizedHighlights.isEmpty()) return base
    if (base.text.isEmpty()) return base

    val builder = AnnotatedString.Builder(base)
    val textLastIndex = base.text.lastIndex
    normalizedHighlights.forEach { range ->
        val start = range.first.coerceIn(0, textLastIndex)
        val endExclusive = (range.last + 1).coerceIn(start + 1, base.text.length)
        builder.addStyle(highlightStyle, start, endExclusive)
    }
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendStyledText(
    text: String,
    style: SpanStyle
) {
    if (text.isEmpty()) return
    withStyle(style) {
        append(text)
    }
}
