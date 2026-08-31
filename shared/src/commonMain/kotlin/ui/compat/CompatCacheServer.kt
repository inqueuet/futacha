package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Preferences used by the compatibility network/cache adapter. */
internal const val COMPAT_CACHE_ENABLED_KEY = "compat.network.cache.enabled"
internal const val COMPAT_CACHE_BASE_URL_KEY = "compat.network.cache.base_url"
internal const val COMPAT_CACHE_STATUS_KEY = "compat.network.cache.status"
internal const val COMPAT_CACHE_AVAILABLE_KEY = "compat.network.cache.available"
internal const val COMPAT_CACHE_CHECK_TIME_KEY = "compat.network.cache.check_time"
internal const val COMPAT_CACHE_STATUS_DATE_KEY = "compat.network.cache.status_date"
internal const val COMPAT_CACHE_RESPONSE_THRESHOLD_KEY = "compat.network.cache.response_threshold"
internal const val COMPAT_ARCHIVE_SEARCH_HISTORY_KEY = "compat.archive.search.history"
internal const val COMPAT_ARCHIVE_SEARCH_NOTICE_HIDDEN_KEY = "compat.archive.search.notice_hidden"

private const val DEFAULT_COMPAT_CACHE_BASE_URL = "https://may.inqueuet.com"
private const val CACHE_STATUS_TIMEOUT_MILLIS = 8_000L
private const val CACHE_STATUS_RESPONSE_MAX_BYTES = 16 * 1024
internal const val COMPAT_CACHE_UNAVAILABLE_RETRY_MILLIS = 15L * 60_000L
internal const val DEFAULT_COMPAT_CACHE_RESPONSE_THRESHOLD = 500
private val compatCacheThreadPathRegex = Regex("/res/[0-9]+\\.html?")
private val compatCacheSupportedBoardRegex = Regex(
    "^https?://(?:may\\.2chan\\.net/b|img\\.2chan\\.net/b|jun\\.2chan\\.net/jun|dec\\.2chan\\.net/b)/",
    RegexOption.IGNORE_CASE
)

internal data class CompatCacheServerStatus(
    val available: Boolean,
    val message: String,
    val checkedAtEpochMillis: Long
)

internal fun formatCompatCacheStatusDate(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val local = Instant.fromEpochMilliseconds(epochMillis.coerceAtLeast(0L)).toLocalDateTime(timeZone)
    return buildString {
        append(local.year.toString().padStart(4, '0'))
        append('/')
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('/')
        append(local.day.toString().padStart(2, '0'))
        append(' ')
        append(local.hour.toString().padStart(2, '0'))
        append(":00")
    }
}

/**
 * sample/1.apk checks once per wall-clock hour and retries an unavailable
 * server after fifteen minutes while the current hour is unchanged.
 */
