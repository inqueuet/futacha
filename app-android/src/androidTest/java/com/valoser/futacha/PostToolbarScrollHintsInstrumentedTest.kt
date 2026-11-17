@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import kotlinx.coroutines.runBlocking
import org.junit.*

/**
 * L150 (rest of M47, decision 2 C): the plain horizontal-scroll tool rows of the Futacha post toolbar and the
 * Futaber post screen show the shared "more" hint when they overflow, and none when everything fits.
 */
class PostToolbarScrollHintsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "post_toolbar_hints_test.db"
    private val width = mutableFloatStateOf(220f)

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun logical(content: @Composable () -> Unit) {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalDensity provides Density(1f), LocalFutachaImageLoader provides loader) {
            Box(Modifier.width(width.floatValue.dp).fillMaxHeight()) { content() }
        } } }
    }

    @Test fun futachaPostToolbarHintsAtItsEndOnlyWhenItOverflows() {
        logical {
            ProvideFutachaSharedFeatures(store, null, FakeBoardRepository(), null, null, "test") {
                FutachaPostToolbar(LocalFutachaSharedFeatures.current!!, "https://may.2chan.net/b/", "本文", {}, "TEST",
                    {}, {}, {}, {}, {}, {}, {}, true,
                    attachmentPickerPreference = AttachmentPickerPreference.ALWAYS_ASK, preferredFileManagerPackage = null)
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("投稿のその他").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("投稿のその他"))
        rule.onNodeWithContentDescription("投稿のその他").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertIsDisplayed()
        rule.runOnIdle { width.floatValue = 2_000f }
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertDoesNotExist()
    }

    /** L33: ふたちゃ's "discard" only closes the post screen (the text stays), so it is named "閉じる" there. */
    @Test fun futachaPostToolbarNamesItsCloseButtonByWhatItDoes() {
        width.floatValue = 2_000f
        var dismissed = 0
        var cleared = 0
        logical {
            ProvideFutachaSharedFeatures(store, null, FakeBoardRepository(), null, null, "test") {
                FutachaPostToolbar(LocalFutachaSharedFeatures.current!!, "https://may.2chan.net/b/", "本文", {}, "TEST",
                    {}, {}, {}, {}, {}, { cleared++ }, { dismissed++ }, true,
                    attachmentPickerPreference = AttachmentPickerPreference.ALWAYS_ASK, preferredFileManagerPackage = null)
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("閉じる").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("内容の破棄").assertDoesNotExist()
        rule.onNodeWithContentDescription("閉じる").performClick()
        rule.runOnIdle {
            Assert.assertEquals(1, dismissed)
            Assert.assertEquals(0, cleared)
        }
    }

    @Test fun futaberPostToolsHintAtTheirEndOnlyWhenTheyOverflow() {
        width.floatValue = 160f
        logical {
            val scope = rememberCoroutineScope()
            val attachment = remember { FutaberAttachment(scope) }
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberPostScreen(FutaberPostTarget.Reply("b", "1", "題名"), "https://may.2chan.net/b/", "", "本文", {}, {}, {},
                    attachment, defaultBoardPostingCapabilities("b"), AttachmentPickerPreference.ALWAYS_ASK, null,
                    FutaberPostSettings(), "", { _, _, _ -> }, {}, { Result.success(null) }, {}, {})
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-clear").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertIsDisplayed()
        rule.runOnIdle { width.floatValue = 2_000f }
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        rule.onNodeWithTag("futaber-post-clear").assertIsDisplayed()
    }
}
