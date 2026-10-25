package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.ui.board.SaveProgressDismissAction
import com.valoser.futacha.shared.ui.board.isSaveProgressCompleted
import com.valoser.futacha.shared.ui.board.resolveSaveProgressDismissAction
import kotlin.test.*

class FtutterHtmlSaveProgressTest {
    @Test fun backendCompletionStillAllowsCancellationWhileTheResultIsBeingIndexed() {
        val completed = SaveProgress(SavePhase.FINALIZING, 1, 1, "完了")
        val visible = assertNotNull(ftutterHtmlActiveSaveProgress(completed, zipBuilding = false))
        assertFalse(isSaveProgressCompleted(visible))
        assertTrue(visible.getOverallProgressPercentage() < 100)
        assertEquals(SaveProgressDismissAction.Cancel, resolveSaveProgressDismissAction(visible, true))
    }
    @Test fun zipCreationHasItsOwnUnfinishedPhaseEvenIfBackendProgressHasNotArrived() {
        val downloading = SaveProgress(SavePhase.DOWNLOADING, 1, 3, "image")
        for (source in listOf(null, downloading, SaveProgress(SavePhase.FINALIZING, 1, 1, "完了"))) {
            val visible = assertNotNull(ftutterHtmlActiveSaveProgress(source, zipBuilding = true))
            assertEquals(SavePhase.FINALIZING, visible.phase)
            assertEquals("ZIPを作成しています", visible.currentItem)
            assertFalse(isSaveProgressCompleted(visible))
            assertTrue(visible.getOverallProgressPercentage() < 100)
            assertEquals(SaveProgressDismissAction.Cancel, resolveSaveProgressDismissAction(visible, true))
        }
    }
    @Test fun downloadProgressAndTheInitialEmptyStateKeepTheirExistingValues() {
        val downloading = SaveProgress(SavePhase.DOWNLOADING, 1, 3, "image", 12, 100)
        assertSame(downloading, ftutterHtmlActiveSaveProgress(downloading, zipBuilding = false))
        assertNull(ftutterHtmlActiveSaveProgress(null, zipBuilding = false))
    }
}
