package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatThreadOrigin
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatViewerCaller
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2026-10-13 全体検証 7.6 の1: coming back from the thread's own child screens is not opening the thread. */
class CompatThreadReturnSupportTest {
    private val thread = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG)
    private val viewer = CompatHost.Viewer("tab-a", 0, CompatViewerCaller.THREAD)
    private val gallery = CompatHost.Gallery("tab-a")

    private fun returns(previous: CompatHost?, lastTab: String? = "tab-a", tab: String = "tab-a") =
        compatThreadReturnsFromOwnChild(previous, lastTab, tab)

    @Test
    fun childScreensOfTheSameThreadDoNotCountAsOpening() {
        assertTrue(returns(viewer))
        assertTrue(returns(gallery))
        assertTrue(returns(CompatHost.Viewer("tab-a", 1, CompatViewerCaller.GALLERY)))
        assertTrue(returns(CompatHost.Settings(path = "thread", origin = thread)))
        assertTrue(returns(CompatHost.Settings(path = "viewer", origin = viewer)))
        assertTrue(returns(CompatHost.Help(thread)))
        assertTrue(returns(CompatHost.Help(CompatHost.Settings(origin = thread))))
        assertTrue(returns(CompatHost.ToolbarEditor(CompatToolbarSurface.THREAD, thread)))
        assertTrue(returns(CompatHost.ToolbarEditor(CompatToolbarSurface.VIEWER, viewer)))
    }

    @Test
    fun openingTheThreadStillCountsAsOpening() {
        // First open, from the catalog, the main screen or a deep link.
        assertFalse(returns(null, lastTab = null))
        assertFalse(returns(CompatHost.Main))
        assertFalse(returns(CompatHost.Catalog("board")))
        assertFalse(returns(thread))
        // The post screen keeps its refresh, also through its toolbar editor.
        assertFalse(returns(CompatHost.Post("tab-a")))
        assertFalse(returns(CompatHost.ToolbarEditor(CompatToolbarSurface.POST, CompatHost.Post("tab-a"))))
        // Settings opened from the catalog or the main screen are not this thread's.
        assertFalse(returns(CompatHost.Settings(origin = CompatHost.Catalog("board"))))
        assertFalse(returns(CompatHost.Help(CompatHost.Main)))
        assertFalse(returns(CompatHost.SavedThreads(thread)))
    }

    @Test
    fun anotherThreadIsOpenedAsUsual() {
        // The viewer of another tab, or a deep link opened from the settings of thread A that shows thread B.
        assertFalse(returns(CompatHost.Viewer("tab-b", 0, CompatViewerCaller.THREAD)))
        assertFalse(returns(CompatHost.Gallery("tab-b")))
        assertFalse(returns(CompatHost.Settings(origin = thread), lastTab = "tab-a", tab = "tab-b"))
        assertFalse(returns(viewer, lastTab = "tab-b"))
    }

    @Test
    fun theTrackerReadsTheLastHostAndTab() {
        val tracker = CompatThreadReturnTracker()
        assertFalse(tracker.returnsFromOwnChild("tab-a"))
        tracker.lastThreadTabKey = "tab-a"
        tracker.lastHost = viewer
        assertTrue(tracker.returnsFromOwnChild("tab-a"))
        tracker.lastHost = CompatHost.Post("tab-a")
        assertFalse(tracker.returnsFromOwnChild("tab-a"))
    }
}
