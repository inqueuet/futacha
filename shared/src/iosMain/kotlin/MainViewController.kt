package com.valoser.futacha.shared

import androidx.compose.runtime.LaunchedEffect
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
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.createFileSystem
import platform.UIKit.UIViewController
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlin.coroutines.cancellation.CancellationException

private object IosAppGraph {
    val stateStore by lazy { createAppStateStore() }
    val fileSystem by lazy { createFileSystem() }
    val autoSavedThreadRepository by lazy {
        fileSystem?.let { SavedThreadRepository(it, baseDirectory = AUTO_SAVE_DIRECTORY) }
    }
    val cookieStorage by lazy { PersistentCookieStorage(fileSystem) }
    val cookieRepository by lazy { CookieRepository(cookieStorage) }
    val httpClient by lazy { createHttpClient(cookieStorage = cookieStorage) }
}

fun registerIosBackgroundRefreshTask() {
    BackgroundRefreshManager.registerAtLaunch()
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
                stateStore.isBackgroundRefreshEnabled
                    .distinctUntilChanged()
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) throw cause
                        val backoffMillis = (1_000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)
                        Logger.e(
                            "MainViewController",
                            "Background refresh flow failed; retrying in ${backoffMillis}ms (attempt=${attempt + 1})",
                            cause
                        )
                        delay(backoffMillis)
                        true
                    }
                    .collect { enabled ->
                        configureIosBackgroundRefresh(
                            enabled = enabled,
                            stateStore = stateStore,
                            httpClient = httpClient,
                            fileSystem = fileSystem,
                            autoSaveRepo = autoSavedThreadRepository
                        )
                    }
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
    BackgroundRefreshManager.configure(enabled) {
        val sharedClientApi = com.valoser.futacha.shared.network.HttpBoardApi(httpClient)
        // Keep shared HttpClient ownership in MainViewController. Background repo closes only its own state.
        val nonClosingApi = object : BoardApi by sharedClientApi {}
        val repo = DefaultBoardRepository(
            api = nonClosingApi,
            parser = createHtmlParser()
        )
        try {
            val refresher = HistoryRefresher(
                stateStore = stateStore,
                repository = repo,
                dispatcher = kotlinx.coroutines.Dispatchers.IO,
                autoSavedThreadRepository = autoSaveRepo,
                httpClient = httpClient,
                fileSystem = fileSystem
            )
            refresher.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.e("BackgroundRefresh", "Background history refresh failed", t)
        } finally {
            repo.closeAsync().join()
        }
    }
}
