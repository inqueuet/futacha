package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.desktop.*
import kotlinx.coroutines.*

@Composable
internal actual fun rememberCompatFontPickerLauncher(onSelected: (ImageData) -> Unit, onError: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val error by rememberUpdatedState(onError)
    return { scope.launch {
        try { chooseDesktopFile("フォントを選択", setOf("ttf", "otf"))?.let { selected(readDesktopAttachment(it, 32L * 1024 * 1024)) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error(failure.message ?: "フォントを読み込めません") }
    } }
}
