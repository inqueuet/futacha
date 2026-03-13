package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

private const val QUOTE_ANNOTATION_TAG = "quote"
private const val URL_ANNOTATION_TAG = "url"
private const val THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES = 512

private val URL_REGEX = Regex("""https?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val SCHEMELESS_URL_REGEX = Regex("""ttps?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val URL_LINK_TEXT_REGEX = Regex("""URL(?:ﾘﾝｸ|リンク)\(([^)]+)\)""", RegexOption.IGNORE_CASE)

private val FutabaQuoteGreen = Color(0xFF789922)
private val FutabaLinkColor = Color(0xFF800000)

private val urlSpanStyle = SpanStyle(
    color = FutabaLinkColor,
    textDecoration = TextDecoration.Underline
)

private data class UrlMatch(
    val url: String,
    val range: IntRange
)

private class ThreadMessageAnnotationCache(
    private val maxEntries: Int
) {
    private val entries = LinkedHashMap<String, AnnotatedString>()

    fun get(key: String): AnnotatedString? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    fun put(key: String, value: AnnotatedString) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxEntries) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }
}

private val threadMessageAnnotationBaseCache = ThreadMessageAnnotationCache(
    maxEntries = THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThreadPostCard(
    post: Post,
    isOp: Boolean,
    isSelfPost: Boolean = false,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    saidaneLabelOverride: String?,
    highlightRanges: List<IntRange> = emptyList(),
    onQuoteClick: (QuoteReference) -> Unit,
    onUrlClick: (String) -> Unit,
    onQuoteRequested: (() -> Unit)? = null,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onSaidaneClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val backgroundColor = when {
        post.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val saidaneLabel = saidaneLabelOverride ?: post.saidaneLabel
    val cardModifier = if (onLongPress != null) {
        modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    } else {
        modifier
    }

    Column(
        modifier = cardModifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThreadPostMetadata(
            post = post,
            isOp = isOp,
            isSelfPost = isSelfPost,
            posterIdLabel = posterIdLabel,
            posterIdValue = posterIdValue,
            saidaneLabel = saidaneLabel,
            onUrlClick = onUrlClick,
            onQuoteRequested = onQuoteRequested,
            onSaidaneClick = onSaidaneClick,
            onPosterIdClick = onPosterIdClick,
            onReferencedByClick = onReferencedByClick
        )
        val thumbnailForDisplay = post.thumbnailUrl ?: post.imageUrl
        thumbnailForDisplay?.let { displayUrl ->
            val thumbnailRequest = remember(platformContext, displayUrl) {
                ImageRequest.Builder(platformContext)
                    .data(displayUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = thumbnailRequest,
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "添付画像",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(backgroundColor)
                    .clickable {
                        val targetUrl = post.imageUrl ?: displayUrl
                        onMediaClick?.invoke(targetUrl, determineMediaType(targetUrl))
                    },
                contentScale = ContentScale.Fit
            )
        }
        ThreadMessageText(
            messageHtml = post.messageHtml,
            isDeleted = post.isDeleted,
            quoteReferences = post.quoteReferences,
            onQuoteClick = onQuoteClick,
            onUrlClick = onUrlClick,
            highlightRanges = highlightRanges
        )
    }
}

@Composable
internal fun ThreadPostMetadata(
    post: Post,
    isOp: Boolean,
    isSelfPost: Boolean = false,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    saidaneLabel: String?,
    onUrlClick: (String) -> Unit,
    onQuoteRequested: (() -> Unit)? = null,
    onSaidaneClick: (() -> Unit)? = null,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val subjectText = post.subject?.ifBlank { "無題" } ?: "無題"
        val authorText = post.author?.ifBlank { "名無し" } ?: "名無し"
        val subjectColor = when {
            subjectText.contains("無念") || subjectText.contains("株") -> MaterialTheme.colorScheme.tertiary
            isOp -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = (post.order ?: 0).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = subjectText,
                style = MaterialTheme.typography.titleMedium,
                color = subjectColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = authorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (post.referencedCount > 0) {
                ReplyCountLabel(
                    count = post.referencedCount,
                    onClick = onReferencedByClick
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val timestampText = remember(post.timestamp) {
                extractTimestampWithoutId(post.timestamp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                posterIdLabel?.let { label ->
                    val idModifier = if (posterIdValue != null && onPosterIdClick != null) {
                        Modifier.clickable(onClick = onPosterIdClick)
                    } else {
                        Modifier
                    }
                    Text(
                        modifier = idModifier,
                        text = label.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (label.highlight) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (saidaneLabel != null && onSaidaneClick != null) {
                    val canSendSaidane = !isSelfPost
                    SaidaneLink(
                        label = saidaneLabel,
                        enabled = canSendSaidane,
                        onClick = onSaidaneClick
                    )
                }
            }
            Text(
                text = "No.${post.id}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val fileName = extractFileNameFromUrl(post.imageUrl ?: post.thumbnailUrl)
            val targetUrl = post.imageUrl ?: post.thumbnailUrl
            if (fileName != null && targetUrl != null) {
                var showFileMenu by remember { mutableStateOf(false) }
                Box {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = FutabaLinkColor,
                        textDecoration = TextDecoration.None,
                        modifier = Modifier.clickable { showFileMenu = true }
                    )
                    DropdownMenu(
                        expanded = showFileMenu,
                        onDismissRequest = { showFileMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("添付を開く") },
                            onClick = {
                                showFileMenu = false
                                onUrlClick(targetUrl)
                            }
                        )
                        onQuoteRequested?.let { quote ->
                            DropdownMenuItem(
                                text = { Text("引用") },
                                onClick = {
                                    showFileMenu = false
                                    quote()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

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
        value = withContext(AppDispatchers.parsing) {
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

@Composable
private fun ReplyCountLabel(
    count: Int,
    onClick: (() -> Unit)? = null
) {
    val labelModifier = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Text(
        modifier = labelModifier,
        text = "${count}レス",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun SaidaneLink(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val normalized = if (label == "+") "そうだね" else label
    Text(
        text = normalized,
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        ),
        textDecoration = TextDecoration.None,
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = onClick
        )
    )
}

private fun buildThreadMessageAnnotationCacheKey(
    messageHtml: String,
    quoteReferences: List<QuoteReference>
): String {
    var hash = 17
    quoteReferences.forEach { reference ->
        hash = 31 * hash + reference.text.hashCode()
        reference.targetPostIds.forEach { targetId ->
            hash = 31 * hash + targetId.hashCode()
        }
    }
    return "${messageHtml.hashCode()}:${quoteReferences.size}:$hash"
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
    urlMatches += URL_REGEX.findAll(builtText).map { match ->
        UrlMatch(url = match.value, range = match.range)
    }
    urlMatches += SCHEMELESS_URL_REGEX.findAll(builtText).map { match ->
        UrlMatch(url = "h${match.value}", range = match.range)
    }
    urlMatches += URL_LINK_TEXT_REGEX.findAll(builtText).mapNotNull { match ->
        val target = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
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

private fun extractTimestampWithoutId(timestamp: String): String {
    val idx = timestamp.indexOf("ID:")
    if (idx == -1) return timestamp.trim()
    return timestamp.substring(0, idx).trimEnd()
}

@Composable
internal fun QuotePreviewDialog(
    state: QuotePreviewState,
    onDismiss: () -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onUrlClick: (String) -> Unit,
    onQuoteClick: (QuoteReference) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    text = state.quoteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState)
                ) {
                    state.targetPosts.forEachIndexed { index, post ->
                        ThreadPostCard(
                            post = post,
                            isOp = post.order == 0,
                            posterIdLabel = state.posterIdLabels[post.id],
                            posterIdValue = normalizePosterIdValue(post.posterId),
                            saidaneLabelOverride = null,
                            onQuoteClick = onQuoteClick,
                            onUrlClick = onUrlClick,
                            onSaidaneClick = null,
                            onLongPress = null,
                            onMediaClick = onMediaClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (index != state.targetPosts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
