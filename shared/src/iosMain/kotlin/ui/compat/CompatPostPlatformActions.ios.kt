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
import kotlinx.cinterop.autoreleasepool
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
import com.valoser.futacha.shared.audio.IosPlaybackAudioLease
import com.valoser.futacha.shared.audio.acquireIosPlaybackAudioSession
import com.valoser.futacha.shared.audio.iosAudioSessionCoordinator
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
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
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
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue
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
        require(attachment.bytes.size <= COMPAT_IOS_ENCODED_IMAGE_MAX_BYTES) { "画像データが大きすぎます" }
        val format = detectCompatPostImageFormat(attachment.bytes)
        // GIF/PNG/WebP keep animation and transparency when only metadata has
        // to go, the stored pixels are already upright and they fit (U-1).
        val orientation = compatExifOrientationTransform(
            if (format == CompatPostImageFormat.GIF) 1 else readIosEncodedImageSize(attachment.bytes)?.orientation ?: 1
        )
        compatPostLosslessSanitizedImage(attachment.bytes, format, orientation, maxBytes)?.let { sanitized ->
            return@runSuspendCatchingPreservingCancellation ImageData(
                bytes = sanitized,
                fileName = compatPostSanitizedFileName(attachment.fileName, requireNotNull(format.extension))
            )
        }
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
            downsampleIosPostImage(
                attachment.bytes,
                max(1, (max(originalSize.first, originalSize.second) * initialScale).toInt())
            ) ?: error("画像を縮小できませんでした")
        } else {
            original
        }
        var encoded: ByteArray? = null
        var extension = "jpg"
        if (image.hasCompatAlpha()) {
            // Keep transparency as PNG when it fits; JPEG would turn it black.
            // PNG ignores UIImage.imageOrientation, so draw the pixels upright first.
            image = autoreleasepool { image.uprightCompatImage() }
            coroutineContext.ensureActive()
            encoded = autoreleasepool {
                UIImagePNGRepresentation(image)?.toByteArrayOrNull(maxBytes.toLong())
            }
            if (encoded != null) {
                extension = "png"
            } else {
                image = autoreleasepool { image.flattenedCompatOnWhite() }
            }
        }
        var quality = 0.92
        for (iteration in 0 until 18) {
            if (encoded != null) break
            coroutineContext.ensureActive()
            val candidate = autoreleasepool {
                val candidateData = UIImageJPEGRepresentation(image, quality)
                    ?: error("画像を圧縮できませんでした")
                candidateData.toByteArrayOrNull(maxBytes.toLong())
            }
            if (candidate != null) {
                encoded = candidate
                break
            } else if (quality > 0.58) {
                quality = (quality - 0.08).coerceAtLeast(0.5)
            } else {
                // Drain the previous bitmap and UIKit's autoreleased context per step.
                image = autoreleasepool {
                    image.scaled(
                        max(1, image.size.useContents { (width * 0.82).roundToInt() }),
                        max(1, image.size.useContents { (height * 0.82).roundToInt() })
                    )
                }
                quality = 0.86
            }
        }
        val bytes = requireNotNull(encoded) { "上限以内に圧縮できませんでした" }
        coroutineContext.ensureActive()
        ImageData(bytes = bytes, fileName = compatPostSanitizedFileName(attachment.fileName, extension))
    }
}

internal actual fun compatPostImageAspectRatio(bytes: ByteArray): Float? {
    if (bytes.isEmpty() || bytes.size > COMPAT_IOS_ENCODED_IMAGE_MAX_BYTES) return null
    // Header only: no NSData copy of up to 32 MB and no UIImage.
    val size = readIosEncodedImageSize(bytes) ?: return null
    return (size.displayWidth / size.displayHeight).toFloat().takeIf { it.isFinite() && it > 0f }
}

