package com.valoser.futacha.shared.ui.compat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private val compatAndroidPreviewUnsafeFileNameRegex = Regex("[^A-Za-z0-9._-]")
private const val COMPAT_ANDROID_PREVIEW_MAX_FILES = 16
private const val COMPAT_ANDROID_DRAWING_MAX_PNG_BYTES = 32 * 1024 * 1024

private class CappedByteArrayOutputStream(private val maxBytes: Int) :
    ByteArrayOutputStream(minOf(maxBytes.coerceAtLeast(1), 64 * 1024)) {
    var exceeded: Boolean = false
        private set

    override fun write(value: Int) {
        if (exceeded) return
        if (count >= maxBytes) {
            exceeded = true
            return
        }
        super.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (exceeded) return
        if (length < 0 || offset < 0 || offset > buffer.size - length) {
            throw IndexOutOfBoundsException()
        }
        if (length > maxBytes - count) {
            exceeded = true
            return
        }
        super.write(buffer, offset, length)
    }

    fun toByteArrayOrNull(): ByteArray? = if (exceeded) null else toByteArray()
}

@Composable
internal actual fun rememberCompatSpeechRecognizer(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val launcher = rememberExperienceProfileActivityResultLauncher(
        ActivityResultContracts.StartActivityForResult()
    ) { result, _ ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberExperienceProfileActivityResultLauncher
        val candidate = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (candidate.isNotBlank()) currentOnResult(candidate)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "コメントを入力")
        }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            currentOnError("音声入力を利用できません。Googleアプリをインストールしてください")
            val market = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.googlequicksearchbox")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(market) }.recoverCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

private var compatPostApplicationContext: Context? = null

actual fun initializeCompatPostPlatformContext(context: Any) {
    compatPostApplicationContext = (context as? Context)?.applicationContext
}

internal actual fun compatPostNetworkInfo(): String = runCatching {
    val context = compatPostApplicationContext
    val connectivity = context
        ?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val capabilities = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)
    val transport = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "モバイル"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "有線LAN"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "Bluetooth"
        capabilities != null -> "接続中"
        else -> null
    }
    val carrier = (context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
        ?.networkOperatorName
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
    // InetAddress.getLocalHost() is not a reliable network probe on Android:
    // many devices return an empty/loopback hostname even while cellular data
    // is fully usable.  The transport above is available without exposing an
    // IP address and is enough for the legacy post-form information row.
    val value = listOfNotNull(transport, carrier).distinct().joinToString(" / ")
    "回線情報: ${value.ifBlank { "取得できません" }}"
}.getOrElse { "回線情報: 取得できません" }

internal actual fun compatPostDeviceInfo(appVersion: String): String = formatCompatPostDeviceInfo(
    appVersion = appVersion,
    brand = Build.BRAND,
    model = Build.MODEL,
    platformVersion = Build.VERSION.RELEASE
)

internal actual suspend fun compressCompatPostImage(
    attachment: ImageData,
    maxBytes: Int
): Result<ImageData> = withContext(Dispatchers.Default) {
    runSuspendCatchingPreservingCancellation {
        coroutineContext.ensureActive()
        require(maxBytes > 0) { "Invalid attachment size limit" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "画像を読み込めませんでした" }
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) > 8_000_000L) {
            sampleSize *= 2
        }
        var bitmap = requireNotNull(
            BitmapFactory.decodeByteArray(
                attachment.bytes,
                0,
                attachment.bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        ) { "画像を読み込めませんでした" }
        val bytes = try {
            var encoded: ByteArray? = null
            var quality = 92
            for (iteration in 0 until 18) {
                coroutineContext.ensureActive()
                val output = CappedByteArrayOutputStream(maxBytes)
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "画像を圧縮できませんでした" }
                val candidate = output.toByteArrayOrNull()
                if (candidate != null) {
                    encoded = candidate
                    break
                }
                if (quality > 58) {
                    quality -= 8
                } else {
                    val nextWidth = (bitmap.width * 0.82f).toInt().coerceAtLeast(1)
                    val nextHeight = (bitmap.height * 0.82f).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(bitmap, nextWidth, nextHeight, true)
                    if (scaled !== bitmap) bitmap.recycle()
                    bitmap = scaled
                    quality = 86
                }
            }
            requireNotNull(encoded) { "上限以内に圧縮できませんでした" }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        val stem = attachment.fileName.substringBeforeLast('.', attachment.fileName).ifBlank { "attachment" }
        coroutineContext.ensureActive()
        ImageData(bytes = bytes, fileName = "$stem.jpg")
    }
}

internal actual fun compatPostImageAspectRatio(bytes: ByteArray): Float? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        (bounds.outWidth.toFloat() / bounds.outHeight.toFloat()).takeIf { it.isFinite() && it > 0f }
    } else {
        null
    }
}

