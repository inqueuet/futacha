package com.valoser.futacha.shared

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.background.BackgroundRefreshManager
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.repo.DefaultBoardRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.releaseSecurityScopedResource
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.watch.WatchCommand
import com.valoser.futacha.shared.watch.WatchCommandType
import com.valoser.futacha.shared.watch.WatchReadAloudStatusStore
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchSnapshotBuilder
import com.valoser.futacha.shared.watch.WatchThreadKey
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

private object IosAppGraph {
    private val resourceLock = NSLock()
    val stateStore by lazy { createAppStateStore() }
    val fileSystem by lazy { createFileSystem() }
    val autoSavedThreadRepository by lazy {
        SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
    }
    val cookieStorage by lazy { PersistentCookieStorage(fileSystem) }
    val cookieRepository by lazy { CookieRepository(cookieStorage) }
    private var httpClient: io.ktor.client.HttpClient? = null
    private var httpClientRefCount = 0

    private inline fun <T> withResourceLock(block: () -> T): T {
        resourceLock.lock()
        return try {
            block()
        } finally {
            resourceLock.unlock()
        }
    }

    fun acquireHttpClient(): io.ktor.client.HttpClient {
        return withResourceLock {
            httpClientRefCount += 1
            httpClient ?: createHttpClient(cookieStorage = cookieStorage).also {
                httpClient = it
            }
        }
    }

    fun releaseHttpClient() {
        val clientToClose = withResourceLock {
            if (httpClientRefCount > 0) {
                httpClientRefCount -= 1
            }
            if (httpClientRefCount == 0) {
                httpClient.also {
                    httpClient = null
                }
            } else {
                null
            }
        }
        clientToClose?.close()
    }
}

private const val IOS_BG_MAX_THREADS_PER_RUN = 40
private const val IOS_BG_AUTO_SAVE_BUDGET_MILLIS = 90 * 1000L
private const val IOS_BG_MAX_AUTO_SAVES_PER_RUN = 2
private const val IOS_BG_REFRESH_TIMEOUT_MILLIS = 9 * 60 * 1000L
private const val IOS_BACKGROUND_FLOW_MAX_RETRIES = 12L
private const val IOS_BACKGROUND_REFRESH_KEY = "background_refresh_enabled"
private const val IOS_WATCH_PREVIEW_THREAD_LIMIT = 8
private const val IOS_WATCH_COMMAND_PAYLOAD_MAX_BYTES = 4 * 1024
private const val IOS_WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES = 128 * 1024
private const val IOS_WATCH_METADATA_LOAD_TIMEOUT_MILLIS = 1_000L

private object IosWatchSnapshotBridge {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val json = Json { ignoreUnknownKeys = true }
    private val builder = WatchSnapshotBuilder()
    private val replyCountLock = NSLock()
    private val previousReplyCounts = mutableMapOf<WatchThreadKey, Int>()

    fun requestSnapshotJson(completion: (String?) -> Unit) {
        scope.launch {
            val encoded = runCatching {
                val snapshot = buildSnapshot()
                json.encodeToString(WatchSnapshot.serializer(), snapshot)
            }.getOrElse { error ->
                Logger.w("IosWatchSnapshotBridge", "Failed to build watch snapshot: ${error.message}")
                null
            }
            completion(encoded)
        }
    }

    fun markSnapshotDelivered(snapshotJson: String) {
        if (
            snapshotJson.isBlank() ||
            snapshotJson.encodeToByteArray().size > IOS_WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES
        ) {
            return
        }
        scope.launch {
            val snapshot = runCatching {
                json.decodeFromString(WatchSnapshot.serializer(), snapshotJson)
            }.getOrNull() ?: return@launch
            withReplyCountLock {
                snapshot.threads.forEach { thread ->
                    previousReplyCounts[WatchThreadKey(thread.boardId, thread.boardUrl, thread.threadId)] = thread.replyCount
                }
            }
        }
    }

