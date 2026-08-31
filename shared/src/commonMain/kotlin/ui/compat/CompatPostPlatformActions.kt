package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.compat.stableCompatHash
import com.valoser.futacha.shared.media.FUTABA_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_VIDEO_EXTENSIONS
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.util.sanitizeForShiftJis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal expect fun rememberCompatSpeechRecognizer(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit

internal expect fun compatPostNetworkInfo(): String

/** Supplies Android-only carrier context without exposing platform APIs to common code. */
expect fun initializeCompatPostPlatformContext(context: Any)

private val COMPAT_NETWORK_INFO_ENDPOINTS = listOf(
    "https://ipinfo.io/hostname",
    "https://api.ipify.org"
)
private val COMPAT_NETWORK_IDENTIFIER_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9.:-]{0,253}")
private const val COMPAT_NETWORK_INFO_RESPONSE_MAX_BYTES = 1024

/**
 * The reference app asks its cache service for the public reverse-DNS name.
 * Do the same on demand instead of blocking the post screen; if the service
 * is unavailable, retain the platform-specific fallback text.
 */
internal suspend fun fetchCompatPostNetworkInfo(
    httpClient: HttpClient?,
    userAgent: String
): String {
    val hostname = COMPAT_NETWORK_INFO_ENDPOINTS.firstNotNullOfOrNull { endpoint ->
        runSuspendCatchingPreservingCancellation {
            val response = httpClient?.get(endpoint) {
                headers { append(HttpHeaders.UserAgent, userAgent) }
            }
            response?.let {
                readBoundedHttpResponseText(it, COMPAT_NETWORK_INFO_RESPONSE_MAX_BYTES)
            }?.trim()
                ?.takeIf { it.length <= 255 && COMPAT_NETWORK_IDENTIFIER_REGEX.matches(it) }
        }.getOrNull()
    }
    val platformInfo = compatPostNetworkInfo().removePrefix("回線情報: ").trim()
    return when {
        hostname != null && platformInfo.isNotBlank() && platformInfo != "取得できません" &&
            !platformInfo.equals(hostname, ignoreCase = true) ->
            "回線情報: $platformInfo ($hostname)"
        hostname != null -> "回線情報: $hostname"
        else -> "回線情報: $platformInfo"
    }
}

internal expect fun compatPostDeviceInfo(appVersion: String): String

internal fun formatCompatPostDeviceInfo(
    appVersion: String,
    brand: String,
    model: String,
    platformVersion: String
): String = buildString {
    // Both reference APKs insert this application identity into the post.
    // It belongs only to the compatibility profile, so keep it out of the
    // normal Futacha UI without renaming it here.
    append("ふたば＠アプリ としあき(仮)")
    appVersion.trim().takeIf(String::isNotEmpty)?.let { append(' ').append(it) }
    val platform = listOf(brand.trim(), model.trim(), platformVersion.trim())
        .filter(String::isNotEmpty)
        .joinToString("/")
    if (platform.isNotEmpty()) append(' ').append(platform)
}

internal fun compatPostAttachmentToolbarLabel(hasAttachment: Boolean): String =
    if (hasAttachment) "添付削除" else "添付画像"

internal expect suspend fun compressCompatPostImage(
    attachment: ImageData,
    maxBytes: Int
): Result<ImageData>

/** Returns a safe width/height ratio for the post-form preview, if decodable. */
internal expect fun compatPostImageAspectRatio(bytes: ByteArray): Float?

/** Decodes the image using the platform decoder, then applies the reference pHash algorithm. */
internal expect suspend fun computeCompatImagePhashFromBytes(bytes: ByteArray): String?

internal data class CompatDrawingPoint(val x: Float, val y: Float)
internal data class CompatDrawingStroke(
    val colorArgb: Int,
    val widthPx: Float,
    val points: List<CompatDrawingPoint>
)

