package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals

class FutaberMenuLabelTest {
    @Test
    fun longLabelsBreakWhereTheOriginalAppBreaksThem() {
        assertEquals("URLを\nコピーする", futaberBalancedLabel("URLをコピーする"))
        assertEquals("返信が多い\nレスを表示", futaberBalancedLabel("返信が多いレスを表示"))
        assertEquals("読み上げを\n開始", futaberBalancedLabel("読み上げを開始"))
        assertEquals("新着へ\nスクロール", futaberBalancedLabel("新着へスクロール"))
        assertEquals("お気に入り\nに追加", futaberBalancedLabel("お気に入りに追加"))
        assertEquals("お気に入り\n解除", futaberBalancedLabel("お気に入り解除"))
        assertEquals("スレッドを\n保存", futaberBalancedLabel("スレッドを保存"))
    }

    @Test
    fun shortLabelsStayOnOneLine() {
        listOf("書き込む", "NG編集", "タブに追加", "タブから外す").forEach { assertEquals(it, futaberBalancedLabel(it)) }
    }

    @Test
    fun noLabelLosesACharacterOrBreaksInsideAProtectedWord() {
        listOf(
            "画像を小さくする", "画像を元の大きさにする", "NGを無効にする", "NGを有効に戻す", "読み上げを停止", "お気に入りに追加"
        ).forEach { label ->
            val balanced = futaberBalancedLabel(label)
            assertEquals(label, balanced.replace("\n", ""))
            val lines = balanced.split("\n")
            assertEquals(true, lines.size <= 2, balanced)
            assertEquals(false, lines.size == 2 && ("お気に入り" in label) && !lines[0].endsWith("お気に入り") && lines[0].contains("お気に"), balanced)
        }
    }
}
