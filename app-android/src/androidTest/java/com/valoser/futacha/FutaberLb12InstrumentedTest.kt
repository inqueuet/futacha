package com.valoser.futacha

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** L-B12 in the real app (the tutorial board only): the URL scheme page, the quote pitch, the wallpaper settings. */
class FutaberLb12InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private val keys = listOf(
        "compat.futaber.theme", "compat.futaber.lastBoardId", "compat.futaber.seen", "compat.futaber.tabs", "compat.futaber.manageCategory",
        "compat.futaber.smallImages", "compat.futaber.imageRatio", "compat.futaber.tapScroll", "compat.futaber.googleLens",
        "compat.futaber.instantCopy", "compat.futaber.legacySearchFilter", "compat.futaber.legacyManyRepliesFilter",
        "compat.futaber.legacySubjectField", "compat.speech.pitch", "compat.speech.rate", "compat.futaber.urlSchemeBrowser", "compat.futaber.urlSchemeAction", "compat.futaber.quotePitch",
        "compat.futaber.wallpaper.catalog.opacity", "compat.futaber.wallpaper.thread.opacity", "compat.futaber.wallpaper.post.opacity", "compat.futaber.wallpaper.menu.opacity"
    )
    private var savedRules: String? = null
    private var savedWords: List<String> = emptyList()
    private var savedHeaders: List<String> = emptyList()

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            savedRules = app.compatibilityStore.loadPreference("compat.watcher.rules")
            savedWords = app.appStateStore.ngWords.first()
            savedHeaders = app.appStateStore.ngHeaders.first()
            app.appStateStore.setNgWords(emptyList())
            app.appStateStore.setNgHeaders(emptyList())
            app.compatibilityStore.savePreferences(keys.associateWith { null } + ("compat.futaber.theme" to "light"))
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.setNgWords(savedWords)
            app.appStateStore.setNgHeaders(savedHeaders)
            app.compatibilityStore.savePreferences(keys.associateWith { null } + ("compat.watcher.rules" to savedRules))
        }
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

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Where a node is in the page, also when it is scrolled out of sight (its bounds are then clipped to nothing). */
    private fun top(tag: String): Float = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun dp(px: Float) = px / rule.activity.resources.displayMetrics.density

    private fun openCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
    }

    private fun openSettings() {
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
    }

    private fun closeSettings() {
        rule.onNodeWithTag("futaber-settings-done").performClick()
        rule.waitUntil(10_000) { absent("futaber-settings") }
    }

    @Test fun theUrlSchemePageSavesBothSchemesAndSitsFifthInTheGeneralGroup() {
        openCatalog()
        openSettings()
        rule.waitForIdle()
        val tops = listOf("row-viewer", "row-url-scheme", "row-font-size").map { top("futaber-settings-$it") }
        assertEquals("the URL scheme row is between the viewer and the font size", tops.sorted(), tops)
        rule.onNodeWithTag("futaber-settings-row-url-scheme").performClick()
        waitForTag("futaber-url-scheme-browser")
        rule.onNodeWithTag("futaber-url-scheme-browser").performTextInput("googlechromes://[url]")
        rule.onNodeWithTag("futaber-url-scheme-action").performScrollTo().performTextInput("goodreader://download?url=[text]")
        rule.waitUntil(10_000) { preference("compat.futaber.urlSchemeBrowser") == "googlechromes://[url]" }
        rule.waitUntil(10_000) { preference("compat.futaber.urlSchemeAction") == "goodreader://download?url=[text]" }
    }

    @Test fun theQuotePitchStartsAtHalfAndSteps() {
        openCatalog()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-thread").performClick()
        waitForTag("futaber-settings-row-speech-quote-pitch")
        rule.onNodeWithTag("futaber-settings-row-speech-quote-pitch").performScrollTo()
        rule.onNodeWithTag("futaber-settings-plus-speech-quote-pitch").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.quotePitch") == "6" }
        // 0.5 is the lowest (C24): stepping back lands on it again and no lower.
        rule.onNodeWithTag("futaber-settings-minus-speech-quote-pitch").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.quotePitch") == "5" }
        rule.onNodeWithTag("futaber-settings-minus-speech-quote-pitch").assertIsNotEnabled()
    }

    @Test fun theWallpaperSettingsStepTheOpacityFromFutabersDefaults() {
        openCatalog()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-theme").performClick()
        waitForTag("futaber-wallpaper-catalog-opacity-value")
        rule.onNodeWithTag("futaber-wallpaper-catalog-opacity-value").assertTextContains("0.10", substring = true)
        rule.onNodeWithTag("futaber-wallpaper-catalog-plus").performScrollTo().performClick()
        rule.onNodeWithTag("futaber-wallpaper-catalog-opacity-value").assertTextContains("0.15", substring = true)
        rule.onNodeWithTag("futaber-wallpaper-menu-opacity-value").performScrollTo().assertTextContains("0.65", substring = true)
    }
}
