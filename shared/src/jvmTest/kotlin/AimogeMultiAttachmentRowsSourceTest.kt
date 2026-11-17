package com.valoser.futacha.shared.ui.board

import kotlin.test.Test

class AimogeMultiAttachmentRowsSourceTest {
    @Test
    fun everyModesPostRowDrawsTheExtraFiles() {
        // Before: all four rows drew only Post.imageUrl.
        val root = java.io.File("src/commonMain/kotlin/ui")
        listOf(
            "board/ThreadPostComponents.kt",
            "compat/CompatPostRow.kt",
            "futaber/FutaberThread.kt",
            "ftutter/FtutterThread.kt"
        ).forEach { path ->
            val source = java.io.File(root, path).readText()
            kotlin.test.assertTrue(source.contains("PostExtraAttachments("), "$path does not draw the extra files")
        }
    }

}
