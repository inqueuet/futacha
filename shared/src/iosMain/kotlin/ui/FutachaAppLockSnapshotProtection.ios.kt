@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.interop.LocalUIViewController
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UISceneActivationStateBackground
import platform.UIKit.UIViewController
import platform.UIKit.UISceneDidEnterBackgroundNotification
import platform.darwin.NSObjectProtocol

internal const val IOS_SNAPSHOT_CONFIGURATION = "com.valoser.futacha.app-lock-snapshot-configuration"
internal const val IOS_SNAPSHOT_FRAME = "com.valoser.futacha.app-lock-snapshot-frame"
internal const val IOS_SNAPSHOT_REDRAW = "com.valoser.futacha.app-lock-snapshot-redraw"

/** Native observers and a direct settings collector remain live when Compose's frame clock is suspended. */
internal class IosAppLockSnapshotProtection(
    private val store: AppStateStore,
    private val lock: FutachaAppLockHolder,
    private val controller: UIViewController? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var background: NSObjectProtocol? = null
    private var sceneBackground: NSObjectProtocol? = null
    private var configured = true
    fun start() {
        background = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, null) {
            if (configured) lock.lockSession()
        }
        sceneBackground = NSNotificationCenter.defaultCenter.addObserverForName(UISceneDidEnterBackgroundNotification, null, null) { note ->
            if (configured && controller != null && note?.`object` == controller.view.window?.windowScene) lock.lockSession()
        }
        publish(true)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                store.appLockPasswordHash.collect {
                    configured = it != null
                    publish(configured)
                    if (configured && controller?.view?.window?.windowScene?.activationState == UISceneActivationStateBackground) lock.lockSession()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { configured = true; publish(true) }
        }
    }
    fun close() {
        background?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        background = null
        sceneBackground?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        sceneBackground = null
        scope.cancel()
    }
    private fun publish(enabled: Boolean) {
        NSNotificationCenter.defaultCenter.postNotificationName(IOS_SNAPSHOT_CONFIGURATION, null, mapOf("enabled" to enabled))
    }
}

@Composable
internal actual fun PlatformAppLockSnapshotProtection(store: AppStateStore, lock: FutachaAppLockHolder, retryGeneration: Int) {
    val controller = LocalUIViewController.current
    DisposableEffect(store, lock, controller, retryGeneration) {
        val protection = IosAppLockSnapshotProtection(store, lock, controller)
        protection.start()
        onDispose { protection.close() }
    }
}

private class AppLockFramePublication { var revision = -1 }

@Composable
internal actual fun Modifier.appLockSnapshotFrameReady(): Modifier {
    val controller = LocalUIViewController.current
    val redraw = remember { mutableIntStateOf(0) }
    val publication = remember { AppLockFramePublication() }
    DisposableEffect(controller) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(IOS_SNAPSHOT_REDRAW, null, null) {
            redraw.intValue++ // Force an actual fresh lock draw after foreground activation, even if its text did not change.
        }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return drawWithContent {
        val revision = redraw.intValue
        drawContent()
        if (publication.revision != revision) {
            val scene = controller.view.window?.windowScene?.session?.persistentIdentifier
            if (scene != null) {
                publication.revision = revision
                NSNotificationCenter.defaultCenter.postNotificationName(IOS_SNAPSHOT_FRAME, null, mapOf("sceneIdentifier" to scene))
            }
        }
    }
}

internal actual fun appLockSnapshotProtectionNotice(): String? = null
