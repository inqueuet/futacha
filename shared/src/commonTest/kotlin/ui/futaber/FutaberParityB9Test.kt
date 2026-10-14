package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.state.AppStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The B9 parity batch of the ふたばー風 mode (C06 the catalog's fine adjustments, the auto-saved copy of a viewed thread). */
class FutaberParityB9Test {
    private val may = BoardSummary("may-b", "虹裏may", "二次元", "https://may.2chan.net/b/futaba.php", "")

    // ---- the switches ----

    @Test fun theNewExtensionsAreOffByDefaultAndReadOnlyAnOn() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extCatalogFineTune)
        assertFalse(defaults.extAutoSaveView)
        assertEquals(FutaberCatalogFineTune(), defaults.catalogFine)
        assertEquals(defaults, FutaberDisplaySettings())
        val on = FutaberDisplaySettings.from(
            mapOf(FutaberSettingKeys.EXT_CATALOG_FINE_TUNE to "ON", FutaberSettingKeys.EXT_AUTO_SAVE_VIEW to "ON")
        )
        assertTrue(on.extCatalogFineTune)
        assertTrue(on.extAutoSaveView)
        val other = FutaberDisplaySettings.from(
            mapOf(FutaberSettingKeys.EXT_CATALOG_FINE_TUNE to "on", FutaberSettingKeys.EXT_AUTO_SAVE_VIEW to "1")
        )
        assertFalse(other.extCatalogFineTune)
        assertFalse(other.extAutoSaveView)
        assertTrue(FutaberSettingKeys.EXT_CATALOG_FINE_TUNE.startsWith("compat.futaber."))
        assertTrue(FutaberSettingKeys.EXT_AUTO_SAVE_VIEW.startsWith("compat.futaber."))
    }

    // ---- C06: the catalog's fine adjustments ----

    @Test fun withNothingStoredOrTheExtensionOffTheGridIsDrawnAsBefore() {
        val fine = FutaberCatalogFineTune()
        FutaberCatalogDisplayStyle.entries.forEach { style ->
            assertEquals(style.columns, futaberCatalogColumns(style, fine), style.name)
        }
        assertEquals(1, fine.gridTitleLines)
        assertEquals("長い題名のスレッド", futaberGridTitle("長い題名のスレッド", fine))
        // Values stored while the extension is off are not read.
        val stored = mapOf(
            FutaberCatalogFineKeys.gridColumns(FutaberCatalogDisplayStyle.Grid4) to "2",
            FutaberCatalogFineKeys.GRID_TITLE_LINES to "3",
            FutaberCatalogFineKeys.GRID_TITLE_CHARS to "0"
        )
        assertEquals(FutaberCatalogFineTune(), FutaberCatalogFineTune.from(stored))
        assertEquals(4, futaberCatalogColumns(FutaberCatalogDisplayStyle.Grid4, FutaberDisplaySettings.from(stored).catalogFine))
    }

    @Test fun theStoredValuesChangeOnlyTheirOwnGridStyle() {
        val fine = FutaberCatalogFineTune.from(
            mapOf(
                FutaberSettingKeys.EXT_CATALOG_FINE_TUNE to "ON",
                FutaberCatalogFineKeys.gridColumns(FutaberCatalogDisplayStyle.Grid4) to "2",
                FutaberCatalogFineKeys.gridColumns(FutaberCatalogDisplayStyle.Grid8) to "7",
                FutaberCatalogFineKeys.GRID_TITLE_LINES to "2",
                FutaberCatalogFineKeys.GRID_TITLE_CHARS to "5"
            )
        )
        assertEquals(2, futaberCatalogColumns(FutaberCatalogDisplayStyle.Grid4, fine))
        assertEquals(7, futaberCatalogColumns(FutaberCatalogDisplayStyle.Grid8, fine))
        assertEquals(3, futaberCatalogColumns(FutaberCatalogDisplayStyle.Grid3, fine))
        assertEquals(6, futaberCatalogColumns(FutaberCatalogDisplayStyle.Grid6, fine))
        // The row styles have no columns, whatever is stored.
        assertNull(futaberCatalogColumns(FutaberCatalogDisplayStyle.Row1, fine))
        assertNull(futaberCatalogColumns(FutaberCatalogDisplayStyle.Row2, fine))
        assertEquals(2, fine.gridTitleLines)
        assertEquals("あいうえお", futaberGridTitle("あいうえおかきくけこ", fine))
        assertEquals("短い", futaberGridTitle("短い", fine))
    }

    @Test fun brokenOrOutOfRangeValuesReadAsTheDefault() {
        val fine = FutaberCatalogFineTune.from(
            mapOf(
                FutaberSettingKeys.EXT_CATALOG_FINE_TUNE to "ON",
                FutaberCatalogFineKeys.gridColumns(FutaberCatalogDisplayStyle.Grid3) to "1",
                FutaberCatalogFineKeys.gridColumns(FutaberCatalogDisplayStyle.Grid4) to "9",
                FutaberCatalogFineKeys.gridColumns(FutaberCatalogDisplayStyle.Grid6) to "abc",
                FutaberCatalogFineKeys.GRID_TITLE_LINES to "4",
                FutaberCatalogFineKeys.GRID_TITLE_CHARS to "21"
            )
        )
        assertEquals(FutaberCatalogFineTune(), fine)
        val emptyChars = FutaberCatalogFineTune.from(
            mapOf(FutaberSettingKeys.EXT_CATALOG_FINE_TUNE to "ON", FutaberCatalogFineKeys.GRID_TITLE_CHARS to "")
        )
        assertNull(emptyChars.gridTitleChars)
        assertEquals(listOf(FutaberCatalogDisplayStyle.Grid3, FutaberCatalogDisplayStyle.Grid4, FutaberCatalogDisplayStyle.Grid6, FutaberCatalogDisplayStyle.Grid8), futaberGridStyles)
        futaberGridStyles.forEach { assertTrue(FutaberCatalogFineKeys.gridColumns(it).startsWith("compat.futaber.catalog.")) }
    }

    @Test fun zeroLettersHidesTheTitleAndAPairIsNotCutInHalf() {
        assertNull(futaberGridTitle("題名", FutaberCatalogFineTune(gridTitleChars = 0)))
        // "a" + 😀 (a surrogate pair): two letters stop before the pair instead of keeping its first half.
        assertEquals("a", futaberGridTitle("a😀b", FutaberCatalogFineTune(gridTitleChars = 2)))
        assertEquals("a😀", futaberGridTitle("a😀b", FutaberCatalogFineTune(gridTitleChars = 3)))
    }

    @Test fun theLettersStepperGoesThroughStandardAtTheTop() {
        assertEquals(FUTABER_GRID_TITLE_CHARS_MAX, futaberGridTitleCharsAfter(null, -1))
        assertNull(futaberGridTitleCharsAfter(null, 1))
        assertNull(futaberGridTitleCharsAfter(FUTABER_GRID_TITLE_CHARS_MAX, 1))
        assertEquals(4, futaberGridTitleCharsAfter(5, -1))
        assertEquals(0, futaberGridTitleCharsAfter(0, -1))
        assertEquals(1, futaberGridTitleCharsAfter(0, 1))
        assertEquals("標準", futaberGridTitleCharsLabel(null))
        assertEquals("表示しない", futaberGridTitleCharsLabel(0))
        assertEquals("8字", futaberGridTitleCharsLabel(8))
    }

    // ---- the auto-saved copy of a viewed thread ----

    private fun page(id: String = "123") = ThreadPage(id, null, null, null, emptyList())

    @Test fun onlyTheLivePageOnScreenIsSaved() {
        val live = page()
        assertSame(live, futaberAutoSaveTarget(enabled = true, livePage = live, visiblePage = live, showingCopy = false))
        // The extension off.
        assertNull(futaberAutoSaveTarget(enabled = false, livePage = live, visiblePage = live, showingCopy = false))
        // A saved copy / an MHT / an archive / the auto-saved copy on screen.
        assertNull(futaberAutoSaveTarget(enabled = true, livePage = live, visiblePage = live, showingCopy = true))
        // Nothing loaded live yet (the first load failed and an archive or a copy is shown).
        assertNull(futaberAutoSaveTarget(enabled = true, livePage = null, visiblePage = page(), showingCopy = false))
        // "更新前に戻す" shows the older page: an equal page that is not the live one is not saved.
        assertNull(futaberAutoSaveTarget(enabled = true, livePage = live, visiblePage = page(), showingCopy = false))
        assertNull(futaberAutoSaveTarget(enabled = true, livePage = live, visiblePage = null, showingCopy = false))
    }

    @Test fun theLinkIsMadeOnlyWithTheExtensionAndEverythingItNeeds() {
        val store = AppStateStore(com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage())
        assertNull(futaberAutoSaveLink(false, null, null, store, emptyList(), may, "1"))
        // On, but the host gave no repository / services.
        assertNull(futaberAutoSaveLink(true, null, null, store, emptyList(), may, "1"))
    }

    private fun entry(threadId: String, boardId: String, hasAutoSave: Boolean = false, boardUrl: String = "") = ThreadHistoryEntry(
        threadId = threadId, boardId = boardId, title = "t$threadId", titleImageUrl = "", boardName = "may",
        boardUrl = boardUrl, lastVisitedEpochMillis = 10L, replyCount = 3, hasAutoSave = hasAutoSave
    )

    @Test fun savingMarksOnlyTheThreadsOwnHistoryRow() {
        val history = listOf(entry("1", "img-b"), entry("1", may.id), entry("2", may.id))
        val marked = futaberHistoryMarkedAutoSaved(history, may, "1")
        assertEquals(listOf(false, true, false), marked.map { it.hasAutoSave })
        // Nothing else of the row moves (the visit time, the count).
        assertEquals(history[1].copy(hasAutoSave = true), marked[1])
        // Already marked, or no row: the same list (so nothing is written).
        assertSame(marked, futaberHistoryMarkedAutoSaved(marked, may, "1"))
        assertSame(history, futaberHistoryMarkedAutoSaved(history, may, "9"))
        // A row of the older form (no board id) is found by the board's address, as the history lookup does.
        val legacy = listOf(entry("5", "", boardUrl = may.url))
        assertTrue(futaberHistoryMarkedAutoSaved(legacy, may, "5").single().hasAutoSave)
        assertNotNull(findFutaberHistoryEntry(legacy, may, "5"))
    }
}