internal actual suspend fun computeCompatImagePhashFromBytes(bytes: ByteArray): String? =
    withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sampleSize = 1
        while (
            (bounds.outWidth.toLong() / sampleSize) *
                (bounds.outHeight.toLong() / sampleSize) > COMPAT_PHASH_MAX_DECODE_PIXELS
        ) {
            sampleSize = (sampleSize * 2).coerceAtMost(1 shl 15)
            if (sampleSize == 1 shl 15) break
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@withContext null
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, CompatImagePhash.SIZE, CompatImagePhash.SIZE, true)
            try {
                val pixels = IntArray(CompatImagePhash.SIZE * CompatImagePhash.SIZE)
                scaled.getPixels(
                    pixels,
                    0,
                    CompatImagePhash.SIZE,
                    0,
                    0,
                    CompatImagePhash.SIZE,
                    CompatImagePhash.SIZE
                )
                CompatImagePhash.computeFromArgbPixels(pixels)
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

private const val COMPAT_PHASH_MAX_DECODE_PIXELS = 4_000_000L

internal actual suspend fun renderCompatDrawingPng(
    strokes: List<CompatDrawingStroke>,
    backgroundArgb: Int,
    widthPx: Int,
    heightPx: Int
): Result<ImageData> = withContext(Dispatchers.Default) {
    runCatching {
        validateCompatDrawingRender(strokes, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = AndroidCanvas(bitmap)
            canvas.drawColor(backgroundArgb)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            strokes.forEach { stroke ->
                paint.color = stroke.colorArgb
                paint.strokeWidth = stroke.widthPx.coerceAtLeast(1f)
                if (stroke.points.size == 1) {
                    val point = stroke.points.single()
                    canvas.drawPoint(point.x, point.y, paint)
                } else {
                    for (index in 1 until stroke.points.size) {
                        val from = stroke.points[index - 1]
                        val to = stroke.points[index]
                        canvas.drawLine(from.x, from.y, to.x, to.y, paint)
                    }
                }
            }
            val output = CappedByteArrayOutputStream(COMPAT_ANDROID_DRAWING_MAX_PNG_BYTES)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "手書き画像を保存できませんでした" }
            val encoded = output.toByteArrayOrNull() ?: error("手書き画像が大きすぎます")
            ImageData(
                bytes = encoded,
                fileName = "tegaki-${System.currentTimeMillis()}.png"
            )
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}

@Composable
internal actual fun CompatDrawingLandscapeEffect() {
    val context = LocalContext.current
    val activity = context.findCompatPostActivity() ?: return
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previous }
    }
}

@Composable
internal actual fun rememberCompatVideoAttachmentPreviewLauncher(
    onError: (String) -> Unit
): (ImageData) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    return { attachment ->
        scope.launch {
            runSuspendCatchingPreservingCancellation {
                val safeName = attachment.fileName.replace(compatAndroidPreviewUnsafeFileNameRegex, "_")
                    .ifBlank { "video.bin" }
                    .take(120)
                val target = withContext(Dispatchers.IO) {
                    val previewDirectory = File(context.cacheDir, "compat_post_preview")
                    if (!previewDirectory.exists() && !previewDirectory.mkdirs() && !previewDirectory.isDirectory) {
                        error("動画の一時保存先を作成できませんでした")
                    }
                    File(previewDirectory, "${attachment.bytes.contentHashCode()}-$safeName")
                        .also {
                            it.writeBytes(attachment.bytes)
                            pruneCompatAndroidPreviewFiles(previewDirectory, it)
                        }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
                val mime = when (target.extension.lowercase()) {
                    "webm" -> "video/webm"
                    "mp4" -> "video/mp4"
                    "mov" -> "video/quicktime"
                    else -> "video/*"
                }
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.onFailure { currentOnError("動画を開けませんでした: ${it.message.orEmpty()}") }
        }
    }
}

private fun pruneCompatAndroidPreviewFiles(directory: File, activeFile: File) {
    directory.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it != activeFile }
        ?.sortedByDescending(File::lastModified)
        ?.drop(COMPAT_ANDROID_PREVIEW_MAX_FILES - 1)
        ?.forEach { stale -> stale.delete() }
}

@Composable
internal actual fun CompatPostImePolicyEffect() {
    val context = LocalContext.current
    val activity = context.findCompatPostActivity() ?: return
    DisposableEffect(activity) {
        val originalMode = activity.window.attributes.softInputMode
        val resizedMode = (originalMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        activity.window.setSoftInputMode(resizedMode)
        onDispose {
            // Window#setSoftInputMode(0) clears its explicit-mode marker but
            // leaves LayoutParams.softInputMode unchanged. Most hosts enter
            // with UNSPECIFIED (0), so using that API to restore kept
            // ADJUST_RESIZE active after the post screen had closed.
            val restoredAttributes = activity.window.attributes
            restoredAttributes.softInputMode = originalMode
            activity.window.attributes = restoredAttributes
        }
    }
}

private tailrec fun Context.findCompatPostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findCompatPostActivity()
    else -> null
}
