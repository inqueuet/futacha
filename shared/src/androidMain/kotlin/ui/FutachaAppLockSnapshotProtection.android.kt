package com.valoser.futacha.shared.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Owns only the protection this app adds; unrelated pre-existing window flags remain owned by their callers. */
internal class AndroidAppLockSnapshotProtection(private val activity: Activity) {
    private val originalSecure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    fun update(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!enabled)
        else restoreSecure(enabled || originalSecure)
    }
    fun close() {
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(true)
        else restoreSecure(originalSecure)
    }
    private fun restoreSecure(secure: Boolean) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
internal actual fun PlatformAppLockSnapshotProtection(store: AppStateStore, lock: FutachaAppLockHolder, retryGeneration: Int) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(view, store, lock, lifecycleOwner, retryGeneration) {
        val activity = view.context.snapshotActivity()
        val policy = activity?.let(::AndroidAppLockSnapshotProtection)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var configured = true
        policy?.update(true) // Loading and failed configuration reads protect the initial window.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && configured) lock.lockSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { store.appLockPasswordHash.collect {
                configured = it != null
                policy?.update(configured)
                if (configured && !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) lock.lockSession()
            } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { configured = true; policy?.update(true) }
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); scope.cancel(); policy?.close() }
    }
}

private tailrec fun Context.snapshotActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.snapshotActivity()
    else -> null
}

@Composable internal actual fun Modifier.appLockSnapshotFrameReady(): Modifier = this
internal actual fun appLockSnapshotProtectionNotice(): String? =
    if (Build.VERSION.SDK_INT < 33) "Android 12L以前では、有効中は通常のスクリーンショットと画面録画も禁止します。" else null
