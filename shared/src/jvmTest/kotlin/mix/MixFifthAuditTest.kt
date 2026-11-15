package com.valoser.futacha.shared.mix

import androidx.compose.runtime.saveable.SaverScope
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/** Regression tests for the fifth MIX audit. */
class MixFifthAuditTest {
    private val url = "https://img.2chan.net/b/"
    private val board = BoardSummary("img", "img", "", url, "")
    private val row = MixEntry(board, CatalogItem("1", "${url}res/1.htm", "1", null, null, replyCount = 1),
        MixEntryKey(url, "1"), null, false, 0, 1)
    private val word = CompatNgRule("word", CompatNgKind.CATALOG_WORD, "*", "隠したい", 0L)
    private fun normalProjection(item: CatalogItem, enabled: Boolean, titles: Map<String, String> = emptyMap()) =
        buildCompatCatalogProjection(CompatCatalogProjectionRequest(
            items = listOf(item), resolvedTitles = titles, searchQuery = "",
            preference = CompatCatalogPreference("img", replyPriorityEnabled = false, showNonPriority = true),
            priorityThreshold = 0, catalogNgEnabled = enabled, watchWords = emptyList(),
            catalogImagePhashRules = emptyList(), catalogImagePhashes = emptyMap(),
            catalogImageNgPhashThreshold = CompatImagePhash.DEFAULT_THRESHOLD,
            catalogRuleIndex = buildCompatCatalogRuleIndex(listOf(word)), catalogSourceTitleLength = 256))

    @Test fun completedTitleReachesMixNgProjectionAndSurvivesRuleEdits() = runBlocking {
        val completed = "隠したい題名"
        assertTrue(normalProjection(row.item, true, mapOf(row.item.id to completed)).isEmpty())
        val titles = mapOf(row.key.asString() to completed)
        assertTrue(calculateMixCatalogProjection(listOf(row), listOf(word), emptyList(), emptyMap(),
            MixModePolicy.COMPAT, resolvedTitles = titles).isEmpty())
        val shown = calculateMixCatalogProjection(listOf(row), emptyList(), emptyList(), emptyMap(),
            MixModePolicy.COMPAT, resolvedTitles = titles)
        assertEquals(completed, shown.single().item.title)
        // A later NG edit must still use the completed title of the original source row.
        assertTrue(calculateMixCatalogProjection(listOf(row), listOf(word), emptyList(), emptyMap(),
            MixModePolicy.COMPAT, resolvedTitles = titles).isEmpty())
    }

    @Test fun compatNgOffKeepsTheSameRowsAsNormalCatalog() = runBlocking {
        val item = row.item.copy(title = "隠したい題名")
        assertEquals(listOf(item), normalProjection(item, false))
        val off = mapOf("compat.catalog.NG機能" to "OFF")
        assertEquals(listOf(row.copy(item = item)), calculateMixCatalogProjection(listOf(row.copy(item = item)),
            listOf(word), emptyList(), off, MixModePolicy.COMPAT))
        assertFalse(mixCatalogNgEnabled(off, MixModePolicy.COMPAT))
        assertTrue(mixCatalogNgEnabled(off, MixModePolicy.FUTABER))
    }

    @Test fun disabledMixRestoresToBoardListAndClearsSelection() {
        val before = MixAvailability.enabled
        try {
            MixAvailability.enabled = false
            val definition = MixDefinition("audit-disabled", "disabled", listOf(url), createdAt = 1)
            val saver = FutachaNavigationState.Saver
            val saved = with(saver) { SaverScope { true }.save(selectFutachaMix(FutachaNavigationState(), definition.id)) }!!
            val restored = saver.restore(saved)!!
            assertFalse(MixAvailability.enabled)
            assertIs<FutachaDestination.BoardManagement>(resolveFutachaDestination(restored, listOf(board), listOf(definition)))
            assertNull(reconcileFutachaMixSelection(restored, null).selectedMixId)
            MixAvailability.enabled = true
            assertIs<FutachaDestination.MixCatalog>(resolveFutachaDestination(restored, listOf(board), listOf(definition)))
        } finally { MixAvailability.enabled = before }
    }

    @Test fun corruptDefinitionsReportWhyOpeningWasRefusedWithoutOverwriting() = runBlocking {
        val root = Files.createTempDirectory("mix-fifth-audit").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val raw = "{broken"
            store.savePreference(MIX_DEFINITIONS_KEY, raw)
            val urls = listOf(url, "https://may.2chan.net/b/")
            assertIs<MixLoadResult.ReadOnly>(MixStore(store).load())
            assertTrue(mixOfferable(urls, raw))
            val refused = firstMixIdOrCreate(store, urls, 1) { error("must not confirm a corrupt definition") }
            assertIs<MixOpenResult.Refused>(refused)
            var notice: String? = null
            assertNull(refused.idOrReport { notice = it })
            assertContains(notice.orEmpty(), "壊れています")
            assertEquals(raw, store.loadPreference(MIX_DEFINITIONS_KEY))
        } finally { store.close(); root.deleteRecursively() }
    }
}
