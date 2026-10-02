package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.http.Headers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil

internal const val OPENAI_LOCAL_TOKENS_PER_MINUTE = 8_000
internal const val OPENAI_LOCAL_REQUESTS_PER_DAY = 4_500
internal const val OPENAI_MODERATION_BATCH_TOKENS = 8_000
private const val HOUR = 3_600_000L

/** Conservative byte-based estimate, NOT the model's tokenizer or billed usage. */
internal fun estimateModerationTokens(text: String): Int = text.encodeToByteArray().size + 8

@Serializable
internal data class AiUsageBucket(val at: Long, val requests: Int = 0, val tokens: Long = 0)

@Serializable
internal data class OpenAiKeyUsage(
    val attempts: Long = 0,
    val estimatedTokens: Long = 0,
    val successes: Long = 0,
    val rateLimits: Long = 0,
    val lastStatus: Int? = null,
    val lastResponseAt: Long = 0,
    val waitUntil: Long = 0,
    val blockedReason: String = "",
    val consecutiveRateLimits: Int = 0,
    val remainingRequests: Long? = null,
    val requestsResetAt: Long? = null,
    val remainingTokens: Long? = null,
    val tokensResetAt: Long? = null,
    val remainingProjectTokens: Long? = null,
    val projectTokensResetAt: Long? = null
) {
    fun requiredWait(now: Long, tokens: Int): Long {
        var until = waitUntil
        if (remainingRequests == 0L) until = maxOf(until, requestsResetAt ?: 0)
        if (remainingTokens != null && remainingTokens < tokens) until = maxOf(until, tokensResetAt ?: 0)
        if (remainingProjectTokens != null && remainingProjectTokens < tokens) until = maxOf(until, projectTokensResetAt ?: 0)
        return (until - now).coerceAtLeast(0)
    }
}

@Serializable
internal data class OpenAiUsageState(
    val hours: List<AiUsageBucket> = emptyList(),
    val minute: List<AiUsageBucket> = emptyList(),
    val waitUntil: Long = 0,
    val waitReason: String = "",
    val lastStatus: Int? = null,
    val lastResponseAt: Long = 0,
    val successfulRequests: Long = 0,
    val rateLimitedRequests: Long = 0,
    val serverRetryAfterMillis: Long? = null,
    val requestLimit: Long? = null,
    val remainingRequests: Long? = null,
    val requestsResetAt: Long? = null,
    val tokenLimit: Long? = null,
    val remainingTokens: Long? = null,
    val tokensResetAt: Long? = null,
    val projectTokenLimit: Long? = null,
    val remainingProjectTokens: Long? = null,
    val projectTokensResetAt: Long? = null,
    val permanentQuotaError: Boolean = false,
    val storageError: Boolean = false,
    val activeKeyId: String? = null,
    val keys: Map<String, OpenAiKeyUsage> = emptyMap()
) {
    // Retaining the boundary hour deliberately overcounts by at most one hour.
    fun recentHours(now: Long) = hours.filter { it.at + HOUR > now - 24 * HOUR }
    fun recentMinute(now: Long) = minute.filter { it.at > now - 60_000 }
}

/** Numeric diagnostics only: never persist response bodies, credentials, or post text. */
internal class OpenAiUsageTracker(private val storage: AiConnectionStorage, private val now: () -> Long) {
    private val mutex = Mutex()
    private var loaded = false
    private val mutableState = MutableStateFlow(OpenAiUsageState())
    val state = mutableState.asStateFlow()

    suspend fun load() = withContext(AppDispatchers.io) { mutex.withLock { loadLocked() } }
    private fun loadLocked() {
        if (loaded) return
        loaded = true
        try {
            storage.readUsage()?.takeIf { it.length <= 100_000 }?.let {
                mutableState.value = Json { ignoreUnknownKeys = true }.decodeFromString<OpenAiUsageState>(it)
            }
        } catch (_: Exception) { mutableState.value = mutableState.value.copy(storageError = true) }
    }
    private suspend fun update(block: (OpenAiUsageState) -> OpenAiUsageState) = withContext(AppDispatchers.io) {
        mutex.withLock {
            loadLocked()
            val next = block(mutableState.value.rebasedAfterClockRollback(now()))
            if (next == mutableState.value) return@withLock
            mutableState.value = next
            try { storage.writeUsage(Json.encodeToString(next)) }
            catch (_: Exception) { mutableState.value = next.copy(storageError = true) }
        }
    }

    /** Shifts stored deadlines when the device clock moved backwards (see [rebasedAfterClockRollback]). */
    suspend fun observeClock() = update { it }

