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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.PosterIdLabel
import kotlin.math.roundToInt

private const val FUTABER_BUBBLE_MAX_HEIGHT_FRACTION = 0.6f

/** Left and right of a bubble (futaber: 4pt), and the frame round its white page (4pt, [FutaberColors.bubbleFrame]). */
internal const val FUTABER_BUBBLE_MARGIN_DP = 4
internal const val FUTABER_BUBBLE_FRAME_DP = 4

/** The corners of a bubble ([FutaberShapes.bubble]). */
private const val FUTABER_BUBBLE_CORNER_DP = 6

/**
 * A white bubble laid over the thread beside the tapped quote line or reply counter, with a
 * pointer to it (V16: corners of 6, a thin frame and a light shadow, 4 from the sides). The rest of the screen dims;
 * tapping outside closes one bubble. Quotes inside the bubble open the next one through [onNested].
 *
 * The thread draws every bubble of its stack (F01): the first one ([drawsScrim]) dims the thread once, and a bubble
 * with [levelsAbove] bubbles over it stays in sight one veil darker per bubble over it. Only the bubble on top
 * ([levelsAbove] 0) takes a tap outside it (`futaber-quote-scrim`), which closes that one.
 */
@Composable
internal fun FutaberQuoteOverlay(
    layer: FutaberQuoteLayer,
    allPosts: List<Post>,
    idLabels: Map<String, PosterIdLabel>,
    replyIndex: Map<String, List<Post>>,
    onNested: (FutaberQuoteKind, List<Post>, FutaberAnchor) -> Unit,
    onDismiss: () -> Unit,
    /** A picture in a bubble opens the viewer / the picture menu like one in the thread (L145). */
    onImageTap: ((Post) -> Unit)? = null,
    onImageLongPress: ((Post) -> Unit)? = null,
    /** The thread's "n/N" of IPs that post more than once ([futaberIpCountLabels]). */
    ipCounts: Map<String, String> = emptyMap(),
    /** How many bubbles are over this one (0: the top one, which takes the taps). */
    levelsAbove: Int = 0,
    /** True for the first bubble of the stack, which dims the thread. */
    drawsScrim: Boolean = true
) {
    val colors = LocalFutaberColors.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var cardHeight by remember(layer) { mutableStateOf(0) }
    val ordinals = remember(allPosts) { futaberOrdinalsById(allPosts) }
    val gapPx = with(density) { 10.dp.toPx() }
    val marginPx = with(density) { FUTABER_BUBBLE_MARGIN_DP.dp.toPx() }
    val pointerHalfPx = with(density) { 7.dp.toPx() }
    val onTop = levelsAbove <= 0
    val veil = Color.Black.copy(alpha = futaberBubbleVeilAlpha(levelsAbove))

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .onSizeChanged { overlaySize = it }
    ) {
        // A sibling of the bubble, not its parent: a clickable parent would merge the bubble's
        // semantics into one node for assistive technology. The first bubble's veil dims the thread once; only the
        // bubble on top takes the taps outside it (the ones under it are covered by the veil of the one over them).
        if (drawsScrim || onTop) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (drawsScrim) Modifier.background(Color.Black.copy(alpha = FUTABER_BUBBLE_VEIL_ALPHA)) else Modifier)
                    .then(
                        if (onTop) Modifier.clickable(onClickLabel = "引用を閉じる", onClick = onDismiss).testTag("futaber-quote-scrim")
                        else Modifier
                    )
            )
        }
        val anchor = when (layer.edge) {
            // A bubble of the operation menu stands at an edge of the thread (M08・M11).
            FutaberBubbleEdge.Top -> FutaberAnchor(overlaySize.width * FUTABER_MENU_BUBBLE_POINTER_X, 0f, 0f)
            FutaberBubbleEdge.Bottom -> overlaySize.height.toFloat().let { FutaberAnchor(overlaySize.width * FUTABER_MENU_BUBBLE_POINTER_X, it, it) }
            null -> FutaberAnchor(
                x = layer.anchor.x - origin.x,
                top = layer.anchor.top - origin.y,
                bottom = layer.anchor.bottom - origin.y
            )
        }
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
                    .testTag("futaber-quote-card")
                    // The bubbles under the top one are covered by its veil: a screen reader reads only what they are.
                    .then(
                        if (onTop) Modifier.semantics { contentDescription = "${layer.kind.label}、${layer.posts.size}件" }
                        else Modifier.clearAndSetSemantics { contentDescription = "${layer.kind.label}、${layer.posts.size}件" }
                    )
                    // A bubble under another one stays in sight, darker (F01).
                    .drawWithContent {
                        drawContent()
                        if (!onTop) drawRoundRect(veil, cornerRadius = androidx.compose.ui.geometry.CornerRadius(FUTABER_BUBBLE_CORNER_DP.dp.toPx()))
                    },
                // A small-cornered bubble with a thin light frame round its white page and a light shadow (63-01).
                shape = FutaberShapes.bubble,
                color = colors.bubbleFrame,
                shadowElevation = 2.dp
            ) {
                // Lazy: a post with hundreds of replies must not build every row at once. A shorter list still wraps its content.
                LazyColumn(
                    Modifier.padding(FUTABER_BUBBLE_FRAME_DP.dp).background(colors.background)
                        .heightIn(max = with(density) { (overlaySize.height * FUTABER_BUBBLE_MAX_HEIGHT_FRACTION).toDp() }),
                    state = layer.listState
                ) {
                    // Keyed by the post, so a changed list keeps each row's state with its post (audit L26). A post listed
                    // twice gets its occurrence added, so keys never repeat.
                    if (layer.posts.isEmpty()) item(key = "nothing-found") {
                        Text(
                            FUTABER_NOTHING_FOUND, color = colors.body, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("futaber-quote-nothing-found")
                        )
                    }
                    val rowKeys = futaberBubbleRowKeys(layer.posts)
                    itemsIndexed(layer.posts, key = { index, _ -> rowKeys[index] }) { _, post ->
                        FutaberPostRow(
                            ordinal = ordinals[post.id] ?: 0,
                            post = post,
                            idLabel = idLabels[post.id],
                            ipCount = ipCounts[post.id],
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
                            },
                            onImageTap = onImageTap?.takeIf { futaberImageUrlOf(post) != null }?.let { tap -> { tap(post) } },
                            onImageLongPress = onImageLongPress?.takeIf { futaberImageUrlOf(post) != null }?.let { press -> { press(post) } }
                        )
                        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                    }
                }
            }
        }
        // The pointer: a small triangle on the card edge nearest the anchor.
        if (cardHeight > 0) {
            val pointerX = futaberBubblePointerX(anchor.x, overlaySize.width.toFloat(), marginPx, pointerHalfPx)
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
                drawPath(path, colors.bubbleFrame)
                if (!onTop) drawPath(path, veil)
            }
        }
    }
}

