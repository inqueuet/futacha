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

    /** L16：送信中に押された2回目は送らずに false を返す（呼び出し側が知らせを出す）。 */
    @Test
    fun aSecondSendWhileOneIsRunningIsRefusedVisibly() = runBlocking<Unit> {
        val appJob = Job(coroutineContext.job)
        val actions = FtutterThreadActions(CoroutineScope(coroutineContext + appJob))
        val gate = CompletableDeferred<Unit>()
        assertTrue(actions.run("b/1:remote") { gate.await() })
        var ran = false
        assertFalse(actions.run("b/1:remote") { ran = true })
        gate.complete(Unit)
        appJob.children.toList().joinAll()
        assertFalse(ran)
        assertTrue(actions.run("b/1:remote") {}, "After the first send ends, the next one starts")
        appJob.children.toList().joinAll()
        appJob.cancel()
    }

    /** L133：持ち越しが溢れた時は、最も前に取った過去ログを捨てる（状態の Map の並びは入れた順ではない）。 */
    @Test
    fun theOldestHeldArchiveIsDroppedWhateverTheMapOrder() {
        fun page(id: String) = ThreadPage(id, null, null, null, emptyList())
        val candidates = listOf("may/b/1442001717", "img/b/1474754356", "may/b/1439396540", "img/b/1474732998",
            "may/b/1439384684", "img/b/1474737500", "may/b/1440876980", "img/b/1474738814")
        // 修正前の捨て方（Map の先頭）が、最初に入れた1件と違う並びにする。
        val hashFirst = androidx.compose.runtime.mutableStateMapOf<String, Int>().apply { candidates.forEach { put(it, 0) } }.keys.first()
        val inserted = candidates.filter { it != hashFirst } + hashFirst
        assertTrue(inserted.first() != hashFirst)
        val actions = FtutterThreadActions(CoroutineScope(Job()))
        inserted.forEach { actions.putArchived(it, page(it)) }
        actions.putArchived("may/b/1443000000", page("new"))
        assertNull(actions.archived(inserted.first()), "The first archive taken is the one to drop")
        inserted.drop(1).forEach { assertEquals(it, actions.archived(it)?.threadId, it) }
        assertEquals("new", actions.archived("may/b/1443000000")?.threadId)
        // 取り直した過去ログは新しい側へ移る。
        actions.putArchived(inserted[1], page(inserted[1]))
        actions.putArchived("may/b/1443000001", page("newer"))
        assertEquals(inserted[1], actions.archived(inserted[1])?.threadId)
        assertNull(actions.archived(inserted[2]))
    }
}
