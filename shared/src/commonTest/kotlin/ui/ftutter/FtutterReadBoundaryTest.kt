package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryTracker
import kotlin.test.*

class FtutterReadBoundaryTest {
    @Test fun readBoundarySurvivesDeletedOlderPostsAndAReopenedStoreValue() {
        val url = "https://may.2chan.net/b/res/100.htm"
        val raw = ftutterReadBoundariesAfter(null, url, 4, "104")!!
        assertEquals("104", ftutterReadBoundaryId(raw, url, 4))
        assertNull(ftutterReadBoundaryId(raw, url, 5)) // An imported count cannot borrow another boundary.
        val posts = listOf("100", "103", "105").map {
            Post(it, author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        }
        assertEquals(1, ThreadOpeningSummaryTracker(4, ftutterReadBoundaryId(raw, url, 4)).accept(posts, emptySet())?.newPosts)
        assertNull(ftutterReadBoundariesAfter(raw, url, 4, "104"))
    }

    @Test fun boundariesStayWithinThePreferenceBudget() {
        var raw: String? = null
        repeat(300) { raw = ftutterReadBoundariesAfter(raw, "https://may.2chan.net/b/res/$it.htm", 2, "9999999999999999") ?: raw }
        assertTrue(raw!!.length <= 18_000)
        // L129: cut by characters, not by 200; the newest boundary is the one kept first.
        assertEquals("https://may.2chan.net/b/res/299.htm", decodeFtutterReadBoundaries(raw).first().url)
        assertNull(ftutterReadBoundaryId("broken", "url", 2))
    }
}
