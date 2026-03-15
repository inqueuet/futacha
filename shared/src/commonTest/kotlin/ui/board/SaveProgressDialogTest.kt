package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveProgressDialogTest {
    @Test
    fun isSaveProgressCompleted_requiresFinalizingAndFinishedCounts() {
        assertFalse(isSaveProgressCompleted(null))
        assertFalse(
            isSaveProgressCompleted(
                SaveProgress(
                    phase = SavePhase.DOWNLOADING,
                    current = 10,
                    total = 10,
                    currentItem = "item"
                )
            )
        )
        assertFalse(
            isSaveProgressCompleted(
                SaveProgress(
                    phase = SavePhase.FINALIZING,
                    current = 0,
                    total = 1,
                    currentItem = "item"
                )
            )
        )
        assertTrue(
            isSaveProgressCompleted(
                SaveProgress(
                    phase = SavePhase.FINALIZING,
                    current = 1,
                    total = 1,
                    currentItem = "item"
                )
            )
        )
    }

    @Test
    fun resolveSaveProgressDismissAction_matchesDialogRules() {
        val completed = SaveProgress(
            phase = SavePhase.FINALIZING,
            current = 1,
            total = 1,
            currentItem = "item"
        )
        val inProgress = SaveProgress(
            phase = SavePhase.DOWNLOADING,
            current = 1,
            total = 2,
            currentItem = "item"
        )

        assertEquals(
            SaveProgressDismissAction.Dismiss,
            resolveSaveProgressDismissAction(completed, hasCancelRequest = true)
        )
        assertEquals(
            SaveProgressDismissAction.Cancel,
            resolveSaveProgressDismissAction(inProgress, hasCancelRequest = true)
        )
        assertEquals(
            SaveProgressDismissAction.None,
            resolveSaveProgressDismissAction(inProgress, hasCancelRequest = false)
        )
    }

    @Test
    fun saveProgressPhaseTitle_matchesUiCopy() {
        assertEquals("準備中", saveProgressPhaseTitle(SavePhase.PREPARING))
        assertEquals("ダウンロード中", saveProgressPhaseTitle(SavePhase.DOWNLOADING))
        assertEquals("変換中", saveProgressPhaseTitle(SavePhase.CONVERTING))
        assertEquals("完了処理中", saveProgressPhaseTitle(SavePhase.FINALIZING))
    }
}
