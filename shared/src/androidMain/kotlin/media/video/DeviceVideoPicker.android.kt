package com.valoser.futacha.shared.media.video

import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.isExperienceProfileSessionCurrent
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*

@Composable
internal actual fun rememberDeviceVideoPicker(onSelected: suspend (VideoEditSource) -> Unit, onBusy: (Boolean) -> Unit, onError: (String) -> Unit): DeviceVideoPicker {
    val context = LocalContext.current
    val profileController by rememberUpdatedState(LocalExperienceProfileUiController.current)
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val busy by rememberUpdatedState(onBusy)
    val error by rememberUpdatedState(onError)
    var pending by remember { mutableStateOf<VideoPickRequest?>(null) }
    var cancelledPending by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Job?>(null) }
    val launcher = rememberExperienceProfileActivityResultLauncher(ActivityResultContracts.OpenDocument()) { uri, session ->
        val request = pending
        val cancelled = cancelledPending
        pending = null
        if (request != null) active = scope.launch {
            var source: VideoEditSource? = null
            try {
                if (cancelled || uri == null || !request.gate.isCurrent(request.permit)) return@launch
                withContext(AppDispatchers.io) {
                    var name = "video"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) require(cursor.getLong(sizeColumn) <= VIDEO_EDIT_MAX_BYTES) { "動画は1GB以内で選択してください" }
                        }
                    }
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "動画を開けません" }.use { input ->
                        source = VideoEditSource.import(request.fileSystem, context.cacheDir.absolutePath, name,
                            object : FileReadSource {
                                override suspend fun read(bytes: ByteArray, offset: Int, length: Int) = runInterruptible { input.read(bytes, offset, length) }
                            }, request.gate, request.permit)
                    }
                }
                ensureActive()
                if (!isExperienceProfileSessionCurrent(session, profileController)) return@launch
                source?.let { it.checkActive(); busy(false); selected(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (request.gate.isCurrent(request.permit) && isExperienceProfileSessionCurrent(session, profileController)) error(failure.message ?: "動画を開けませんでした") }
            finally { try { source?.close() } finally { busy(false) } }
        }
        else busy(false)
    }
    return DeviceVideoPicker(launch = { request ->
        // An older picker remains pending across OFF/ON; never attribute its result to a newer permit.
        if (pending == null && active?.isActive != true && request.gate.isCurrent(request.permit)) {
            pending = request; cancelledPending = false; busy(true)
            try { launcher.launch(arrayOf("video/*")) }
            catch (failure: Exception) { pending = null; busy(false); error(failure.message ?: "動画を選択できません") }
        }
    }, cancel = { cancelledPending = true; active?.cancel(); busy(false) })
}
