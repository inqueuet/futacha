package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.decode.ImageSource as createImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.pathExtension
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKSnapshotConfiguration
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
internal class IosVideoFrameFetcher(
    private val url: NSURL,
    private val options: Options,
    private val dataSource: DataSource
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val extension = url.pathExtension?.lowercase().orEmpty()
        val data = when (extension) {
            "webm" -> createWebKitThumbnail(url, options)
            else -> createAvFoundationThumbnail(url, options)
        }
        return SourceFetchResult(
            source = createImageSource(
                source = Buffer().write(data.toByteArray()),
                fileSystem = options.fileSystem,
                metadata = object : ImageSource.Metadata() {}
            ),
            mimeType = "image/png",
            dataSource = dataSource
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(
            data: String,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            val url = data.toVideoThumbnailUrlOrNull() ?: return null
            val extension = url.pathExtension?.lowercase().orEmpty()
            if (extension !in IOS_VIDEO_THUMBNAIL_EXTENSIONS) return null
            val dataSource = if (url.isFileURL()) DataSource.DISK else DataSource.NETWORK
            return IosVideoFrameFetcher(url, options, dataSource)
        }
    }
}

private val IOS_VIDEO_THUMBNAIL_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm")

private fun String.toVideoThumbnailUrlOrNull(): NSURL? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("/") -> NSURL.fileURLWithPath(trimmed)
        else -> NSURL.URLWithString(trimmed)
    }
}

