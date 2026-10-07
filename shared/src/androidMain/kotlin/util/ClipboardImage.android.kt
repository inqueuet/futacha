package com.valoser.futacha.shared.util

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberClipboardImageReader(): suspend () -> ImageData {
    val context = LocalContext.current
    return remember(context) { { readClipboardImage(context) } }
}

internal suspend fun readClipboardImage(context: Context): ImageData {
    val uri = withContext(Dispatchers.Main.immediate) {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        (0 until (clip?.itemCount ?: 0)).firstNotNullOfOrNull { index ->
            clip?.getItemAt(index)?.uri?.takeIf { it.scheme == "content" }
        }
    } ?: error("コピーされた画像がありません。画像そのものをコピーしてから貼り付けてください")
    return withContext(Dispatchers.IO) {
        val image = readImageDataFromUri(context, uri, COMPAT_POST_PICKER_MAX_BYTES)
            ?: error("画像を読み込めませんでした。32MB以下の画像をもう一度コピーしてください")
        clipboardImageData(image.bytes)
    }
}
