package com.valoser.futacha.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import platform.UIKit.UIViewController
import version.createVersionChecker

fun MainViewController(): UIViewController {
    val stateStore = createAppStateStore()
    val httpClient = createHttpClient()
    val versionChecker = createVersionChecker(httpClient)

    return ComposeUIViewController {
        FutachaApp(
            stateStore = stateStore,
            versionChecker = versionChecker
        )
    }
}
