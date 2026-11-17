package com.valoser.futacha.shared.ui.board

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/** One counter per window's view (the flag belongs to the view); touched on the main thread only. */
private val androidKeepScreenOn = WeakHashMap<View, KeepScreenOnCounter>()

@Composable
internal actual fun PlatformKeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val counter = androidKeepScreenOn.getOrPut(view) { KeepScreenOnCounter { on -> view.keepScreenOn = on } }
        counter.acquire()
        onDispose {
            counter.release()
            if (counter.holders == 0) androidKeepScreenOn.remove(view)
        }
    }
}
