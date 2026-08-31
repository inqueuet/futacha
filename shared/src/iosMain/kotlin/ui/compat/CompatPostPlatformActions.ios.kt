@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.util.currentIosPresentationController
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import platform.AVFoundation.AVPlayer
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIViewController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import kotlin.coroutines.coroutineContext
import platform.Speech.SFSpeechRecognizer
import platform.posix.memcpy
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Clock

private val compatIosPreviewUnsafeFileNameRegex = Regex("[^A-Za-z0-9._-]")
private const val COMPAT_IOS_ENCODED_IMAGE_MAX_BYTES = 32L * 1024L * 1024L
private const val COMPAT_IOS_PHASH_INPUT_MAX_BYTES = 32 * 1024 * 1024

@Composable
internal actual fun rememberCompatSpeechRecognizer(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val currentResult = rememberUpdatedState(onResult)
    val currentError = rememberUpdatedState(onError)
    val session = remember { IosCompatSpeechSession() }
    DisposableEffect(session) {
        onDispose { session.stop() }
    }
    return remember(session) {
        {
            session.toggle(
                onResult = { text -> currentResult.value(text) },
                onError = { message -> currentError.value(message) }
            )
        }
    }
}

internal actual fun compatPostNetworkInfo(): String = "回線情報: iOS"

actual fun initializeCompatPostPlatformContext(context: Any) = Unit

internal actual fun compatPostDeviceInfo(appVersion: String): String = formatCompatPostDeviceInfo(
    appVersion = appVersion,
    brand = "Apple",
    model = UIDevice.currentDevice.model,
    platformVersion = "iOS ${UIDevice.currentDevice.systemVersion}"
)

internal actual suspend fun compressCompatPostImage(
    attachment: ImageData,
    maxBytes: Int
): Result<ImageData> = withContext(AppDispatchers.io) {
    runSuspendCatchingPreservingCancellation {
        coroutineContext.ensureActive()
        require(maxBytes > 0) { "画像サイズの上限が不正です" }
        require(attachment.bytes.isNotEmpty()) { "画像データが空です" }
        val original = UIImage.imageWithData(attachment.bytes.toNSData())
            ?: error("画像を読み込めませんでした")
        require(original.size.useContents { width > 0.0 && height > 0.0 }) { "画像を読み込めませんでした" }
        val originalSize = original.size.useContents { width to height }
        val originalPixels = originalSize.first * originalSize.second
        val initialScale = if (originalPixels > COMPAT_POST_IMAGE_MAX_DECODE_PIXELS) {
            sqrt(COMPAT_POST_IMAGE_MAX_DECODE_PIXELS / originalPixels)
        } else {
            1.0
        }
        var image = if (initialScale < 1.0) {
            original.scaled(
                max(1, (originalSize.first * initialScale).roundToInt()),
                max(1, (originalSize.second * initialScale).roundToInt())
            )
        } else {
            original
        }
        var quality = 0.92
        var encoded: ByteArray? = null
        for (iteration in 0 until 18) {
            coroutineContext.ensureActive()
            val candidateData = UIImageJPEGRepresentation(image, quality)
                ?: error("画像を圧縮できませんでした")
            val candidate = candidateData.toByteArrayOrNull(maxBytes.toLong())
            if (candidate != null) {
                encoded = candidate
                break
            } else if (quality > 0.58) {
                quality = (quality - 0.08).coerceAtLeast(0.5)
            } else {
                image = image.scaled(
                    max(1, image.size.useContents { (width * 0.82).roundToInt() }),
                    max(1, image.size.useContents { (height * 0.82).roundToInt() })
                )
                quality = 0.86
            }
        }
        val bytes = requireNotNull(encoded) { "上限以内に圧縮できませんでした" }
        val stem = attachment.fileName.substringBeforeLast('.', attachment.fileName).ifBlank { "attachment" }
        coroutineContext.ensureActive()
        ImageData(bytes = bytes, fileName = "$stem.jpg")
    }
}

