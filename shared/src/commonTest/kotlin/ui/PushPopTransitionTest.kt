package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ui.ftutter.FtutterScreen
import com.valoser.futacha.shared.ui.ftutter.FtutterThreadRef
import com.valoser.futacha.shared.ui.ftutter.ftutterTransitionScreen
import kotlin.test.Test
import kotlin.test.assertEquals

/** App Store review 2026-11-16: ふたばー風・ふたったー風 had no screen change animation. */
class PushPopTransitionTest {
    private fun s(id: String, depth: Int) = PushPopScreen(id, depth, Unit)

    @Test
    fun deeperPushesShallowerPopsAndTheRestDoNotSlide() {
        assertEquals(1, pushPopDirection(s("catalog:a", 0), s("thread:a:1", 1)))
        assertEquals(-1, pushPopDirection(s("thread:a:1", 1), s("catalog:a", 0)))
        assertEquals(0, pushPopDirection(s("thread:a:1", 1), s("thread:a:2", 1)))
        assertEquals(0, pushPopDirection(s("mix:1", -1), s("thread:a:1", 1)))
    }

    @Test
    fun twoPanesKeepOneScreenForTheCatalogAndTheThread() {
        val catalog = FtutterScreen.Catalog("a")
        val thread = FtutterScreen.Thread("a", FtutterThreadRef("a", "1", "t"), catalog)
        assertEquals(ftutterTransitionScreen(catalog, true, false).id, ftutterTransitionScreen(thread, true, false).id)
        assertEquals(1, pushPopDirection(ftutterTransitionScreen(catalog, false, false), ftutterTransitionScreen(thread, false, false)))
    }
}