    fun handleCommandJson(commandJson: String): Boolean {
        if (commandJson.isBlank() || commandJson.encodeToByteArray().size > IOS_WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
            return false
        }
        val command = runCatching {
            json.decodeFromString(WatchCommand.serializer(), commandJson)
        }.getOrNull() ?: return false
        when (command.type) {
            WatchCommandType.Refresh -> {
                scope.launch {
                    val httpClient = IosAppGraph.acquireHttpClient()
                    try {
                        runIosBackgroundRefresh(
                            stateStore = IosAppGraph.stateStore,
                            httpClient = httpClient,
                            fileSystem = IosAppGraph.fileSystem,
                            autoSaveRepo = IosAppGraph.autoSavedThreadRepository,
                            cookieRepository = IosAppGraph.cookieRepository,
                            maxThreadsPerRun = 40,
                            autoSaveBudgetMillis = 60_000L
                        )
                    } finally {
                        IosAppGraph.releaseHttpClient()
                    }
                }
                return true
            }
            WatchCommandType.OpenThreadOnPhone -> {
                val boardId = command.boardId?.takeIf { it.isNotBlank() } ?: return false
                val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() } ?: return false
                val threadId = command.threadId?.takeIf { it.isNotBlank() } ?: return false
                return FutachaAiCommandBridge.enqueue(
                    FutachaAiCommand(
                        action = FutachaAiAction.OpenThread,
                        parameters = mapOf(
                            "boardId" to boardId,
                            "boardUrl" to boardUrl,
                            "threadId" to threadId
                        ),
                        source = "watchos"
                    )
                )
            }
            WatchCommandType.SelectBoard -> {
                val boardId = command.boardId?.takeIf { it.isNotBlank() }
                val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() }
                if (boardId == null && boardUrl == null) return false
                return FutachaAiCommandBridge.enqueue(
                    FutachaAiCommand(
                        action = FutachaAiAction.OpenBoard,
                        parameters = buildMap {
                            boardId?.let { put("boardId", it) }
                            boardUrl?.let { put("boardUrl", it) }
                        },
                        source = "watchos"
                    )
                )
            }
            WatchCommandType.StartReadAloudOnPhone -> {
                return enqueueIosWatchThreadAction(command, FutachaAiAction.StartThreadReadAloud)
            }
            WatchCommandType.PauseReadAloudOnPhone -> {
                return enqueueIosWatchThreadAction(command, FutachaAiAction.PauseThreadReadAloud)
            }
            WatchCommandType.StopReadAloudOnPhone -> {
                return enqueueIosWatchThreadAction(command, FutachaAiAction.StopThreadReadAloud)
            }
            WatchCommandType.NextReadAloudOnPhone -> {
                return enqueueIosWatchThreadAction(command, FutachaAiAction.NextThreadReadAloud)
            }
            WatchCommandType.PreviousReadAloudOnPhone -> {
                return enqueueIosWatchThreadAction(command, FutachaAiAction.PreviousThreadReadAloud)
            }
        }
    }

    private fun enqueueIosWatchThreadAction(
        command: WatchCommand,
        action: FutachaAiAction
    ): Boolean {
        val boardId = command.boardId?.takeIf { it.isNotBlank() } ?: return false
        val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() } ?: return false
        val threadId = command.threadId?.takeIf { it.isNotBlank() } ?: return false
        return FutachaAiCommandBridge.enqueue(
            FutachaAiCommand(
                action = action,
                parameters = mapOf(
                    "boardId" to boardId,
                    "boardUrl" to boardUrl,
                    "threadId" to threadId
                ),
                source = "watchos"
            )
        )
    }

    private suspend fun buildSnapshot(): WatchSnapshot {
        val boards = IosAppGraph.stateStore.boards.first()
        val history = IosAppGraph.stateStore.history.first()
        val watchWords = IosAppGraph.stateStore.watchWords.first()
        val previousCounts = withReplyCountLock { previousReplyCounts.toMap() }
        val snapshot = builder.build(
            boards = boards,
            history = history,
            watchWords = watchWords,
            threadPages = loadPreviewThreadPages(history),
            previousReplyCounts = previousCounts,
            readAloudStatus = WatchReadAloudStatusStore.status.value
        )
        return snapshot
    }

    private suspend fun loadPreviewThreadPages(
        history: List<ThreadHistoryEntry>
    ): Map<WatchThreadKey, ThreadPage> {
        val result = mutableMapOf<WatchThreadKey, ThreadPage>()
        history
            .asSequence()
            .sortedByDescending { it.lastVisitedEpochMillis }
            .filter { it.hasAutoSave }
            .take(IOS_WATCH_PREVIEW_THREAD_LIMIT)
            .forEach { entry ->
                val metadata = withTimeoutOrNull(IOS_WATCH_METADATA_LOAD_TIMEOUT_MILLIS) {
                    IosAppGraph.autoSavedThreadRepository
                        .loadThreadMetadata(entry.threadId, entry.boardId)
                        .getOrNull()
                }
                    ?: return@forEach
                result[WatchThreadKey(entry.boardId, entry.boardUrl, entry.threadId)] =
                    metadata.toThreadPage(IosAppGraph.fileSystem)
            }
        return result
    }

    private inline fun <T> withReplyCountLock(block: () -> T): T {
        replyCountLock.lock()
        return try {
            block()
        } finally {
            replyCountLock.unlock()
        }
    }
}

