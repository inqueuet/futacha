package com.valoser.futacha.shared.ui.image

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.valoser.futacha.shared.media.source.OriginalMediaRequest
import com.valoser.futacha.shared.media.source.originalMediaSourceOrNull
import com.valoser.futacha.shared.network.readBoundedHttpResponseBytes
import com.valoser.futacha.shared.util.isDesktop
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal expect suspend fun copyImageBytesToClipboard(bytes: ByteArray)

internal suspend fun copyImageUrlToClipboard(client: HttpClient, url: String, writeImage: suspend (ByteArray) -> Unit = ::copyImageBytesToClipboard) = withTimeout(30_000L) {
    val maximum = 32 * 1024 * 1024
    val source = client.originalMediaSourceOrNull()
    val bytes = if (source != null) {
        val lease = source.acquire(OriginalMediaRequest(url))
        try {
            require(lease.info.sizeBytes in 1..maximum.toLong()) { "コピーする画像が大きすぎます" }
            lease.readAt(0L, lease.info.sizeBytes.toInt())
        } finally { lease.close() }
    } else client.prepareGet(url).execute { response ->
        check(response.status.isSuccess()) { "画像を取得できませんでした" }
        readBoundedHttpResponseBytes(response, maximum)
    }
    writeImage(bytes)
}

/** Available only on Desktop. Uses the host's original-media cache and never copies a thumbnail. */
@Composable
internal fun rememberImageClipboardAction(url: String?, client: HttpClient?): (() -> Unit)? {
    if (!isDesktop()) return null
    // The copy and its notice stay when the page turns to a video (url null): returning before these were remembered
    // dropped them, which cancelled a copy in progress without a word (audit L32).
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message, busy) {
        if (message != null && !busy) { delay(5000); message = null }
    }
    message?.let { text -> FutachaAppLockAwareWindow { Popup(alignment = Alignment.BottomCenter) {
        Surface(Modifier.padding(16.dp).testTag("image-clipboard-notice")) {
            Row(Modifier.padding(8.dp)) {
                Text(text, modifier = Modifier.padding(8.dp))
                if (!busy) TextButton(onClick = { message = null }) { Text("閉じる") }
            }
        }
    } } }
    if (url == null || client == null) return null
    return {
        if (!busy) {
            busy = true
            message = "画像をコピー中…"
            scope.launch {
                try { copyImageUrlToClipboard(client, url); message = "画像をコピーしました" }
                catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); message = "画像のコピーがタイムアウトしました" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "画像をコピーできませんでした" }
                finally { busy = false }
            }
        }
    }
}
