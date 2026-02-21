package com.valoser.futacha.shared

import androidx.compose.runtime.DisposableEffect
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
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.createFileSystem
import platform.UIKit.UIViewController
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlin.coroutines.cancellation.CancellationException

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        val stateStore = remember { createAppStateStore() }
        val fileSystem = remember { createFileSystem() }
        val cookieStorage = remember(fileSystem) { PersistentCookieStorage(fileSystem) }
        val cookieRepository = remember(cookieStorage) { CookieRepository(cookieStorage) }
        val httpClient = remember(cookieStorage) { createHttpClient(cookieStorage = cookieStorage) }
        DisposableEffect(httpClient, cookieStorage) {
            onDispose {
                httpClient.close()
            }
        }
        LaunchedEffect(stateStore, httpClient, fileSystem) {
            stateStore.isBackgroundRefreshEnabled
                .distinctUntilChanged()
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) throw cause
                    val maxRetries = 6L
                    if (attempt >= maxRetries) {
                        Logger.e("MainViewController", "Background refresh flow failed too many times; stopping collection", cause)
                        return@retryWhen false
                    }
                    val backoffMillis = (1_000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)
                    Logger.e(
                        "MainViewController",
                        "Background refresh flow failed; retrying in ${backoffMillis}ms (attempt=${attempt + 1}/$maxRetries)",
                        cause
                    )
                    delay(backoffMillis)
                    true
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    Logger.e("MainViewController", "Background refresh flow terminated unexpectedly", e)
                }
                .collect { enabled ->
                    configureIosBackgroundRefresh(
                        enabled = enabled,
                        stateStore = stateStore,
                        httpClient = httpClient,
                        fileSystem = fileSystem
                    )
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
            cookieRepository = cookieRepository
        )
    }
}

private fun configureIosBackgroundRefresh(
    enabled: Boolean,
    stateStore: com.valoser.futacha.shared.state.AppStateStore,
    httpClient: io.ktor.client.HttpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?
) {
    val autoSaveRepo = fileSystem?.let { SavedThreadRepository(it, baseDirectory = com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY) }
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
