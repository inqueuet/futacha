package com.valoser.futacha.shared.util

import androidx.compose.runtime.*
import com.valoser.futacha.shared.desktop.openDesktopUrl
import kotlinx.coroutines.*
import javax.swing.JOptionPane

@Composable
actual fun rememberUrlLauncher(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) { { value: String -> scope.launch {
        try { withContext(Dispatchers.IO) { openDesktopUrl(value) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // Only our own (Japanese) validation messages are shown; the JDK's are English.
            val message = (failure as? IllegalArgumentException)?.message ?: "リンクを開けませんでした"
            JOptionPane.showMessageDialog(null, message, "ふたちゃ", JOptionPane.ERROR_MESSAGE)
        }
    }; Unit } }
}
