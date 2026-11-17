package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.compat.CompatThreadReturnTracker
import com.valoser.futacha.shared.ui.ftutter.FtutterThreadMemory
import kotlin.test.*

class ThreadOpeningVisitTest {
    private fun post(id: String) = Post(id = id, author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
    private val posts = (1..4).map { post("$it") }

    @Test fun settingsViewerAndWritingReturnsRetainTheConsumedToshiakiOpening() {
        val host = CompatThreadReturnTracker()
        val first = host.openingVisit("a").apply { initialize(2, "2") }
        assertEquals(2, first.tracker!!.accept(posts, emptySet())?.newPosts)
        host.lastThreadTabKey = "a"
        listOf<CompatHost>(CompatHost.Settings(origin = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG)),
            CompatHost.Viewer("a", 0, CompatViewerCaller.THREAD), CompatHost.Post("a"),
            CompatHost.ToolbarEditor(CompatToolbarSurface.POST, CompatHost.Post("a"))).forEach { child ->
            host.lastHost = child
            val resumed = host.openingVisit("a").apply { initialize(1, "1") }
            assertSame(first, resumed)
            assertNull(resumed.tracker!!.accept(posts + post("5"), emptySet()))
        }
        host.lastHost = CompatHost.Catalog("board")
        val reopened = host.openingVisit("a").apply { initialize(4, "4") }
        assertNotSame(first, reopened)
        assertEquals(1, reopened.tracker!!.accept(posts + post("5"), emptySet())?.newPosts)
    }

    @Test fun anotherThreadOpenedFromAChildStartsItsOwnOpening() {
        val host = CompatThreadReturnTracker()
        val first = host.openingVisit("a")
        host.lastThreadTabKey = "a"
        host.lastHost = CompatHost.Post("a")
        assertNotSame(first, host.openingVisit("b"))
    }

    @Test fun ftutterHeldPagesKeepTheOpeningUntilAnActualOpenStartsAnother() {
        val memory = FtutterThreadMemory()
        memory.open("url", false, 2, "2")
        val first = memory.opening("url")
        memory.put("url", ThreadPage("1", null, null, null, posts))
        assertEquals(2, first.tracker!!.accept(memory.page("url")!!.posts, emptySet())?.newPosts)
        assertSame(first, memory.opening("url"))
        assertNull(memory.opening("url").tracker!!.accept(posts + post("5"), emptySet()))
        memory.open("url", true, 4, "4")
        assertNotSame(first, memory.opening("url"))
        assertEquals(1, memory.opening("url").tracker!!.accept(posts + post("5"), emptySet())?.newPosts)
        assertTrue(memory.takeRefresh("url"))
    }
}