fun requestIosWatchSnapshotJson(completion: (String?) -> Unit) {
    IosWatchSnapshotBridge.requestSnapshotJson(completion)
}

fun markIosWatchSnapshotDelivered(snapshotJson: String) {
    IosWatchSnapshotBridge.markSnapshotDelivered(snapshotJson)
}

fun handleIosWatchCommandJson(commandJson: String): Boolean {
    return IosWatchSnapshotBridge.handleCommandJson(commandJson)
}

/**
 * Lightweight registration that MUST be called in didFinishLaunchingWithOptions.
 * Registers the BGTask identifier and restores the persisted enabled state
 * without eagerly initializing the heavy iOS app graph.
 */
fun registerIosBackgroundRefreshTask() {
    BackgroundRefreshManager.registerAtLaunch()
    val enabledAtLaunch = NSUserDefaults.standardUserDefaults().boolForKey(IOS_BACKGROUND_REFRESH_KEY)
    Logger.d("MainViewController", "registerIosBackgroundRefreshTask(enabledAtLaunch=$enabledAtLaunch)")
    BackgroundRefreshManager.configure(enabledAtLaunch) {
        val httpClient = IosAppGraph.acquireHttpClient()
        try {
            runIosBackgroundRefresh(
                stateStore = IosAppGraph.stateStore,
                httpClient = httpClient,
                fileSystem = IosAppGraph.fileSystem,
                autoSaveRepo = IosAppGraph.autoSavedThreadRepository,
                cookieRepository = IosAppGraph.cookieRepository
            )
        } finally {
            IosAppGraph.releaseHttpClient()
        }
    }
}

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        val stateStore = remember { IosAppGraph.stateStore }
        val fileSystem = remember { IosAppGraph.fileSystem }
        val autoSavedThreadRepository = remember { IosAppGraph.autoSavedThreadRepository }
        val cookieRepository = remember { IosAppGraph.cookieRepository }
        val httpClient = remember { IosAppGraph.acquireHttpClient() }
        LaunchedEffect(fileSystem) {
            (fileSystem as? com.valoser.futacha.shared.util.IosFileSystem)
                ?.cleanupTempFiles()
                ?.onSuccess { deletedCount ->
                    if (deletedCount > 0) {
                        Logger.i("MainViewController", "Cleaned up $deletedCount stale iOS temp files")
                    }
                }
                ?.onFailure { error ->
                    Logger.w("MainViewController", "Failed to clean up iOS temp files: ${error.message}")
                }
        }
        LaunchedEffect(stateStore, httpClient, fileSystem, autoSavedThreadRepository) {
            try {
                Logger.d("MainViewController", "Starting background refresh enabled-state collector")
                stateStore.isBackgroundRefreshEnabled
                    .distinctUntilChanged()
                    .onEach { enabled ->
                        Logger.d("MainViewController", "Background refresh enabled state changed: $enabled")
                        configureIosBackgroundRefresh(
                            enabled = enabled,
                            stateStore = stateStore,
                            fileSystem = fileSystem,
                            autoSaveRepo = autoSavedThreadRepository
                        )
                    }
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) throw cause
                        val retryState = resolveIosBackgroundRefreshFlowRetryState(
                            attempt = attempt,
                            maxRetries = IOS_BACKGROUND_FLOW_MAX_RETRIES
                        )
                        if (!retryState.shouldRetry) {
                            Logger.e(
                                "MainViewController",
                                "Background refresh flow failed too many times; stopping collector",
                                cause
                            )
                            return@retryWhen false
                        }
                        val backoffMillis = retryState.backoffMillis ?: return@retryWhen false
                        Logger.e(
                            "MainViewController",
                            "Background refresh flow failed; retrying in ${backoffMillis}ms (attempt=${attempt + 1})",
                            cause
                        )
                        delay(backoffMillis)
                        true
                    }
                    .collect { }
                Logger.w("MainViewController", "Background refresh flow completed unexpectedly")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MainViewController", "Background refresh flow terminated unexpectedly", e)
            }
        }
        val versionChecker = remember(httpClient) {
            createVersionChecker(httpClient)
        }
        DisposableEffect(Unit) {
            onDispose {
                releaseSecurityScopedResource()
                IosAppGraph.releaseHttpClient()
            }
        }

        FutachaApp(
            stateStore = stateStore,
            versionChecker = versionChecker,
            httpClient = httpClient,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            autoSavedThreadRepository = autoSavedThreadRepository
        )
    }
}

