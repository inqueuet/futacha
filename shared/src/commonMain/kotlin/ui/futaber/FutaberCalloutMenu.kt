package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One item of [FutaberCalloutMenu]. */
internal data class FutaberCalloutItem(val id: String, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** The original's black menu (`P/out-post/90-08-camera.png`): 36pt high, 10pt from the screen's side, the arrow 7pt. */
internal const val FUTABER_CALLOUT_HEIGHT_DP = 36
internal const val FUTABER_CALLOUT_MARGIN_DP = 10
internal const val FUTABER_CALLOUT_ARROW_DP = 7

/**
 * Where the black menu goes over an anchor (the camera of the writing screen): above it when it fits, else under it,
 * centred on it but at least [FUTABER_CALLOUT_MARGIN_DP] from the sides (the original's starts at 10 over the camera at
 * the far left), the arrow at the anchor. [density] converts the dp of the measurements.
 */
internal fun futaberCalloutPlacement(
    anchor: FutaberAnchor,
    width: Float,
    height: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float
): FutaberArrowPlacement = futaberArrowPlacement(
    anchorX = anchor.x, anchorTop = anchor.top, anchorBottom = anchor.bottom,
    width = width, height = height, screenWidth = screenWidth, screenHeight = screenHeight,
    margin = FUTABER_CALLOUT_MARGIN_DP * density, arrow = FUTABER_CALLOUT_ARROW_DP * density,
    arrowInset = 12f * density
)

/**
 * The small black horizontal menu with an arrow (§3.6; the camera's 最近の画像／ファイル／ライブラリ／カメラ and the
 * clear button's long press, wired in L-B8): white labels on [FutaberColors.callout] split by thin white lines. It
 * leaves the keyboard as it is (the original's stays up). A tap outside only closes it; so does the system's Back.
 * A menu wider than the screen scrolls sideways. Drawn by the caller in a layer over the whole screen.
 */
@Composable
internal fun FutaberCalloutMenu(anchor: FutaberAnchor, items: List<FutaberCalloutItem>, onDismiss: () -> Unit) {
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
                .testTag("futaber-callout-outside")
        )
        val placement = futaberCalloutPlacement(
            FutaberAnchor(anchor.x - origin.x, anchor.top - origin.y, anchor.bottom - origin.y),
            box.width.toFloat(), box.height.toFloat(), screen.width.toFloat(), screen.height.toFloat(), density.density
        )
        val maxWidth = with(density) { (screen.width - 2 * FUTABER_CALLOUT_MARGIN_DP.dp.toPx()).coerceAtLeast(0f).toDp() }
        Row(
            Modifier.offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                .alpha(if (box == IntSize.Zero) 0f else 1f)
                .widthIn(max = maxWidth)
                .onSizeChanged { box = it }
                .clip(FutaberShapes.button).background(colors.callout)
                .height(IntrinsicSize.Min)
                .horizontalScroll(rememberScrollState())
                .testTag("futaber-callout")
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.onCallout))
                Box(
                    Modifier.heightIn(min = FUTABER_CALLOUT_HEIGHT_DP.dp)
                        .clickable(enabled = item.enabled, onClickLabel = item.label, role = Role.Button, onClick = item.onClick)
                        .padding(horizontal = 11.dp)
                        .testTag("futaber-callout-${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.label, color = if (item.enabled) colors.onCallout else colors.iconDisabled, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        if (box != IntSize.Zero) {
            FutaberArrow(placement, with(density) { FUTABER_CALLOUT_ARROW_DP.dp.toPx() }, colors.callout, box.height.toFloat())
        }
    }
}
