package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatibilitySelectorActionsTest {
    @Test
    fun catalogContextHasTheExactTwoReferenceChoices() {
        assertEquals(
            listOf("更新の確認", "スレを閉じる"),
            compatSelectorContextChoices(threadContext = false, isCurrentTab = false)
        )
    }

    @Test
    fun updateCheckNeverFallsThroughToCatalogOrThreadReload() {
        assertEquals(
            CompatSelectorActionEffect.CHECK_UPDATES,
            resolveCompatSelectorLongTapEffect("更新の確認", threadContext = false, isCurrentTab = false)
        )
        assertEquals(
            CompatSelectorActionEffect.CHECK_UPDATES,
            resolveCompatSelectorLongTapEffect("更新の確認", threadContext = true, isCurrentTab = true)
        )
        assertEquals(
            CompatSelectorActionEffect.CHECK_UPDATES,
            resolveCompatSelectorMenuEffect("更新の確認")
        )
        assertEquals(
            CompatSelectorActionEffect.RELOAD_CURRENT,
            resolveCompatSelectorMenuEffect("再読み込み")
        )
    }

    @Test
    fun currentOnlyActionsSelectANonCurrentTabBeforeActing() {
        assertEquals(
            CompatSelectorActionEffect.SELECT_TAB,
            resolveCompatSelectorLongTapEffect("再読み込み", threadContext = true, isCurrentTab = false)
        )
        assertEquals(
            CompatSelectorActionEffect.SELECT_TAB,
            resolveCompatSelectorLongTapEffect("レスを書き込む", threadContext = true, isCurrentTab = false)
        )
        assertEquals(
            CompatSelectorActionEffect.REPLY_CURRENT,
            resolveCompatSelectorLongTapEffect("レスを書き込む", threadContext = true, isCurrentTab = true)
        )
    }
}
