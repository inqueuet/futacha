package com.valoser.futacha.shared.ai

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.google.mlkit.genai.summarization.Summarizer
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val AICORE_PACKAGE = "com.google.android.aicore"
private const val AI_STATUS_TIMEOUT_MILLIS = 10_000L
private const val AI_SUMMARY_INFERENCE_TIMEOUT_MILLIS = 35_000L
private const val AI_POST_MODERATION_TIMEOUT_MILLIS = 20_000L
private const val AI_REMOTE_REQUEST_TIMEOUT_MILLIS = 45_000L
private const val AI_REMOTE_AVAILABILITY_TIMEOUT_MILLIS = 20_000L
private const val AI_REMOTE_DOWNLOAD_TIMEOUT_MILLIS = 15 * 60 * 1_000L

private const val MSG_SUMMARIZE_THREAD = 1
private const val MSG_CLASSIFY_POSTS = 2
private const val MSG_CHECK_AVAILABILITY = 3
private const val KEY_REQUEST_ID = "request_id"
private const val KEY_RESPONSE_FINAL = "response_final"
private const val KEY_START_DOWNLOAD = "start_download"
private const val KEY_THREAD_ID = "thread_id"
private const val KEY_THREAD_TITLE = "thread_title"
private const val KEY_POST_IDS = "post_ids"
private const val KEY_POST_SUBJECTS = "post_subjects"
private const val KEY_POST_MESSAGES = "post_messages"
private const val KEY_SUCCESS = "success"
private const val KEY_ERROR = "error"
private const val KEY_SUMMARY_HEADLINE = "summary_headline"
private const val KEY_SUMMARY_BULLETS = "summary_bullets"
private const val KEY_PROVIDER_LABEL = "provider_label"
private const val KEY_MODERATION_IDS = "moderation_ids"
private const val KEY_MODERATION_HIDE = "moderation_hide"
private const val KEY_MODERATION_REASONS = "moderation_reasons"
private const val KEY_MODERATION_CONFIDENCES = "moderation_confidences"
private const val KEY_AVAILABILITY_AVAILABLE = "availability_available"
private const val KEY_AVAILABILITY_REASON = "availability_reason"
private const val KEY_AVAILABILITY_SUMMARY = "availability_summary"
private const val KEY_AVAILABILITY_MODERATION = "availability_moderation"
private const val KEY_AVAILABILITY_PROVIDER = "availability_provider"
private const val KEY_AVAILABILITY_DOWNLOADING = "availability_downloading"
private const val KEY_AVAILABILITY_DOWNLOADED = "availability_downloaded"
private const val KEY_AVAILABILITY_TOTAL = "availability_total"

private const val AI_IPC_SUMMARY_MAX_POSTS = 80
private const val AI_IPC_MODERATION_MAX_POSTS = 24
private const val AI_IPC_MAX_MESSAGE_CHARS = 500

private val aiRequestIds = AtomicInteger(1)

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return AndroidOnDeviceAiService(platformContext as? Context)
}

