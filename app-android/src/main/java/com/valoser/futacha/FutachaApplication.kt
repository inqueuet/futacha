package com.valoser.futacha

import android.app.Application
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
    lateinit var appStateStore: AppStateStore
        private set
    lateinit var httpClient: io.ktor.client.HttpClient
        private set
    lateinit var boardRepository: BoardRepository
        private set
    lateinit var historyRefresher: HistoryRefresher
        private set
    lateinit var autoSavedThreadRepository: SavedThreadRepository
        private set
    lateinit var fileSystem: FileSystem
        private set
    lateinit var cookieStorage: PersistentCookieStorage
        private set
    lateinit var cookieRepository: CookieRepository
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initializeVersionCheckerContext(applicationContext)
        appStateStore = createAppStateStore(applicationContext)
        fileSystem = createFileSystem(applicationContext)

        // FIX: 起動時ANR防止 - 一時ファイルクリーンアップはバックグラウンドで実行
        applicationScope.launch {
            (fileSystem as? com.valoser.futacha.shared.util.AndroidFileSystem)?.cleanupTempFiles()
                ?.onSuccess { count ->
                    if (count > 0) {
                        com.valoser.futacha.shared.util.Logger.i("FutachaApplication", "Cleaned up $count temp files")
                    }
                }
        }

        autoSavedThreadRepository = SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
        cookieStorage = PersistentCookieStorage(fileSystem)
        cookieRepository = CookieRepository(cookieStorage)
        httpClient = createHttpClient(applicationContext, cookieStorage)
        boardRepository = DefaultBoardRepository(
            api = HttpBoardApi(httpClient),
            parser = createHtmlParser(),
            cookieRepository = cookieRepository
        )
        historyRefresher = HistoryRefresher(
            stateStore = appStateStore,
            repository = boardRepository,
            dispatcher = Dispatchers.IO,
            autoSavedThreadRepository = autoSavedThreadRepository,
            httpClient = httpClient,
            fileSystem = fileSystem
        )

        // Initialize WorkManager for background refresh
        val workManager = WorkManager.getInstance(applicationContext)
        applicationScope.launch {
            try {
                appStateStore.isBackgroundRefreshEnabled
                    .distinctUntilChanged()
                    .onEach { enabled ->
                        if (enabled) {
                            HistoryRefreshWorker.enqueuePeriodic(workManager)
                            HistoryRefreshWorker.enqueueImmediate(workManager)
                        } else {
                            HistoryRefreshWorker.cancel(workManager)
                        }
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
        boardRepository.closeAsync()
        super.onTerminate()
    }
}
