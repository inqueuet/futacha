package com.valoser.futacha.shared.ui.util

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Row(
            Modifier.horizontalScroll(rememberScrollState()).width(contentWidth),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )
    }
}
