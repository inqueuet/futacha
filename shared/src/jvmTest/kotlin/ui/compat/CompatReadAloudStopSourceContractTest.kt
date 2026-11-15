package com.valoser.futacha.shared.ui.compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** としあき(仮)：「停止」の後の「読み上げ」は画面のレスから（ふたちゃスレ 1447418590、2026-11-13）。 */
class CompatReadAloudStopSourceContractTest {
    private val source = File("src/commonMain/kotlin/ui/compat/CompatibilityApp.kt").readText()

    @Test
    fun everyUserStopForgetsTheStoppedReply() {
        assertTrue("TextButton(onClick = { stopReadAloudByUser() }) { Text(\"停止\") }" in source)
        assertTrue("onDismiss = { stopReadAloudByUser() }" in source, "the dialog's 「停止して閉じる」")
        assertTrue("stopReadAloudByUser(\"読み上げを停止しました\")" in source)
        assertTrue("readAloudCursor = compatReadAloudCursorAfterStop()" in source)
        assertTrue("stopReadAloud = ::stopReadAloudByUser," in source, "the toolbar's 読み上げ toggle")
        assertFalse("TextButton(onClick = { stopReadAloud() }) { Text(\"停止\") }" in source)
    }
}
