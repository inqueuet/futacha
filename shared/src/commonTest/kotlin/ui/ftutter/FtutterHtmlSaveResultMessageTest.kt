package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.service.MediaSaveDestinationException
import com.valoser.futacha.shared.service.ThreadSaveAlreadyRunningException
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L13: a ZIP that fails or is cancelled after the HTML was saved keeps the saved result and explains itself. */
class FtutterHtmlSaveResultMessageTest {
    private val saved = "保存しました：スレ"

    @Test fun aCompletedSaveListsItsZip() {
        assertEquals("$saved\nZIP: 100.zip", ftutterHtmlSaveResultMessage(saved, "100.zip", null, cancelled = false))
        assertEquals(saved, ftutterHtmlSaveResultMessage(saved, null, null, cancelled = false))
    }

    @Test fun aZipFailureKeepsTheSavedHtmlAndGivesAReadableReason() {
        val requirement = runCatching { require(false) }.exceptionOrNull()!!
        for (failure in listOf(requirement, IOException("File not found: /data/user/0/x/100.zip"), IllegalStateException("EACCES"))) {
            val message = ftutterHtmlSaveResultMessage(saved, null, failure, cancelled = false)
            assertTrue(message.startsWith("$saved\nZIPは作成できませんでした："), message)
            assertFalse("Failed requirement" in message || "File not found" in message || "EACCES" in message, message)
        }
        assertEquals("$saved\nZIPは作成できませんでした：ZIPに含めるファイルが大きすぎます",
            ftutterHtmlSaveResultMessage(saved, null, IllegalArgumentException("ZIPに含めるファイルが大きすぎます"), cancelled = false))
        assertEquals("$saved\nZIPは作成できませんでした：別の保存を実行中です",
            ftutterHtmlSaveResultMessage(saved, null, ThreadSaveAlreadyRunningException(), cancelled = false))
        assertTrue(ftutterHtmlSaveResultMessage(saved, null, MediaSaveDestinationException(IOException("x")), cancelled = false)
            .endsWith("選び直してください。"))
    }

    @Test fun cancellingTheZipSaysTheHtmlIsKeptWhileCancellingBeforeItSaysNothingWasSaved() {
        assertEquals("$saved\nZIPの作成を中止しました（HTMLは保存済みです）", ftutterHtmlSaveResultMessage(saved, null, null, cancelled = true))
        assertEquals("保存を中止しました", ftutterHtmlSaveResultMessage(null, null, null, cancelled = true))
    }
}
