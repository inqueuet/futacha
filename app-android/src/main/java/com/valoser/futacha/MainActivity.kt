package com.valoser.futacha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.network.createHttpClient
import version.createVersionChecker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val stateStore = remember { createAppStateStore(applicationContext) }
            val httpClient = remember { createHttpClient() }
            val versionChecker = remember { createVersionChecker(applicationContext, httpClient) }
            val fileSystem = remember { com.valoser.futacha.shared.util.createFileSystem(applicationContext) }
            FutachaApp(
                stateStore = stateStore,
                versionChecker = versionChecker,
                httpClient = httpClient,
                fileSystem = fileSystem
            )
        }
    }
}