internal const val COMPAT_DRAWING_OUTPUT_WIDTH_PX = 344
internal const val COMPAT_DRAWING_OUTPUT_HEIGHT_PX = 135
internal const val COMPAT_DRAWING_BRUSH_PIXEL_SCALE = 3f
internal const val COMPAT_DRAWING_MAIN_COLOR_ARGB: Long = 0xFF800000
internal const val COMPAT_DRAWING_MAIN_SIZE = 6
internal const val COMPAT_DRAWING_SUB_COLOR_ARGB: Long = 0xFFF0E0D6
internal const val COMPAT_DRAWING_SUB_SIZE = 24
internal const val COMPAT_DRAWING_MIN_SIZE = 1
internal const val COMPAT_DRAWING_MAX_SIZE = 25

internal data class CompatDrawingBrush(
    val colorArgb: Long,
    val logicalSize: Int
) {
    init {
        require(logicalSize in COMPAT_DRAWING_MIN_SIZE..COMPAT_DRAWING_MAX_SIZE)
    }

    val widthPx: Float get() = logicalSize * COMPAT_DRAWING_BRUSH_PIXEL_SCALE
}

internal val compatDrawingReferencePresets: List<Long> = listOf(
    0xFF000000, 0xFF808080, 0xFF800000, 0xFFF0E0D6,
    0xFFFFFFFF, 0xFFEC3323, 0xFFF8991D, 0xFFF6EB39,
    0xFF64AD3B, 0xFF0791CC, 0xFF7C3692, 0xFFF19EC2
)

internal fun scaleCompatDrawingStrokesForReferencePng(
    strokes: List<CompatDrawingStroke>,
    sourceWidthPx: Int,
    sourceHeightPx: Int
): List<CompatDrawingStroke> {
    require(sourceWidthPx > 0 && sourceHeightPx > 0) { "キャンバスを初期化できませんでした" }
    val scaleX = COMPAT_DRAWING_OUTPUT_WIDTH_PX.toFloat() / sourceWidthPx
    val scaleY = COMPAT_DRAWING_OUTPUT_HEIGHT_PX.toFloat() / sourceHeightPx
    val widthScale = (scaleX + scaleY) / 2f
    return strokes.map { stroke ->
        stroke.copy(
            widthPx = (stroke.widthPx * widthScale).coerceAtLeast(1f),
            points = stroke.points.map { point ->
                CompatDrawingPoint(point.x * scaleX, point.y * scaleY)
            }
        )
    }
}

internal fun compatDrawingFileName(
    timestampEpochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val local = Instant.fromEpochMilliseconds(timestampEpochMillis)
        .toLocalDateTime(timeZone)
    return buildString {
        append("drawing_")
        append(local.year.toString().padStart(4, '0'))
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append(local.day.toString().padStart(2, '0'))
        append('_')
        append(local.hour.toString().padStart(2, '0'))
        append(local.minute.toString().padStart(2, '0'))
        append(local.second.toString().padStart(2, '0'))
        append(".png")
    }
}

private const val COMPAT_DRAWING_MAX_PIXELS = 16_000_000L
private const val COMPAT_DRAWING_MAX_STROKES = 100_000
private const val COMPAT_DRAWING_MAX_POINTS = 2_000_000L

internal fun validateCompatDrawingRender(
    strokes: List<CompatDrawingStroke>,
    widthPx: Int,
    heightPx: Int
) {
    require(widthPx > 0 && heightPx > 0) { "キャンバスを初期化できませんでした" }
    require(widthPx.toLong() * heightPx.toLong() <= COMPAT_DRAWING_MAX_PIXELS) {
        "キャンバスサイズが大きすぎます"
    }
    require(strokes.size <= COMPAT_DRAWING_MAX_STROKES) { "描画データが大きすぎます" }
    var pointCount = 0L
    for (stroke in strokes) {
        pointCount += stroke.points.size.toLong()
        require(pointCount <= COMPAT_DRAWING_MAX_POINTS) { "描画データが大きすぎます" }
    }
}

internal expect suspend fun renderCompatDrawingPng(
    strokes: List<CompatDrawingStroke>,
    backgroundArgb: Int,
    widthPx: Int,
    heightPx: Int
): Result<ImageData>

@Composable
internal expect fun CompatDrawingLandscapeEffect()

