package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import com.valoser.futacha.shared.desktop.DesktopOsIntegration
import kotlinx.coroutines.*

@Composable
internal actual fun rememberCompatWatcherNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf(false) }
    return {
        if (!pending) {
            pending = true
            scope.launch {
                try { callback(DesktopOsIntegration.notificationPermission()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { callback(false) }
                finally { pending = false }
            }
        }
    }
}

@Composable
internal actual fun rememberCompatWatcherNotificationSettings(): () -> Result<Unit> = {
    runCatching { DesktopOsIntegration.openNotificationSettings() }
}
