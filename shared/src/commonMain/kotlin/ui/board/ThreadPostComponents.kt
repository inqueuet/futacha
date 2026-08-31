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
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.ui.LocalIosReviewCompliance
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlin.math.min

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
    compactHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val reviewComplianceEnabled = LocalIosReviewCompliance.current.isEnabled
    val density = LocalDensity.current
    val backgroundColor = when {
        post.isDeleted || post.isIsolated -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val saidaneLabel = saidaneLabelOverride ?: post.saidaneLabel
    var showDeletedBody by remember(post.id, post.messageHtml, post.isDeleted, post.isIsolated) {
        mutableStateOf(false)
    }
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
            .padding(
                horizontal = if (compactHeader) 8.dp else 12.dp,
                vertical = if (compactHeader) 6.dp else 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compactHeader) 5.dp else 8.dp)
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
            bodyTextSize = bodyTextSize,
            compactHeader = compactHeader
        )
        if (reviewComplianceEnabled && onLongPress != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onLongPress) {
                    Text("通報・ブロック")
                }
            }
        }
        onAiHideAgain?.let { hideAgain ->
            AiHiddenPostRestoreAction(onClick = hideAgain)
        }
        val shouldCollapseDeletedBody = (post.isDeleted || post.isIsolated) && !showDeletedBody
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
        TextButton(onClick = {
            AnalyticsTracker.uiControl("thread_post", "AI非表示に戻す")
            onClick()
        }) {
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
            TextButton(onClick = {
                AnalyticsTracker.uiControl("thread_post", "削除済みレスの本文を表示")
                onReveal()
            }) {
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
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    compactHeader: Boolean = false
) {
    val threadColors = LocalFutabaThreadColors.current
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
        verticalArrangement = Arrangement.spacedBy(if (compactHeader) 2.dp else 6.dp)
    ) {
        val subjectText = post.subject?.ifBlank { "無題" } ?: "無題"
        val authorText = post.author?.ifBlank { "名無し" } ?: "名無し"
        val timestampText = remember(post.timestamp) {
            extractTimestampWithoutId(post.timestamp)
        }
        val subjectColor = when {
            subjectText.contains("無念") || subjectText.contains("株") -> MaterialTheme.colorScheme.tertiary
            isOp -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
        if (compactHeader) {
            ThreadPostCompactMetadata(
                order = post.order ?: 0,
                subjectText = subjectText,
                subjectColor = subjectColor,
                authorText = authorText,
                timestampText = timestampText,
                postId = post.id,
                orderStyle = orderStyle,
                subjectStyle = subjectStyle,
                authorStyle = authorStyle,
                secondaryStyle = secondaryStyle,
                labelStyle = labelStyle
            )
            return@Column
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
                color = threadColors.accent
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
                color = threadColors.author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (post.referencedCount > 0) {
                ReplyCountLabel(
                    count = post.referencedCount,
                    onClick = onReferencedByClick?.let { callback ->
                        {
                            AnalyticsTracker.uiControl(
                                "thread_post_reference",
                                "この投稿を参照したレスを表示",
                                mapOf("post_context" to analyticsSessionContextId("post", post.id))
                            )
                            callback()
                        }
                    },
                    bodyTextSize = bodyTextSize
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
                Text(
                    text = timestampText,
                    style = secondaryStyle,
                    color = threadColors.footerText
                )
                posterIdLabel?.let { label ->
                    val idModifier = if (posterIdValue != null && onPosterIdClick != null) {
                        Modifier.clickable {
                            AnalyticsTracker.uiControl(
                                "thread_post_id",
                                "同じIDの投稿を表示",
                                mapOf("post_context" to analyticsSessionContextId("post", post.id))
                            )
                            onPosterIdClick()
                        }
                    } else {
                        Modifier
                    }
                    Text(
                        modifier = idModifier,
                        text = label.text,
                        style = labelStyle,
                        color = if (label.highlight) {
                            threadColors.accent
                        } else {
                            threadColors.footerText
                        }
                    )
                }
                if (saidaneLabel != null && onSaidaneClick != null) {
                    val canSendSaidane = !isSelfPost
                    SaidaneLink(
                        label = saidaneLabel,
                        enabled = canSendSaidane,
                        onClick = {
                            AnalyticsTracker.uiControl(
                                "thread_post_saidane",
                                "投稿のそうだねを選択",
                                mapOf("post_context" to analyticsSessionContextId("post", post.id))
                            )
                            onSaidaneClick()
                        },
                        bodyTextSize = bodyTextSize
                    )
                }
            Text(
                text = "No.${post.id}",
                style = labelStyle,
                color = threadColors.footerText
            )
        }
        val targetUrl = resolvePostTargetMediaUrl(post)
        val fileName = extractFileNameFromUrl(targetUrl)
        if (fileName != null && targetUrl != null) {
            val targetMediaType = resolvePostTargetMediaType(post, targetUrl)
            Text(
                text = fileName,
                style = secondaryStyle,
                color = threadColors.link,
                textDecoration = TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onMediaLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = {
                            AnalyticsTracker.uiControl(
                                "thread_post_media",
                                "投稿添付を開く",
                                mapOf("post_context" to analyticsSessionContextId("post", post.id), "media_type" to targetMediaType.name.lowercase())
                            )
                            onMediaClick?.invoke(targetUrl, targetMediaType)
                        },
                        onLongClick = {
                            AnalyticsTracker.uiControl(
                                "thread_post_media_long_press",
                                "投稿添付を長押し",
                                mapOf("post_context" to analyticsSessionContextId("post", post.id), "media_type" to targetMediaType.name.lowercase())
                            )
                            onMediaLongPress.invoke(post, targetUrl, targetMediaType)
                        }
                    )
                } else {
                    Modifier.clickable {
                        AnalyticsTracker.uiControl(
                            "thread_post_media",
                            "投稿添付を開く",
                            mapOf("post_context" to analyticsSessionContextId("post", post.id), "media_type" to targetMediaType.name.lowercase())
                        )
                        onMediaClick?.invoke(targetUrl, targetMediaType)
                    }
                }
            )
        }
    }
}

@Composable
private fun ReplyCountLabel(
    count: Int,
    onClick: (() -> Unit)? = null,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val threadColors = LocalFutabaThreadColors.current
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
        color = threadColors.accent
    )
}

