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
import platform.AVFoundation.AVAssetImageGeneratorSucceeded
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.valueWithCMTime
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSValue
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalForeignApi::class)
internal class IosVideoFrameFetcher(
    private val url: NSURL,
    private val options: Options,
    private val dataSource: DataSource
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val extension = url.pathExtension?.lowercase().orEmpty()
        val data = when (extension) {
            "webm" -> createCachedWebKitThumbnail(url, options)
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
private val webKitThumbnailSemaphore = Semaphore(permits = 1)
private val webKitThumbnailCacheMutex = Mutex()
private val webKitThumbnailCache = LinkedHashMap<String, NSData>()

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
private suspend fun createAvFoundationThumbnail(
    url: NSURL,
    options: Options
): NSData {
    val asset = AVURLAsset.URLAssetWithURL(url, options = null)
    val generator = AVAssetImageGenerator(asset)
    generator.appliesPreferredTrackTransform = true
    resolveMaximumThumbnailSize(options)?.let { (width, height) ->
        generator.maximumSize = CGSizeMake(width, height)
    }
    return withTimeoutOrNull(AV_THUMBNAIL_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                generator.cancelAllCGImageGeneration()
            }
            val requestedTime = NSValue.valueWithCMTime(
                CMTimeMakeWithSeconds(0.0, preferredTimescale = 600)
            )
            generator.generateCGImagesAsynchronouslyForTimes(
                listOf(requestedTime)
            ) { _, cgImage, _, result, generationError ->
                if (!continuation.isActive) return@generateCGImagesAsynchronouslyForTimes
                if (result == AVAssetImageGeneratorSucceeded && cgImage != null) {
                    val data = UIImagePNGRepresentation(UIImage.imageWithCGImage(cgImage))
                    if (data != null) {
                        continuation.resume(data)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("Failed to encode video thumbnail as PNG")
                        )
                    }
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            generationError?.localizedDescription
                                ?: "Failed to generate video thumbnail for $url"
                        )
                    )
                }
            }
        }
    } ?: error("Timed out generating video thumbnail for $url")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun createCachedWebKitThumbnail(
    url: NSURL,
    options: Options
): NSData {
    val (targetWidth, targetHeight) = resolveWebKitThumbnailSize(options)
    val cacheKey = buildWebKitThumbnailCacheKey(url, targetWidth, targetHeight)
    readCachedWebKitThumbnail(cacheKey)?.let { return it }
    return webKitThumbnailSemaphore.withPermit {
        readCachedWebKitThumbnail(cacheKey)?.let { return@withPermit it }
        createWebKitThumbnail(
            url = url,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        ).also { data ->
            rememberWebKitThumbnail(cacheKey, data)
        }
    }
}

private suspend fun readCachedWebKitThumbnail(cacheKey: String): NSData? {
    return webKitThumbnailCacheMutex.withLock {
        webKitThumbnailCache.remove(cacheKey)?.also { data ->
            webKitThumbnailCache[cacheKey] = data
        }
    }
}

private suspend fun rememberWebKitThumbnail(cacheKey: String, data: NSData) {
    webKitThumbnailCacheMutex.withLock {
        webKitThumbnailCache.remove(cacheKey)
        webKitThumbnailCache[cacheKey] = data
        while (webKitThumbnailCache.size > WEBKIT_THUMBNAIL_CACHE_MAX_ENTRIES) {
            val eldestKey = webKitThumbnailCache.keys.firstOrNull() ?: break
            webKitThumbnailCache.remove(eldestKey)
        }
    }
}

private fun buildWebKitThumbnailCacheKey(
    url: NSURL,
    width: Double,
    height: Double
): String {
    return "${url.absoluteString.orEmpty()}#${width.toInt()}x${height.toInt()}"
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun createWebKitThumbnail(
    url: NSURL,
    targetWidth: Double,
    targetHeight: Double
): NSData {
    return withTimeoutOrNull(WEBKIT_THUMBNAIL_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
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
    } ?: error("Timed out generating WebM thumbnail for $url")
}

private const val DEFAULT_WEBKIT_THUMBNAIL_WIDTH = 160.0
private const val DEFAULT_WEBKIT_THUMBNAIL_HEIGHT = 90.0
private const val MAX_WEBKIT_THUMBNAIL_WIDTH = 240.0
private const val MAX_WEBKIT_THUMBNAIL_HEIGHT = 180.0
private const val WEBKIT_THUMBNAIL_CACHE_MAX_ENTRIES = 32
private const val WEBKIT_READY_POLL_LIMIT = 25
private const val WEBKIT_READY_POLL_DELAY_MILLIS = 100L
private const val WEBKIT_THUMBNAIL_TIMEOUT_MILLIS = 10_000L
private const val AV_THUMBNAIL_TIMEOUT_MILLIS = 20_000L

private fun resolveWebKitThumbnailSize(options: Options): Pair<Double, Double> {
    val maximumSize = resolveMaximumThumbnailSize(options)
    val width = (maximumSize?.first ?: DEFAULT_WEBKIT_THUMBNAIL_WIDTH)
        .coerceIn(1.0, MAX_WEBKIT_THUMBNAIL_WIDTH)
    val height = (maximumSize?.second ?: DEFAULT_WEBKIT_THUMBNAIL_HEIGHT)
        .coerceIn(1.0, MAX_WEBKIT_THUMBNAIL_HEIGHT)
    return width to height
}

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
        webView.evaluateJavaScript(
            "(function(){var v=document.querySelector('video'); if(v){v.pause(); v.removeAttribute('src'); v.load();} document.body.innerHTML='';})()",
            completionHandler = null
        )
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
        <video muted playsinline preload="metadata">
            <source src="$escapedUrl" type="video/webm">
        </video>
        <script>
        (function(){
          var v=document.querySelector('video');
          if(!v){return;}
          var seek=function(){
            try {
              var t = isFinite(v.duration) && v.duration > 0.2 ? 0.1 : 0;
              v.currentTime = t;
            } catch(e) {}
          };
          v.addEventListener('loadedmetadata', seek, {once:true});
          v.load();
        })();
        </script>
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