internal actual suspend fun computeCompatImagePhashFromBytes(bytes: ByteArray): String? =
    withContext(AppDispatchers.io) {
        if (bytes.isEmpty() || bytes.size > COMPAT_IOS_PHASH_INPUT_MAX_BYTES) return@withContext null
        val sourceSize = readIosEncodedImageSize(bytes) ?: return@withContext null
        if (sourceSize.width * sourceSize.height > COMPAT_PHASH_MAX_SOURCE_PIXELS.toDouble()) {
            return@withContext null
        }
        // Let ImageIO decode a bounded thumbnail (like Android's inSampleSize)
        // instead of drawing up to 16 MP (64 MB) into the 32x32 context. The
        // raw pixel grid is kept, as UIImage.CGImage and BitmapFactory do.
        val cgImage = createIosImageThumbnail(bytes, COMPAT_PHASH_DECODE_MAX_SIDE, applyOrientation = false)
            ?: return@withContext null
        val size = CompatImagePhash.SIZE
        val raw = ByteArray(size * size * 4)
        try {
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
                        // 32-bit little-endian premultiplied-first = BGRA in
                        // memory. The former bitmapInfo 0 (24-bit RGB without
                        // alpha) is not a supported context format, so
                        // CGBitmapContextCreate returned null and every hash failed.
                        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little
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
        } finally {
            CGImageRelease(cgImage)
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
private const val COMPAT_PHASH_DECODE_MAX_SIDE = 512

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
            var unownedAudioLease: IosPlaybackAudioLease? = null
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
                // Playback category, not a PlayAndRecord left by voice input
                // (receiver output); released when the preview closes (G-4).
                withContext(NonCancellable + AppDispatchers.io) {
                    unownedAudioLease = acquireIosPlaybackAudioSession(mixWithOthers = false)
                }
                val audioLease = requireNotNull(unownedAudioLease)
                dispatch_async(dispatch_get_main_queue()) {
                    val presenter = currentIosPresentationController()
                    if (presenter == null) {
                        releaseCompatPreviewAudioLease(audioLease)
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
                                releaseCompatPreviewAudioLease(audioLease)
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
                                releaseCompatPreviewAudioLease(audioLease)
                                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                            }
                        }.apply { this.player = player }
                    }
                    presenter.presentViewController(controller, animated = true, completion = null)
                }
                // The presented controller (or the presenter-unavailable branch)
                // now owns deletion of the temporary file and the audio lease.
                unownedPreviewPath = null
                unownedAudioLease = null
            } catch (cancelled: CancellationException) {
                unownedAudioLease?.let(::releaseCompatPreviewAudioLease)
                val target = unownedPreviewPath
                if (target != null) withContext(NonCancellable + AppDispatchers.io) {
                    NSFileManager.defaultManager.removeItemAtPath(target, error = null)
                }
                throw cancelled
            } catch (error: Throwable) {
                unownedAudioLease?.let(::releaseCompatPreviewAudioLease)
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

/** Deactivating the audio session can block, so it is kept off the main thread. */
private fun releaseCompatPreviewAudioLease(lease: IosPlaybackAudioLease) {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { lease.close() }
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

private fun UIImage.hasCompatAlpha(): Boolean = when (CGImageGetAlphaInfo(CGImage)) {
    CGImageAlphaInfo.kCGImageAlphaNone,
    CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst,
    CGImageAlphaInfo.kCGImageAlphaNoneSkipLast -> false
    else -> CGImage != null
}

/** Redraws into an up-oriented image; UIKit applies imageOrientation while drawing. */
private fun UIImage.uprightCompatImage(): UIImage {
    if (imageOrientation == UIImageOrientation.UIImageOrientationUp) return this
    return size.useContents { scaled(max(1, width.roundToInt()), max(1, height.roundToInt())) }
}

/** Composites transparent pixels onto white so JPEG does not render them black. */
private fun UIImage.flattenedCompatOnWhite(): UIImage {
    val (width, height) = size.useContents { width to height }
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
    return try {
        UIColor.whiteColor.setFill()
        UIGraphicsGetCurrentContext()?.let { CGContextFillRect(it, CGRectMake(0.0, 0.0, width, height)) }
        drawInRect(CGRectMake(0.0, 0.0, width, height))
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
    private var generation = 0L
    private var latestText = ""
    private var recordingSession: Long? = null

    fun toggle(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (engine?.running == true) {
            val text = latestText
            stop()
            if (text.isNotBlank()) onResult(text)
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
        generation += 1
        engine?.inputNode?.removeTapOnBus(0u)
        engine?.stop()
        request?.endAudio()
        task?.cancel()
        task = null
        request = null
        engine = null
        latestText = ""
        recordingSession?.let { session ->
            // Back to playback (and let other apps resume) so a later preview
            // is not routed to the receiver in PlayAndRecord (G-4). Restoring the
            // category can block, so like the other releases it runs off the main
            // thread; the session token keeps it from ending a newer voice input (E4-3).
            recordingSession = null
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                iosAudioSessionCoordinator.endRecording(session)
            }
        }
    }

    private fun requestMicrophoneThenStart(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val requestedGeneration = generation
        AVAudioSession.sharedInstance().requestRecordPermission { allowed ->
            dispatch_async(dispatch_get_main_queue()) {
                if (generation == requestedGeneration) {
                    if (allowed) start(onResult, onError)
                    else onError("マイクを許可してください。許可しない場合はキーボード入力を利用できます")
                }
            }
        }
    }

    private fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!recognizer.isAvailable()) {
            onError("この端末では音声認識を利用できません。キーボード入力を利用してください")
            return
        }
        stop()
        recordingSession = iosAudioSessionCoordinator.beginRecordingSession() ?: run {
            onError("マイク入力を準備できませんでした。キーボード入力を利用してください")
            return
        }
        val activeGeneration = generation
        val nextRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            // Keep partial text for manual stop, but append it only once when
            // recognition finishes or the user stops recording.
            shouldReportPartialResults = true
        }
        val nextEngine = AVAudioEngine()
        val input = nextEngine.inputNode
        val format = input.outputFormatForBus(0u)
        if (format.sampleRate <= 0.0 || format.channelCount == 0u) {
            stop()
            onError("マイク入力を利用できません。キーボード入力を利用してください")
            return
        }
        input.installTapOnBus(0u, bufferSize = 1_024u, format = format) { buffer, _ ->
            buffer?.let(nextRequest::appendAudioPCMBuffer)
        }
        task = recognizer.recognitionTaskWithRequest(nextRequest) { result, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (generation == activeGeneration) {
                    result?.bestTranscription?.formattedString?.let { latestText = it }
                    if (result?.isFinal() == true) {
                        val text = latestText
                        stop()
                        if (text.isNotBlank()) onResult(text)
                    } else if (error != null) {
                        stop()
                        onError("音声認識に失敗しました: ${error.localizedDescription}")
                    }
                }
            }
        }
        if (!nextEngine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            task?.cancel()
            task = null
            stop()
            onError("マイク入力を開始できませんでした。キーボード入力を利用してください")
            return
        }
        engine = nextEngine
        request = nextRequest
    }
}

private const val IOS_SPEECH_NOT_DETERMINED = 0L
private const val IOS_SPEECH_AUTHORIZED = 3L
