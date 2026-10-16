package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 再監査M3：スレ画面はスレッドごとに作り直すので、そうだね・del・削除・過去ログ取得は画面より長く続ける受け皿で送る。
 * 以前は画面のスコープで送っていたので、タブを切り替えると取り消されて結果が出なかった。
 */
class FtutterThreadActionsTest {
    @Test
    fun aSendGoesOnAfterTheScreenThatStartedItIsGone() = runBlocking<Unit> {
        val appJob = Job(coroutineContext.job)
        val actions = FtutterThreadActions(CoroutineScope(coroutineContext + appJob))
        val gate = CompletableDeferred<Unit>()
        var done = false
        val screen = launch {
            actions.run("b/1:remote") { gate.await(); done = true }
            awaitCancellation()
        }
        yield()
        // タブを切り替えた：送った画面が破棄される。
        screen.cancelAndJoin()
        assertTrue(actions.isBusy("b/1:remote"))
        // 送信中は同じスレッドで重ねて送らない（戻ってきた画面から押しても）。
        actions.run("b/1:remote") { error("sent twice") }
        gate.complete(Unit)
        appJob.children.toList().joinAll()
        assertTrue(done)
        assertFalse(actions.isBusy("b/1:remote"))
        appJob.cancel()
    }

    @Test
    fun anArchiveFetchedAfterLeavingIsKeptForTheReopenedScreenWithinALimit() {
        val actions = FtutterThreadActions(CoroutineScope(Job()))
        fun page(id: String) = ThreadPage(id, null, null, null, emptyList())
        (1..9).forEach { actions.putArchived("b/$it", page("$it")) }
        assertNull(actions.archived("b/1"))
        assertEquals("9", actions.archived("b/9")?.threadId)
        assertEquals("2", actions.archived("b/2")?.threadId)
    }
}
