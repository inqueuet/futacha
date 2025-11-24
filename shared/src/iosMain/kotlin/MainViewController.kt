package com.valoser.futacha.shared

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.parser.createHtmlParser
import com.valoser.futacha.shared.repo.DefaultBoardRepository
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.util.createFileSystem
import platform.UIKit.UIViewController
import platform.Foundation.NSLog
import version.createVersionChecker

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        val stateStore = remember { createAppStateStore() }
        val httpClient = remember { createHttpClient() }
        DisposableEffect(httpClient) {
            onDispose {
                httpClient.close()
            }
        }
        LaunchedEffect(stateStore, httpClient) {
            stateStore.isBackgroundRefreshEnabled.collect { enabled ->
                configureIosBackgroundRefresh(
                    enabled = enabled,
                    stateStore = stateStore,
                    httpClient = httpClient
                )
            }
        }
        val versionChecker = remember(httpClient) {
            createVersionChecker(httpClient)
        }
        val fileSystem = remember { createFileSystem() }

        FutachaApp(
            stateStore = stateStore,
            versionChecker = versionChecker,
            httpClient = httpClient,
            fileSystem = fileSystem
        )
    }
}

private fun configureIosBackgroundRefresh(
    enabled: Boolean,
    stateStore: com.valoser.futacha.shared.state.AppStateStore,
    httpClient: io.ktor.client.HttpClient
) {
    BackgroundRefreshManager.configure(enabled) {
        val repo = DefaultBoardRepository(
            api = com.valoser.futacha.shared.network.HttpBoardApi(httpClient),
            parser = createHtmlParser()
        )
        val refresher = HistoryRefresher(
            stateStore = stateStore,
            repository = repo,
            dispatcher = kotlinx.coroutines.Dispatchers.Default
        )
        refresher.refresh()
        repo.close()
    }
}
