package com.valoser.futacha

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import androidx.work.WorkManager
import com.valoser.futacha.shared.network.HttpBoardApi
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.DefaultBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.version.initializeVersionCheckerContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val BACKGROUND_FLOW_MAX_RETRIES = 12L

class FutachaApplication : Application() {
    private var appStateStoreValue: AppStateStore? = null
    val appStateStore: AppStateStore
        get() = requireMainProcessValue("appStateStore", appStateStoreValue)

    private var httpClientValue: io.ktor.client.HttpClient? = null
    val httpClient: io.ktor.client.HttpClient
        get() = requireMainProcessValue("httpClient", httpClientValue)

    private var boardRepositoryValue: BoardRepository? = null
    val boardRepository: BoardRepository
        get() = requireMainProcessValue("boardRepository", boardRepositoryValue)

    private var historyRefresherValue: HistoryRefresher? = null
    val historyRefresher: HistoryRefresher
        get() = requireMainProcessValue("historyRefresher", historyRefresherValue)

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

    private var watchSyncManagerValue: WatchSyncManager? = null
    val watchSyncManager: WatchSyncManager
        get() = requireMainProcessValue("watchSyncManager", watchSyncManagerValue)

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (isAiWorkerProcess()) {
            return
        }
        initializeVersionCheckerContext(applicationContext)
        appStateStoreValue = createAppStateStore(applicationContext)
        fileSystemValue = createFileSystem(applicationContext)

        // FIX: 起動時ANR防止 - 一時ファイルクリーンアップはバックグラウンドで実行
        applicationScope.launch {
            (fileSystem as? com.valoser.futacha.shared.util.AndroidFileSystem)?.cleanupTempFiles()
                ?.onSuccess { count ->
                    if (count > 0) {
                        com.valoser.futacha.shared.util.Logger.i("FutachaApplication", "Cleaned up $count temp files")
                    }
                }
        }

        autoSavedThreadRepositoryValue = SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
        cookieStorageValue = PersistentCookieStorage(fileSystem)
        cookieRepositoryValue = CookieRepository(cookieStorage)
        httpClientValue = createHttpClient(applicationContext, cookieStorage)
        boardRepositoryValue = DefaultBoardRepository(
            api = HttpBoardApi(httpClient),
            parser = createHtmlParser(),
            cookieRepository = cookieRepository
        )
        historyRefresherValue = HistoryRefresher(
            stateStore = appStateStore,
            repository = boardRepository,
            dispatcher = Dispatchers.IO,
            autoSavedThreadRepository = autoSavedThreadRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            maxConcurrency = 1
        )
        watchSyncManagerValue = WatchSyncManager(
            context = applicationContext,
            stateStore = appStateStore,
            historyRefresher = historyRefresher,
            autoSavedThreadRepository = autoSavedThreadRepository,
            fileSystem = fileSystem,
            scope = applicationScope
        )
        watchSyncManager.start()

        applicationScope.launch {
            // Initialize WorkManager off the Application.onCreate() critical path.
            val workManager = WorkManager.getInstance(applicationContext)
            var hasObservedBackgroundToggle = false
            try {
                appStateStore.isBackgroundRefreshEnabled
                    .distinctUntilChanged()
                    .onEach { enabled ->
                        if (enabled) {
                            HistoryRefreshWorker.enqueuePeriodic(workManager)
                            if (hasObservedBackgroundToggle) {
                                HistoryRefreshWorker.enqueueImmediate(workManager)
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
                "The :ai process skips app-wide initialization; only AndroidAiWorkerService should run there."
        )
    }

    private fun isAiWorkerProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            currentProcessNameCompat()
        }
        return processName == "$packageName:ai"
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
