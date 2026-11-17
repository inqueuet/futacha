package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.compat.aimoge.withAimoge
import com.valoser.futacha.shared.compat.aimogeWatchThreadUrlOf
import com.valoser.futacha.shared.compat.watchMatchBoardUrlOf
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadStatus
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.SiteThreadState
import com.valoser.futacha.shared.repo.SiteThreadSummary
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BOARD = "https://nijiurachan.net/b/aimoge/"
private fun uuid(i: Int) = "0193b2c4-1a2b-7c3d-8e4f-" + i.toString().padStart(12, '0')

private class CountingRepository(
    val answer: (String, List<String>) -> List<SiteThreadSummary>
) : BoardRepository by FakeBoardRepository() {
    val calls = mutableListOf<List<String>>()
    override suspend fun getThreadSummaries(board: String, threadIds: List<String>): List<SiteThreadSummary>? {
        calls += threadIds
        return answer(board, threadIds)
    }
}

private fun entry(id: String, count: Int = 5, dead: Boolean = false, disabled: Boolean = false) = ThreadHistoryEntry(
    threadId = id, title = "t", titleImageUrl = "", boardName = "あいもげ", boardUrl = BOARD,
    lastVisitedEpochMillis = 0L, replyCount = count, isAutoRefreshDisabled = disabled,
    threadStatus = if (dead) ThreadStatus(isDead = true) else null
)

class SiteRefreshRouterTest {
    private var now = 1_000_000L

    @BeforeTest fun setUp() { SiteRefreshPause.reset(); SiteRefreshPause.nowMillis = { now } }
    @AfterTest fun tearDown() = SiteRefreshPause.reset()

    private fun targets(n: Int) = (1..n).map { SiteRefreshTarget(BOARD, uuid(it)) }

    @Test
    fun twentyThreadsGoInOneRequestAndTheCapIsKept() = runTest {
        val repo = CountingRepository { _, ids -> ids.map { SiteThreadSummary(it, SiteThreadState.ALIVE, 3) } }
        val outcome = SiteRefreshRouter(repo, maxRequests = 2, requestTimeoutMillis = 5_000).run(targets(50)) {}
        assertEquals(listOf(20, 20), repo.calls.map { it.size })
        assertEquals(2, outcome.requests)
        assertEquals(10, outcome.notVisited.size)
    }

    @Test
    fun noRequestStartsAfterTheDeadlineExceptTheFirst() = runTest {
        val repo = CountingRepository { _, ids -> ids.map { SiteThreadSummary(it, SiteThreadState.ALIVE) } }
        SiteRefreshRouter(repo, 10, 5_000, fetchStartDeadline = now - 1, nowMillis = { now }).run(targets(60)) {}
        assertEquals(1, repo.calls.size)
    }

    @Test
    fun a429PausesTheSiteDoublingUpToAnHourAndNothingIsSentWhilePaused() = runTest {
        val repo = CountingRepository { _, _ -> throw NetworkException("busy", 429) }
        SiteRefreshRouter(repo, 10, 5_000).run(targets(60)) {}
        assertEquals(1, repo.calls.size)
        assertEquals(SITE_PAUSE_429_MILLIS, SiteRefreshPause.snapshot().lengthMillis)
        SiteRefreshRouter(repo, 10, 5_000).run(targets(60)) {}
        assertEquals(1, repo.calls.size, "nothing while paused")
        var length = SITE_PAUSE_429_MILLIS
        repeat(4) {
            now = SiteRefreshPause.snapshot().untilMillis + 1
            SiteRefreshRouter(repo, 10, 5_000).run(targets(1)) {}
            length = (length * 2).coerceAtMost(SITE_PAUSE_MAX_MILLIS)
            assertEquals(length, SiteRefreshPause.snapshot().lengthMillis)
        }
        assertEquals(SITE_PAUSE_MAX_MILLIS, SiteRefreshPause.snapshot().lengthMillis)
    }

    @Test
    fun aBoard404PausesTheBoardForADayWithoutMakingThreadsFallen() = runTest {
        val repo = CountingRepository { _, _ -> throw NetworkException("gone", 404) }
        val batches = mutableListOf<List<SiteThreadSummary>>()
        SiteRefreshRouter(repo, 10, 5_000).run(targets(3)) { batches += it }
        assertTrue(batches.isEmpty())
        assertTrue(SiteRefreshPause.boardPausedNow(BOARD))
        SiteRefreshRouter(repo, 10, 5_000).run(targets(3)) {}
        assertEquals(1, repo.calls.size)
        now += SITE_BOARD_PAUSE_MILLIS + 1
        SiteRefreshRouter(repo, 10, 5_000).run(targets(3)) {}
        assertEquals(2, repo.calls.size)
    }

    @Test
    fun a422StopsTheRunAndIsNotResent() = runTest {
        val repo = CountingRepository { _, _ -> throw NetworkException("token", 422) }
        val outcome = SiteRefreshRouter(repo, 10, 5_000).run(targets(60)) {}
        assertEquals(1, repo.calls.size)
        assertEquals(60, outcome.notVisited.size)
    }

    @Test
    fun theHistoryRowOnlyGrowsAndFallsOnGone() {
        val e = entry(uuid(1), count = 10)
        assertNull(aimogeHistoryUpdate(e, SiteThreadSummary(e.threadId, SiteThreadState.ALIVE, 8), now))
        assertEquals(12, aimogeHistoryUpdate(e, SiteThreadSummary(e.threadId, SiteThreadState.ALIVE, 12), now)?.replyCount)
        assertNull(aimogeHistoryUpdate(e, SiteThreadSummary(e.threadId, SiteThreadState.UNKNOWN), now))
        val gone = aimogeHistoryUpdate(e, SiteThreadSummary(e.threadId, SiteThreadState.GONE), now)!!
        assertTrue(gone.isAutoRefreshDisabled && gone.threadStatus?.isDead == true)
        assertEquals(10, gone.replyCount)
    }

    @Test
    fun fallenAndStoppedRowsAndFutabaRowsAreNotChecked() = withAimoge {
        val futaba = entry("123").copy(boardUrl = "https://may.2chan.net/b/futaba.php")
        val rows = listOf(entry(uuid(1)), entry(uuid(2), dead = true), entry(uuid(3), disabled = true), futaba)
        assertEquals(listOf(uuid(1)), aimogeHistoryTargets(rows).map { it.second.threadId })
    }

    @Test
    fun theCursorTurnsTheListToWhereTheLastRunStopped() {
        assertEquals(listOf(3, 4, 1, 2), rotateFrom(listOf(1, 2, 3, 4), "3") { it.toString() })
        assertEquals(listOf(1, 2), rotateFrom(listOf(1, 2), "9") { it.toString() })
    }

    @Test
    fun notificationsOpenTheAimogeThreadAndFutabaStaysAsBefore() = withAimoge {
        val thread = "https://nijiurachan.net/b/aimoge/thread/${uuid(1)}"
        assertEquals(BOARD, watchMatchBoardUrlOf(thread))
        assertEquals(thread, aimogeWatchThreadUrlOf(BOARD, uuid(1)))
        assertEquals("https://may.2chan.net/b", watchMatchBoardUrlOf("https://may.2chan.net/b/res/123.htm"))
        assertNull(aimogeWatchThreadUrlOf("https://may.2chan.net/b/", "123"))
    }
}
