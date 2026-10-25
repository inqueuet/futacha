@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.compat.COMPAT_DRAWING_RECOVERY_PATH
import com.valoser.futacha.shared.ui.compat.CompatDrawingRecovery
import com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Removes the kept drawing (L69) through its queue, so a drawing screen a test opens on the app's real files is not asked
 * about a drawing another test left.
 */
internal fun clearCompatDrawingRecovery(context: Context) {
    runBlocking {
        val recovery = CompatDrawingRecovery(createFileSystem(context))
        recovery.discard()
        // Read after the delete (and any write handed over before it) has been done.
        recovery.load()
    }
}

/** L69: a drawing lost with the screen is offered back the next time; one left or saved on purpose is not. Real files. */
class CompatDrawingRecoveryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var fileSystem: FileSystem
    private val shown = mutableStateOf(true)
    private val saved = CopyOnWriteArrayList<ImageData>()

    @Before fun prepare() {
        fileSystem = createFileSystem(rule.activity)
        clearCompatDrawingRecovery(rule.activity)
        rule.setContent {
            MaterialTheme {
                if (shown.value) CompatPostDrawingScreen(
                    onSaved = { saved += it; shown.value = false },
                    onBack = { shown.value = false },
                    forceLandscape = false,
                    recoveryFileSystem = fileSystem
                )
            }
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        clearCompatDrawingRecovery(rule.activity)
    }

    private fun kept(): Boolean = runBlocking { fileSystem.exists(COMPAT_DRAWING_RECOVERY_PATH) }

    private fun waitForCanvas() {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun stroke(index: Int) {
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            val y = height * (0.25f + index * 0.2f)
            swipe(Offset(width * 0.2f, y), Offset(width * 0.7f, y), 150)
        }
    }

    /** The check of a kept drawing is over (the palette, like drawing, waits for it) with nothing asked. */
    private fun waitForTheCheckWithNothingOffered() {
        waitForCanvas()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("パレット") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithTag("compat-drawing-recovery").assertCountEquals(0)
    }

    private fun drawThreeStrokesOnceTheCheckIsOver() {
        waitForTheCheckWithNothingOffered()
        repeat(3) { stroke(it) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()
    }

    private fun undoCount(): Int {
        var count = 0
        while (rule.onAllNodes(hasContentDescription("元に戻す") and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("元に戻す").performClick()
            rule.waitForIdle()
            count++
        }
        return count
    }

    /** The screen goes away without its 戻る (as the app or the mode closing it would), then a new one opens. */
    private fun removeAndReopen() {
        rule.runOnIdle { shown.value = false }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle { shown.value = true }
        waitForCanvas()
    }

    private fun waitForTheOffer() {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-recovery").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aDrawingLostWithTheScreenIsOfferedBackAndRestored() {
        drawThreeStrokesOnceTheCheckIsOver()
        // Kept 500 ms after the last change, before any closing (an app ended by the system closes nothing).
        rule.mainClock.advanceTimeBy(700)
        rule.waitUntil(5_000) { kept() }
        removeAndReopen()
        waitForTheOffer()
        rule.onNodeWithText("前回の手書き（3筆・", substring = true).assertIsDisplayed()
        // Nothing is drawn while the question is open.
        rule.onNodeWithContentDescription("元に戻す").assertIsNotEnabled()
        rule.onNodeWithText("復元する").performClick()
        rule.onAllNodesWithTag("compat-drawing-recovery").assertCountEquals(0)
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()
        // Leaving on purpose (戻る → 送信画面に戻る) removes it: the next screen asks nothing.
        rule.onNodeWithText("戻る").performClick()
        rule.onNodeWithText("送信画面に戻る").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(5_000) { !kept() }
        rule.runOnIdle { shown.value = true }
        drawThreeStrokesOnceTheCheckIsOver()
        rule.onAllNodesWithTag("compat-drawing-recovery").assertCountEquals(0)
        assertEquals(3, undoCount())
    }

    @Test fun aDrawingRemovedWithTheScreenIsKeptEvenBeforeTheDelay() {
        drawThreeStrokesOnceTheCheckIsOver()
        // Removed at once: the screen keeps it as it goes.
        removeAndReopen()
        waitForTheOffer()
        rule.onNodeWithText("復元する").performClick()
        assertEquals(3, undoCount())
    }

    @Test fun aDiscardedDrawingIsNotOfferedAgain() {
        drawThreeStrokesOnceTheCheckIsOver()
        removeAndReopen()
        waitForTheOffer()
        rule.onNodeWithText("破棄する").performClick()
        rule.onAllNodesWithTag("compat-drawing-recovery").assertCountEquals(0)
        rule.onNodeWithContentDescription("元に戻す").assertIsNotEnabled()
        rule.waitUntil(5_000) { !kept() }
        // An untouched canvas leaves nothing to offer.
        removeAndReopen()
        drawThreeStrokesOnceTheCheckIsOver()
        rule.onAllNodesWithTag("compat-drawing-recovery").assertCountEquals(0)
    }

    @Test fun aSavedDrawingIsNotOfferedAgain() {
        drawThreeStrokesOnceTheCheckIsOver()
        rule.mainClock.advanceTimeBy(700)
        rule.waitUntil(5_000) { kept() }
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.waitUntil(10_000) { saved.isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(5_000) { !kept() }
        rule.runOnIdle { shown.value = true }
        drawThreeStrokesOnceTheCheckIsOver()
        rule.onAllNodesWithTag("compat-drawing-recovery").assertCountEquals(0)
        assertTrue(saved.single().isHandwriting)
    }

    @Test fun backOnTheQuestionLeavesTheScreenAndKeepsTheDrawingForNextTime() {
        drawThreeStrokesOnceTheCheckIsOver()
        removeAndReopen()
        waitForTheOffer()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
        assertFalse(shown.value)
        assertTrue(kept())
        rule.runOnIdle { shown.value = true }
        waitForTheOffer()
        rule.onNodeWithText("復元する").performClick()
        assertEquals(3, undoCount())
    }
}
