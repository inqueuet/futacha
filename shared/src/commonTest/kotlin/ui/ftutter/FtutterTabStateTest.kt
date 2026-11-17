package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 監査M1・再監査L25、計画B7a：書き込みは非同期で、ストアの通知は遅れて・途中の値で届く。書き込みが終わるまでは手元の
 * タブを巻き戻さず、全部終わってから最新の通知を出す。
 */
class FtutterTabStateTest {
    private fun url(n: Int) = "https://may.2chan.net/27/res/$n.htm"

    private class RecordingWriter : FtutterTabWriter {
        val calls = mutableListOf<String>()
        override suspend fun open(url: String, seed: FtutterTabSeed?) { calls += "open $url" }
        override suspend fun move(shown: List<String>) { calls += "move $shown" }
        override suspend fun close(urls: List<String>) { calls += "close $urls" }
        override suspend fun protect(url: String, on: Boolean): Boolean { calls += "protect $url $on"; return true }
        override suspend fun restore(): FtutterRestoredTab? { calls += "restore"; return null }
    }

    private class Harness(shared: List<String>, protected: Set<String> = emptySet()) {
        val writer = RecordingWriter()
        val queued = ArrayDeque<suspend () -> Unit>()
        val state = FtutterTabState(FtutterTabSnapshot(shared, emptyList(), protected), "", writer) { queued += it }
        suspend fun runAll() { while (queued.isNotEmpty()) queued.removeFirst().invoke() }
    }

    private fun snapshot(vararg urls: String, protected: Set<String> = emptySet()) = FtutterTabSnapshot(urls.toList(), emptyList(), protected)

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }

    @Test
    fun twoQuickClosesAreNotRewoundByTheFirstEcho() = runTest {
        val h = Harness(listOf(url(1), url(2), url(3)))
        h.state.close(listOf(url(1)))
        h.state.close(listOf(url(2)))
        h.state.sync(snapshot(url(2), url(3)))
        assertEquals(listOf(url(3)), h.state.urls, "the first close's snapshot does not bring url(2) back")
        h.runAll()
        assertEquals(1, h.state.settledWrites)
        h.state.sync(snapshot(url(3)))
        assertEquals(listOf(url(3)), h.state.urls)
        assertEquals(listOf("close [${url(1)}]", "close [${url(2)}]"), h.writer.calls)
    }

    @Test
    fun theNewestSnapshotIsShownOnceTheWritesAreDone() = runTest {
        val h = Harness(listOf(url(1)))
        h.state.open(url(2))
        assertEquals(listOf(url(2), url(1)), h.state.urls, "shown at once")
        h.state.sync(snapshot(url(1)))
        assertEquals(listOf(url(2), url(1)), h.state.urls)
        h.runAll()
        h.state.sync(snapshot(url(2), url(1), url(9)))
        assertEquals(listOf(url(2), url(1), url(9)), h.state.urls, "another mode's tab arrives")
        assertEquals(url(2), h.state.opening)
    }

    @Test
    fun aProtectionEchoDoesNotRewindTheTabs() = runTest {
        val h = Harness(listOf(url(1), url(2)))
        h.state.toggleProtect(url(2))
        h.state.close(listOf(url(1)))
        h.state.sync(snapshot(url(1), url(2), protected = setOf(url(2))))
        assertEquals(listOf(url(2)), h.state.urls)
        assertTrue(url(2) in h.state.protected)
        h.runAll()
        h.state.sync(snapshot(url(2), protected = setOf(url(2))))
        h.state.showLatest()
        assertEquals(listOf(url(2)), h.state.urls)
    }

    @Test
    fun theHistoryLimitOnlyHidesAndWritesNothing() = runTest {
        val h = Harness(listOf(url(1), url(2), url(3)), protected = setOf(url(3)))
        h.state.historyOrder = listOf(url(1), url(2), url(3))
        h.state.limit = 1
        assertEquals(listOf(url(1), url(3)), h.state.urls)
        assertEquals(0, h.queued.size, "the limit closes nothing")
        h.state.limit = 200
        assertEquals(listOf(url(1), url(2), url(3)), h.state.urls, "raising it shows them again")
    }

    @Test
    fun aMoveWritesEveryTabShown() = runTest {
        val writer = RecordingWriter()
        val queued = ArrayDeque<suspend () -> Unit>()
        val state = FtutterTabState(FtutterTabSnapshot(listOf(url(1), url(2)), listOf(url(7)), emptySet()), "", writer) { queued += it }
        state.move(2, 0)
        while (queued.isNotEmpty()) queued.removeFirst().invoke()
        assertEquals(listOf(url(7), url(1), url(2)), state.urls)
        assertEquals(listOf("move [${url(7)}, ${url(1)}, ${url(2)}]"), writer.calls, "the writer splits marks and the old row")
    }
}
