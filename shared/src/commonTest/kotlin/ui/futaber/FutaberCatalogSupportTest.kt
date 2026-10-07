package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutaberCatalogSupportTest {
    private fun item(id: String, title: String?, replies: Int = 0) = CatalogItem(
        id = id,
        threadUrl = "https://may.2chan.net/b/res/$id.htm",
        title = title,
        thumbnailUrl = null,
        fullImageUrl = null,
        replyCount = replies
    )

    @Test
    fun theSixLayoutsKeepTheirColumnsAndTitleLines() {
        assertEquals(
            listOf("行1", "行2", "横3", "横4", "横6", "横8"),
            FutaberCatalogDisplayStyle.entries.map { it.label }
        )
        assertEquals(
            listOf(null, null, 3, 4, 6, 8),
            FutaberCatalogDisplayStyle.entries.map { it.columns }
        )
        assertEquals(listOf(1, 2), FutaberCatalogDisplayStyle.entries.take(2).map { it.titleLines })
        assertTrue(FutaberCatalogDisplayStyle.Grid4.isGrid)
        assertTrue(!FutaberCatalogDisplayStyle.Row1.isGrid)
    }

    @Test
    fun layoutIsRestoredFromItsStoredValueAndFallsBackToTheDefault() {
        FutaberCatalogDisplayStyle.entries.forEach {
            assertEquals(it, FutaberCatalogDisplayStyle.fromPersistedValue(it.persistedValue))
        }
        assertEquals(FutaberCatalogDisplayStyle.default, FutaberCatalogDisplayStyle.fromPersistedValue(null))
        assertEquals(FutaberCatalogDisplayStyle.default, FutaberCatalogDisplayStyle.fromPersistedValue("grid5"))
        assertEquals(
            setOf("row1", "row2", "grid3", "grid4", "grid6", "grid8"),
            FutaberCatalogDisplayStyle.entries.map { it.persistedValue }.toSet()
        )
    }

    @Test
    fun theSixSortsMapToExistingCatalogModes() {
        assertEquals(
            listOf("通常", "新順", "古順", "多順", "少順", "勢い"),
            futaberCatalogSorts.map { it.label }
        )
        assertEquals(
            listOf(CatalogMode.Catalog, CatalogMode.New, CatalogMode.Old, CatalogMode.Many, CatalogMode.Few, CatalogMode.Momentum),
            futaberCatalogSorts.map { it.mode }
        )
        futaberCatalogSorts.forEach {
            assertEquals(it.mode, futaberCatalogModeFromPersisted(it.mode.name))
        }
        assertEquals(futaberDefaultCatalogMode, futaberCatalogModeFromPersisted(null))
        // Modes ふたばー does not offer fall back instead of leaking in from another mode.
        assertEquals(futaberDefaultCatalogMode, futaberCatalogModeFromPersisted(CatalogMode.So.name))
        assertEquals(futaberDefaultCatalogMode, futaberCatalogModeFromPersisted(CatalogMode.WatchWords.name))
    }

    @Test
    fun searchIgnoresWidthCaseAndKanaType() {
        val items = listOf(item("1", "ＡＢＣ のスレ"), item("2", "ねこ画像"), item("3", "ネコの話"), item("4", null))
        assertEquals(items, filterFutaberCatalog(items, ""))
        assertEquals(items, filterFutaberCatalog(items, "   "))
        assertEquals(listOf("1"), filterFutaberCatalog(items, "abc").map { it.id })
        assertEquals(listOf("2", "3"), filterFutaberCatalog(items, "ねこ").map { it.id })
        assertEquals(listOf("2", "3"), filterFutaberCatalog(items, "ネコ").map { it.id })
        assertTrue(filterFutaberCatalog(items, "いぬ").isEmpty())
    }

    @Test
    fun boardAddressDropsSchemeAndTrailingSlash() {
        assertEquals("may.2chan.net/b", futaberBoardAddress("https://may.2chan.net/b/"))
        assertEquals("img.2chan.net/b", futaberBoardAddress(" http://img.2chan.net/b "))
        assertEquals("www.example.com/t", futaberBoardAddress("https://www.example.com/t/"))
    }

    @Test
    fun aThreadWithoutATitleStaysTappable() {
        assertEquals("(無題)", futaberCatalogTitle(item("1", null)))
        assertEquals("(無題)", futaberCatalogTitle(item("1", "  ")))
        assertEquals("題名", futaberCatalogTitle(item("1", " 題名 ")))
    }

    @Test
    fun storedKeysStayUnderTheCompatPrefixAndAreDistinct() {
        val keys = listOf(
            FutaberPreferenceKeys.CATALOG_DISPLAY_STYLE,
            FutaberPreferenceKeys.CATALOG_SORT,
            FutaberPreferenceKeys.THEME,
            FutaberPreferenceKeys.LAST_BOARD_ID
        )
        keys.forEach { assertTrue(it.startsWith("compat.futaber."), it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun catalogNgWordsHideThreadsByTitleAndEmptyEntriesChangeNothing() {
        val a = CatalogItem("1", "u1", "猫の画像", null, null, replyCount = 1)
        val b = CatalogItem("2", "u2", "ＮＧワード入り", null, null, replyCount = 2)
        val items = listOf(a, b)
        assertEquals(items, futaberApplyCatalogNg(items, emptyList()))
        assertEquals(items, futaberApplyCatalogNg(items, listOf("", "  ")))
        // Compared the way the search compares: full-width and half-width letters are the same.
        assertEquals(listOf(a), futaberApplyCatalogNg(items, listOf("ngワード")))
        assertEquals(emptyList(), futaberApplyCatalogNg(items, listOf("猫", "ワード")))
    }
}
