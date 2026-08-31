package com.valoser.futacha

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import androidx.work.WorkManager
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.compat.AndroidExperienceProfileStore
import com.valoser.futacha.compat.AndroidLauncherAliasManager
import com.valoser.futacha.compat.AndroidModeSwitchCoordinator
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatForegroundPolicyEnabled
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.CrashReporter
import com.valoser.futacha.shared.analytics.PerformanceTracker
import com.valoser.futacha.shared.analytics.analyticsEnabledValue
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.DefaultBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.CatalogWatchAlertRefresher
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.initializeAndroidThreadSavePlatformProtection
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.util.initializeAndroidPersistentLogging
import com.valoser.futacha.shared.ui.compat.initializeCompatPostPlatformContext
import com.valoser.futacha.shared.version.initializeVersionCheckerContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val BACKGROUND_FLOW_MAX_RETRIES = 12L
private const val BACKGROUND_REFRESH_SCHEDULER_PREFS = "background_refresh_scheduler"
private const val LAST_IMMEDIATE_BACKGROUND_REFRESH_ENQUEUE_MILLIS =
    "last_immediate_background_refresh_enqueue_millis"
private const val STARTUP_TEMP_CLEANUP_DELAY_MILLIS = 15_000L
private const val STARTUP_ARCHIVE_REPORT_DELAY_MILLIS = 15_000L
private const val STARTUP_STRICT_MODE_DELAY_MILLIS = 3_000L
private const val PROFILE_ALIAS_SETTLE_DELAY_MILLIS = 1_500L

class FutachaApplication : Application() {
    lateinit var experienceProfileStore: AndroidExperienceProfileStore
        private set
    lateinit var compatibilityStore: AndroidCompatibilityStore
        private set
    lateinit var modeSwitchCoordinator: AndroidModeSwitchCoordinator
        private set
    private var appStateStoreValue: AppStateStore? = null
    val appStateStore: AppStateStore
        get() = requireMainProcessValue("appStateStore", appStateStoreValue)

    @Volatile
    private var httpClientValue: io.ktor.client.HttpClient? = null
    val httpClient: io.ktor.client.HttpClient
        get() = requireMainProcessValue("httpClient", httpClientValue)

    @Volatile
    private var boardRepositoryValue: BoardRepository? = null
    val boardRepository: BoardRepository
        get() = requireMainProcessValue("boardRepository", boardRepositoryValue)

    @Volatile
    private var historyRefresherValue: HistoryRefresher? = null
    val historyRefresher: HistoryRefresher
        get() = requireMainProcessValue("historyRefresher", historyRefresherValue)

    @Volatile
    private var catalogWatchAlertRefresherValue: CatalogWatchAlertRefresher? = null
    val catalogWatchAlertRefresher: CatalogWatchAlertRefresher
        get() = requireMainProcessValue("catalogWatchAlertRefresher", catalogWatchAlertRefresherValue)

    private var autoSavedThreadRepositoryValue: SavedThreadRepository? = null
    val autoSavedThreadRepository: SavedThreadRepository
        get() = requireMainProcessValue("autoSavedThreadRepository", autoSavedThreadRepositoryValue)

    private var fileSystemValue: FileSystem? = null
    val fileSystem: FileSystem
        get() = requireMainProcessValue("fileSystem", fileSystemValue)

    private var cookieStorageValue: PersistentCookieStorage? = null
    val cookieStorage: PersistentCookieStorage
        get() = requireMainProcessValue("cookieStorage", cookieStorageValue)

    private var cookieRepositoryValue: CookieRepository? = null
    val cookieRepository: CookieRepository
        get() = requireMainProcessValue("cookieRepository", cookieRepositoryValue)

    @Volatile
    private var watchSyncManagerValue: WatchSyncManager? = null
    val watchSyncManager: WatchSyncManager
        get() = requireMainProcessValue("watchSyncManager", watchSyncManagerValue)

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Alias changes are followed by a delayed root relaunch. Keep only the
    // newest request; an old delayed job must never bring a stale MainActivity
    // above a screen that was opened after the profile switch.
    @Volatile
    private var profileRootRelaunchJob: Job? = null

    private val networkServicesReadyValue = MutableStateFlow(false)
    val networkServicesReady: StateFlow<Boolean> = networkServicesReadyValue.asStateFlow()

