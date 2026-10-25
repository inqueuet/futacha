@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * M53: the real settings navigation and LazyColumn, with words/IDs equal to its former chrome keys.
 * Uses only pre-fix product APIs, isolated shared storage, and no repository/client/audio services.
 * Names and IDs share one settings page; the fifth case also covers externally registered rule IDs.
 */
class FutaberSettingsReservedWordsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val state = AppStateStore(BaseInMemoryPlatformStateStorage())
    private val reserved = listOf("hint", "add", "gap")
    private val words = reserved + listOf("chrome:hint", "entry:add", "entry:chrome:gap", "empty")
    private var catalogRules by mutableStateOf(words.mapIndexed { index, word ->
        CompatNgRule(word, CompatNgKind.CATALOG_WORD, "*", word, index.toLong())
    })
    private enum class Target(val tag: String, val title: String) {
        Body("ng-res-words", "レスのNGワード"),
        Headers("ng-res-headers", "レスのNGネーム・ID等"),
        Catalog("ng-catalog-words", "カタログのNGワード"),
        Watch("watch-words", "監視ワード（アラート用）"),
        Rules("ng-catalog-word-rules", "NGワード（詳細）")
    }

    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} } }

    private fun host() {
        runBlocking {
            state.setNgWords(words)
            state.setNgHeaders(words)
            state.setCatalogNgWords(words)
            state.setWatchWords(words)
        }
        rule.setContent {
            val body by state.ngWords.collectAsState(emptyList())
            val headers by state.ngHeaders.collectAsState(emptyList())
            val catalog by state.catalogNgWords.collectAsState(emptyList())
            val watch by state.watchWords.collectAsState(emptyList())
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberSettingsScreen(
                    themeMode = FutaberThemeMode.Light, onThemeModeChange = {},
                    settings = FutaberDisplaySettings(), onSettingChange = { _, _ -> },
                    postSettings = FutaberPostSettings(), deleteKey = "", onDeleteKeyChange = {},
                    onPostSettingsChange = { _, _, _ -> },
                    ngWords = body, ngHeaders = headers, catalogNgWords = catalog,
                    onEditNgWords = { edit -> runBlocking { state.updateNgWords(edit) } },
                    onEditNgHeaders = { edit -> runBlocking { state.updateNgHeaders(edit) } },
                    onEditCatalogNgWords = { edit -> runBlocking { state.updateCatalogNgWords(edit) } },
                    watchWords = watch,
                    onEditWatchWords = { edit -> runBlocking { state.updateWatchWords(edit) } },
                    catalogWordRules = catalogRules,
                    onDeleteNgRule = { id -> catalogRules = catalogRules.filterNot { it.id == id } },
                    mediaServices = null, onOpenNotifications = {}, onClose = {}, stateStore = state
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-settings").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun open(target: Target) {
        if (target != Target.Watch) {
            rule.onNodeWithTag("futaber-settings-row-ng").performScrollTo().performClick()
        }
        rule.onNodeWithTag("futaber-settings-row-${target.tag}").performScrollTo().performClick()
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals(target.title)
        // A duplicate LazyColumn key throws during this actual measure, before any edits can be made.
        rule.waitForIdle()
    }

    private fun rowLabel(target: Target, word: String) = if (target == Target.Rules) "$word　全板" else word

    private fun showRow(target: Target, word: String) {
        val label = rowLabel(target, word)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label, substring = false))
        rule.onNodeWithText(label, substring = false).assertIsDisplayed()
    }

    private fun saved(target: Target): List<String> = runBlocking {
        when (target) {
            Target.Body -> state.ngWords.first()
            Target.Headers -> state.ngHeaders.first()
            Target.Catalog -> state.catalogNgWords.first()
            Target.Watch -> state.watchWords.first()
            Target.Rules -> catalogRules.map { it.id }
        }
    }

    private fun exercise(target: Target) {
        host()
        open(target)
        assertEquals(words, saved(target))
        for (word in words) showRow(target, word)
        var expected = words
        for (word in reserved) {
            showRow(target, word)
            rule.onNodeWithContentDescription("「${rowLabel(target, word)}」を削除").performClick()
            expected = expected - word
            rule.waitUntil(5_000) { saved(target) == expected }
        }
        if (target == Target.Rules) {
            rule.onNodeWithTag("futaber-ng-input").assertDoesNotExist()
        } else {
            for (word in reserved) {
                rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                rule.onNodeWithTag("futaber-ng-input").performTextInput(word)
                rule.onNodeWithTag("futaber-ng-add").performClick()
                expected = expected + word
                rule.waitUntil(5_000) { saved(target) == expected }
                showRow(target, word)
            }
        }
        // Navigation away and back must preserve exact stored values and their order.
        rule.onNodeWithTag("futaber-settings-back").performClick()
        if (target != Target.Watch) rule.onNodeWithTag("futaber-settings-back").performClick()
        open(target)
        for (word in expected) showRow(target, word)
        assertEquals(expected, saved(target))
        // Editing one category never modifies another one.
        for (other in Target.entries.filter { it != target }) assertEquals(words, saved(other))
    }

    @Test fun bodyNgWordsCanOpenRemoveAndReaddReservedWords() = exercise(Target.Body)
    @Test fun nameAndIdNgWordsCanOpenRemoveAndReaddReservedWords() = exercise(Target.Headers)
    @Test fun catalogNgWordsCanOpenRemoveAndReaddReservedWords() = exercise(Target.Catalog)
    @Test fun watchWordsCanOpenRemoveAndReaddReservedWords() = exercise(Target.Watch)
    @Test fun catalogRulesWithReservedIdsCanOpenAndRemoveWithoutChangingOtherIds() = exercise(Target.Rules)
}
