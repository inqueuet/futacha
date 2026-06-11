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
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val AICORE_PACKAGE = "com.google.android.aicore"
private const val AI_STATUS_TIMEOUT_MILLIS = 10_000L
private const val AI_SUMMARY_INFERENCE_TIMEOUT_MILLIS = 35_000L
private const val AI_POST_MODERATION_TIMEOUT_MILLIS = 20_000L
private const val AI_REMOTE_REQUEST_TIMEOUT_MILLIS = 180_000L
private const val AI_REMOTE_AVAILABILITY_TIMEOUT_MILLIS = 90_000L
private const val AI_REMOTE_DOWNLOAD_TIMEOUT_MILLIS = 15 * 60 * 1_000L
private const val AI_REMOTE_SERVICE_IDLE_UNBIND_MILLIS = 10_000L

private const val MSG_SUMMARIZE_THREAD = 1
private const val MSG_CLASSIFY_POSTS = 2
private const val MSG_CHECK_AVAILABILITY = 3
private const val MSG_CANCEL_REQUEST = 4
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
private const val AI_IPC_MODERATION_MAX_POSTS_PER_REQUEST = 256
private const val AI_IPC_MAX_MESSAGE_CHARS = 500
private const val AI_IPC_MODERATION_MAX_TOTAL_MESSAGE_CHARS = 120_000
private const val AI_IPC_SUMMARY_MAX_MESSAGE_CHARS = 10_000
private const val AI_IPC_SUMMARY_MAX_TOTAL_MESSAGE_CHARS = 10_000

private val aiRequestIds = AtomicInteger(1)
private val aiRemoteServiceSession = AndroidAiRemoteServiceSession()

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return AndroidOnDeviceAiService(platformContext as? Context)
}

private class AndroidOnDeviceAiService(
    private val context: Context?
) : OnDeviceAiService {
    override fun observeAvailability(): Flow<AiAvailability> = channelFlow {
        val appContext = context?.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            send(
                AiAvailability(
                    isAvailable = false,
                    unavailableReason = "Gemini Nano 要約には Android 8.0 以降が必要です。",
                    providerLabel = "Gemini Nano"
                )
            )
            return@channelFlow
        }
        if (appContext == null) {
            send(
                AiAvailability(
                    isAvailable = false,
                    unavailableReason = "Android Context がないため端末AIを確認できません。",
                    providerLabel = "Gemini Nano"
                )
            )
            return@channelFlow
        }
        send(
            AiAvailability(
                isAvailable = false,
                unavailableReason = "Gemini Nano の状態を確認中です。",
                providerLabel = "Gemini Nano"
            )
        )

        val availability = requestRemoteAvailability(
            context = appContext,
            startDownloadIfNeeded = false,
            progressChannel = this
        )
        if (availability == null) {
            send(
                AiAvailability(
                    isAvailable = false,
                    unavailableReason = "Gemini Nano のモデル準備がタイムアウトしました。",
                    providerLabel = "Gemini Nano"
                )
            )
            return@channelFlow
        }
        send(availability)
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
            unavailableReason = "Gemini Nano の状態確認がタイムアウトしました。",
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

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> = withContext(AppDispatchers.io) {
        val appContext = context?.applicationContext
        if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestRemotePostModeration(appContext, input)?.let {
                return@withContext it
            }
        }
        Result.success(emptyList())
    }
}

class AndroidAiWorkerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestJobs = ConcurrentHashMap<Int, Job>()
    private val incomingMessenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MSG_CHECK_AVAILABILITY -> {
                    val replyTo = message.replyTo
                    val requestId = message.data.getInt(KEY_REQUEST_ID)
                    val startDownloadIfNeeded = message.data.getBoolean(KEY_START_DOWNLOAD)
                    launchRequest(requestId) {
                        sendReply(
                            replyTo,
                            AiAvailability(
                                isAvailable = false,
                                unavailableReason = "Gemini Nano / AICore を起動中です。",
                                providerLabel = "Gemini Nano"
                            ).toAvailabilityBundle(requestId, isFinal = false)
                        )
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
                    launchRequest(requestId) {
                        val result = performLocalThreadSummary(applicationContext, request)
                        sendReply(replyTo, result.toSummaryBundle(requestId))
                    }
                    true
                }
                MSG_CLASSIFY_POSTS -> {
                    val request = message.data.toPostModerationInput()
                    val replyTo = message.replyTo
                    val requestId = message.data.getInt(KEY_REQUEST_ID)
                    launchRequest(requestId) {
                        val result = performLocalPostModeration(request)
                        sendReply(replyTo, result.toModerationBundle(requestId))
                    }
                    true
                }
                MSG_CANCEL_REQUEST -> {
                    cancelRequest(message.data.getInt(KEY_REQUEST_ID))
                    true
                }
                else -> false
            }
        }
    )

    override fun onCreate() {
        super.onCreate()
        MlKitContext.initializeIfNeeded(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    override fun onDestroy() {
        requestJobs.values.forEach { it.cancel() }
        requestJobs.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun launchRequest(requestId: Int, block: suspend () -> Unit) {
        requestJobs.remove(requestId)?.cancel()
        val job = serviceScope.launch {
            try {
                block()
            } finally {
                requestJobs.remove(requestId)
            }
        }
        requestJobs[requestId] = job
        if (job.isCompleted) {
            requestJobs.remove(requestId, job)
        }
    }

    private fun cancelRequest(requestId: Int) {
        requestJobs.remove(requestId)?.cancel()
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
        putPosts(
            posts = input.posts.take(AI_IPC_SUMMARY_MAX_POSTS),
            maxMessageChars = AI_IPC_SUMMARY_MAX_MESSAGE_CHARS,
            maxTotalMessageChars = AI_IPC_SUMMARY_MAX_TOTAL_MESSAGE_CHARS
        )
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
    val postChunks = chunkModerationIpcPosts(input.posts)
    if (postChunks.isEmpty()) {
        return Result.success(emptyList())
    }
    val mergedResults = linkedMapOf<String, PostModerationResult>()
    for (posts in postChunks) {
        val chunkResult = requestRemotePostModerationChunk(
            context = context,
            threadId = input.threadId,
            posts = posts
        ) ?: return null
        chunkResult.getOrElse { return Result.failure(it) }
            .forEach { result ->
                mergedResults[result.postId] = result
            }
    }
    return Result.success(mergedResults.values.toList())
}

private suspend fun requestRemotePostModerationChunk(
    context: Context,
    threadId: String,
    posts: List<Post>
): Result<List<PostModerationResult>>? {
    val bundle = Bundle().apply {
        putInt(KEY_REQUEST_ID, aiRequestIds.getAndIncrement())
        putString(KEY_THREAD_ID, threadId)
        putPosts(
            posts = posts,
            maxMessageChars = AI_IPC_MAX_MESSAGE_CHARS,
            maxTotalMessageChars = AI_IPC_MODERATION_MAX_TOTAL_MESSAGE_CHARS
        )
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

private fun chunkModerationIpcPosts(posts: List<Post>): List<List<Post>> {
    if (posts.isEmpty()) return emptyList()
    val chunks = mutableListOf<List<Post>>()
    val current = mutableListOf<Post>()
    var currentChars = 0
    posts.forEach { post ->
        val messageChars = minOf(post.messageHtml.length, AI_IPC_MAX_MESSAGE_CHARS)
        val exceedsPostLimit = current.size >= AI_IPC_MODERATION_MAX_POSTS_PER_REQUEST
        val exceedsCharLimit = current.isNotEmpty() &&
            currentChars + messageChars > AI_IPC_MODERATION_MAX_TOTAL_MESSAGE_CHARS
        if (exceedsPostLimit || exceedsCharLimit) {
            chunks += current.toList()
            current.clear()
            currentChars = 0
        }
        current += post
        currentChars += messageChars
    }
    if (current.isNotEmpty()) {
        chunks += current.toList()
    }
    return chunks
}

private suspend fun requestRemoteAi(
    context: Context,
    what: Int,
    data: Bundle,
    timeoutMillis: Long,
    onProgress: ((Bundle) -> Unit)? = null
): Bundle? {
    return withTimeoutOrNull(timeoutMillis) {
        aiRemoteServiceSession.request(
            context = context,
            what = what,
            data = data,
            onProgress = onProgress
        )
    }
}

private class AndroidAiRemoteServiceSession {
    private val requestMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private var remoteMessenger: Messenger? = null
    private var isBinding = false
    private var pendingBindReady: ((Messenger?) -> Unit)? = null
    private var activeDisconnectHandler: (() -> Unit)? = null
    private var idleUnbindRunnable: Runnable? = null

    suspend fun request(
        context: Context,
        what: Int,
        data: Bundle,
        onProgress: ((Bundle) -> Unit)?
    ): Bundle? = requestMutex.withLock {
        suspendCancellableCoroutine { continuation ->
            val appContext = context.applicationContext
            val requestId = data.getInt(KEY_REQUEST_ID)
            val completed = AtomicBoolean(false)

            lateinit var handleDisconnect: () -> Unit

            fun sendCancelToWorker() {
                val cancel = Message.obtain(null, MSG_CANCEL_REQUEST).apply {
                    this.data = Bundle().apply {
                        putInt(KEY_REQUEST_ID, requestId)
                    }
                }
                mainHandler.post {
                    runCatching {
                        remoteMessenger?.send(cancel)
                    }
                }
            }

            fun finish(bundle: Bundle?) {
                if (!completed.compareAndSet(false, true)) return
                mainHandler.post {
                    if (activeDisconnectHandler === handleDisconnect) {
                        activeDisconnectHandler = null
                    }
                    scheduleIdleUnbind(appContext)
                }
                continuation.resume(bundle)
            }

            val replyMessenger = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    if (message.data.getInt(KEY_REQUEST_ID) != requestId) {
                        return@Handler false
                    }
                    if (completed.get()) {
                        return@Handler true
                    }
                    if (!message.data.getBoolean(KEY_RESPONSE_FINAL, true)) {
                        onProgress?.invoke(message.data)
                        return@Handler true
                    }
                    finish(message.data)
                    true
                }
            )

            fun sendRequest(remote: Messenger) {
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

            handleDisconnect = {
                finish(null)
            }

            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) {
                    sendCancelToWorker()
                    mainHandler.post {
                        if (activeDisconnectHandler === handleDisconnect) {
                            activeDisconnectHandler = null
                        }
                        scheduleIdleUnbind(appContext)
                    }
                }
            }

            mainHandler.post {
                if (completed.get()) {
                    return@post
                }
                cancelIdleUnbind()
                activeDisconnectHandler = handleDisconnect
                val existingRemote = remoteMessenger
                if (existingRemote != null) {
                    sendRequest(existingRemote)
                    return@post
                }
                bind(appContext) { remote ->
                    if (completed.get()) {
                        // The coroutine was cancelled while Android was binding the service.
                    } else if (remote == null) {
                        finish(null)
                    } else {
                        sendRequest(remote)
                    }
                }
            }
        }
    }

    private fun bind(appContext: Context, onReady: (Messenger?) -> Unit) {
        pendingBindReady = onReady
        if (isBinding) return
        val serviceConnection = connection ?: createConnection().also {
            connection = it
        }
        isBinding = true
        val intent = Intent(appContext, AndroidAiWorkerService::class.java)
        val bound = runCatching {
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) {
            isBinding = false
            pendingBindReady = null
            onReady(null)
        }
    }

    private fun createConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isBinding = false
                remoteMessenger = service?.let(::Messenger)
                val onReady = pendingBindReady
                pendingBindReady = null
                onReady?.invoke(remoteMessenger)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                clearRemoteConnection()
                activeDisconnectHandler?.invoke()
            }

            override fun onBindingDied(name: ComponentName?) {
                clearRemoteConnection()
                activeDisconnectHandler?.invoke()
            }

            override fun onNullBinding(name: ComponentName?) {
                clearRemoteConnection()
                activeDisconnectHandler?.invoke()
            }
        }
    }

    private fun clearRemoteConnection() {
        isBinding = false
        remoteMessenger = null
        pendingBindReady?.invoke(null)
        pendingBindReady = null
    }

    private fun cancelIdleUnbind() {
        idleUnbindRunnable?.let(mainHandler::removeCallbacks)
        idleUnbindRunnable = null
    }

    private fun scheduleIdleUnbind(appContext: Context) {
        cancelIdleUnbind()
        val runnable = Runnable {
            if (activeDisconnectHandler != null || pendingBindReady != null) return@Runnable
            val serviceConnection = connection ?: return@Runnable
            runCatching { appContext.unbindService(serviceConnection) }
            if (connection === serviceConnection) {
                connection = null
                remoteMessenger = null
                isBinding = false
            }
            idleUnbindRunnable = null
        }
        idleUnbindRunnable = runnable
        mainHandler.postDelayed(runnable, AI_REMOTE_SERVICE_IDLE_UNBIND_MILLIS)
    }
}