    suspend fun requiredWait(tokens: Int, localLimitsOnly: Boolean = false): Long {
        load()
        observeClock()
        val time = now()
        val s = state.value
        var until = if (localLimitsOnly && s.waitReason !in setOf("local_tokens", "local_requests")) 0L else s.waitUntil
        var reason = s.waitReason
        fun hold(target: Long?, cause: String) {
            if (target != null && target > until) { until = target; reason = cause }
        }
        val minute = s.recentMinute(time)
        var total = minute.sumOf { it.tokens } + tokens
        for (entry in minute) {
            if (total <= OPENAI_LOCAL_TOKENS_PER_MINUTE) break
            hold(entry.at + 60_000, "local_tokens")
            total -= entry.tokens
        }
        val hours = s.recentHours(time)
        var requests = hours.sumOf { it.requests }
        for (entry in hours) {
            if (requests < OPENAI_LOCAL_REQUESTS_PER_DAY) break
            hold(entry.at + 25 * HOUR, "local_requests")
            requests -= entry.requests
        }
        if (!localLimitsOnly) {
            if (s.remainingRequests == 0L) hold(s.requestsResetAt, "server_requests")
            if (s.remainingTokens != null && s.remainingTokens < tokens) hold(s.tokensResetAt, "server_tokens")
            if (s.remainingProjectTokens != null && s.remainingProjectTokens < tokens) hold(s.projectTokensResetAt, "server_tokens")
        }
        // Local-only checks ignore the server wait; never replace a longer active server wait
        // (and its displayed reason) with a shorter local one.
        if (until > maxOf(time, s.waitUntil)) {
            update { it.copy(waitUntil = until, waitReason = reason) }
        }
        return (until - time).coerceAtLeast(0)
    }

    suspend fun attempt(tokens: Int) = update { s ->
        val time = now()
        val hour = time / HOUR * HOUR
        val hours = s.recentHours(time).toMutableList()
        val index = hours.indexOfFirst { it.at == hour }
        val previous = hours.getOrNull(index) ?: AiUsageBucket(hour)
        val next = previous.copy(requests = previous.requests + 1, tokens = previous.tokens + tokens)
        if (index < 0) hours += next else hours[index] = next
        s.copy(hours = hours, minute = s.recentMinute(time) + AiUsageBucket(time, 1, tokens.toLong()))
    }

    suspend fun retainKeys(ids: Set<String>, resetOrder: Boolean = false) = update { s ->
        s.copy(keys = s.keys.filterKeys { it in ids }, activeKeyId = s.activeKeyId?.takeIf { it in ids && !resetOrder })
    }
    suspend fun migrateLegacyKey(id: String) = update { s ->
        if (s.keys.isNotEmpty()) s else s.copy(activeKeyId = id, keys = mapOf(id to OpenAiKeyUsage(
            attempts = s.hours.sumOf { it.requests }.toLong(), estimatedTokens = s.hours.sumOf { it.tokens },
            successes = s.successfulRequests, rateLimits = s.rateLimitedRequests,
            lastStatus = s.lastStatus, lastResponseAt = s.lastResponseAt,
            waitUntil = s.waitUntil, blockedReason = if (s.permanentQuotaError) "利用枠・契約の確認が必要です" else "",
            remainingRequests = s.remainingRequests, requestsResetAt = s.requestsResetAt,
            remainingTokens = s.remainingTokens, tokensResetAt = s.tokensResetAt,
            remainingProjectTokens = s.remainingProjectTokens, projectTokensResetAt = s.projectTokensResetAt)))
    }
    suspend fun keyAttempt(id: String, tokens: Int) = update { s ->
        val key = s.keys[id] ?: OpenAiKeyUsage()
        s.copy(activeKeyId = id, keys = s.keys + (id to key.copy(attempts = key.attempts + 1, estimatedTokens = key.estimatedTokens + tokens)))
    }
    suspend fun clearKeyBlock(id: String) = update { s ->
        val key = s.keys[id] ?: return@update s
        // An explicit retry may clear a billing/authentication hold, never Retry-After.
        s.copy(keys = s.keys + (id to key.copy(blockedReason = "")))
    }
    suspend fun keyResponse(id: String, status: Int, headers: Headers, permanentQuota: Boolean) = update { s ->
        val key = s.keys[id] ?: OpenAiKeyUsage()
        val time = now()
        fun count(name: String) = headers[name]?.toLongOrNull()?.takeIf { it >= 0 }
        fun reset(name: String) = openAiResetMillis(headers[name])?.let { time + it }
        val limited = status == 429 && !permanentQuota
        val retry = openAiRetryAfterMillis(headers["Retry-After"], time)
        val until = if (limited) time + maxOf(retry ?: 0L, 1_000L shl key.consecutiveRateLimits.coerceAtMost(6)) else 0L
        val next = key.copy(
            lastStatus = status, lastResponseAt = time, waitUntil = maxOf(key.waitUntil, until),
            successes = key.successes + if (status in 200..299) 1 else 0,
            rateLimits = key.rateLimits + if (status == 429) 1 else 0,
            consecutiveRateLimits = if (limited) (key.consecutiveRateLimits + 1).coerceAtMost(6) else 0,
            blockedReason = when { status == 401 -> "APIキーの確認が必要です"; status == 403 -> "APIの利用権限を確認してください";
                permanentQuota -> "利用枠・契約の確認が必要です"; else -> "" },
            remainingRequests = count("x-ratelimit-remaining-requests"), requestsResetAt = reset("x-ratelimit-reset-requests"),
            remainingTokens = count("x-ratelimit-remaining-tokens"), tokensResetAt = reset("x-ratelimit-reset-tokens"),
            remainingProjectTokens = count("x-ratelimit-remaining-project-tokens"), projectTokensResetAt = reset("x-ratelimit-reset-project-tokens"))
        s.copy(keys = s.keys + (id to next), waitUntil = if (limited) until else s.waitUntil,
            waitReason = if (limited) { if (retry != null) "server_retry" else "backoff" } else s.waitReason)
    }

