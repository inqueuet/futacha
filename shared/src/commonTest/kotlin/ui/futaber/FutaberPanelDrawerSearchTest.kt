package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/** L-B10: the manage panel, the board list and the catalog search as futaber has them (look plan §1.3, §2.10). */
class FutaberPanelDrawerSearchTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun aRowsDateIsFutabersTwoDigitYearForm() {
        val tokyo = TimeZone.of("Asia/Tokyo")
        val millis = LocalDateTime(2026, 10, 31, 1, 8).toInstant(tokyo).toEpochMilliseconds()
        assertEquals("26/10/31 01:08", futaberPanelDate(millis, tokyo))
        val morning = LocalDateTime(2009, 1, 5, 9, 3).toInstant(tokyo).toEpochMilliseconds()
        assertEquals("09/01/05 09:03", futaberPanelDate(morning, tokyo))
        // The shared form the other modes use is unchanged.
        assertEquals(16, com.valoser.futacha.shared.ui.board.formatLastVisited(millis).length)
    }

    @Test
    fun theCategoryChosenLastIsKeptAndAnUnknownOrOldValueOpensTheHistory() {
        FutaberManageCategory.entries.forEach { assertEquals(it, futaberManageCategoryFromStored(it.persisted)) }
        assertEquals("compat.futaber.manageCategory", FutaberPreferenceKeys.MANAGE_CATEGORY)
        // Nothing stored (before L-B10 the panel always opened on the history), a broken or a lower-case value.
        listOf(null, "", "Unknown", "history", "保存箱", " ").forEach {
            assertEquals(FutaberManageCategory.History, futaberManageCategoryFromStored(it), "$it")
        }
        assertEquals(FutaberManageCategory.Favorites, futaberManageCategoryFromStored(" Favorites "))
    }

    @Test
    fun eachCategoryHasFutabersHeadingWordAndAllDeleteWhileEditing() {
        // 「編集」 for the history and the notices, 「選択」 for the saved box and the favourites (M29・M50).
        assertEquals("編集", futaberManageHeader(FutaberManageCategory.History, false, 3).rightLabel)
        assertEquals("編集", futaberManageHeader(FutaberManageCategory.Notifications, false, 0).rightLabel)
        assertEquals("選択", futaberManageHeader(FutaberManageCategory.SavedBox, false, 0).rightLabel)
        assertEquals("選択", futaberManageHeader(FutaberManageCategory.Favorites, false, 1).rightLabel)
        listOf(FutaberManageCategory.History, FutaberManageCategory.Notifications, FutaberManageCategory.SavedBox, FutaberManageCategory.Favorites)
            .forEach { category ->
                assertNull(futaberManageHeader(category, false, 5).leftLabel, "$category")
                val editing = futaberManageHeader(category, true, 0)
                // 「すべて削除」 and 「完了」 also with nothing listed (futaber's); nothing to delete then.
                assertEquals("すべて削除", editing.leftLabel)
                assertEquals("完了", editing.rightLabel)
                assertFalse(editing.leftEnabled)
                assertTrue(futaberManageHeader(category, true, 2).leftEnabled)
            }
        assertEquals("MHTファイル", futaberManageHeader(FutaberManageCategory.SavedBox, false, 0).title)
        assertEquals("スレッド履歴", futaberManageHeader(FutaberManageCategory.History, false, 0).title)
        // The tabs keep 「すべて削除」 only, while something is listed (as before).
        val tabs = futaberManageHeader(FutaberManageCategory.Tabs, false, 2)
        assertEquals("すべて削除", tabs.leftLabel)
        assertNull(tabs.rightLabel)
        assertNull(futaberManageHeader(FutaberManageCategory.Tabs, false, 0).leftLabel)
        // A row opens only outside the edit.
        assertTrue(futaberManageRowOpens(editing = false, openable = true))
        assertFalse(futaberManageRowOpens(editing = true, openable = true))
        assertFalse(futaberManageRowOpens(editing = false, openable = false))
        // The confirmation says what goes.
        assertEquals("保存箱からMHTファイル2件と保存したスレッド1件を削除します。元に戻せません。",
            futaberManageClearMessage(FutaberManageCategory.SavedBox, mhtCount = 2, savedCount = 1))
        assertTrue(futaberManageClearMessage(FutaberManageCategory.Notifications, count = 3).startsWith("通知の3件"))
    }

    @Test
    fun theAdditionsComeAfterFutabersListFromOneFunction() {
        assertEquals(
            listOf(FutaberManageExtra.NotificationsStatus, FutaberManageExtra.KeywordManager),
            futaberManagePanelExtras(FutaberManageCategory.Notifications)
        )
        assertEquals(emptyList(), futaberManagePanelExtras(FutaberManageCategory.Notifications, notificationsAvailable = false))
        assertEquals(listOf(FutaberManageExtra.HtmlSaves, FutaberManageExtra.MhtImport),
            futaberManagePanelExtras(FutaberManageCategory.SavedBox, htmlSaves = true, mhtAvailable = true))
        assertEquals(listOf(FutaberManageExtra.MhtImport), futaberManagePanelExtras(FutaberManageCategory.SavedBox, mhtAvailable = true))
        assertEquals(listOf(FutaberManageExtra.OtherFavorites), futaberManagePanelExtras(FutaberManageCategory.Favorites, otherFavorites = true))
        assertEquals(emptyList(), futaberManagePanelExtras(FutaberManageCategory.Favorites))
        assertEquals(emptyList(), futaberManagePanelExtras(FutaberManageCategory.History))
        assertEquals(emptyList(), futaberManagePanelExtras(FutaberManageCategory.Tabs))
        // S21: futaber's total line over the MHT files.
        assertEquals("0KB 0項目の合計", futaberMhtTotalLine(0, 0))
        assertEquals("154KB 1項目の合計", futaberMhtTotalLine(154L * 1024 + 300, 1))
        assertEquals("2.5MB 3項目の合計", futaberMhtTotalLine(2_621_440, 3))
        assertEquals("読み込み中…", futaberMhtTotalLine(0, 0, "読み込み中…"))
        // The board list's edit: the bulk add and the rename after futaber's 「完了」 and 「板の追加」.
        assertEquals(listOf(FutaberDrawerEditExtra.BulkAdd, FutaberDrawerEditExtra.Rename), futaberBoardDrawerEditExtras(true, true))
        assertEquals(listOf(FutaberDrawerEditExtra.Rename), futaberBoardDrawerEditExtras(false, true))
        assertEquals(emptyList(), futaberBoardDrawerEditExtras(false, false))
    }

    @Test
    fun theBoardAddAlertSaysWhyAnEmptyOkAddsNothing() {
        val boards = listOf(BoardSummary("b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", ""))
        assertEquals("https://", FUTABER_ADD_BOARD_PREFILL)
        listOf("", "https://", " http:// ").forEach { assertEquals("板のアドレスを入力してください", futaberAddBoardAlertReason(it, boards)) }
        assertNull(futaberAddBoardAlertReason("https://zip.2chan.net/1/", boards))
        // An address without the scheme, or one already listed, is refused with the shared validation's reason.
        assertTrue(futaberAddBoardAlertReason("zip.2chan.net/1/", boards) != null)
        assertTrue(futaberAddBoardAlertReason("https://may.2chan.net/b/", boards) != null)
    }

    @Test
    fun aDraggedBoardMovesByWholeRows() {
        assertEquals(2, futaberDragTargetIndex(2, 10f, 100f, 5))
        assertEquals(3, futaberDragTargetIndex(2, 50f, 100f, 5))
        assertEquals(1, futaberDragTargetIndex(2, -60f, 100f, 5))
        assertEquals(0, futaberDragTargetIndex(2, -900f, 100f, 5))
        assertEquals(4, futaberDragTargetIndex(2, 900f, 100f, 5))
        assertEquals(2, futaberDragTargetIndex(2, 90f, 0f, 5))
        assertEquals(0.04f, FUTABER_DRAWER_DIM_ALPHA)
    }

    @Test
    fun theSearchShowsNothingUntilAWordIsTypedAndThePopoverLabelsAreFullWidth() {
        assertTrue(futaberCatalogSearchShowsNothing(searchOpen = true, query = ""))
        assertFalse(futaberCatalogSearchShowsNothing(searchOpen = true, query = "ね"))
        assertFalse(futaberCatalogSearchShowsNothing(searchOpen = false, query = ""))
        // A space is a word typed (the list then filters as before).
        assertFalse(futaberCatalogSearchShowsNothing(searchOpen = true, query = " "))
        assertEquals(listOf("行１", "行２", "横３", "横４", "横６", "横８"), FutaberCatalogDisplayStyle.entries.map { futaberPopoverLabel(it.label) })
        // Only the labels change: the stored values and the screen reader's names stay.
        assertEquals(listOf("row1", "row2", "grid3", "grid4", "grid6", "grid8"), FutaberCatalogDisplayStyle.entries.map { it.persistedValue })
        assertEquals("行1", FutaberCatalogDisplayStyle.Row1.label)
        assertEquals(FutaberCatalogDisplayStyle.Grid8, FutaberCatalogDisplayStyle.fromPersistedValue("grid8"))
        assertEquals(listOf("通常", "新順", "古順", "多順", "少順", "勢い"), futaberCatalogSorts.map { futaberPopoverLabel(it.label) })
    }

    @Test
    fun theChosenCategoryIsFilledAndTheOthersAreLines() {
        val colors = FutaberTokens.light
        FutaberManageCategory.entries.forEach { category ->
            assertTrue(category.filledIcon.name.startsWith("FutaberPanel"), category.name)
            assertTrue(category.filledIcon !== category.icon)
            // The line glyph of the others is the thin outline set.
            assertTrue(futaberIconFor(category.icon).name.startsWith("FutaberLine."), category.name)
        }
        assertEquals(colors.categorySaved, futaberCategoryColor(FutaberManageCategory.SavedBox, colors))
        assertEquals(colors.categoryTabs, futaberCategoryColor(FutaberManageCategory.Tabs, colors))
    }
}
