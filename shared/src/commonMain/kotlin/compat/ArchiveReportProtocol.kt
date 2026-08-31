package com.valoser.futacha.shared.compat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.valoser.futacha.shared.util.saturatingEpochSubtract

const val ARCHIVE_REPORT_API_BASE_URL = "https://api.inqueuet.com"
const val ARCHIVE_REPORT_ENDPOINT = "$ARCHIVE_REPORT_API_BASE_URL/api/v1/viewed-threads"
const val ARCHIVE_REPORT_PROTOCOL_VERSION = 1
const val ARCHIVE_REPORT_MAX_BATCH_SIZE = 20
const val ARCHIVE_REPORT_MAX_BODY_BYTES = 8_192
const val ARCHIVE_REPORT_MAX_URL_BYTES = 2_048
const val ARCHIVE_REPORT_SEND_DELAY_MILLIS = 15_000L
const val ARCHIVE_REPORT_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
const val ARCHIVE_REPORT_SENDING_STALE_MILLIS = 10L * 60L * 1_000L
const val ARCHIVE_REPORT_MAX_ROWS = 5_000
/** Background-only maintenance starts before the hard cap and deletes at most one small batch. */
const val ARCHIVE_REPORT_MAINTENANCE_START_ROWS = 4_500
const val ARCHIVE_REPORT_MAINTENANCE_TARGET_ROWS = 4_000
const val ARCHIVE_REPORT_MAINTENANCE_BATCH_ROWS = 100
const val ARCHIVE_REPORT_MAX_RESPONSE_BYTES = 65_536
const val ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS = 10_000L
const val ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY = "compat.archive_report.enabled"
const val ARCHIVE_REPORT_CONFIG_HOLD_MILLIS = Long.MAX_VALUE

fun archiveReportStaleCutoffEpochMillis(nowEpochMillis: Long): Long =
    saturatingEpochSubtract(nowEpochMillis, ARCHIVE_REPORT_SENDING_STALE_MILLIS)

private val archiveReportAllowedTargets = setOf(
    "may/layout",
    "img/b",
    "may/b",
    "may/id",
    "may/39",
    "may/26",
    "may/40"
)
private val archiveThreadPathRegex = Regex("^/([a-z0-9]+)/res/([0-9]+)\\.htm$", RegexOption.IGNORE_CASE)
private val archiveAuthorityRegex = Regex("^[^/?#]+://([^/?#]+)", RegexOption.IGNORE_CASE)
private val archiveHostRegex = Regex("^([a-z0-9]+)\\.2chan\\.net$", RegexOption.IGNORE_CASE)

data class NormalizedArchiveThread(
    val threadId: String,
    val url: String
)

/** Strict normalization for the seven production targets in client.txt §3/§5. */
fun normalizeArchiveReportThreadUrl(rawUrl: String): NormalizedArchiveThread? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty() || '%' in trimmed.substringBefore('?').substringBefore('#')) return null
    val authority = archiveAuthorityRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: return null
    if ('@' in authority || ':' in authority) return null // Explicit userinfo or port, including :443.

    val withoutFragment = trimmed.substringBefore('#')
    val withoutQuery = withoutFragment.substringBefore('?')
    val schemeSeparator = withoutQuery.indexOf("://")
    if (schemeSeparator <= 0) return null
    val scheme = withoutQuery.substring(0, schemeSeparator)
    if (!scheme.equals("http", true) && !scheme.equals("https", true)) return null
    val afterScheme = withoutQuery.substring(schemeSeparator + 3)
    val slash = afterScheme.indexOf('/')
    if (slash <= 0) return null
    val host = afterScheme.substring(0, slash).lowercase()
    val server = archiveHostRegex.matchEntire(host)?.groupValues?.getOrNull(1)?.lowercase() ?: return null
    val normalizedPath = afterScheme.substring(slash).replace(Regex("/{2,}"), "/")
    val pathMatch = archiveThreadPathRegex.matchEntire(normalizedPath) ?: return null
    val board = pathMatch.groupValues[1].lowercase()
    val threadNo = pathMatch.groupValues[2]
    if ("$server/$board" !in archiveReportAllowedTargets) return null
    val normalizedUrl = "https://$host/$board/res/$threadNo.htm"
    if (normalizedUrl.encodeToByteArray().size > ARCHIVE_REPORT_MAX_URL_BYTES) return null
    return NormalizedArchiveThread("$server/$board/$threadNo", normalizedUrl)
}

