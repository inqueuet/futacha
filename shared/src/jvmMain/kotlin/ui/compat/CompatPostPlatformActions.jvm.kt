package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.util.ImageData
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@Composable
internal actual fun rememberCompatSpeechRecognizer(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit = { onError("音声入力を利用できません") }

internal actual fun compatPostNetworkInfo(): String = "回線情報: localhost"

actual fun initializeCompatPostPlatformContext(context: Any) = Unit

internal actual fun compatPostDeviceInfo(appVersion: String): String = "ふたちゃ $appVersion\nJVM"

internal actual suspend fun compressCompatPostImage(
    attachment: ImageData,
    maxBytes: Int
): Result<ImageData> = Result.failure(UnsupportedOperationException("画像圧縮を利用できません"))

internal actual fun compatPostImageAspectRatio(bytes: ByteArray): Float? = runCatching {
    if (bytes.isEmpty()) return@runCatching null
    val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
    if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height.toFloat() else null
}.getOrNull()

internal actual suspend fun computeCompatImagePhashFromBytes(bytes: ByteArray): String? = null

internal actual suspend fun renderCompatDrawingPng(
    strokes: List<CompatDrawingStroke>,
    backgroundArgb: Int,
    widthPx: Int,
    heightPx: Int
): Result<ImageData> = Result.failure(UnsupportedOperationException("手書きを利用できません"))

@Composable
internal actual fun CompatDrawingLandscapeEffect() = Unit

@Composable
internal actual fun rememberCompatVideoAttachmentPreviewLauncher(
    onError: (String) -> Unit
): (ImageData) -> Unit = { onError("動画プレビューを利用できません") }

@Composable
internal actual fun CompatPostImePolicyEffect() = Unit