private class AndroidOnDeviceAiService(
    private val context: Context?
) : OnDeviceAiService {
    override fun observeAvailability(): Flow<AiAvailability> = channelFlow {
        val appContext = context?.applicationContext
        val availability = appContext?.let {
            requestRemoteAvailability(
                context = it,
                startDownloadIfNeeded = true,
                progressChannel = this
            )
        }
        send(
            availability ?: AiAvailability(
                isAvailable = false,
                unavailableReason = "Gemini Nano の準備プロセスを起動できませんでした。",
                providerLabel = "Gemini Nano"
            )
        )
    }

    override suspend fun getAvailability(): AiAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return AiAvailability(
                isAvailable = false,
                unavailableReason = "Gemini Nano 要約には Android 8.0 以降が必要です。",
                providerLabel = "Gemini Nano"
            )
        }
        val appContext = context?.applicationContext ?: return AiAvailability(
            isAvailable = false,
            unavailableReason = "Android Context がないため端末AIを確認できません。",
            providerLabel = "Gemini Nano"
        )
        return requestRemoteAvailability(
            context = appContext,
            startDownloadIfNeeded = false,
            progressChannel = null
        ) ?: AiAvailability(
            isAvailable = false,
            unavailableReason = "Gemini Nano の準備プロセスを起動できませんでした。",
            providerLabel = "Gemini Nano"
        )
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        val appContext = context?.applicationContext
            ?: return Result.failure(IllegalStateException("Android Context is not available"))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(IllegalStateException("Android 8.0 or later is required"))
        }
        requestRemoteThreadSummary(appContext, input)?.let {
            return it
        }
        return Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        val appContext = context?.applicationContext
        if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestRemotePostModeration(appContext, input)?.let {
                return it
            }
        }
        return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
    }
}

class AndroidAiWorkerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incomingMessenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MSG_CHECK_AVAILABILITY -> {
                    val replyTo = message.replyTo
                    val requestId = message.data.getInt(KEY_REQUEST_ID)
                    val startDownloadIfNeeded = message.data.getBoolean(KEY_START_DOWNLOAD)
                    serviceScope.launch {
                        val result = performAvailabilityCheck(
                            appContext = applicationContext,
                            startDownloadIfNeeded = startDownloadIfNeeded,
                            onProgress = { availability ->
                                sendReply(replyTo, availability.toAvailabilityBundle(requestId, isFinal = false))
                            }
                        )
                        sendReply(replyTo, result.toAvailabilityBundle(requestId, isFinal = true))
                    }
                    true
                }
                MSG_SUMMARIZE_THREAD -> {
                    val request = message.data.toThreadSummaryInput()
                    val replyTo = message.replyTo
                    val requestId = message.data.getInt(KEY_REQUEST_ID)
                    serviceScope.launch {
                        val result = performLocalThreadSummary(applicationContext, request)
                        sendReply(replyTo, result.toSummaryBundle(requestId))
                    }
                    true
                }
                MSG_CLASSIFY_POSTS -> {
                    val request = message.data.toPostModerationInput()
                    val replyTo = message.replyTo
                    val requestId = message.data.getInt(KEY_REQUEST_ID)
                    serviceScope.launch {
                        val result = performLocalPostModeration(request)
                        sendReply(replyTo, result.toModerationBundle(requestId))
                    }
                    true
                }
                else -> false
            }
        }
    )

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun sendReply(replyTo: Messenger?, bundle: Bundle) {
        if (replyTo == null) return
        runCatching {
            val reply = Message.obtain()
            reply.data = bundle
            replyTo.send(reply)
        }
    }
}

private suspend fun requestRemoteThreadSummary(
    context: Context,
    input: ThreadSummaryInput
): Result<ThreadSummary>? {
    val bundle = Bundle().apply {
        putInt(KEY_REQUEST_ID, aiRequestIds.getAndIncrement())
        putString(KEY_THREAD_ID, input.threadId)
        putString(KEY_THREAD_TITLE, input.title)
        putPosts(input.posts.take(AI_IPC_SUMMARY_MAX_POSTS))
    }
    val response = requestRemoteAi(
        context = context,
        what = MSG_SUMMARIZE_THREAD,
        data = bundle,
        timeoutMillis = AI_REMOTE_REQUEST_TIMEOUT_MILLIS
    ) ?: return null

    if (!response.getBoolean(KEY_SUCCESS)) {
        return Result.failure(IllegalStateException(response.getString(KEY_ERROR) ?: "Gemini Nano 要約に失敗しました。"))
    }
    return Result.success(
        ThreadSummary(
            headline = response.getString(KEY_SUMMARY_HEADLINE).orEmpty(),
            bullets = response.getStringArrayList(KEY_SUMMARY_BULLETS).orEmpty(),
            providerLabel = response.getString(KEY_PROVIDER_LABEL) ?: "Gemini Nano"
        )
    )
}

