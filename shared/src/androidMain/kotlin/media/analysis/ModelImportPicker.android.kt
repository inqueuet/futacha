package com.valoser.futacha.shared.media.analysis

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.isExperienceProfileSessionCurrent
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import kotlinx.coroutines.*
import okio.source

@Composable
internal actual fun rememberModelImportPicker(onBusy: (Boolean) -> Unit, onImported: (AnalysisModel) -> Unit, onError: (String) -> Unit): ModelImportPicker {
    val context = LocalContext.current
    val controller by rememberUpdatedState(LocalExperienceProfileUiController.current)
    val busy by rememberUpdatedState(onBusy)
    val imported by rememberUpdatedState(onImported)
    val error by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ModelImportRequest?>(null) }
    var cancelledPending by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Job?>(null) }
    val launcher = rememberExperienceProfileActivityResultLauncher(ActivityResultContracts.OpenDocument()) { uri, session ->
        val request = pending
        pending = null
        if (request == null || uri == null || cancelledPending || !request.gate.isCurrent(request.permit) ||
            !isExperienceProfileSessionCurrent(session, controller)) busy(false)
        else active = scope.launch {
            try {
                request.store.import(request.model, request.permit) {
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "モデルファイルを開けません" }.source()
                }
                ensureActive()
                if (request.gate.isCurrent(request.permit) && isExperienceProfileSessionCurrent(session, controller)) imported(request.model)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request.gate.isCurrent(request.permit) && isExperienceProfileSessionCurrent(session, controller)) error(failure.message ?: "モデルを取り込めませんでした")
            } finally { busy(false) }
        }
    }
    return ModelImportPicker(launch = { request ->
        if (pending == null && active?.isCompleted != false && request.gate.isCurrent(request.permit)) {
            pending = request; cancelledPending = false; busy(true)
            try { launcher.launch(arrayOf("*/*")) }
            catch (failure: Exception) { pending = null; busy(false); error(failure.message ?: "モデルを選択できません") }
        }
    }, cancel = { cancelledPending = true; active?.cancel(); if (active?.isCompleted != false) busy(false) })
}
