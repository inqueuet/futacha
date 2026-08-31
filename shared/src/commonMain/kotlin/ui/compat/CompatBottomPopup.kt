package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Small bottom-toolbar popup used by the reference APK instead of a centered
 * Material dialog. Its offset follows the real navigation-bar inset so the
 * menu stays immediately above the toolbar on both gesture and 3-button hosts.
 */
@Composable
internal fun CompatBottomPopup(
    alignment: Alignment = Alignment.BottomEnd,
    testTag: String = "compat-bottom-popup",
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    val density = LocalDensity.current
    val bottomInset = with(density) {
        40.dp.roundToPx() + WindowInsets.navigationBars.getBottom(this)
    }
    val availablePopupHeight = with(density) {
        (LocalWindowInfo.current.containerSize.height - bottomInset - 1)
            .coerceAtLeast(1)
            .toDp()
    }
    Popup(
        alignment = alignment,
        offset = IntOffset(0, -bottomInset),
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .heightIn(max = minOf(560.dp, availablePopupHeight))
                .testTag(testTag),
            shape = RoundedCornerShape(2.dp),
            color = compatibilityPopupSurface(palette),
            contentColor = compatibilityPopupContent(palette),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
        }
    }
}