internal actual fun compatPostImageAspectRatio(bytes: ByteArray): Float? {
    if (bytes.isEmpty() || bytes.size > COMPAT_IOS_ENCODED_IMAGE_MAX_BYTES) return null
    val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
    return image.size.useContents {
        if (width > 0.0 && height > 0.0) {
            (width / height).toFloat().takeIf { it.isFinite() && it > 0f }
        } else {
            null
        }
    }
}

internal actual suspend fun computeCompatImagePhashFromBytes(bytes: ByteArray): String? =
    withContext(AppDispatchers.io) {
        if (bytes.isEmpty() || bytes.size > COMPAT_IOS_PHASH_INPUT_MAX_BYTES) return@withContext null
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return@withContext null
        val cgImage = image.CGImage ?: return@withContext null
        if (CGImageGetWidth(cgImage).toLong() * CGImageGetHeight(cgImage).toLong() > COMPAT_PHASH_MAX_SOURCE_PIXELS) {
            return@withContext null
        }
        val size = CompatImagePhash.SIZE
        val raw = ByteArray(size * size * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        try {
        raw.usePinned { pinned ->
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = size.toULong(),
                height = size.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = (size * 4).toULong(),
                space = colorSpace,
                bitmapInfo = 0u
            ) ?: return@withContext null
            try {
                CGContextDrawImage(context, CGRectMake(0.0, 0.0, size.toDouble(), size.toDouble()), cgImage)
            } finally {
                CGContextRelease(context)
            }
        }
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
        // A bitmap context with the default iOS little-endian layout yields
        // BGRA bytes.  Convert to Android's ARGB before applying the shared
        // 32x32/DCT implementation, so persisted NG hashes are compatible.
        val pixels = IntArray(size * size) { index ->
            val offset = index * 4
            val blue = raw[offset].toInt() and 0xff
            val green = raw[offset + 1].toInt() and 0xff
            val red = raw[offset + 2].toInt() and 0xff
            val alpha = raw[offset + 3].toInt() and 0xff
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        CompatImagePhash.computeFromArgbPixels(pixels)
    }

private const val COMPAT_POST_IMAGE_MAX_DECODE_PIXELS = 8_000_000.0
private const val COMPAT_PHASH_MAX_SOURCE_PIXELS = 16_000_000L

internal actual suspend fun renderCompatDrawingPng(
    strokes: List<CompatDrawingStroke>,
    backgroundArgb: Int,
    widthPx: Int,
    heightPx: Int
): Result<ImageData> = withContext(AppDispatchers.io) {
    runCatching {
        validateCompatDrawingRender(strokes, widthPx, heightPx)
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(widthPx.toDouble(), heightPx.toDouble()), false, 1.0)
        try {
            val context = UIGraphicsGetCurrentContext() ?: error("手書きキャンバスを作成できませんでした")
            colorFromArgb(backgroundArgb).setFill()
            CGContextFillRect(context, CGRectMake(0.0, 0.0, widthPx.toDouble(), heightPx.toDouble()))
            strokes.forEach { stroke ->
                val points = stroke.points
                if (points.isEmpty()) return@forEach
                val path = UIBezierPath.bezierPath()
                path.lineWidth = stroke.widthPx.coerceAtLeast(1f).toDouble()
                path.moveToPoint(CGPointMake(points.first().x.toDouble(), points.first().y.toDouble()))
                if (points.size == 1) {
                    path.addLineToPoint(CGPointMake(points.first().x.toDouble() + 0.1, points.first().y.toDouble() + 0.1))
                } else {
                    points.drop(1).forEach { point -> path.addLineToPoint(CGPointMake(point.x.toDouble(), point.y.toDouble())) }
                }
                colorFromArgb(stroke.colorArgb).setStroke()
                path.stroke()
            }
            val image = UIGraphicsGetImageFromCurrentImageContext() ?: error("手書き画像を保存できませんでした")
            val bytes = UIImagePNGRepresentation(image)
                ?.toByteArrayOrNull(COMPAT_IOS_ENCODED_IMAGE_MAX_BYTES)
                ?: error("手書き画像を保存できませんでした")
            ImageData(bytes = bytes, fileName = "tegaki-${Clock.System.now().toEpochMilliseconds()}.png")
        } finally {
            UIGraphicsEndImageContext()
        }
    }
}

@Composable
internal actual fun CompatDrawingLandscapeEffect() = Unit

@Composable
internal actual fun rememberCompatVideoAttachmentPreviewLauncher(
    onError: (String) -> Unit
): (ImageData) -> Unit {
    val scope = rememberCoroutineScope()
    val currentError = rememberUpdatedState(onError)
    return { attachment ->
        scope.launch {
            var unownedPreviewPath: String? = null
            try {
                val extension = attachment.fileName.substringAfterLast('.', "mp4")
                    .lowercase().ifBlank { "mp4" }
                require(extension in setOf("mp4", "m4v", "mov", "webm")) {
                    "対応していない動画形式です"
                }
                val safeName = attachment.fileName.replace(compatIosPreviewUnsafeFileNameRegex, "_")
                    .ifBlank { "compat-preview.$extension" }
                    .take(120)
                val path = withContext(AppDispatchers.io) {
                    val target = NSTemporaryDirectory() + "compat-preview-${Clock.System.now().toEpochMilliseconds()}-$safeName"
                    require(attachment.bytes.toNSData().writeToFile(target, atomically = true)) {
                        "動画の一時ファイルを作成できませんでした"
                    }
                    target
                }
                unownedPreviewPath = path
                dispatch_async(dispatch_get_main_queue()) {
                    val presenter = currentIosPresentationController()
                    if (presenter == null) {
                        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                        currentError.value("動画プレビューを表示できません")
                        return@dispatch_async
                    }
                    val controller = if (extension == "webm") {
                        // AVPlayer does not decode WebM.  Match the existing
                        // iOS thread viewer by using an isolated WKWebView for
                        // the temporary local attachment.
                        object : UIViewController(nibName = null, bundle = null) {
                            override fun viewDidDisappear(animated: Boolean) {
                                super.viewDidDisappear(animated)
                                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                            }
                        }.apply {
                            val web = WKWebView(
                                frame = CGRectMake(0.0, 0.0, 1.0, 1.0),
                                configuration = WKWebViewConfiguration().apply {
                                    allowsInlineMediaPlayback = true
                                }
                            )
                            web.setFrame(view.bounds)
                            view.addSubview(web)
                            val escaped = NSURL.fileURLWithPath(path).absoluteString
                                ?.replace("&", "&amp;")
                                ?.replace("\"", "&quot;")
                                .orEmpty()
                            web.loadHTMLString(
                                "<html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><body style=\"margin:0;background:#000\"><video controls autoplay playsinline style=\"width:100%;height:100%\" src=\"$escaped\"></video></body></html>",
                                baseURL = NSURL.fileURLWithPath(path).URLByDeletingLastPathComponent
                            )
                        }
                    } else {
                        val player = AVPlayer.playerWithURL(NSURL.fileURLWithPath(path))
                        object : AVPlayerViewController(nibName = null, bundle = null) {
                            override fun viewDidDisappear(animated: Boolean) {
                                this.player = null
                                super.viewDidDisappear(animated)
                                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                            }
                        }.apply { this.player = player }
                    }
                    presenter.presentViewController(controller, animated = true, completion = null)
                }
                // The presented controller (or the presenter-unavailable branch)
                // now owns deletion of the temporary file.
                unownedPreviewPath = null
            } catch (cancelled: CancellationException) {
                val target = unownedPreviewPath
                if (target != null) withContext(NonCancellable + AppDispatchers.io) {
                    NSFileManager.defaultManager.removeItemAtPath(target, error = null)
                }
                throw cancelled
            } catch (error: Throwable) {
                unownedPreviewPath?.let { target ->
                    withContext(AppDispatchers.io) {
                        NSFileManager.defaultManager.removeItemAtPath(target, error = null)
                    }
                }
                currentError.value("動画を開けませんでした: ${error.message.orEmpty()}")
            }
        }
    }
}

@Composable
internal actual fun CompatPostImePolicyEffect() = Unit

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private fun NSData.toByteArrayOrNull(maxBytes: Long): ByteArray? {
    if (maxBytes < 0L || length > maxBytes.toULong() || length > Int.MAX_VALUE.toULong()) return null
    val byteCount = length.toInt()
    return ByteArray(byteCount).also { output ->
        if (output.isNotEmpty()) {
            output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
        }
    }
}

private fun UIImage.scaled(width: Int, height: Int): UIImage {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), false, 1.0)
    return try {
        drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        UIGraphicsGetImageFromCurrentImageContext() ?: this
    } finally {
        UIGraphicsEndImageContext()
    }
}

