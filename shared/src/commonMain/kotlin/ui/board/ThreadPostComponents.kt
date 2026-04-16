package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader

private val ThreadPostFooterTextColor = FutabaTextDim
private val ThreadPostFooterAccentColor = FutabaAccentRed
private val ThreadPostFooterAuthorColor = FutabaNameGreen

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
    onMediaLongPress: ((Post, String, MediaType) -> Unit)? = null,
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
            onReferencedByClick = onReferencedByClick,
            onMediaClick = onMediaClick,
            onMediaLongPress = onMediaLongPress
        )
        val thumbnailForDisplay = resolvePostDisplayMediaUrl(post)
        thumbnailForDisplay?.let { displayUrl ->
            val imageLoader = LocalFutachaImageLoader.current
            val thumbnailRequest = remember(platformContext, displayUrl) {
                ImageRequest.Builder(platformContext)
                    .data(displayUrl)
                    .crossfade(true)
                    .build()
            }
            val thumbnailPainter = rememberAsyncImagePainter(
                model = thumbnailRequest,
                imageLoader = imageLoader
            )
            val thumbnailPainterState by thumbnailPainter.state.collectAsState()
            Box(
                modifier = run {
                    val baseModifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(backgroundColor)
                    val targetUrl = resolvePostTargetMediaUrl(post) ?: displayUrl
                    val targetMediaType = resolvePostTargetMediaType(post, targetUrl)
                    if (onMediaLongPress != null) {
                        baseModifier.combinedClickable(
                            onClick = {
                                onMediaClick?.invoke(targetUrl, targetMediaType)
                            },
                            onLongClick = {
                                onMediaLongPress.invoke(post, targetUrl, targetMediaType)
                            }
                        )
                    } else {
                        baseModifier.clickable {
                            onMediaClick?.invoke(targetUrl, targetMediaType)
                        }
                    }
                }
            ) {
                when (thumbnailPainterState) {
                    is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty -> {
                        MediaThumbnailFallbackIcon(
                            url = displayUrl,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        Image(
                            painter = thumbnailPainter,
                            contentDescription = "添付画像",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
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
    onReferencedByClick: (() -> Unit)? = null,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onMediaLongPress: ((Post, String, MediaType) -> Unit)? = null
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
                color = ThreadPostFooterAccentColor
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
                color = ThreadPostFooterAuthorColor
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
                    color = ThreadPostFooterTextColor
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
                            ThreadPostFooterAccentColor
                        } else {
                            ThreadPostFooterTextColor
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
                color = ThreadPostFooterTextColor
            )
            val targetUrl = resolvePostTargetMediaUrl(post)
            val fileName = extractFileNameFromUrl(targetUrl)
            if (fileName != null && targetUrl != null) {
                val targetMediaType = resolvePostTargetMediaType(post, targetUrl)
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = FutabaLinkColor,
                    textDecoration = TextDecoration.None,
                    modifier = if (onMediaLongPress != null) {
                        Modifier.combinedClickable(
                            onClick = {
                                onMediaClick?.invoke(targetUrl, targetMediaType)
                            },
                            onLongClick = {
                                onMediaLongPress.invoke(post, targetUrl, targetMediaType)
                            }
                        )
                    } else {
                        Modifier.clickable {
                            onMediaClick?.invoke(targetUrl, targetMediaType)
                        }
                    }
                )
            }
        }
    }
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
        color = ThreadPostFooterAccentColor
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
            color = ThreadPostFooterTextColor,
            fontWeight = FontWeight.SemiBold
        ),
        textDecoration = TextDecoration.None,
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = onClick
        )
    )
}

private fun extractTimestampWithoutId(timestamp: String): String {
    val idx = timestamp.indexOf("ID:")
    if (idx == -1) return timestamp.trim()
    return timestamp.substring(0, idx).trimEnd()
}
