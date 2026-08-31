package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.saturatingEpochAdd
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock
import platform.Foundation.NSUUID
import kotlin.concurrent.AtomicReference

private const val TAG = "IosArchiveReport"
private const val IOS_ARCHIVE_REPORT_MAX_BATCHES_PER_RUN = 8
private const val IOS_ARCHIVE_REPORT_MAX_SPLIT_DEPTH = 5
private const val IOS_ARCHIVE_RESPONSE_MAX_ZERO_READS = 100
private const val IOS_ARCHIVE_RESPONSE_ZERO_READ_BACKOFF_MILLIS = 10L

internal data class IosArchiveReportHttpResult(
    val status: Int,
    val response: ArchiveReportResponse?,
    val retryAfterMillis: Long?
)

/**
 * iOS counterpart to Android's ArchiveReportWorker sender.  It deliberately
 * owns a narrow, redirect-disabled Darwin client instead of reusing browsing
 * cookies or a general-purpose client configuration.
 */
internal class IosArchiveReportSender(
    private val client: HttpClient,
    private val endpoint: String = ARCHIVE_REPORT_ENDPOINT
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun send(payload: ArchiveReportPayload, nowEpochMillis: Long): IosArchiveReportHttpResult {
        check(endpoint == ARCHIVE_REPORT_ENDPOINT && endpoint.startsWith("https://api.inqueuet.com/")) {
            "Untrusted archive report endpoint"
        }
        val response = withTimeout(ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS) {
            client.post(endpoint) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "Futacha/5")
                setBody(payload.bytes)
            }
        }
        val bytes = response.readArchiveReportResponseBytes()
        val decoded = bytes?.let { body ->
            runCatching { json.decodeFromString(ArchiveReportResponse.serializer(), body.decodeToString()) }.getOrNull()
        }
        val retryAfter = response.headers[HttpHeaders.RetryAfter]
            ?.trim()
            ?.toLongOrNull()
            ?.coerceIn(0L, IOS_ARCHIVE_REPORT_MAX_SERVER_RETRY_SECONDS)
            ?.times(1_000L)
            ?: decoded?.retryAfterSeconds
                ?.coerceIn(0L, IOS_ARCHIVE_REPORT_MAX_SERVER_RETRY_SECONDS)
                ?.times(1_000L)
        return IosArchiveReportHttpResult(response.status.value, decoded, retryAfter)
    }

    private suspend fun HttpResponse.readArchiveReportResponseBytes(): ByteArray? {
        val declared = contentLength()
        if (declared != null && declared > ARCHIVE_REPORT_MAX_RESPONSE_BYTES) {
            bodyAsChannel().cancel()
            return null
        }
        return withTimeout(ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS) {
            val channel = bodyAsChannel()
            val output = ByteArray(ARCHIVE_REPORT_MAX_RESPONSE_BYTES)
            val buffer = ByteArray(8 * 1024)
            var outputSize = 0
            var zeroReadCount = 0
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) {
                    zeroReadCount += 1
                    if (zeroReadCount >= IOS_ARCHIVE_RESPONSE_MAX_ZERO_READS) return@withTimeout null
                    delay(IOS_ARCHIVE_RESPONSE_ZERO_READ_BACKOFF_MILLIS)
                    continue
                }
                zeroReadCount = 0
                if (read > ARCHIVE_REPORT_MAX_RESPONSE_BYTES - outputSize) {
                    channel.cancel()
                    return@withTimeout null
                }
                buffer.copyInto(output, destinationOffset = outputSize, startIndex = 0, endIndex = read)
                outputSize += read
            }
            output.copyOf(outputSize)
        }
    }
}

