package com.valoser.futacha.shared

import com.valoser.futacha.shared.media.source.bindOriginalMediaSource
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.background.BackgroundRefreshManager
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.decodeAiQueryValue
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ai.parseFutachaAiDeepLink
import com.valoser.futacha.shared.ai.threadIdParameter
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.ai.boardUrlParameter
import com.valoser.futacha.shared.model.CatalogFetchSettings
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
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import com.valoser.futacha.shared.service.CatalogWatchAlertRefresher
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.WatchAlertNotificationLedger
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.state.AppStateSeedDefaults
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.ui.IosReviewCompliance
import com.valoser.futacha.shared.ui.LocalIosReviewCompliance
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.applyAppIconVariant
import com.valoser.futacha.shared.util.releaseSecurityScopedResource
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ExperienceProfileUiController
import com.valoser.futacha.shared.compat.IosCompatibilityStore
import com.valoser.futacha.shared.compat.IosExperienceProfileStore
import com.valoser.futacha.shared.compat.IosModeSwitchCoordinator
import com.valoser.futacha.shared.compat.IosArchiveReportScheduler
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.compatibilityHistorySharedMetadata
import com.valoser.futacha.shared.compat.CompatForegroundNetworkPolicy
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.compatForegroundLastCheckStoredValue
import com.valoser.futacha.shared.compat.parseCompatForegroundNetworkPolicy
import com.valoser.futacha.shared.compat.parseCompatWatchWords
import com.valoser.futacha.shared.compat.refreshCompatTabsInBackground
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import com.valoser.futacha.shared.ui.compat.IosCompatNetworkStateBridge
import com.valoser.futacha.shared.watch.WatchCommand
import com.valoser.futacha.shared.watch.WatchCommandType
import com.valoser.futacha.shared.watch.WatchReadAloudStatusStore
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchSnapshotBuilder
import com.valoser.futacha.shared.watch.WatchThreadKey
import platform.Foundation.NSLock
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import com.valoser.futacha.shared.version.createVersionChecker
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.coroutineContext

