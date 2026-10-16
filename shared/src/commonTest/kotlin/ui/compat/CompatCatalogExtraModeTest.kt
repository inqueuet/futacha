package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatCatalogExtraModeTest {
    private val board = "compat_board_abc"

    @Test
    fun theExtraOrderIsAKeyOfItsOwnAndTheEnumIsUntouched() {
        assertEquals("compat.catalog.extraMode.$board", compatCatalogExtraModeKey(board))
        assertTrue(compatCatalogExtraModeKey(board) < "compat.ownpost.")
        assertEquals(listOf("CATALOG", "NEW", "OLD", "MANY", "FEW", "LIVELY"), CompatCatalogSort.entries.map { it.name })
        assertNull(compatCatalogExtraMode(emptyMap(), board))
        assertNull(compatCatalogExtraMode(mapOf(compatCatalogExtraModeKey(board) to "BOGUS"), board))
        assertEquals(CompatCatalogExtraMode.SO, compatCatalogExtraMode(mapOf(compatCatalogExtraModeKey(board) to "SO"), board))
        assertEquals(CompatCatalogExtraMode.WATCH, compatCatalogExtraMode(mapOf(compatCatalogExtraModeKey(board) to "WATCH"), board))
        // Another board's value does not leak.
        assertNull(compatCatalogExtraMode(mapOf(compatCatalogExtraModeKey("compat_board_other") to "SO"), board))
        assertEquals(listOf("そ順", "監視"), CompatCatalogExtraMode.entries.map { it.label })
    }

    @Test
    fun soFetchesSortEightAndWatchHasNoSingleMode() {
        assertEquals(CatalogMode.So, CompatCatalogExtraMode.SO.fetchMode())
        assertEquals("8", CompatCatalogExtraMode.SO.fetchMode()?.sortParam)
        assertNull(CompatCatalogExtraMode.WATCH.fetchMode())
    }

    @Test
    fun watchWordsArePatrolWordsThenCatalogExtractionWordsOfTheBoard() {
        fun rule(kind: CompatNgKind, value: String) = CompatNgRule("id-$value", kind, board, value, 0L)
        val words = compatCatalogWatchModeWords(
            patrolWords = listOf("猫", " 犬 ", ""),
            boardCatalogRules = listOf(
                rule(CompatNgKind.CATALOG_EXTRACT, "鳥"),
                rule(CompatNgKind.CATALOG_EXTRACT, "猫"),
                rule(CompatNgKind.CATALOG_IGNORE, "隠す")
            )
        )
        assertEquals(listOf("猫", "犬", "鳥"), words)
    }

    private class RecordingRepository(private val items: List<CatalogItem>) : com.valoser.futacha.shared.repo.BoardRepository by FakeBoardRepository() {
        val modes = mutableListOf<CatalogMode>()
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
            modes += mode
            return items
        }
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) =
            com.valoser.futacha.shared.model.CatalogPageContent(getCatalogWithSettings(board, mode, settings))
    }

    private fun item(id: String, title: String, replies: Int = 1) =
        CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", title, null, null, replyCount = replies)

    @Test
    fun theFetchUsesTheBoardOrderTheExtraOrderOrTheWatchCatalogs() = runBlocking {
        val items = listOf(item("1", "猫の話", 5), item("2", "犬の話", 3), item("3", "鳥の話", 9))
        val settings = CatalogFetchSettings()

        val plain = RecordingRepository(items)
        assertEquals(items, fetchCompatCatalogItems(plain, "https://may.2chan.net/b/", CatalogMode.New, null, settings) { emptyList() })
        assertEquals(listOf(CatalogMode.New), plain.modes)

        val so = RecordingRepository(items)
        assertEquals(items, fetchCompatCatalogItems(so, "https://may.2chan.net/b/", CatalogMode.New, CompatCatalogExtraMode.SO, settings) { emptyList() })
        assertEquals(listOf(CatalogMode.So), so.modes)

        val watch = RecordingRepository(items)
        val watched = fetchCompatCatalogItems(watch, "https://may.2chan.net/b/", CatalogMode.New, CompatCatalogExtraMode.WATCH, settings) { listOf("猫", "鳥") }
        assertEquals(listOf("3", "1"), watched.map { it.id })
        assertEquals(CatalogMode.watchSourceModes.toSet(), watch.modes.toSet())

        // No words: empty, and nothing is fetched (as ふたちゃ's 監視).
        val none = RecordingRepository(items)
        assertEquals(emptyList(), fetchCompatCatalogItems(none, "https://may.2chan.net/b/", CatalogMode.New, CompatCatalogExtraMode.WATCH, settings) { emptyList() })
        assertEquals(emptyList(), none.modes)
    }

    /** Re-audit L14: "前の状態に戻す" says it cannot go back in "そ順"/"監視" instead of showing the board's own order. */
    @Test
    fun theExtraOrdersRefuseGoingBack() {
        kotlin.test.assertNull(compatCatalogRollbackRefusal(null))
        kotlin.test.assertEquals("「監視」の一覧は前の状態に戻せません", compatCatalogRollbackRefusal(CompatCatalogExtraMode.WATCH))
    }
}
