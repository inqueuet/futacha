package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

class FtutterToolbarEditorTest {
    @Test fun movingKeepsEverythingAndIgnoresOutOfRange() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.ftutterMoved(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), list.ftutterMoved(3, 1))
        assertEquals(list, list.ftutterMoved(-1, 2))
        assertEquals(list, list.ftutterMoved(1, 4))
    }

    @Test fun theImagePostsRowUsesTheOriginalsName() {
        assertEquals("画像レス抽出 / 長押しで画像一覧", ftutterThreadToolEditorLabel(FtutterThreadTool.IMAGE_POSTS))
        assertEquals("更新", ftutterThreadToolEditorLabel(FtutterThreadTool.REFRESH))
    }
}
