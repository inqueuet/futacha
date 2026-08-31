package com.valoser.futacha

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_CONFIG_HOLD_MILLIS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENDPOINT
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAX_RESPONSE_BYTES
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_SEND_DELAY_MILLIS
import com.valoser.futacha.shared.compat.ArchiveReportDisposition
import com.valoser.futacha.shared.compat.ArchiveReportOutboxBatch
import com.valoser.futacha.shared.compat.ArchiveReportPayload
import com.valoser.futacha.shared.compat.ArchiveReportResponse
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.NormalizedArchiveThread
import com.valoser.futacha.shared.compat.archiveReportNetworkFailureDisposition
import com.valoser.futacha.shared.compat.buildArchiveReportPayload
import com.valoser.futacha.shared.compat.classifyArchiveReportResponse
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.saturatingEpochAdd
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ArchiveReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? FutachaApplication
            ?: return Result.failure()
        val store = runCatching { app.compatibilityStore }.getOrNull() ?: return Result.failure()
        val enabled = runSuspendCatchingPreservingCancellation {
            store.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY) != "OFF"
        }.getOrElse {
            Logger.w(TAG, "Unable to read archive report setting")
            return Result.retry()
        }
        if (!enabled) return Result.success()

        runSuspendCatchingPreservingCancellation {
            store.maintainArchiveReportOutbox(System.currentTimeMillis())
        }
            .onFailure { Logger.w(TAG, "Unable to maintain archive report outbox") }
        runSuspendCatchingPreservingCancellation {
            store.recoverStaleArchiveReports(System.currentTimeMillis())
        }
            .onFailure { Logger.w(TAG, "Unable to recover stale archive report batches") }

        val client = createArchiveReportHttpClient()
        val sender = ArchiveReportSender(client)
        return try {
            var processedBatches = 0
            while (processedBatches < MAX_BATCHES_PER_RUN) {
                val now = System.currentTimeMillis()
                val batch = store.claimArchiveReportBatch(now, newArchiveReportRequestId()) ?: break
                processBatch(store, sender, batch, splitDepth = 0)
                processedBatches += 1
            }
            scheduleNextDue(store)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Logger.w(TAG, "Archive report worker failed before the current batch was finalized")
            runSuspendCatchingPreservingCancellation { scheduleNextDue(store) }
            Result.retry()
        } finally {
            client.close()
        }
    }

    private suspend fun processBatch(
        store: CompatibilityStore,
        sender: ArchiveReportSender,
        batch: ArchiveReportOutboxBatch,
        splitDepth: Int
    ) {
        val payload = batch.payload
        val now = System.currentTimeMillis()
        val httpResult = try {
            sender.send(payload, now)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        val disposition = if (httpResult == null) {
            archiveReportNetworkFailureDisposition(
                previousAttemptCount = batch.attemptCount,
                jitterFactor = archiveReportJitter(),
                errorCode = "network_failure"
            )
        } else {
            classifyArchiveReportResponse(
                status = httpResult.status,
                response = httpResult.response,
                retryAfterMillis = httpResult.retryAfterMillis,
                previousAttemptCount = batch.attemptCount,
                jitterFactor = archiveReportJitter()
            )
        }
        when (disposition) {
            ArchiveReportDisposition.Accepted -> {
                store.markArchiveReportAccepted(payload.requestId, now)
                Logger.d(TAG, "Archive report accepted count=${payload.urls.size} status=${httpResult?.status ?: 0} attempt=${batch.attemptCount}")
            }
            ArchiveReportDisposition.Split -> {
                if (payload.urls.size <= 1 || splitDepth >= MAX_SPLIT_DEPTH) {
                    store.markArchiveReportAbandoned(
                        payload.requestId,
                        now,
                        if (httpResult?.status == 413) "http_413:body_too_large" else "http_400:invalid_thread_url"
                    )
                    Logger.w(TAG, "Archive report abandoned single row status=${httpResult?.status ?: 0}")
                } else {
                    splitAndProcess(store, sender, batch, splitDepth, now)
                }
            }
            is ArchiveReportDisposition.Retry -> {
                store.markArchiveReportRetry(
                    payload.requestId,
                    safeNextAttemptAt(now, disposition.delayMillis),
                    disposition.errorCode
                )
                val nextAttempt = (batch.attemptCount.toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong())
                Logger.w(TAG, "Archive report deferred count=${payload.urls.size} status=${httpResult?.status ?: 0} attempt=$nextAttempt")
            }
            is ArchiveReportDisposition.Hold -> {
                store.markArchiveReportRetry(
                    payload.requestId,
                    safeNextAttemptAt(now, disposition.delayMillis),
                    disposition.errorCode
                )
                Logger.w(TAG, "Archive report held count=${payload.urls.size} status=${httpResult?.status ?: 0}")
            }
            is ArchiveReportDisposition.Abandon -> {
                store.markArchiveReportAbandoned(payload.requestId, now, disposition.errorCode)
            }
        }
    }

    private suspend fun splitAndProcess(
        store: CompatibilityStore,
        sender: ArchiveReportSender,
        original: ArchiveReportOutboxBatch,
        splitDepth: Int,
        now: Long
    ) {
        val rows = original.payload.threadIds.zip(original.payload.urls).map { (id, url) ->
            NormalizedArchiveThread(id, url)
        }
        val middle = rows.size / 2
        val first = buildArchiveReportPayload(newArchiveReportRequestId(), rows.subList(0, middle))
        val second = buildArchiveReportPayload(newArchiveReportRequestId(), rows.subList(middle, rows.size))
        if (first == null || second == null || !store.splitSendingArchiveReportBatch(
                original.payload.requestId,
                first,
                second,
                now
            )
        ) {
            store.markArchiveReportRetry(
                original.payload.requestId,
                safeNextAttemptAt(now, 60_000L),
                "split_transaction_failure"
            )
            return
        }
        // Both halves are assigned in one DB transaction before either request is sent.
        // Process both even if the first is deferred, so the second never remains sending.
        processBatch(store, sender, ArchiveReportOutboxBatch(first, original.attemptCount), splitDepth + 1)
        processBatch(store, sender, ArchiveReportOutboxBatch(second, original.attemptCount), splitDepth + 1)
    }

    private suspend fun scheduleNextDue(store: CompatibilityStore) {
        val next = store.archiveReportNextAttemptAt() ?: return
        if (next == Long.MAX_VALUE) return
        enqueueRetry(applicationContext, next)
    }

    companion object {
        private const val TAG = "ArchiveReportWorker"
        private const val WORK_TAG = "archive_report_upload"
        private const val DELAYED_WORK_NAME = "archive_report_delayed"
        private const val URGENT_WORK_NAME = "archive_report_urgent"
        private const val STARTUP_WORK_NAME = "archive_report_startup"
        private const val RETRY_WORK_NAME = "archive_report_retry"
        private const val MAX_BATCHES_PER_RUN = 8
        private const val MAX_SPLIT_DEPTH = 5

        fun enqueueAfterView(context: Context, sendableCount: Int) {
            enqueue(
                context = context,
                uniqueName = if (sendableCount >= 20) URGENT_WORK_NAME else DELAYED_WORK_NAME,
                delayMillis = if (sendableCount >= 20) 0L else ARCHIVE_REPORT_SEND_DELAY_MILLIS,
                policy = ExistingWorkPolicy.KEEP
            )
        }

        fun enqueueStartup(context: Context) {
            enqueue(context, STARTUP_WORK_NAME, 0L, ExistingWorkPolicy.KEEP)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }

        private fun enqueueRetry(context: Context, nextAttemptAt: Long) {
            enqueue(
                context,
                RETRY_WORK_NAME,
                (nextAttemptAt - System.currentTimeMillis()).coerceAtLeast(0L),
                ExistingWorkPolicy.APPEND_OR_REPLACE
            )
        }

        private fun enqueue(
            context: Context,
            uniqueName: String,
            delayMillis: Long,
            policy: ExistingWorkPolicy
        ) {
            val request = OneTimeWorkRequestBuilder<ArchiveReportWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, policy, request)
        }
    }
}

