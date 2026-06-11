package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

private const val QUOTE_ANNOTATION_TAG = "quote"
private const val URL_ANNOTATION_TAG = "url"
private const val THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES = 512
private const val THREAD_MESSAGE_ANNOTATION_CACHE_MAX_BYTES = 2 * 1024 * 1024
private const val THREAD_MESSAGE_URL_ANNOTATION_MAX_MATCHES = 64

private val URL_REGEX = Regex("""https?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val SCHEMELESS_URL_REGEX = Regex("""ttps?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val URL_LINK_TEXT_REGEX = Regex("""URL(?:ﾘﾝｸ|リンク)\(([^)]+)\)""", RegexOption.IGNORE_CASE)

internal val FutabaLinkColor = Color(0xFF800000)

private val urlSpanStyle = SpanStyle(
    color = FutabaLinkColor,
    textDecoration = TextDecoration.Underline
)

private data class UrlMatch(
    val url: String,
    val range: IntRange
)

private data class ThreadMessageQuoteCacheKey(
    val text: String,
    val targetPostIds: List<String>
)

private data class ThreadMessageAnnotationCacheKey(
    val messageHtml: String,
    val quoteReferences: List<ThreadMessageQuoteCacheKey>
)

private class ThreadMessageAnnotationCache(
    private val maxEntries: Int,
    private val maxBytes: Int
) {
    private val entries = LinkedHashMap<ThreadMessageAnnotationCacheKey, AnnotatedString>()
    private var estimatedBytes = 0

    fun get(key: ThreadMessageAnnotationCacheKey): AnnotatedString? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    fun put(key: ThreadMessageAnnotationCacheKey, value: AnnotatedString) {
        entries.remove(key)?.let { removed ->
            estimatedBytes -= estimateEntryBytes(key, removed)
        }
        entries[key] = value
        estimatedBytes += estimateEntryBytes(key, value)
        while (entries.size > maxEntries || estimatedBytes > maxBytes) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            val removed = iterator.next()
            iterator.remove()
            estimatedBytes -= estimateEntryBytes(removed.key, removed.value)
        }
    }

    private fun estimateEntryBytes(
        key: ThreadMessageAnnotationCacheKey,
        value: AnnotatedString
    ): Int {
        val quoteBytes = key.quoteReferences.sumOf { quote ->
            quote.text.length + quote.targetPostIds.sumOf { it.length }
        }
        return (key.messageHtml.length + value.text.length + quoteBytes) * 2
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
    modifier: Modifier = Modifier
) {
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f)
    )
    val annotationCacheKey = remember(messageHtml, quoteReferences) {
        buildThreadMessageAnnotationCacheKey(messageHtml, quoteReferences)
    }
    val baseAnnotated: AnnotatedString by produceState(
        initialValue = threadMessageAnnotationBaseCache.get(annotationCacheKey) ?: AnnotatedString(""),
        key1 = annotationCacheKey,
        key2 = messageHtml,
        key3 = quoteReferences
    ) {
        threadMessageAnnotationBaseCache.get(annotationCacheKey)?.let {
            value = it
            return@produceState
        }
        if (messageHtml.isBlank()) {
            value = AnnotatedString("")
            return@produceState
        }
        value = withContext(AppDispatchers.textAnnotation) {
            buildAnnotatedMessageBase(messageHtml, quoteReferences)
        }
        threadMessageAnnotationBaseCache.put(annotationCacheKey, value)
    }
    val annotated = remember(baseAnnotated, highlightRanges, highlightStyle) {
        applyHighlightsToAnnotatedMessage(baseAnnotated, highlightRanges, highlightStyle)
    }

    val textColor = if (isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = modifier.pointerInput(annotated, quoteReferences, onUrlClick) {
            detectTapGestures(
                onLongPress = { position ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(position)
                    val quoteIndex = annotated
                        .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                    quoteIndex
                        ?.let { index -> quoteReferences.getOrNull(index) }
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                        ?.let(onQuoteClick)
                },
                onTap = { position ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(position)
                    val url = annotated
                        .getStringAnnotations(URL_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                    if (url != null) {
                        onUrlClick(url)
                        return@detectTapGestures
                    }
                    val quoteIndex = annotated
                        .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                    quoteIndex
                        ?.let { index -> quoteReferences.getOrNull(index) }
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                        ?.let(onQuoteClick)
                }
            )
        },
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        onTextLayout = { textLayoutResult = it }
    )
}

private fun buildThreadMessageAnnotationCacheKey(
    messageHtml: String,
    quoteReferences: List<QuoteReference>
): ThreadMessageAnnotationCacheKey {
    return ThreadMessageAnnotationCacheKey(
        messageHtml = messageHtml,
        quoteReferences = quoteReferences.map { reference ->
            ThreadMessageQuoteCacheKey(
                text = reference.text,
                targetPostIds = reference.targetPostIds.toList()
            )
        }
    )
}

private fun buildAnnotatedMessageBase(
    html: String,
    quoteReferences: List<QuoteReference>
): AnnotatedString {
    val lines = messageHtmlToLines(html)
    var referenceIndex = 0
    val urlMatches = mutableListOf<UrlMatch>()
    val built = buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val content = line.trimEnd()
            val isQuote = content.startsWith(">") || content.startsWith("＞")
            if (isQuote) {
                val spanStyle = SpanStyle(color = FutabaQuoteGreen, fontWeight = FontWeight.SemiBold)
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
            style = urlSpanStyle,
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
