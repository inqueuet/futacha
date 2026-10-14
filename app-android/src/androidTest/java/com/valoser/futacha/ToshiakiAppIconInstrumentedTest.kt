package com.valoser.futacha

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.compat.AndroidLauncherAliasManager
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The app icon chosen in としあき(仮)'s own settings really switches the launcher icon, through the application's
 * normal path (the shared setting that the activity applies), and the app stays open while it does.
 */
class ToshiakiAppIconInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var originalIcon = AppIconVariant.Current

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        originalIcon = runBlocking { app.appStateStore.appIconVariant.first() }
        switchProfile(ExperienceProfile.TOSHIAKI_COMPAT)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.appStateStore.setAppIconVariant(originalIcon) }
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

    @Test fun choosingTheClassicIconInToshiakiSwitchesTheLauncherAliasWithoutClosingTheApp() {
        runBlocking { app.appStateStore.setAppIconVariant(AppIconVariant.Current) }
        rule.waitUntil(30_000) { rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithTag("compat-settings-list-root").performScrollToNode(hasText("アプリアイコン"))
        rule.onNodeWithTag("compat-setting-appIcon").performClick()
        rule.onNodeWithTag("compat-app-icon-Classic").assertIsDisplayed().performClick()

        val manager = AndroidLauncherAliasManager(app)
        rule.waitUntil(20_000) { manager.enabledAliases() == setOf(AndroidLauncherAliasManager.CLASSIC_ALIAS) }
        // The Activity is still alive and showing the dialog.
        assertNotEquals(Lifecycle.State.DESTROYED, rule.activityRule.scenario.state)
        rule.onNodeWithTag("compat-app-icon-Current").assertIsDisplayed().performClick()
        rule.waitUntil(20_000) { manager.enabledAliases() == setOf(AndroidLauncherAliasManager.CURRENT_ALIAS) }
        assertEquals(AppIconVariant.Current, runBlocking { app.appStateStore.appIconVariant.first() })
        assertNotEquals(Lifecycle.State.DESTROYED, rule.activityRule.scenario.state)
    }
}
