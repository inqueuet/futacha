@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.ThreadImageSearchDialog
import com.valoser.futacha.shared.ui.compat.COMPAT_CUSTOM_IMAGE_SEARCH_KEY
import com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget
import com.valoser.futacha.shared.ui.ftutter.FtutterBrightness
import com.valoser.futacha.shared.ui.ftutter.FtutterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The same preferred-target API as FtutterApp, with shared settings and no network client. */
class FtutterImageSearchPreferencesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private val database = "ftutter_image_search_preferences_test.db"
    private val preferences = mutableStateOf<Map<String, String>>(emptyMap())
    private var settingsPath: String? = null

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun show(raw: String?, preferred: CompatImageSearchTarget, gif: Boolean = false) {
        preferences.value = raw?.let { mapOf(COMPAT_CUSTOM_IMAGE_SEARCH_KEY to it) }.orEmpty()
        rule.setContent {
            val features = remember {
                FutachaSharedFeatures(store, preferences, null, null, null, null, "test", { settingsPath = it })
            }
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                CompositionLocalProvider(LocalFutachaSharedFeatures provides features) {
                    ThreadImageSearchDialog(
                        imageUrl = "https://search-test.invalid/fixture.${if (gif) "gif" else "png"}",
                        httpClient = null, cookieRepository = null, preferredTarget = preferred,
                        openExternal = { fail("This selection-only test must never open an external URL") },
                        onDismiss = {})
                }
            }
        }
    }

    @Test fun sharedSelectionAndItsChangesWinOverTheLegacyGooglePreference() {
        show("lens.file|iqdb.url", CompatImageSearchTarget.GOOGLE_URL)
        rule.onNodeWithText(CompatImageSearchTarget.LENS_FILE.label).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(CompatImageSearchTarget.IQDB_URL.label).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(CompatImageSearchTarget.GOOGLE_URL.label).assertDoesNotExist()
        rule.runOnIdle { preferences.value = mapOf(COMPAT_CUSTOM_IMAGE_SEARCH_KEY to "tineye.url") }
        rule.onNodeWithText(CompatImageSearchTarget.TINEYE_URL.label).assertIsDisplayed()
        rule.onNodeWithText(CompatImageSearchTarget.LENS_FILE.label).assertDoesNotExist()
        rule.onNodeWithText(CompatImageSearchTarget.IQDB_URL.label).assertDoesNotExist()
    }

    @Test fun ascii2dPreferenceMovesItFirstAndKeepsTheOtherSelectedProviders() {
        show("google.url|ascii2d.url|iqdb.url", CompatImageSearchTarget.ASCII2D_URL)
        val ascii = rule.onNodeWithText(CompatImageSearchTarget.ASCII2D_URL.label).fetchSemanticsNode().boundsInRoot
        val google = rule.onNodeWithText(CompatImageSearchTarget.GOOGLE_URL.label).fetchSemanticsNode().boundsInRoot
        assertTrue("The preferred supported provider should appear first", ascii.top < google.top)
        rule.onNodeWithText(CompatImageSearchTarget.IQDB_URL.label).performScrollTo().assertIsDisplayed()
    }

    @Test fun gifWithAscii2dPreferenceKeepsAllOtherSupportedSearchChoices() {
        show(null, CompatImageSearchTarget.ASCII2D_URL, gif = true)
        rule.onNodeWithText("検索先を設定してください。").assertDoesNotExist()
        rule.onNodeWithText(CompatImageSearchTarget.ASCII2D_URL.label).assertDoesNotExist()
        rule.onNodeWithText(CompatImageSearchTarget.LENS_FILE.label).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(CompatImageSearchTarget.BING_URL.label).performScrollTo().assertIsDisplayed()
    }

    @Test fun explicitlyClearedSharedSelectionStaysEmptyAndSettingsRemainReachable() {
        show("", CompatImageSearchTarget.GOOGLE_URL)
        rule.onNodeWithText("検索先を設定してください。").assertIsDisplayed()
        CompatImageSearchTarget.entries.forEach { rule.onNodeWithText(it.label).assertDoesNotExist() }
        rule.onNodeWithText("検索先を設定").performClick()
        rule.runOnIdle { assertEquals("image_search", settingsPath) }
    }
}
