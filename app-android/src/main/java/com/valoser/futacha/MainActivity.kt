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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FutachaApplication
        enableEdgeToEdge()
        // FIX: サービス起動状態をトラッキングして重複起動を防止
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.appStateStore.isBackgroundRefreshEnabled
                    .distinctUntilChanged()
                    .collect { enabled ->
                        val intent = Intent(this@MainActivity, HistoryRefreshService::class.java)

                        // 状態が変わった時だけアクションを実行
                        if (enabled && !isServiceRunning) {
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                            isServiceRunning = true
                        } else if (!enabled && isServiceRunning) {
                            stopService(intent)
                            isServiceRunning = false
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

    // FIX: onDestroyでサービスを確実に停止
    override fun onDestroy() {
        if (isServiceRunning) {
            stopService(Intent(this, HistoryRefreshService::class.java))
            isServiceRunning = false
        }
        super.onDestroy()
    }
}
