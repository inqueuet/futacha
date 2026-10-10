package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberReadAloudTest {
    @Test fun liveReadingWaitsEvenWhenThereIsNoReadableInitialBody() = runBlocking {
        val engine = FakeEngine()
        val initial = listOf(post("1", "&gt;引用だけ"))
        var polls = 0
        val reader = FutaberReadAloud(this, { engine }, livePollMillis = 1)
        reader.start(initial, 0, loadNewPosts = {
            if (++polls == 1) initial + post("2", "新着本文") else null
        }) {}
        reader.awaitIdle()
        assertEquals(listOf("新着本文"), engine.spoken)
        assertEquals(2, polls)
        assertFalse(reader.isReading)
    }

    @Test fun liveReadingReadsOnlyNewPostNumbersAndStopsWhenThreadIsGone() = runBlocking {
        val engine = FakeEngine()
        var polls = 0
        val reader = FutaberReadAloud(this, { engine }, livePollMillis = 1)
        reader.start(posts, 0, loadNewPosts = {
            polls++
            if (polls <= 2) posts + listOf(post("6", "六番目"), post("6", "六番目")) else null
        }) {}
        reader.awaitIdle()
        assertEquals(listOf("一番目", "三番目", "五番目", "六番目"), engine.spoken)
        assertEquals(3, polls)
        assertEquals("スレッドが落ちています", reader.message)
        assertFalse(reader.isReading)
    }

    @Test fun stoppingWhileWaitingCancelsTheRequestAndKeepsItFromSpeakingLater() = runBlocking {
        val engine = FakeEngine()
        val entered = CompletableDeferred<Unit>()
        val reader = FutaberReadAloud(this, { engine }, livePollMillis = 1)
        reader.start(posts, 0, loadNewPosts = { entered.complete(Unit); kotlinx.coroutines.awaitCancellation() }) {}
        entered.await()
        reader.stop()
        reader.awaitIdle()
        assertEquals(listOf("一番目", "三番目", "五番目"), engine.spoken)
        assertFalse(reader.isReading)
        reader.dispose()
    }

    private class FakeEngine(val failOn: String? = null, val blockOn: String? = null) : FutaberSpeechEngine {
        val spoken = mutableListOf<String>()
        var prepared = 0
        var stops = 0
        var closed = false
        val gate = CompletableDeferred<Unit>()
        override suspend fun prepare() { prepared += 1 }
        override suspend fun speak(text: String) {
            if (text == failOn) throw IllegalStateException("音声エンジンが応答しません")
            spoken += text
            if (text == blockOn) gate.await()
        }
        override fun stop() { stops += 1 }
        override fun close() { closed = true }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) yield()
    }

    private fun post(id: String, html: String, deleted: Boolean = false) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = html,
        imageUrl = null, thumbnailUrl = null, isDeleted = deleted
    )

    private val posts = listOf(
        post("1", "一番目"),
        post("2", ">引用だけ"),
        post("3", "三番目<br>http://example.com/x"),
        post("4", "削除されたもの", deleted = true),
        post("5", "五番目")
    )

    @Test
    fun readsFromTheFirstVisibleRowSkippingQuotesUrlsAndDeletedPosts() = runBlocking {
        val engine = FakeEngine()
        val scrolled = mutableListOf<Int>()
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 0) { scrolled += it }
        reader.awaitIdle()
        assertEquals(listOf("一番目", "三番目", "五番目"), engine.spoken)
        assertEquals(listOf(0, 2, 4), scrolled)
        assertEquals("読み上げを完了しました", reader.message)
        assertFalse(reader.isReading)
        assertNull(reader.currentPostId)
        assertEquals(1, engine.prepared)
    }

    @Test
    fun startingMidThreadSkipsWhatIsAbove() = runBlocking {
        val engine = FakeEngine()
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 2) {}
        reader.awaitIdle()
        assertEquals(listOf("三番目", "五番目"), engine.spoken)
    }

    @Test
    fun nothingLeftToReadIsReportedInsteadOfStartingSilently() = runBlocking {
        val engine = FakeEngine()
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 99) {}
        reader.awaitIdle()
        assertTrue(engine.spoken.isEmpty())
        assertEquals(FUTABER_NOTHING_TO_READ_MESSAGE, reader.message)
        assertFalse(reader.isReading)
        reader.start(listOf(post("9", ">だけ")), 0) {}
        reader.awaitIdle()
        assertEquals(FUTABER_NOTHING_TO_READ_MESSAGE, reader.message)
    }

    @Test
    fun stopCancelsTheSessionAndTheEngineAndReportsNoFailure() = runBlocking {
        val engine = FakeEngine(blockOn = "一番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, 0) {}
        awaitCondition { reader.currentPostId == "1" && engine.spoken.isNotEmpty() }
        assertTrue(reader.isReading)
        assertEquals("1", reader.currentPostId)
        reader.stop()
        reader.awaitIdle()
        assertFalse(reader.isReading)
        assertNull(reader.currentPostId)
        assertEquals(listOf("一番目"), engine.spoken)
        assertEquals(1, engine.stops)
        assertNull(reader.message)
    }

    @Test
    fun anEngineFailureEndsTheSessionWithItsReason() = runBlocking {
        val engine = FakeEngine(failOn = "三番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, 0) {}
        reader.awaitIdle()
        assertEquals("音声エンジンが応答しません", reader.message)
        assertFalse(reader.isReading)
        assertEquals(listOf("一番目"), engine.spoken)
    }

    @Test
    fun aSecondStartWhileReadingIsIgnoredAndDisposeReleasesTheEngine() = runBlocking {
        val engine = FakeEngine(blockOn = "一番目")
        var created = 0
        val reader = FutaberReadAloud(this, { created += 1; engine })
        reader.start(posts, 0) {}
        awaitCondition { engine.spoken.isNotEmpty() }
        reader.start(posts, 0) {}
        assertEquals(1, created)
        assertEquals(1, engine.prepared)
        reader.dispose()
        reader.awaitIdle()
        assertTrue(engine.closed)
        assertFalse(reader.isReading)
    }

    @Test
    fun skippingForwardCutsTheCurrentPostAndCarriesOnFromTheNextReadablePost() = runBlocking {
        val engine = FakeEngine(blockOn = "一番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 0) {}
        awaitCondition { engine.spoken.contains("一番目") }
        reader.skip(1)
        reader.awaitIdle()
        // 一番目 was cut off; the quote-only and deleted posts are not readable, so the next is 三番目.
        assertEquals(listOf("一番目", "三番目", "五番目"), engine.spoken)
        assertEquals(1, engine.stops)
        assertFalse(reader.isReading)
        assertNull(reader.currentPostId)
        assertEquals("読み上げを完了しました", reader.message)
    }

    @Test
    fun skippingBackReadsThePreviousPostAgainAndThenTheRest() = runBlocking {
        val engine = FakeEngine(blockOn = "三番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 2) {}
        awaitCondition { engine.spoken.contains("三番目") }
        reader.skip(-1)
        engine.gate.complete(Unit)
        reader.awaitIdle()
        assertEquals(listOf("三番目", "一番目", "三番目", "五番目"), engine.spoken)
    }

    @Test
    fun skippingPastTheEndStopsAtTheLastPostAndSkippingWhileIdleDoesNothing() = runBlocking {
        val engine = FakeEngine(blockOn = "一番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.skip(1) // idle: nothing happens
        assertFalse(reader.isReading)
        assertTrue(engine.spoken.isEmpty())
        reader.start(posts, firstVisibleRow = 0) {}
        awaitCondition { engine.spoken.contains("一番目") }
        reader.skip(99)
        reader.awaitIdle()
        assertEquals(listOf("一番目", "五番目"), engine.spoken)
    }

    @Test
    fun theReplacedRunDoesNotClearTheRunThatReplacedIt() = runBlocking {
        val engine = FakeEngine(blockOn = "三番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 0) {}
        awaitCondition { engine.spoken.contains("三番目") }
        reader.skip(-1)
        // While the new run is reading, the state belongs to it.
        awaitCondition { engine.spoken.count { it == "一番目" } == 2 }
        assertTrue(reader.isReading)
        engine.gate.complete(Unit)
        reader.awaitIdle()
        assertFalse(reader.isReading)
    }

    @Test
    fun stoppingRightAfterASkipLeavesNothingReading() = runBlocking {
        val engine = FakeEngine(blockOn = "三番目")
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 2) {}
        awaitCondition { engine.spoken.contains("三番目") }
        reader.skip(-1)
        reader.stop()
        reader.awaitIdle()
        assertFalse(reader.isReading)
        assertNull(reader.currentPostId)
    }

    @Test
    fun stoppingRightAfterTheStartLeavesNothingReading() = runBlocking {
        val engine = FakeEngine()
        val reader = FutaberReadAloud(this, { engine })
        reader.start(posts, firstVisibleRow = 0) {}
        reader.stop()
        reader.awaitIdle()
        assertFalse(reader.isReading)
        assertNull(reader.currentPostId)
        // The engine was never even created for a run that was stopped before it began.
        assertEquals(0, engine.prepared)
    }
}
