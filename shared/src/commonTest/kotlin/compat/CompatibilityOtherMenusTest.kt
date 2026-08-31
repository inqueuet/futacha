package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityOtherMenusTest {
    @Test
    fun catalogRoot_hasExactTargetOrderAndOnlyDefersCacheBackend() {
        val items = compatCatalogOtherMenu(
            route = CompatOtherMenuRoute.CATALOG_ROOT,
            ngEnabled = true,
            replyPriorityEnabled = false,
            showNonPriority = false,
            canUndoClose = false
        )

        assertEquals(
            listOf(
                "監視ワード", "NG管理", "巡回検索", "過去スレ検索", "外部アプリ",
                "表示の切り替え", "一番上に行く", "プライバシー", "通信の軽量化",
                "更新の確認", "閉じたスレを元に戻す"
            ),
            items.map(CompatOtherMenuItem::label)
        )
        assertFalse(items.first { it.key == "cache" }.deferredExternalBackend)
        assertTrue(items.first { it.key == "bypass" }.deferredExternalBackend)
        assertFalse(items.first { it.key == "undo" }.enabled)
        assertEquals(null, items.first { it.key == "extract" }.childRoute)

        val extract = compatCatalogOtherMenu(
            CompatOtherMenuRoute.CATALOG_EXTRACT,
            ngEnabled = true,
            replyPriorityEnabled = false,
            showNonPriority = false,
            canUndoClose = false
        )
        assertEquals(listOf("検索", "新規追加", "全て削除"), extract.map { it.label })
    }

    @Test
    fun catalogNgSubmenu_flipsAllDynamicLabels() {
        val before = compatCatalogOtherMenu(
            CompatOtherMenuRoute.CATALOG_NG,
            ngEnabled = true,
            replyPriorityEnabled = false,
            showNonPriority = false,
            canUndoClose = false
        )
        val after = compatCatalogOtherMenu(
            CompatOtherMenuRoute.CATALOG_NG,
            ngEnabled = false,
            replyPriorityEnabled = true,
            showNonPriority = true,
            canUndoClose = false
        )

        assertEquals(listOf("NGスレッド", "NGワード", "NG画像"), before.take(3).map { it.label })
        assertEquals("無効にする", before.first { it.key == "ng_toggle" }.label)
        assertEquals("レス数優先を有効にする", before.first { it.key == "reply_priority_toggle" }.label)
        assertEquals("レス数非優先を表示する", before.first { it.key == "non_priority_toggle" }.label)
        assertEquals("有効にする", after.first { it.key == "ng_toggle" }.label)
        assertEquals("レス数優先を無効にする", after.first { it.key == "reply_priority_toggle" }.label)
        assertEquals("レス数非優先を隠す", after.first { it.key == "non_priority_toggle" }.label)
    }

    @Test
    fun threadNestedMenus_matchSaveNgUrlAndExtractionContracts() {
        val save = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_SAVE, true, false, 0)
        val ng = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_NG, true, false, 0)
        val url = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_URL, true, false, 0)
        val extract = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_EXTRACT, true, false, 12)

        assertEquals(
            listOf(
                "HTML",
                "HTMLとサムネイル",
                "HTMLと全ての画像",
                "メディアのみ(ZIP)",
                "メディアのみ(フォルダ)"
            ),
            save.map { it.label }
        )
        assertTrue(save.first().enabled)
        assertEquals(listOf("NGヘッダー", "NGワード", "NG画像", "無効にする"), ng.map { it.label })
        assertEquals(7, url.size)
        assertEquals("ブラウザ", url.first().label)
        assertEquals("ふたポで開く", url.last().label)
        assertEquals(8, extract.size)
        assertEquals("NG非表示のレス", extract.last().label)

        val root = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_ROOT, true, false, 0)
        assertEquals("過去ログ検索", root.first { it.key == "cache" }.label)
        assertEquals("1ページ上へ", root.first { it.key == "page_up" }.label)
    }

    @Test
    fun rootMenusHideCommandsAlreadyPresentOnTheToolbar() {
        val catalog = compatCatalogOtherMenu(
            CompatOtherMenuRoute.CATALOG_ROOT,
            ngEnabled = true,
            replyPriorityEnabled = false,
            showNonPriority = false,
            canUndoClose = false,
            activeToolbarKeys = setOf("privacy", "check")
        )
        assertFalse(catalog.any { it.key == "privacy" || it.key == "check" })
        assertTrue(catalog.any { it.key == "ng" })

        val thread = compatThreadOtherMenu(
            CompatOtherMenuRoute.THREAD_ROOT,
            ngEnabled = true,
            canUndoClose = false,
            ngCount = 0,
            activeToolbarKeys = setOf("top", "page_down", "search", "close")
        )
        assertFalse(thread.any { it.key in setOf("top", "page_down", "search", "close") })
        assertTrue(thread.any { it.key == "save" })
    }
}
