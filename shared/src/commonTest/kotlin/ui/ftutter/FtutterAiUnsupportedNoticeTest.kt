package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L120: an AI command answered in ふたったー風 names this mode, not ふたばー風. */
class FtutterAiUnsupportedNoticeTest {
    @Test fun theNoticeNamesThisMode() {
        val notice = ftutterAiUnsupportedNotice(FutachaAiCommand(FutachaAiAction.StartThreadReadAloud))
        assertTrue("ふたったー風モード" in notice, notice)
        assertFalse("ふたばー風" in notice, notice)
        assertTrue(FutachaAiAction.StartThreadReadAloud.label in notice, notice)
    }
}