private fun colorFromArgb(color: Int): UIColor = UIColor.colorWithRed(
    red = ((color ushr 16) and 0xff) / 255.0,
    green = ((color ushr 8) and 0xff) / 255.0,
    blue = (color and 0xff) / 255.0,
    alpha = ((color ushr 24) and 0xff) / 255.0
)

/** A short-lived, Compose-owned SFSpeechRecognizer session for the post form. */
private class IosCompatSpeechSession {
    private val recognizer = SFSpeechRecognizer(NSLocale(localeIdentifier = "ja-JP"))
    private var engine: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: platform.Speech.SFSpeechRecognitionTask? = null

    fun toggle(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (engine?.running == true) {
            stop()
            return
        }
        when (SFSpeechRecognizer.authorizationStatus().value) {
            IOS_SPEECH_AUTHORIZED -> requestMicrophoneThenStart(onResult, onError)
            IOS_SPEECH_NOT_DETERMINED -> {
                SFSpeechRecognizer.requestAuthorization { status ->
                    if (status.value == IOS_SPEECH_AUTHORIZED) {
                        requestMicrophoneThenStart(onResult, onError)
                    } else {
                        onError("音声認識を許可してください。許可しない場合はキーボード入力を利用できます")
                    }
                }
            }
            else -> onError("音声認識を許可してください。許可しない場合はキーボード入力を利用できます")
        }
    }

