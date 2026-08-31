package com.valoser.futacha

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ExperienceProfileSessionToken
import com.valoser.futacha.shared.compat.ExperienceProfileUiController
import com.valoser.futacha.shared.compat.CompatVolumeKey
import com.valoser.futacha.shared.compat.CompatVolumeKeyBus
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.isExperienceProfileSessionCurrent
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingDeepLinks by mutableStateOf(PendingPlatformDeepLinks())
    private var pendingThreadBoardRegistrationApproved by mutableStateOf(false)
    private var compatBackAnimationCallback: OnBackAnimationCallback? = null

    // ComponentActivity exposes this override through an androidx.core restricted
    // API marker even though overriding it is the supported Activity hook for
    // volume-key routing. Keep the hook because compatibility-mode TTS uses it.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val compatKey = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> CompatVolumeKey.UP
                KeyEvent.KEYCODE_VOLUME_DOWN -> CompatVolumeKey.DOWN
                else -> null
            }
            if (compatKey != null && CompatVolumeKeyBus.dispatch(compatKey)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? FutachaApplication
        val restoredDeepLinks = if (savedInstanceState?.getBoolean(KEY_HAS_PENDING_DEEP_LINK_SNAPSHOT) == true) {
            PendingPlatformDeepLinks(
                ai = savedInstanceState.getString(KEY_PENDING_AI_DEEP_LINK).boundedPlatformDeepLinkOrNull(),
                thread = savedInstanceState.getString(KEY_PENDING_THREAD_DEEP_LINK).boundedPlatformDeepLinkOrNull()
            )
        } else {
            PendingPlatformDeepLinks().withIncoming(
                ai = intent?.futachaAiDeepLinkOrNull(),
                thread = intent?.futabaThreadDeepLinkOrNull()
            )
        }
        val durableThreadDeepLink = app?.experienceProfileStore?.readPendingThreadNavigation(
            app.experienceProfileStore.readActiveProfile()
        )?.boundedPlatformDeepLinkOrNull()
        pendingDeepLinks = if (restoredDeepLinks.thread != null) {
            restoredDeepLinks
        } else {
            restoredDeepLinks.copy(thread = durableThreadDeepLink)
        }
        pendingThreadBoardRegistrationApproved = durableThreadDeepLink != null &&
            pendingDeepLinks.thread == durableThreadDeepLink
        enableEdgeToEdge()
        setContent {
            val profileStore = remember(app) { app?.experienceProfileStore }
            val activeProfile = if (profileStore != null) {
                profileStore.activeProfile.collectAsState().value
            } else {
                com.valoser.futacha.shared.compat.ExperienceProfile.FUTACHA
            }
            val profileGeneration = if (profileStore != null) {
                profileStore.generation.collectAsState().value
            } else {
                0L
            }
            val profileScope = rememberCoroutineScope()
            var profileSwitchInProgress by remember { mutableStateOf(false) }
            var profileSessionActive by remember { mutableStateOf(true) }
            var profileSwitchError by remember { mutableStateOf<String?>(null) }
            var pendingWatchAlertPermissionSession by remember {
                mutableStateOf<ExperienceProfileSessionToken?>(null)
            }
            var watchAlertPermissionResultMessage by remember { mutableStateOf<String?>(null) }
            val fileSystem = remember(app) {
                app?.fileSystem ?: createFileSystem(applicationContext)
            }
            val stateStore = remember(app, fileSystem) {
                app?.appStateStore ?: createAppStateStore(applicationContext, fileSystem)
            }
            val cookieStorage = remember(app, fileSystem) {
                app?.cookieStorage ?: PersistentCookieStorage(fileSystem)
            }
            val networkServicesReady = app?.networkServicesReady?.collectAsState(initial = false)?.value ?: true
            val networkServicesError = app?.networkServicesError?.collectAsState(initial = null)?.value
            val httpClient = remember(app, networkServicesReady) {
                if (app != null && networkServicesReady) app.httpClient else null
            }
            val cookieRepository = remember(app, cookieStorage) {
                app?.cookieRepository ?: CookieRepository(cookieStorage)
            }
            val versionChecker = remember(httpClient) {
                httpClient?.let { createVersionChecker(applicationContext, it) }
            }
            val autoSavedThreadRepository = remember(app, fileSystem) {
                app?.autoSavedThreadRepository ?: SavedThreadRepository(
                    fileSystem,
                    baseDirectory = AUTO_SAVE_DIRECTORY
                )
            }
            val preferredAppIconVariant by stateStore.appIconVariant.collectAsState(
                initial = com.valoser.futacha.shared.model.AppIconVariant.Current
            )
            val modernBoards by stateStore.boards.collectAsState(initial = emptyList())
            val modernHistory by stateStore.history.collectAsState(initial = emptyList())
            androidx.compose.runtime.LaunchedEffect(activeProfile, app, modernBoards, modernHistory) {
                try {
                    if (
                        activeProfile == com.valoser.futacha.shared.compat.ExperienceProfile.TOSHIAKI_COMPAT &&
                        app != null
                    ) {
                        // The first Flow emission can be an empty loading value. Do
                        // not permanently mark the compat bootstrap as complete
                        // before the seeded/current board list is available, but
                        // keep retrying history import when boards and history are
                        // emitted separately.
                        if (modernBoards.isNotEmpty()) {
                            app.compatibilityStore.bootstrapBoardsIfNeeded(modernBoards)
                        }
                        app.compatibilityStore.importModernHistory(modernHistory)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    com.valoser.futacha.shared.util.Logger.e(
                        "MainActivity",
                        "Compatibility bootstrap failed",
                        failure
                    )
                }
            }
            androidx.compose.runtime.LaunchedEffect(app, stateStore, activeProfile) {
                // The two profiles use different UI stores, but their boards,
                // history, and shared thread snapshots are one user dataset.
                // Keep the compatibility -> modern bridge alive even when the
                // app starts directly in Futacha mode; restricting it to a
                // compatibility session left data invisible until the user
                // switched modes once (issue #11).
                val compatibilityApp = app ?: return@LaunchedEffect
                coroutineScope {
                    launch {
                        try {
                            if (activeProfile == ExperienceProfile.TOSHIAKI_COMPAT) {
                                var hasObservedAuthoritativeCompatBoards = false
                                compatibilityApp.compatibilityStore.boards.collect { compatBoards ->
                                    if (compatBoards.isNotEmpty()) hasObservedAuthoritativeCompatBoards = true
                                    if (!hasObservedAuthoritativeCompatBoards) return@collect
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
                                    compatibilityApp.compatibilityStore.boards.first()
                                        .filterNot { it.key in desiredKeys }
                                        .forEach { compatibilityApp.compatibilityStore.deleteBoard(it.key) }
                                    compatibilityApp.compatibilityStore.upsertBoards(desired)
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            com.valoser.futacha.shared.util.Logger.e(
                                "MainActivity",
                                "Compatibility board bridge stopped",
                                failure
                            )
                        }
                    }
                    launch {
                        try {
                            compatibilityApp.compatibilityStore.history.collect { compatHistory ->
                                val boards = stateStore.boards.first()
                                stateStore.updateHistory { current ->
                                    mergeCompatibilityHistory(current, compatHistory, boards)
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            com.valoser.futacha.shared.util.Logger.e(
                                "MainActivity",
                                "Compatibility history bridge stopped",
                                failure
                            )
                        }
                    }
                }
            }
            DisposableEffect(app, httpClient) {
                onDispose {
                    if (app == null) {
                        runCatching { httpClient?.close() }
                    }
                }
            }
            val profileUiController = ExperienceProfileUiController(
                    isAvailable = app != null,
                    activeProfile = activeProfile,
                    sessionGeneration = profileGeneration,
                    isSessionActive = profileSessionActive,
                    switchInProgress = profileSwitchInProgress,
                    lastError = profileSwitchError,
                    isSessionAuthoritativelyCurrent = profileStore?.let { store ->
                        { token ->
                            store.isGenerationCommitAllowed(token.profile, token.generation)
                        }
                    },
                    requestSwitch = switchRequest@{ target ->
                        val application = app ?: return@switchRequest
                        if (target == activeProfile || profileSwitchInProgress) return@switchRequest
                        profileSwitchInProgress = true
                        profileSessionActive = false
                        profileScope.launch {
                            try {
                                profileSwitchError = null
                            // Make the cross-profile dataset authoritative before
                            // the new Activity is launched.  The collectors below
                            // normally do this continuously, but a switch can
                            // race the first Flow emission on a cold start.
                            if (target == ExperienceProfile.TOSHIAKI_COMPAT) {
                                val boards = stateStore.boards.first()
                                val history = stateStore.history.first()
                                application.compatibilityStore.bootstrapBoardsIfNeeded(boards)
                                application.compatibilityStore.importModernBoards(boards)
                                application.compatibilityStore.importModernHistory(history)
                            } else if (activeProfile == ExperienceProfile.TOSHIAKI_COMPAT) {
                                val compatBoards = application.compatibilityStore.boards.first()
                                val compatHistory = application.compatibilityStore.history.first()
                                val currentBoards = stateStore.boards.first()
                                val mergedBoards = synchronizeModernBoardsFromCompatibility(currentBoards, compatBoards)
                                if (mergedBoards != currentBoards) stateStore.setBoards(mergedBoards)
                                stateStore.updateHistory { currentHistory ->
                                    mergeCompatibilityHistory(currentHistory, compatHistory, mergedBoards)
                                }
                            }
                            pendingDeepLinks.thread?.let { threadUrl ->
                                withContext(Dispatchers.IO) {
                                    application.experienceProfileStore.savePendingThreadNavigation(
                                        url = threadUrl,
                                        target = target
                                    )
                                }
                            }
                            application.modeSwitchCoordinator.switchTo(
                                target = target,
                                preferredFutachaIcon = preferredAppIconVariant,
                                quiesceOldProfile = {
                                    if (activeProfile == com.valoser.futacha.shared.compat.ExperienceProfile.FUTACHA) {
                                        withContext(Dispatchers.IO) {
                                            HistoryRefreshWorker.cancelAndAwait(
                                                WorkManager.getInstance(applicationContext)
                                            )
                                        }
                                        application.watchSyncManager.stopAndAwait()
                                    }
                                }
                            ).onSuccess {
                                application.scheduleProfileRootRelaunch(
                                    // A mode switch is a profile-root navigation.
                                    // Do not reinterpret the other profile's
                                    // active tab as a deep link; doing so opened
                                    // the last compatibility thread even when
                                    // the user was on the catalog (#44). Genuine
                                    // incoming platform deep links remain valid.
                                    threadDeepLink = pendingDeepLinks.thread,
                                    expectedProfile = target
                                )
                                finish()
                            }.onFailure { error ->
                                profileSwitchError = error.message ?: "モードを切り替えられませんでした"
                                profileSessionActive = true
                            }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Throwable) {
                                com.valoser.futacha.shared.util.Logger.e(
                                    "MainActivity",
                                    "Profile switch failed before commit",
                                    failure
                                )
                                profileSwitchError = failure.message ?: "モードを切り替えられませんでした"
                                profileSessionActive = true
                            } finally {
                                profileSwitchInProgress = false
                            }
                        }
                    }
                )
            CompositionLocalProvider(
                LocalExperienceProfileUiController provides profileUiController
            ) {
                if (profileSessionActive) {
                    fun commitWatchAlertSettingIfCurrent(
                        enabled: Boolean,
                        session: ExperienceProfileSessionToken
                    ) {
                        profileScope.launch {
                            try {
                                if (
                                    session.profile == ExperienceProfile.FUTACHA &&
                                    isExperienceProfileSessionCurrent(session, profileUiController)
                                ) {
                                    stateStore.setWatchAlertEnabled(enabled)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Throwable) {
                                com.valoser.futacha.shared.util.Logger.e(
                                    "MainActivity",
                                    "Watch alert setting update failed",
                                    failure
                                )
                            }
                        }
                    }
                    val notificationPermissionLauncher =
                        rememberExperienceProfileActivityResultLauncher(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { granted, session ->
                            pendingWatchAlertPermissionSession = null
                            if (granted) {
                                commitWatchAlertSettingIfCurrent(enabled = true, session = session)
                            } else {
                                watchAlertPermissionResultMessage =
                                    "通知権限が許可されなかったため、監視ワード自動アラートはOFFのままです。"
                            }
                        }
                    if (app != null && networkServicesError != null) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Text("通信機能の初期化に失敗しました。アプリを再起動してください。")
                            }
                        }
                    } else {
                        FutachaApp(
                            stateStore = stateStore,
                            versionChecker = versionChecker,
                            httpClient = httpClient,
                            sharedRepository = if (networkServicesReady) app?.boardRepository else null,
                            sharedHistoryRefresher = if (networkServicesReady) app?.historyRefresher else null,
                            fileSystem = fileSystem,
                            cookieRepository = cookieRepository,
                            autoSavedThreadRepository = autoSavedThreadRepository,
                            platformAiDeepLink = pendingDeepLinks.ai,
                            onPlatformAiDeepLinkConsumed = { consumed ->
                                consumeAiDeepLink(consumed)
                            },
                            platformThreadDeepLink = pendingDeepLinks.thread,
                            platformThreadDeepLinkPreapprovedBoardRegistration =
                                pendingThreadBoardRegistrationApproved,
                            onPlatformThreadDeepLinkConsumed = { consumed ->
                                consumeThreadDeepLink(consumed)
                            },
                            onWatchAlertSettingChangeRequested = { enabled ->
                                val session = ExperienceProfileSessionToken(
                                    profile = activeProfile,
                                    generation = profileGeneration
                                )
                                when (
                                    resolveWatchAlertPermissionAction(
                                        requestedEnabled = enabled,
                                        runtimePermissionRequired =
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                                        permissionGranted = ContextCompat.checkSelfPermission(
                                            this,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                    )
                                ) {
                                    WatchAlertPermissionAction.DISABLE ->
                                        commitWatchAlertSettingIfCurrent(enabled = false, session = session)
                                    WatchAlertPermissionAction.ENABLE_IMMEDIATELY ->
                                        commitWatchAlertSettingIfCurrent(enabled = true, session = session)
                                    WatchAlertPermissionAction.EXPLAIN_AND_REQUEST_PERMISSION ->
                                        pendingWatchAlertPermissionSession = session
                                }
                            },
                            onArchiveReportEnqueued = { sendableCount ->
                                ArchiveReportWorker.enqueueAfterView(applicationContext, sendableCount)
                            },
                            onArchiveReportEnabledChanged = { enabled ->
                                if (enabled) ArchiveReportWorker.enqueueStartup(applicationContext)
                                else ArchiveReportWorker.cancel(applicationContext)
                            },
                            onCurrentThreadChanged = {},
                            experienceProfile = activeProfile,
                            compatibilityStore = app?.compatibilityStore,
                            onExitApplication = { finish() }
                        )
                    }
                    pendingWatchAlertPermissionSession?.let { session ->
                        AlertDialog(
                            onDismissRequest = { pendingWatchAlertPermissionSession = null },
                            title = { Text("通知を許可") },
                            text = {
                                Text(
                                    "監視ワードに一致した新着スレを通知するため、Androidの通知権限が必要です。続けるとシステムの確認画面が開きます。"
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        pendingWatchAlertPermissionSession = null
                                        if (isExperienceProfileSessionCurrent(session, profileUiController)) {
                                            runCatching {
                                                notificationPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                )
                                            }.onFailure {
                                                watchAlertPermissionResultMessage =
                                                    "通知権限の確認画面を開けませんでした。監視ワード自動アラートはOFFのままです。"
                                            }
                                        }
                                    }
                                ) { Text("続ける") }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { pendingWatchAlertPermissionSession = null }
                                ) { Text("キャンセル") }
                            }
                        )
                    }
                    watchAlertPermissionResultMessage?.let { message ->
                        AlertDialog(
                            onDismissRequest = { watchAlertPermissionResultMessage = null },
                            title = { Text("通知は有効になっていません") },
                            text = { Text(message) },
                            confirmButton = {
                                TextButton(
                                    onClick = { watchAlertPermissionResultMessage = null }
                                ) { Text("OK") }
                            }
                        )
                    }
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                            androidx.compose.material3.Text("モードを切り替えています…")
                        }
                    }
                }
            }
        }
        // Register after Compose has installed its normal BackHandler so the
        // overlay callback remains the highest-priority predictive-back hook.
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerCompatBackAnimationCallback()
            }
        }
    }

    override fun onDestroy() {
        compatBackAnimationCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
        compatBackAnimationCallback = null
        super.onDestroy()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerCompatBackAnimationCallback() {
        val callback = object : OnBackAnimationCallback {
            override fun onBackStarted(backEvent: BackEvent) = Unit

            override fun onBackProgressed(backEvent: BackEvent) = Unit

            override fun onBackCancelled() = Unit

            override fun onBackInvoked() {
                // The reference APK never turns a normal Back gesture into a
                // history-drawer open. It closes an already-open drawer through
                // the screen handler, then lets the current Activity navigate
                // back. Keep predictive Back on that same path for every mode.
                onBackPressedDispatcher.onBackPressed()
            }
        }
        compatBackAnimationCallback = callback
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val ai = intent.futachaAiDeepLinkOrNull()
        val thread = intent.futabaThreadDeepLinkOrNull()
        if (ai == null && thread == null) return
        setIntent(intent)
        pendingDeepLinks = pendingDeepLinks.withIncoming(ai = ai, thread = thread)
        if (thread != null) pendingThreadBoardRegistrationApproved = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_HAS_PENDING_DEEP_LINK_SNAPSHOT, true)
        outState.putString(KEY_PENDING_AI_DEEP_LINK, pendingDeepLinks.ai)
        outState.putString(KEY_PENDING_THREAD_DEEP_LINK, pendingDeepLinks.thread)
        super.onSaveInstanceState(outState)
    }

    private fun consumeAiDeepLink(consumed: String) {
        val updated = pendingDeepLinks.consumeAi(consumed)
        if (updated == pendingDeepLinks) return
        pendingDeepLinks = updated
        clearConsumedIntentData(consumed)
    }

    private fun consumeThreadDeepLink(consumed: String) {
        val updated = pendingDeepLinks.consumeThread(consumed)
        if (updated == pendingDeepLinks) return
        pendingDeepLinks = updated
        pendingThreadBoardRegistrationApproved = false
        (application as? FutachaApplication)?.let { app ->
            app.applicationScope.launch {
                app.experienceProfileStore.clearPendingThreadNavigation(consumed)
            }
        }
        clearConsumedIntentData(consumed)
    }

    private fun clearConsumedIntentData(consumed: String) {
        val current = intent ?: return
        if (current.dataString != consumed) return
        setIntent(Intent(current).apply { data = null })
    }

    internal fun pendingDeepLinksForTest(): PendingPlatformDeepLinks = pendingDeepLinks

    private fun Intent.futachaAiDeepLinkOrNull(): String? {
        val raw = dataString.boundedPlatformDeepLinkOrNull() ?: return null
        val uri = data ?: return null
        if (uri.scheme != "futacha" || uri.host != "ai") {
            return null
        }
        return raw
    }

    private fun Intent.futabaThreadDeepLinkOrNull(): String? {
        val raw = dataString.boundedPlatformDeepLinkOrNull() ?: return null
        val uri = data ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        if (!isTrustedFutabaDeepLinkHost(uri.host)) return null
        if (!Regex("/.+/res/[0-9]+\\.htm/?", RegexOption.IGNORE_CASE).matches(uri.path.orEmpty())) return null
        return raw
    }

    private companion object {
        const val KEY_HAS_PENDING_DEEP_LINK_SNAPSHOT = "pending_deep_link_snapshot"
        const val KEY_PENDING_AI_DEEP_LINK = "pending_ai_deep_link"
        const val KEY_PENDING_THREAD_DEEP_LINK = "pending_thread_deep_link"
    }
}
