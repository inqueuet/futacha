package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.service.ThreadSaveAlreadyRunningException
import kotlin.test.Test
import kotlin.test.assertEquals

/** L142: a save refused because another runs is told as such, not as a failure. */
class CompatSaveBusyMessageTest {
    @Test fun anotherSaveRunningIsToldAsSuch() {
        assertEquals("別の保存を実行中です", ThreadSaveAlreadyRunningException().toCompatUserMessage("保存できませんでした"))
        assertEquals("別の保存を実行中です", ThreadSaveAlreadyRunningException().toCompatUserMessage("画像を保存できませんでした"))
    }
}
