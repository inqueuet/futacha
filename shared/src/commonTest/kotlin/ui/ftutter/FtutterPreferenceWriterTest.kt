package com.valoser.futacha.shared.ui.ftutter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** 監査M2：続けて押した変更が、遅れて届く古い値で上書きされない。 */
class FtutterPreferenceWriterTest {
    private class SlowStore {
        val values = mutableMapOf<String, String>()
        suspend fun load(key: String): String? { delay(10); return values[key] }
        suspend fun save(key: String, value: String) { delay(30); values[key] = value }
    }

    /** 書き込みが全部終わるまで待つ。 */
    private fun SlowStore.writing(block: CoroutineScope.(FtutterPreferenceWriter) -> Unit) = runBlocking {
        coroutineScope { block(FtutterPreferenceWriter(this, ::load, ::save) { _, e -> throw e }) }
    }

    @Test
    fun twoQuickUnhidesBothCount() {
        val store = SlowStore()
        store.values[FtutterKeys.HIDDEN_THREADS] = encodeFtutterList(listOf("a", "b", "c"))
        store.writing { writer ->
            writer.edit(FtutterKeys.HIDDEN_THREADS) { encodeFtutterList(parseFtutterHiddenThreads(it) - "a") }
            writer.edit(FtutterKeys.HIDDEN_THREADS) { encodeFtutterList(parseFtutterHiddenThreads(it) - "b") }
        }
        assertEquals(listOf("c"), parseFtutterHiddenThreads(store.values[FtutterKeys.HIDDEN_THREADS]))
    }

    @Test
    fun countsAddUpAndAnEditSeesAnEarlierSave() {
        val store = SlowStore()
        store.writing { writer ->
            writer.save(FtutterKeys.STATS_OPENED, "5")
            repeat(3) { writer.edit(FtutterKeys.STATS_OPENED, ::ftutterIncrementedCount) }
        }
        assertEquals("8", store.values[FtutterKeys.STATS_OPENED])
    }

    /** 再監査M5：モードを切り替えて書き込み口のスコープが取り消されても、離れる時に頼んだ書き込みは最後まで行う。 */
    @Test
    fun aWriteAskedJustBeforeTheModeClosesStillLands() = runBlocking<Unit> {
        val store = SlowStore()
        store.values[FtutterKeys.STATS_OPENED] = "1"
        val host = kotlinx.coroutines.Job()
        val writer = FtutterPreferenceWriter(CoroutineScope(host), store::load, store::save) { _, e -> throw e }
        writer.save(FtutterKeys.READ_COUNTS, "read")
        writer.edit(FtutterKeys.STATS_OPENED, ::ftutterIncrementedCount)
        host.cancel()
        host.join()
        assertEquals("read", store.values[FtutterKeys.READ_COUNTS])
        assertEquals("2", store.values[FtutterKeys.STATS_OPENED])
    }

    @Test
    fun aNullResultWritesNothing() {
        val store = SlowStore()
        store.writing { writer -> writer.edit("k") { null } }
        assertEquals(null, store.values["k"])
    }

    @Test
    fun aBrokenCountStartsAgainFromZero() {
        assertEquals("1", ftutterIncrementedCount(null))
        assertEquals("1", ftutterIncrementedCount("x"))
        assertEquals("1", ftutterIncrementedCount("-3"))
        assertEquals(Int.MAX_VALUE.toString(), ftutterIncrementedCount(Int.MAX_VALUE.toString()))
    }

    @Test
    fun ngEditsApplyToTheStoredList() {
        val a = FtutterNgRule("a", FtutterNgType.WORD.stored)
        val b = FtutterNgRule("b", FtutterNgType.WORD.stored)
        val c = FtutterNgRule("c", FtutterNgType.WORD.stored)
        val x = a.copy(text = "x")
        // 画面に出ている一覧は [a, b] のままでも、保存は [a, b, c] になっている。
        assertEquals(listOf(b, c), ftutterNgWithout(listOf(a, b, c), a))
        assertEquals(listOf(x, c, a), ftutterNgReplaced(listOf(a, c, a), a, x))
        // 他で消されたルールの削除・編集は、何も変えない。
        assertEquals(listOf(c), ftutterNgWithout(listOf(c), a))
        assertEquals(listOf(c), ftutterNgReplaced(listOf(c), a, x))
    }

    /** Re-audit L28: an import waits its turn and merges the read counts with one written just before it. */
    @Test
    fun anImportMergesTheReadCountsWrittenJustBefore() = runBlocking<Unit> {
        val store = SlowStore()
        val a = "https://may.2chan.net/b/res/1.htm"
        val b = "https://may.2chan.net/b/res/2.htm"
        val writer = FtutterPreferenceWriter(CoroutineScope(coroutineContext), store::load, store::save) { _, e -> throw e }
        // A read count is being written when the import (planned before it) comes.
        writer.save(FtutterKeys.READ_COUNTS, encodeFtutterReadCounts(mapOf(b to 9)))
        val planned = encodeFtutterReadCounts(mapOf(a to 3))
        writer.editAllNow(mapOf(FtutterKeys.READ_COUNTS to { stored: String? -> ftutterMergeImportedReadCounts(planned, stored, setOf(a)) })) { values ->
            values.forEach { (k, v) -> store.save(k, v) }
        }
        assertEquals(mapOf(a to 3, b to 9), parseFtutterReadCounts(store.values[FtutterKeys.READ_COUNTS]))
    }
}