    suspend fun cooldown(millis: Long, reason: String) = update { s ->
        val until = now() + millis
        if (until > s.waitUntil) s.copy(waitUntil = until, waitReason = reason) else s
    }

    suspend fun response(status: Int, headers: Headers, permanentQuota: Boolean) = update { s ->
        val time = now()
        fun count(name: String) = headers[name]?.toLongOrNull()?.takeIf { it >= 0 }
        fun reset(name: String) = openAiResetMillis(headers[name])?.let { time + it }
        val retry = openAiRetryAfterMillis(headers["Retry-After"], time)
        s.copy(lastStatus = status, lastResponseAt = time,
            successfulRequests = s.successfulRequests + if (status in 200..299) 1 else 0,
            rateLimitedRequests = s.rateLimitedRequests + if (status == 429) 1 else 0,
            serverRetryAfterMillis = retry,
            requestLimit = count("x-ratelimit-limit-requests"),
            remainingRequests = count("x-ratelimit-remaining-requests"),
            requestsResetAt = reset("x-ratelimit-reset-requests"),
            tokenLimit = count("x-ratelimit-limit-tokens"),
            remainingTokens = count("x-ratelimit-remaining-tokens"),
            tokensResetAt = reset("x-ratelimit-reset-tokens"),
            projectTokenLimit = count("x-ratelimit-limit-project-tokens"),
            remainingProjectTokens = count("x-ratelimit-remaining-project-tokens"),
            projectTokensResetAt = reset("x-ratelimit-reset-project-tokens"),
            permanentQuotaError = permanentQuota,
            waitUntil = if (status in 200..299) 0 else s.waitUntil,
            waitReason = if (status in 200..299) "" else s.waitReason)
    }
}

private const val CLOCK_ROLLBACK_TOLERANCE_MILLIS = 5_000L

/**
 * Deadlines are wall-clock times. If the clock moved backwards (manual change, bad NTP), every
 * stored time is shifted by the same amount so remaining waits keep their original length
 * instead of growing by the rollback and blocking requests until the clock catches up.
 */
internal fun OpenAiUsageState.rebasedAfterClockRollback(now: Long): OpenAiUsageState {
    val latest = maxOf(lastResponseAt, minute.maxOfOrNull { it.at } ?: 0L, hours.maxOfOrNull { it.at } ?: 0L,
        keys.values.maxOfOrNull { it.lastResponseAt } ?: 0L)
    if (latest - now <= CLOCK_ROLLBACK_TOLERANCE_MILLIS) return this
    val delta = now - latest
    fun Long.shift() = if (this > 0L) (this + delta).coerceAtLeast(0L) else this
    fun Long?.shiftOrNull() = this?.shift()
    return copy(
        hours = hours.map { it.copy(at = it.at.shift()) }, minute = minute.map { it.copy(at = it.at.shift()) },
        waitUntil = waitUntil.shift(), lastResponseAt = lastResponseAt.shift(),
        requestsResetAt = requestsResetAt.shiftOrNull(), tokensResetAt = tokensResetAt.shiftOrNull(),
        projectTokensResetAt = projectTokensResetAt.shiftOrNull(),
        keys = keys.mapValues { (_, key) -> key.copy(waitUntil = key.waitUntil.shift(), lastResponseAt = key.lastResponseAt.shift(),
            requestsResetAt = key.requestsResetAt.shiftOrNull(), tokensResetAt = key.tokensResetAt.shiftOrNull(),
            projectTokensResetAt = key.projectTokensResetAt.shiftOrNull()) })
}

/** Reset headers use durations such as 1m2.5s, unlike Retry-After. */
internal fun openAiResetMillis(value: String?): Long? {
    if (value == null || value.length > 80) return null
    val text = value.trim()
    val matches = Regex("([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h|d)").findAll(text).toList()
    if (matches.isEmpty() || matches.joinToString("") { it.value } != text) return null
    val millis = matches.sumOf {
        it.groupValues[1].toDouble() * when (it.groupValues[2]) {
            "ms" -> 1; "s" -> 1_000; "m" -> 60_000; "h" -> 3_600_000; else -> 86_400_000
        }
    }
    return millis.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE / 4 }?.let { ceil(it).toLong() }
}