private suspend fun requestRemoteAvailability(
    context: Context,
    startDownloadIfNeeded: Boolean,
    progressChannel: SendChannel<AiAvailability>?
): AiAvailability? {
    val bundle = Bundle().apply {
        putInt(KEY_REQUEST_ID, aiRequestIds.getAndIncrement())
        putBoolean(KEY_START_DOWNLOAD, startDownloadIfNeeded)
    }
    val response = requestRemoteAi(
        context = context,
        what = MSG_CHECK_AVAILABILITY,
        data = bundle,
        timeoutMillis = if (startDownloadIfNeeded) {
            AI_REMOTE_DOWNLOAD_TIMEOUT_MILLIS
        } else {
            AI_REMOTE_AVAILABILITY_TIMEOUT_MILLIS
        },
        onProgress = { progress ->
            progress.toAiAvailability()?.let { progressChannel?.trySend(it) }
        }
    ) ?: return null
    return response.toAiAvailability()
}

private suspend fun requestRemotePostModeration(
    context: Context,
    input: PostModerationInput
): Result<List<PostModerationResult>>? {
    val bundle = Bundle().apply {
        putInt(KEY_REQUEST_ID, aiRequestIds.getAndIncrement())
        putString(KEY_THREAD_ID, input.threadId)
        putPosts(input.posts.take(AI_IPC_MODERATION_MAX_POSTS))
    }
    val response = requestRemoteAi(
        context = context,
        what = MSG_CLASSIFY_POSTS,
        data = bundle,
        timeoutMillis = AI_REMOTE_REQUEST_TIMEOUT_MILLIS
    ) ?: return null

    if (!response.getBoolean(KEY_SUCCESS)) {
        return Result.failure(IllegalStateException(response.getString(KEY_ERROR) ?: "Gemini Nano 荒らし判定に失敗しました。"))
    }

    val ids = response.getStringArrayList(KEY_MODERATION_IDS).orEmpty()
    val hideFlags = response.getBooleanArray(KEY_MODERATION_HIDE) ?: BooleanArray(ids.size)
    val reasons = response.getStringArrayList(KEY_MODERATION_REASONS).orEmpty()
    val confidences = response.getFloatArray(KEY_MODERATION_CONFIDENCES) ?: FloatArray(ids.size)
    return Result.success(
        ids.mapIndexed { index, postId ->
            PostModerationResult(
                postId = postId,
                shouldHide = hideFlags.getOrElse(index) { false },
                reason = reasons.getOrNull(index)?.takeIf { it.isNotBlank() },
                confidence = confidences.getOrElse(index) { 0f }
            )
        }
    )
}

private suspend fun requestRemoteAi(
    context: Context,
    what: Int,
    data: Bundle,
    timeoutMillis: Long,
    onProgress: ((Bundle) -> Unit)? = null
): Bundle? {
    return withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val appContext = context.applicationContext
            val completed = AtomicBoolean(false)
            var connection: ServiceConnection? = null

            fun finish(bundle: Bundle?) {
                if (!completed.compareAndSet(false, true)) return
                connection?.let {
                    runCatching { appContext.unbindService(it) }
                }
                continuation.resume(bundle)
            }

            val requestId = data.getInt(KEY_REQUEST_ID)
            val replyMessenger = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    if (message.data.getInt(KEY_REQUEST_ID) != requestId) {
                        return@Handler false
                    }
                    if (!message.data.getBoolean(KEY_RESPONSE_FINAL, true)) {
                        onProgress?.invoke(message.data)
                        return@Handler true
                    }
                    finish(message.data)
                    true
                }
            )

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val remote = Messenger(service)
                    val request = Message.obtain(null, what).apply {
                        this.data = data
                        replyTo = replyMessenger
                    }
                    try {
                        remote.send(request)
                    } catch (_: RemoteException) {
                        finish(null)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    finish(null)
                }

                override fun onBindingDied(name: ComponentName?) {
                    finish(null)
                }

                override fun onNullBinding(name: ComponentName?) {
                    finish(null)
                }
            }

            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) {
                    connection?.let {
                        runCatching { appContext.unbindService(it) }
                    }
                }
            }

            val intent = Intent(appContext, AndroidAiWorkerService::class.java)
            val bound = runCatching {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!bound) {
                finish(null)
            }
        }
    }
}

