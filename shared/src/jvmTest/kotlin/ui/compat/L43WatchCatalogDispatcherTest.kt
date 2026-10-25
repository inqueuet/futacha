package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * 2026-10-20の監査L43：としあき(仮)の「監視」は、2つのカタログの結合・監視ワードの照合・並べ替えを呼び出し側（画面の
 * スレッド）で行っていた。取得した一覧に触れるのは呼び出し側の外だけで、結果（並び）は変わらないことを確かめる。
 */
class L43WatchCatalogDispatcherTest {
    private fun item(id: String, title: String, replies: Int) =
        CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", title, null, null, replyCount = replies)

    /** A catalog whose rows may only be read away from [caller]. */
    private class CallerForbiddenItems(
        private val rows: List<CatalogItem>,
        private val caller: () -> Thread?
    ) : AbstractList<CatalogItem>() {
        override val size: Int get() = rows.size
        override fun get(index: Int): CatalogItem {
            assertNotSame(caller(), Thread.currentThread(), "The watch catalogs must not be merged/matched on the caller UI thread")
            return rows[index]
        }
    }

    private class ForbiddenRepository(
        private val rows: List<CatalogItem>,
        private val caller: () -> Thread?
    ) : BoardRepository by FakeBoardRepository() {
        val modes = mutableListOf<CatalogMode>()
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent {
            synchronized(modes) { modes += mode }
            return CatalogPageContent(CallerForbiddenItems(rows, caller))
        }
    }

    @Test
    fun theWatchCatalogsAreMergedAndMatchedOffTheCallerThreadWithTheSameResult() = runBlocking {
        val rows = listOf(item("1", "猫の話", 5), item("2", "犬の話", 3), item("3", "鳥の話", 9), item("4", "猫と鳥", 1))
        var callerThread: Thread? = null
        Executors.newSingleThreadExecutor { task -> Thread(task, "L43-ui") }.asCoroutineDispatcher().use { ui ->
            withContext(ui) {
                val caller = Thread.currentThread()
                callerThread = caller
                var wordsReadOn: Thread? = null
                val repository = ForbiddenRepository(rows) { callerThread }
                val watched = fetchCompatCatalogItems(
                    repository = repository,
                    boardUrl = "https://may.2chan.net/b/",
                    baseMode = CatalogMode.New,
                    extraMode = CompatCatalogExtraMode.WATCH,
                    settings = CatalogFetchSettings()
                ) {
                    // The words come from the screen's state: they are still read on the caller.
                    wordsReadOn = Thread.currentThread()
                    listOf("猫", "鳥")
                }
                assertSame(caller, Thread.currentThread(), "The caller must resume on its UI dispatcher")
                assertSame(caller, wordsReadOn, "The watch words are read on the caller")
                // Same rows and order as before the move (best matches first, then the most replies).
                assertEquals(listOf("4", "3", "1"), watched.map { it.id })
                assertEquals(CatalogMode.watchSourceModes.toSet(), repository.modes.toSet())
            }
        }
    }
}
