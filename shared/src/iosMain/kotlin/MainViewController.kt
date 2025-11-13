package com.valoser.futacha.shared

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.util.createFileSystem
import platform.UIKit.UIViewController
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
