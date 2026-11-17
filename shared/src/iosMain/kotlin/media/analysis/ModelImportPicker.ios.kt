@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.analysis

import androidx.compose.runtime.*
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.presentPicker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun rememberModelImportPicker(onBusy: (Boolean) -> Unit, onImported: (AnalysisModel) -> Unit, onError: (String) -> Unit): ModelImportPicker {
    val scope = rememberCoroutineScope()
    val busy by rememberUpdatedState(onBusy)
    val imported by rememberUpdatedState(onImported)
    val error by rememberUpdatedState(onError)
    var active by remember { mutableStateOf<Job?>(null) }
    return ModelImportPicker(launch = { request ->
        if (active?.isCompleted != false && request.gate.isCurrent(request.permit)) active = scope.launch {
            val operation = this
            val monitor = launch(start = CoroutineStart.UNDISPATCHED) {
                request.gate.permits(request.permit.feature).first { !request.gate.isCurrent(request.permit) }
                operation.cancel()
            }
            busy(true)
            try {
                val url = pickModelUrl() ?: return@launch
                withContext(AppDispatchers.io) {
                    val access = url.startAccessingSecurityScopedResource()
                    try {
                        request.store.import(request.model, request.permit) {
                            FileSystem.SYSTEM.source(requireNotNull(url.path) { "モデルの場所を読み取れません" }.toPath())
                        }
                    } finally { if (access) url.stopAccessingSecurityScopedResource() }
                }
                ensureActive()
                if (request.gate.isCurrent(request.permit)) imported(request.model)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (request.gate.isCurrent(request.permit)) error(failure.message ?: "モデルを取り込めませんでした") }
            finally { monitor.cancel(); busy(false) }
        }
    }, cancel = { active?.cancel() })
}

private suspend fun pickModelUrl(): NSURL? = suspendCancellableCoroutine { continuation ->
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = false)
    picker.allowsMultipleSelection = false
    var retained: NSObject? = null
    var finished = false
    fun finish(value: NSURL?) {
        if (finished) return
        finished = true
        picker.dismissViewControllerAnimated(true) { continuation.resume(value); retained = null }
    }
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) { finish(didPickDocumentsAtURLs.firstOrNull() as? NSURL) }
        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { finish(null) }
        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) { finish(null) }
    }
    retained = delegate; picker.delegate = delegate
    continuation.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) {
        finished = true
        picker.dismissViewControllerAnimated(true) { retained = null }
    } }
    presentPicker(picker, "analysis model picker", onPresented = {
        picker.presentationController?.delegate = retained as? UIAdaptivePresentationControllerDelegateProtocol
    }, onPresentFailed = {
        if (!finished) { finished = true; retained = null; continuation.resumeWithException(IllegalStateException("モデルの選択画面を開けません")) }
    })
}