/**
 * Every bubble of [stack] (F01): the first dims the thread, each one is drawn over the one before it, and a bubble under
 * others gets one veil per bubble over it. Only the top one takes a tap outside it ([onDismissTop] closes it).
 */
@Composable
internal fun FutaberQuoteStack(
    stack: List<FutaberQuoteLayer>,
    allPosts: List<Post>,
    idLabels: Map<String, PosterIdLabel>,
    ipCounts: Map<String, String>,
    replyIndex: Map<String, List<Post>>,
    onNested: (FutaberQuoteKind, List<Post>, FutaberAnchor) -> Unit,
    onDismissTop: () -> Unit,
    onImageTap: ((Post) -> Unit)?,
    onImageLongPress: ((Post) -> Unit)?
) {
    stack.forEachIndexed { index, layer ->
        // Keyed by the layer: closing the top one keeps the state (the measured card, the scroll) of the ones under it.
        androidx.compose.runtime.key(layer) {
            FutaberQuoteOverlay(
                layer = layer,
                allPosts = allPosts,
                idLabels = idLabels,
                ipCounts = ipCounts,
                replyIndex = replyIndex,
                onNested = onNested,
                onDismiss = onDismissTop,
                onImageTap = onImageTap,
                onImageLongPress = onImageLongPress,
                levelsAbove = stack.lastIndex - index,
                drawsScrim = index == 0
            )
        }
    }
}

/** Stable, distinct keys of the rows of a bubble: the post number, with "#n" on the n-th repeat of the same number. */
internal fun futaberBubbleRowKeys(posts: List<com.valoser.futacha.shared.model.Post>): List<String> {
    val seen = HashMap<String, Int>()
    return posts.map { post ->
        val n = seen.getOrElse(post.id) { 0 }
        seen[post.id] = n + 1
        if (n == 0) post.id else "${post.id}#$n"
    }
}

/**
 * Where the bubble's pointer sits: under the anchor, kept off the edges. A window narrower than the margins (an empty
 * range, which made `coerceIn` throw; audit A4) puts it in the middle.
 */
internal fun futaberBubblePointerX(anchorX: Float, width: Float, marginPx: Float, pointerHalfPx: Float): Float {
    val low = marginPx + pointerHalfPx * 2
    val high = width - marginPx - pointerHalfPx * 2
    return if (low > high) width / 2f else anchorX.coerceIn(low, high)
}
