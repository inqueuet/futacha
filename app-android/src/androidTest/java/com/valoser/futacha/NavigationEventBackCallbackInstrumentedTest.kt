package com.valoser.futacha

import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@SdkSuppress(minSdkVersion = 34)
class NavigationEventBackCallbackInstrumentedTest {
    @Test
    fun removedInputIgnoresQueuedPlatformCallbacks() = verifyLateCallbacks(dispose = false)

    @Test
    fun disposedDispatcherIgnoresQueuedPlatformCallbacks() = verifyLateCallbacks(dispose = true)

    private fun verifyLateCallbacks(dispose: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val platform = RecordingPlatformDispatcher()
            val dispatcher = NavigationEventDispatcher()
            var disposed = false
            val input = OnBackInvokedDefaultInput(platform)
            var completions = 0
            val handler = object : NavigationEventHandler<NavigationEventInfo>(
                initialInfo = NavigationEventInfo.None,
                isBackEnabled = true
            ) {
                override fun onBackCompleted() { completions++ }
            }
            try {
                dispatcher.addHandler(handler)
                dispatcher.addInput(input)
                val callback = platform.registered as OnBackAnimationCallback
                callback.onBackStarted(BackEvent(0f, 100f, 0f, BackEvent.EDGE_LEFT))
                if (dispose) {
                    dispatcher.dispose()
                    disposed = true
                } else {
                    dispatcher.removeInput(input)
                }
                assertNull(platform.registered)

                // Retain the real AndroidX callback just as a queued platform animation does.
                callback.onBackProgressed(BackEvent(50f, 100f, 0.5f, BackEvent.EDGE_LEFT))
                callback.onBackCancelled()
                callback.onBackInvoked()
                callback.onBackStarted(BackEvent(0f, 100f, 0f, BackEvent.EDGE_LEFT))
                assertEquals(0, completions)

                if (!dispose) {
                    // Complete the departing screen's lifecycle before attaching its successor.
                    handler.remove()
                    dispatcher.addHandler(handler)
                    dispatcher.addInput(OnBackInvokedDefaultInput(platform))
                    val replacement = platform.registered as OnBackAnimationCallback
                    replacement.onBackStarted(BackEvent(0f, 100f, 0f, BackEvent.EDGE_RIGHT))
                    replacement.onBackCancelled()
                    assertEquals(0, completions)
                    replacement.onBackInvoked()
                    assertEquals(1, completions)
                }
            } finally {
                if (!disposed) dispatcher.dispose()
            }
        }
    }

    private class RecordingPlatformDispatcher : OnBackInvokedDispatcher {
        var registered: OnBackInvokedCallback? = null
        override fun registerOnBackInvokedCallback(priority: Int, callback: OnBackInvokedCallback) {
            assertEquals(OnBackInvokedDispatcher.PRIORITY_DEFAULT, priority)
            registered = callback
        }
        override fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback) {
            assertSame(registered, callback)
            registered = null
        }
    }
}
