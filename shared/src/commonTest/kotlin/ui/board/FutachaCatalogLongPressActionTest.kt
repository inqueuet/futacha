package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class FutachaCatalogLongPressActionTest {
    @Test
    fun mapsEverySavedValueAsBefore() {
        assertEquals(FutachaCatalogLongPress.MENU, futachaCatalogLongPressAction(null))
        assertEquals(FutachaCatalogLongPress.MENU, futachaCatalogLongPressAction("unknown"))
        assertEquals(FutachaCatalogLongPress.NONE, futachaCatalogLongPressAction("何もしない"))
        assertEquals(FutachaCatalogLongPress.NG, futachaCatalogLongPressAction("ng"))
        assertEquals(FutachaCatalogLongPress.ADD_TAB, futachaCatalogLongPressAction("タブに追加する"))
        assertEquals(FutachaCatalogLongPress.DEL, futachaCatalogLongPressAction("del"))
    }
}