private fun Bundle.putPosts(posts: List<Post>) {
    putStringArrayList(KEY_POST_IDS, ArrayList(posts.map { it.id }))
    putStringArrayList(KEY_POST_SUBJECTS, ArrayList(posts.map { it.subject.orEmpty() }))
    putStringArrayList(
        KEY_POST_MESSAGES,
        ArrayList(posts.map { it.messageHtml.take(AI_IPC_MAX_MESSAGE_CHARS) })
    )
}

private fun Bundle.toThreadSummaryInput(): ThreadSummaryInput {
    return ThreadSummaryInput(
        threadId = getString(KEY_THREAD_ID).orEmpty(),
        title = getString(KEY_THREAD_TITLE),
        posts = toPosts()
    )
}

private fun Bundle.toPostModerationInput(): PostModerationInput {
    return PostModerationInput(
        threadId = getString(KEY_THREAD_ID).orEmpty(),
        posts = toPosts()
    )
}

private fun Bundle.toPosts(): List<Post> {
    val ids = getStringArrayList(KEY_POST_IDS).orEmpty()
    val subjects = getStringArrayList(KEY_POST_SUBJECTS).orEmpty()
    val messages = getStringArrayList(KEY_POST_MESSAGES).orEmpty()
    return ids.mapIndexed { index, id ->
        Post(
            id = id,
            order = index + 1,
            author = null,
            subject = subjects.getOrNull(index)?.takeIf { it.isNotBlank() },
            timestamp = "",
            messageHtml = messages.getOrNull(index).orEmpty(),
            imageUrl = null,
            thumbnailUrl = null
        )
    }
}

private fun Result<ThreadSummary>.toSummaryBundle(requestId: Int): Bundle {
    return fold(
        onSuccess = { summary ->
            Bundle().apply {
                putInt(KEY_REQUEST_ID, requestId)
                putBoolean(KEY_SUCCESS, true)
                putString(KEY_SUMMARY_HEADLINE, summary.headline)
                putStringArrayList(KEY_SUMMARY_BULLETS, ArrayList(summary.bullets))
                putString(KEY_PROVIDER_LABEL, summary.providerLabel)
            }
        },
        onFailure = { error ->
            Bundle().apply {
                putInt(KEY_REQUEST_ID, requestId)
                putBoolean(KEY_SUCCESS, false)
                putString(KEY_ERROR, error.message)
            }
        }
    )
}

private fun Result<List<PostModerationResult>>.toModerationBundle(requestId: Int): Bundle {
    return fold(
        onSuccess = { results ->
            Bundle().apply {
                putInt(KEY_REQUEST_ID, requestId)
                putBoolean(KEY_SUCCESS, true)
                putStringArrayList(KEY_MODERATION_IDS, ArrayList(results.map { it.postId }))
                putBooleanArray(KEY_MODERATION_HIDE, results.map { it.shouldHide }.toBooleanArray())
                putStringArrayList(KEY_MODERATION_REASONS, ArrayList(results.map { it.reason.orEmpty() }))
                putFloatArray(KEY_MODERATION_CONFIDENCES, results.map { it.confidence }.toFloatArray())
            }
        },
        onFailure = { error ->
            Bundle().apply {
                putInt(KEY_REQUEST_ID, requestId)
                putBoolean(KEY_SUCCESS, false)
                putString(KEY_ERROR, error.message)
            }
        }
    )
}

