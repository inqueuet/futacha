package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** 監査M1：保存は非同期で、保存値の通知が遅れて届く。途中の値で手元のタブを巻き戻さない。 */
class FtutterTabStateTest {
    private fun url(n: Int) = "https://may.2chan.net/27/res/$n.htm"

    /** 保存を貯めておき、あとから1件ずつストアへ反映して通知する（実際の`scope.launch`での保存と同じ順）。 */
    private class LaggingStore {
        val stored = mutableMapOf<String, String>()
        val queued = ArrayDeque<Pair<String, String>>()
        fun save(key: String, value: String) { queued += key to value }
        fun landNext(): Map<String, String> {
            val (k, v) = queued.removeFirst()
            stored[k] = v
            return stored.toMap()
        }
    }

    @Test
    fun twoQuickClosesAreNotRewoundByTheFirstEcho() {
        val store = LaggingStore()
        val initial = mapOf(FtutterKeys.TABS to encodeFtutterList(listOf(url(1), url(2), url(3))))
        store.stored.putAll(initial)
        val state = FtutterTabState(parseFtutterTabs(initial), emptySet(), store::save)
        state.close(listOf(url(1)))
        state.close(listOf(url(2)))
        assertEquals(listOf(url(3)), state.urls)
        // 1回目の保存だけが届いた通知：途中の値なので取り込まない。
        state.sync(store.landNext())
        assertEquals(listOf(url(3)), state.urls)
        state.sync(store.landNext())
        assertEquals(listOf(url(3)), state.urls)
    }

    @Test
    fun protectingTwiceIsNotRewoundByTheFirstEcho() {
        val store = LaggingStore()
        val state = FtutterTabState(FtutterTabs(listOf(url(1), url(2)), url(1)), emptySet(), store::save)
        state.toggleProtect(url(1))
        state.toggleProtect(url(2))
        state.sync(store.landNext())
        assertEquals(setOf(url(1), url(2)), state.protected)
        state.sync(store.landNext())
        assertEquals(setOf(url(1), url(2)), state.protected)
    }

    @Test
    fun aValueWrittenElsewhereIsStillTakenIn() {
        val store = LaggingStore()
        val state = FtutterTabState(FtutterTabs(listOf(url(1)), url(1)), emptySet(), store::save)
        state.close(listOf(url(1)))
        state.sync(store.landNext())
        // バックアップの取り込みなど、自分が書いていない値。
        state.sync(mapOf(FtutterKeys.TABS to encodeFtutterList(listOf(url(7), url(8)))))
        assertEquals(listOf(url(7), url(8)), state.urls)
        state.sync(mapOf(FtutterKeys.PROTECTED to encodeFtutterList(listOf(url(8)))))
        assertEquals(setOf(url(8)), state.protected)
    }

    @Test
    fun aRepeatedValueStillWaitsForTheLaterWrites() {
        val guard = FtutterEchoGuard()
        guard.wrote("A")
        guard.wrote("B")
        guard.wrote("A")
        assertEquals(true, guard.isOwnEcho("A"))
        assertEquals(true, guard.isOwnEcho("B"))
        assertEquals(true, guard.isOwnEcho("A"))
        assertEquals(false, guard.isOwnEcho("A"))
    }

    /** Re-audit L25: a notice where only the protection changed does not bring back a tab just closed. */
    @Test
    fun aProtectionEchoDoesNotRewindTheTabs() {
        val store = LaggingStore()
        val initial = mapOf(FtutterKeys.TABS to encodeFtutterList(listOf(url(1), url(2))))
        store.stored.putAll(initial)
        val state = FtutterTabState(parseFtutterTabs(initial), emptySet(), store::save)
        state.sync(store.stored.toMap())
        state.toggleProtect(url(2))
        state.close(listOf(url(1)))
        // The protection lands first: the stored tabs are still the old ones.
        state.sync(store.landNext())
        assertEquals(listOf(url(2)), state.urls)
        state.sync(store.landNext())
        assertEquals(listOf(url(2)), state.urls)
    }
}