private fun configureIosBackgroundRefresh(
    enabled: Boolean,
    stateStore: com.valoser.futacha.shared.state.AppStateStore,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveRepo: SavedThreadRepository?
) {
    Logger.d(
        "MainViewController",
        "configureIosBackgroundRefresh(enabled=$enabled, hasFileSystem=${fileSystem != null}, hasAutoSaveRepo=${autoSaveRepo != null})"
    )
    BackgroundRefreshManager.configure(enabled) {
        val managedHttpClient = IosAppGraph.acquireHttpClient()
        try {
            runIosBackgroundRefresh(
                stateStore = stateStore,
                httpClient = managedHttpClient,
                fileSystem = fileSystem,
                autoSaveRepo = autoSaveRepo,
                cookieRepository = IosAppGraph.cookieRepository
            )
        } finally {
            IosAppGraph.releaseHttpClient()
        }
    }
}

private suspend fun runIosBackgroundRefresh(
    stateStore: com.valoser.futacha.shared.state.AppStateStore,
    httpClient: io.ktor.client.HttpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveRepo: SavedThreadRepository?,
    cookieRepository: CookieRepository?,
    maxThreadsPerRun: Int = IOS_BG_MAX_THREADS_PER_RUN,
    autoSaveBudgetMillis: Long = IOS_BG_AUTO_SAVE_BUDGET_MILLIS,
    maxAutoSavesPerRun: Int = IOS_BG_MAX_AUTO_SAVES_PER_RUN,
    refreshTimeoutMillis: Long = IOS_BG_REFRESH_TIMEOUT_MILLIS
) {
    val sharedClientApi = com.valoser.futacha.shared.network.HttpBoardApi(httpClient)
    // Keep shared HttpClient ownership in MainViewController. Background repo closes only its own state.
    val nonClosingApi = object : BoardApi by sharedClientApi {}
    val repo = DefaultBoardRepository(
        api = nonClosingApi,
        parser = createHtmlParser(),
        cookieRepository = cookieRepository
    )
    val refresher = HistoryRefresher(
        stateStore = stateStore,
        repository = repo,
        dispatcher = AppDispatchers.io,
        autoSavedThreadRepository = autoSaveRepo,
        httpClient = httpClient,
        fileSystem = fileSystem,
        maxConcurrency = 2
    )
    try {
        Logger.d("BackgroundRefresh", "Starting iOS background refresh run (maxThreadsPerRun=$maxThreadsPerRun)")
        withTimeout(refreshTimeoutMillis) {
            refresher.refresh(
                autoSaveBudgetMillis = autoSaveBudgetMillis,
                maxThreadsPerRun = maxThreadsPerRun,
                maxAutoSavesPerRun = maxAutoSavesPerRun
            )
        }
        Logger.d("BackgroundRefresh", "Completed iOS background refresh run successfully")
    } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
        Logger.d("BackgroundRefresh", "Refresh already running; skipping duplicate iOS background run")
    } catch (e: TimeoutCancellationException) {
        Logger.w("BackgroundRefresh", "iOS background refresh timed out after ${refreshTimeoutMillis}ms")
        throw e
    } catch (e: CancellationException) {
        Logger.w("BackgroundRefresh", "iOS background refresh run cancelled")
        throw e
    } finally {
        refresher.close()
        Logger.d("BackgroundRefresh", "Closing temporary iOS background repository")
        repo.closeAsync().join()
    }
}