private fun AiAvailability.toAvailabilityBundle(requestId: Int, isFinal: Boolean): Bundle {
    return Bundle().apply {
        putInt(KEY_REQUEST_ID, requestId)
        putBoolean(KEY_RESPONSE_FINAL, isFinal)
        putBoolean(KEY_AVAILABILITY_AVAILABLE, isAvailable)
        putString(KEY_AVAILABILITY_REASON, unavailableReason)
        putBoolean(KEY_AVAILABILITY_SUMMARY, supportsThreadSummary)
        putBoolean(KEY_AVAILABILITY_MODERATION, supportsPostModeration)
        putString(KEY_AVAILABILITY_PROVIDER, providerLabel)
        putBoolean(KEY_AVAILABILITY_DOWNLOADING, isDownloadInProgress)
        downloadedBytes?.let { putLong(KEY_AVAILABILITY_DOWNLOADED, it) }
        downloadTotalBytes?.let { putLong(KEY_AVAILABILITY_TOTAL, it) }
    }
}

private fun Bundle.toAiAvailability(): AiAvailability? {
    if (!containsKey(KEY_AVAILABILITY_AVAILABLE)) return null
    return AiAvailability(
        isAvailable = getBoolean(KEY_AVAILABILITY_AVAILABLE),
        unavailableReason = getString(KEY_AVAILABILITY_REASON),
        supportsThreadSummary = getBoolean(KEY_AVAILABILITY_SUMMARY),
        supportsPostModeration = getBoolean(KEY_AVAILABILITY_MODERATION),
        providerLabel = getString(KEY_AVAILABILITY_PROVIDER) ?: "Gemini Nano",
        isDownloadInProgress = getBoolean(KEY_AVAILABILITY_DOWNLOADING),
        downloadedBytes = if (containsKey(KEY_AVAILABILITY_DOWNLOADED)) {
            getLong(KEY_AVAILABILITY_DOWNLOADED)
        } else {
            null
        },
        downloadTotalBytes = if (containsKey(KEY_AVAILABILITY_TOTAL)) {
            getLong(KEY_AVAILABILITY_TOTAL)
        } else {
            null
        }
    )
}

