@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.valoser.futacha.shared.media.source

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/** Owned by the native player. All delegate and close calls run on the main dispatcher. */
internal class IosOriginalMediaAsset(original: OriginalMediaPlayback, extension: String) : AutoCloseable {
    private val delegate = OriginalResourceLoader(original.retain(), extension)
    val asset = AVURLAsset(
        uRL = requireNotNull(NSURL.URLWithString("futacha-original://media/asset.$extension")), options = null
    ).apply { resourceLoader.setDelegate(delegate, dispatch_get_main_queue()) }

    override fun close() {
        asset.resourceLoader.setDelegate(null, dispatch_get_main_queue())
        delegate.close()
    }
}

private class OriginalResourceLoader(
    private val original: OriginalMediaPlayback,
    private val extension: String
) : NSObject(), AVAssetResourceLoaderDelegateProtocol {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = mutableMapOf<AVAssetResourceLoadingRequest, Job>()
    private var closed = false

    @ObjCSignatureOverride
    override fun resourceLoader(resourceLoader: AVAssetResourceLoader, shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest): Boolean {
        val request = shouldWaitForLoadingOfRequestedResource
        if (closed || request.request.URL?.scheme != "futacha-original") return false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // AVFoundation requires an exact length before finishing its information query.
                // Unknown-length responses keep downloading once, then publish the verified EOF.
                val info = original.info(requireSize = request.contentInformationRequest != null)
                request.contentInformationRequest?.let { content ->
                    val type = if (extension == "mov" || info.mimeType == "video/quicktime") "com.apple.quicktime-movie" else "public.mpeg-4"
                    val allowed = content.allowedContentTypes
                    check(allowed.isNullOrEmpty() || allowed.contains(type)) { "Unsupported video content type" }
                    content.contentType = type
                    content.contentLength = info.sizeBytes
                    content.byteRangeAccessSupported = true
                }
                request.dataRequest?.let { data ->
                    var position = maxOf(data.requestedOffset, data.currentOffset)
                    require(position >= 0 && data.requestedLength >= 0)
                    val end = if (data.requestsAllDataToEndOfResource) Long.MAX_VALUE else {
                        require(data.requestedOffset <= Long.MAX_VALUE - data.requestedLength)
                        data.requestedOffset + data.requestedLength
                    }
                    while (position < end) {
                        currentCoroutineContext().ensureActive()
                        val bytes = original.readAt(position, minOf(64 * 1024L, end - position).toInt())
                        if (closed || request.cancelled || request.finished) return@launch
                        if (bytes.isEmpty()) break
                        data.respondWithData(bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) })
                        position += bytes.size
                    }
                }
                if (!closed && !request.cancelled && !request.finished) request.finishLoading()
            } catch (cancelled: CancellationException) {
                // Caller cancellation and a failed/cleared original both release the native request.
                if (!closed && !request.cancelled && !request.finished) request.finishLoadingWithError(readError())
                throw cancelled
            } catch (_: Exception) {
                if (!closed && !request.cancelled && !request.finished) request.finishLoadingWithError(readError())
            } finally { requests.remove(request) }
        }
        requests[request] = job
        job.start()
        return true
    }

    @ObjCSignatureOverride
    override fun resourceLoader(resourceLoader: AVAssetResourceLoader, didCancelLoadingRequest: AVAssetResourceLoadingRequest) {
        requests.remove(didCancelLoadingRequest)?.cancel()
    }

    fun close() {
        if (closed) return
        closed = true
        for ((request, job) in requests.toMap()) {
            job.cancel()
            if (!request.cancelled && !request.finished) request.finishLoadingWithError(readError())
        }
        requests.clear(); scope.cancel(); original.close()
    }

    private fun readError() = NSError.errorWithDomain("FutachaOriginalMedia", -1, mapOf(NSLocalizedDescriptionKey to "動画データを読み込めませんでした"))
}