private fun Bundle.putPosts(
    posts: List<Post>,
    maxMessageChars: Int = AI_IPC_MAX_MESSAGE_CHARS,
    maxTotalMessageChars: Int = posts.size * maxMessageChars
) {
    var remainingChars = maxTotalMessageChars.coerceAtLeast(0)
    putStringArrayList(KEY_POST_IDS, ArrayList(posts.map { it.id }))
    putStringArrayList(KEY_POST_SUBJECTS, ArrayList(posts.map { it.subject.orEmpty() }))
    putStringArrayList(
        KEY_POST_MESSAGES,
        ArrayList(
            posts.map { post ->
                val limit = minOf(maxMessageChars, remainingChars)
                val message = post.messageHtml.take(limit)
                remainingChars = (remainingChars - message.length).coerceAtLeast(0)
                message
            }
        )
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
            if (error is CancellationException) throw error
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
                if (error is CancellationException) throw error
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
            return Result.success(emptyList())
        }
        val sourceChunks = buildPostModerationSourceChunks(input)
        if (sourceChunks.isEmpty()) {
            return Result.success(emptyList())
        }
        return withContext(AppDispatchers.io) {
            val promptModel = runCatching {
                Generation.getClient()
            }.getOrElse {
                return@withContext Result.success(emptyList())
            }
            try {
                val status = withTimeoutOrNull(AI_STATUS_TIMEOUT_MILLIS) {
                    promptModel.checkStatus()
                }
                if (status != FeatureStatus.AVAILABLE) {
                    return@withContext Result.success(emptyList())
                }
                val detected = linkedMapOf<String, PostModerationResult>()
                sourceChunks.forEach { sourceText ->
                    val response = withTimeoutOrNull(AI_POST_MODERATION_TIMEOUT_MILLIS) {
                        promptModel.generateContent(buildPostModerationPrompt(sourceText))
                    } ?: return@forEach
                    val responseText = response.candidates.firstOrNull()?.text.orEmpty()
                    parsePostModerationResponse(responseText).forEach { (postId, result) ->
                        detected[postId] = result
                    }
                }
                Result.success(detected.values.toList())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Result.success(emptyList())
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

private suspend fun downloadSummarizerFeature(
    summarizer: Summarizer,
    onProgress: (AiAvailability) -> Unit
): Boolean {
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
    return summarizer.downloadFeature(callback).awaitOrNull(AI_REMOTE_DOWNLOAD_TIMEOUT_MILLIS) != null
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
        Hide posts that are disruptive to reading the thread: repeated flooding, copy-paste spam, harassment, threats, abusive personal attacks, slurs, unrelated vandalism, or bait that repeatedly derails discussion.
        Do not hide ordinary disagreement, normal jokes, useful criticism, quoted text, or rough tone alone.
        When a post is more likely disruptive than useful, hide it. Return every matching hidden post in this batch.
        Return one line per hidden post only.
        Format exactly: postId<TAB>HIDE<TAB>short Japanese reason
        Posts:
        $posts
    """.trimIndent()
}
