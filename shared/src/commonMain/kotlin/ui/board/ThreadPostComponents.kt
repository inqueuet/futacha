package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlin.math.min

private val ThreadPostFooterTextColor = FutabaTextDim
private val ThreadPostFooterAccentColor = FutabaAccentRed
private val ThreadPostFooterAuthorColor = FutabaNameGreen
private val ThreadPostThumbnailMaxWidth = 800.dp

internal data class ThreadPostThumbnailDisplayBounds(
    val width: Dp,
    val height: Dp
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
    onMediaLongPress: ((Post, String, MediaType) -> Unit)? = null,
    onSaidaneClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onAiHideAgain: (() -> Unit)? = null,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    postImageSize: ThreadPostImageSize = ThreadPostImageSize.Small,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val backgroundColor = when {
        post.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val saidaneLabel = saidaneLabelOverride ?: post.saidaneLabel
    var showDeletedBody by remember(post.id, post.messageHtml, post.isDeleted) { mutableStateOf(false) }
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
            onMediaLongPress = onMediaLongPress,
            bodyTextSize = bodyTextSize
        )
        onAiHideAgain?.let { hideAgain ->
            AiHiddenPostRestoreAction(onClick = hideAgain)
        }
        val shouldCollapseDeletedBody = post.isDeleted && !showDeletedBody
        val thumbnailForDisplay = if (shouldCollapseDeletedBody) null else resolvePostDisplayMediaUrl(post)
        thumbnailForDisplay?.let { displayUrl ->
            val imageLoader = LocalFutachaImageLoader.current
            val thumbnailMaxHeight = remember(postImageSize) {
                resolveThreadPostThumbnailMaxHeight(postImageSize)
            }
            val thumbnailTargetWidthPx = remember(density) {
                with(density) { ThreadPostThumbnailMaxWidth.roundToPx() }
            }
            val thumbnailTargetHeightPx = remember(density, thumbnailMaxHeight) {
                with(density) { thumbnailMaxHeight.roundToPx() }
            }
            val thumbnailRequest = remember(
                platformContext,
                displayUrl,
                thumbnailTargetWidthPx,
                thumbnailTargetHeightPx
            ) {
                ImageRequest.Builder(platformContext)
                    .data(displayUrl)
                    .crossfade(true)
                    .size(thumbnailTargetWidthPx, thumbnailTargetHeightPx)
                    .build()
            }
            val thumbnailPainter = rememberAsyncImagePainter(
                model = thumbnailRequest,
                imageLoader = imageLoader
            )
            val thumbnailPainterState by thumbnailPainter.state.collectAsState()
            val shouldShowThumbnailFallback = thumbnailPainterState is AsyncImagePainter.State.Error
            BoxWithConstraints(
                modifier = run {
                    val baseModifier = Modifier
                        .fillMaxWidth()
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
                val thumbnailDisplayBounds = remember(
                    thumbnailPainter.intrinsicSize,
                    maxWidth,
                    thumbnailMaxHeight
                ) {
                    resolveThreadPostThumbnailDisplayBounds(
                        intrinsicWidth = thumbnailPainter.intrinsicSize.width,
                        intrinsicHeight = thumbnailPainter.intrinsicSize.height,
                        maxWidth = maxWidth,
                        maxHeight = thumbnailMaxHeight
                    )
                }
                val imageContainerModifier = Modifier
                    .fillMaxWidth()
                    .height(thumbnailDisplayBounds.height)
                    .clip(MaterialTheme.shapes.small)
                    .background(backgroundColor)
                Box(modifier = imageContainerModifier) {
                    if (shouldShowThumbnailFallback) {
                        MediaThumbnailFallbackIcon(
                            url = displayUrl,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Image(
                            painter = thumbnailPainter,
                            contentDescription = "添付画像",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(thumbnailDisplayBounds.width)
                                .height(thumbnailDisplayBounds.height)
                        )
                    }
                }
            }
        }
        if (shouldCollapseDeletedBody) {
            DeletedPostBodyPlaceholder(
                hasBody = post.messageHtml.isNotBlank(),
                onReveal = { showDeletedBody = true },
                bodyTextSize = bodyTextSize
            )
        } else {
            ThreadMessageText(
                messageHtml = post.messageHtml,
                isDeleted = post.isDeleted,
                quoteReferences = post.quoteReferences,
                onQuoteClick = onQuoteClick,
                onUrlClick = onUrlClick,
                highlightRanges = highlightRanges,
                bodyTextSize = bodyTextSize
            )
        }
    }
}

internal fun resolveThreadPostThumbnailMaxHeight(size: ThreadPostImageSize): Dp {
    return when (size) {
        ThreadPostImageSize.ExtraSmall -> 120.dp
        ThreadPostImageSize.Small -> 200.dp
        ThreadPostImageSize.Medium -> 320.dp
        ThreadPostImageSize.Large -> 480.dp
    }
}

internal fun resolveThreadPostThumbnailDisplayBounds(
    intrinsicWidth: Float,
    intrinsicHeight: Float,
    maxWidth: Dp,
    maxHeight: Dp
): ThreadPostThumbnailDisplayBounds {
    val resolvedMaxWidth = if (maxWidth.value.isFinite() && maxWidth > 0.dp) {
        maxWidth
    } else {
        ThreadPostThumbnailMaxWidth
    }
    val resolvedMaxHeight = if (maxHeight.value.isFinite() && maxHeight > 0.dp) {
        maxHeight
    } else {
        resolveThreadPostThumbnailMaxHeight(ThreadPostImageSize.Small)
    }
    if (
        !intrinsicWidth.isFinite() ||
        !intrinsicHeight.isFinite() ||
        intrinsicWidth <= 0f ||
        intrinsicHeight <= 0f
    ) {
        return ThreadPostThumbnailDisplayBounds(
            width = resolvedMaxWidth,
            height = resolvedMaxHeight
        )
    }
    val scale = min(
        resolvedMaxWidth.value / intrinsicWidth,
        resolvedMaxHeight.value / intrinsicHeight
    )
    if (!scale.isFinite() || scale <= 0f) {
        return ThreadPostThumbnailDisplayBounds(
            width = resolvedMaxWidth,
            height = resolvedMaxHeight
        )
    }
    return ThreadPostThumbnailDisplayBounds(
        width = (intrinsicWidth * scale).dp,
        height = (intrinsicHeight * scale).dp
    )
}

@Composable
private fun AiHiddenPostRestoreAction(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClick) {
            Text("AI非表示に戻す")
        }
    }
}

