package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals

/** may 1449174385: a fallen thread's subtitle says 落ち instead of the stale "〜頃消えます". */
class ThreadFallenStatusLabelTest {
    private fun page(count: Int) = ThreadPage(
        threadId = "1", boardTitle = "b", expiresAtLabel = "19:54頃消えます", deletedNotice = null,
        posts = (1..count).map { Post(id = "$it", author = null, subject = null, timestamp = "t", messageHtml = "m", imageUrl = null, thumbnailUrl = null) }
    )

    private fun label(state: ThreadUiState, initial: Int? = null, offline: Boolean = false) = buildThreadScreenDerivedUiState(
        currentState = state, initialReplyCount = initial, threadTitle = null,
        isReadAloudControlsVisible = false, readAloudStatus = ReadAloudStatus.Idle, isOfflineCopy = offline
    ).statusLabel

    @Test
    fun aLiveThreadKeepsItsExpiry() {
        assertEquals("3レス / 19:54頃消えます", label(ThreadUiState.Success(page(3))))
    }

    @Test
    fun fallenComesFirstSoTheCompactHeaderDoesNotCutItOff() {
        // The compact header is one line ("may / …") and cuts the end; 落ち must not be the end.
        val label = label(ThreadUiState.Success(page(1000), isArchived = true))!!
        kotlin.test.assertTrue(label.startsWith("落ち"), label)
    }

    @Test
    fun anArchivedCopySaysFallenFromTheArchive() {
        assertEquals("落ち（過去ログ） / 26レス", label(ThreadUiState.Success(page(26), isArchived = true)))
    }

    @Test
    fun aLocalCopyOfAGoneThreadSaysFallen() {
        assertEquals("落ち / 4レス", label(ThreadUiState.Success(page(4), confirmedGone = true), offline = true))
        assertEquals("落ち / 4レス", label(ThreadUiState.Success(page(4), isArchived = true), offline = true))
    }

    @Test
    fun a404WithNothingShownStillTellsTheKnownCount() {
        assertEquals("落ち / 26レス", label(ThreadUiState.Error(confirmedGone = true), initial = 26))
        assertEquals("落ち", label(ThreadUiState.Error(confirmedGone = true)))
        // A network error is not a fallen thread.
        assertEquals("26レス", label(ThreadUiState.Error(), initial = 26))
    }
}
