package com.valoser.futacha

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.ui.FutachaApp
import version.createVersionChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FutachaApplication
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.appStateStore.isBackgroundRefreshEnabled.collect { enabled ->
                    val intent = Intent(this@MainActivity, HistoryRefreshService::class.java)
                    if (enabled) {
                        ContextCompat.startForegroundService(this@MainActivity, intent)
                    } else {
                        stopService(intent)
                    }
                }
            }
        }
        setContent {
            val stateStore = app.appStateStore
            val httpClient = app.httpClient
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
