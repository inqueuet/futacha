package com.valoser.futacha

import android.window.BackEvent
import androidx.test.filters.SdkSuppress
import com.valoser.futacha.shared.compat.CompatBackGestureBus
import org.junit.Test
import org.junit.Assert.assertEquals

@SdkSuppress(minSdkVersion = 34)
class ThreadDrawerBackAnimationCallbackTest {
    @Test
    fun leftCommitOpensDrawerButRightButtonAndCancellationDoNot() {
        val owner = Any()
        var opens = 0
        var backs = 0
        try {
            CompatBackGestureBus.register(owner) { opens++; true }
            val callback = ThreadDrawerBackAnimationCallback { backs++ }
            callback.onBackStarted(BackEvent(0f, 900f, 0f, BackEvent.EDGE_LEFT))
            callback.onBackCancelled()
            assertEquals(0, opens)
            assertEquals(0, backs)
            // A completion without a predictive session also remains ordinary Back.
            callback.onBackInvoked()
            callback.onBackStarted(BackEvent(1080f, 900f, 0f, BackEvent.EDGE_RIGHT))
            callback.onBackInvoked()
            assertEquals(0, opens)
            assertEquals(2, backs)
            callback.onBackStarted(BackEvent(0f, 900f, 0f, BackEvent.EDGE_LEFT))
            callback.onBackInvoked()
            assertEquals(1, opens)
            assertEquals(2, backs)
        } finally { CompatBackGestureBus.unregister(owner) }
    }

    @Test
    fun androidButtonStartNotificationsNeverOpenDrawer() {
        val owner = Any()
        var opens = 0
        var backs = 0
        try {
            CompatBackGestureBus.register(owner) { opens++; true }
            val callback = ThreadDrawerBackAnimationCallback { backs++ }
            val buttonStarts = listOf(
                // ViewRootImpl.doOnBackKeyEvent on Android 14/15 and some Android 16 builds.
                BackEvent(0f, 0f, 0f, BackEvent.EDGE_LEFT),
                // Newer platforms distinguish button input using EDGE_NONE (= 2).
                BackEvent(0f, 0f, 0f, 2),
                BackEvent(Float.NaN, Float.NaN, 0f, 2),
                // Missing touch coordinates must not be treated as a left-edge touch either.
                BackEvent(Float.NaN, Float.NaN, 0f, BackEvent.EDGE_LEFT)
            )
            buttonStarts.forEach { event ->
                callback.onBackStarted(event)
                callback.onBackInvoked()
            }
            assertEquals(0, opens)
            assertEquals(buttonStarts.size, backs)
        } finally { CompatBackGestureBus.unregister(owner) }
    }

    @Test
    fun cancelledButtonAndButtonAfterLeftGestureDoNotReopenDrawer() {
        val owner = Any()
        var opens = 0
        var backs = 0
        try {
            CompatBackGestureBus.register(owner) { opens++; true }
            val callback = ThreadDrawerBackAnimationCallback { backs++ }
            callback.onBackStarted(BackEvent(0f, 900f, 0f, BackEvent.EDGE_LEFT))
            callback.onBackInvoked()
            assertEquals(1, opens)

            callback.onBackStarted(BackEvent(0f, 0f, 0f, BackEvent.EDGE_LEFT))
            callback.onBackCancelled()
            assertEquals(0, backs)
            repeat(4) {
                callback.onBackStarted(BackEvent(0f, 0f, 0f, BackEvent.EDGE_LEFT))
                callback.onBackInvoked()
            }
            assertEquals(1, opens)
            assertEquals(4, backs)
        } finally { CompatBackGestureBus.unregister(owner) }
    }

    @Test
    fun changingScreenDuringGestureDoesNotOpenDrawerOrNavigateSuccessor() {
        val owner = Any()
        var backs = 0
        try {
            CompatBackGestureBus.register(owner) { error("Stale screen") }
            val callback = ThreadDrawerBackAnimationCallback { backs++ }
            callback.onBackStarted(BackEvent(0f, 900f, 0f, BackEvent.EDGE_LEFT))
            CompatBackGestureBus.unregister(owner)
            callback.onBackInvoked()
            assertEquals(0, backs)
            callback.onBackStarted(BackEvent(0f, 900f, 0f, BackEvent.EDGE_LEFT))
            callback.onBackInvoked()
            assertEquals(1, backs)
        } finally { CompatBackGestureBus.unregister(owner) }
    }
}
