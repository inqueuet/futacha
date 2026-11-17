package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostingNavigationGuardTest {
    @Test
    fun runUnlessPosting_holdsNavigationOnlyWhilePosting() {
        var navigated = 0
        var blocked = 0
        val whilePosting = runUnlessPosting(true, { blocked++ }, false) { navigated++; true }
        assertFalse(whilePosting)
        assertEquals(0, navigated)
        assertEquals(1, blocked)

        val afterPosting = runUnlessPosting(false, { blocked++ }, false) { navigated++; true }
        assertTrue(afterPosting)
        assertEquals(1, navigated)
        assertEquals(1, blocked)
    }

    @Test
    fun replySubmit_reportsSendingUntilTheRequestFinishes() = runBlocking {
        var dialogState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(comment = "本文", password = "key")
        )
        var actionInProgress = false
        var busyNoticeAt = 0L
        val sendingChanges = mutableListOf<Boolean>()
        val replyStarted = CompletableDeferred<Unit>()
        val replyResponse = CompletableDeferred<String?>()
        handleThreadScreenReplySubmit(
            ThreadScreenReplySubmitDependencies(
                replyDialogBinding = ThreadReplyDialogStateBinding(
                    currentState = { dialogState },
                    setState = { dialogState = it }
                ),
                effectiveBoardUrl = "https://may.2chan.net/b/",
                threadId = "123",
                boardId = "may-b",
                coroutineScope = this,
                stateStore = null,
                updateLastUsedDeleteKey = {},
                actionBindings = ThreadScreenActionBindings(
                    coroutineScope = this,
                    stateBindings = ThreadScreenActionStateBindings(
                        currentActionInProgress = { actionInProgress },
                        setActionInProgress = { actionInProgress = it },
                        currentLastBusyNoticeAtMillis = { busyNoticeAt },
                        setLastBusyNoticeAtMillis = { busyNoticeAt = it }
                    ),
                    dependencies = ThreadScreenActionDependencies(
                        currentTimeMillis = { 0L },
                        busyNoticeIntervalMillis = 1_000L,
                        showMessage = {},
                        onDebugLog = {},
                        onInfoLog = {},
                        onErrorLog = { _, _ -> }
                    )
                ),
                threadReplyActionCallbacks = ThreadReplyActionCallbacks(
                    replyToThread = {
                        replyStarted.complete(Unit)
                        replyResponse.await()
                    }
                ),
                refreshThread = {},
                snackbarHostState = SnackbarHostState(),
                onOpenCookieManager = null,
                showMessage = {},
                onSendingChanged = { sendingChanges += it }
            )
        )
        replyStarted.await()
        yield()
        assertEquals(listOf(true), sendingChanges)
        assertFalse(dialogState.isVisible)

        replyResponse.complete("456")
        while (sendingChanges.size < 2) yield()
        assertEquals(listOf(true, false), sendingChanges)
    }
}