private suspend fun performAvailabilityCheck(
    appContext: Context,
    startDownloadIfNeeded: Boolean,
    onProgress: ((AiAvailability) -> Unit)?
): AiAvailability {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return AiAvailability(
            isAvailable = false,
            unavailableReason = "Gemini Nano 要約には Android 8.0 以降が必要です。",
            providerLabel = "Gemini Nano"
        )
    }
    val hasAiCore = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
    }.isSuccess

    if (!hasAiCore) {
        return AiAvailability(
            isAvailable = false,
            unavailableReason = "Gemini Nano / AICore がこの端末で確認できません。",
            providerLabel = "Gemini Nano"
        )
    }
    return withContext(AppDispatchers.io) {
        val summarizer = runCatching {
            Summarization.getClient(buildSummarizerOptions(appContext))
        }.getOrElse { error ->
            return@withContext AiAvailability(
                isAvailable = false,
                unavailableReason = error.message ?: "Gemini Nano 要約の可用性を確認できませんでした。",
                providerLabel = "Gemini Nano"
            )
        }
        try {
            val summaryStatus = summarizer.checkFeatureStatus().awaitOrNull(AI_STATUS_TIMEOUT_MILLIS)
            when (summaryStatus) {
                FeatureStatus.AVAILABLE -> {
                    val promptStatus = checkPromptStatus()
                    if (
                        startDownloadIfNeeded &&
                        onProgress != null &&
                        (promptStatus == FeatureStatus.DOWNLOADABLE || promptStatus == FeatureStatus.DOWNLOADING)
                    ) {
                        downloadPromptFeature(onProgress)
                        val updatedPromptStatus = checkPromptStatus()
                        AiAvailability(
                            isAvailable = true,
                            supportsThreadSummary = true,
                            supportsPostModeration = updatedPromptStatus == FeatureStatus.AVAILABLE,
                            providerLabel = "Gemini Nano"
                        )
                    } else {
                        AiAvailability(
                            isAvailable = true,
                            supportsThreadSummary = true,
                            supportsPostModeration = promptStatus == FeatureStatus.AVAILABLE,
                            providerLabel = "Gemini Nano"
                        )
                    }
                }
                FeatureStatus.DOWNLOADABLE,
                FeatureStatus.DOWNLOADING -> {
                    if (startDownloadIfNeeded && onProgress != null) {
                        downloadSummarizerFeature(
                            summarizer = summarizer,
                            onProgress = onProgress
                        )
                        val updatedSummaryStatus = summarizer.checkFeatureStatus().awaitOrNull(AI_STATUS_TIMEOUT_MILLIS)
                        if (updatedSummaryStatus == FeatureStatus.AVAILABLE) {
                            val updatedPromptStatus = checkPromptStatus()
                            if (
                                updatedPromptStatus == FeatureStatus.DOWNLOADABLE ||
                                updatedPromptStatus == FeatureStatus.DOWNLOADING
                            ) {
                                downloadPromptFeature(onProgress)
                            }
                            val finalPromptStatus = checkPromptStatus()
                            AiAvailability(
                                isAvailable = true,
                                supportsThreadSummary = true,
                                supportsPostModeration = finalPromptStatus == FeatureStatus.AVAILABLE,
                                providerLabel = "Gemini Nano"
                            )
                        } else {
                            AiAvailability(
                                isAvailable = false,
                                unavailableReason = "Gemini Nano のモデルをダウンロード中です。完了後に利用できます。",
                                providerLabel = "Gemini Nano",
                                isDownloadInProgress = updatedSummaryStatus == FeatureStatus.DOWNLOADING
                            )
                        }
                    } else {
                        AiAvailability(
                            isAvailable = false,
                            unavailableReason = if (summaryStatus == FeatureStatus.DOWNLOADING) {
                                "Gemini Nano のモデルをダウンロード中です。完了後に利用できます。"
                            } else {
                                "Gemini Nano のモデルを準備中です。しばらくしてから再確認してください。"
                            },
                            providerLabel = "Gemini Nano",
                            isDownloadInProgress = summaryStatus == FeatureStatus.DOWNLOADING
                        )
                    }
                }
                else -> AiAvailability(
                    isAvailable = false,
                    unavailableReason = "この端末構成では Gemini Nano 要約を利用できません。",
                    providerLabel = "Gemini Nano"
                )
            }
        } catch (error: Throwable) {
            AiAvailability(
                isAvailable = false,
                unavailableReason = error.message ?: "Gemini Nano 要約の可用性を確認できませんでした。",
                providerLabel = "Gemini Nano"
            )
        } finally {
            summarizer.close()
        }
    }
}

private suspend fun performLocalThreadSummary(
    appContext: Context,
    input: ThreadSummaryInput
): Result<ThreadSummary> {
        val sourceText = buildThreadSummarySourceText(input)
        if (sourceText.isBlank()) {
            return Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
        }
        return withContext(AppDispatchers.io) {
            val summarizer = runCatching {
                Summarization.getClient(buildSummarizerOptions(appContext))
            }.getOrElse {
                return@withContext Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
            }
            try {
                val status = summarizer.checkFeatureStatus().awaitOrNull(AI_STATUS_TIMEOUT_MILLIS)
                if (status != FeatureStatus.AVAILABLE) {
                    return@withContext Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
                }
                val request = SummarizationRequest.builder(sourceText).build()
                val result = summarizer.runInference(request)
                    .awaitOrNull(AI_SUMMARY_INFERENCE_TIMEOUT_MILLIS)
                    ?: return@withContext Result.success(
                        buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano")
                    )
                Result.success(
                    parseGeneratedThreadSummary(
                        input = input,
                        generatedText = result.getSummary(),
                        providerLabel = "Gemini Nano",
                        generatedTextHasHeadline = false
                    )
                )
            } catch (error: Throwable) {
                Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
            } finally {
                summarizer.close()
            }
        }
}

