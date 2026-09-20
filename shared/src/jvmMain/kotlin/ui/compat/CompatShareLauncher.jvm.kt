package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.valoser.futacha.shared.desktop.DesktopOsIntegration
import kotlinx.coroutines.*

@Composable
actual fun rememberCompatShareLauncher(): (text: String, mimeType: String, absoluteFilePath: String?) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("共有") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("閉じる") } }) }
    return { text, _, path ->
        if (!pending) {
            pending = true
            scope.launch {
                try { DesktopOsIntegration.share(text, path) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "共有できませんでした" }
                finally { pending = false }
            }
        }
    }
}