@Serializable
data class ArchiveReportRequest(
    @SerialName("protocol_version") val protocolVersion: Int = ARCHIVE_REPORT_PROTOCOL_VERSION,
    @SerialName("request_id") val requestId: String,
    val threads: List<String>
)

data class ArchiveReportPayload(
    val requestId: String,
    val threadIds: List<String>,
    val urls: List<String>,
    val bytes: ByteArray,
    val sha256: String
)

data class ArchiveReportEnqueueResult(
    val inserted: Boolean,
    val sendableCount: Int,
    val droppedForCapacity: Boolean = false
)

data class ArchiveReportOutboxBatch(
    val payload: ArchiveReportPayload,
    val attemptCount: Int
)

data class ArchiveReportOutboxStats(
    val total: Int,
    val pendingOrRetry: Int
)

@Serializable
data class ArchiveReportResponse(
    val accepted: Boolean? = null,
    val received: Int? = null,
    val reason: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Long? = null
)

sealed interface ArchiveReportDisposition {
    data object Accepted : ArchiveReportDisposition
    data object Split : ArchiveReportDisposition
    data class Retry(val delayMillis: Long, val errorCode: String) : ArchiveReportDisposition
    data class Abandon(val errorCode: String) : ArchiveReportDisposition
    /** Long.MAX_VALUE means that a corrected app build must explicitly release the batch. */
    data class Hold(val delayMillis: Long, val errorCode: String) : ArchiveReportDisposition
}

private val archiveReportJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

fun buildArchiveReportPayload(
    requestId: String,
    rows: List<NormalizedArchiveThread>,
    maxCount: Int = ARCHIVE_REPORT_MAX_BATCH_SIZE,
    maxBodyBytes: Int = ARCHIVE_REPORT_MAX_BODY_BYTES
): ArchiveReportPayload? {
    if (!requestId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{7,127}$"))) return null
    val sorted = rows.distinctBy(NormalizedArchiveThread::threadId)
        .sortedBy(NormalizedArchiveThread::threadId)
    if (sorted.isEmpty()) return null
    var accepted = emptyList<NormalizedArchiveThread>()
    for (candidate in sorted.take(maxCount.coerceIn(1, ARCHIVE_REPORT_MAX_BATCH_SIZE))) {
        val next = accepted + candidate
        val bytes = archiveReportJson.encodeToString(
            ArchiveReportRequest(requestId = requestId, threads = next.map(NormalizedArchiveThread::url))
        ).encodeToByteArray()
        if (bytes.size > maxBodyBytes) break
        accepted = next
    }
    if (accepted.isEmpty()) return null
    val bytes = archiveReportJson.encodeToString(
        ArchiveReportRequest(requestId = requestId, threads = accepted.map(NormalizedArchiveThread::url))
    ).encodeToByteArray()
    return ArchiveReportPayload(
        requestId = requestId,
        threadIds = accepted.map(NormalizedArchiveThread::threadId),
        urls = accepted.map(NormalizedArchiveThread::url),
        bytes = bytes,
        sha256 = archiveSha256(bytes)
    )
}

fun archiveReportRetryDelayMillis(attemptCountAfterFailure: Int, jitterFactor: Double): Long {
    val base = when (attemptCountAfterFailure.coerceAtLeast(1)) {
        1 -> 60_000L
        2 -> 5L * 60_000L
        3 -> 15L * 60_000L
        4 -> 60L * 60_000L
        else -> 6L * 60L * 60_000L
    }
    return (base * jitterFactor.coerceIn(0.8, 1.2)).toLong()
}

/** HTTP policy from client.txt section 9. Response text is never persisted in errorCode. */
fun classifyArchiveReportResponse(
    status: Int,
    response: ArchiveReportResponse?,
    retryAfterMillis: Long?,
    previousAttemptCount: Int,
    jitterFactor: Double = 1.0
): ArchiveReportDisposition {
    val reason = response?.reason.archiveReportSafeReason()
    val errorCode = "http_$status:$reason"
    fun retry(): ArchiveReportDisposition.Retry = ArchiveReportDisposition.Retry(
        delayMillis = retryAfterMillis?.coerceAtLeast(0L)
            ?: archiveReportRetryDelayMillis(nextArchiveReportAttempt(previousAttemptCount), jitterFactor),
        errorCode = errorCode
    )
    return when {
        status == 200 || status == 202 -> when {
            response?.accepted == true -> ArchiveReportDisposition.Accepted
            previousAttemptCount == 0 -> retry()
            else -> ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, "invalid_success_response")
        }
        status in 300..399 -> ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, errorCode)
        status == 400 && response?.reason == "invalid_thread_url" -> ArchiveReportDisposition.Split
        status == 400 -> ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, errorCode)
        status == 401 || status == 403 ->
            ArchiveReportDisposition.Hold(24L * 60L * 60L * 1_000L, errorCode)
        status == 404 || status == 405 || status == 415 ->
            ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, errorCode)
        status == 408 -> retry()
        status == 413 -> ArchiveReportDisposition.Split
        status == 429 -> retry()
        status in 500..599 -> retry()
        status in 200..299 -> ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, errorCode)
        status in 400..499 -> ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, errorCode)
        else -> retry()
    }
}

