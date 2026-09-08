package com.valoser.futacha

import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import androidx.annotation.RequiresApi
import com.valoser.futacha.shared.compat.CompatBackGestureBus

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class ThreadDrawerBackAnimationCallback(private val onBack: () -> Unit) : OnBackAnimationCallback {
    private var drawerRequest: (() -> Boolean)? = null

    override fun onBackStarted(backEvent: BackEvent) {
        drawerRequest = if (backEvent.swipeEdge == BackEvent.EDGE_LEFT) {
            CompatBackGestureBus.captureDrawerRequest()
        } else null
    }

    override fun onBackProgressed(backEvent: BackEvent) = Unit

    override fun onBackCancelled() {
        drawerRequest = null
    }

    override fun onBackInvoked() {
        // Android limits exclusion to 200dp. Outside that region a thread's
        // left-edge swipe reaches this callback instead of Compose's drawer.
        val request = drawerRequest
        drawerRequest = null
        if (request != null) {
            // A screen disposed or blocked during the gesture rejects the
            // captured request. Drop it rather than navigating its successor.
            request()
        } else {
            onBack()
        }
    }
}