private object IosAppGraph {
    private val resourceLock = NSLock()
    val fileSystem by lazy { createFileSystem() }
    val stateStore by lazy { createAppStateStore(fileSystem = fileSystem) }
    val autoSavedThreadRepository by lazy {
        SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
    }
    val cookieStorage by lazy { PersistentCookieStorage(fileSystem) }
    val cookieRepository by lazy { CookieRepository(cookieStorage) }
    val compatibilityStore by lazy { IosCompatibilityStore(fileSystem) }
    val experienceProfileStore by lazy { IosExperienceProfileStore() }
    val modeSwitchCoordinator by lazy {
        IosModeSwitchCoordinator(experienceProfileStore) { _, preferredFutachaIcon ->
            applyAppIconVariant(platformContext = null, variant = preferredFutachaIcon)
        }
    }
    private var httpClient: io.ktor.client.HttpClient? = null
    private var originalMediaSession: com.valoser.futacha.shared.media.source.OriginalMediaSession? = null
    private var previousMediaShutdown: kotlinx.coroutines.Deferred<Unit>? = null
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
            val client = httpClient ?: createHttpClient(cookieStorage = cookieStorage).also {
                httpClient = it
                val previousShutdown = previousMediaShutdown
                originalMediaSession = com.valoser.futacha.shared.media.source.createOriginalMediaSession(it) {
                    previousShutdown?.await()
                }
                it.bindOriginalMediaSource(checkNotNull(originalMediaSession))
            }
            httpClientRefCount += 1
            client
        }
    }

    fun originalMediaSessionFor(client: io.ktor.client.HttpClient) = withResourceLock {
        check(httpClient === client)
        checkNotNull(originalMediaSession)
    }

    fun releaseHttpClient() {
        val clientToClose = withResourceLock {
            if (httpClientRefCount > 0) {
                httpClientRefCount -= 1
            }
            if (httpClientRefCount == 0) {
                httpClient.also {
                    originalMediaSession?.let { session ->
                        previousMediaShutdown = session.shutdownSignal
                        session.close()
                    }
                    originalMediaSession = null
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
private const val IOS_BG_REPOSITORY_CLOSE_TIMEOUT_MILLIS = 2_000L
private const val IOS_BACKGROUND_FLOW_MAX_RETRIES = 12L
private const val IOS_COMPAT_MANUAL_HISTORY_REFRESH_TIMEOUT_MILLIS = 60_000L
private const val IOS_COMPAT_MANUAL_HISTORY_REFRESH_MAX_TABS = 40
private const val IOS_BACKGROUND_REFRESH_KEY = "background_refresh_enabled"
private const val IOS_WATCH_ALERT_KEY = "watch_alert_enabled"
private const val IOS_ACTIVE_PROFILE_KEY = "experience.active_profile"
private const val IOS_BACKGROUND_TASK_ENABLED_DECISION_KEY = "background_task_enabled_last_decision"
private const val IOS_WATCH_PREVIEW_THREAD_LIMIT = 8
private const val IOS_WATCH_COMMAND_PAYLOAD_MAX_BYTES = 4 * 1024
private const val IOS_WATCH_COMMAND_ID_MAX_BYTES = 128
private const val IOS_WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES = 128 * 1024
private const val IOS_WATCH_METADATA_LOAD_TIMEOUT_MILLIS = 1_000L
private const val IOS_WATCH_HANDLED_COMMAND_ID_MAX_COUNT = 128
private const val IOS_THREAD_DEEP_LINK_MAX_CHARS = 64 * 1024

/**
 * Buffers an incoming custom-scheme thread URL until the Compose root is ready.
 * AI commands use their own channel; keeping thread navigation separate avoids
 * a `futacha://thread` URL being silently discarded by the AI parser.
 */
private object IosThreadDeepLinkBridge {
    private val links = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)

    fun submit(raw: String): Boolean {
        val normalized = normalizeIosThreadDeepLink(raw) ?: return false
        return links.tryEmit(normalized)
    }

    fun stream() = links
}


/** Keeps an incoming Watch board selection until the profile root is ready. */
private object IosBoardDeepLinkBridge {
    private val links = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)

    fun submit(raw: String): Boolean {
        val canonical = canonicalizeBoardUrl(raw) ?: return false
        return links.tryEmit(canonical)
    }

    fun stream() = links
}

/** Called from SwiftUI's `onOpenURL` for both cold and warm launches. */
fun submitIosThreadDeepLink(raw: String): Boolean = IosThreadDeepLinkBridge.submit(raw)

internal fun normalizeIosThreadDeepLink(raw: String): String? {
    if (raw.length > IOS_THREAD_DEEP_LINK_MAX_CHARS) return null
    val trimmed = raw.trim()
    if (canonicalizeThreadUrl(trimmed) != null) return trimmed
    // `futacha://ai?action=open_thread` is normally dispatched through the
    // single-consumer AI command channel. Compatibility mode intentionally
    // has a separate workspace, though, so routing a concrete thread target
    // here prevents an iOS custom URL (or Watch handoff) from being consumed
    // by the modern-only command receiver. Other AI actions keep their normal
    // confirmation and command handling path.
    parseFutachaAiDeepLink(trimmed, source = "ios-thread-deep-link")
        ?.takeIf {
            it.action == FutachaAiAction.OpenThread ||
                it.action == FutachaAiAction.OpenThreadFromUrl
        }
        ?.let(::resolveIosAiThreadTarget)
        ?.let { target ->
            if (canonicalizeThreadUrl(target) != null) return target
        }
    val withoutFragment = trimmed.substringBefore('#')
    val prefix = "futacha://thread"
    if (!withoutFragment.startsWith(prefix, ignoreCase = true)) return null
    val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
    val value = query
        .split('&', ';')
        .firstOrNull { pair ->
            pair.substringBefore('=', missingDelimiterValue = "")
                .trim()
                .lowercase()
                .filter { it != '_' && it != '-' } in setOf("url", "threadurl")
        }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.let(::decodeIosThreadDeepLinkComponent)
        ?.trim()
        ?: return null
    return value.takeIf { canonicalizeThreadUrl(it) != null }
}

private fun resolveIosAiThreadTarget(command: FutachaAiCommand): String? {
    command.threadUrlParameter()?.let { return it }
    val boardUrl = command.boardUrlParameter() ?: return null
    val threadId = command.threadIdParameter() ?: return null
    if (!threadId.all(Char::isDigit)) return null
    return "${boardUrl.trimEnd('/')}/res/$threadId.htm"
}

/**
 * WatchConnectivity supplies a board URL and numeric thread id rather than a
 * full URL. Convert it before entering the profile-neutral deep-link stream.
 */
private fun submitIosWatchThreadDeepLink(boardUrl: String, threadId: String): Boolean {
    if (!threadId.all(Char::isDigit)) return false
    return submitIosThreadDeepLink("${boardUrl.trimEnd('/')}/res/$threadId.htm")
}

private fun submitIosWatchBoardDeepLink(boardUrl: String): Boolean =
    IosBoardDeepLinkBridge.submit(boardUrl)

private fun decodeIosThreadDeepLinkComponent(value: String): String {
    return decodeAiQueryValue(value)
}

private data class IosBackgroundScheduleState(
    val profile: ExperienceProfile,
    val generation: Long,
    val enabled: Boolean
)

private object IosWatchSnapshotBridge {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val json = Json { ignoreUnknownKeys = true }
    private val builder = WatchSnapshotBuilder()
    private val replyCountLock = NSLock()
    private val previousReplyCounts = mutableMapOf<WatchThreadKey, Int>()
    private val handledCommandIdsLock = NSLock()
    private val handledCommandIds = LinkedHashSet<String>()
    private val refreshJobLock = NSLock()
    private var refreshJob: Job? = null
    private var lastRefreshStartedAt: TimeMark? = null

    fun requestSnapshotJson(completion: (String?) -> Unit) {
        scope.launch {
            val encoded = runSuspendCatchingPreservingCancellation {
                val snapshot = buildSnapshot()
                val snapshotJson = json.encodeToString(WatchSnapshot.serializer(), snapshot)
                val payloadBytes = snapshotJson.encodeToByteArray().size
                if (payloadBytes > IOS_WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) {
                    Logger.w(
                        "IosWatchSnapshotBridge",
                        "Dropped watch snapshot because payload is too large: $payloadBytes bytes"
                    )
                    null
                } else {
                    snapshotJson
                }
            }.getOrElse { error ->
                Logger.w("IosWatchSnapshotBridge", "Failed to build watch snapshot: ${error.message}")
                null
            }
            withContext(Dispatchers.Main) {
                completion(encoded)
            }
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
                val activeKeys = snapshot.threads.mapTo(mutableSetOf()) { thread ->
                    WatchThreadKey(thread.boardId, thread.boardUrl, thread.threadId)
                }
                previousReplyCounts.keys.retainAll(activeKeys)
                snapshot.threads.forEach { thread ->
                    previousReplyCounts[WatchThreadKey(thread.boardId, thread.boardUrl, thread.threadId)] = thread.replyCount
                }
            }
        }
    }

    fun handleCommandJson(commandJson: String): Boolean =
        handleCommandJsonOutcome(commandJson) != IosWatchCommandOutcome.Rejected

    fun handleCommandJsonOutcome(commandJson: String): IosWatchCommandOutcome {
        if (commandJson.isBlank() || commandJson.encodeToByteArray().size > IOS_WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
            return IosWatchCommandOutcome.Rejected
        }
        val command = runCatching {
            json.decodeFromString(WatchCommand.serializer(), commandJson)
        }.getOrNull() ?: return IosWatchCommandOutcome.Rejected
        if (isDuplicateCommand(command)) {
            return IosWatchCommandOutcome.Accepted
        }
        if (command.type == WatchCommandType.Refresh) {
            return when (startWatchRefreshIfAllowed()) {
                IosWatchRefreshDecision.Throttled -> IosWatchCommandOutcome.RefreshThrottled
                IosWatchRefreshDecision.Start, IosWatchRefreshDecision.CoalesceIntoRunning ->
                    IosWatchCommandOutcome.Accepted
            }
        }
        return if (handleNonRefreshCommand(command)) IosWatchCommandOutcome.Accepted else IosWatchCommandOutcome.Rejected
    }

    private fun handleNonRefreshCommand(command: WatchCommand): Boolean {
        when (command.type) {
            WatchCommandType.Refresh -> return false
            WatchCommandType.OpenThreadOnPhone -> {
                val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() } ?: return false
                val threadId = command.threadId?.takeIf { it.isNotBlank() } ?: return false
                return submitIosWatchThreadDeepLink(boardUrl, threadId)
            }
            WatchCommandType.SelectBoard -> {
                val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() }
                return boardUrl?.let(::submitIosWatchBoardDeepLink) ?: false
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

    /**
     * Starts a Watch-requested refresh at most once per
     * [IOS_WATCH_REFRESH_MIN_INTERVAL], like Android's WatchSyncManager, so a
     * repeatedly tapped "更新" cannot hammer the boards.
     */
    private fun startWatchRefreshIfAllowed(): IosWatchRefreshDecision {
        refreshJobLock.lock()
        try {
            val decision = resolveIosWatchRefreshDecision(
                isRefreshRunning = refreshJob?.isActive == true,
                elapsedSinceLastStart = lastRefreshStartedAt?.elapsedNow(),
                minInterval = IOS_WATCH_REFRESH_MIN_INTERVAL
            )
            if (decision != IosWatchRefreshDecision.Start) return decision
            lastRefreshStartedAt = TimeSource.Monotonic.markNow()
            val nextJob = scope.launch(start = CoroutineStart.LAZY) {
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
                    refreshJobLock.lock()
                    try {
                        if (refreshJob === coroutineContext[Job]) refreshJob = null
                    } finally {
                        refreshJobLock.unlock()
                    }
                }
            }
            refreshJob = nextJob
            nextJob.start()
            return decision
        } finally {
            refreshJobLock.unlock()
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
                parameters = buildIosWatchCommandParameters(command) {
                    put("boardId", boardId)
                    put("boardUrl", boardUrl)
                    put("threadId", threadId)
                },
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
    ): Map<WatchThreadKey, ThreadPage> = coroutineScope {
        history
            .asSequence()
            .sortedByDescending { it.lastVisitedEpochMillis }
            .filter { it.hasAutoSave }
            .take(IOS_WATCH_PREVIEW_THREAD_LIMIT)
            .map { entry ->
                async {
                    val metadata = withTimeoutOrNull(IOS_WATCH_METADATA_LOAD_TIMEOUT_MILLIS) {
                        IosAppGraph.autoSavedThreadRepository
                            .loadThreadMetadata(entry.threadId, entry.boardId)
                            .getOrNull()
                    }
                        ?: return@async null
                    val key = WatchThreadKey(entry.boardId, entry.boardUrl, entry.threadId)
                    key to metadata.toThreadPage(IosAppGraph.fileSystem)
                }
            }
            .toList()
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    private inline fun buildIosWatchCommandParameters(
        command: WatchCommand,
        block: MutableMap<String, String>.() -> Unit
    ): Map<String, String> = buildMap {
        block()
        command.commandId
            ?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= IOS_WATCH_COMMAND_ID_MAX_BYTES }
            ?.let { put("commandId", it) }
    }

    private inline fun <T> withReplyCountLock(block: () -> T): T {
        replyCountLock.lock()
        return try {
            block()
        } finally {
            replyCountLock.unlock()
        }
    }

    private fun isDuplicateCommand(command: WatchCommand): Boolean {
        val commandId = command.commandId
            ?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= IOS_WATCH_COMMAND_ID_MAX_BYTES }
            ?: return false
        handledCommandIdsLock.lock()
        return try {
            if (!handledCommandIds.add(commandId)) {
                true
            } else {
                while (handledCommandIds.size > IOS_WATCH_HANDLED_COMMAND_ID_MAX_COUNT) {
                    val oldestCommandId = handledCommandIds.firstOrNull() ?: break
                    handledCommandIds.remove(oldestCommandId)
                }
                false
            }
        } finally {
            handledCommandIdsLock.unlock()
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
 * Like [handleIosWatchCommandJson] but tells Swift how to answer: "rejected",
 * "accepted", or "refreshThrottled" when a Refresh arrived within the minimum
 * interval and only the current snapshot should be sent back.
 */
fun handleIosWatchCommandJsonOutcome(commandJson: String): String =
    IosWatchSnapshotBridge.handleCommandJsonOutcome(commandJson).wireValue

internal enum class IosWatchCommandOutcome(val wireValue: String) {
    Rejected("rejected"),
    Accepted("accepted"),
    RefreshThrottled("refreshThrottled")
}

internal enum class IosWatchRefreshDecision { Start, CoalesceIntoRunning, Throttled }

internal val IOS_WATCH_REFRESH_MIN_INTERVAL: Duration = 2.minutes

internal fun resolveIosWatchRefreshDecision(
    isRefreshRunning: Boolean,
    elapsedSinceLastStart: Duration?,
    minInterval: Duration
): IosWatchRefreshDecision = when {
    isRefreshRunning -> IosWatchRefreshDecision.CoalesceIntoRunning
    elapsedSinceLastStart == null -> IosWatchRefreshDecision.Start
    elapsedSinceLastStart < minInterval -> IosWatchRefreshDecision.Throttled
    else -> IosWatchRefreshDecision.Start
}

/** Called by the Swift Network.framework monitor. */
fun updateIosWifiConnected(connected: Boolean) {
    IosCompatNetworkStateBridge.updateWifiConnected(connected)
}

/**
 * Lightweight registration that MUST be called in didFinishLaunchingWithOptions.
 * Registers the BGTask identifier and restores the persisted enabled state
 * without eagerly initializing the heavy iOS app graph.
 */
fun registerIosBackgroundRefreshTask() {
    BackgroundRefreshManager.registerAtLaunch()
    val defaults = NSUserDefaults.standardUserDefaults()
    // Compatibility preferences live in the profile store and cannot be
    // synchronously loaded during didFinishLaunching.  They also enable the
    // ふたちゃ task (archive reports default ON, shared-feature patrol), so
    // reuse the last decision of the screen-side collector and keep the task
    // alive until it has made one; runIosBackgroundRefresh re-checks the
    // actual policy before doing network work.
    val lastScreenDecision = if (defaults.objectForKey(IOS_BACKGROUND_TASK_ENABLED_DECISION_KEY) != null) {
        defaults.boolForKey(IOS_BACKGROUND_TASK_ENABLED_DECISION_KEY)
    } else {
        null
    }
    val enabledAtLaunch =
        defaults.boolForKey(IOS_BACKGROUND_REFRESH_KEY) ||
            defaults.boolForKey(IOS_WATCH_ALERT_KEY) ||
            ExperienceProfile.fromPersistedValue(defaults.stringForKey(IOS_ACTIVE_PROFILE_KEY)) ==
            ExperienceProfile.TOSHIAKI_COMPAT ||
            (lastScreenDecision ?: true)
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

/**
 * Pairs acquireHttpClient/releaseHttpClient with the remember lifecycle,
 * including abandoned compositions where DisposableEffect.onDispose never runs.
 */
private class IosHttpClientLease : RememberObserver {
    val client: io.ktor.client.HttpClient = IosAppGraph.acquireHttpClient()
    private var released = false

    private fun release() {
        if (!released) {
            released = true
            IosAppGraph.releaseHttpClient()
        }
    }

    override fun onRemembered() {}
    override fun onForgotten() = release()
    override fun onAbandoned() = release()
}

private const val IOS_ISSUE_78_THREAD_URL =
    "https://img.2chan.net/b/res/1463510009.htm"

private suspend fun seedIosIssue78ArchiveFixture(store: IosCompatibilityStore) {
    val boardUrl = "https://img.2chan.net/b/"
    val boardKey = compatBoardKey(boardUrl)
    val tabKey = compatTabKey(IOS_ISSUE_78_THREAD_URL)
    val sourceUrl = "https://dec.2chan.net/up2/src/fu7190971.png"
    val revision = kotlin.time.Clock.System.now().toEpochMilliseconds()
    store.upsertBoard(
        CompatBoard(boardKey, "二次元裏", boardUrl, boardUrl, sortOrder = 0)
    )
    store.openTab(
        CompatTab(
            key = tabKey,
            canonicalUrl = IOS_ISSUE_78_THREAD_URL,
            originalUrl = IOS_ISSUE_78_THREAD_URL,
            boardKey = boardKey,
            boardName = "二次元裏",
            threadNo = "1463510009",
            title = "生成残量回復...15%！",
            replyCount = 1,
            insertedAtEpochMillis = revision,
            contentUpdatedAtEpochMillis = revision,
            snapshotRevision = revision
        )
    )
    store.saveThreadSnapshot(
        CompatThreadSnapshot(
            tabKey = tabKey,
            revision = revision,
            fetchedAtEpochMillis = revision,
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = "1463510009",
                    timestamp = "26/08/30(日)12:09:25",
                    messageHtml =
                        "<a href=\"$sourceUrl\">fu7190971.png</a>" +
                            "<span onclick=\"previewImg('body','$sourceUrl')\">[見る]</span><br>りんみ"
                ),
                CompatPostSnapshot(
                    position = 1,
                    postNo = "1463510029",
                    timestamp = "26/08/30(日)12:09:30",
                    messageHtml =
                        "&gt;<a href=\"$sourceUrl\">fu7190971.png</a>" +
                            "<span onclick=\"previewImg('quote','$sourceUrl')\">[見る]</span><br>失恋はほむらもだろ…"
                )
            )
        )
    )
}

fun MainViewController(issue78ArchiveFixture: Boolean): UIViewController {
    return ComposeUIViewController {
        val stateStore = remember { IosAppGraph.stateStore }
        val fileSystem = remember { IosAppGraph.fileSystem }
        val autoSavedThreadRepository = remember { IosAppGraph.autoSavedThreadRepository }
        val cookieRepository = remember { IosAppGraph.cookieRepository }
        val compatibilityStore = remember { IosAppGraph.compatibilityStore }
        val profileStore = remember { IosAppGraph.experienceProfileStore }
        val modeSwitchCoordinator = remember { IosAppGraph.modeSwitchCoordinator }
        val httpClient = remember { IosHttpClientLease() }.client
        val originalMediaSession = remember(httpClient) { IosAppGraph.originalMediaSessionFor(httpClient) }
        val profileScope = rememberCoroutineScope()
        val activeProfile by profileStore.activeProfile.collectAsState()
        val profileGeneration by profileStore.generation.collectAsState()
        LaunchedEffect(stateStore) {
            stateStore.appIconVariant.collect { variant ->
                applyAppIconVariant(platformContext = null, variant = variant)
            }
        }
        val preferredAppIcon by stateStore.appIconVariant.collectAsState(
            initial = com.valoser.futacha.shared.model.AppIconVariant.Current
        )
        var initializationComplete by remember { mutableStateOf(false) }
        var initializationError by remember { mutableStateOf<String?>(null) }
        var initializationAttempt by remember { mutableIntStateOf(0) }
        var profileSwitchInProgress by remember { mutableStateOf(false) }
        var profileSessionActive by remember { mutableStateOf(true) }
        var profileSwitchError by remember { mutableStateOf<String?>(null) }
        var platformThreadDeepLink by remember { mutableStateOf<String?>(null) }
        var platformBoardDeepLink by remember { mutableStateOf<String?>(null) }
        var platformAiCommand by remember { mutableStateOf<FutachaAiCommand?>(null) }
        LaunchedEffect(Unit) {
            IosThreadDeepLinkBridge.stream().collect { raw ->
                platformThreadDeepLink = raw
            }
        }
        LaunchedEffect(Unit) {
            IosBoardDeepLinkBridge.stream().collect { raw ->
                platformBoardDeepLink = raw
            }
        }
        // FutachaAiCommandBridge is intentionally single-consumer. iOS owns
        // it at the profile root and injects each command into the active
        // profile, so compatibility mode cannot lose commands to the modern
        // screen collector.
        LaunchedEffect(Unit) {
            FutachaAiCommandBridge.commands.collect { command ->
                platformAiCommand = command
            }
        }
        val sharedRepository = remember(httpClient, cookieRepository, stateStore) {
            val api = com.valoser.futacha.shared.network.HttpBoardApi(httpClient)
            DefaultBoardRepository(
                api = object : BoardApi by api {},
                parser = createHtmlParser(),
                cookieRepository = cookieRepository,
                diagnosticFileSystem = fileSystem,
                catalogFetchSettingsProvider = {
                    CatalogFetchSettings(rows = stateStore.catalogFetchRows.first()).normalized()
                }
            )
        }
        DisposableEffect(sharedRepository) {
            onDispose { sharedRepository.closeAsync() }
        }
        LaunchedEffect(stateStore, compatibilityStore, profileStore, modeSwitchCoordinator, initializationAttempt) {
            runSuspendCatchingPreservingCancellation {
                stateStore.seedIfEmpty(
                    AppStateSeedDefaults(
                        boards = mockBoardSummaries,
                        history = mockThreadHistory,
                        selfPostIdentifierMap = emptyMap(),
                        catalogModeMap = emptyMap(),
                        lastUsedDeleteKey = ""
                    )
                )
                compatibilityStore.initialize()
                if (issue78ArchiveFixture) {
                    seedIosIssue78ArchiveFixture(compatibilityStore)
                }
                // ChangeLogActivity in the reference APK stores this value in
                // NSUserDefaults. Migrate it once into the namespaced KMP
                // compatibility store; the argument domain also lets iOS UI
                // tests start from an explicit already-read version.
                val launchArguments = NSProcessInfo.processInfo.arguments.filterIsInstance<String>()
                val explicitUsedVersion = launchArguments.indexOf("-commonUsedVersion")
                    .takeIf { it >= 0 }
                    ?.let { launchArguments.getOrNull(it + 1) }
                resolveCommonUsedVersionToStore(
                    stored = compatibilityStore.loadPreference("compat.commonUsedVersion"),
                    defaultsValue = NSUserDefaults.standardUserDefaults().stringForKey("commonUsedVersion"),
                    explicitArgument = explicitUsedVersion
                )?.let { usedVersion ->
                    compatibilityStore.savePreference("compat.commonUsedVersion", usedVersion)
                }
                modeSwitchCoordinator.recoverIfNeeded().getOrThrow()
                val boards = stateStore.boards.first()
                compatibilityStore.bootstrapBoardsIfNeeded(boards)
                compatibilityStore.importModernHistory(stateStore.history.first())
                if (compatibilityStore.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY) != "OFF") {
                    val startupProfile = profileStore.readActiveProfile()
                    val startupGeneration = profileStore.readGeneration()
                    IosArchiveReportScheduler.enqueueStartup(compatibilityStore) {
                        profileStore.isGenerationCommitAllowed(startupProfile, startupGeneration)
                    }
                }
            }.onSuccess {
                initializationComplete = true
                initializationError = null
                if (issue78ArchiveFixture) {
                    platformThreadDeepLink = IOS_ISSUE_78_THREAD_URL
                }
            }.onFailure { error ->
                Logger.e("MainViewController", "Failed to initialize iOS compatibility profile", error)
                initializationError = error.message ?: "互換モードの初期化に失敗しました"
            }
        }
        LaunchedEffect(initializationComplete, stateStore, compatibilityStore, activeProfile) {
            if (!initializationComplete) return@LaunchedEffect
            coroutineScope {
                launch {
                    if (activeProfile == ExperienceProfile.TOSHIAKI_COMPAT) {
                        compatibilityStore.boards.collect { compatBoards ->
                            val current = stateStore.boards.first()
                            val synchronized = synchronizeModernBoardsFromCompatibility(current, compatBoards)
                            if (synchronized != current) stateStore.setBoards(synchronized)
                        }
                    } else {
                        var hasObservedLoadedModernBoards = false
                        stateStore.boards.collect { modernBoards ->
                            if (modernBoards.isNotEmpty()) hasObservedLoadedModernBoards = true
                            if (!hasObservedLoadedModernBoards) return@collect
                            val desired = modernBoardsToCompatibility(modernBoards)
                            val desiredKeys = desired.mapTo(mutableSetOf()) { it.key }
                            compatibilityStore.boards.first()
                                .filterNot { it.key in desiredKeys }
                                .forEach { compatibilityStore.deleteBoard(it.key) }
                            compatibilityStore.upsertBoards(desired)
                        }
                    }
                }
                launch {
                    compatibilityStore.history
                        .distinctUntilChangedBy(::compatibilityHistorySharedMetadata)
                        .collect { compatHistory ->
                        val boards = stateStore.boards.first()
                        stateStore.updateHistory { current ->
                            mergeCompatibilityHistory(current, compatHistory, boards)
                        }
                        }
                }
            }
        }
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
        LaunchedEffect(
            stateStore,
            compatibilityStore,
            profileStore,
            httpClient,
            fileSystem,
            autoSavedThreadRepository
        ) {
            try {
                Logger.d("MainViewController", "Starting background refresh enabled-state collector")
                combine(
                    stateStore.isBackgroundRefreshEnabled,
                    stateStore.isWatchAlertEnabled,
                    compatibilityStore.preferences,
                    profileStore.activeProfile,
                    profileStore.generation
                ) { backgroundEnabled, watchAlertEnabled, compatPreferences, profile, generation ->
                    val archiveReportEnabled = compatPreferences[ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY] != "OFF"
                    val compatEnabled = when (profile) {
                        ExperienceProfile.FUTACHA -> false
                        ExperienceProfile.TOSHIAKI_COMPAT -> {
                            val update = parseCompatForegroundNetworkPolicy(
                                compatPreferences["compat.background.backgroundThreadUpdateCheck"]
                            )
                            val existence = parseCompatForegroundNetworkPolicy(
                                compatPreferences["compat.background.backgroundThreadExistCheck"]
                            )
                            update != CompatForegroundNetworkPolicy.NONE ||
                                existence != CompatForegroundNetworkPolicy.NONE ||
                                com.valoser.futacha.shared.compat.compatWatchEnabled(compatPreferences) ||
                                archiveReportEnabled
                        }
                    }
                    IosBackgroundScheduleState(
                        profile = profile,
                        generation = generation,
                        enabled = when (profile) {
                            ExperienceProfile.FUTACHA -> backgroundEnabled || watchAlertEnabled || archiveReportEnabled ||
                                com.valoser.futacha.shared.compat.sharedFeatureRefreshEnabled(compatPreferences)
                            ExperienceProfile.TOSHIAKI_COMPAT -> compatEnabled
                        }
                    )
                }
                    .distinctUntilChanged()
                    .onEach { schedule ->
                        Logger.d("MainViewController", "Background refresh state changed: $schedule")
                        // Read by registerIosBackgroundRefreshTask on the next
                        // (possibly background-only) launch.
                        NSUserDefaults.standardUserDefaults().setBool(
                            schedule.enabled,
                            forKey = IOS_BACKGROUND_TASK_ENABLED_DECISION_KEY
                        )
                        configureIosBackgroundRefresh(
                            enabled = schedule.enabled,
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
            }
        }

        if (initializationComplete) {
            // The compatibility UI is common code and remains Android-default
            // unless a host supplies this callback.  iOS runs the operation
            // through its profile generation gate so a late response cannot
            // mutate the SQLite store after a mode switch.
            val compatibilityHistoryRefresh = remember(
                activeProfile,
                profileGeneration,
                profileStore,
                compatibilityStore,
                sharedRepository
            ) {
                suspend {
                    if (!profileStore.isGenerationCommitAllowed(
                            ExperienceProfile.TOSHIAKI_COMPAT,
                            profileGeneration
                        )
                    ) {
                        Result.failure(IllegalStateException("としあき(仮)モードが切り替わりました"))
                    } else {
                        runSuspendCatchingPreservingCancellation {
                            val result = withTimeout(IOS_COMPAT_MANUAL_HISTORY_REFRESH_TIMEOUT_MILLIS) {
                                refreshCompatTabsInBackground(
                                    store = compatibilityStore,
                                    repository = sharedRepository,
                                    maxTabs = IOS_COMPAT_MANUAL_HISTORY_REFRESH_MAX_TABS,
                                    checkUpdates = true,
                                    checkExistence = true,
                                    checkWatchWords = true,
                                    commitGate = { commit ->
                                        profileStore.runIfGenerationCurrent(
                                            ExperienceProfile.TOSHIAKI_COMPAT,
                                            profileGeneration,
                                            commit
                                        )
                                    }
                                )
                            }
                            "履歴を更新しました（更新 ${result.updatedTabs}件、終了 ${result.deadTabs}件、失敗 ${result.failures}件）"
                        }
                    }
                }
            }
            val compatibilityArchiveCommitGate = remember(activeProfile, profileGeneration, profileStore) {
                suspend {
                    profileStore.isGenerationCommitAllowed(
                        ExperienceProfile.TOSHIAKI_COMPAT,
                        profileGeneration
                    )
                }
            }
            val profileController = ExperienceProfileUiController(
                isAvailable = true,
                activeProfile = activeProfile,
                sessionGeneration = profileGeneration,
                isSessionActive = profileSessionActive,
                switchInProgress = profileSwitchInProgress,
                lastError = profileSwitchError,
                isSessionAuthoritativelyCurrent = { token ->
                    profileStore.isGenerationCommitAllowed(token.profile, token.generation)
                },
                requestSwitch = switchRequest@{ target ->
                    if (target == activeProfile || profileSwitchInProgress) return@switchRequest
                    profileSwitchInProgress = true
                    profileSessionActive = false
                    profileScope.launch {
                        try {
                            profileSwitchError = null
                            // Copy the shared dataset before persisting the new
                            // profile so a cold recompose can read it at once.
                            if (target == ExperienceProfile.TOSHIAKI_COMPAT) {
                                val boards = stateStore.boards.first()
                                compatibilityStore.bootstrapBoardsIfNeeded(boards)
                                compatibilityStore.importModernBoards(boards)
                                compatibilityStore.importModernHistory(stateStore.history.first())
                            } else if (activeProfile == ExperienceProfile.TOSHIAKI_COMPAT) {
                                val compatBoards = compatibilityStore.boards.first()
                                val compatHistory = compatibilityStore.history.first()
                                val boards = stateStore.boards.first()
                                val mergedBoards = synchronizeModernBoardsFromCompatibility(boards, compatBoards)
                                if (mergedBoards != boards) stateStore.setBoards(mergedBoards)
                                stateStore.updateHistory { current ->
                                    mergeCompatibilityHistory(current, compatHistory, mergedBoards)
                                }
                            }
                            modeSwitchCoordinator.switchTo(
                                target = target,
                                preferredFutachaIcon = preferredAppIcon,
                                quiesceOldProfile = { BackgroundRefreshManager.cancel() }
                            ).getOrThrow()
                            // activeProfile/generation flows now change and the
                            // key below disposes every old Compose coroutine.
                            profileSessionActive = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Logger.e("MainViewController", "iOS profile switch failed", error)
                            profileSwitchError = error.message ?: "モードを切り替えられませんでした"
                            profileSessionActive = true
                        } finally {
                            profileSwitchInProgress = false
                        }
                    }
                }
            )
            key(activeProfile, profileGeneration) {
                CompositionLocalProvider(
                    LocalExperienceProfileUiController provides profileController,
                    LocalIosReviewCompliance provides IosReviewCompliance(isEnabled = true)
                ) {
                    FutachaApp(
                        originalMediaSession = originalMediaSession,
                        stateStore = stateStore,
                        versionChecker = versionChecker,
                        httpClient = httpClient,
                        sharedRepository = sharedRepository,
                        fileSystem = fileSystem,
                        cookieRepository = cookieRepository,
                        autoSavedThreadRepository = autoSavedThreadRepository,
                        compatibilityHistoryRefresh = compatibilityHistoryRefresh,
                        experienceProfile = activeProfile,
                        compatibilityStore = compatibilityStore,
                        platformThreadDeepLink = platformThreadDeepLink,
                        onPlatformThreadDeepLinkConsumed = { consumed ->
                            if (platformThreadDeepLink == consumed) {
                                platformThreadDeepLink = null
                            }
                        },
                        platformBoardDeepLink = platformBoardDeepLink,
                        onPlatformBoardDeepLinkConsumed = { consumed ->
                            if (platformBoardDeepLink == consumed) {
                                platformBoardDeepLink = null
                            }
                        },
                        platformAiCommand = platformAiCommand,
                        onPlatformAiCommandConsumed = { consumed ->
                            if (platformAiCommand === consumed) {
                                platformAiCommand = null
                            }
                        },
                        consumeAiCommandBridge = false,
                        onArchiveReportEnqueued = { sendableCount ->
                            IosArchiveReportScheduler.enqueueAfterView(
                                compatibilityStore,
                                sendableCount,
                                compatibilityArchiveCommitGate
                            )
                        },
                        onArchiveReportEnabledChanged = { enabled ->
                            if (enabled) IosArchiveReportScheduler.enqueueStartup(
                                compatibilityStore,
                                compatibilityArchiveCommitGate
                            )
                            else IosArchiveReportScheduler.cancel()
                        },
                        onExitApplication = { profileController.requestSwitch(ExperienceProfile.FUTACHA) }
                    )
                }
            }
        } else if (initializationError != null) {
            // Every step above is idempotent, so a retry simply runs them again
            // instead of leaving the launch on a blank screen.
            IosInitializationErrorScreen(
                message = initializationError.orEmpty(),
                onRetry = {
                    initializationError = null
                    initializationAttempt += 1
                }
            )
        }
    }
}

@Composable
private fun IosInitializationErrorScreen(message: String, onRetry: () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "アプリを起動できませんでした",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("再試行")
            }
        }
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

/**
 * Serializes the BGTask and the Watch-triggered runs of [runIosBackgroundRefresh].
 * Their compatibility refresh and shared-feature patrol have no lock of their
 * own, so overlapping runs reported the same watch matches twice.
 */
private val iosBackgroundRefreshRunMutex = Mutex()

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
    if (!iosBackgroundRefreshRunMutex.tryLock()) {
        Logger.d("BackgroundRefresh", "iOS background refresh already running; skipping duplicate run")
        return
    }
    try {
        runIosBackgroundRefreshLocked(
            stateStore = stateStore,
            httpClient = httpClient,
            fileSystem = fileSystem,
            autoSaveRepo = autoSaveRepo,
            cookieRepository = cookieRepository,
            maxThreadsPerRun = maxThreadsPerRun,
            autoSaveBudgetMillis = autoSaveBudgetMillis,
            maxAutoSavesPerRun = maxAutoSavesPerRun,
            refreshTimeoutMillis = refreshTimeoutMillis
        )
    } finally {
        iosBackgroundRefreshRunMutex.unlock()
    }
}

private suspend fun runIosBackgroundRefreshLocked(
    stateStore: com.valoser.futacha.shared.state.AppStateStore,
    httpClient: io.ktor.client.HttpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveRepo: SavedThreadRepository?,
    cookieRepository: CookieRepository?,
    maxThreadsPerRun: Int,
    autoSaveBudgetMillis: Long,
    maxAutoSavesPerRun: Int,
    refreshTimeoutMillis: Long
) {
    val profileStore = IosAppGraph.experienceProfileStore
    val activeProfile = profileStore.readActiveProfile()
    val expectedGeneration = profileStore.readGeneration()
    com.valoser.futacha.shared.ui.image.initializeOriginalMediaCache(
        IosAppGraph.originalMediaSessionFor(httpClient),
        platformContext = null,
        lightweightMode = stateStore.isLightweightModeEnabled.first() ||
            com.valoser.futacha.shared.util.detectDevicePerformanceProfile(null).isLowSpec
    )
    val sharedClientApi = com.valoser.futacha.shared.network.HttpBoardApi(httpClient)
    // Keep shared HttpClient ownership in MainViewController. Background repo closes only its own state.
    val nonClosingApi = object : BoardApi by sharedClientApi {}
    val repo = DefaultBoardRepository(
        api = nonClosingApi,
        parser = createHtmlParser(),
        cookieRepository = cookieRepository,
        diagnosticFileSystem = fileSystem,
        catalogFetchSettingsProvider = {
            CatalogFetchSettings(rows = stateStore.catalogFetchRows.first()).normalized()
        }
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
        if (activeProfile == ExperienceProfile.TOSHIAKI_COMPAT) {
            val store = IosAppGraph.compatibilityStore
            store.initialize()
            val preferences = store.preferences.first()
            val updatePolicy = parseCompatForegroundNetworkPolicy(
                preferences["compat.background.backgroundThreadUpdateCheck"]
            )
            val existencePolicy = parseCompatForegroundNetworkPolicy(
                preferences["compat.background.backgroundThreadExistCheck"]
            )
            val watchWordsEnabled = com.valoser.futacha.shared.compat.compatWatchAllowed(preferences, isCompatWifiConnected(null))
            val archiveReportEnabled = preferences[ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY] != "OFF"
            val wifi = isCompatWifiConnected(null)
            fun allowed(policy: CompatForegroundNetworkPolicy): Boolean = when (policy) {
                CompatForegroundNetworkPolicy.ALWAYS -> true
                CompatForegroundNetworkPolicy.WIFI_ONLY -> wifi
                CompatForegroundNetworkPolicy.NONE -> false
            }
            val updateAllowed = allowed(updatePolicy)
            val existenceAllowed = allowed(existencePolicy)
            if (!updateAllowed && !existenceAllowed && !watchWordsEnabled && !archiveReportEnabled) {
                Logger.d("BackgroundRefresh", "iOS compatibility background refresh disabled; skipping run")
                return
            }
            withTimeout(refreshTimeoutMillis) {
                if (updateAllowed || existenceAllowed || watchWordsEnabled) {
                    val watchResult = refreshCompatTabsInBackground(
                        store = store,
                        repository = repo,
                        maxTabs = maxThreadsPerRun,
                        checkUpdates = updateAllowed,
                        checkExistence = existenceAllowed,
                        checkWatchWords = watchWordsEnabled,
                        commitGate = { commit ->
                            profileStore.runIfGenerationCurrent(
                                ExperienceProfile.TOSHIAKI_COMPAT,
                                expectedGeneration,
                                commit
                            )
                        }
                    )
                    if (preferences[com.valoser.futacha.shared.compat.COMPAT_WATCH_NOTIFY_KEY] != "OFF" &&
                        profileStore.isGenerationCommitAllowed(ExperienceProfile.TOSHIAKI_COMPAT, expectedGeneration)) {
                        val matches = watchResult.newWatchMatches.map { match ->
                            com.valoser.futacha.shared.service.CatalogWatchAlertMatch(
                                threadId = match.history.threadNo,
                                boardId = match.history.boardKey,
                                boardName = match.history.boardName,
                                boardUrl = match.history.originalUrl.substringBefore("/res/"),
                                title = match.history.title,
                                titleImageUrl = match.history.thumbnailUrl.orEmpty(),
                                replyCount = match.history.replyCount,
                                detectedAtEpochMillis = match.history.contentUpdatedAtEpochMillis
                            )
                        }
                        notifyNewIosWatchAlertMatches(matches)
                    }
                    if (updateAllowed || existenceAllowed) {
                        val completedAt = compatForegroundLastCheckStoredValue(
                            kotlin.time.Clock.System.now().toEpochMilliseconds()
                        )
                        profileStore.runIfGenerationCurrent(
                            ExperienceProfile.TOSHIAKI_COMPAT,
                            expectedGeneration
                        ) {
                            if (updateAllowed) {
                                store.savePreference(
                                    COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE,
                                    completedAt
                                )
                            }
                            if (existenceAllowed) {
                                store.savePreference(
                                    COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE,
                                    completedAt
                                )
                            }
                        }
                    }
                }
                if (archiveReportEnabled && profileStore.isGenerationCommitAllowed(
                        ExperienceProfile.TOSHIAKI_COMPAT,
                        expectedGeneration
                    )
                ) {
                    IosArchiveReportScheduler.processNow(store) {
                        profileStore.isGenerationCommitAllowed(
                            ExperienceProfile.TOSHIAKI_COMPAT,
                            expectedGeneration
                        )
                    }
                }
            }
            Logger.d("BackgroundRefresh", "Completed iOS compatibility background refresh")
            return
        }
        val backgroundEnabled = stateStore.isBackgroundRefreshEnabled.first()
        val watchAlertEnabled = stateStore.isWatchAlertEnabled.first()
        val archiveStore = IosAppGraph.compatibilityStore
        archiveStore.initialize()
        val archiveReportEnabled = archiveStore.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY) != "OFF"
        val sharedFeaturesEnabled = com.valoser.futacha.shared.compat.sharedFeatureRefreshEnabled(archiveStore.preferences.first())
        if (!backgroundEnabled && !watchAlertEnabled && !archiveReportEnabled && !sharedFeaturesEnabled) {
            Logger.d("BackgroundRefresh", "iOS background refresh disabled; skipping run")
            return
        }
        Logger.d("BackgroundRefresh", "Starting iOS background refresh run (maxThreadsPerRun=$maxThreadsPerRun, watchAlert=$watchAlertEnabled)")
        withTimeout(refreshTimeoutMillis) {
            if (sharedFeaturesEnabled) {
                com.valoser.futacha.shared.compat.refreshSharedFeatures(archiveStore, repo, isCompatWifiConnected(null),
                    maxTabs = maxThreadsPerRun, onNewMatches = { matches ->
                        val alerts = matches.map { match -> com.valoser.futacha.shared.service.CatalogWatchAlertMatch(
                            threadId = match.history.threadNo, boardId = match.history.boardKey,
                            boardName = match.history.boardName, boardUrl = match.history.originalUrl.substringBefore("/res/"),
                            title = match.history.title, titleImageUrl = match.history.thumbnailUrl.orEmpty(),
                            replyCount = match.history.replyCount, detectedAtEpochMillis = match.history.contentUpdatedAtEpochMillis) }
                        notifyNewIosWatchAlertMatches(alerts)
                    }, commitGate = { commit ->
                        profileStore.runIfGenerationCurrent(ExperienceProfile.FUTACHA, expectedGeneration, commit)
                    },
                    // Leave most of the short iOS window for the history refresh below.
                    budgetMillis = refreshTimeoutMillis / 3)
            }
            if (backgroundEnabled) {
                // A foreground history refresh may hold HistoryRefresher's
                // process lock. Skip only this step: the watch alert and
                // archive report steps below are independent of it.
                try {
                    refresher.refresh(
                        autoSaveBudgetMillis = autoSaveBudgetMillis,
                        maxThreadsPerRun = maxThreadsPerRun,
                        maxAutoSavesPerRun = maxAutoSavesPerRun,
                        historyCommitGate = { commit ->
                            profileStore.runIfGenerationCurrent(
                                ExperienceProfile.FUTACHA,
                                expectedGeneration,
                                commit
                            )
                        },
                        autoSaveCommitGate = { commit ->
                            profileStore.runIfGenerationCurrent(
                                ExperienceProfile.FUTACHA,
                                expectedGeneration,
                                commit
                            )
                        }
                    )
                } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
                    Logger.d("BackgroundRefresh", "History refresh already running; skipping only the history step")
                }
            }
            if (watchAlertEnabled && profileStore.isGenerationCommitAllowed(ExperienceProfile.FUTACHA, expectedGeneration)) {
                try {
                    val result = CatalogWatchAlertRefresher(
                        stateStore = stateStore,
                        repository = repo,
                        dispatcher = AppDispatchers.io
                    ).refresh()
                    val newMatches = notifyNewIosWatchAlertMatches(result.matches)
                    if (newMatches.isNotEmpty()) {
                        Logger.d("BackgroundRefresh", "Detected ${newMatches.size} iOS watch alert match(es)")
                    }
                    if (result.failureCount > 0) {
                        Logger.w("BackgroundRefresh", "iOS watch alert partial failures: ${result.failureCount}")
                    }
                } catch (e: CatalogWatchAlertRefresher.RefreshAlreadyRunningException) {
                    Logger.d("BackgroundRefresh", "Catalog watch alert refresh already running; skipping only that step")
                }
            }
            if (archiveReportEnabled && profileStore.isGenerationCommitAllowed(
                    ExperienceProfile.FUTACHA,
                    expectedGeneration
                )
            ) {
                IosArchiveReportScheduler.processNow(archiveStore) {
                    profileStore.isGenerationCommitAllowed(ExperienceProfile.FUTACHA, expectedGeneration)
                }
            }
        }
        Logger.d("BackgroundRefresh", "Completed iOS background refresh run successfully")
    } catch (e: TimeoutCancellationException) {
        Logger.w("BackgroundRefresh", "iOS background refresh timed out after ${refreshTimeoutMillis}ms")
        throw e
    } catch (e: CancellationException) {
        Logger.w("BackgroundRefresh", "iOS background refresh run cancelled")
        throw e
    } finally {
        refresher.close()
        Logger.d("BackgroundRefresh", "Closing temporary iOS background repository")
        val closeJob = repo.closeAsync()
        withContext(NonCancellable) {
            awaitIosBackgroundRepositoryClose(
                closeJob = closeJob,
                timeoutMillis = IOS_BG_REPOSITORY_CLOSE_TIMEOUT_MILLIS
            )
        }
    }
}