@Composable
internal expect fun rememberCompatVideoAttachmentPreviewLauncher(
    onError: (String) -> Unit
): (ImageData) -> Unit

@Composable
internal expect fun CompatPostImePolicyEffect()


internal fun normalizeCompatSpeechResult(raw: String): String = raw
    .replace("開業", "\n")
    .replace("改行", "\n")
    .replace("、", "")
    .replace("。", "\n")
    .trimEnd()

internal fun appendCompatPostText(current: String, added: String): String {
    val normalized = added.trimEnd()
    if (normalized.isEmpty()) return current
    return if (current.isEmpty() || current.endsWith('\n')) current + normalized else "$current\n$normalized"
}

internal data class CompatUpsUploadInitialFields(
    val comment: String,
    val deleteKey: String
)

/** Mirrors PostUpsUploadByUriDialogFragment in old.apk and 1.apk. */
internal fun compatUpsUploadInitialFields(storedDeleteKey: String): CompatUpsUploadInitialFields =
    CompatUpsUploadInitialFields(comment = "", deleteKey = storedDeleteKey)

internal fun compatPostShiftJisByteCount(comment: String): Int {
    val crlf = comment
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "\r\n")
    // The multipart sender escapes characters unavailable in Shift_JIS (including emoji)
    // as numeric character references before encoding. Count that actual wire payload so
    // the legacy 1000-byte warning does not under-report an emoji-heavy post.
    val wireText = sanitizeForShiftJis(crlf).sanitizedText
    return TextEncoding.encodeToShiftJis(wireText).size
}

internal fun compatPostLineCount(comment: String, emptyIsOneLine: Boolean = true): Int {
    if (comment.isEmpty()) return if (emptyIsOneLine) 1 else 0
    return comment.replace("\r\n", "\n").replace('\r', '\n').count { it == '\n' } + 1
}

internal enum class CompatPostAttachmentKind { IMAGE, VIDEO, UNSUPPORTED }

internal sealed interface CompatPostAttachmentDecision {
    data object Accept : CompatPostAttachmentDecision
    data object EmptyPayload : CompatPostAttachmentDecision
    data object MissingFileName : CompatPostAttachmentDecision
    data object UnsupportedExtension : CompatPostAttachmentDecision
    data object OversizedVideo : CompatPostAttachmentDecision
    data object AskImageCompression : CompatPostAttachmentDecision
}

internal fun compatPostAttachmentDecisionMessage(
    decision: CompatPostAttachmentDecision,
    fileName: String,
    maxBytes: Int
): String? = when (decision) {
    CompatPostAttachmentDecision.Accept -> null
    CompatPostAttachmentDecision.EmptyPayload -> "ファイルサイズが0です"
    CompatPostAttachmentDecision.MissingFileName -> "ファイル名が不明です"
    CompatPostAttachmentDecision.UnsupportedExtension ->
        "対応しないフォーマットです\n${compatPostAttachmentExtension(fileName)}"
    CompatPostAttachmentDecision.OversizedVideo,
    CompatPostAttachmentDecision.AskImageCompression ->
        "ファイルサイズ超過です\n${compatPostAttachmentLimitLabel(maxBytes)}まで"
}

private fun compatPostAttachmentLimitLabel(maxBytes: Int): String = when (maxBytes) {
    8_192_000 -> "8MB"
    3_072_000 -> "3MB"
    else -> "${(maxBytes.coerceAtLeast(1) + 1_023_999) / 1_024_000}MB"
}

private val COMPAT_POST_IMAGE_EXTENSIONS = FUTABA_IMAGE_EXTENSIONS - "jpe"
private val COMPAT_POST_VIDEO_EXTENSIONS = FUTABA_VIDEO_EXTENSIONS

internal fun compatPostAttachmentExtension(fileName: String): String =
    fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

internal fun compatPostAttachmentKind(fileName: String): CompatPostAttachmentKind {
    val extension = compatPostAttachmentExtension(fileName)
    return when (extension) {
        in COMPAT_POST_IMAGE_EXTENSIONS -> CompatPostAttachmentKind.IMAGE
        in COMPAT_POST_VIDEO_EXTENSIONS -> CompatPostAttachmentKind.VIDEO
        else -> CompatPostAttachmentKind.UNSUPPORTED
    }
}

