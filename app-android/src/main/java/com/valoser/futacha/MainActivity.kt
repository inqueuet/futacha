package com.valoser.futacha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.version.createVersionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? FutachaApplication
        enableEdgeToEdge()
        setContent {
            val stateStore = remember(app) {
                app?.appStateStore ?: createAppStateStore(applicationContext)
            }
            val fileSystem = remember(app) {
                app?.fileSystem ?: createFileSystem(applicationContext)
            }
            val cookieStorage = remember(app, fileSystem) {
                app?.cookieStorage ?: PersistentCookieStorage(fileSystem)
            }
            val httpClient = remember(app, cookieStorage) {
                app?.httpClient ?: createHttpClient(applicationContext, cookieStorage)
            }
            val cookieRepository = remember(app, cookieStorage) {
                app?.cookieRepository ?: CookieRepository(cookieStorage)
            }
            val versionChecker = remember { createVersionChecker(applicationContext, httpClient) }
            val autoSavedThreadRepository = remember(app, fileSystem) {
                app?.autoSavedThreadRepository ?: SavedThreadRepository(
                    fileSystem,
                    baseDirectory = AUTO_SAVE_DIRECTORY
                )
            }
            DisposableEffect(app, httpClient) {
                onDispose {
                    if (app == null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { httpClient.close() }
                        }
                    }
                }
            }
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