private suspend fun notifyIosWatchAlertMatches(matches: List<CatalogWatchAlertMatch>) {
    if (matches.isEmpty()) return
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val granted = suspendCancellableCoroutine { continuation ->
        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { isGranted, error ->
            if (error != null) {
                Logger.w("BackgroundRefresh", "Failed to request iOS notification authorization: ${error.localizedDescription}")
            }
            if (continuation.isActive) {
                continuation.resume(error == null && isGranted)
            }
        }
    }
    if (!granted) return
    val first = matches.first()
    val title = if (matches.size == 1) {
        "監視ワードに一致しました"
    } else {
        "監視ワードに ${matches.size} 件一致しました"
    }
    val body = if (matches.size == 1) {
        "${first.boardName}: ${first.title}"
    } else {
        "${first.boardName}: ${first.title} ほか"
    }
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound())
    }
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = "watch-alert-${first.detectedAtEpochMillis}",
        content = content,
        trigger = null
    )
    suspendCancellableCoroutine { continuation ->
        center.addNotificationRequest(request) { notificationError ->
            if (notificationError != null) {
                Logger.w("BackgroundRefresh", "Failed to post iOS watch alert notification: ${notificationError.localizedDescription}")
            }
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }
}

/**
 * Guards the read → record → notify update of the single NSUserDefaults
 * ledger shared by every watch notification path (ふたちゃ catalog alerts,
 * shared-feature patrol and the compatibility refresh).
 */