fun archiveReportNetworkFailureDisposition(
    previousAttemptCount: Int,
    jitterFactor: Double = 1.0,
    errorCode: String = "network_failure"
): ArchiveReportDisposition.Retry = ArchiveReportDisposition.Retry(
    archiveReportRetryDelayMillis(nextArchiveReportAttempt(previousAttemptCount), jitterFactor),
    errorCode.archiveReportSafeReason()
)

private fun nextArchiveReportAttempt(previousAttemptCount: Int): Int = when {
    previousAttemptCount < 0 -> 1
    previousAttemptCount == Int.MAX_VALUE -> Int.MAX_VALUE
    else -> previousAttemptCount + 1
}

private fun String?.archiveReportSafeReason(): String =
    this?.takeIf { it.matches(Regex("^[a-z0-9_]{1,64}$")) } ?: "unknown"

private fun archiveSha256(bytes: ByteArray): String {
    val hash = IntArray(8).apply {
        this[0] = 0x6a09e667
        this[1] = 0xbb67ae85.toInt()
        this[2] = 0x3c6ef372
        this[3] = 0xa54ff53a.toInt()
        this[4] = 0x510e527f
        this[5] = 0x9b05688c.toInt()
        this[6] = 0x1f83d9ab
        this[7] = 0x5be0cd19
    }
    val constants = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )
    val bitLength = bytes.size.toLong() * 8L
    val padding = (64 - ((bytes.size + 9) % 64)) % 64
    val message = ByteArray(bytes.size + 1 + padding + 8)
    bytes.copyInto(message)
    message[bytes.size] = 0x80.toByte()
    for (i in 0 until 8) message[message.lastIndex - i] = (bitLength ushr (8 * i)).toByte()
    val words = IntArray(64)
    for (offset in message.indices step 64) {
        for (i in 0 until 16) {
            val p = offset + i * 4
            words[i] = ((message[p].toInt() and 0xff) shl 24) or
                ((message[p + 1].toInt() and 0xff) shl 16) or
                ((message[p + 2].toInt() and 0xff) shl 8) or
                (message[p + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = words[i - 15].rotateRight(7) xor words[i - 15].rotateRight(18) xor (words[i - 15] ushr 3)
            val s1 = words[i - 2].rotateRight(17) xor words[i - 2].rotateRight(19) xor (words[i - 2] ushr 10)
            words[i] = words[i - 16] + s0 + words[i - 7] + s1
        }
        var a = hash[0]; var b = hash[1]; var c = hash[2]; var d = hash[3]
        var e = hash[4]; var f = hash[5]; var g = hash[6]; var h = hash[7]
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + s1 + ch + constants[i] + words[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        hash[0] += a; hash[1] += b; hash[2] += c; hash[3] += d
        hash[4] += e; hash[5] += f; hash[6] += g; hash[7] += h
    }
    return hash.joinToString("") { it.toUInt().toString(16).padStart(8, '0') }
}
