package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The reference APK's `AlertDialog.Builder.setItems` menus have no title or
 * explicit cancel button. They dismiss after a selection, outside tap, or
 * Back, and their ListView remains scrollable when every image-search target
 * is enabled on a short screen.
 */
@Composable
internal fun CompatLegacyChoiceDialog(
    onDismiss: () -> Unit,
    choices: List<String>,
    onChoice: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (String) -> Boolean = { true },
    testTag: String? = null,
    alignment: Alignment = Alignment.Center,
    footer: (@Composable () -> Unit)? = null
) {
    val palette = LocalCompatibilityPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // With usePlatformDefaultWidth=false the native Dialog window
                // occupies the whole screen, so the platform has no outside
                // region from which to invoke onDismissRequest. Treat the
                // visible scrim as that region explicitly.
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = alignment
        ) {
            Surface(
                modifier = modifier
                    .widthIn(max = 355.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    // Consume taps on the menu surface so only the visible
                    // scrim dismisses it. Child TextButtons still own their
                    // own gestures and dismiss through onChoice.
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                color = compatibilityPopupSurface(palette),
                contentColor = compatibilityPopupContent(palette),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().then(
                        if (testTag == null) Modifier else Modifier.testTag(testTag)
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(choices, key = { it }) { choice ->
                        TextButton(
                            enabled = enabled(choice),
                            onClick = {
                                onDismiss()
                                onChoice(choice)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                choice,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (enabled(choice)) {
                                    compatibilityPopupContent(palette)
                                } else {
                                    compatibilityPopupContent(palette).copy(alpha = 0.38f)
                                },
                                fontSize = 18.sp
                            )
                        }
                    }
                    footer?.let { footerContent ->
                        item(key = "compat-choice-footer") { footerContent() }
                    }
                }
            }
        }
    }
}
