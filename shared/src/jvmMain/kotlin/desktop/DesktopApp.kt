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
import java.nio.file.StandardCopyOption

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
    private val watchRefresher = CatalogWatchAlertRefresher(stateStore, repository, Dispatchers.IO)
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
        if (switching || target == profile) return@withLock
        switching = true
        try {
            withContext(Dispatchers.IO) {
                if (target == ExperienceProfile.TOSHIAKI_COMPAT) {
                    synchronizeBoardsToCompatibility(stateStore.boards.first())
                    compatibility.importModernHistory(stateStore.history.first())
                } else {
                    val boards = synchronizeModernBoardsFromCompatibility(stateStore.boards.first(), compatibility.boards.first())
                    stateStore.setBoards(boards)
                    val history = compatibility.history.first()
                    stateStore.updateHistory { mergeCompatibilityHistory(it, history, boards) }
                }
                val next = nextExperienceProfileGeneration(generation)
                val temp = File(profileFile.parentFile, "profile.pending")
                temp.outputStream().use { output ->
                    output.write("${target.persistedValue}\n$next\n".toByteArray()); output.fd.sync()
                }
                Files.move(temp.toPath(), profileFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
            val updated = synchronizeModernBoardsFromCompatibility(current, compatibility.boards.first())
            if (current != updated) stateStore.setBoards(updated)
        }
    }

    internal suspend fun refreshCompatibility(manual: Boolean = false): String {
        val token = generation
        val preferences = compatibility.preferences.first()
        fun allowed(key: String): Boolean = when (parseCompatForegroundNetworkPolicy(preferences[key])) {
            CompatForegroundNetworkPolicy.ALWAYS -> true
            CompatForegroundNetworkPolicy.WIFI_ONLY -> DesktopNetworkState.wifi
            CompatForegroundNetworkPolicy.NONE -> false
        }
        val result = withTimeout(5 * 60_000L) { refreshCompatTabsInBackground(compatibility, repository,
            checkUpdates = manual || allowed("compat.background.backgroundThreadUpdateCheck"),
            checkExistence = manual || allowed("compat.background.backgroundThreadExistCheck"),
            checkWatchWords = compatWatchAllowed(preferences, DesktopNetworkState.wifi),
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
        return "履歴を更新しました（更新 ${result.updatedTabs}件、終了 ${result.deadTabs}件、失敗 ${result.failures}件）"
    }

    internal suspend fun refreshModern(background: Boolean, watch: Boolean) {
        val token = generation
        val gate: suspend (suspend () -> Unit) -> Boolean = { commit -> profileMutex.withLock {
            if (switching || profile != ExperienceProfile.FUTACHA || generation != token) false
            else { commit(); true }
        } }
        if (background) refresher.refresh(historyCommitGate = gate, autoSaveCommitGate = gate)
        if (watch && stateStore.isWatchAlertEnabled.first()) {
            val result = watchRefresher.refresh()
            watchNotifications.notify(result.matches, sessionMutex = profileMutex) {
                !switching && profile == ExperienceProfile.FUTACHA && generation == token && stateStore.isWatchAlertEnabled.first()
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        refresher.close()
        repository.closeAsync().join()
        originals.closeAndAwait()
        httpClient.close()
        cookies.close()
        compatibility.close()
        environment.closeAndAwait()
    }
}

@Composable
fun DesktopFutachaApp(graph: DesktopAppGraph, onExit: () -> Unit) {
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(graph) {
        try { graph.initialize(); ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "起動できませんでした" }
    }
    if (!ready) {
        MaterialTheme { Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(32.dp)) {
            if (error == null) CircularProgressIndicator() else Text(error!!)
        } } }
        return
    }
    LaunchedEffect(Unit) {
        while (isActive) { DesktopNetworkState.refresh(); delay(60_000) }
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
            delay(500)
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
    val controller = ExperienceProfileUiController(
        isAvailable = true, activeProfile = graph.profile, sessionGeneration = graph.generation,
        isSessionActive = !graph.switching, switchInProgress = graph.switching, lastError = error,
        isSessionAuthoritativelyCurrent = { !graph.switching && it.profile == graph.profile && it.generation == graph.generation },
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
