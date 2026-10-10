package com.valoser.futacha.shared

import com.valoser.futacha.shared.media.source.bindOriginalMediaSource
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.background.BackgroundRefreshManager
import com.valoser.futacha.shared.background.IOS_WATCH_REFRESH_PLAN
import com.valoser.futacha.shared.background.IosBackgroundRefreshPlan
import com.valoser.futacha.shared.background.IosBackgroundRefreshStage
import com.valoser.futacha.shared.background.iosBackgroundRefreshPlanFor
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.decodeAiQueryValue
import com.valoser.futacha.shared.ai.FutachaAiCommandArrivals
import com.valoser.futacha.shared.compat.modeSwitchFailureMessage
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
import com.valoser.futacha.shared.ui.consumePlatformAiCommand
import com.valoser.futacha.shared.ui.enqueuePlatformAiCommand
import com.valoser.futacha.shared.ui.PLATFORM_AI_COMMAND_QUEUE_MAX
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
import com.valoser.futacha.shared.watch.ThreadReadAloudRemoteControl
import com.valoser.futacha.shared.watch.WatchCommand
import com.valoser.futacha.shared.watch.WatchCommandType
import com.valoser.futacha.shared.watch.WatchReadAloudStatusStore
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchSnapshotBuilder
import com.valoser.futacha.shared.watch.WatchThreadKey
import com.valoser.futacha.shared.watch.encodeWatchSnapshotWithinPayload
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import com.valoser.futacha.shared.version.createVersionChecker
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.CoroutineScope
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
// WatchConnectivity rejects application contexts / messages above about 64KB; keep
// headroom for the dictionary wrapper and trim the snapshot to fit.
private const val IOS_WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES = 60 * 1024
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
        // S4-3: a `futacha://ai` link (also open_thread / open_thread_url) is
        // left to the AI command path (SwiftUI falls back to it when this
        // returns false), where the "AIアプリ操作" setting, the URL host check
        // and the lock rules apply in both modes. Routed as a plain thread link
        // it bypassed them.
        if (isIosFutachaAiLink(raw)) return false
        val normalized = normalizeIosThreadDeepLink(raw) ?: return false
        return links.tryEmit(normalized)
    }

    fun stream() = links

    /**
     * The replayed link only bridges the gap until a root has started
     * collecting (cold launch). Once a root took it, it must not be replayed to
     * a root created later (the second iPad window), which would reopen a
     * thread the user already left.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearReplay() = links.resetReplayCache()
}


/** Keeps an incoming Watch board selection until the profile root is ready. */
private object IosBoardDeepLinkBridge {
    private val links = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)

    fun submit(raw: String): Boolean {
        val canonical = canonicalizeBoardUrl(raw) ?: return false
        return links.tryEmit(canonical)
    }

    fun stream() = links

    /** See [IosThreadDeepLinkBridge.clearReplay]. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearReplay() = links.resetReplayCache()
}

/** Called from SwiftUI's `onOpenURL` for both cold and warm launches. */
fun submitIosThreadDeepLink(raw: String): Boolean = IosThreadDeepLinkBridge.submit(raw)

/** Whether [raw] is a `futacha://ai` command link (S4-3). */
internal fun isIosFutachaAiLink(raw: String): Boolean {
    val trimmed = raw.trim()
    val prefix = "futacha://ai"
    if (!trimmed.startsWith(prefix, ignoreCase = true)) return false
    val next = trimmed.getOrNull(prefix.length) ?: return true
    return next == '?' || next == '/' || next == '#'
}

