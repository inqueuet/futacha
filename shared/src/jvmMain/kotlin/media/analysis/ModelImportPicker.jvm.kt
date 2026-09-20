package com.valoser.futacha.shared.media.analysis

import androidx.compose.runtime.*
import com.valoser.futacha.shared.desktop.chooseDesktopFile
import kotlinx.coroutines.*
import okio.source

@Composable
internal actual fun rememberModelImportPicker(onBusy: (Boolean) -> Unit, onImported: (AnalysisModel) -> Unit, onError: (String) -> Unit): ModelImportPicker {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val busy by rememberUpdatedState(onBusy)
    val imported by rememberUpdatedState(onImported)
    val failed by rememberUpdatedState(onError)
    return ModelImportPicker({ request -> if (job?.isActive != true) job = scope.launch {
        busy(true)
        try {
            val file = chooseDesktopFile("ONNXモデルを選択", setOf("onnx")) ?: return@launch
            withContext(Dispatchers.IO) { request.store.import(request.model, request.permit) { file.source() } }
            ensureActive()
            if (request.gate.isCurrent(request.permit)) imported(request.model)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failed(failure.message ?: "モデルを読み込めませんでした") }
        finally { busy(false) }
    } }, { job?.cancel() })
}
