package com.valoser.futacha.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val stateStore = createAppStateStore()
    return ComposeUIViewController {
        FutachaApp(stateStore = stateStore)
    }
}