internal data class ArchiveReportHttpResult(
    val status: Int,
    val response: ArchiveReportResponse?,
    val retryAfterMillis: Long?
)

internal class ArchiveReportSender(
    private val client: HttpClient,
    private val endpoint: String = ARCHIVE_REPORT_ENDPOINT,
    private val allowTestEndpoint: Boolean = false
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun send(payload: ArchiveReportPayload, nowEpochMillis: Long): ArchiveReportHttpResult {
        check(
            allowTestEndpoint ||
                (endpoint == ARCHIVE_REPORT_ENDPOINT && endpoint.startsWith("https://api.inqueuet.com/"))
        ) {
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
            runCatching { json.decodeFromString<ArchiveReportResponse>(body.decodeToString()) }.getOrNull()
        }
        val retryAfter = parseRetryAfterMillis(response.headers[HttpHeaders.RetryAfter], nowEpochMillis)
            ?: decoded?.retryAfterSeconds?.coerceIn(0L, MAX_SERVER_RETRY_SECONDS)?.times(1_000L)
        return ArchiveReportHttpResult(response.status.value, decoded, retryAfter)
    }

    private suspend fun HttpResponse.readArchiveReportResponseBytes(): ByteArray? {
        val declared = contentLength()
        if (declared != null && declared > ARCHIVE_REPORT_MAX_RESPONSE_BYTES) {
            bodyAsChannel().cancel()
            return null
        }
        return withTimeout(ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS) {
            val channel = bodyAsChannel()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var zeroReadCount = 0
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) {
                    zeroReadCount += 1
                    if (zeroReadCount >= ARCHIVE_RESPONSE_MAX_ZERO_READS) return@withTimeout null
                    delay(ARCHIVE_RESPONSE_ZERO_READ_BACKOFF_MILLIS)
                    continue
                }
                zeroReadCount = 0
                if (read > ARCHIVE_REPORT_MAX_RESPONSE_BYTES - output.size()) {
                    channel.cancel()
                    return@withTimeout null
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun parseRetryAfterMillis(value: String?, nowEpochMillis: Long): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.let { seconds ->
            return seconds.coerceIn(0L, MAX_SERVER_RETRY_SECONDS) * 1_000L
        }
        return runCatching {
            val epochMillis = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
            (epochMillis - nowEpochMillis).coerceIn(0L, MAX_SERVER_RETRY_SECONDS * 1_000L)
        }.getOrNull()
    }

    private companion object {
        const val ARCHIVE_RESPONSE_MAX_ZERO_READS = 100
        const val ARCHIVE_RESPONSE_ZERO_READ_BACKOFF_MILLIS = 10L
        const val MAX_SERVER_RETRY_SECONDS = 7L * 24L * 60L * 60L
    }
}

internal fun createArchiveReportHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
        connectTimeoutMillis = ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
        socketTimeoutMillis = ARCHIVE_REPORT_HTTP_TIMEOUT_MILLIS
    }
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
        }
    }
}

private fun newArchiveReportRequestId(): String = UUID.randomUUID().toString()

private fun archiveReportJitter(): Double = Random.nextDouble(0.8, 1.2)

private fun safeNextAttemptAt(now: Long, delayMillis: Long): Long = when {
    delayMillis == ARCHIVE_REPORT_CONFIG_HOLD_MILLIS -> Long.MAX_VALUE
    delayMillis <= 0L -> now
    else -> saturatingEpochAdd(now, delayMillis)
}
