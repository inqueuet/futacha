package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtutterPostNoticeTest {
    /** L15: the imported attachment notice points to the attach button only where one exists. */
    @Test fun theImportedAttachmentNoticeFollowsWhetherThisPostCanAttach() {
        val attachable = ftutterSourceAttachmentNotice("old.png", canAttach = true)
        assertTrue("old.png" in attachable && "添付ボタンから選び直してください" in attachable, attachable)
        val imgReply = ftutterSourceAttachmentNotice("old.png", canAttach = false)
        assertTrue("old.png" in imgReply && "使われません" in imgReply, imgReply)
        assertFalse("添付ボタン" in imgReply, imgReply)
    }

    /** L22: a handwriting copy failure joins the dialog's own errors. */
    @Test fun aHandwritingCopyFailureIsListedWithTheOtherErrors() {
        assertEquals(listOf("サイズ", "手書き", "キー"), ftutterPostErrors("サイズ", "手書き", "キー"))
        assertEquals(listOf("手書き"), ftutterPostErrors(null, "手書き", null))
    }
}