private suspend fun performLocalPostModeration(
    input: PostModerationInput
): Result<List<PostModerationResult>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || input.posts.isEmpty()) {
            return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
        }
        val sourceText = buildPostModerationSourceText(input)
        if (sourceText.isBlank()) {
            return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
        }
        return withContext(AppDispatchers.io) {
            val promptModel = runCatching {
                Generation.getClient()
            }.getOrElse {
                return@withContext Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
            }
            try {
                val status = withTimeoutOrNull(AI_STATUS_TIMEOUT_MILLIS) {
                    promptModel.checkStatus()
                }
                if (status != FeatureStatus.AVAILABLE) {
                    return@withContext Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
                }
                val response = withTimeoutOrNull(AI_POST_MODERATION_TIMEOUT_MILLIS) {
                    promptModel.generateContent(buildPostModerationPrompt(sourceText))
                } ?: return@withContext Result.success(
                    input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) }
                )
                val responseText = response.candidates.firstOrNull()?.text.orEmpty()
                val detected = parsePostModerationResponse(responseText)
                Result.success(
                    input.posts.map { post ->
                        detected[post.id] ?: PostModerationResult(postId = post.id, shouldHide = false)
                    }
                )
            } catch (error: Throwable) {
                Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
            } finally {
                promptModel.close()
            }
        }
}

private suspend fun <T> ListenableFuture<T>.awaitOrNull(timeoutMillis: Long): T? {
    return withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    runCatching { get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                MoreExecutors.directExecutor()
            )
            continuation.invokeOnCancellation {
                cancel(true)
            }
        }
    }
}

private fun downloadSummarizerFeature(
    summarizer: Summarizer,
    onProgress: (AiAvailability) -> Unit
) {
    var totalBytesExpected: Long? = null
    val callback = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) {
            totalBytesExpected = bytesToDownload
            onProgress(
                buildGeminiNanoDownloadAvailability(
                    modelLabel = "要約モデル",
                    downloadedBytes = 0L,
                    downloadTotalBytes = bytesToDownload
                )
            )
        }

        override fun onDownloadProgress(totalBytesDownloaded: Long) {
            onProgress(
                buildGeminiNanoDownloadAvailability(
                    modelLabel = "要約モデル",
                    downloadedBytes = totalBytesDownloaded,
                    downloadTotalBytes = totalBytesExpected
                )
            )
        }

        override fun onDownloadCompleted() {
            onProgress(
                AiAvailability(
                    isAvailable = false,
                    unavailableReason = "Gemini Nano の要約モデルを確認中です。",
                    providerLabel = "Gemini Nano"
                )
            )
        }

        override fun onDownloadFailed(error: GenAiException) {
            onProgress(
                AiAvailability(
                    isAvailable = false,
                    unavailableReason = error.message ?: "Gemini Nano の要約モデルダウンロードに失敗しました。",
                    providerLabel = "Gemini Nano"
                )
            )
        }
    }
    summarizer.downloadFeature(callback).get()
}

private suspend fun checkPromptStatus(): Int? {
    return runCatching {
        val promptModel = Generation.getClient()
        try {
            promptModel.checkStatus()
        } finally {
            promptModel.close()
        }
    }.getOrNull()
}

private suspend fun downloadPromptFeature(onProgress: (AiAvailability) -> Unit) {
    val promptModel = Generation.getClient()
    try {
        collectPromptDownloadProgress(promptModel, onProgress)
    } finally {
        promptModel.close()
    }
}

