@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.ConcurrentLinkedQueue

/** Queue real store writes and gate their displayed flow to reproduce taps made before the UI catches up. */
class FutaberNgMutationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AppStateStore
    private val words = listOf("first-word", "second-word", "keep-word")
    private val headers = listOf("Name:old", "ID:old", "keep-header")
    private val pending = ConcurrentLinkedQueue<suspend () -> Unit>()
    private var releaseDisplay by mutableStateOf(false)
    @Before fun prepare() {
        store = AppStateStore(BaseInMemoryPlatformStateStorage())
        runBlocking { store.setNgWords(words); store.setNgHeaders(headers) }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        pending.clear(); if (::store.isInitialized) store.close()
    }
    // Both overloads keep the same fixture source compatible with the old replacement API and the new mutation API.
    private fun changeWords(value: List<String>) { pending.add { store.setNgWords(value) } }
    private fun changeWords(edit: (List<String>) -> List<String>) { pending.add { store.updateNgWords(edit) } }
    private fun changeHeaders(value: List<String>) { pending.add { store.setNgHeaders(value) } }
    private fun changeHeaders(edit: (List<String>) -> List<String>) { pending.add { store.updateNgHeaders(edit) } }
    private fun open() {
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            val currentWords by store.ngWords.collectAsState(words)
            val currentHeaders by store.ngHeaders.collectAsState(headers)
            FutaberNgDialog(if (releaseDisplay) currentWords else words, if (releaseDisplay) currentHeaders else headers,
                hiddenCount = 0, onChangeWords = ::changeWords, onChangeHeaders = ::changeHeaders, onDismiss = {})
        } }
    }
    private fun commit() {
        runBlocking { while (true) { val next = pending.poll() ?: break; next() } }
        rule.runOnUiThread { releaseDisplay = true }; rule.waitForIdle()
    }
    private fun delete(entry: String) = rule.onNodeWithContentDescription("「$entry」を削除").performClick()
    @Test fun twoDeletesQueuedBeforeAFlowUpdateDoNotResurrectTheFirstWord() {
        open(); delete("first-word"); delete("second-word")
        assertEquals(2, pending.size)
        commit()
        assertEquals(listOf("keep-word"), runBlocking { store.ngWords.first() })
        rule.onNodeWithText("first-word").assertDoesNotExist(); rule.onNodeWithText("second-word").assertDoesNotExist()
        rule.onNodeWithText("keep-word").assertIsDisplayed()
        assertEquals(headers, runBlocking { store.ngHeaders.first() })
    }
    @Test fun anAddFollowedByADeleteKeepsTheCapturedAdditionAfterTheInputWasCleared() {
        open()
        rule.onNodeWithTag("futaber-ng-input").performTextInput("new-word")
        rule.onNodeWithTag("futaber-ng-add").performClick()
        assertEquals("", rule.onNodeWithTag("futaber-ng-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        delete("first-word"); commit()
        assertEquals(listOf("second-word", "keep-word", "new-word"), runBlocking { store.ngWords.first() })
        rule.onNodeWithText("new-word").assertIsDisplayed()
    }
    @Test fun anotherScreensWordUpdateSurvivesQueuedDeletesFromTheOlderDisplay() {
        open(); delete("first-word"); delete("second-word")
        runBlocking { store.updateNgWords { it + "from-other-screen" } }
        commit()
        assertEquals(listOf("keep-word", "from-other-screen"), runBlocking { store.ngWords.first() })
        assertEquals(headers, runBlocking { store.ngHeaders.first() })
    }
    @Test fun nameAndIdMutationsUseTheLatestHeadersWithoutTouchingWordsOrOtherHeaders() {
        open(); rule.onNodeWithTag("futaber-ng-section-Headers").performClick()
        delete("Name:old"); delete("ID:old")
        runBlocking {
            store.updateNgWords { it + "separate-word" }
            store.updateNgHeaders { it + "IP:external" }
        }
        commit()
        assertEquals(listOf("keep-header", "IP:external"), runBlocking { store.ngHeaders.first() })
        assertEquals(words + "separate-word", runBlocking { store.ngWords.first() })
        rule.onNodeWithText("IP:external").assertIsDisplayed()
    }
}
