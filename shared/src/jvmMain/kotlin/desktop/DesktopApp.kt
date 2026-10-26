package com.valoser.futacha.shared.desktop

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.network.*
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.repository.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.service.*
import com.valoser.futacha.shared.state.*
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.util.*
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files

/**
 * Syncs `profile.pending`, then renames it over [file]. The rename retries while a Windows
 * scanner briefly holds the profile, so a switch whose data already synchronized is not
 * reported as failed for a transient sharing violation (N4-3).
 */
internal fun writeDesktopProfileFile(
    file: File,
    target: ExperienceProfile,
    generation: Long,
    move: (java.nio.file.Path, java.nio.file.Path, Array<out java.nio.file.CopyOption>) -> Unit =
        { source, destination, options -> Files.move(source, destination, *options) }
) {
    val temp = File(file.parentFile, "profile.pending")
    temp.outputStream().use { output ->
        output.write("${target.persistedValue}\n$generation\n".toByteArray()); output.fd.sync()
    }
    moveReplacingWithRetry(temp.toPath(), file.toPath(), move)
}

/** What the manual history refresh of としあき(仮) tells when it is over; as on iOS, with what the time left (re-audit L81). */
internal fun desktopCompatHistoryRefreshNotice(result: CompatBackgroundRefreshResult): String =
    "履歴を更新しました（更新 ${result.updatedTabs}件、終了 ${result.deadTabs}件、失敗 ${result.failures}件）${result.unfinishedNotice}"