internal fun compatPostAttachmentLimitBytes(boardUrl: String): Int =
    defaultBoardPostingCapabilities(boardUrl).maxFileSizeBytes.toInt()

/** Decode safety ceiling. The board-specific 3MB/8MB posting limit is applied after this read. */
internal const val COMPAT_POST_PICKER_MAX_BYTES = 32_000_000L

internal fun decideCompatPostAttachment(
    attachment: ImageData,
    maxBytes: Int,
    supportedExtensions: Set<String> = COMPAT_POST_IMAGE_EXTENSIONS + COMPAT_POST_VIDEO_EXTENSIONS
): CompatPostAttachmentDecision = when {
    attachment.bytes.isEmpty() -> CompatPostAttachmentDecision.EmptyPayload
    attachment.fileName.isBlank() -> CompatPostAttachmentDecision.MissingFileName
    compatPostAttachmentExtension(attachment.fileName) !in supportedExtensions.map(String::lowercase) ->
        CompatPostAttachmentDecision.UnsupportedExtension
    compatPostAttachmentKind(attachment.fileName) == CompatPostAttachmentKind.UNSUPPORTED ->
        CompatPostAttachmentDecision.UnsupportedExtension
    attachment.bytes.size <= maxBytes -> CompatPostAttachmentDecision.Accept
    compatPostAttachmentKind(attachment.fileName) == CompatPostAttachmentKind.VIDEO ->
        CompatPostAttachmentDecision.OversizedVideo
    else -> CompatPostAttachmentDecision.AskImageCompression
}

private const val COMPAT_POST_ATTACHMENT_DIRECTORY = "private/compat_post_attachments"
private val COMPAT_POST_ATTACHMENT_FILE_NAME_REGEX = Regex("[^A-Za-z0-9._-]")
private val COMPAT_POST_ATTACHMENT_EXTENSION_REGEX = Regex("[^A-Za-z0-9]")
private val COMPAT_POST_ATTACHMENT_CONTAINER_REGEX = Regex("[0-9a-f]{16}(?:-[0-9a-f]{1,16})?")
private val COMPAT_POST_ATTACHMENT_DOT_RUN_REGEX = Regex("\\.{2,}")

internal fun compatPostAttachmentLocator(
    tabKey: String,
    fileName: String,
    payloadHash: String = stableCompatHash(fileName)
): String {
    val sourceFileName = fileName
        .replace('\\', '/')
        .substringAfterLast('/')
    val extension = sourceFileName
        .substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { sourceFileName.lastIndexOf('.') > 0 }
        ?.replace(COMPAT_POST_ATTACHMENT_EXTENSION_REGEX, "")
        ?.take(12)
        .orEmpty()
    val sourceStem = if (extension.isNotEmpty()) sourceFileName.substringBeforeLast('.') else sourceFileName
    val sanitizedStem = sourceStem
        .replace(COMPAT_POST_ATTACHMENT_FILE_NAME_REGEX, "_")
        .replace(COMPAT_POST_ATTACHMENT_DOT_RUN_REGEX, "_")
        .trim('.', '_', '-')
        .ifBlank { "attachment" }
    val suffix = extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
    val hashPrefix = payloadHash.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
        .take(16)
        .ifBlank { stableCompatHash(fileName) }
    val sanitizedFileName = sanitizedStem.take(96 - suffix.length) + suffix
    val container = "${stableCompatHash(tabKey)}-$hashPrefix"
    return "$COMPAT_POST_ATTACHMENT_DIRECTORY/$container/$sanitizedFileName"
}

fun compatPostAttachmentContainer(locator: String): String? {
    val normalized = locator.replace('\\', '/')
    val parts = normalized.split('/')
    if (
        parts.size != 4 ||
        parts[0] != "private" ||
        parts[1] != "compat_post_attachments" ||
        !COMPAT_POST_ATTACHMENT_CONTAINER_REGEX.matches(parts[2]) ||
        parts[3].isBlank() ||
        parts[3] == "." ||
        parts[3] == ".."
    ) return null
    return parts.take(3).joinToString("/")
}

