package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.valoser.futacha.shared.ui.board.DesktopVideoFiles
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import java.io.File
import com.valoser.futacha.shared.util.ImageData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Path2D
import java.awt.geom.AffineTransform
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import kotlinx.coroutines.*
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import javax.imageio.ImageIO
import com.valoser.futacha.shared.desktop.MacNative
import com.valoser.futacha.shared.desktop.DesktopPlatform
import com.valoser.futacha.shared.desktop.status
import com.valoser.futacha.shared.desktop.checked
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Composable
internal actual fun rememberCompatSpeechRecognizer(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    if (DesktopPlatform.isWindows) return { onError("投稿欄を選択して Windows + H キーで音声入力できます。") }
    val scope = rememberCoroutineScope()
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onError)
    var session by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    session?.let { id ->
        LaunchedEffect(id) {
            try {
                var result = MacNative.call("speech", "id" to id).checked()
                while (result.status() in setOf("pending", "recording")) {
                    recording = result.status() == "recording"
                    transcript = result["text"]?.jsonPrimitive?.content.orEmpty()
                    delay(100)
                    result = MacNative.call("poll", "id" to id).checked()
                }
                ensureActive()
                if (session == id) result["text"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)?.let(resultCallback)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (session == id) errorCallback(failure.message ?: "音声入力に失敗しました") }
            finally {
                withContext(NonCancellable) { MacNative.call("cancel", "id" to id) }
                if (session == id) session = null
            }
        }
        FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = { session = null }, title = { Text("音声入力") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when { finishing -> "認識結果を確定しています…"; recording -> "話してください（最大60秒）"; else -> "音声認識とマイクの許可を確認しています…" })
                if (transcript.isNotBlank()) Text(transcript.takeLast(2000))
            }
        }, confirmButton = {
            TextButton(enabled = recording && !finishing, onClick = {
                finishing = true
                scope.launch {
                    try { MacNative.call("speechStop", "id" to id).checked() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { errorCallback(failure.message ?: "停止できませんでした"); session = null }
                }
            }) { Text("入力する") }
        }, dismissButton = { TextButton(onClick = { session = null }) { Text("キャンセル") } }) }
    }
    return { if (session == null) { recording = false; finishing = false; transcript = ""; session = UUID.randomUUID().toString() } }
}

internal actual fun compatPostNetworkInfo(): String = "回線情報: ${DesktopPlatform.displayName}"

actual fun initializeCompatPostPlatformContext(context: Any) = Unit

internal actual fun compatPostDeviceInfo(appVersion: String): String = "ふたちゃ $appVersion\n${DesktopPlatform.displayName} ${System.getProperty("os.version")}"

