package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okio.IOException
import kotlin.test.*

/** L131: 「タブを全て更新」 applies its results in batches, shows progress, and keeps what it got when cancelled. */
class FtutterTabRefreshTest {
    private fun entry(id: String) = ThreadHistoryEntry(id, "b", "題$id", "", "板", "https://may.2chan.net/b/res/$id.htm",
        lastVisitedEpochMillis = 1, replyCount = 1)
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文$id", imageUrl = null, thumbnailUrl = null)
    private fun target(id: String) = FtutterTabRefreshTarget("https://may.2chan.net/b/res/$id.htm", "b", id)

    @Test fun oneScanAppliesEveryTabAndLeavesOtherRowsAsTheyWere() {
        val history = listOf(entry("1"), entry("2"), entry("3"), entry("4"))
        val result = ftutterHistoryAfterTabRefresh(history, listOf(
            FtutterTabRefreshResult.Loaded("b", "1", listOf(post("1"), post("11"), post("12"))),
            FtutterTabRefreshResult.Fallen("b", "3")
        ), now = 50)
        assertEquals(3, result[0].replyCount)
        assertEquals(50, result[0].lastConfirmedAliveEpochMillis)
        assertSame(history[1], result[1])
        assertTrue(result[2].isAutoRefreshDisabled && result[2].threadStatus?.isDead == true)
        assertSame(history[3], result[3])
    }

    @Test fun resultsAreAppliedInBatchesNotPerTab() = runBlocking {
        val batches = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val failed = ftutterRefreshTabs((1..12).map { target("$it") },
            fetch = { if (it.threadId == "5") throw IOException("x") else listOf(post(it.threadId)) },
            isGone = { false }, apply = { batches += it.size }, onProgress = { d, t -> progress += d to t })
        assertEquals(1, failed)
        assertEquals(listOf(10, 1), batches)
        assertEquals(0 to 12, progress.first())
        assertEquals(12 to 12, progress.last())
        val small = mutableListOf<Int>()
        ftutterRefreshTabs((1..5).map { target("$it") }, fetch = { listOf(post(it.threadId)) }, isGone = { false }, apply = { small += it.size })
        assertEquals(listOf(5), small, "Five tabs are one history write")
    }

    @Test fun aGoneThreadIsMarkedFallenAndCancellingKeepsWhatWasFetched() = runBlocking {
        val applied = mutableListOf<FtutterTabRefreshResult>()
        val third = CompletableDeferred<Unit>()
        val run = async(start = CoroutineStart.UNDISPATCHED) {
            ftutterRefreshTabs((1..5).map { target("$it") },
                fetch = { if (it.threadId == "3") { third.complete(Unit); awaitCancellation() }
                    else if (it.threadId == "2") throw IOException("404") else listOf(post(it.threadId)) },
                isGone = { true }, apply = { applied += it })
        }
        third.await(); yield()
        run.cancel()
        assertFailsWith<CancellationException> { run.await() }
        assertEquals(listOf<FtutterTabRefreshResult>(
            FtutterTabRefreshResult.Loaded("b", "1", listOf(post("1"))), FtutterTabRefreshResult.Fallen("b", "2")), applied)
    }
}