    private val networkServicesErrorValue = MutableStateFlow<String?>(null)
    val networkServicesError: StateFlow<String?> = networkServicesErrorValue.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        initializeAndroidPersistentLogging(applicationContext)
        initializeAndroidThreadSavePlatformProtection(this)
        initializeCompatPostPlatformContext(applicationContext)
        experienceProfileStore = AndroidExperienceProfileStore(applicationContext)
        if (isLightweightWorkerProcess()) {
            return
        }
        // StrictMode is useful while diagnosing main-thread I/O, but installing
        // it before the first Compose frame makes every startup read pay the
        // debug-policy bookkeeping cost.  Install the same diagnostics after
        // the startup window so it cannot turn a cold start into a frame stall.
        applicationScope.launch {
            delay(STARTUP_STRICT_MODE_DELAY_MILLIS)
            withContext(Dispatchers.Main.immediate) {
                configureDebugStrictMode()
            }
        }
        initializeVersionCheckerContext(applicationContext)
        // Telemetry is optional infrastructure.  Its Firebase facades can
        // synchronously initialize Remote Config/SharedPreferences when they
        // are first touched.  Do not pay that disk-I/O cost in Application.onCreate
        // on the main thread; the first UI frame does not depend on telemetry.
        applicationScope.launch {
            runCatching {
                AnalyticsTracker.configure(applicationContext)
                PerformanceTracker.configure(applicationContext)
                CrashReporter.configure(applicationContext)
            }.onFailure { error ->
                com.valoser.futacha.shared.util.Logger.w(
                    "FutachaApplication",
                    "Telemetry initialization skipped: ${error::class.simpleName.orEmpty()}"
                )
            }
        }
        fileSystemValue = createFileSystem(applicationContext)
        appStateStoreValue = createAppStateStore(applicationContext, fileSystem)
        compatibilityStore = AndroidCompatibilityStore(applicationContext, fileSystem)
        modeSwitchCoordinator = AndroidModeSwitchCoordinator(
            profileStore = experienceProfileStore,
            aliasReconciler = AndroidLauncherAliasManager(applicationContext)
        )
        applicationScope.launch {
            compatibilityStore.initialize()
            compatibilityStore.recoverStaleArchiveReports(System.currentTimeMillis())
            // Archive reporting is optional telemetry and is not part of the
            // first interactive screen.  WorkManager startup plus the first
            // outbox scan can compete with Compose's initial measure/draw on
            // compact devices, so leave the UI a quiet startup window.
            delay(STARTUP_ARCHIVE_REPORT_DELAY_MILLIS)
            ArchiveReportWorker.enqueueStartup(applicationContext)
            modeSwitchCoordinator.recoverIfNeeded().onFailure { error ->
                com.valoser.futacha.shared.util.Logger.e(
                    "FutachaApplication",
                    "Failed to reconcile experience profile",
                    error
                )
            }
        }

