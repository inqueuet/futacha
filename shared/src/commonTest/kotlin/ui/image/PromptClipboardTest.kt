package com.valoser.futacha.shared.ui.image

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.valoser.futacha.shared.media.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PromptClipboardTest {
    @Test fun standardSelectionAndButtonsCannotWriteAfterOffOrSnapshotDisposal(): Unit = runBlocking {
        var writes = 0
        val underlying = object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = error("Prompt must not read the user's clipboard")
            override suspend fun setClipEntry(clipEntry: ClipEntry?) { writes++ }
        }
        val gate = MediaFeatureGate()
        val enabled = MediaFeatureSettings(promptDisplayEnabled = true)
        gate.update(enabled)
        var active = true
        val clipboard = PromptClipboard(underlying, gate, gate.permit(MediaFeature.PROMPT)!!) { active }
        assertNull(clipboard.getClipEntry())
        clipboard.setClipEntry(null)
        assertEquals(1, writes)
        active = false
        clipboard.setClipEntry(null)
        assertEquals(1, writes)
        active = true
        gate.update(MediaFeatureSettings.Disabled)
        clipboard.setClipEntry(null)
        assertEquals(1, writes)
        gate.update(enabled)
        clipboard.setClipEntry(null)
        assertEquals(1, writes, "Re-enabling must not revive the old selection's copy handler")
    }
}