@Composable
private fun DeletedPostBodyPlaceholder(
    hasBody: Boolean,
    onReveal: () -> Unit,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val textStyle = MaterialTheme.typography.bodySmall.withThreadTextSize(
        bodyTextSize = bodyTextSize,
        fallbackFontSize = 12.sp,
        fallbackLineHeight = 16.sp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "削除されたレスです",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (hasBody) {
            TextButton(onClick = onReveal) {
                Text("本文を表示")
            }
        }
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
    onMediaLongPress: ((Post, String, MediaType) -> Unit)? = null,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val orderStyle = MaterialTheme.typography.labelLarge.withThreadTextSize(
        bodyTextSize = bodyTextSize,
        fallbackFontSize = 14.sp,
        fallbackLineHeight = 20.sp
    )
    val subjectStyle = MaterialTheme.typography.titleMedium.withThreadTextSize(
        bodyTextSize = bodyTextSize,
        fallbackFontSize = 16.sp,
        fallbackLineHeight = 24.sp
    )
    val authorStyle = MaterialTheme.typography.bodyMedium.withThreadTextSize(
        bodyTextSize = bodyTextSize,
        fallbackFontSize = 14.sp,
        fallbackLineHeight = 20.sp
    )
    val secondaryStyle = MaterialTheme.typography.bodySmall.withThreadTextSize(
        bodyTextSize = bodyTextSize,
        fallbackFontSize = 12.sp,
        fallbackLineHeight = 16.sp
    )
    val labelStyle = MaterialTheme.typography.labelMedium.withThreadTextSize(
        bodyTextSize = bodyTextSize,
        fallbackFontSize = 12.sp,
        fallbackLineHeight = 16.sp
    )
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = (post.order ?: 0).toString(),
                style = orderStyle,
                fontWeight = FontWeight.Bold,
                color = ThreadPostFooterAccentColor
            )
            Text(
                text = subjectText,
                style = subjectStyle,
                color = subjectColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = authorText,
                style = authorStyle,
                color = ThreadPostFooterAuthorColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (post.referencedCount > 0) {
                ReplyCountLabel(
                    count = post.referencedCount,
                    onClick = onReferencedByClick,
                    bodyTextSize = bodyTextSize
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = timestampText,
                    style = secondaryStyle,
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
                        style = labelStyle,
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
                        onClick = onSaidaneClick,
                        bodyTextSize = bodyTextSize
                    )
                }
            }
            Text(
                text = "No.${post.id}",
                style = labelStyle,
                color = ThreadPostFooterTextColor
            )
            val targetUrl = resolvePostTargetMediaUrl(post)
            val fileName = extractFileNameFromUrl(targetUrl)
            if (fileName != null && targetUrl != null) {
                val targetMediaType = resolvePostTargetMediaType(post, targetUrl)
                Text(
                    text = fileName,
                    style = secondaryStyle,
                    color = FutabaLinkColor,
                    textDecoration = TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    onClick: (() -> Unit)? = null,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val labelModifier = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Text(
        modifier = labelModifier,
        text = "${count}レス",
        style = MaterialTheme.typography.labelMedium.withThreadTextSize(
            bodyTextSize = bodyTextSize,
            fallbackFontSize = 12.sp,
            fallbackLineHeight = 16.sp
        ),
        fontWeight = FontWeight.Bold,
        color = ThreadPostFooterAccentColor
    )
}

@Composable
private fun SaidaneLink(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val normalized = if (label == "+") "そうだね" else label
    Text(
        text = normalized,
        style = MaterialTheme.typography.labelMedium.withThreadTextSize(
            bodyTextSize = bodyTextSize,
            fallbackFontSize = 12.sp,
            fallbackLineHeight = 16.sp
        ).copy(
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
