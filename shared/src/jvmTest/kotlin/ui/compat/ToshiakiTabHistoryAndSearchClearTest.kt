package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review 2026-11-11 (としあき(仮)): optional link between closing a tab and deleting its history,
 * and the clear button at the right end of the search fields.
 */
class ToshiakiTabHistoryAndSearchClearTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
    private fun src(path: String) = File(root, "shared/src/commonMain/kotlin/$path").readText()

    private fun url(n: Int) = "https://may.2chan.net/b/res/$n.htm"
    private fun tab(n: Int) = CompatTab(key = "tab$n", canonicalUrl = url(n), originalUrl = url(n), boardKey = "b", boardName = "二次元裏", threadNo = "$n", title = "題$n", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)
    private fun history(n: Int) = CompatHistoryEntry(canonicalUrl = url(n), originalUrl = url(n), boardKey = "b", boardName = "二次元裏", threadNo = "$n", title = "題$n", contentUpdatedAtEpochMillis = 1L)

    @Test
    fun bothSwitchesAreOffUnlessTurnedOn() {
        assertFalse(compatTabCloseDeletesHistory(emptyMap()))
        assertFalse(compatHistoryDeleteClosesTab(emptyMap()))
        assertFalse(compatTabCloseDeletesHistory(mapOf(COMPAT_TAB_CLOSE_DELETES_HISTORY_STORAGE_KEY to "OFF")))
        assertTrue(compatTabCloseDeletesHistory(mapOf(COMPAT_TAB_CLOSE_DELETES_HISTORY_STORAGE_KEY to "ON")))
        assertTrue(compatHistoryDeleteClosesTab(mapOf(COMPAT_HISTORY_DELETE_CLOSES_TAB_STORAGE_KEY to "ON")))
        val entries = compatParityControlEntries().associateBy { it.preferenceKey }
        assertTrue(COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY in entries)
        assertTrue(COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY in entries)
        assertTrue(COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY in COMPAT_PARITY_CONTROL_BOOLEAN_KEYS)
        assertTrue(COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY in COMPAT_PARITY_CONTROL_BOOLEAN_KEYS)
    }

    @Test
    fun onlyTheClosedTabsHistoryRowsAndOnlyTheDeletedRowsTabsAreChosen() {
        val rows = compatHistoryRowsForClosedTabs(setOf(url(1), url(3)), listOf(history(1), history(2), history(3)))
        assertEquals(listOf(url(1), url(3)), rows.map { it.canonicalUrl })
        assertEquals(setOf("tab2"), compatTabKeysForDeletedHistory(url(2), listOf(tab(1), tab(2), tab(3))))
        assertEquals(emptySet(), compatTabKeysForDeletedHistory(url(9), listOf(tab(1))))
    }

    @Test
    fun theAppWiresBothSwitchesThroughTheSharedStore() {
        val app = src("ui/compat/CompatibilityApp.kt")
        assertTrue("compatTabCloseDeletesHistory(" in app)
        assertTrue("compatHistoryDeleteClosesTab(" in app)
        assertTrue("deleteCompatHistoryRows(" in app)
    }

    @Test
    fun theSearchFieldsHaveAClearButton() {
        val bars = src("ui/compat/CompatTopBars.kt")
        assertTrue("compat-thread-search-clear" in bars && "onQueryChanged(\"\")" in bars)
        assertTrue("compat-catalog-search-clear" in src("ui/compat/CompatibilityApp.kt"))
        assertTrue("TextFieldValue(\"\")" in src("ui/board/ThreadTopLevelComponents.kt"))
        // Already present: ふたちゃ catalog, ふたばー風 catalog and menu search, ふたったー風 catalog and viewer.
        assertTrue("catalog_search_clear" in src("ui/board/CatalogChromeComponents.kt"))
        assertTrue("検索ワードを消す" in src("ui/futaber/FutaberOperationMenu.kt"))
        assertTrue("語を消す" in src("ui/ftutter/FtutterCatalog.kt"))
    }
}
