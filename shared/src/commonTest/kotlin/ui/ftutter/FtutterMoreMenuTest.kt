package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtutterMoreMenuTest {
    @Test
    fun theOriginalsMenusAreTheDefaultAndTheLegacySwitchIsOff() {
        assertFalse(ftutterLegacyMenus(emptyMap()))
        assertTrue(ftutterLegacyMenus(mapOf("compat.ftutter.legacyMenus" to "true")))
        assertEquals("その他の機能", FTUTTER_MORE_LABEL)
    }

    @Test
    fun theSettingsTopUsesTheOriginalsNamesInItsOrder() {
        assertEquals(
            listOf("デザイン", "カタログ", "スレッド", "タブ・ドロワー", "画像・動画ビューワー", "ピックアップ・NG管理", "ネットワーク・書き込み",
                "バックアップ・復元", "統計情報"),
            FtutterSettingsPage.entries.map { it.title }
        )
        assertEquals("二画面モード", FTUTTER_SETTINGS_TABLET_TITLE)
    }

    @Test
    fun theDrawersRestoreButtonTellsTheRemainingCountWithoutText() {
        assertEquals("閉じたタブを戻す（残り2件）", ftutterRestoreClosedLabel(2))
    }
}
