package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Where a box with an arrow goes: its top-left corner, whether it is above its anchor, and the arrow's x (px). */
internal data class FutaberArrowPlacement(val left: Float, val top: Float, val above: Boolean, val arrowX: Float)

/**
 * Where a box of [width]×[height] px with an arrow of [arrow] px goes to point at an anchor [anchorX] wide and
 * [anchorTop]..[anchorBottom] high, inside a screen of [screenWidth]×[screenHeight] keeping [margin] from its edges:
 * above the anchor when it fits (the original's popovers and callouts open over the button at the bottom), else
 * under it; centred on the anchor and moved in from the sides when it would cross them. The arrow keeps pointing at
 * the anchor, at least [arrowInset] from the box's corners.
 */
internal fun futaberArrowPlacement(
    anchorX: Float,
    anchorTop: Float,
    anchorBottom: Float,
    width: Float,
    height: Float,
    screenWidth: Float,
    screenHeight: Float,
    margin: Float,
    arrow: Float,
    arrowInset: Float
): FutaberArrowPlacement {
    val maxLeft = (screenWidth - margin - width).coerceAtLeast(margin)
    val left = (anchorX - width / 2f).coerceIn(margin, maxLeft)
    val aboveTop = anchorTop - arrow - height
    val above = aboveTop >= margin || anchorTop - margin >= screenHeight - margin - anchorBottom
    val top = if (above) aboveTop.coerceAtLeast(margin) else (anchorBottom + arrow).coerceAtMost((screenHeight - margin - height).coerceAtLeast(margin))
    val arrowMin = left + arrowInset
    val arrowMax = (left + width - arrowInset).coerceAtLeast(arrowMin)
    return FutaberArrowPlacement(left, top, above, anchorX.coerceIn(arrowMin, arrowMax))
}

/** One choice of [FutaberPopover]; the [selected] one is drawn in the light blue. */
internal data class FutaberPopoverOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    /** The row's test tag; `futaber-popover-<id>` unless a menu keeps its older one (the catalog's, L-B10). */
    val tag: String = "futaber-popover-$id",
    val onClick: () -> Unit
)

/** The original's popover (V29, `M/10-02-menu-crop.png`): 140 wide, the heading over 31pt rows. */
internal const val FUTABER_POPOVER_WIDTH_DP = 140
internal const val FUTABER_POPOVER_ROW_DP = 31
internal const val FUTABER_POPOVER_ARROW_DP = 10

/**
 * A popover pointing at [anchor] (root px, as the quote bubbles take it; §3.6, wired in L-B10 for 「表示形式」 and
 * 「表示順序」): a light rounded box with the heading, the choices left-aligned under it, the chosen one in the light
 * blue ([FutaberColors.popoverSelected]), and an arrow at the anchor. A tap outside only closes it (it does not
 * reach what is under it, M52); so does the system's Back. Drawn by the caller in a layer over the whole screen.
 */
@Composable
internal fun FutaberPopover(
    anchor: FutaberAnchor,
    title: String?,
    options: List<FutaberPopoverOption>,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val density = LocalDensity.current
    FutaberBackHandler(onDismiss)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.onSizeChanged { screen = it }) {
        Box(
            Modifier.fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClickLabel = "閉じる", onClick = onDismiss)
                .testTag("futaber-popover-outside")
        )
        val arrowPx = with(density) { FUTABER_POPOVER_ARROW_DP.dp.toPx() }
        val placement = futaberArrowPlacement(
            anchorX = anchor.x - origin.x, anchorTop = anchor.top - origin.y, anchorBottom = anchor.bottom - origin.y,
            width = box.width.toFloat(), height = box.height.toFloat(),
            screenWidth = screen.width.toFloat(), screenHeight = screen.height.toFloat(),
            margin = with(density) { 8.dp.toPx() }, arrow = arrowPx, arrowInset = with(density) { 16.dp.toPx() }
        )
        Column(
            Modifier.offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                .alpha(if (box == IntSize.Zero) 0f else 1f)
                .width(FUTABER_POPOVER_WIDTH_DP.dp)
                .onSizeChanged { box = it }
                .clip(FutaberShapes.sheet).background(colors.fill)
                .semantics { title?.let { paneTitle = it } }
                .testTag("futaber-popover")
        ) {
            if (title != null) {
                Text(
                    title, color = colors.body, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).testTag("futaber-popover-title"),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                HorizontalDivider(color = colors.sheetSeparator, thickness = 0.5.dp)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = colors.sheetSeparator, thickness = 0.5.dp)
                    FutaberPopoverRow(option)
                }
            }
        }
        if (box != IntSize.Zero) FutaberArrow(placement, arrowPx, colors.fill, box.height.toFloat())
    }
}

@Composable
private fun FutaberPopoverRow(option: FutaberPopoverOption) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.fillMaxWidth().heightIn(min = FUTABER_POPOVER_ROW_DP.dp)
            .clickable(onClickLabel = option.label, role = Role.Button, onClick = option.onClick)
            .semantics { selected = option.selected }
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .testTag(option.tag),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart
    ) {
        Text(
            option.label,
            color = if (option.selected) colors.popoverSelected else colors.body,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light
        )
    }
}

/** The small triangle from a box to its anchor: on the box's bottom edge when it is above the anchor, else on its top. */
@Composable
internal fun FutaberArrow(placement: FutaberArrowPlacement, arrowPx: Float, color: Color, boxHeight: Float) {
    val density = LocalDensity.current
    val half = arrowPx
    Canvas(
        Modifier.offset {
            val y = if (placement.above) placement.top + boxHeight else placement.top - arrowPx
            IntOffset((placement.arrowX - half).roundToInt(), y.roundToInt())
        }.size(with(density) { (half * 2).toDp() }, with(density) { arrowPx.toDp() })
    ) {
        val path = Path().apply {
            if (placement.above) { moveTo(0f, 0f); lineTo(size.width / 2f, size.height); lineTo(size.width, 0f) }
            else { moveTo(0f, size.height); lineTo(size.width / 2f, 0f); lineTo(size.width, size.height) }
            close()
        }
        drawPath(path, color)
    }
}
