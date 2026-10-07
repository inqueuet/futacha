package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.PosterIdLabel
import kotlin.math.roundToInt

private const val FUTABER_BUBBLE_SCRIM_ALPHA = 0.2f
private const val FUTABER_BUBBLE_MAX_HEIGHT_FRACTION = 0.6f

/**
 * A white bubble laid over the thread beside the tapped quote line or reply counter, with a
 * pointer to it. The rest of the screen dims; tapping outside closes one bubble. Quotes inside
 * the bubble open the next one through [onNested].
 */
@Composable
internal fun FutaberQuoteOverlay(
    layer: FutaberQuoteLayer,
    allPosts: List<Post>,
    idLabels: Map<String, PosterIdLabel>,
    replyIndex: Map<String, List<Post>>,
    onNested: (FutaberQuoteKind, List<Post>, FutaberAnchor) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var cardHeight by remember(layer) { mutableStateOf(0) }
    val ordinals = remember(allPosts) { futaberOrdinalsById(allPosts) }
    val gapPx = with(density) { 10.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val pointerHalfPx = with(density) { 7.dp.toPx() }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .onSizeChanged { overlaySize = it }
    ) {
        // A sibling of the bubble, not its parent: a clickable parent would merge the bubble's
        // semantics into one node for assistive technology.
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = FUTABER_BUBBLE_SCRIM_ALPHA))
                .clickable(onClickLabel = "引用を閉じる", onClick = onDismiss)
                .testTag("futaber-quote-scrim")
        )
        val anchor = FutaberAnchor(
            x = layer.anchor.x - origin.x,
            top = layer.anchor.top - origin.y,
            bottom = layer.anchor.bottom - origin.y
        )
        val placement = futaberBubblePlacement(
            anchor = anchor,
            cardHeight = cardHeight.toFloat(),
            minTop = marginPx,
            maxBottom = overlaySize.height - marginPx,
            gap = gapPx
        )
        // Not drawn until measured, so it never flashes at a wrong spot.
        Box(
            Modifier
                .offset { IntOffset(marginPx.roundToInt(), placement.top.roundToInt()) }
                .alpha(if (cardHeight == 0) 0f else 1f)
        ) {
            Surface(
                // Taps inside the bubble must not reach the scrim.
                modifier = Modifier
                    .padding(end = with(density) { (2 * marginPx).toDp() })
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .onSizeChanged { cardHeight = it.height }
                    .semantics { contentDescription = "${layer.kind.label}、${layer.posts.size}件" }
                    .testTag("futaber-quote-card"),
                shape = FutaberShapes.card,
                color = colors.background,
                shadowElevation = 8.dp
            ) {
                // Lazy: a post with hundreds of replies must not build every row at once. A shorter list still wraps its content.
                LazyColumn(
                    Modifier.heightIn(max = with(density) { (overlaySize.height * FUTABER_BUBBLE_MAX_HEIGHT_FRACTION).toDp() })
                ) {
                    items(layer.posts) { post ->
                        FutaberPostRow(
                            ordinal = ordinals[post.id] ?: 0,
                            post = post,
                            idLabel = idLabels[post.id],
                            replyCount = futaberReplyCount(post, replyIndex),
                            onQuoteTap = { line, nested ->
                                onNested(
                                    FutaberQuoteKind.Source,
                                    futaberPostsById(allPosts, futaberQuoteTargetIds(post, line)),
                                    nested
                                )
                            },
                            onRepliesTap = { nested ->
                                onNested(FutaberQuoteKind.Replies, replyIndex[post.id].orEmpty(), nested)
                            }
                        )
                        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                    }
                }
            }
        }
        // The pointer: a small triangle on the card edge nearest the anchor.
        if (cardHeight > 0) {
            val pointerX = (anchor.x).coerceIn(marginPx + pointerHalfPx * 2, overlaySize.width - marginPx - pointerHalfPx * 2)
            val pointerTop = if (placement.pointerOnTop) placement.top - pointerHalfPx else placement.top + cardHeight
            Canvas(
                Modifier
                    .offset { IntOffset((pointerX - pointerHalfPx).roundToInt(), pointerTop.roundToInt()) }
                    .size(with(density) { (pointerHalfPx * 2).toDp() }, with(density) { pointerHalfPx.toDp() })
            ) {
                val path = Path().apply {
                    if (placement.pointerOnTop) {
                        moveTo(0f, size.height); lineTo(size.width / 2f, 0f); lineTo(size.width, size.height)
                    } else {
                        moveTo(0f, 0f); lineTo(size.width / 2f, size.height); lineTo(size.width, 0f)
                    }
                    close()
                }
                drawPath(path, colors.background)
            }
        }
    }
}