@Composable
private fun SaidaneLink(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard
) {
    val threadColors = LocalFutabaThreadColors.current
    val normalized = if (label == "+") "そうだね" else label
    Text(
        text = normalized,
        style = MaterialTheme.typography.labelMedium.withThreadTextSize(
            bodyTextSize = bodyTextSize,
            fallbackFontSize = 12.sp,
            fallbackLineHeight = 16.sp
        ).copy(
            color = threadColors.footerText,
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

@Composable
private fun ThreadPostCompactMetadata(
    order: Int,
    subjectText: String,
    subjectColor: androidx.compose.ui.graphics.Color,
    authorText: String,
    timestampText: String,
    postId: String,
    orderStyle: androidx.compose.ui.text.TextStyle,
    subjectStyle: androidx.compose.ui.text.TextStyle,
    authorStyle: androidx.compose.ui.text.TextStyle,
    secondaryStyle: androidx.compose.ui.text.TextStyle,
    labelStyle: androidx.compose.ui.text.TextStyle
) {
    val threadColors = LocalFutabaThreadColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = order.toString(),
            style = orderStyle,
            fontWeight = FontWeight.Bold,
            color = threadColors.accent
        )
        Text(
            text = subjectText,
            style = subjectStyle,
            color = subjectColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.34f, fill = true)
        )
        Text(
            text = authorText,
            style = authorStyle,
            color = threadColors.author,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.18f, fill = true)
        )
        Text(
            text = timestampText,
            style = secondaryStyle,
            color = threadColors.footerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.40f, fill = true)
        )
        Text(
            text = "No.$postId",
            style = labelStyle,
            color = threadColors.footerText,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