internal fun shouldProbeCompatCacheServer(
    nowEpochMillis: Long,
    storedStatusDate: String?,
    storedAvailable: Boolean,
    storedCheckTimeEpochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Boolean {
    if (storedStatusDate != formatCompatCacheStatusDate(nowEpochMillis, timeZone)) return true
    if (storedAvailable) return false
    return nowEpochMillis - storedCheckTimeEpochMillis > COMPAT_CACHE_UNAVAILABLE_RETRY_MILLIS
}

internal fun formatCompatCacheStatusSummary(statusDate: String, message: String): String =
    "$statusDate - $message"

internal data class CompatCacheToggle(
    val storedValue: String,
    val message: String
)

/** Catalog/Thread toolbar switches toggle immediately without the Settings warning. */
internal fun nextCompatCacheToggle(currentlyEnabled: Boolean): CompatCacheToggle =
    if (currentlyEnabled) {
        CompatCacheToggle("OFF", "通信の軽量化オフ")
    } else {
        CompatCacheToggle("ON", "通信の軽量化オン")
    }

/**
 * sample/1.apk only sends eligible high-volume Futaba threads to its compact
 * response server. An empty local response count is treated as a first load
 * and is also eligible; the historical archive remains a separate fallback.
 */
internal fun canUseCompatCacheServer(
    settingEnabled: Boolean,
    serverAvailable: Boolean,
    sourceUrl: String,
    currentReplyCount: Int,
    responseThreshold: Int = DEFAULT_COMPAT_CACHE_RESPONSE_THRESHOLD
): Boolean = settingEnabled &&
    serverAvailable &&
    compatCacheSupportedBoardRegex.containsMatchIn(sourceUrl.trim()) &&
    (currentReplyCount <= 0 || currentReplyCount >= responseThreshold.coerceAtLeast(1))

internal fun normalizeCompatCacheBaseUrl(raw: String?): String? {
    val value = raw?.trim()?.trimEnd('/') ?: return null
    if (value.isBlank()) return null
    return runCatching {
        val url = Url(value)
        if (url.protocol.name !in setOf("http", "https")) return null
        if (url.host.isBlank() || value.substringAfter("://").substringBefore('/').contains('@')) return null
        if (value.substringAfter("://").contains('?') || value.substringAfter("://").contains('#')) return null
        // A cache endpoint is a server root. Keeping a path would make the same
        // preference produce ambiguous /api/api/... URLs.
        if (url.encodedPath.isNotBlank() && url.encodedPath != "/") return null
        "${url.protocol.name}://${url.host}${url.port.takeIf { it != url.protocol.defaultPort }?.let { ":$it" }.orEmpty()}"
    }.getOrNull()
}

internal fun effectiveCompatCacheBaseUrl(raw: String?): String {
    return normalizeCompatCacheBaseUrl(raw) ?: DEFAULT_COMPAT_CACHE_BASE_URL
}

/** Maps a source Futaba thread to the self-owned cache/archive GET endpoint. */
internal fun buildCompatCacheThreadUrl(sourceUrl: String, baseUrl: String?): String? {
    val normalizedBase = normalizeCompatCacheBaseUrl(baseUrl)
    if (normalizedBase == null || sourceUrl.isBlank()) {
        return buildInqueuetArchiveThreadUrlFromUrl(sourceUrl)
    }
    return runCatching {
        val source = Url(sourceUrl.trim())
        val path = source.encodedPath.takeIf { it.isNotBlank() } ?: return null
        if (!compatCacheThreadPathRegex.containsMatchIn(path)) return null
        "$normalizedBase$path"
    }.getOrNull()
}

internal suspend fun probeCompatCacheServer(
    httpClient: HttpClient,
    baseUrl: String,
    nowEpochMillis: Long,
    timeoutMillis: Long = CACHE_STATUS_TIMEOUT_MILLIS
): CompatCacheServerStatus {
    val normalized = normalizeCompatCacheBaseUrl(baseUrl)
        ?: return CompatCacheServerStatus(false, "接続先URLが不正です", nowEpochMillis)
    return try {
        withTimeout(timeoutMillis) {
            val health = httpClient.get("$normalized/health/search") {
                headers[HttpHeaders.Accept] = "application/json"
                headers[HttpHeaders.CacheControl] = "no-cache"
            }
            readBoundedHttpResponseText(
                health,
                CACHE_STATUS_RESPONSE_MAX_BYTES,
                timeoutMillis
            )
            if (health.status.isSuccess()) {
                // The reference status API supplies a user-facing message;
                // the current health endpoint supplies machine JSON instead.
                // Keep the reference summary rather than exposing raw JSON.
                CompatCacheServerStatus(true, "稼働中", nowEpochMillis)
            } else {
                CompatCacheServerStatus(false, "Http ${health.status.value} Error", nowEpochMillis)
            }
        }
    } catch (_: TimeoutCancellationException) {
        CompatCacheServerStatus(false, "通信タイムアウト", nowEpochMillis)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        val detail = failure.message?.trim().orEmpty()
        CompatCacheServerStatus(
            false,
            if (detail.isBlank()) "通信エラー" else "通信エラー\n${detail.take(120)}",
            nowEpochMillis
        )
    }
}