private fun resolveMaximumThumbnailSize(options: Options): Pair<Double, Double>? {
    val width = options.size.width.pxOrElse { 0 }
    val height = options.size.height.pxOrElse { 0 }
    if (width <= 0 || height <= 0) return null
    return width.toDouble() to height.toDouble()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { bytes ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun createAvFoundationThumbnail(
    url: NSURL,
    options: Options
): NSData {
    val asset = AVURLAsset.URLAssetWithURL(url, options = null)
    val generator = AVAssetImageGenerator(asset)
    generator.appliesPreferredTrackTransform = true
    resolveMaximumThumbnailSize(options)?.let { (width, height) ->
        generator.maximumSize = CGSizeMake(width, height)
    }
    val cgImage = generator.copyCGImageAtTime(
        requestedTime = CMTimeMakeWithSeconds(0.0, preferredTimescale = 600),
        actualTime = null,
        error = null
    ) ?: error("Failed to generate video thumbnail for $url")
    val uiImage = UIImage.imageWithCGImage(cgImage)
    return UIImagePNGRepresentation(uiImage)
        ?: error("Failed to encode video thumbnail as PNG")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun createWebKitThumbnail(
    url: NSURL,
    options: Options
): NSData = suspendCancellableCoroutine { continuation ->
    val delegateRetainer = WebmThumbnailDelegateRetainer()
    continuation.invokeOnCancellation {
        dispatch_async(dispatch_get_main_queue()) {
            delegateRetainer.dispose()
        }
    }
    dispatch_async(dispatch_get_main_queue()) {
        val configuration = WKWebViewConfiguration().apply {
            allowsInlineMediaPlayback = true
        }
        val maximumSize = resolveMaximumThumbnailSize(options)
        val targetWidth = maximumSize?.first ?: DEFAULT_WEBKIT_THUMBNAIL_WIDTH
        val targetHeight = maximumSize?.second ?: DEFAULT_WEBKIT_THUMBNAIL_HEIGHT
        val webView = WKWebView(
            frame = CGRectMake(0.0, 0.0, targetWidth, targetHeight),
            configuration = configuration
        )
        val delegate = WebmThumbnailNavigationDelegate(
            webView = webView,
            url = url,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            onComplete = completion@{ result ->
                delegateRetainer.release()
                if (!continuation.isActive) return@completion
                result
                    .onSuccess { data -> continuation.resume(data) }
                    .onFailure { error -> continuation.resumeWithException(error) }
            }
        )
        delegateRetainer.retain(delegate)
        webView.navigationDelegate = delegate
        delegate.start()
    }
}

private const val DEFAULT_WEBKIT_THUMBNAIL_WIDTH = 320.0
private const val DEFAULT_WEBKIT_THUMBNAIL_HEIGHT = 180.0
private const val WEBKIT_READY_POLL_LIMIT = 40
private const val WEBKIT_READY_POLL_DELAY_MILLIS = 100L

private class WebmThumbnailDelegateRetainer {
    private var delegate: WebmThumbnailNavigationDelegate? = null

    fun retain(delegate: WebmThumbnailNavigationDelegate) {
        this.delegate = delegate
    }

    fun release() {
        delegate = null
    }

    fun dispose() {
        delegate?.dispose()
        delegate = null
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class WebmThumbnailNavigationDelegate(
    private val webView: WKWebView,
    private val url: NSURL,
    private val targetWidth: Double,
    private val targetHeight: Double,
    private val onComplete: (Result<NSData>) -> Unit
) : NSObject(), WKNavigationDelegateProtocol {
    private var completionDelivered = false
    private var pollAttempts = 0

    fun start() {
        webView.loadHTMLString(buildThumbnailHtml(url.absoluteString.orEmpty()), baseURL = null)
    }

    fun dispose() {
        if (completionDelivered) return
        completionDelivered = true
        cleanup()
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        pollVideoReady()
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError
    ) {
        finish(Result.failure(IllegalStateException(withError.localizedDescription)))
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError
    ) {
        finish(Result.failure(IllegalStateException(withError.localizedDescription)))
    }

    private fun pollVideoReady() {
        if (completionDelivered) return
        webView.evaluateJavaScript(
            "(function(){var v=document.querySelector('video'); if(!v){return 'missing';} return [v.readyState||0,v.videoWidth||0,v.videoHeight||0].join(',');})()"
        ) { result, error ->
            if (completionDelivered) return@evaluateJavaScript
            if (error != null) {
                finish(Result.failure(IllegalStateException(error.localizedDescription)))
                return@evaluateJavaScript
            }
            val status = (result as? String).orEmpty()
            val parts = status.split(',')
            val readyState = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val videoWidth = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val videoHeight = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            if (readyState >= 2 && videoWidth > 0.0 && videoHeight > 0.0) {
                val aspectHeight = (targetWidth / videoWidth * videoHeight)
                    .coerceAtLeast(1.0)
                webView.setFrame(
                    CGRectMake(0.0, 0.0, targetWidth, aspectHeight.coerceAtMost(targetHeight * 2.0))
                )
                snapshot()
                return@evaluateJavaScript
            }
            if (pollAttempts >= WEBKIT_READY_POLL_LIMIT) {
                finish(Result.failure(IllegalStateException("Timed out waiting for WebM frame for $url")))
                return@evaluateJavaScript
            }
            pollAttempts += 1
            dispatch_after_main(WEBKIT_READY_POLL_DELAY_MILLIS) {
                pollVideoReady()
            }
        }
    }

    private fun snapshot() {
        if (completionDelivered) return
        val configuration = WKSnapshotConfiguration().apply {
            rect = webView.bounds
        }
        webView.takeSnapshotWithConfiguration(configuration) { image, error ->
            if (completionDelivered) return@takeSnapshotWithConfiguration
            if (error != null) {
                finish(Result.failure(IllegalStateException(error.localizedDescription)))
                return@takeSnapshotWithConfiguration
            }
            val data = image?.let { uiImage -> UIImagePNGRepresentation(uiImage) }
            if (data == null) {
                finish(Result.failure(IllegalStateException("Failed to encode WebM thumbnail as PNG")))
                return@takeSnapshotWithConfiguration
            }
            finish(Result.success<NSData>(data))
        }
    }

    private fun finish(result: Result<NSData>) {
        if (completionDelivered) return
        completionDelivered = true
        cleanup()
        onComplete(result)
    }

    private fun cleanup() {
        webView.stopLoading()
        webView.navigationDelegate = null
    }
}

private fun dispatch_after_main(delayMillis: Long, block: () -> Unit) {
    val delayNanos = delayMillis * 1_000_000L
    dispatch_after(
        dispatch_time(DISPATCH_TIME_NOW, delayNanos),
        dispatch_get_main_queue()
    ) {
        block()
    }
}

private fun buildThumbnailHtml(rawUrl: String): String {
    val escapedUrl = escapeHtmlAttribute(rawUrl)
    return """
        <!doctype html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
        html,body{margin:0;padding:0;background:#f6f2df;overflow:hidden;}
        video{display:block;width:100%;height:100%;object-fit:contain;background:#f6f2df;}
        </style>
        </head>
        <body>
        <video muted playsinline preload="auto" autoplay>
            <source src="$escapedUrl" type="video/webm">
        </video>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtmlAttribute(value: String): String {
    return buildString(value.length + 16) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }
}