    fun stop() {
        engine?.inputNode?.removeTapOnBus(0u)
        engine?.stop()
        request?.endAudio()
        task?.cancel()
        task = null
        request = null
        engine = null
    }

    private fun requestMicrophoneThenStart(onResult: (String) -> Unit, onError: (String) -> Unit) {
        AVAudioSession.sharedInstance().requestRecordPermission { allowed ->
            if (allowed) start(onResult, onError)
            else onError("マイクを許可してください。許可しない場合はキーボード入力を利用できます")
        }
    }

    private fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!recognizer.isAvailable()) {
            onError("この端末では音声認識を利用できません。キーボード入力を利用してください")
            return
        }
        stop()
        val nextRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            // The post form appends a recognition result to the comment.  Do
            // not emit interim text repeatedly, or each partial phrase would
            // be appended as a separate line.
            shouldReportPartialResults = false
        }
        val nextEngine = AVAudioEngine()
        val input = nextEngine.inputNode
        val format = input.outputFormatForBus(0u)
        input.installTapOnBus(0u, bufferSize = 1_024u, format = format) { buffer, _ ->
            buffer?.let(nextRequest::appendAudioPCMBuffer)
        }
        task = recognizer.recognitionTaskWithRequest(nextRequest) { result, error ->
            if (result?.isFinal() == true) {
                result.bestTranscription.formattedString
                    .takeIf { text -> text.isNotBlank() }
                    ?.let(onResult)
                stop()
            }
            if (error != null) {
                stop()
                onError("音声認識に失敗しました: ${error.localizedDescription}")
            }
        }
        if (!nextEngine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            task?.cancel()
            task = null
            onError("マイク入力を開始できませんでした。キーボード入力を利用してください")
            return
        }
        engine = nextEngine
        request = nextRequest
    }
}

private const val IOS_SPEECH_NOT_DETERMINED = 0L
private const val IOS_SPEECH_AUTHORIZED = 3L
