package com.valoser.futacha.shared.ui.futaber

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** L-B11: what the viewer's source must keep (the shared viewer page gets only a default-valued argument; the new controls are wired). */
class FutaberViewerSourceContractTest {
    private fun text(path: String) = File("src/$path").readText()

    @Test fun theSharedViewerPageOnlyGotADefaultValuedArgument() {
        val page = text("commonMain/kotlin/ui/compat/CompatViewerScreen.kt")
        assertTrue("onDoubleTapZoom: ((zoomedIn: Boolean) -> Unit)? = null" in page, "the double-tap zoom is opt-in")
    }

    @Test fun theViewerRoutesItsControlsAndGuardsTheLongPress() {
        val viewer = text("commonMain/kotlin/ui/futaber/FutaberViewerScreen.kt")
        listOf("onSharePressed()", "onSavePressed()", "longPressWhereTheFingerStayed()", "onDoubleTapZoom", "LocalVideoPlaybackOptions", "barTaps")
            .forEach { assertTrue(it in viewer, "the viewer uses $it") }
        assertTrue("saveCurrent(true) }" !in viewer.substringAfter("futaber-viewer-share"), "the share button goes through onSharePressed")
    }

    @Test fun theOverlayHoldsTheOrientationAndTheThreadShowsTheBubble() {
        assertTrue("ApplyFutaberViewerOrientation(" in text("commonMain/kotlin/ui/futaber/FutaberMediaOverlay.kt"))
        assertTrue("FutaberQuoteKind.FromViewer" in text("commonMain/kotlin/ui/futaber/FutaberThread.kt"))
    }

    @Test fun theHostLocksTheOrientationOnlyOnRequest() {
        val host = File("../iosApp/iosApp/ContentView.swift").readText()
        assertTrue("futaber-orientation-lock" in host && "supportedInterfaceOrientations" in host)
    }
}