        applicationScope.launch {
            appStateStore.isTelemetryCollectionEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                    runCatching {
                        AnalyticsTracker.setQualityCollectionEnabled(enabled)
                        PerformanceTracker.setCollectionEnabled(enabled)
                        CrashReporter.setCollectionEnabled(enabled)
                        CrashReporter.setKey("telemetry_enabled", analyticsEnabledValue(enabled))
                    }.onFailure { error ->
                        com.valoser.futacha.shared.util.Logger.w(
                            "FutachaApplication",
                            "Telemetry state update skipped: ${error::class.simpleName.orEmpty()}"
                        )
                    }
                }
        }

        // FIX: 起動時ANR防止 - 一時ファイルクリーンアップはバックグラウンドで実行
        applicationScope.launch {
            delay(STARTUP_TEMP_CLEANUP_DELAY_MILLIS)
            (fileSystem as? com.valoser.futacha.shared.util.AndroidFileSystem)
                ?.cleanupTempFiles(includeExternalStorage = false)
                ?.onSuccess { count ->
                    if (count > 0) {
                        com.valoser.futacha.shared.util.Logger.i("FutachaApplication", "Cleaned up $count temp files")
                    }
                }
        }

        autoSavedThreadRepositoryValue = SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
        cookieStorageValue = PersistentCookieStorage(fileSystem)
        cookieRepositoryValue = CookieRepository(cookieStorage)
        // Ktor's engine setup can initialize ServiceLoader/SLF4J classes and read
        // jars. Do it off the main thread; the Activity shows a short loading state
        // until this graph is ready instead of paying that cost during first frame.
        applicationScope.launch {
            var initializingClient: io.ktor.client.HttpClient? = null
            runCatching {
                val client = createHttpClient(applicationContext, cookieStorage).also {
                    initializingClient = it
                }
                val repository = DefaultBoardRepository(
                    api = HttpBoardApi(client),
                    parser = createHtmlParser(),
                    cookieRepository = cookieRepository,
                    diagnosticFileSystem = fileSystem,
                    catalogFetchSettingsProvider = {
                        val modernSettings = CatalogFetchSettings(
                            rows = appStateStore.catalogFetchRows.first()
                        ).normalized()
                        if (experienceProfileStore.activeProfile.value == ExperienceProfile.TOSHIAKI_COMPAT) {
                            val requestedThreads = compatibilityStore.loadPreference(
                                "compat.catalog.catalogThreadSize"
                            )?.filter(Char::isDigit)?.toIntOrNull()
                                ?.coerceIn(50, 3_000)
                                ?: 300
                            // The compatibility APK encodes the catalog
                            // size as `${size / 25}x25x256x0x1`. Keep the
                            // background/watch refresh path identical to the
                            // foreground compatibility catalog. Match the
                            // reference APK's integer division exactly.
                            CatalogFetchSettings(
                                columns = (requestedThreads / 25).coerceIn(2, 120),
                                rows = 25,
                                titleLines = 256,
                                showVisitedHistory = true
                            ).normalized()
                        } else {
                            modernSettings
                        }
                    }
                )
                val history = HistoryRefresher(
                    stateStore = appStateStore,
                    repository = repository,
                    dispatcher = Dispatchers.IO,
                    autoSavedThreadRepository = autoSavedThreadRepository,
                    httpClient = client,
                    fileSystem = fileSystem,
                    maxConcurrency = 1
                )
                val catalogWatch = CatalogWatchAlertRefresher(
                    stateStore = appStateStore,
                    repository = repository,
                    dispatcher = Dispatchers.IO
                )
                val watchSync = WatchSyncManager(
                    context = applicationContext,
                    stateStore = appStateStore,
                    historyRefresher = history,
                    autoSavedThreadRepository = autoSavedThreadRepository,
                    fileSystem = fileSystem,
                    scope = applicationScope,
                    isModernProfileActive = {
                        experienceProfileStore.activeProfile.value == ExperienceProfile.FUTACHA
                    },
                    currentModernProfileGeneration = {
                        experienceProfileStore.captureGenerationIfCommitAllowed(ExperienceProfile.FUTACHA)
                    },
                    runIfModernProfileGenerationCurrent = { generation, commit ->
                        experienceProfileStore.runIfGenerationCurrent(
                            ExperienceProfile.FUTACHA,
                            generation,
                            commit
                        )
                    }
                )
                httpClientValue = client
                boardRepositoryValue = repository
                historyRefresherValue = history
                catalogWatchAlertRefresherValue = catalogWatch
                watchSyncManagerValue = watchSync
                networkServicesReadyValue.value = true
            }.onFailure { error ->
                // The client is created before the remaining repository graph.
                // Do not retain its engine/pool when a later constructor fails.
                initializingClient?.let { client ->
                    if (httpClientValue !== client) {
                        runCatching { client.close() }
                    }
                }
                if (error is CancellationException) throw error
                networkServicesErrorValue.value = error.message ?: error::class.simpleName
                com.valoser.futacha.shared.util.Logger.e(
                    "FutachaApplication",
                    "Failed to initialize network services",
                    error
                )
            }
        }

        applicationScope.launch {
            if (!awaitNetworkServicesReady()) return@launch
            experienceProfileStore.activeProfile.collect { profile ->
                if (profile == ExperienceProfile.FUTACHA) watchSyncManager.start()
                else watchSyncManager.stopAndAwait()
            }
        }

        applicationScope.launch {
            if (!awaitNetworkServicesReady()) return@launch
            // Initialize WorkManager off the Application.onCreate() critical path.
            val workManager = WorkManager.getInstance(applicationContext)
            StorageMaintenanceWorker.enqueuePeriodic(workManager)
            val schedulerPrefs = applicationContext.getSharedPreferences(
                BACKGROUND_REFRESH_SCHEDULER_PREFS,
                Context.MODE_PRIVATE
            )
            var hasObservedBackgroundToggle = false
            try {
                combine(
                    appStateStore.isBackgroundRefreshEnabled,
                    appStateStore.isWatchAlertEnabled,
                    compatibilityStore.preferences,
                    experienceProfileStore.activeProfile
                ) { backgroundEnabled, watchAlertEnabled, compatPreferences, activeProfile ->
                    val enabled = when (activeProfile) {
                        ExperienceProfile.FUTACHA -> backgroundEnabled || watchAlertEnabled
                        ExperienceProfile.TOSHIAKI_COMPAT -> {
                            val update = compatPreferences["compat.background.backgroundThreadUpdateCheck"]
                            val existence = compatPreferences["compat.background.backgroundThreadExistCheck"]
                            val watchWords = compatPreferences["compat.catalog.監視ワード"]
                                .orEmpty()
                                .lineSequence()
                                .map(String::trim)
                                .any(String::isNotEmpty)
                            compatForegroundPolicyEnabled(update) ||
                                compatForegroundPolicyEnabled(existence) ||
                                watchWords
                        }
                    }
                    activeProfile to enabled
                }
                    .distinctUntilChanged()
                    .onEach { (_, enabled) ->
                        if (enabled) {
                            val profileGeneration = experienceProfileStore.readGeneration()
                            HistoryRefreshWorker.enqueuePeriodic(workManager, profileGeneration)
                            val nowMillis = System.currentTimeMillis()
                            if (
                                shouldEnqueueImmediateBackgroundRefresh(
                                    enabled = true,
                                    hasObservedBackgroundToggle = hasObservedBackgroundToggle,
                                    lastImmediateEnqueueEpochMillis = schedulerPrefs.getLong(
                                        LAST_IMMEDIATE_BACKGROUND_REFRESH_ENQUEUE_MILLIS,
                                        0L
                                    ),
                                    nowEpochMillis = nowMillis
                                )
                            ) {
                                HistoryRefreshWorker.enqueueImmediate(workManager, profileGeneration)
                                schedulerPrefs
                                    .edit()
                                    .putLong(LAST_IMMEDIATE_BACKGROUND_REFRESH_ENQUEUE_MILLIS, nowMillis)
                                    .apply()
                            }
                        } else {
                            HistoryRefreshWorker.cancel(workManager)
                        }
                        hasObservedBackgroundToggle = true
                    }
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) throw cause
                        val shouldRetry = attempt < BACKGROUND_FLOW_MAX_RETRIES
                        if (!shouldRetry) {
                            com.valoser.futacha.shared.util.Logger.e(
                                "FutachaApplication",
                                "Background refresh flow failed too many times; stopping collector",
                                cause
                            )
                            return@retryWhen false
                        }
                        val backoffMillis = (1_000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)
                        com.valoser.futacha.shared.util.Logger.e(
                            "FutachaApplication",
                            "Background refresh flow failed; retrying in ${backoffMillis}ms (attempt=${attempt + 1})",
                            cause
                        )
                        delay(backoffMillis)
                        true
                    }
                    .collect()
                com.valoser.futacha.shared.util.Logger.w(
                    "FutachaApplication",
                    "Background refresh flow completed unexpectedly"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.valoser.futacha.shared.util.Logger.e(
                    "FutachaApplication",
                    "Background refresh flow collection terminated unexpectedly",
                    e
                )
            }
        }
    }

    private suspend fun awaitNetworkServicesReady(): Boolean =
        combine(networkServicesReady, networkServicesError) { ready, error -> ready to error }
            .first { (ready, error) -> ready || error != null }
            .first

    @Synchronized
    fun scheduleProfileRootRelaunch(
        threadDeepLink: String?,
        expectedProfile: ExperienceProfile = experienceProfileStore.readActiveProfile()
    ) {
        profileRootRelaunchJob?.cancel()
        val expectedGeneration = experienceProfileStore.readGeneration()
        profileRootRelaunchJob = applicationScope.launch {
            // Launcher alias updates emit a package-changed event after the component call returns.
            // API 37 closes the current task at that point even with DONT_KILL_APP, so reopening
            // immediately is racy. Relaunch from the application scope after the alias settles.
            delay(PROFILE_ALIAS_SETTLE_DELAY_MILLIS)
            if (experienceProfileStore.readActiveProfile() != expectedProfile ||
                experienceProfileStore.readGeneration() != expectedGeneration
            ) {
                com.valoser.futacha.shared.util.Logger.d(
                    "FutachaApplication",
                    "Skipping stale profile root relaunch"
                )
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                startActivity(
                    Intent(this@FutachaApplication, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        threadDeepLink?.let { data = Uri.parse(it) }
                    }
                )
            }
        }
    }

    private fun configureDebugStrictMode() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
    }

    override fun onTerminate() {
        // This is only called in emulators, but close resources defensively
        applicationScope.cancel()
        // httpClient will be closed automatically when scope is cancelled
        // Avoid runBlocking to prevent ANR
        boardRepositoryValue?.closeAsync()
        super.onTerminate()
    }

    private fun <T : Any> requireMainProcessValue(name: String, value: T?): T {
        return value ?: error(
            "FutachaApplication.$name is not initialized. " +
                "A lightweight worker process skips app-wide initialization; its service must not access this value."
        )
    }

    private fun isLightweightWorkerProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            currentProcessNameCompat()
        }
        return processName == "$packageName:ai" ||
            processName == "$packageName:compat_snapshot_crash_test" ||
            processName == "$packageName:profile_switch_crash_test"
    }

    @Suppress("DEPRECATION")
    private fun currentProcessNameCompat(): String? {
        val currentPid = android.os.Process.myPid()
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return null
        return activityManager.runningAppProcesses
            ?.firstOrNull { it.pid == currentPid }
            ?.processName
    }
}