internal fun isCompatPostAttachmentLocator(locator: String): Boolean =
    compatPostAttachmentContainer(locator) != null

private fun compatPostAttachmentPayloadHash(bytes: ByteArray): String {
    var hash = 0xcbf29ce484222325UL
    bytes.forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
    }
    return hash.toString(16).padStart(16, '0')
}

internal suspend fun persistCompatPostAttachment(
    fileSystem: FileSystem,
    tabKey: String,
    attachment: ImageData
): Result<String> {
    val locator = compatPostAttachmentLocator(
        tabKey = tabKey,
        fileName = attachment.fileName,
        payloadHash = stableCompatHash(
            "${Clock.System.now().toEpochMilliseconds()}:${compatPostAttachmentPayloadHash(attachment.bytes)}"
        )
    )
    return fileSystem.writeBytes(locator, attachment.bytes).map { locator }
}

/**
 * The reference drawing activity writes an additional PNG copy to the user
 * selected tree/path.  The draft still keeps the private attachment locator;
 * this copy is the visible file users expect from the "手書きファイルの保存先"
 * preference.  A failed external write must not discard the draft attachment.
 */
internal suspend fun persistCompatDrawingCopy(
    fileSystem: FileSystem,
    location: SaveLocation?,
    drawing: ImageData,
    timestampEpochMillis: Long
): Result<String>? {
    if (location == null) return null
    val fileName = compatDrawingFileName(timestampEpochMillis)
    return fileSystem.writeBytes(location, fileName, drawing.bytes).map { fileName }
}

internal suspend fun loadCompatPostAttachment(
    fileSystem: FileSystem,
    locator: String
): Result<ImageData> {
    if (!isCompatPostAttachmentLocator(locator)) {
        return Result.failure(IllegalArgumentException("Invalid compatibility attachment locator"))
    }
    val size = try {
        fileSystem.getFileSize(locator)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        return Result.failure(error)
    }
    if (size !in 0..COMPAT_POST_PICKER_MAX_BYTES) {
        return Result.failure(IllegalArgumentException("Compatibility attachment is too large"))
    }
    return fileSystem.readBytes(locator).mapCatching { bytes ->
        require(bytes.size.toLong() <= COMPAT_POST_PICKER_MAX_BYTES) {
            "Compatibility attachment is too large"
        }
        ImageData(bytes = bytes, fileName = locator.substringAfterLast('/'))
    }
}

internal suspend fun deleteCompatPostAttachment(
    fileSystem: FileSystem,
    locator: String,
    deleteContainer: Boolean = false
): Result<Unit> {
    if (!isCompatPostAttachmentLocator(locator)) {
        return Result.failure(IllegalArgumentException("Invalid compatibility attachment locator"))
    }
    return if (deleteContainer) {
        fileSystem.deleteRecursively(checkNotNull(compatPostAttachmentContainer(locator)))
    } else {
        fileSystem.delete(locator)
    }
}

/** Removes payloads whose draft/closed-tab ownership was permanently discarded. */
suspend fun cleanupCompatPostAttachmentLocators(
    fileSystem: FileSystem,
    candidateLocators: Set<String>,
    retainedLocators: Set<String>
): Result<Int> = runSuspendCatchingPreservingCancellation {
    val retained = retainedLocators.filterTo(mutableSetOf(), ::isCompatPostAttachmentLocator)
    val candidatesByContainer = candidateLocators
        .filter(::isCompatPostAttachmentLocator)
        .filterNot(retained::contains)
        .groupBy { checkNotNull(compatPostAttachmentContainer(it)) }
    var removed = 0
    candidatesByContainer.forEach { (container, candidates) ->
        val containerIsRetained = retained.any { compatPostAttachmentContainer(it) == container }
        if (!containerIsRetained) {
            fileSystem.deleteRecursively(container).getOrThrow()
            removed += candidates.size
        } else {
            candidates.forEach { locator ->
                fileSystem.delete(locator).getOrThrow()
                removed += 1
            }
        }
    }
    removed
}
