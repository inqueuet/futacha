package com.valoser.futacha

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingAiDeepLink by mutableStateOf<String?>(null)
    private val localResourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAiDeepLink = intent?.futachaAiDeepLinkOrNull()
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
                        // onDestroy cancels localResourceScope before the composition is
                        // disposed; NonCancellable keeps this cleanup job runnable.
                        localResourceScope.launch(NonCancellable) {
                            runCatching { httpClient.close() }
                        }
                    }
                }
            }
            FutachaApp(
                stateStore = stateStore,
                versionChecker = versionChecker,
                httpClient = httpClient,
                sharedRepository = app?.boardRepository,
                sharedHistoryRefresher = app?.historyRefresher,
                fileSystem = fileSystem,
                cookieRepository = cookieRepository,
                autoSavedThreadRepository = autoSavedThreadRepository,
                platformAiDeepLink = pendingAiDeepLink,
                onPlatformAiDeepLinkConsumed = { consumed ->
                    if (pendingAiDeepLink == consumed) {
                        pendingAiDeepLink = null
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        localResourceScope.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAiDeepLink = intent.futachaAiDeepLinkOrNull()
    }

    private fun Intent.futachaAiDeepLinkOrNull(): String? {
        val uri = data ?: return null
        if (uri.scheme != "futacha" || uri.host != "ai") {
            return null
        }
        return dataString
    }
}
