package com.valoser.futacha

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** まとめカタログの入口：ふたばー風のドロワーから開くと、まとめの画面（切替・編集）が出る。 */
class MixEntryPointsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private var profile = ExperienceProfile.FUTABER

    private fun prepare(target: ExperienceProfile) {
        profile = target
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(mapOf("compat.futaber.lastBoardId" to null, "compat.ftutter.lastBoard" to null))
            app.appStateStore.updateBoards { b -> b.filterNot { it.id.startsWith("mixtest-") } + listOf(
                com.valoser.futacha.shared.model.BoardSummary("mixtest-may", "may", "", "https://may.2chan.net/b/", ""),
                com.valoser.futacha.shared.model.BoardSummary("mixtest-img", "img", "", "https://img.2chan.net/b/", "")
            ) }
        }
        switchProfile(target)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.appStateStore.updateBoards { b -> b.filterNot { it.id.startsWith("mixtest-") } } }
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    @Test fun theFutaberDrawerOpensTheMix() {
        prepare(ExperienceProfile.FUTABER)
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-drawer").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("まとめカタログ").performScrollTo().performClick()
        try {
            rule.waitUntil(20_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            rule.onRoot(useUnmergedTree = true).printToLog("MIXDBG")
            throw e
        }
    }

    @Test fun theFtutterBoardListOpensTheMix() {
        prepare(ExperienceProfile.FTUTTER)
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("ftutter-board-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("まとめカタログ").performScrollTo().performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theFutachaMenuOpensTheMix() {
        prepare(ExperienceProfile.FUTACHA)
        rule.waitUntil(20_000) { rule.onAllNodesWithContentDescription("メニュー").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("メニュー").performClick()
        rule.onNodeWithText("まとめカタログ").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theToshiakiMainMenuOpensTheMix() {
        prepare(ExperienceProfile.TOSHIAKI_COMPAT)
        rule.waitUntil(20_000) { rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("まとめカタログ").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
    }

    /** 実在の板（may・img）を読むだけ：まとめの一覧が空・失敗でなく実際に並ぶ。書き込みはしない。 */
    @Test fun theMixListsRealThreadsFromBothBoards() {
        prepare(ExperienceProfile.FUTABER)
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-drawer").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("まとめカタログ").performScrollTo().performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
        val spinner = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)
        try {
            rule.waitUntil(150_000) { rule.onAllNodes(spinner, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        } catch (e: Throwable) {
            rule.onRoot(useUnmergedTree = true).printToLog("MIXREAL")
            throw e
        }
        rule.onRoot(useUnmergedTree = true).printToLog("MIXREAL")
        val mayRows = rule.onAllNodes(androidx.compose.ui.test.hasText("may ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size
        val imgRows = rule.onAllNodes(androidx.compose.ui.test.hasText("img ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size
        org.junit.Assert.assertTrue("may rows=$mayRows img rows=$imgRows", mayRows > 0 && imgRows > 0)
        val failed = rule.onAllNodesWithText("まとめカタログを読み込めませんでした").fetchSemanticsNodes().size
        val empty = rule.onAllNodesWithText("表示できるスレッドがありません").fetchSemanticsNodes().size
        org.junit.Assert.assertEquals(0, failed + empty)
    }
}
