package com.valoser.futacha.shared

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.background.BackgroundRefreshManager
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
import platform.UIKit.UIViewController
import platform.Foundation.NSUserDefaults
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlin.coroutines.cancellation.CancellationException

private object IosAppGraph {
    val stateStore by lazy { createAppStateStore() }
    val fileSystem by lazy { createFileSystem() }
    val autoSavedThreadRepository by lazy {
        SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
    }
    val cookieStorage by lazy { PersistentCookieStorage(fileSystem) }
    val cookieRepository by lazy { CookieRepository(cookieStorage) }
    val httpClient by lazy { createHttpClient(cookieStorage = cookieStorage) }
}

private const val IOS_BG_MAX_THREADS_PER_RUN = 120
private const val IOS_BACKGROUND_FLOW_MAX_RETRIES = 12L
private const val IOS_BACKGROUND_REFRESH_KEY = "background_refresh_enabled"

fun registerIosBackgroundRefreshTask() {
    BackgroundRefreshManager.registerAtLaunch()
    val enabled = NSUserDefaults.standardUserDefaults().boolForKey(IOS_BACKGROUND_REFRESH_KEY)
    Logger.d("MainViewController", "registerIosBackgroundRefreshTask(enabled=$enabled)")
    configureIosBackgroundRefresh(
        enabled = enabled,
        stateStore = IosAppGraph.stateStore,
        httpClient = IosAppGraph.httpClient,
        fileSystem = IosAppGraph.fileSystem,
        autoSaveRepo = IosAppGraph.autoSavedThreadRepository
    )
}

fun MainViewController(): UIViewController {
    registerIosBackgroundRefreshTask()
    return ComposeUIViewController {
        val stateStore = remember { IosAppGraph.stateStore }
        val fileSystem = remember { IosAppGraph.fileSystem }
        val autoSavedThreadRepository = remember { IosAppGraph.autoSavedThreadRepository }
        val cookieRepository = remember { IosAppGraph.cookieRepository }
        val httpClient = remember { IosAppGraph.httpClient }
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
                            httpClient = httpClient,
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
    httpClient: io.ktor.client.HttpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveRepo: SavedThreadRepository?
) {
    Logger.d(
        "MainViewController",
        "configureIosBackgroundRefresh(enabled=$enabled, hasFileSystem=${fileSystem != null}, hasAutoSaveRepo=${autoSaveRepo != null})"
    )
    BackgroundRefreshManager.configure(enabled) {
        runIosBackgroundRefresh(
            stateStore = stateStore,
            httpClient = httpClient,
            fileSystem = fileSystem,
            autoSaveRepo = autoSaveRepo
        )
    }
}

private suspend fun runIosBackgroundRefresh(
    stateStore: com.valoser.futacha.shared.state.AppStateStore,
    httpClient: io.ktor.client.HttpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveRepo: SavedThreadRepository?
) {
    val sharedClientApi = com.valoser.futacha.shared.network.HttpBoardApi(httpClient)
    // Keep shared HttpClient ownership in MainViewController. Background repo closes only its own state.
    val nonClosingApi = object : BoardApi by sharedClientApi {}
    val repo = DefaultBoardRepository(
        api = nonClosingApi,
        parser = createHtmlParser()
    )
    try {
        Logger.d("BackgroundRefresh", "Starting iOS background refresh run (maxThreadsPerRun=$IOS_BG_MAX_THREADS_PER_RUN)")
        val refresher = HistoryRefresher(
            stateStore = stateStore,
            repository = repo,
            dispatcher = AppDispatchers.io,
            autoSavedThreadRepository = autoSaveRepo,
            httpClient = httpClient,
            fileSystem = fileSystem
        )
        refresher.refresh(maxThreadsPerRun = IOS_BG_MAX_THREADS_PER_RUN)
        Logger.d("BackgroundRefresh", "Completed iOS background refresh run successfully")
    } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
        Logger.d("BackgroundRefresh", "Refresh already running; skipping duplicate iOS background run")
    } catch (e: CancellationException) {
        Logger.w("BackgroundRefresh", "iOS background refresh run cancelled")
        throw e
    } finally {
        Logger.d("BackgroundRefresh", "Closing temporary iOS background repository")
        repo.closeAsync().join()
    }
}
