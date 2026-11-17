package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L-B6 (V34): the operation menu's labels are not broken by hand any more (`futaberBalancedLabel` broke them at a word's
 * end): the label is drawn as it is in a box of the original's 50 width, and the text breaks by that width, character
 * by character for Japanese ("画像を小さ／くする"). The labels the items turn to after a press (M10・M17) are futaber's.
 */
class FutaberMenuLabelTest {
    private fun item(id: String, label: String = id) = FutaberMenuItem(id, label, Icons.Outlined.Close) {}

    @Test
    fun noLabelCarriesALineBreakPutInByHand() {
        val labels = listOf(
            "URLをコピーする", futaberSmallImagesLabel(false), futaberSmallImagesLabel(true), "返信が多いレスを表示", "読み上げを開始",
            "URLスキームアクション", "お気に入りに追加", "MHTで保存", "NG編集", futaberNgToggleLabel(false), futaberNgToggleLabel(true),
            "新着へスクロール"
        )
        labels.forEach { assertFalse('\n' in it, it) }
        val extras = futaberOperationMenuExtras(
            close = {}, liveReadAloudEnabled = true, onLiveReadAloud = {}, saveEnabled = true, onSave = {}, onShareUrl = {},
            onOpenBrowser = {}, undoReloadEnabled = true, onUndoReload = {}, isTabbed = false, tabEnabled = true, onToggleTab = {},
            onWrite = {}, extract = FutaberMenuToggle(false) {}, autoScroll = FutaberMenuToggle(true) {}, onArchive = {},
            onCacheSearch = {}, ngDetail = FutaberNgDetailEntry(true) {}, scrollItems = listOf(item("page-up", "1ページ上へ")),
            onImageEdit = {}, onVideoEdit = {}
        )
        extras.forEach { assertFalse('\n' in it.label, it.label) }
    }

    @Test
    fun fiveFullWidthCharactersFitTheLabelsWidthAndSixDoNot() {
        // The label box is 50 wide (futaber: 50×30); a full-width character is one em of the label's size. Five fit
        // ("画像を小さ"), the sixth goes to the next line, as in the original.
        assertTrue(FUTABER_MENU_LABEL_SP * 5 <= 50f)
        assertTrue(FUTABER_MENU_LABEL_SP * 6 > 50f)
    }

    @Test
    fun theLabelsTurnAsTheOriginalsDo() {
        assertEquals("画像を小さくする", futaberSmallImagesLabel(false))
        assertEquals("画像を大きくする", futaberSmallImagesLabel(true))
        assertEquals("NGを無効にする", futaberNgToggleLabel(false))
        assertEquals("NGを有効にする", futaberNgToggleLabel(true))
    }
}
