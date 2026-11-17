package com.valoser.futacha.shared.ui.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val TOOLBAR_MIN_ITEM_WIDTH_DP = 40
/** A finite row width keeps weighted slots usable even inside a horizontal scroll container. */
internal fun toolbarContentWidth(width: Float, itemCount: Int, itemWidth: Float): Float =
    maxOf(width, itemCount.coerceAtLeast(0) * itemWidth)

/** Preserve the usual arrangement, scrolling only when the buttons would otherwise be squeezed. */
@Composable
internal fun ScrollableToolbarRow(
    itemCount: Int,
    modifier: Modifier = Modifier,
    minItemWidth: Dp = TOOLBAR_MIN_ITEM_WIDTH_DP.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    BoxWithConstraints(modifier) {
        val contentWidth = toolbarContentWidth(maxWidth.value, itemCount, minItemWidth.value).dp
        val scroll = rememberScrollState()
        Row(
            Modifier.horizontalScroll(scroll).width(contentWidth),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )
        ToolbarOverflowHints(scroll)
    }
}

/** Non-interactive edge hints leave the underlying buttons and swipes available. */
@Composable
internal fun BoxScope.ToolbarOverflowHints(scroll: ScrollState) {
    // Match the measured row after layout; hints must never make a natural-height toolbar fill its parent.
    Box(Modifier.matchParentSize()) {
        if (scroll.canScrollBackward) ToolbarOverflowHint(towardStart = true)
        if (scroll.canScrollForward) ToolbarOverflowHint(towardStart = false)
    }
}

@Composable
private fun BoxScope.ToolbarOverflowHint(towardStart: Boolean) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shadow = Color.Black.copy(alpha = 0.5f)
    val colors = if (towardStart != rtl) listOf(shadow, Color.Transparent) else listOf(Color.Transparent, shadow)
    Box(Modifier.align(if (towardStart) Alignment.CenterStart else Alignment.CenterEnd)
        .width(12.dp).fillMaxHeight().background(Brush.horizontalGradient(colors))
        .testTag(if (towardStart) "scrollable-toolbar-more-start" else "scrollable-toolbar-more-end")) {
        Icon(if (towardStart) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (towardStart) "前の操作があります" else "続きの操作があります",
            tint = Color.White, modifier = Modifier.align(Alignment.Center).size(16.dp))
    }
}
