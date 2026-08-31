package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatThreadSpeechDialogTest {
    @Test
    fun reloadCountdownMatchesTheFinalApkFormattedString() {
        assertEquals("自動リロードまで30秒", compatReadAloudReloadTimer(30))
        assertEquals("自動リロードまで5秒", compatReadAloudReloadTimer(5))
        assertEquals("自動リロードまで0秒", compatReadAloudReloadTimer(-1))
    }
}