/** Processes one bounded outbox run and is independently testable without HTTP. */
internal class IosArchiveReportOutboxProcessor(
    private val send: suspend (ArchiveReportPayload, Long) -> IosArchiveReportHttpResult?
) {
    suspend fun process(
        store: CompatibilityStore,
        commitAllowed: suspend () -> Boolean = { true }
    ): Long? {
        if (!commitAllowed()) return null
        if (store.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY) == "OFF") return null
        val initialNow = Clock.System.now().toEpochMilliseconds()
        if (!commitAllowed()) return null
        store.maintainArchiveReportOutbox(initialNow)
        if (!commitAllowed()) return null
        store.recoverStaleArchiveReports(initialNow)
        var processedBatches = 0
        while (processedBatches < IOS_ARCHIVE_REPORT_MAX_BATCHES_PER_RUN) {
            if (!commitAllowed()) return null
            val now = Clock.System.now().toEpochMilliseconds()
            val batch = store.claimArchiveReportBatch(now, newIosArchiveReportRequestId()) ?: break
            processBatch(store, batch, splitDepth = 0, commitAllowed = commitAllowed)
            processedBatches += 1
        }
        return if (commitAllowed()) store.archiveReportNextAttemptAt() else null
    }

    private suspend fun processBatch(
        store: CompatibilityStore,
        batch: ArchiveReportOutboxBatch,
        splitDepth: Int,
        commitAllowed: suspend () -> Boolean
    ) {
        val payload = batch.payload
        val now = Clock.System.now().toEpochMilliseconds()
        val result = try {
            send(payload, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        // Do not let an HTTP response from an old compatibility session write
        // into the shared SQLite state after a profile generation changed.
        if (!commitAllowed()) return
        val disposition = if (result == null) {
            archiveReportNetworkFailureDisposition(batch.attemptCount, archiveReportJitter(), "network_failure")
        } else {
            classifyArchiveReportResponse(
                status = result.status,
                response = result.response,
                retryAfterMillis = result.retryAfterMillis,
                previousAttemptCount = batch.attemptCount,
                jitterFactor = archiveReportJitter()
            )
        }
        when (disposition) {
            ArchiveReportDisposition.Accepted -> {
                if (!commitAllowed()) return
                store.markArchiveReportAccepted(payload.requestId, now)
            }
            ArchiveReportDisposition.Split -> {
                if (!commitAllowed()) return
                if (payload.urls.size <= 1 || splitDepth >= IOS_ARCHIVE_REPORT_MAX_SPLIT_DEPTH) {
                    store.markArchiveReportAbandoned(
                        payload.requestId,
                        now,
                        if (result?.status == 413) "http_413_body_too_large" else "http_400_invalid_thread_url"
                    )
                } else {
                    splitAndProcess(store, batch, splitDepth, now, commitAllowed)
                }
            }
            is ArchiveReportDisposition.Retry -> {
                if (!commitAllowed()) return
                store.markArchiveReportRetry(
                    payload.requestId,
                    safeIosArchiveNextAttemptAt(now, disposition.delayMillis),
                    disposition.errorCode
                )
            }
            is ArchiveReportDisposition.Hold -> {
                if (!commitAllowed()) return
                store.markArchiveReportRetry(
                    payload.requestId,
                    safeIosArchiveNextAttemptAt(now, disposition.delayMillis),
                    disposition.errorCode
                )
            }
            is ArchiveReportDisposition.Abandon -> {
                if (!commitAllowed()) return
                store.markArchiveReportAbandoned(
                    payload.requestId,
                    now,
                    disposition.errorCode
                )
            }
        }
    }

    private suspend fun splitAndProcess(
        store: CompatibilityStore,
        original: ArchiveReportOutboxBatch,
        splitDepth: Int,
        now: Long,
        commitAllowed: suspend () -> Boolean
    ) {
        if (!commitAllowed()) return
        val rows = original.payload.threadIds.zip(original.payload.urls).map { (id, url) ->
            NormalizedArchiveThread(id, url)
        }
        val middle = rows.size / 2
        val first = buildArchiveReportPayload(newIosArchiveReportRequestId(), rows.subList(0, middle))
        val second = buildArchiveReportPayload(newIosArchiveReportRequestId(), rows.subList(middle, rows.size))
        if (first == null || second == null || !commitAllowed() || !store.splitSendingArchiveReportBatch(
                original.payload.requestId,
                first,
                second,
                now
            )
        ) {
            if (!commitAllowed()) return
            store.markArchiveReportRetry(
                original.payload.requestId,
                safeIosArchiveNextAttemptAt(now, 60_000L),
                "split_transaction_failure"
            )
            return
        }
        processBatch(store, ArchiveReportOutboxBatch(first, original.attemptCount), splitDepth + 1, commitAllowed)
        processBatch(store, ArchiveReportOutboxBatch(second, original.attemptCount), splitDepth + 1, commitAllowed)
    }
}

/**
 * Foreground retries are best effort.  The iOS BGTask path calls [processNow]
 * as well, so a suspended app never relies solely on this delayed coroutine.
 */
internal object IosArchiveReportScheduler {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val mutex = Mutex()
    private val scheduleState = AtomicReference(IosArchiveReportScheduleState())

    fun enqueueAfterView(
        store: CompatibilityStore,
        sendableCount: Int,
        commitAllowed: suspend () -> Boolean = { true }
    ) {
        schedule(
            store,
            if (sendableCount >= ARCHIVE_REPORT_MAX_BATCH_SIZE) 0L else ARCHIVE_REPORT_SEND_DELAY_MILLIS,
            commitAllowed
        )
    }

    fun enqueueStartup(
        store: CompatibilityStore,
        commitAllowed: suspend () -> Boolean = { true }
    ) = schedule(store, 0L, commitAllowed)

    fun cancel() {
        while (true) {
            val current = scheduleState.value
            val cleared = IosArchiveReportScheduleState(
                generation = nextIosArchiveScheduleGeneration(current.generation)
            )
            if (scheduleState.compareAndSet(current, cleared)) {
                current.job?.cancel()
                return
            }
        }
    }

    suspend fun processNow(
        store: CompatibilityStore,
        commitAllowed: suspend () -> Boolean = { true }
    ): Long? = mutex.withLock {
        val client = createIosArchiveReportHttpClient()
        try {
            IosArchiveReportOutboxProcessor { payload, now ->
                IosArchiveReportSender(client).send(payload, now)
            }.process(store, commitAllowed)
        } finally {
            client.close()
        }
    }

    private fun schedule(
        store: CompatibilityStore,
        delayMillis: Long,
        commitAllowed: suspend () -> Boolean
    ) {
        while (true) {
            val current = scheduleState.value
            val generation = nextIosArchiveScheduleGeneration(current.generation)
            lateinit var nextJob: Job
            nextJob = scope.launch(start = CoroutineStart.LAZY) {
                if (delayMillis > 0L) delay(delayMillis)
                val next = runSuspendCatchingPreservingCancellation { processNow(store, commitAllowed) }
                    .onFailure { failure -> Logger.w(TAG, "Archive report run failed: ${failure.message.orEmpty()}") }
                    .getOrNull()
                val stillCurrent = scheduleState.value.let { state ->
                    state.generation == generation && state.job === nextJob
                }
                if (stillCurrent && next != null && next != Long.MAX_VALUE) {
                    val nowMillis = Clock.System.now().toEpochMilliseconds()
                    val remaining = when {
                        next <= nowMillis -> 0L
                        next - nowMillis < 0L -> Long.MAX_VALUE
                        else -> next - nowMillis
                    }
                    schedule(store, remaining, commitAllowed)
                }
            }
            val replacement = IosArchiveReportScheduleState(generation, nextJob)
            if (scheduleState.compareAndSet(current, replacement)) {
                current.job?.cancel()
                nextJob.start()
                return
            }
            nextJob.cancel()
        }
    }
}

private data class IosArchiveReportScheduleState(
    val generation: Long = 0L,
    val job: Job? = null
)

private fun nextIosArchiveScheduleGeneration(current: Long): Long =
    if (current <= 0L || current == Long.MAX_VALUE) 1L else current + 1L

private fun createIosArchiveReportHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
        connectTimeoutMillis = ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
        socketTimeoutMillis = ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
    }
    engine {
        configureRequest { setTimeoutInterval(ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS / 1_000.0) }
    }
}

private fun newIosArchiveReportRequestId(): String = NSUUID().UUIDString()

private fun archiveReportJitter(): Double = Random.nextDouble(0.8, 1.2)

private fun safeIosArchiveNextAttemptAt(now: Long, delayMillis: Long): Long = when {
    delayMillis == ARCHIVE_REPORT_CONFIG_HOLD_MILLIS -> Long.MAX_VALUE
    delayMillis <= 0L -> now
    else -> saturatingEpochAdd(now, delayMillis)
}

private const val IOS_ARCHIVE_REPORT_MAX_SERVER_RETRY_SECONDS = 7L * 24L * 60L * 60L
