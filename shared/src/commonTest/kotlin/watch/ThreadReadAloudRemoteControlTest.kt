package com.valoser.futacha.shared.watch

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadReadAloudRemoteControlTest {
    @AfterTest
    fun tearDown() {
        ThreadReadAloudRemoteControl.clearForTest()
    }

    @Test
    fun dispatchReachesOnlyTheRegisteredThreadUntilUnregistered() {
        val received = mutableListOf<Pair<String, ThreadReadAloudRemoteControl.Command>>()
        val first = ThreadReadAloudRemoteControl.register("b", "https://may.2chan.net/b", "123") {
            received += "123" to it
        }
        ThreadReadAloudRemoteControl.register("b", "https://may.2chan.net/b", "456") {
            received += "456" to it
        }

        assertTrue(ThreadReadAloudRemoteControl.dispatch(ThreadReadAloudRemoteControl.Command.Stop, "B", null, "123"))
        // The board URL also identifies the board (watch ids can differ in case or form).
        assertTrue(
            ThreadReadAloudRemoteControl.dispatch(
                ThreadReadAloudRemoteControl.Command.Pause, "other", "https://may.2chan.net/b/", "123"
            )
        )
        assertFalse(ThreadReadAloudRemoteControl.dispatch(ThreadReadAloudRemoteControl.Command.Stop, "img", null, "123"))
        assertEquals(
            listOf("123" to ThreadReadAloudRemoteControl.Command.Stop, "123" to ThreadReadAloudRemoteControl.Command.Pause),
            received
        )

        first.unregister()
        assertFalse(ThreadReadAloudRemoteControl.dispatch(ThreadReadAloudRemoteControl.Command.Stop, "b", null, "123"))
    }
}
