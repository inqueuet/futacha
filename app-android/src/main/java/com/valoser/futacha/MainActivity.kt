package com.valoser.futacha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.ui.FutachaApp
import version.createVersionChecker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.work.WorkManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FutachaApplication
        val workManager = WorkManager.getInstance(applicationContext)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.appStateStore.isBackgroundRefreshEnabled
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            HistoryRefreshWorker.enqueuePeriodic(workManager)
                            HistoryRefreshWorker.enqueueImmediate(workManager)
                        } else {
                            HistoryRefreshWorker.cancel(workManager)
                        }
                    }
            }
        }
        setContent {
            val stateStore = app.appStateStore
            val httpClient = app.httpClient
            val cookieRepository = remember { app.cookieRepository }
            val versionChecker = remember { createVersionChecker(applicationContext, httpClient) }
            val fileSystem = remember { app.fileSystem }
            FutachaApp(
                stateStore = stateStore,
                versionChecker = versionChecker,
                httpClient = httpClient,
                fileSystem = fileSystem,
                cookieRepository = cookieRepository
            )
        }
    }
}
