package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow

/** The drawn size of a bottom bar icon (futaber: about 21pt of a 22pt glyph). */
internal const val FUTABER_TOOLBAR_ICON_DP = 28

/** The drawn size of a navigation bar icon. */
internal const val FUTABER_NAV_ICON_DP = 24

/**
 * The bottom bar of the catalog and the thread (V03): the original's five buttons in five equal slots on the white
 * band with a hairline over it. On a narrow screen the row scrolls (each slot keeps 48dp, as before).
 */
@Composable
internal fun FutaberToolbarRow(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalFutaberColors.current
    ScrollableToolbarRow(
        itemCount = itemCount, minItemWidth = 48.dp,
        modifier = modifier.futaberToolbar(colors).height(FUTABER_BOTTOM_BAR_HEIGHT_DP.dp).testTag("futaber-toolbar"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * One slot of the bottom bar: the whole slot (its share of the width, the bar's height) takes the touch, the line icon
 * in its middle. A button that cannot be pressed draws the same icon lighter.
 */
@Composable
internal fun FutaberToolbarButton(
    icon: ImageVector,
    contentDescription: String?,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = LocalFutaberColors.current
    Box(
        modifier.fillMaxHeight()
            .combinedClickable(
                enabled = enabled, role = Role.Button, onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        FutaberIcon(
            icon, contentDescription = contentDescription, modifier = Modifier.size(FUTABER_TOOLBAR_ICON_DP.dp),
            tint = tint ?: if (enabled) colors.icon else colors.iconDisabled
        )
    }
}

/** A button of the catalog's navigation bar: [widthDp] wide (the original's 60 / 50), the bar's height. */
@Composable
internal fun FutaberNavButton(icon: ImageVector, contentDescription: String, tag: String, widthDp: Int, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.width(widthDp.dp).fillMaxHeight()
            .combinedClickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        FutaberIcon(icon, contentDescription = contentDescription, modifier = Modifier.size(FUTABER_NAV_ICON_DP.dp), tint = colors.onNavBar)
    }
}
