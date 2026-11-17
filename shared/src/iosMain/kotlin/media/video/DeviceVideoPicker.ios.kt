@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import androidx.compose.runtime.*
import com.valoser.futacha.shared.media.MediaFeature
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val videoPickerCleanup = CoroutineScope(SupervisorJob() + AppDispatchers.io)

@Composable
internal actual fun rememberDeviceVideoPicker(onSelected: suspend (VideoEditSource) -> Unit, onBusy: (Boolean) -> Unit, onError: (String) -> Unit): DeviceVideoPicker {
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val busy by rememberUpdatedState(onBusy)
    val error by rememberUpdatedState(onError)
    var active by remember { mutableStateOf<Job?>(null) }
    return DeviceVideoPicker(launch = { request -> if (active?.isActive != true && request.gate.isCurrent(request.permit)) {
        active = scope.launch {
            var owned: VideoEditSource? = null
            val operation = this
            val monitor = launch { request.gate.permits(MediaFeature.VIDEO_EDITOR).collect {
                if (!request.gate.isCurrent(request.permit)) operation.cancel()
            } }
            busy(true)
            try {
                val photos = awaitIosTwoOptionChoice("動画を選択", "スマホ内の動画を編集用に読み込みます（最大1GB）。", "写真ライブラリ", "ファイル") ?: return@launch
                val resource = pickVideoResource(photos) ?: return@launch
                if (resource is NSItemProvider) withTimeout(120_000) { owned = importPhotoVideo(request, resource) }
                else try {
                    withContext(AppDispatchers.io) { owned = importVideoUrl(request, resource as NSURL) }
                } finally {
                    // The Files picker copied the movie into tmp/<bundle>-Inbox; the
                    // import made its own work copy, so drop the picker's copy (up to 1GB).
                    withContext(NonCancellable + AppDispatchers.io) { deleteIosDocumentPickerInboxCopies(listOf(resource)) }
                }
                ensureActive()
                owned?.let { it.checkActive(); busy(false); selected(it) }
            } catch (timeout: TimeoutCancellationException) { if (request.gate.isCurrent(request.permit)) error("動画の取得がタイムアウトしました。ダウンロード済みの動画で再度お試しください") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (request.gate.isCurrent(request.permit)) error(failure.message ?: "動画を開けませんでした") }
            finally { monitor.cancel(); try { owned?.close() } finally { busy(false) } }
        }
    } }, cancel = { active?.cancel() })
}

/** Delegate is strongly held by the cancellation handler until selection or dismissal completes. */
private suspend fun pickVideoResource(photos: Boolean): Any? = suspendCancellableCoroutine { continuation ->
    var retained: NSObject? = null
    var finished = false
    val picker: UIViewController
    fun finish(controller: UIViewController, value: Any?) {
        if (finished) return
        finished = true
        controller.dismissViewControllerAnimated(true) { continuation.resume(value); retained = null }
    }
    if (photos) {
        val config = PHPickerConfiguration().apply {
            selectionLimit = 1; filter = PHPickerFilter.videosFilter
            preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
        }
        val photoPicker = PHPickerViewController(config)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                finish(picker, (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider)
            }
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) { finish(photoPicker, null) }
        }
        retained = delegate; photoPicker.delegate = delegate; picker = photoPicker
    } else {
        val files = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeMovie), asCopy = true)
        files.allowsMultipleSelection = false
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) { finish(controller, didPickDocumentsAtURLs.firstOrNull() as? NSURL) }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { finish(controller, null) }
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) { finish(files, null) }
        }
        retained = delegate; files.delegate = delegate; picker = files
    }
    continuation.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) {
        finished = true
        picker.dismissViewControllerAnimated(true) { retained = null }
    } }
    presentPicker(picker, "device video picker", onPresented = {
        picker.presentationController?.delegate = retained as? UIAdaptivePresentationControllerDelegateProtocol
    }, onPresentFailed = {
        if (!finished) { finished = true; retained = null; continuation.resumeWithException(IllegalStateException("動画の選択画面を開けません")) }
    })
}

/** NSItemProvider's temporary URL expires on callback return: finish the bounded copy inside it. */
private suspend fun importPhotoVideo(request: VideoPickRequest, provider: NSItemProvider): VideoEditSource = suspendCancellableCoroutine { continuation ->
    val copyJob = Job(continuation.context[Job])
    val type = provider.registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull {
        platform.UniformTypeIdentifiers.UTType.typeWithIdentifier(it)?.conformsToType(UTTypeMovie) == true
    } ?: "public.movie"
    val progress = provider.loadFileRepresentationForTypeIdentifier(type) { url, failure ->
        var owned: VideoEditSource? = null
        var delivered = false
        try {
            runBlocking(copyJob + AppDispatchers.io) {
                owned = importVideoUrl(request, requireNotNull(url) { failure?.localizedDescription ?: "動画を取得できません" })
            }
            val source = requireNotNull(owned)
            delivered = true
            continuation.resume(source, onCancellation = { _, value, _ -> videoPickerCleanup.launch { value.close() } })
        } catch (cancelled: CancellationException) { continuation.cancel(cancelled) }
        catch (error: Exception) { continuation.resumeWithException(error) }
        finally { if (!delivered) owned?.let { value -> videoPickerCleanup.launch { value.close() } }; copyJob.complete() }
    }
    continuation.invokeOnCancellation { progress.cancel(); copyJob.cancel() }
}

private suspend fun importVideoUrl(request: VideoPickRequest, url: NSURL): VideoEditSource {
    val access = url.startAccessingSecurityScopedResource()
    try {
        val path = requireNotNull(url.path) { "動画の場所を読み取れません" }
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
        (attributes?.get(NSFileSize) as? NSNumber)?.let { require(it.longLongValue <= VIDEO_EDIT_MAX_BYTES) { "動画は1GB以内で選択してください" } }
        return request.fileSystem.readByteStream(path) { reader ->
            VideoEditSource.import(request.fileSystem, NSTemporaryDirectory(), url.lastPathComponent ?: "video", reader, request.gate, request.permit)
        }.getOrThrow()
    } finally { if (access) url.stopAccessingSecurityScopedResource() }
}