class DesktopAppGraph(val environment: DesktopEnvironment) {
    val fileSystem = createFileSystem(environment)
    val stateStore = createAppStateStore(environment, fileSystem)
    private val cookies = PersistentCookieStorage(fileSystem)
    val cookieRepository = CookieRepository(cookies)
    val httpClient = createHttpClient(cookieStorage = cookies)
    val originals = createOriginalMediaSession(httpClient).also { httpClient.bindOriginalMediaSource(it) }
    internal val compatibility = DesktopCompatibilityStore(fileSystem)
    val autoSaved = SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
    val repository = DefaultBoardRepository(HttpBoardApi(httpClient), createHtmlParser(),
        cookieRepository = cookieRepository, diagnosticFileSystem = fileSystem,
        catalogFetchSettingsProvider = { CatalogFetchSettings(rows = stateStore.catalogFetchRows.first()).normalized() })
    val refresher = HistoryRefresher(stateStore, repository, Dispatchers.IO, autoSaved, httpClient, fileSystem)
    private val watchRefresher = CatalogWatchAlertRefresher(stateStore, repository, Dispatchers.IO, diagnosticsStore = compatibility)
    private val watchNotifications = DesktopWatchNotifications(File(environment.dataDirectory, "watch-notifications.tsv"))
    var initialized by mutableStateOf(false)
        private set
    private val profileMutex = Mutex()
    private val profileFile = File(environment.dataDirectory, "profile.txt")
    var profile by mutableStateOf(ExperienceProfile.FUTACHA)
        private set
    var generation by mutableStateOf(0L)
        private set
    var switching by mutableStateOf(false)
        private set

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Atomic writes leave tmp_*.tmp files behind only after a crash mid-write.
        runCatching { (fileSystem as? JvmFileSystem)?.cleanupTempFiles() }
            .onFailure { Logger.w("DesktopFileSystem", "Temp file cleanup failed: ${it.message}") }
        stateStore.seedIfEmpty(AppStateSeedDefaults(boards = mockBoardSummaries, history = mockThreadHistory,
            selfPostIdentifierMap = emptyMap(), catalogModeMap = emptyMap(), lastUsedDeleteKey = ""))
        compatibility.initialize()
        compatibility.bootstrapBoardsIfNeeded(stateStore.boards.first())
        val saved = if (profileFile.isFile) profileFile.readLines() else emptyList()
        profile = ExperienceProfile.fromPersistedValue(saved.firstOrNull())
        generation = saved.getOrNull(1)?.toLongOrNull() ?: 0
        initialized = true
    }

    suspend fun switchTo(target: ExperienceProfile) = profileMutex.withLock {
        // Desktop never offers preview or mobile-only modes (see ExperienceProfile.isSelectable).
        if (switching || target == profile || !target.isSelectable) return@withLock
        switching = true
        try {
            withContext(Dispatchers.IO) {
                if (target == ExperienceProfile.TOSHIAKI_COMPAT) {
                    synchronizeBoardsToCompatibility(stateStore.boards.first())
                    compatibility.importModernHistory(stateStore.readHistory())
                } else {
                    val boards = synchronizeModernBoardsFromCompatibility(
                        stateStore.boards.first(), compatibility.boards.first(), compatibility.holdsBundledTutorialBoard
                    )
                    stateStore.setBoards(boards)
                    val history = compatibility.history.first()
                    stateStore.updateHistory { mergeCompatibilityHistory(it, history, boards) }
                }
                val next = nextExperienceProfileGeneration(generation)
                writeDesktopProfileFile(profileFile, target, next)
                profile = target; generation = next
            }
        } finally { switching = false }
    }

    private suspend fun synchronizeBoardsToCompatibility(boards: List<com.valoser.futacha.shared.model.BoardSummary>) {
        val desired = modernBoardsToCompatibility(boards)
        // The tutorial has an intentional example.com URL accepted by the store's importer.
        val keys = compatibility.modernBoardKeys(boards)
        compatibility.boards.first().filterNot { it.key in keys }.forEach { compatibility.deleteBoard(it.key) }
        compatibility.importModernBoards(boards)
        compatibility.upsertBoards(desired)
    }

    internal suspend fun synchronizeBoards(observedProfile: ExperienceProfile) = profileMutex.withLock {
        if (switching || observedProfile != profile) return@withLock
        if (profile == ExperienceProfile.FUTACHA) synchronizeBoardsToCompatibility(stateStore.boards.first())
        else {
            val current = stateStore.boards.first()
            val updated = synchronizeModernBoardsFromCompatibility(current, compatibility.boards.first(), compatibility.holdsBundledTutorialBoard)
            if (current != updated) stateStore.setBoards(updated)
        }
    }

    internal suspend fun refreshCompatibility(manual: Boolean = false): String {
        val token = generation
        val preferences = compatibility.preferences.first()
        // The periodic probe slows down while minimized; background work needs a fresh value.
        DesktopNetworkState.refreshIfStale(maxAgeMillis = 60_000)
        fun allowed(key: String): Boolean = when (parseCompatForegroundNetworkPolicy(preferences[key])) {
            CompatForegroundNetworkPolicy.ALWAYS -> true
            CompatForegroundNetworkPolicy.WIFI_ONLY -> DesktopNetworkState.unmetered
            CompatForegroundNetworkPolicy.NONE -> false
        }
        val result = withTimeout(5 * 60_000L) { refreshCompatTabsInBackground(compatibility, repository,
            checkUpdates = manual || allowed("compat.background.backgroundThreadUpdateCheck"),
            checkExistence = manual || allowed("compat.background.backgroundThreadExistCheck"),
            checkWatchWords = compatWatchAllowed(preferences, DesktopNetworkState.unmetered),
            budgetMillis = com.valoser.futacha.shared.compat.compatRefreshBudgetWithin(5 * 60_000L),
            commitGate = { commit -> profileMutex.withLock {
                if (switching || profile != ExperienceProfile.TOSHIAKI_COMPAT || generation != token) false
                else { commit(); true }
            } }) }
        if (!manual && preferences[COMPAT_WATCH_NOTIFY_KEY] != "OFF") {
            watchNotifications.notify(result.newWatchMatches.map { match ->
                val history = match.history
                CatalogWatchAlertMatch(history.threadNo, history.boardKey, history.boardName,
                    history.originalUrl.substringBefore("/res/"), history.title, history.thumbnailUrl.orEmpty(),
                    history.replyCount, history.contentUpdatedAtEpochMillis)
            }, sessionMutex = profileMutex) {
                !switching && profile == ExperienceProfile.TOSHIAKI_COMPAT && generation == token &&
                    compatibility.preferences.first()[COMPAT_WATCH_NOTIFY_KEY] != "OFF"
            }
        }
        return desktopCompatHistoryRefreshNotice(result)
    }

    internal suspend fun refreshModern(background: Boolean, watch: Boolean) {
        val token = generation
        val gate: suspend (suspend () -> Unit) -> Boolean = { commit -> profileMutex.withLock {
            if (switching || profile != ExperienceProfile.FUTACHA || generation != token) false
            else { commit(); true }
        } }
        if (background) refresher.refresh(historyCommitGate = gate, autoSaveCommitGate = gate)
        if (watch && stateStore.isWatchAlertEnabled.first()) {
            // Its cursor and diagnostics are not written after a mode switch (re-audit L82).
            val result = watchRefresher.refresh(commitGate = gate)
            watchNotifications.notify(result.matches, sessionMutex = profileMutex) {
                !switching && profile == ExperienceProfile.FUTACHA && generation == token && stateStore.isWatchAlertEnabled.first()
            }
        }
    }

    /**
     * Each step is bounded and runs even if an earlier one fails or times out:
     * a video VLC never released must not keep the HTTP client, cookies, the
     * database and the single-instance lock open (the host's overall limit would
     * otherwise cut all of them off). A step blocked in a synchronous call (VLC's
     * release, a SQLite or file close) ignores cancellation, so it runs detached and
     * is abandoned at its limit instead of holding up the remaining steps (T4-4).
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        closeStep("history refresher", 2_000) { refresher.close() }
        closeStep("board repository", 2_000) { repository.closeAsync().join() }
        closeStep("original media", 3_000) { originals.closeAndAwait() }
        closeStep("HTTP client", 1_000) { httpClient.close() }
        closeStep("cookies", 1_000) { cookies.close() }
        closeStep("compatibility database", 1_000) { compatibility.close() }
        // DesktopVlc.release() itself waits up to 3 s for libVLC; keep that budget.
        closeStep("video runtime", 3_500) { com.valoser.futacha.shared.ui.board.DesktopVlc.release() }
        closeStep("environment", 1_000) { environment.closeAndAwait() }
    }

    private suspend fun closeStep(name: String, timeoutMillis: Long, step: suspend () -> Unit) =
        runDesktopCloseStep(name, timeoutMillis, step)
}

/** One bounded shutdown step; see [DesktopAppGraph.close]. */
internal suspend fun runDesktopCloseStep(name: String, timeoutMillis: Long, step: suspend () -> Unit) {
    val running = CoroutineScope(Dispatchers.IO).async { step() }
    try {
        if (withTimeoutOrNull(timeoutMillis) { running.await() } == null) {
            running.cancel()
            Logger.w("DesktopShutdown", "$name did not close within ${timeoutMillis}ms")
        }
    } catch (cancelled: CancellationException) {
        running.cancel()
        currentCoroutineContext().ensureActive()
        // The step cancelled itself; the remaining steps still run.
        Logger.w("DesktopShutdown", "$name was cancelled while closing")
    } catch (failure: Exception) { Logger.e("DesktopShutdown", "Failed to close $name", failure) }
}

