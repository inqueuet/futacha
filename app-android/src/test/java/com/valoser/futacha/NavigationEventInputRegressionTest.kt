package com.valoser.futacha

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercise the resolved AndroidX artifact, so a dependency downgrade reintroduces the failure. */
class NavigationEventInputRegressionTest {
    @Test
    fun lateBackEventsAfterInputRemovalAreIgnoredAndReplacementStillWorks() {
        val dispatcher = NavigationEventDispatcher()
        val handler = RecordingHandler()
        val removed = BackInput()
        val replacement = BackInput()
        try {
            dispatcher.addHandler(handler)
            dispatcher.addInput(removed)
            removed.start()
            removed.progress()
            assertEquals(listOf("start", "progress"), handler.events)
            dispatcher.removeInput(removed)
            dispatcher.addInput(replacement)
            val afterRemoval = handler.events.toList()

            // Android's BackProgressAnimator can retain a callback after unregistering it.
            removed.progress()
            removed.cancel()
            removed.complete()
            removed.start()
            assertEquals(afterRemoval, handler.events)

            // Leaving the screen also removes its handler, ending the old gesture.
            handler.remove()
            val successor = RecordingHandler()
            dispatcher.addHandler(successor)
            replacement.start()
            replacement.progress()
            removed.complete()
            assertEquals(listOf("start", "progress"), successor.events)
            replacement.complete()
            assertEquals(listOf("start", "progress", "complete"), successor.events)
        } finally {
            dispatcher.dispose()
        }
    }

    @Test
    fun lateBackEventsAfterDispatcherDisposalAreIgnored() {
        val dispatcher = NavigationEventDispatcher()
        val handler = RecordingHandler()
        val input = BackInput()
        dispatcher.addHandler(handler)
        dispatcher.addInput(input)
        input.start()
        dispatcher.dispose()
        val afterDisposal = handler.events.toList()

        input.progress()
        input.cancel()
        input.complete()
        input.start()
        assertEquals(afterDisposal, handler.events)
    }

    @Test
    fun connectedCancellationAndButtonBackKeepTheirSemantics() {
        val dispatcher = NavigationEventDispatcher()
        val handler = RecordingHandler()
        val input = BackInput()
        try {
            dispatcher.addHandler(handler)
            dispatcher.addInput(input)
            input.start()
            input.progress()
            input.cancel()
            assertEquals(listOf("start", "progress", "cancel"), handler.events)

            handler.events.clear()
            input.complete() // Button Back has no predictive start/progress events.
            assertEquals(listOf("complete"), handler.events)
        } finally {
            dispatcher.dispose()
        }
    }

    private class BackInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(progress = 0f))
        fun progress() = dispatchOnBackProgressed(NavigationEvent(progress = 0.5f))
        fun cancel() = dispatchOnBackCancelled()
        fun complete() = dispatchOnBackCompleted()
    }

    private class RecordingHandler : NavigationEventHandler<NavigationEventInfo>(
        initialInfo = NavigationEventInfo.None,
        isBackEnabled = true
    ) {
        val events = mutableListOf<String>()
        override fun onBackStarted(event: NavigationEvent) { events += "start" }
        override fun onBackProgressed(event: NavigationEvent) { events += "progress" }
        override fun onBackCancelled() { events += "cancel" }
        override fun onBackCompleted() { events += "complete" }
    }
}
