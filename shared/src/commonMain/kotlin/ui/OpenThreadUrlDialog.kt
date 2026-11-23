package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag

/**
 * The board list's 「URLから開く」 in ふたちゃ and としあき(仮) (may 1449258130: a thread the catalog does not show, such as
 * an isolated one). A thread address on the clipboard is put in the field. It opens the way an OS link does
 * ([InAppThreadLinks]), so a board that is not registered is asked about first.
 */
@Composable
fun OpenThreadUrlDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (prefilled) return@LaunchedEffect
        prefilled = true
        val copied = runCatching { clipboard.getText()?.text }.getOrNull().orEmpty()
        InAppThreadLinks.firstIn(copied)?.let { if (text.isEmpty()) text = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("URLから開く") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2048); error = null },
                    label = { Text("スレッドのURL") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth().testTag("open-thread-url-field")
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("open-thread-url-submit"),
                onClick = {
                    val url = InAppThreadLinks.firstIn(text)
                    if (url == null) error = OPEN_THREAD_URL_NOT_A_THREAD
                    else { InAppThreadLinks.open(url); onDismiss() }
                }
            ) { Text("開く") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

const val OPEN_THREAD_URL_NOT_A_THREAD = "スレッドのURLではありません（例：https://may.2chan.net/b/res/123.htm）"