internal actual suspend fun compressCompatPostImage(
    attachment: ImageData,
    maxBytes: Int
): Result<ImageData> = withContext(Dispatchers.IO) { runSuspendCatchingPreservingCancellation {
    require(maxBytes > 0)
    // ImageIO ignores the EXIF orientation; read it like the image editor does so phone
    // photos are not posted sideways once the metadata is dropped (U-1, N4-2).
    val format = detectCompatPostImageFormat(attachment.bytes)
    val orientation = compatExifOrientationTransform(if (format == CompatPostImageFormat.GIF) 1 else readCompatPostEncodedOrigin(attachment.bytes))
    // GIF/PNG/WebP keep animation and transparency when only metadata has to go.
    compatPostLosslessSanitizedImage(attachment.bytes, format, orientation, maxBytes)?.let { sanitized ->
        return@runSuspendCatchingPreservingCancellation ImageData(sanitized, compatPostSanitizedFileName(attachment.fileName, requireNotNull(format.extension)))
    }
    val image = ImageIO.read(ByteArrayInputStream(attachment.bytes)) ?: run {
        // ImageIO cannot decode WebP; send it upright-as-stored rather than failing as before the orientation check.
        compatPostLosslessSanitizedImage(attachment.bytes, format, CompatImageOrientation(0, false), maxBytes)?.let { sanitized ->
            return@runSuspendCatchingPreservingCancellation ImageData(sanitized, compatPostSanitizedFileName(attachment.fileName, requireNotNull(format.extension)))
        }
        error("画像を読み込めません")
    }
    require(image.width.toLong() * image.height <= 32_000_000) { "画像が大きすぎます" }
    val swap = orientation.rotationDegrees % 180 != 0
    val uprightWidth = if (swap) image.height else image.width
    val uprightHeight = if (swap) image.width else image.height
    fun render(width: Int, height: Int, type: Int): BufferedImage {
        val out = BufferedImage(width, height, type)
        val g = out.createGraphics()
        try {
            if (type == BufferedImage.TYPE_INT_RGB) { g.color = Color.WHITE; g.fillRect(0, 0, width, height) }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.scale(width.toDouble() / uprightWidth, height.toDouble() / uprightHeight)
            g.transform(compatPostOrientationTransform(orientation, image.width.toDouble(), image.height.toDouble()))
            g.drawImage(image, 0, 0, null)
        } finally { g.dispose() }
        return out
    }
    if (image.colorModel.hasAlpha() && image.width <= 2048 && image.height <= 2048) {
        // Keep transparency as PNG when it fits; the JPEG path below flattens onto white.
        val upright = if (orientation.isIdentity) image else render(uprightWidth, uprightHeight, BufferedImage.TYPE_INT_ARGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(upright, "png", it) }.toByteArray()
        if (png.size <= maxBytes) return@runSuspendCatchingPreservingCancellation ImageData(png, compatPostSanitizedFileName(attachment.fileName, "png"))
    }
    var scale = minOf(1.0, 2048.0 / maxOf(image.width, image.height))
    for (attempt in 0 until 8) {
        ensureActive()
        val resized = render(maxOf(1, (uprightWidth * scale).toInt()), maxOf(1, (uprightHeight * scale).toInt()), BufferedImage.TYPE_INT_RGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(resized, "jpg", it) }.toByteArray()
        if (bytes.size <= maxBytes) return@runSuspendCatchingPreservingCancellation ImageData(bytes, compatPostSanitizedFileName(attachment.fileName, "jpg"))
        scale *= .7
    }
    error("指定サイズまで画像を圧縮できませんでした")
} }

/** EXIF Orientation (1-8) as Skia's codec reads it from the header; 1 when unknown or unreadable. */
internal fun readCompatPostEncodedOrigin(bytes: ByteArray): Int = runCatching {
    Data.makeFromBytes(bytes).use { data -> Codec.makeFromData(data).use { codec ->
        when (codec.encodedOrigin) {
            EncodedOrigin.TOP_RIGHT -> 2; EncodedOrigin.BOTTOM_RIGHT -> 3; EncodedOrigin.BOTTOM_LEFT -> 4
            EncodedOrigin.LEFT_TOP -> 5; EncodedOrigin.RIGHT_TOP -> 6; EncodedOrigin.RIGHT_BOTTOM -> 7
            EncodedOrigin.LEFT_BOTTOM -> 8; else -> 1
        }
    } }
}.getOrDefault(1)

/** Maps raw pixel coordinates ([width] x [height]) to upright ones: rotate clockwise, then mirror. */
internal fun compatPostOrientationTransform(orientation: CompatImageOrientation, width: Double, height: Double): AffineTransform {
    val rotation = when (orientation.rotationDegrees) {
        90 -> AffineTransform(0.0, 1.0, -1.0, 0.0, height, 0.0)
        180 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, width, height)
        270 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, width)
        else -> AffineTransform()
    }
    if (!orientation.mirrorHorizontally) return rotation
    val uprightWidth = if (orientation.rotationDegrees % 180 != 0) height else width
    return AffineTransform(-1.0, 0.0, 0.0, 1.0, uprightWidth, 0.0).apply { concatenate(rotation) }
}

