package com.valoser.futacha

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_KEY
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY
import com.valoser.futacha.shared.watch.WatchCommand
import com.valoser.futacha.shared.watch.WatchCommandType
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchReadAloudStatusStore
import com.valoser.futacha.shared.watch.WatchReadAloudStatusUpdate
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchSnapshotBuilder
import com.valoser.futacha.shared.watch.WatchThreadKey
import com.valoser.futacha.shared.watch.WatchThreadSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

class WatchSyncManager(
    private val context: Context,
    private val stateStore: AppStateStore,
    private val historyRefresher: HistoryRefresher,
    private val autoSavedThreadRepository: SavedThreadRepository,
    private val fileSystem: FileSystem,
    private val scope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotBuilder = WatchSnapshotBuilder()
    private val previousReplyCountsMutex = Mutex()
    private val previousReplyCounts = mutableMapOf<WatchThreadKey, Int>()
    private val snapshotRequestMutex = Mutex()
    private val pendingSnapshotAckMutex = Mutex()
    private val pendingSnapshotAcks = LinkedHashMap<String, WatchSnapshot>()
    private val handledCommandIdsMutex = Mutex()
    private val handledCommandIds = LinkedHashSet<String>()
    private val watchRefreshRequestMutex = Mutex()
    private val previewMetadataSuppressedUntilElapsedMillis = AtomicLong(0L)
    private val lastReadAloudStatusSentElapsedMillis = AtomicLong(0L)
    private val lastReadAloudStatusSent = AtomicReference<WatchReadAloudStatus?>(null)
    private var isSnapshotRequestInFlight = false
    private var shouldSendSnapshotAfterCurrentRequest = false
    private var pendingSnapshotRequestIncludesPreview = false
    private var isWatchRefreshInFlight = false
    private var lastWatchRefreshStartedElapsedMillis = 0L
    private val isStarted = AtomicBoolean(false)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        scope.launch {
            try {
                combine(
                    stateStore.boards,
                    stateStore.history
                        .map { history ->
                            WatchHistorySnapshotInput(
                                history = history,
                                fingerprint = history.watchSnapshotFingerprint()
                            )
                        }
                        .distinctUntilChanged { previous, next ->
                            previous.fingerprint == next.fingerprint
                        }
                        .map { it.history },
                    stateStore.watchWords
                ) { boards, history, watchWords ->
                    WatchSnapshotInputs(
                        boards = boards,
                        history = history,
                        watchWords = watchWords
                    )
                }
                    .debounce(WATCH_SNAPSHOT_DEBOUNCE_MILLIS)
                    .distinctUntilChanged()
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) throw cause
                        val delayMillis = watchSnapshotRetryDelayMillis(attempt)
                        Logger.e(
                            TAG,
                            "Watch snapshot stream failed; retrying in ${delayMillis}ms (attempt=${attempt + 1})",
                            cause
                        )
                        delay(delayMillis)
                        true
                    }
                    .collect { inputs ->
                        try {
                            sendSnapshot(
                                buildSnapshot(
                                    boards = inputs.boards,
                                    history = inputs.history,
                                    watchWords = inputs.watchWords,
                                    readAloudStatus = WatchReadAloudStatusStore.status.value
                                )
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            Logger.w(TAG, "Failed to send automatic watch snapshot: ${error.message}")
                        }
                    }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Logger.e(TAG, "Watch snapshot stream stopped unexpectedly", error)
            } finally {
                isStarted.set(false)
            }
        }
        scope.launch {
            var hasObservedInitialStatus = false
            WatchReadAloudStatusStore.status
                .debounce(WATCH_READ_ALOUD_STATUS_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { status ->
                    if (!hasObservedInitialStatus) {
                        hasObservedInitialStatus = true
                        if (status == null) return@collect
                    }
                    val nowElapsedMillis = SystemClock.elapsedRealtime()
                    if (!shouldSendWatchReadAloudStatusUpdate(
                            status = status,
                            lastSentStatus = lastReadAloudStatusSent.get(),
                            lastSentElapsedMillis = lastReadAloudStatusSentElapsedMillis.get(),
                            nowElapsedMillis = nowElapsedMillis,
                            minIntervalMillis = WATCH_READ_ALOUD_STATUS_MIN_INTERVAL_MILLIS
                        )
                    ) {
                        return@collect
                    }
                    if (sendReadAloudStatusUpdate(status)) {
                        lastReadAloudStatusSentElapsedMillis.set(nowElapsedMillis)
                        lastReadAloudStatusSent.set(status)
                    }
                }
        }
    }

    fun requestSnapshot() {
        requestSnapshot(includePreviewThreadPages = true)
    }

    private fun requestSnapshot(includePreviewThreadPages: Boolean) {
        scope.launch {
            val shouldStart = snapshotRequestMutex.withLock {
                if (isSnapshotRequestInFlight) {
                    shouldSendSnapshotAfterCurrentRequest = true
                    pendingSnapshotRequestIncludesPreview =
                        pendingSnapshotRequestIncludesPreview || includePreviewThreadPages
                    false
                } else {
                    isSnapshotRequestInFlight = true
                    pendingSnapshotRequestIncludesPreview = false
                    true
                }
            }
            if (shouldStart) {
                drainSnapshotRequests(includePreviewThreadPages)
            }
        }
    }

    private suspend fun drainSnapshotRequests(initialIncludesPreview: Boolean) {
        var shouldContinue = true
        var includePreviewThreadPages = initialIncludesPreview
        try {
            while (shouldContinue) {
                runCatching {
                    withTimeout(WATCH_SNAPSHOT_REQUEST_TIMEOUT_MILLIS) {
                        sendCurrentSnapshot(includePreviewThreadPages)
                    }
                }.onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        throw error
                    }
                }
                val nextRequestIncludesPreview = snapshotRequestMutex.withLock {
                    val pending = shouldSendSnapshotAfterCurrentRequest
                    val pendingIncludesPreview = pendingSnapshotRequestIncludesPreview
                    shouldSendSnapshotAfterCurrentRequest = false
                    pendingSnapshotRequestIncludesPreview = false
                    if (!pending) {
                        isSnapshotRequestInFlight = false
                    }
                    shouldContinue = pending
                    pendingIncludesPreview
                }
                includePreviewThreadPages = nextRequestIncludesPreview
            }
        } finally {
            // If we exit abnormally (e.g. scope cancellation) the in-flight flag
            // must be cleared, or every future requestSnapshot() becomes a no-op.
            if (shouldContinue) {
                withContext(NonCancellable) {
                    snapshotRequestMutex.withLock {
                        shouldSendSnapshotAfterCurrentRequest = false
                        pendingSnapshotRequestIncludesPreview = false
                        isSnapshotRequestInFlight = false
                    }
                }
            }
        }
    }

    fun handleCommandPayload(payload: ByteArray) {
        if (payload.isEmpty() || payload.size > WATCH_COMMAND_PAYLOAD_MAX_BYTES) return
        scope.launch {
            val command = runCatching {
                withContext(Dispatchers.Default) {
                    json.decodeFromString(WatchCommand.serializer(), payload.decodeToString())
                }
            }.getOrNull() ?: return@launch
            if (isDuplicateCommand(command)) {
                return@launch
            }
            handleCommand(command)
        }
    }

    fun handleSnapshotAckPayload(payload: ByteArray) {
        if (payload.isEmpty() || payload.size > WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES) return
        val ackId = payload.decodeToString().takeIf { it.isNotBlank() } ?: return
        scope.launch {
            completeSnapshotAck(ackId)
        }
    }

    private fun handleCommand(command: WatchCommand) {
        when (command.type) {
            WatchCommandType.Refresh -> {
                scope.launch {
                    var didStartRefresh = false
                    try {
                        didStartRefresh = beginWatchRefreshIfAllowed()
                        if (!didStartRefresh) {
                            return@launch
                        }
                        runCatching {
                            withTimeout(WATCH_REFRESH_TIMEOUT_MILLIS) {
                                historyRefresher.refresh(
                                    autoSaveBudgetMillis = WATCH_REFRESH_AUTO_SAVE_BUDGET_MILLIS,
                                    maxThreadsPerRun = WATCH_REFRESH_MAX_THREADS_PER_RUN,
                                    maxAutoSavesPerRun = WATCH_REFRESH_MAX_AUTO_SAVES_PER_RUN
                                )
                            }
                        }.onFailure { error ->
                            if (error is CancellationException && error !is TimeoutCancellationException) {
                                throw error
                            }
                        }
                    } finally {
                        if (didStartRefresh) {
                            finishWatchRefresh()
                        }
                        requestSnapshot(includePreviewThreadPages = false)
                    }
                }
            }
            WatchCommandType.OpenThreadOnPhone -> {
                enqueueOpenThreadCommand(command)
            }
            WatchCommandType.SelectBoard -> {
                enqueueOpenBoardCommand(command)
            }
            WatchCommandType.StartReadAloudOnPhone -> {
                enqueueThreadActionCommand(command, FutachaAiAction.StartThreadReadAloud)
            }
            WatchCommandType.PauseReadAloudOnPhone -> {
                enqueueThreadActionCommand(command, FutachaAiAction.PauseThreadReadAloud)
            }
            WatchCommandType.StopReadAloudOnPhone -> {
                enqueueThreadActionCommand(command, FutachaAiAction.StopThreadReadAloud)
            }
            WatchCommandType.NextReadAloudOnPhone -> {
                enqueueThreadActionCommand(command, FutachaAiAction.NextThreadReadAloud)
            }
            WatchCommandType.PreviousReadAloudOnPhone -> {
                enqueueThreadActionCommand(command, FutachaAiAction.PreviousThreadReadAloud)
            }
        }
    }

    private suspend fun isDuplicateCommand(command: WatchCommand): Boolean {
        val commandId = command.commandId
            ?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= WATCH_COMMAND_ID_MAX_BYTES }
            ?: return false
        return handledCommandIdsMutex.withLock {
            if (!handledCommandIds.add(commandId)) {
                true
            } else {
                while (handledCommandIds.size > WATCH_HANDLED_COMMAND_ID_MAX_COUNT) {
                    val oldestCommandId = handledCommandIds.firstOrNull() ?: break
                    handledCommandIds.remove(oldestCommandId)
                }
                false
            }
        }
    }

    private fun enqueueOpenBoardCommand(command: WatchCommand) {
        val boardId = command.boardId?.takeIf { it.isNotBlank() }
        val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() }
        if (boardId == null && boardUrl == null) return
        val accepted = FutachaAiCommandBridge.enqueue(
            FutachaAiCommand(
                action = FutachaAiAction.OpenBoard,
                parameters = buildMap {
                    boardId?.let { put("boardId", it) }
                    boardUrl?.let { put("boardUrl", it) }
                    command.commandId?.takeIf { it.isNotBlank() }?.let { put("commandId", it) }
                },
                source = "wear-os"
            )
        )
        if (accepted) {
            openMainActivity()
        } else {
            Logger.w(TAG, "Dropped watch open-board command because AI command queue is full")
        }
    }

    private fun enqueueOpenThreadCommand(command: WatchCommand) {
        enqueueThreadActionCommand(command, FutachaAiAction.OpenThread)
    }

    private fun enqueueThreadActionCommand(
        command: WatchCommand,
        action: FutachaAiAction
    ) {
        val boardId = command.boardId?.takeIf { it.isNotBlank() } ?: return
        val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() } ?: return
        val threadId = command.threadId?.takeIf { it.isNotBlank() } ?: return
        val accepted = FutachaAiCommandBridge.enqueue(
            FutachaAiCommand(
                action = action,
                parameters = mapOf(
                    "boardId" to boardId,
                    "boardUrl" to boardUrl,
                    "threadId" to threadId
                ) + command.commandId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { mapOf("commandId" to it) }
                    .orEmpty(),
                source = "wear-os"
            )
        )
        if (accepted) {
            openMainActivity()
        } else {
            Logger.w(TAG, "Dropped watch thread command because AI command queue is full: action=${action.id}")
        }
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            context.startActivity(intent)
        }.onFailure { error ->
            Logger.w(TAG, "Failed to open MainActivity from watch command: ${error.message}")
        }
    }

    private suspend fun sendCurrentSnapshot(includePreviewThreadPages: Boolean) {
        val boards = stateStore.boards.first()
        val history = stateStore.history.first()
        val watchWords = stateStore.watchWords.first()
        val readAloudStatus = WatchReadAloudStatusStore.status.value
        sendSnapshot(
            buildSnapshot(
                boards = boards,
                history = history,
                watchWords = watchWords,
                readAloudStatus = readAloudStatus,
                includePreviewThreadPages = includePreviewThreadPages
            )
        )
    }

    private suspend fun buildSnapshot(
        boards: List<BoardSummary>,
        history: List<ThreadHistoryEntry>,
        watchWords: List<String>,
        readAloudStatus: WatchReadAloudStatus?,
        includePreviewThreadPages: Boolean = true
    ): WatchSnapshot {
        val previousCountsSnapshot = previousReplyCountsMutex.withLock {
            previousReplyCounts.toMap()
        }
        val shouldLoadPreviewThreadPages = shouldLoadWatchPreviewThreadPages(
            includePreviewThreadPages = includePreviewThreadPages,
            previewSuppressedUntilElapsedMillis = previewMetadataSuppressedUntilElapsedMillis.get(),
            nowElapsedMillis = SystemClock.elapsedRealtime()
        )
        val threadPages = if (shouldLoadPreviewThreadPages) {
            loadPreviewThreadPages(history)
        } else {
            emptyMap()
        }
        return snapshotBuilder.build(
            boards = boards,
            history = history,
            watchWords = watchWords,
            threadPages = threadPages,
            previousReplyCounts = previousCountsSnapshot,
            readAloudStatus = readAloudStatus
        )
    }

    private suspend fun beginWatchRefreshIfAllowed(): Boolean {
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        return watchRefreshRequestMutex.withLock {
            val decision = resolveWatchRefreshRequestDecision(
                isRefreshInFlight = isWatchRefreshInFlight,
                lastRefreshStartedElapsedMillis = lastWatchRefreshStartedElapsedMillis,
                nowElapsedMillis = nowElapsedMillis,
                minIntervalMillis = WATCH_REFRESH_MIN_INTERVAL_MILLIS
            )
            if (decision == WatchRefreshRequestDecision.StartRefresh) {
                isWatchRefreshInFlight = true
                lastWatchRefreshStartedElapsedMillis = nowElapsedMillis
                suppressPreviewMetadataLoads(nowElapsedMillis)
                true
            } else {
                false
            }
        }
    }

    private suspend fun finishWatchRefresh() {
        withContext(NonCancellable) {
            suppressPreviewMetadataLoads(SystemClock.elapsedRealtime())
            watchRefreshRequestMutex.withLock {
                isWatchRefreshInFlight = false
            }
        }
    }

    private fun suppressPreviewMetadataLoads(nowElapsedMillis: Long) {
        val suppressedUntil = nowElapsedMillis + WATCH_PREVIEW_METADATA_SUPPRESSION_MILLIS
        previewMetadataSuppressedUntilElapsedMillis.updateAndGet { current ->
            maxOf(current, suppressedUntil)
        }
    }

    private suspend fun loadPreviewThreadPages(
        history: List<ThreadHistoryEntry>
    ): Map<WatchThreadKey, ThreadPage> = coroutineScope {
        history
            .asSequence()
            .sortedByDescending { it.lastVisitedEpochMillis }
            .filter { it.hasAutoSave }
            .take(WATCH_PREVIEW_THREAD_LIMIT)
            .map { entry ->
                async {
                    val metadata = autoSavedThreadRepository
                        .loadThreadMetadataWithTimeout(entry.threadId, entry.boardId)
                        ?: return@async null
                    val key = WatchThreadKey(
                        boardId = entry.boardId,
                        boardUrl = entry.boardUrl,
                        threadId = entry.threadId
                    )
                    key to metadata.toThreadPage(fileSystem)
                }
            }
            .toList()
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    private suspend fun sendSnapshot(snapshot: WatchSnapshot) {
        val encoded = json.encodeToString(WatchSnapshot.serializer(), snapshot)
        val payload = encoded.encodeToByteArray()
        if (payload.size > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) {
            Logger.w(
                TAG,
                "Dropped watch snapshot because payload is too large: ${payload.size} bytes"
            )
            return
        }
        val ackId = nextSnapshotAckId(snapshot)
        registerPendingSnapshotAck(ackId, snapshot)
        val request = PutDataMapRequest.create(WATCH_SNAPSHOT_PATH).apply {
            dataMap.putString(WATCH_SNAPSHOT_KEY, encoded)
            dataMap.putString(WATCH_SNAPSHOT_ACK_KEY, ackId)
            dataMap.putLong("generatedAtMillis", snapshot.generatedAtMillis)
        }.asPutDataRequest().setUrgent()

        val sentDataItem = (Wearable.getDataClient(context.applicationContext)
            .putDataItem(request)
            .awaitOrNull(WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS) != null)
        val sentMessage = sendSnapshotMessage(encoded, ackId)
        if (!sentMessage && !sentDataItem) {
            removePendingSnapshotAck(ackId)
        }
    }

    private suspend fun sendSnapshotMessage(encoded: String, ackId: String): Boolean {
        val payload = DataMap().apply {
            putString(WATCH_SNAPSHOT_KEY, encoded)
            putString(WATCH_SNAPSHOT_ACK_KEY, ackId)
        }.toByteArray()
        if (payload.size > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) {
            Logger.w(
                TAG,
                "Skipped watch snapshot message because payload is too large: ${payload.size} bytes"
            )
            return false
        }
        val appContext = context.applicationContext
        val connectedNodes = Wearable.getNodeClient(appContext)
            .connectedNodes
            .awaitOrNull(WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS)
            .orEmpty()
        val nodes = connectedNodes
            .filter { it.isNearby }
            .ifEmpty { connectedNodes }
        if (nodes.isEmpty()) return false

        var sent = false
        nodes.forEach { node ->
            val messageId = Wearable.getMessageClient(appContext)
                .sendMessage(node.id, WATCH_SNAPSHOT_PATH, payload)
                .awaitOrNull(WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS)
            if (messageId != null) {
                sent = true
            }
        }
        return sent
    }

    private suspend fun sendReadAloudStatusUpdate(status: WatchReadAloudStatus?): Boolean {
        val update = WatchReadAloudStatusUpdate(
            status = status,
            updatedAtMillis = System.currentTimeMillis()
        )
        val encoded = json.encodeToString(WatchReadAloudStatusUpdate.serializer(), update)
        if (encoded.encodeToByteArray().size > WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES) {
            return false
        }
        val request = PutDataMapRequest.create(WATCH_READ_ALOUD_STATUS_PATH).apply {
            dataMap.putString(WATCH_READ_ALOUD_STATUS_KEY, encoded)
            dataMap.putLong(WATCH_UPDATED_AT_KEY, update.updatedAtMillis)
        }.asPutDataRequest().setUrgent()

        val sentDataItem = (Wearable.getDataClient(context.applicationContext)
            .putDataItem(request)
            .awaitOrNull(WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS) != null)
        val sentMessage = sendReadAloudStatusMessage(encoded)
        return sentDataItem || sentMessage
    }

    private suspend fun sendReadAloudStatusMessage(encoded: String): Boolean {
        val payload = DataMap().apply {
            putString(WATCH_READ_ALOUD_STATUS_KEY, encoded)
        }.toByteArray()
        if (payload.size > WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES) {
            return false
        }
        val appContext = context.applicationContext
        val connectedNodes = Wearable.getNodeClient(appContext)
            .connectedNodes
            .awaitOrNull(WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS)
            .orEmpty()
        val nodes = connectedNodes
            .filter { it.isNearby }
            .ifEmpty { connectedNodes }
        if (nodes.isEmpty()) return false

        var sent = false
        nodes.forEach { node ->
            val messageId = Wearable.getMessageClient(appContext)
                .sendMessage(node.id, WATCH_READ_ALOUD_STATUS_PATH, payload)
                .awaitOrNull(WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS)
            if (messageId != null) {
                sent = true
            }
        }
        return sent
    }

    private suspend fun registerPendingSnapshotAck(ackId: String, snapshot: WatchSnapshot) {
        pendingSnapshotAckMutex.withLock {
            pendingSnapshotAcks[ackId] = snapshot
            while (pendingSnapshotAcks.size > WATCH_PENDING_SNAPSHOT_ACK_MAX_COUNT) {
                val oldestAckId = pendingSnapshotAcks.keys.firstOrNull() ?: break
                pendingSnapshotAcks.remove(oldestAckId)
            }
        }
    }

    private suspend fun removePendingSnapshotAck(ackId: String) {
        pendingSnapshotAckMutex.withLock {
            pendingSnapshotAcks.remove(ackId)
        }
    }

    private suspend fun completeSnapshotAck(ackId: String) {
        val snapshot = pendingSnapshotAckMutex.withLock {
            pendingSnapshotAcks.remove(ackId)
        } ?: return

        previousReplyCountsMutex.withLock {
            val activeKeys = snapshot.threads.mapTo(mutableSetOf()) { it.toPreviousReplyCountKey() }
            previousReplyCounts.keys.retainAll(activeKeys)
            snapshot.threads.forEach { thread ->
                previousReplyCounts[thread.toPreviousReplyCountKey()] = thread.replyCount
            }
        }
    }

    private fun nextSnapshotAckId(snapshot: WatchSnapshot): String {
        return "${snapshot.generatedAtMillis}-${System.nanoTime()}"
    }

    private fun WatchThreadSummary.toPreviousReplyCountKey() =
        WatchThreadKey(
            boardId = boardId,
            boardUrl = boardUrl,
            threadId = threadId
        )

    private suspend fun SavedThreadRepository.loadThreadMetadataWithTimeout(
        threadId: String,
        boardId: String
    ) = withTimeoutOrNull(WATCH_METADATA_LOAD_TIMEOUT_MILLIS) {
        loadThreadMetadata(threadId, boardId).getOrNull()
    }

    private suspend fun <T> Task<T>.awaitOrNull(timeoutMillis: Long): T? {
        return withTimeoutOrNull(timeoutMillis.coerceAtLeast(1L)) {
            suspendCancellableCoroutine { continuation ->
                val continuationRef = AtomicReference<CancellableContinuation<T?>?>(continuation)
                continuation.invokeOnCancellation {
                    continuationRef.set(null)
                }
                addOnCompleteListener { task ->
                    val activeContinuation = continuationRef.getAndSet(null) ?: return@addOnCompleteListener
                    if (!activeContinuation.isActive) return@addOnCompleteListener
                    activeContinuation.resume(
                        if (task.isSuccessful) task.result else null
                    )
                }
            }
        }
    }

    private data class WatchSnapshotInputs(
        val boards: List<BoardSummary>,
        val history: List<ThreadHistoryEntry>,
        val watchWords: List<String>
    )

    private data class WatchHistorySnapshotInput(
        val history: List<ThreadHistoryEntry>,
        val fingerprint: List<WatchHistoryEntryFingerprint>
    )

    private data class WatchHistoryEntryFingerprint(
        val threadId: String,
        val boardId: String,
        val boardUrl: String,
        val title: String,
        val titleImageUrl: String,
        val boardName: String,
        val replyCount: Int,
        val hasAutoSave: Boolean,
        val isAutoRefreshDisabled: Boolean
    )

    private fun List<ThreadHistoryEntry>.watchSnapshotFingerprint(): List<WatchHistoryEntryFingerprint> {
        return map { entry ->
            WatchHistoryEntryFingerprint(
                threadId = entry.threadId,
                boardId = entry.boardId,
                boardUrl = entry.boardUrl,
                title = entry.title,
                titleImageUrl = entry.titleImageUrl,
                boardName = entry.boardName,
                replyCount = entry.replyCount,
                hasAutoSave = entry.hasAutoSave,
                isAutoRefreshDisabled = entry.isAutoRefreshDisabled
            )
        }
    }

    private fun watchSnapshotRetryDelayMillis(attempt: Long): Long {
        val shift = attempt.coerceAtMost(WATCH_SNAPSHOT_RETRY_MAX_SHIFT.toLong()).toInt()
        val multiplier = 1L shl shift
        return (WATCH_SNAPSHOT_RETRY_BASE_DELAY_MILLIS * multiplier)
            .coerceAtMost(WATCH_SNAPSHOT_RETRY_MAX_DELAY_MILLIS)
    }

    private companion object {
        private const val TAG = "WatchSyncManager"
        private const val WATCH_PREVIEW_THREAD_LIMIT = 8
        private const val WATCH_COMMAND_PAYLOAD_MAX_BYTES = 4 * 1024
        private const val WATCH_COMMAND_ID_MAX_BYTES = 128
        private const val WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES = 128
        private const val WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES = 96 * 1024
        private const val WATCH_PENDING_SNAPSHOT_ACK_MAX_COUNT = 8
        private const val WATCH_HANDLED_COMMAND_ID_MAX_COUNT = 128
        private const val WATCH_SNAPSHOT_DEBOUNCE_MILLIS = 1_000L
        private const val WATCH_READ_ALOUD_STATUS_DEBOUNCE_MILLIS = 500L
        private const val WATCH_READ_ALOUD_STATUS_MIN_INTERVAL_MILLIS = 5_000L
        private const val WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES = 4 * 1024
        private const val WATCH_SNAPSHOT_RETRY_BASE_DELAY_MILLIS = 1_000L
        private const val WATCH_SNAPSHOT_RETRY_MAX_DELAY_MILLIS = 60_000L
        private const val WATCH_SNAPSHOT_RETRY_MAX_SHIFT = 6
        private const val WATCH_SNAPSHOT_REQUEST_TIMEOUT_MILLIS = 10_000L
        private const val WATCH_DATA_LAYER_SEND_TIMEOUT_MILLIS = 10_000L
        private const val WATCH_METADATA_LOAD_TIMEOUT_MILLIS = 1_000L
        private val WATCH_REFRESH_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(6)
        private val WATCH_REFRESH_AUTO_SAVE_BUDGET_MILLIS = TimeUnit.SECONDS.toMillis(90)
        private val WATCH_REFRESH_MIN_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(2)
        private val WATCH_PREVIEW_METADATA_SUPPRESSION_MILLIS = TimeUnit.MINUTES.toMillis(2)
        private const val WATCH_REFRESH_MAX_THREADS_PER_RUN = 60
        private const val WATCH_REFRESH_MAX_AUTO_SAVES_PER_RUN = 2
    }
}
