package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.*

class FtutterGestureMenuTest {
    @Test fun everyThreadGestureCanAlsoBeRunFromTheMenu() {
        assertEquals(FtutterGestureAction.entries.filter { it != FtutterGestureAction.NONE }.toSet(), ftutterGestureMenuActions(true).toSet())
        assertEquals("前のタブ", ftutterGestureMenuLabel(FtutterGestureAction.PREVIOUS, true))
    }
    @Test fun catalogMenuDescribesBoardNavigationAndOmitsThreadOnlyFilters() {
        val actions = ftutterGestureMenuActions(false)
        assertFalse(FtutterGestureAction.NONE in actions)
        assertTrue(listOf(FtutterGestureAction.TREE, FtutterGestureAction.POPULAR, FtutterGestureAction.IMAGES).none { it in actions })
        assertEquals("前のお気に入りの板", ftutterGestureMenuLabel(FtutterGestureAction.PREVIOUS, false))
        assertEquals("スレ立て", ftutterGestureMenuLabel(FtutterGestureAction.EDIT, false))
    }
}
