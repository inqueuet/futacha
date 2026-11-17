package com.valoser.futacha.shared.ui.futaber

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * L-B10: what the screens leave to the system. iOS's edge swipe closes neither the board list (S27) nor the manage
 * panel (G36) — Android's Back still does both (look plan §1.6) — and the catalog's menus are futaber's popovers (V29),
 * not Material's drop-down menu. The panel's dates are this mode's own form (V35), the shared one stays as it is.
 */
class FutaberPanelDrawerSourceTest {
    private fun source(name: String) = File("src/commonMain/kotlin/ui/futaber/$name").readText()

    @Test
    fun iosEdgeSwipesLeaveTheBoardListAndThePanelOpenAndAndroidBackStillClosesThem() {
        val app = source("FutaberApp.kt")
        assertTrue("if (drawerOpen) PlatformBackHandler(enabled = true, iosEdgeGestureEnabled = false) { drawerOpen = false }" in app)
        val panel = source("FutaberManagePanel.kt")
        assertTrue("FutaberBackHandler(onBack = onClose, iosEdgeGestureEnabled = false)" in panel)
        assertTrue("PlatformBackHandler(onBack = onClose)" !in panel)
        // The edge flag is iOS's only: the Android Back callback is registered whatever it is.
        val android = File("src/androidMain/kotlin/ui/util").walkTopDown().filter { it.isFile && it.name.startsWith("PlatformBackHandler") }
            .map { it.readText() }.joinToString("\n")
        assertTrue("BackHandler" in android && "iosEdgeGestureEnabled" in android, "the Android handler takes the flag and ignores it")
    }

    @Test
    fun theCatalogMenusArePopoversAndThePanelDatesAreThisModesOwn() {
        val catalog = source("FutaberCatalog.kt")
        assertTrue("DropdownMenu" !in catalog)
        assertTrue("FutaberPopover(" in catalog)
        assertTrue("formatLastVisited" !in source("FutaberManagePanel.kt"))
        assertTrue("futaberPanelDate(" in source("FutaberManagePanel.kt"))
        // The board list's dialogs are futaber's alerts; Material's AlertDialog is gone from it.
        val drawer = source("FutaberBoardDrawer.kt")
        assertTrue("AlertDialog" !in drawer)
        assertTrue("FutaberAlert(" in drawer)
    }
}
