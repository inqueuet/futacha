@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.ui.futaber.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** L110: the titles of the thread-scoped NG rules are worked out off the main thread, and only for the rules' scopes. */
class FutaberThreadNgTitlesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val threadKey = "thread-scope-key"
    private val calls = CopyOnWriteArrayList<Pair<Boolean, Set<String>>>()

    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} } }

    @Test fun theThreadDetailListWorksOutItsTitlesOffTheMainThread() {
        rule.setContent {
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberSettingsScreen(
                    themeMode = FutaberThemeMode.Light, onThemeModeChange = {},
                    settings = FutaberDisplaySettings(), onSettingChange = { _, _ -> },
                    postSettings = FutaberPostSettings(), deleteKey = "", onDeleteKeyChange = {},
                    onPostSettingsChange = { _, _, _ -> },
                    ngWords = emptyList(), ngHeaders = emptyList(), catalogNgWords = emptyList(),
                    onEditNgWords = {}, onEditNgHeaders = {}, onEditCatalogNgWords = {},
                    threadDetailRules = listOf(
                        CompatNgRule("r1", CompatNgKind.THREAD_POST_NO, threadKey, "77", 1),
                        CompatNgRule("r2", CompatNgKind.THREAD_WORD, "*", "宣伝", 2)
                    ),
                    threadNgScopeTitles = { wanted ->
                        calls += (Looper.myLooper() == Looper.getMainLooper()) to wanted
                        mapOf(threadKey to "あるスレ")
                    },
                    mediaServices = null, onOpenNotifications = {}, onClose = {}
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-settings").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-settings-row-ng").performScrollTo().performClick()
        rule.onNodeWithTag("futaber-settings-row-ng-res-thread-details").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("レス番号　No.77　あるスレ").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Called: $calls", calls.isNotEmpty() && calls.none { it.first })
        assertTrue("Only the rules' own scope is asked for: $calls", calls.all { it.second == setOf(threadKey) })
    }
}