internal actual fun compatPostImageAspectRatio(bytes: ByteArray): Float? = runCatching {
    if (bytes.isEmpty()) return@runCatching null
    val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
    if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height.toFloat() else null
}.getOrNull()

internal actual suspend fun computeCompatImagePhashFromBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
    runCatching {
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
        require(image.width.toLong() * image.height <= 16_000_000)
        val small = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = small.createGraphics()
        try { g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(image, 0, 0, 32, 32, null) } finally { g.dispose() }
        CompatImagePhash.computeFromArgbPixels(small.getRGB(0, 0, 32, 32, null, 0, 32))
    }.getOrNull()
}

internal actual suspend fun renderCompatDrawingPng(
    strokes: List<CompatDrawingStroke>,
    backgroundArgb: Int,
    widthPx: Int,
    heightPx: Int
): Result<ImageData> = withContext(Dispatchers.IO) { runSuspendCatchingPreservingCancellation {
    validateCompatDrawingRender(strokes, widthPx, heightPx)
    val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.color = Color(backgroundArgb, true); g.fillRect(0, 0, widthPx, heightPx)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        strokes.forEach { stroke ->
            ensureActive()
            val first = stroke.points.firstOrNull() ?: return@forEach
            g.color = Color(stroke.colorArgb, true)
            g.stroke = BasicStroke(stroke.widthPx.coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val path = Path2D.Float(); path.moveTo(first.x.toDouble(), first.y.toDouble())
            if (stroke.points.size == 1) path.lineTo(first.x.toDouble() + .1, first.y.toDouble() + .1)
            else stroke.points.drop(1).forEach { path.lineTo(it.x.toDouble(), it.y.toDouble()) }
            g.draw(path)
        }
    } finally { g.dispose() }
    ImageData(ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray(), "tegaki-${System.currentTimeMillis()}.png")
} }

@Composable
internal actual fun CompatDrawingLandscapeEffect() = Unit

@Composable
internal actual fun rememberCompatVideoAttachmentPreviewLauncher(
    onError: (String) -> Unit
): (ImageData) -> Unit {
    var attachment by remember { mutableStateOf<ImageData?>(null) }
    val errorCallback by rememberUpdatedState(onError)
    attachment?.let { selected -> key(selected) {
        var file by remember { mutableStateOf<File?>(null) }
        var loading by remember { mutableStateOf(true) }
        LaunchedEffect(selected) {
            var owned: File? = null
            try {
                withContext(Dispatchers.IO) {
                    val suffix = selected.fileName.substringAfterLast('.').lowercase().takeIf { it in setOf("mp4", "webm", "mov") } ?: "mp4"
                    owned = File.createTempFile("futacha-attachment-", ".$suffix")
                    owned!!.writeBytes(selected.bytes)
                }
                file = owned
                awaitCancellation()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errorCallback(failure.message ?: "動画を読み込めません"); attachment = null }
            // The player below may still be releasing VLC; delete once it let go (Windows cannot delete open files).
            finally { withContext(NonCancellable + Dispatchers.IO) { owned?.let(DesktopVideoFiles::delete) } }
        }
        FutachaAppLockAwareWindow { Dialog(onDismissRequest = { attachment = null }) {
            Surface {
                Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(16.dp)) {
                    Text(selected.fileName)
                    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        file?.let { video -> PlatformVideoPlayer(video.absolutePath, Modifier.fillMaxSize(),
                            onStateChanged = { loading = it == VideoPlayerState.Buffering },
                            onPlaybackError = { errorCallback(it.message ?: "動画を再生できません"); attachment = null }) }
                        if (loading) CircularProgressIndicator()
                    }
                    TextButton(onClick = { attachment = null }, modifier = Modifier.align(Alignment.End)) { Text("閉じる") }
                }
            }
        } }
    } }
    return { attachment = it }
}

@Composable
internal actual fun CompatPostImePolicyEffect() = Unit
