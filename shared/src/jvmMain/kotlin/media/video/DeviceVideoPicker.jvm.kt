package com.valoser.futacha.shared.media.video

import androidx.compose.runtime.*
import com.valoser.futacha.shared.desktop.chooseDesktopFile
import kotlinx.coroutines.*

@Composable
internal actual fun rememberDeviceVideoPicker(onSelected: suspend (VideoEditSource) -> Unit, onBusy: (Boolean) -> Unit, onError: (String) -> Unit): DeviceVideoPicker {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val busy by rememberUpdatedState(onBusy)
    val selected by rememberUpdatedState(onSelected)
    val failed by rememberUpdatedState(onError)
    return DeviceVideoPicker({ request -> if (job?.isActive != true) job = scope.launch {
        var owned: VideoEditSource? = null
        busy(true)
        try {
            val file = chooseDesktopFile("動画を選択", setOf("mp4", "mov", "webm")) ?: return@launch
            request.fileSystem.readByteStream(file.absolutePath) { reader ->
                owned = VideoEditSource.import(request.fileSystem, "video_edit_sessions", file.name, reader, request.gate, request.permit)
            }.getOrThrow()
            ensureActive(); busy(false)
            owned?.let { it.checkActive(); selected(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failed(failure.message ?: "動画を読み込めませんでした") }
        finally { owned?.close(); busy(false) }
    } }, { job?.cancel() })
}