private val iosWatchAlertNotificationMutex = Mutex()

/**
 * Notifies only the matches not yet in the ledger and returns them. The
 * ledger is written before the notification is posted, so an expired task
 * can lose one notification but never repeat it.
 */
private suspend fun notifyNewIosWatchAlertMatches(
    matches: List<CatalogWatchAlertMatch>
): List<CatalogWatchAlertMatch> {
    if (matches.isEmpty()) return emptyList()
    return iosWatchAlertNotificationMutex.withLock {
        val fresh = filterNewIosWatchAlertMatches(matches)
        if (fresh.isNotEmpty()) {
            markIosWatchAlertMatchesNotified(fresh)
            notifyIosWatchAlertMatches(fresh)
        }
        fresh
    }
}

private fun filterNewIosWatchAlertMatches(
    matches: List<CatalogWatchAlertMatch>
): List<CatalogWatchAlertMatch> {
    return WatchAlertNotificationLedger.filterNewMatches(
        serializedEntries = NSUserDefaults.standardUserDefaults()
            .stringForKey(IOS_NOTIFIED_WATCH_ALERT_ENTRIES_KEY),
        matches = matches
    )
}

private fun markIosWatchAlertMatchesNotified(matches: List<CatalogWatchAlertMatch>) {
    if (matches.isEmpty()) return
    val defaults = NSUserDefaults.standardUserDefaults()
    val serialized = WatchAlertNotificationLedger.markMatches(
        serializedEntries = defaults.stringForKey(IOS_NOTIFIED_WATCH_ALERT_ENTRIES_KEY),
        matches = matches,
        nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
    )
    defaults.setObject(
        serialized,
        forKey = IOS_NOTIFIED_WATCH_ALERT_ENTRIES_KEY
    )
}

private const val IOS_NOTIFIED_WATCH_ALERT_ENTRIES_KEY = "watch_alert_notified_match_entries"
