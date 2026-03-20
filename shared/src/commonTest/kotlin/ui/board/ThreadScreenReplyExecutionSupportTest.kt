package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadScreenReplyExecutionSupportTest {
    @Test
    fun shouldConfirmThreadPostingNotice_returnsFalseWithoutStateStore() = runBlocking {
        assertFalse(shouldConfirmThreadPostingNotice(null))
    }

    @Test
    fun checkPostingNoticeIfNeeded_skipsConfirmationWhenStateStoreIsMissing() = runBlocking {
        var confirmCallCount = 0

        val result = checkPostingNoticeIfNeeded(
            stateStore = null,
            confirmNotice = {
                confirmCallCount += 1
                true
            }
        )

        assertTrue(result)
        assertEquals(0, confirmCallCount)
    }

    @Test
    fun checkPostingNoticeIfNeeded_marksNoticeAsShownAfterAcceptance() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        var confirmCallCount = 0

        val result = checkPostingNoticeIfNeeded(
            stateStore = store,
            confirmNotice = {
                confirmCallCount += 1
                true
            }
        )

        assertTrue(result)
        assertEquals(1, confirmCallCount)
        assertTrue(store.hasShownPostingNotice.first())
        assertFalse(shouldConfirmThreadPostingNotice(store))
    }

    @Test
    fun checkPostingNoticeIfNeeded_keepsNoticeUnsetWhenConfirmationIsRejected() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        var confirmCallCount = 0

        val result = checkPostingNoticeIfNeeded(
            stateStore = store,
            confirmNotice = {
                confirmCallCount += 1
                false
            }
        )

        assertFalse(result)
        assertEquals(1, confirmCallCount)
        assertFalse(store.hasShownPostingNotice.first())
    }
}
