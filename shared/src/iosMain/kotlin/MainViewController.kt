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
import com.valoser.futacha.shared.repo.DefaultBoardRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.createFileSystem
import platform.UIKit.UIViewController
import version.createVersionChecker

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
            stateStore.isBackgroundRefreshEnabled.collect { enabled ->
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
        val repo = DefaultBoardRepository(
            api = com.valoser.futacha.shared.network.HttpBoardApi(httpClient),
            parser = createHtmlParser()
        )
        try {
            val refresher = HistoryRefresher(
                stateStore = stateStore,
                repository = repo,
                dispatcher = kotlinx.coroutines.Dispatchers.Default,
                autoSavedThreadRepository = autoSaveRepo,
                httpClient = httpClient,
                fileSystem = fileSystem
            )
            refresher.refresh()
        } catch (t: Throwable) {
            Logger.e("BackgroundRefresh", "Background history refresh failed", t)
        }
    }
}
