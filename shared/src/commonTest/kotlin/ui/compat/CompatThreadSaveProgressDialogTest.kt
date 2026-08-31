package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatThreadSaveProgressDialogTest {
    @Test
    fun cancelRequestUsesTheFinalApkProgressMessage() {
        val progress = SaveProgress(
            phase = SavePhase.DOWNLOADING,
            current = 4,
            total = 10,
            currentItem = "画像を保存中"
        )

        assertEquals("画像を保存中", compatThreadSaveProgressItem(progress, cancelRequested = false))
        assertEquals("中断しています…", compatThreadSaveProgressItem(progress, cancelRequested = true))
        assertEquals(
            "しばらくお待ち下さい",
            compatThreadSaveProgressItem(progress.copy(currentItem = ""), cancelRequested = false)
        )
    }

    @Test
    fun completionAndCancellationRetainTheFinalApkFormattedCounts() {
        val partial = SavedThread(
            threadId = "123",
            boardId = "img",
            boardName = "二次元裏",
            title = "thread",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 3,
            imageCount = 2,
            videoCount = 0,
            totalSize = 10L,
            status = SaveStatus.PARTIAL,
            incompleteMediaCount = 4
        )

        assertEquals("保存しました\n4件のメディアを取得できませんでした", compatThreadSaveCompletionMessage(partial))
        assertEquals("キャンセルしました", compatThreadSaveCancellationMessage())
        assertEquals(
            "キャンセルしました\n3件のメディアをここまで保存しました",
            compatThreadSaveCancellationMessage(partiallySavedCount = 3)
        )
    }
}
