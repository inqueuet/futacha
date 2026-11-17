package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatModeListTabKeys
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterList
import com.valoser.futacha.shared.ui.futaber.FutaberPreferenceKeys
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.futaberTabsToStore
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tab trim of the four modes on the real (desktop) store (plan B4, audit H2). Before, the trim counted every tab and
 * took no notice of ふたばー風's and ふたったー風's own rows: a tab trimmed in one mode was copied back by the mode that
 * still listed it (it vanished and came back), or the reconciliation closed it in that mode too.
 */
class SharedTabTrimTest {
    private val may = SingleMode.may

    /** Plan B8b: the modes' old rows exempt nothing (both strips are views of the shared tabs); ten new tabs keep their place. */
    @Test fun theModesOldRowsExemptNothingAndTenNewOnesKeepTheirPlace() = SingleMode.withStores { compat, _ ->
        val listed = (1..195).map { SingleMode.tab(may, "$it", insertedAt = it.toLong()) }
        assertEquals(195, compat.addTabsIfAbsent(listed, emptyMap()))
        val summaries = compat.boards.first().map { it.toBoardSummary() }
        val boardId = summaries.single { it.url.contains("may.2chan.net") }.id
        val futaberRow = futaberTabsToStore((1..50).map { FutaberTabKey(boardId, "$it") }, summaries)
        val ftutterRow = encodeFtutterList((51..195).map { SingleMode.threadUrl(may, "$it") })
        compat.savePreferences(mapOf(FutaberPreferenceKeys.TABS to futaberRow, FtutterKeys.TABS to ftutterRow))
        assertEquals(emptySet(), compatModeListTabKeys(compat.preferences.first(), compat.boards.first()), "no old row is exempt")

        // ふたちゃ opens ten threads: past the limit of 200 the tabs go back to 90% of it, the oldest first.
        (1001..1010).forEach { compat.openTab(SingleMode.tab(may, "$it", insertedAt = it.toLong())) }
        val kept = compat.tabs.first().map(CompatTab::key).toSet()
        assertTrue(kept.size in 100..200, "within the limit: ${kept.size}")
        assertTrue((1001..1010).all { SingleMode.tab(may, "$it", insertedAt = 0).key in kept }, "the ten new ones keep their place")
        assertTrue((1..10).none { SingleMode.tab(may, "$it", insertedAt = 0).key in kept }, "the oldest go, ふたばー風's old row too")
        val preferences = compat.preferences.first()
        assertEquals(futaberRow, preferences[FutaberPreferenceKeys.TABS], "ふたばー風's old row is untouched")
        assertEquals(ftutterRow, preferences[FtutterKeys.TABS], "ふたったー風's old row is untouched")
        assertEquals(null, compat.loadPendingClosedTabs(0L), "a trim is not a close")
    }

    @Test fun aTabAScreenHoldsStaysUntilTheScreenLetsGo() = SingleMode.withStores { compat, _ ->
        val held = SingleMode.tab(may, "1", insertedAt = 1)
        compat.openTab(held)
        compat.setScreenHeldTabs(ExperienceProfile.FUTABER, setOf(held.key))
        (2..230).forEach { compat.openTab(SingleMode.tab(may, "$it", insertedAt = it.toLong())) }
        assertTrue(compat.tabs.first().any { it.key == held.key }, "held while ふたばー風 shows it")
        compat.setScreenHeldTabs(ExperienceProfile.FUTABER, emptySet())
        (231..260).forEach { compat.openTab(SingleMode.tab(may, "$it", insertedAt = it.toLong())) }
        assertTrue(compat.tabs.first().none { it.key == held.key }, "trimmed like the others once let go")
    }

    @Test fun aStarAndTheActiveTabStayAndTheTotalStaysWithinTheLimit() = SingleMode.withStores { compat, _ ->
        compat.openTab(SingleMode.tab(may, "1", insertedAt = 1, favorite = true))
        (2..600).forEach { compat.openTab(SingleMode.tab(may, "$it", insertedAt = it.toLong())) }
        val kept = compat.tabs.first()
        assertTrue(kept.size <= 200, "within the shared limit")
        assertTrue(kept.any { it.threadNo == "1" } && kept.any { it.threadNo == "600" })
    }

    @Test fun theSourcesUseTheOneRuleAndKeepAnExistingTabWhenItIsAddedAgain() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        fun read(path: String) = File(root, path).readText()
        val android = read("app-android/src/main/java/com/valoser/futacha/compat/AndroidCompatibilityStore.kt")
        assertTrue("planSharedTabTrim(" in android && "compatTabTrimExemptKeys(" in android, "Android trims with the shared rule")
        assertTrue("TAB_LIMIT_TRIGGER" !in android, "no limit of 100 is left on Android")
        for (path in listOf("shared/src/iosMain/kotlin/compat/IosCompatibilityStore.kt", "shared/src/jvmMain/kotlin/compat/DesktopCompatibilityStore.kt")) {
            val store = read(path)
            assertTrue("MAX_TABS" !in store && "TAB_LIMIT_TRIGGER" !in store, "no limit of 100 is left in $path")
            assertTrue("screenHeldTabs.keys()" in store, "$path reads the held tabs")
        }
        val catalog = read("shared/src/commonMain/kotlin/ui/board/FutachaCatalogFeatures.kt")
        assertTrue("openSharedTab(candidate, profile = null)" in catalog, "ふたちゃ's 「タブに追加」 opens a shared tab (plan B9), which keeps an existing tab")
        assertTrue("store.openTab(" !in catalog, "ふたちゃ's 「タブに追加」 no longer writes the tab whole")
        assertTrue("setScreenHeldTabs(ExperienceProfile.FUTABER" in read("shared/src/commonMain/kotlin/ui/futaber/FutaberApp.kt"))
        assertTrue("ExperienceProfile.FTUTTER, setOfNotNull(key)" in read("shared/src/commonMain/kotlin/ui/ftutter/FtutterApp.kt"))
    }
}
