package com.valoser.futacha

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
    private var isFlexibleUpdateDownloaded by mutableStateOf(false)
    private var didFlexibleUpdateCompletionFail by mutableStateOf(false)
    private var compatBackAnimationCallback: OnBackAnimationCallback? = null
    private lateinit var inAppUpdateController: AndroidInAppUpdateController

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
        if (savedInstanceState == null) receiveSharedAttachment(intent)
        val app = application as? FutachaApplication
        inAppUpdateController = AndroidInAppUpdateController(
            activity = this,
            onFlexibleUpdateDownloaded = {
                didFlexibleUpdateCompletionFail = false
                isFlexibleUpdateDownloaded = true
            },
            onFlexibleUpdateCompletionFailed = {
                didFlexibleUpdateCompletionFail = true
                isFlexibleUpdateDownloaded = true
            }
        )
        inAppUpdateController.register()
        lifecycleScope.launch {
            val updateCheckEnabled = app?.appStateStore?.isUpdateCheckEnabled?.first() ?: true
            inAppUpdateController.checkForNewUpdate(
                allowOptionalUpdate = updateCheckEnabled
            )
        }
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
        if (app == null || app.profileRecoveryComplete.value) {
            applyDurableThreadDeepLink(app, restoredDeepLinks)
        } else {
            // The pending navigation is stored for the profile an interrupted switch
            // was heading to; read it once that switch has been recovered (M-3).
            pendingDeepLinks = restoredDeepLinks
            lifecycleScope.launch {
                app.profileRecoveryComplete.first { it }
                applyDurableThreadDeepLink(app, pendingDeepLinks)
            }
        }
        enableEdgeToEdge()
        // Android 15 can retain the decor's legacy inset fitting on recreation.
        // Keep layout flags explicit as well as WindowCompat's modern setting.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
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
            var profileSwitchFailureNotice by remember { mutableStateOf<String?>(null) }
            val profileRecoveryComplete = app?.profileRecoveryComplete?.collectAsState()?.value ?: true
            val profileRecoveryFailure = app?.profileRecoveryFailure?.collectAsState()?.value
            androidx.compose.runtime.LaunchedEffect(profileRecoveryFailure) {
                // Startup could not finish an interrupted switch, so nothing was
                // rolled back and commits stay refused: advise a restart (M4-1).
                if (profileRecoveryFailure != null && app?.experienceProfileStore?.readJournal() != null) {
                    profileSwitchFailureNotice = profileSwitchFailureMessage(profileRecoveryFailure, rolledBack = false)
                }
            }
            var pendingWatchAlertPermissionSession by remember {
                mutableStateOf<ExperienceProfileSessionToken?>(null)
            }
            var watchAlertPermissionResultMessage by remember { mutableStateOf<String?>(null) }
            // These dialogs sit outside FutachaApp, so they would open above its
            // lock overlay; hide (not dismiss) them until the app is unlocked (C-2).
            var isFutachaAppUnlocked by remember { mutableStateOf(false) }
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
            androidx.compose.runtime.LaunchedEffect(stateStore) {
                stateStore.appIconVariant.collect { variant ->
                    com.valoser.futacha.shared.util.applyAppIconVariant(this@MainActivity, variant)
                }
            }
            androidx.compose.runtime.LaunchedEffect(activeProfile, app, stateStore) {
                val compatStore = app?.compatibilityStore ?: return@LaunchedEffect
                if (activeProfile.usesAppStateData) return@LaunchedEffect
                kotlinx.coroutines.flow.combine(stateStore.observedBoards, stateStore.observedHistory) { boards, history ->
                    boards to history
                }.collect { (boards, history) ->
                    try {
                        if (boards.isNotEmpty()) compatStore.bootstrapBoardsIfNeeded(boards)
                        compatStore.importModernHistory(history)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Throwable) {
                        // Same as before the shared-flow change: log and keep the
                        // app running; the next emission retries the import.
                        com.valoser.futacha.shared.util.Logger.e("MainActivity", "Compatibility bootstrap failed", failure)
                    }
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
                            if (!activeProfile.usesAppStateData) {
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
                                stateStore.observedBoards.collect { modernBoards ->
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
            // Remembered: a new instance (fresh lambdas) on every recomposition of
            // this scope, e.g. each app-lock change, replaced the static local
            // and recomposed all of FutachaApp (M4-4).
            val profileUiController = remember(
                app, profileStore, activeProfile, profileGeneration, profileSessionActive,
                profileSwitchInProgress, profileSwitchError, stateStore, profileScope
            ) { ExperienceProfileUiController(
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
                        val switchThreadNavigation = profileSwitchThreadNavigation(application)
                        profileScope.launch {
                            // The link stored for the target profile; removed again if the switch fails (M-6).
                            var switchThreadUrl: String? = null
                            try {
                                profileSwitchError = null
                            // Make the cross-profile dataset authoritative before
                            // the new Activity is launched.  The collectors below
                            // normally do this continuously, but a switch can
                            // race the first Flow emission on a cold start.
                            if (!target.usesAppStateData) {
                                val boards = stateStore.boards.first()
                                val history = stateStore.history.first()
                                application.compatibilityStore.bootstrapBoardsIfNeeded(boards)
                                application.compatibilityStore.importModernBoards(boards)
                                application.compatibilityStore.importModernHistory(history)
                            } else if (!activeProfile.usesAppStateData) {
                                val compatBoards = application.compatibilityStore.boards.first()
                                val compatHistory = application.compatibilityStore.history.first()
                                val currentBoards = stateStore.boards.first()
                                val mergedBoards = synchronizeModernBoardsFromCompatibility(currentBoards, compatBoards)
                                if (mergedBoards != currentBoards) stateStore.setBoards(mergedBoards)
                                stateStore.updateHistory { currentHistory ->
                                    mergeCompatibilityHistory(currentHistory, compatHistory, mergedBoards)
                                }
                            }
                            switchThreadUrl = pendingDeepLinks.thread
                            withContext(Dispatchers.IO) {
                                switchThreadNavigation.beforeSwitch(switchThreadUrl, target)
                            }
                            application.modeSwitchCoordinator.switchTo(
                                target = target,
                                preferredFutachaIcon = preferredAppIconVariant,
                                quiesceOldProfile = {
                                    if (activeProfile.usesAppStateData) {
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
                                withContext(Dispatchers.IO) {
                                    // Kept when the rollback failed: the next launch completes the switch (M4-2).
                                    switchThreadNavigation.afterFailedSwitch(switchThreadUrl, error)
                                }
                                profileSwitchError = error.message ?: "モードを切り替えられませんでした"
                                // The coordinator rolled the switch back unless that failed too,
                                // or recovering an earlier interrupted switch failed (M4-1).
                                profileSwitchFailureNotice = profileSwitchFailureMessage(
                                    error,
                                    rolledBack = error.suppressed.isEmpty()
                                )
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
                                withContext(Dispatchers.IO) {
                                    switchThreadNavigation.afterFailedSwitch(switchThreadUrl)
                                }
                                profileSwitchError = failure.message ?: "モードを切り替えられませんでした"
                                profileSwitchFailureNotice = profileSwitchFailureMessage(failure, rolledBack = true)
                                profileSessionActive = true
                            } finally {
                                profileSwitchInProgress = false
                            }
                        }
                    }
                ) }
            // Without FutachaApp on screen there is no lock overlay to stay under.
            val isFutachaAppShown = profileSessionActive && profileRecoveryComplete &&
                !(app != null && networkServicesError != null)
            val mayShowActivityDialogs = isFutachaAppUnlocked || !isFutachaAppShown
            CompositionLocalProvider(
                LocalExperienceProfileUiController provides profileUiController
            ) {
                if (profileSessionActive && profileRecoveryComplete) {
                    fun commitWatchAlertSettingIfCurrent(
                        enabled: Boolean,
                        session: ExperienceProfileSessionToken
                    ) {
                        profileScope.launch {
                            try {
                                if (
                                    // Futaber shares the Futacha app state, so its switch commits here too.
                                    session.profile.usesAppStateData &&
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
                            imageTransport = if (networkServicesReady) app?.imageTransport else null,
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
                            onExitApplication = { finish() },
                            onAppUnlockedChanged = { unlocked -> isFutachaAppUnlocked = unlocked }
                        )
                    }
                    pendingWatchAlertPermissionSession?.takeIf { isFutachaAppUnlocked }?.let { session ->
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
                    watchAlertPermissionResultMessage?.takeIf { isFutachaAppUnlocked }?.let { message ->
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
            profileSwitchFailureNotice?.takeIf { mayShowActivityDialogs }?.let { message ->
                val dismissProfileSwitchFailureNotice = {
                    profileSwitchFailureNotice = null
                    app?.acknowledgeProfileRecoveryFailure()
                    Unit
                }
                AlertDialog(
                    onDismissRequest = dismissProfileSwitchFailureNotice,
                    title = { Text("モードを切り替えられませんでした") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = dismissProfileSwitchFailureNotice) { Text("OK") }
                    }
                )
            }
            if (isFlexibleUpdateDownloaded && mayShowActivityDialogs) {
                AlertDialog(
                    onDismissRequest = {
                        isFlexibleUpdateDownloaded = false
                        didFlexibleUpdateCompletionFail = false
                    },
                    title = { Text("アップデートの準備ができました") },
                    text = {
                        Text(
                            if (didFlexibleUpdateCompletionFail) {
                                "アップデートを適用できませんでした。もう一度お試しください。"
                            } else {
                                "新しいバージョンをダウンロードしました。再起動して更新します。"
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isFlexibleUpdateDownloaded = false
                                didFlexibleUpdateCompletionFail = false
                                inAppUpdateController.completeFlexibleUpdate()
                            }
                        ) { Text(if (didFlexibleUpdateCompletionFail) "再試行" else "再起動して更新") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                isFlexibleUpdateDownloaded = false
                                didFlexibleUpdateCompletionFail = false
                            }
                        ) { Text("後で") }
                    }
                )
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

    private fun profileSwitchThreadNavigation(app: FutachaApplication) = ProfileSwitchThreadNavigation(
        save = { url, target ->
            app.experienceProfileStore.savePendingThreadNavigation(url = url, target = target)
        },
        clear = { expectedUrl ->
            // A failed cleanup must not fail (or mask the result of) the switch.
            runCatching { app.experienceProfileStore.clearPendingThreadNavigation(expectedUrl) }
                .onFailure { error ->
                    com.valoser.futacha.shared.util.Logger.e(
                        "MainActivity",
                        "Failed to clear pending thread navigation",
                        error
                    )
                }
        }
    )

    private fun applyDurableThreadDeepLink(app: FutachaApplication?, base: PendingPlatformDeepLinks) {
        val durableThreadDeepLink = app?.experienceProfileStore?.readPendingThreadNavigation(
            app.experienceProfileStore.readActiveProfile()
        )?.boundedPlatformDeepLinkOrNull()
        pendingDeepLinks = if (base.thread != null) {
            base
        } else {
            base.copy(thread = durableThreadDeepLink)
        }
        pendingThreadBoardRegistrationApproved = durableThreadDeepLink != null &&
            pendingDeepLinks.thread == durableThreadDeepLink
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.resumeUpdateIfNeeded()
        }
    }

    override fun onStop() {
        super.onStop()
        // API 37 closes the visible task when a launcher alias changes, so an
        // icon selected in settings is applied once the app is off screen (G-12).
        com.valoser.futacha.shared.util.applyDeferredAppIconVariantOnStop(this)
    }

    override fun onDestroy() {
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.unregister()
        }
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
        val callback = ThreadDrawerBackAnimationCallback {
            onBackPressedDispatcher.onBackPressed()
        }
        compatBackAnimationCallback = callback
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback
        )
    }

    private var incomingAttachmentJob: kotlinx.coroutines.Job? = null

    @Suppress("DEPRECATION")
    private fun receiveSharedAttachment(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND ||
            !(incoming.type?.startsWith("image/") == true || incoming.type?.startsWith("video/") == true)) return
        val uri = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                incoming.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                incoming.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? android.net.Uri
            }
        }.getOrNull() ?: return
        if (uri.scheme != "content") return
        incomingAttachmentJob?.cancel()
        incomingAttachmentJob = lifecycleScope.launch {
            val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.valoser.futacha.shared.util.readImageDataFromUri(this@MainActivity, uri, 32_000_000L)
            }
            if (image != null) com.valoser.futacha.shared.ui.board.IncomingSharedAttachment.offer(image)
            android.widget.Toast.makeText(this@MainActivity,
                if (image != null) "書き込み画面で「共有された画像を添付」を選んでください"
                else "共有された画像を読み込めませんでした（上限32MB）", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveSharedAttachment(intent)
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
        fun profileSwitchFailureMessage(error: Throwable, rolledBack: Boolean): String =
            com.valoser.futacha.shared.compat.modeSwitchFailureMessage(error, rolledBack)

        const val KEY_HAS_PENDING_DEEP_LINK_SNAPSHOT = "pending_deep_link_snapshot"
        const val KEY_PENDING_AI_DEEP_LINK = "pending_ai_deep_link"
        const val KEY_PENDING_THREAD_DEEP_LINK = "pending_thread_deep_link"
    }
}
