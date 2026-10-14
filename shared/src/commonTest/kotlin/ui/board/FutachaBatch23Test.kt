package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutachaBatch23Test {
    private fun post(
        id: String = "1001",
        message: String = "ねこ画像<br>&gt;引用済み<br>2本目の行",
        image: String? = "https://may.2chan.net/b/src/1700000000000.jpg",
        thumb: String? = "https://may.2chan.net/b/thumb/1700000000000s.jpg",
        posterId: String? = "ID:abcd1234",
        mail: String? = "sage"
    ) = Post(id = id, order = 3, author = "としあき", subject = "無念", timestamp = "24/10/12(土)10:00:00",
        posterId = posterId, messageHtml = message, imageUrl = image, thumbnailUrl = thumb, mail = mail)

    @Test fun postSnapshotMatchesTheThreadConversion() {
        val p = post()
        val page = ThreadPage(threadId = "1000", boardTitle = "b", expiresAtLabel = null, deletedNotice = null,
            posts = listOf(post(id = "1000"), p))
        assertEquals(page.toCompatThreadSnapshot("tab", 1L).posts[1], p.toCompatPostSnapshot(1))
    }

    @Test fun quickQuoteQuotesEveryBodyLine() {
        assertEquals(listOf(">ねこ画像", ">>引用済み", ">2本目の行"), futachaQuickQuoteLines(post()))
        assertEquals(listOf(">No.1001"), futachaQuickQuoteLines(post(message = "")))
    }

    @Test fun copyCandidatesStartWithNoAndIncludeIdFileMailAndLines() {
        val selection = futachaPostItemSelection(post(), FutachaPostItemSelectionMode.COPY)
        val values = selection.candidates.map { it.value }
        assertEquals("No.1001", values.first())
        assertTrue(values.any { "abcd1234" in it }, values.toString())
        assertTrue("1700000000000.jpg" in values, values.toString())
        assertTrue("sage" in values)
        assertTrue("2本目の行" in values)
        assertTrue(selection.selected.isEmpty())
    }

    @Test fun selectedItemsAreJoinedByLineForCopyAndBySpaceForSearch() {
        val copy = futachaPostItemSelection(post(), FutachaPostItemSelectionMode.COPY).let { it.copy(selected = setOf(0, it.candidates.lastIndex)) }
        assertEquals("No.1001\n2本目の行", futachaSelectedItemsText(copy))
        val search = futachaPostItemSelection(post(message = "ねこ 画像<br>犬"), FutachaPostItemSelectionMode.SEARCH)
        assertTrue(search.candidates.isNotEmpty())
        assertEquals(search.candidates.joinToString(" ") { it.value },
            futachaSelectedItemsText(search.copy(selected = search.candidates.indices.toSet())))
    }

    @Test fun googleSearchUrlIsEncodedAndBlankGivesNothing() {
        assertEquals("https://www.google.com/search?q=%E7%8C%AB%20cat", futachaGoogleSearchUrl(" 猫 cat "))
        assertNull(futachaGoogleSearchUrl("   "))
    }

    @Test fun mediaOnlyUrlsUseOriginalOrThumbnailOnceEach() {
        val page = ThreadPage(threadId = "1000", boardTitle = "b", expiresAtLabel = null, deletedNotice = null, posts = listOf(
            post(id = "1000"),
            post(id = "1001", image = null, thumb = "https://may.2chan.net/b/thumb/2s.jpg"),
            post(id = "1002"),
            post(id = "1003", image = null, thumb = null)
        ))
        assertEquals(
            listOf("https://may.2chan.net/b/src/1700000000000.jpg", "https://may.2chan.net/b/thumb/2s.jpg"),
            futachaMediaOnlyUrls(page)
        )
    }

    private fun tab(key: String, dead: Boolean = false, favorite: Boolean = false) = CompatTab(
        key = key, canonicalUrl = "https://may.2chan.net/b/res/$key.htm", originalUrl = "https://may.2chan.net/b/res/$key.htm",
        boardKey = "b", boardName = "may", threadNo = key, title = key, isDead = dead, favorite = favorite,
        insertedAtEpochMillis = 0L, contentUpdatedAtEpochMillis = 0L
    )

    @Test fun checkedDeadTabsAskTheServerAndKeepFavorites() = runBlocking {
        val tabs = listOf(tab("1"), tab("2"), tab("3", favorite = true), tab("4", dead = true), tab("5"))
        val gone = setOf("2", "3")
        val probed = mutableListOf<String>()
        val keys = futachaCheckedDeadTabCloseKeys({ tabs }, protectFavorites = true) { url ->
            probed += url
            if (url.endsWith("/5.htm")) error("network")
            gone.any { url.endsWith("/$it.htm") }
        }
        // 2 is gone on the server, 4 was already known dead; 3 is a protected favorite; 5 failed = alive.
        assertEquals(setOf("2", "4"), keys)
        assertTrue(probed.none { it.endsWith("/3.htm") || it.endsWith("/4.htm") })
        assertEquals(setOf("2", "3", "4"),
            futachaCheckedDeadTabCloseKeys({ tabs }, protectFavorites = false) { url -> gone.any { url.endsWith("/$it.htm") } })
    }
}
