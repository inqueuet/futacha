package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

private object SaveNoticePosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
            (windowSize.height - popupContentSize.height).coerceAtLeast(0))
}

/** A non-focusable window stays above media dialogs without interrupting browsing. */
@Composable
internal fun MediaSaveNotice(message: String, onDismiss: () -> Unit, onShare: (() -> Unit)? = null) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) { delay(5_000); dismiss() }
    com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow { Popup(popupPositionProvider = SaveNoticePosition, properties = PopupProperties(focusable = false)) {
        Snackbar(
            modifier = Modifier.navigationBarsPadding().padding(12.dp).testTag("media-save-notice"),
            action = onShare?.let { share -> { TextButton(onClick = share, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.inversePrimary)) { Text("共有") } } },
            dismissAction = { TextButton(onClick = onDismiss, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.inversePrimary)) { Text("閉じる") } }
        ) { Text("保存しました") }
    }
} }