private suspend fun collectPromptDownloadProgress(
    promptModel: GenerativeModel,
    onProgress: (AiAvailability) -> Unit
) {
    var totalBytesExpected: Long? = null
    promptModel.download().collect { status ->
        when (status) {
            is DownloadStatus.DownloadStarted -> {
                totalBytesExpected = status.bytesToDownload
                onProgress(
                    buildGeminiNanoDownloadAvailability(
                        modelLabel = "荒らし判定モデル",
                        downloadedBytes = 0L,
                        downloadTotalBytes = totalBytesExpected,
                        supportsThreadSummary = true
                    )
                )
            }
            is DownloadStatus.DownloadProgress -> {
                onProgress(
                    buildGeminiNanoDownloadAvailability(
                        modelLabel = "荒らし判定モデル",
                        downloadedBytes = status.totalBytesDownloaded,
                        downloadTotalBytes = totalBytesExpected,
                        supportsThreadSummary = true
                    )
                )
            }
            DownloadStatus.DownloadCompleted -> {
                onProgress(
                    AiAvailability(
                        isAvailable = true,
                        supportsThreadSummary = true,
                        supportsPostModeration = false,
                        unavailableReason = "Gemini Nano の荒らし判定モデルを確認中です。",
                        providerLabel = "Gemini Nano"
                    )
                )
            }
            is DownloadStatus.DownloadFailed -> {
                onProgress(
                    AiAvailability(
                        isAvailable = true,
                        supportsThreadSummary = true,
                        supportsPostModeration = false,
                        unavailableReason = status.e.message ?: "Gemini Nano の荒らし判定モデルダウンロードに失敗しました。",
                        providerLabel = "Gemini Nano"
                    )
                )
            }
        }
    }
}

private fun buildGeminiNanoDownloadAvailability(
    modelLabel: String,
    downloadedBytes: Long?,
    downloadTotalBytes: Long?,
    supportsThreadSummary: Boolean = false
): AiAvailability {
    val progressText = formatGeminiNanoDownloadProgress(downloadedBytes, downloadTotalBytes)
    return AiAvailability(
        isAvailable = supportsThreadSummary,
        supportsThreadSummary = supportsThreadSummary,
        unavailableReason = if (progressText == null) {
            "Gemini Nano の$modelLabel をダウンロード中です。"
        } else {
            "Gemini Nano の$modelLabel をダウンロード中です。$progressText"
        },
        providerLabel = "Gemini Nano",
        isDownloadInProgress = true,
        downloadedBytes = downloadedBytes,
        downloadTotalBytes = downloadTotalBytes
    )
}

private fun formatGeminiNanoDownloadProgress(
    downloadedBytes: Long?,
    downloadTotalBytes: Long?
): String? {
    val downloaded = downloadedBytes ?: return null
    val total = downloadTotalBytes ?: return null
    if (total <= 0L) return null
    val percent = ((downloaded.toDouble() / total.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
    return "$percent%"
}

private fun buildSummarizerOptions(context: Context): SummarizerOptions {
    return SummarizerOptions.builder(context)
        .setInputType(SummarizerOptions.InputType.CONVERSATION)
        .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
        .setLanguage(SummarizerOptions.Language.JAPANESE)
        .build()
}

private fun buildPostModerationPrompt(posts: String): String {
    return """
        You are a local moderation assistant for a Japanese imageboard thread.
        Hide only posts that are clearly disruptive spam, harassment, repeated flooding, threats, or unrelated vandalism.
        Do not hide ordinary disagreement, jokes, criticism, short replies, or quoted text.
        Do not hide the opening post. Prefer hiding no posts when uncertain. Return at most 8 hidden posts.
        Return one line per hidden post only.
        Format exactly: postId<TAB>HIDE<TAB>short Japanese reason
        Posts:
        $posts
    """.trimIndent()
}
