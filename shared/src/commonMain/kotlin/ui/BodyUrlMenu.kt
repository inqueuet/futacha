package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher

/** A body link is independent of the thread URL and of the post's action sheet. */
@Suppress("DEPRECATION")
@Composable
internal fun rememberBodyUrlMenu(onOpen: (String) -> Unit): (String) -> Unit {
    var selected by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val share = rememberCompatShareLauncher()
    val open by rememberUpdatedState(onOpen)
    selected?.let { url ->
        FutachaAppLockAwareWindow {
            AlertDialog(
                modifier = Modifier.testTag("body-url-menu"),
                onDismissRequest = { selected = null },
                title = { Text("リンク") },
                text = { Column {
                    Text(url)
                    TextButton(onClick = { selected = null; open(url) }) { Text("リンクを開く") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(url)); selected = null }) { Text("URLをコピー") }
                    TextButton(onClick = { selected = null; share(url, "text/plain", null) }) { Text("URLを共有") }
                } },
                confirmButton = { TextButton(onClick = { selected = null }) { Text("閉じる") } }
            )
        }
    }
    return remember { { url: String -> selected = url } }
}