/**
 * The start of the Desktop window (L125). A failed [DesktopAppGraph.initialize] used to leave only its message, with
 * nothing to do but quit, where iOS offers 再試行; its steps are idempotent, so [retry] runs them again.
 */
@Stable
internal class DesktopStartup {
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var attempt by mutableIntStateOf(0)
        private set

    /** Runs the start again after a failure; nothing while one is running or done. */
    fun retry() {
        if (error == null) return
        error = null
        attempt += 1
    }

    internal suspend fun run(initialize: suspend () -> Unit) {
        try { initialize(); ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "起動できませんでした" }
    }
}

@Composable
internal fun rememberDesktopStartup(key: Any, initialize: suspend () -> Unit): DesktopStartup {
    val startup = remember(key) { DesktopStartup() }
    val currentInitialize by rememberUpdatedState(initialize)
    LaunchedEffect(startup, startup.attempt) { startup.run { currentInitialize() } }
    return startup
}

@Composable
fun DesktopFutachaApp(graph: DesktopAppGraph, onExit: () -> Unit) {
    val startup = rememberDesktopStartup(graph) { graph.initialize() }
    // A failed mode switch (the start's failure is the startup's).
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (!startup.ready) {
        MaterialTheme { Surface(Modifier.fillMaxSize()) {
            val failure = startup.error
            if (failure == null) Box(Modifier.padding(32.dp)) { CircularProgressIndicator() }
            else com.valoser.futacha.shared.ui.FutachaStartupErrorScreen(message = failure, onRetry = startup::retry)
        } }
        return
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            DesktopNetworkState.refresh()
            // Probe once a minute while shown, rarely while minimized or hidden (App Nap);
            // background refreshes probe on demand and showing the window probes at once.
            val shown = DesktopLifecycle.foreground.value
            withTimeoutOrNull(if (shown) 60_000L else 10 * 60_000L) { DesktopLifecycle.foreground.first { it != shown } }
        }
    }
    LaunchedEffect(graph.profile, graph.switching) {
        if (graph.switching) return@LaunchedEffect
        val observedProfile = graph.profile
        if (observedProfile == ExperienceProfile.FUTACHA) {
            graph.stateStore.boards.collect { graph.synchronizeBoards(observedProfile) }
        } else {
            graph.compatibility.boards.collect { graph.synchronizeBoards(observedProfile) }
        }
    }
    LaunchedEffect(graph) {
        graph.compatibility.history.distinctUntilChangedBy(::compatibilityHistorySharedMetadata).collect { history ->
            val boards = graph.stateStore.boards.first()
            graph.stateStore.updateHistory { mergeCompatibilityHistory(it, history, boards) }
        }
    }
    LaunchedEffect(graph.profile, graph.generation, graph.switching) {
        if (graph.profile == ExperienceProfile.TOSHIAKI_COMPAT && !graph.switching) while (isActive) {
            if (!DesktopLifecycle.foreground.value) {
                try { graph.refreshCompatibility() }
                catch (timeout: TimeoutCancellationException) { Logger.w("DesktopRefresh", "履歴更新がタイムアウトしました") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Logger.w("DesktopRefresh", failure.message.orEmpty()) }
            }
            delay(15 * 60_000L)
        }
    }
    val background by graph.stateStore.isBackgroundRefreshEnabled.collectAsState(false)
    val watch by graph.stateStore.isWatchAlertEnabled.collectAsState(false)
    LaunchedEffect(background, watch, graph.profile, graph.generation, graph.switching) {
        if ((background || watch) && !graph.switching && graph.profile == ExperienceProfile.FUTACHA) {
            while (isActive) {
                try { withTimeout(5 * 60_000L) { graph.refreshModern(background, watch) } }
                catch (timeout: TimeoutCancellationException) { Logger.w("DesktopRefresh", "履歴更新がタイムアウトしました") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Logger.w("DesktopRefresh", failure.message.orEmpty()) }
                delay(15 * 60_000L)
            }
        }
    }
    LaunchedEffect(graph) {
        graph.stateStore.appIconVariant.collect { variant ->
            try { DesktopOsIntegration.applyDockIcon(variant) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Logger.w("DesktopIcon", failure.message.orEmpty()) }
        }
    }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(graph) {
        // Register the notification delegate without requesting permission.
        try { withTimeout(10_000) { DesktopOsIntegration.notificationAllowed() } }
        catch (timeout: TimeoutCancellationException) { Logger.w("DesktopNotifications", "通知設定を取得できません") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Logger.w("DesktopNotifications", failure.message.orEmpty()) }
        while (isActive) {
            if (pendingLink == null) {
                try {
                    DesktopOsIntegration.notificationLinks().lastOrNull()?.let { link ->
                        pendingLink = link
                        DesktopLifecycle.activationRequests.value += 1
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Logger.w("DesktopNotifications", failure.message.orEmpty()); delay(5000) }
            }
            awaitNextNotificationPoll()
        }
    }
    var notificationRequestPending by remember { mutableStateOf(false) }
    var notificationError by remember { mutableStateOf<String?>(null) }
    notificationError?.let { message -> AlertDialog(onDismissRequest = { notificationError = null },
        title = { Text("監視ワード通知") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = {
            notificationError = null
            scope.launch {
                try { DesktopOsIntegration.notificationSettings() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { notificationError = failure.message }
            }
        }) { Text("通知設定を開く") } },
        dismissButton = { TextButton(onClick = { notificationError = null }) { Text("閉じる") } }) }
    val controller = rememberDesktopProfileUiController(
        profile = graph.profile, generation = graph.generation, switching = graph.switching, lastError = error,
        isSessionCurrent = { !graph.switching && it.profile == graph.profile && it.generation == graph.generation },
        requestSwitch = { target -> scope.launch {
            try { graph.switchTo(target); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message }
        } }
    )
    CompositionLocalProvider(LocalExperienceProfileUiController provides controller) {
        key(graph.profile, graph.generation) {
            FutachaApp(stateStore = graph.stateStore, versionChecker = remember { createVersionChecker(graph.httpClient) },
                httpClient = graph.httpClient, originalMediaSession = graph.originals,
                sharedRepository = graph.repository, sharedHistoryRefresher = graph.refresher,
                fileSystem = graph.fileSystem, cookieRepository = graph.cookieRepository,
                autoSavedThreadRepository = graph.autoSaved, experienceProfile = graph.profile,
                compatibilityStore = graph.compatibility,
                compatibilityHistoryRefresh = { runSuspendCatchingPreservingCancellation { graph.refreshCompatibility(manual = true) } },
                platformThreadDeepLink = pendingLink,
                onPlatformThreadDeepLinkConsumed = { if (pendingLink == it) pendingLink = null },
                // The window's Esc and menu "戻る" do nothing under the lock screen (L124).
                onAppUnlockedChanged = DesktopBackDispatcher::setAppUnlocked,
                onWatchAlertSettingChangeRequested = { enabled ->
                    if (!notificationRequestPending) {
                        notificationRequestPending = true
                        scope.launch {
                            try {
                                if (!enabled || DesktopOsIntegration.notificationPermission()) graph.stateStore.setWatchAlertEnabled(enabled)
                                else notificationError = "通知を有効にするには、OSの通知設定でふたちゃを許可してください。"
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { notificationError = failure.message ?: "通知設定を変更できませんでした" }
                            finally { notificationRequestPending = false }
                        }
                    }
                },
                onExitApplication = onExit)
        }
    }
}

/**
 * Remembered like Android's and iOS's (L123, 2026-10-02 M4-4): a new controller (fresh lambdas, so never equal to the
 * last) on every recomposition of the host, e.g. a notification click or a notification setting change, replaced the
 * static local and recomposed all of FutachaApp. A new one is made only when what it shows changes; its lambdas call
 * the host's latest ones, so the session check still reads the state at the time it is asked.
 */
@Composable
internal fun rememberDesktopProfileUiController(
    profile: ExperienceProfile,
    generation: Long,
    switching: Boolean,
    lastError: String?,
    isSessionCurrent: (ExperienceProfileSessionToken) -> Boolean,
    requestSwitch: (ExperienceProfile) -> Unit
): ExperienceProfileUiController {
    val currentIsSessionCurrent by rememberUpdatedState(isSessionCurrent)
    val currentRequestSwitch by rememberUpdatedState(requestSwitch)
    return remember(profile, generation, switching, lastError) {
        ExperienceProfileUiController(
            isAvailable = true, activeProfile = profile, sessionGeneration = generation,
            isSessionActive = !switching, switchInProgress = switching, lastError = lastError,
            isSessionAuthoritativelyCurrent = { token -> currentIsSessionCurrent(token) },
            requestSwitch = { target -> currentRequestSwitch(target) }
        )
    }
}

/**
 * Polls quickly only while a window has focus. Otherwise a notification click
 * activates the app first, which wakes the poll, so a slow fallback suffices
 * and a hidden or minimized app is not woken twice a second.
 */
private suspend fun awaitNextNotificationPoll() {
    val interval = if (DesktopLifecycle.focused.value) 500L else 5_000L
    withTimeoutOrNull(interval) {
        merge(DesktopLifecycle.focused.drop(1), DesktopLifecycle.appActivations.drop(1)).first()
    }
}