internal fun normalizeIosThreadDeepLink(raw: String): String? {
    if (raw.length > IOS_THREAD_DEEP_LINK_MAX_CHARS) return null
    val trimmed = raw.trim()
    // Emit the canonical `https://<official host>/...` form, never the raw
    // value: only that string is known to name the host it was checked for (S4-1).
    canonicalizeThreadUrl(trimmed)?.let { return it.canonicalUrl }
    // App links never reach this branch any more: `futacha://ai` links take the
    // AI command path so the AIアプリ操作 setting applies (S4-3). It remains for
    // direct callers that already hold an open_thread command URL.
    parseFutachaAiDeepLink(trimmed, source = "ios-thread-deep-link")
        ?.takeIf {
            it.action == FutachaAiAction.OpenThread ||
                it.action == FutachaAiAction.OpenThreadFromUrl
        }
        ?.let(::resolveIosAiThreadTarget)
        ?.let { target ->
            canonicalizeThreadUrl(target)?.let { return it.canonicalUrl }
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
    return canonicalizeThreadUrl(value)?.canonicalUrl
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
    private val refreshController = IosWatchRefreshController(scope) {
        val httpClient = IosAppGraph.acquireHttpClient()
        try {
            runIosBackgroundRefresh(
                stateStore = IosAppGraph.stateStore,
                httpClient = httpClient,
                fileSystem = IosAppGraph.fileSystem,
                autoSaveRepo = IosAppGraph.autoSavedThreadRepository,
                cookieRepository = IosAppGraph.cookieRepository,
                // A background task of about 30 s that also sends the snapshot (H4-1).
                plan = IOS_WATCH_REFRESH_PLAN
            )
        } finally {
            IosAppGraph.releaseHttpClient()
        }
    }

    fun requestSnapshotJson(completion: (String?) -> Unit) {
        scope.launch {
            val encoded = runSuspendCatchingPreservingCancellation {
                val snapshot = buildSnapshot()
                encodeWatchSnapshotWithinPayload(snapshot, IOS_WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) { candidate ->
                    json.encodeToString(WatchSnapshot.serializer(), candidate)
                } ?: run {
                    Logger.w("IosWatchSnapshotBridge", "Dropped watch snapshot because it cannot fit the payload limit")
                    null
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
                return handleIosWatchPlaybackReduction(
                    command,
                    ThreadReadAloudRemoteControl.Command.Pause,
                    FutachaAiAction.PauseThreadReadAloud
                )
            }
            WatchCommandType.StopReadAloudOnPhone -> {
                return handleIosWatchPlaybackReduction(
                    command,
                    ThreadReadAloudRemoteControl.Command.Stop,
                    FutachaAiAction.StopThreadReadAloud
                )
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
    private fun startWatchRefreshIfAllowed(): IosWatchRefreshDecision = refreshController.startIfAllowed()

    fun invokeWhenRefreshIdle(onIdle: () -> Unit) = refreshController.invokeWhenIdle(onIdle)

    fun cancelRefresh() = refreshController.cancel()

    /**
     * Pause/stop act now on the thread screen that is still composed, also
     * while the app is in the background (audio keeps it alive), like
     * Android's WatchSyncManager (C-4/D7). Screens that do not register for
     * remote control (the other modes) get the command through the AI queue
     * with a short lifetime, so a stop that cannot be delivered while the app
     * is suspended is dropped instead of running when the app is opened later.
     * The check runs on the main thread, where composition registers.
     */
    private fun handleIosWatchPlaybackReduction(
        command: WatchCommand,
        remoteCommand: ThreadReadAloudRemoteControl.Command,
        action: FutachaAiAction
    ): Boolean {
        val boardId = command.boardId?.takeIf { it.isNotBlank() } ?: return false
        val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() } ?: return false
        val threadId = command.threadId?.takeIf { it.isNotBlank() } ?: return false
        dispatch_async(dispatch_get_main_queue()) {
            val handled = ThreadReadAloudRemoteControl.dispatch(remoteCommand, boardId, boardUrl, threadId)
            if (!handled) {
                enqueueIosWatchThreadAction(command, action, IOS_WATCH_UI_COMMAND_MAX_AGE_MILLIS)
            }
        }
        return true
    }

    private fun enqueueIosWatchThreadAction(
        command: WatchCommand,
        action: FutachaAiAction,
        maxAgeMillis: Long? = null
    ): Boolean {
        val boardId = command.boardId?.takeIf { it.isNotBlank() } ?: return false
        val boardUrl = command.boardUrl?.takeIf { it.isNotBlank() } ?: return false
        val threadId = command.threadId?.takeIf { it.isNotBlank() } ?: return false
        val aiCommand = FutachaAiCommand(
            action = action,
            parameters = buildIosWatchCommandParameters(command) {
                put("boardId", boardId)
                put("boardUrl", boardUrl)
                put("threadId", threadId)
            },
            source = "watchos"
        )
        return if (maxAgeMillis == null) {
            FutachaAiCommandBridge.enqueue(aiCommand)
        } else {
            FutachaAiCommandBridge.enqueue(aiCommand, maxAgeMillis)
        }
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

/**
 * Calls [onIdle] on the main thread once the Watch-requested refresh (if any)
 * has finished, so Swift can end the background task it holds for it.
 */
fun invokeWhenIosWatchRefreshIdle(onIdle: () -> Unit) {
    IosWatchSnapshotBridge.invokeWhenRefreshIdle {
        dispatch_async(dispatch_get_main_queue()) { onIdle() }
    }
}

/** Ends the Watch-requested refresh when iOS takes back its background time. */
fun cancelIosWatchRefresh() {
    IosWatchSnapshotBridge.cancelRefresh()
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

/** How long an undelivered Watch pause/stop may wait for the app UI. Same as Android. */
internal const val IOS_WATCH_UI_COMMAND_MAX_AGE_MILLIS = 60_000L

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
            !ExperienceProfile.fromPersistedValue(defaults.stringForKey(IOS_ACTIVE_PROFILE_KEY)).usesAppStateData ||
            (lastScreenDecision ?: true)
    Logger.d("MainViewController", "registerIosBackgroundRefreshTask(enabledAtLaunch=$enabledAtLaunch)")
    BackgroundRefreshManager.configure(enabledAtLaunch) { kind ->
        val httpClient = IosAppGraph.acquireHttpClient()
        try {
            runIosBackgroundRefresh(
                stateStore = IosAppGraph.stateStore,
                httpClient = httpClient,
                fileSystem = IosAppGraph.fileSystem,
                autoSaveRepo = IosAppGraph.autoSavedThreadRepository,
                cookieRepository = IosAppGraph.cookieRepository,
                plan = iosBackgroundRefreshPlanFor(kind)
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
        // The rollback gives the old profile a new generation, so the inline
        // settings error is rebuilt away; this notice survives it (M-2).
        var profileSwitchFailureNotice by remember { mutableStateOf<String?>(null) }
        var isFutachaAppUnlocked by remember { mutableStateOf(false) }
        var platformThreadDeepLink by remember { mutableStateOf<String?>(null) }
        var platformBoardDeepLink by remember { mutableStateOf<String?>(null) }
        var platformAiCommandQueue by remember { mutableStateOf<List<FutachaAiCommand>>(emptyList()) }
        LaunchedEffect(Unit) {
            IosThreadDeepLinkBridge.stream().collect { raw ->
                platformThreadDeepLink = raw
                IosThreadDeepLinkBridge.clearReplay()
            }
        }
        LaunchedEffect(Unit) {
            IosBoardDeepLinkBridge.stream().collect { raw ->
                platformBoardDeepLink = raw
                IosBoardDeepLinkBridge.clearReplay()
            }
        }
        // FutachaAiCommandBridge is intentionally single-consumer. iOS owns
        // it at the profile root and injects each command into the active
        // profile, so compatibility mode cannot lose commands to the modern
        // screen collector.
        // Commands are queued and the active profile receives the head; a
        // single slot let a second command overwrite one not yet consumed.
        // While the queue is full (e.g. Shortcuts piling up behind the app
        // lock) stop taking commands from the bridge instead of letting
        // enqueuePlatformAiCommand drop the oldest one silently. The bridge
        // channel then fills and further Shortcuts/Siri requests are refused
        // at enqueue time, so the caller is told "not accepted" (C-12).
        LaunchedEffect(Unit) {
            while (true) {
                snapshotFlow { platformAiCommandQueue.size }.first { it < PLATFORM_AI_COMMAND_QUEUE_MAX }
                val queued = FutachaAiCommandBridge.receiveQueued()
                // Keep the bridge enqueue time so a command waiting behind the
                // queue head is aged from its real arrival (C-12).
                FutachaAiCommandArrivals.record(queued.command, queued.enqueuedAt)
                platformAiCommandQueue = enqueuePlatformAiCommand(platformAiCommandQueue, queued.command)
            }
        }
        val platformAiCommand = platformAiCommandQueue.firstOrNull()
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
                // The modern history import runs after the first frame (below):
                // reading up to 20k history files kept the screen blank for seconds.
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
        LaunchedEffect(initializationComplete, stateStore, compatibilityStore) {
            if (!initializationComplete) return@LaunchedEffect
            // Like Android, copy the modern history in the background. The
            // import is idempotent and serialized by the store; a mode switch
            // to compatibility imports again before it commits, and the
            // compat -> modern merge below converges with it in either order.
            runSuspendCatchingPreservingCancellation {
                compatibilityStore.importModernHistory(stateStore.history.first())
            }.onFailure { error ->
                Logger.e("MainViewController", "Failed to import modern history into the compatibility store", error)
            }
        }
        // Background refreshes only check notification permission (G-10), so
        // ask here, while the app is visible, once a watch notification is on.
        val isAppForeground = rememberIosApplicationActive()
        LaunchedEffect(initializationComplete, isAppForeground, stateStore, compatibilityStore) {
            if (!initializationComplete || !isAppForeground) return@LaunchedEffect
            combine(stateStore.isWatchAlertEnabled, compatibilityStore.preferences) { futachaAlerts, preferences ->
                iosWatchNotificationsWanted(futachaAlerts, preferences)
            }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (wanted) {
                        runSuspendCatchingPreservingCancellation { requestIosNotificationAuthorizationIfUndetermined() }
                            .onFailure { Logger.w("MainViewController", "Notification permission check failed: ${it.message}") }
                    }
                }
        }
        LaunchedEffect(initializationComplete, stateStore, compatibilityStore, activeProfile) {
            if (!initializationComplete) return@LaunchedEffect
            coroutineScope {
                launch {
                    if (!activeProfile.usesAppStateData) {
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
                            val desiredKeys = com.valoser.futacha.shared.compat.compatibilityBoardSynchronizationKeys(modernBoards)
                            compatibilityStore.importModernBoards(modernBoards)
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
                        ExperienceProfile.FUTACHA, ExperienceProfile.FUTABER -> false
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
                            ExperienceProfile.FUTACHA, ExperienceProfile.FUTABER -> backgroundEnabled || watchAlertEnabled || archiveReportEnabled ||
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
            // Remembered: a new instance (fresh lambdas) on every recomposition of
            // this scope, e.g. each app-lock change, replaced the static local
            // and recomposed all of FutachaApp (M4-4).
            val profileController = remember(
                activeProfile, profileGeneration, profileSessionActive, profileSwitchInProgress,
                profileSwitchError, profileStore, stateStore, compatibilityStore, modeSwitchCoordinator, profileScope
            ) { ExperienceProfileUiController(
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
                            if (!target.usesAppStateData) {
                                val boards = stateStore.boards.first()
                                compatibilityStore.bootstrapBoardsIfNeeded(boards)
                                compatibilityStore.importModernBoards(boards)
                                compatibilityStore.importModernHistory(stateStore.history.first())
                            } else if (!activeProfile.usesAppStateData) {
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
                            profileSwitchFailureNotice = modeSwitchFailureMessage(error)
                            profileSessionActive = true
                        } finally {
                            profileSwitchInProgress = false
                        }
                    }
                }
            ) }
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
                            FutachaAiCommandArrivals.forget(consumed)
                            platformAiCommandQueue = consumePlatformAiCommand(platformAiCommandQueue, consumed)
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
                        onExitApplication = { profileController.requestSwitch(ExperienceProfile.FUTACHA) },
                        // ContentView.swift presents saved HTML only while unlocked.
                        onAppUnlockedChanged = { unlocked ->
                            isFutachaAppUnlocked = unlocked
                            publishIosAppUnlockedState(unlocked)
                        }
                    )
                }
            }
            // Outside FutachaApp, so gated on its lock state here (C-2): hidden,
            // not dismissed, while the lock screen is shown.
            profileSwitchFailureNotice?.takeIf { isFutachaAppUnlocked }?.let { message ->
                AlertDialog(
                    onDismissRequest = { profileSwitchFailureNotice = null },
                    title = { Text("モードを切り替えられませんでした") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { profileSwitchFailureNotice = null }) { Text("OK") }
                    }
                )
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
    BackgroundRefreshManager.configure(enabled) { kind ->
        val managedHttpClient = IosAppGraph.acquireHttpClient()
        try {
            runIosBackgroundRefresh(
                stateStore = stateStore,
                httpClient = managedHttpClient,
                fileSystem = fileSystem,
                autoSaveRepo = autoSaveRepo,
                cookieRepository = IosAppGraph.cookieRepository,
                plan = iosBackgroundRefreshPlanFor(kind)
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
    plan: IosBackgroundRefreshPlan
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
            plan = plan
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
    plan: IosBackgroundRefreshPlan
) {
    val maxThreadsPerRun = plan.maxThreadsPerRun
    val refreshTimeoutMillis = plan.totalTimeoutMillis
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
        maxConcurrency = 2,
        cursorNamespace = "background"
    )
    try {
        if (!activeProfile.usesAppStateData) {
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
                    suspend fun notifyRecordedWatchMatches(
                        recorded: List<com.valoser.futacha.shared.compat.CompatWatchMatch>
                    ) {
                        if (preferences[com.valoser.futacha.shared.compat.COMPAT_WATCH_NOTIFY_KEY] == "OFF" ||
                            !profileStore.isGenerationCommitAllowed(ExperienceProfile.TOSHIAKI_COMPAT, expectedGeneration)
                        ) return
                        val matches = recorded.map { match ->
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
                    refreshCompatTabsInBackground(
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
                        },
                        // A short run stops each phase in time to save its
                        // progress and the check times below (H4-1).
                        budgetMillis = plan.compatRefreshBudgetMillis,
                        updateBudgetMillis = plan.compatUpdateBudgetMillis,
                        existenceBudgetMillis = plan.compatExistenceBudgetMillis,
                        watchCheckBudgetMillis = plan.compatWatchCheckBudgetMillis,
                        // Recording marks a match as seen; notify before the BGTask
                        // deadline can cancel the later phases.
                        onWatchMatchesRecorded = { recorded -> notifyRecordedWatchMatches(recorded) }
                    )
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
                    // Each sent batch is saved; an unfinished one is retried later.
                    withIosBackgroundStageTimeout(plan.archiveReportTimeoutMillis, "archive report") {
                        IosArchiveReportScheduler.processNow(store) {
                            profileStore.isGenerationCommitAllowed(
                                ExperienceProfile.TOSHIAKI_COMPAT,
                                expectedGeneration
                            )
                        }
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
        suspend fun runSharedFeatures() {
            if (!sharedFeaturesEnabled) return
            // Budgeted, so it returns in time to save its check times.
            com.valoser.futacha.shared.compat.refreshSharedFeatures(archiveStore, repo, isCompatWifiConnected(null),
                maxTabs = maxThreadsPerRun, onNewMatches = { matches ->
                    val alerts = matches.map { match -> com.valoser.futacha.shared.service.CatalogWatchAlertMatch(
                        threadId = match.history.threadNo, boardId = match.history.boardKey,
                        boardName = match.history.boardName, boardUrl = match.history.originalUrl.substringBefore("/res/"),
                        title = match.history.title, titleImageUrl = match.history.thumbnailUrl.orEmpty(),
                        replyCount = match.history.replyCount, detectedAtEpochMillis = match.history.contentUpdatedAtEpochMillis) }
                    notifyNewIosWatchAlertMatches(alerts)
                }, commitGate = { commit ->
                    profileStore.runIfGenerationCurrent(activeProfile, expectedGeneration, commit)
                },
                budgetMillis = plan.sharedFeaturesBudgetMillis)
        }
        suspend fun runHistory() {
            if (!backgroundEnabled) return
            // A foreground history refresh may hold HistoryRefresher's
            // process lock. Skip only this step: the watch alert and
            // archive report steps are independent of it.
            try {
                // Cut off, the refresher keeps what it saved and starts the
                // next run at the first thread it did not finish.
                withIosBackgroundStageTimeout(plan.historyTimeoutMillis, "history refresh") {
                    refresher.refresh(
                        autoSaveBudgetMillis = plan.autoSaveBudgetMillis,
                        maxThreadsPerRun = maxThreadsPerRun,
                        maxAutoSavesPerRun = plan.maxAutoSavesPerRun,
                        threadFetchTimeoutMillisOverride = plan.threadFetchTimeoutMillis,
                        runBudgetMillis = plan.historyRunBudgetMillis,
                        historyCommitGate = { commit ->
                            profileStore.runIfGenerationCurrent(
                                activeProfile,
                                expectedGeneration,
                                commit
                            )
                        },
                        autoSaveCommitGate = { commit ->
                            profileStore.runIfGenerationCurrent(
                                activeProfile,
                                expectedGeneration,
                                commit
                            )
                        }
                    )
                }
            } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
                Logger.d("BackgroundRefresh", "History refresh already running; skipping only the history step")
            }
        }
        suspend fun runWatchAlerts() {
            if (!watchAlertEnabled || !profileStore.isGenerationCommitAllowed(activeProfile, expectedGeneration)) return
            try {
                // Nothing is marked seen until notified. A cut-off check still
                // notifies what it found; the rest is redone next run.
                val result = withIosBackgroundStageTimeout(plan.watchAlertTimeoutMillis, "watch alert") {
                    CatalogWatchAlertRefresher(
                        stateStore = stateStore,
                        diagnosticsStore = archiveStore,
                        repository = repo,
                        dispatcher = AppDispatchers.io
                    ).refresh(onMatchesFound = { matches ->
                        val newMatches = notifyNewIosWatchAlertMatches(matches)
                        if (newMatches.isNotEmpty()) {
                            Logger.d("BackgroundRefresh", "Detected ${newMatches.size} iOS watch alert match(es)")
                        }
                    })
                } ?: return
                if (result.failureCount > 0) {
                    Logger.w("BackgroundRefresh", "iOS watch alert partial failures: ${result.failureCount}")
                }
            } catch (e: CatalogWatchAlertRefresher.RefreshAlreadyRunningException) {
                Logger.d("BackgroundRefresh", "Catalog watch alert refresh already running; skipping only that step")
            }
        }
        suspend fun runArchiveReports() {
            if (!archiveReportEnabled || !profileStore.isGenerationCommitAllowed(
                    activeProfile,
                    expectedGeneration
                )
            ) return
            // Each sent batch is saved; an unfinished one is retried later.
            withIosBackgroundStageTimeout(plan.archiveReportTimeoutMillis, "archive report") {
                IosArchiveReportScheduler.processNow(archiveStore) {
                    profileStore.isGenerationCommitAllowed(activeProfile, expectedGeneration)
                }
            }
        }
        withTimeout(refreshTimeoutMillis) {
            plan.stageOrder.forEach { stage ->
                when (stage) {
                    IosBackgroundRefreshStage.SHARED_FEATURES -> runSharedFeatures()
                    IosBackgroundRefreshStage.HISTORY -> runHistory()
                    IosBackgroundRefreshStage.WATCH_ALERTS -> runWatchAlerts()
                    IosBackgroundRefreshStage.ARCHIVE_REPORTS -> runArchiveReports()
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

/**
 * Runs one stage of a background run within [timeoutMillis] (null: no cap of
 * its own), so a stage that overruns leaves time for the later ones (H4-1).
 */
private suspend fun <T> withIosBackgroundStageTimeout(
    timeoutMillis: Long?,
    stage: String,
    block: suspend () -> T
): T? {
    if (timeoutMillis == null) return block()
    var finished = false
    val result = withTimeoutOrNull(timeoutMillis) { block().also { finished = true } }
    if (!finished) {
        Logger.w("BackgroundRefresh", "iOS background $stage stage stopped after ${timeoutMillis}ms")
    }
    return result
}

/**
 * Schedules one notification for [matches]; true once iOS accepted it. Runs
 * only with authorization already granted: background work never prompts.
 */
private suspend fun postIosWatchAlertNotification(matches: List<CatalogWatchAlertMatch>): Boolean {
    if (matches.isEmpty()) return false
    val center = UNUserNotificationCenter.currentNotificationCenter()
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
    // Bounded: the caller holds the ledger lock and runs this without cancellation.
    return withTimeoutOrNull(IOS_WATCH_ALERT_POST_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(request) { notificationError ->
                if (notificationError != null) {
                    Logger.w("BackgroundRefresh", "Failed to post iOS watch alert notification: ${notificationError.localizedDescription}")
                }
                if (continuation.isActive) {
                    continuation.resume(notificationError == null)
                }
            }
        }
    } ?: false
}

/**
 * Shared by every watch notification path (ふたちゃ catalog alerts,
 * shared-feature patrol and the compatibility refresh), so they update the
 * single NSUserDefaults ledger one at a time.
 */
private val iosWatchAlertNotifications = IosWatchAlertNotificationDispatcher(
    isAuthorized = ::isIosNotificationAuthorized,
    filterNew = ::filterNewIosWatchAlertMatches,
    markNotified = ::markIosWatchAlertMatchesNotified,
    post = ::postIosWatchAlertNotification
)

/**
 * Notifies only the matches not yet in the ledger and returns them. Without
 * notification permission nothing is recorded, so the matches are notified
 * once permission is granted (see [IosWatchAlertNotificationDispatcher]).
 */
private suspend fun notifyNewIosWatchAlertMatches(
    matches: List<CatalogWatchAlertMatch>
): List<CatalogWatchAlertMatch> = iosWatchAlertNotifications.notifyNew(matches)

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
private const val IOS_WATCH_ALERT_POST_TIMEOUT_MILLIS = 10_000L

private const val IOS_APP_LOCK_NOTIFICATION = "com.valoser.futacha.app-lock"

/** Lets the SwiftUI host gate UI it presents outside Compose behind the app lock. */
private fun publishIosAppUnlockedState(unlocked: Boolean) {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = IOS_APP_LOCK_NOTIFICATION,
        `object` = null,
        userInfo = mapOf("unlocked" to unlocked)
    )
}
