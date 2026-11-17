@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSItemProvider
import platform.UIKit.UIPasteboard
import platform.posix.memcpy

@Composable
internal actual fun rememberClipboardImageReader(): suspend () -> ImageData = remember { { readClipboardImage() } }

internal suspend fun readClipboardImage(): ImageData {
    val choice = withContext(Dispatchers.Main) {
        val types = listOf("com.compuserve.gif", "org.webmproject.webp", "public.png", "public.jpeg")
        UIPasteboard.generalPasteboard.itemProviders.filterIsInstance<NSItemProvider>().firstNotNullOfOrNull { provider ->
            types.firstOrNull { provider.hasItemConformingToTypeIdentifier(it) }?.let { provider to it }
        }
    } ?: error("コピーされた画像がありません。画像そのものをコピーしてから貼り付けてください")
    // dataForPasteboardType can synchronously wait for another process (and hang
    // the main thread). Item providers load asynchronously and can be cancelled.
    return withTimeoutOrNull(30_000) {
        suspendCancellableCoroutine { continuation ->
            val progress = choice.first.loadDataRepresentationForTypeIdentifier(choice.second) { data, error ->
                val result = runCatching {
                    check(data != null && error == null) { "画像を読み込めませんでした。もう一度画像をコピーしてください" }
                    require(data.length > 0uL && data.length <= COMPAT_POST_PICKER_MAX_BYTES.toULong()) {
                        "画像を読み込めませんでした。32MB以下の画像をコピーしてください"
                    }
                    val bytes = ByteArray(data.length.toInt())
                    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                    clipboardImageData(bytes)
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
            continuation.invokeOnCancellation { progress.cancel() }
        }
    } ?: error("画像の読み込みがタイムアウトしました。もう一度画像をコピーしてください")
}
