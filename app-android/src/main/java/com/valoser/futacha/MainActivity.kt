package com.valoser.futacha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.version.createVersionChecker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FutachaApplication
        enableEdgeToEdge()
        setContent {
            val stateStore = app.appStateStore
            val httpClient = app.httpClient
            val cookieRepository = remember { app.cookieRepository }
            val versionChecker = remember { createVersionChecker(applicationContext, httpClient) }
            val fileSystem = remember { app.fileSystem }
            val autoSavedThreadRepository = remember { app.autoSavedThreadRepository }
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
}
