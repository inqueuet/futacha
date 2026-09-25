package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
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
        AlertDialog(onDismissRequest = { session = null }, title = { Text("音声入力") }, text = {
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
        }, dismissButton = { TextButton(onClick = { session = null }) { Text("キャンセル") } })
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
    val image = requireNotNull(ImageIO.read(ByteArrayInputStream(attachment.bytes))) { "画像を読み込めません" }
    require(image.width.toLong() * image.height <= 32_000_000) { "画像が大きすぎます" }
    var scale = minOf(1.0, 2048.0 / maxOf(image.width, image.height))
    for (attempt in 0 until 8) {
        ensureActive()
        val resized = BufferedImage(maxOf(1, (image.width * scale).toInt()), maxOf(1, (image.height * scale).toInt()), BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        try { g.color = Color.WHITE; g.fillRect(0, 0, resized.width, resized.height); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(image, 0, 0, resized.width, resized.height, null) } finally { g.dispose() }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(resized, "jpg", it) }.toByteArray()
        if (bytes.size <= maxBytes) return@runSuspendCatchingPreservingCancellation ImageData(bytes, attachment.fileName.substringBeforeLast('.') + ".jpg")
        scale *= .7
    }
    error("指定サイズまで画像を圧縮できませんでした")
} }

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
        Dialog(onDismissRequest = { attachment = null }) {
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
        }
    } }
    return { attachment = it }
}

@Composable
internal actual fun CompatPostImePolicyEffect() = Unit
