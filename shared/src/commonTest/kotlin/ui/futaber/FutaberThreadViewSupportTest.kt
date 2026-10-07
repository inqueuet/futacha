package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberThreadViewSupportTest {
    private fun post(id: String, html: String = "", author: String? = null, subject: String? = null, poster: String? = null, vararg refs: QuoteReference) =
        Post(id = id, author = author, subject = subject, timestamp = "", posterId = poster, messageHtml = html,
            imageUrl = null, thumbnailUrl = null, quoteReferences = refs.toList())

    private val posts = listOf(
        post("100", "最初の本文<br>二行目"),
        post("101", "ねこがいる", author = "名無し", poster = "ID:abc"),
        post("102", "ＡＢＣ とイヌ", subject = "題名"),
        post("103", ">最初の本文", refs = arrayOf(QuoteReference(">最初の本文", listOf("100")))),
        post("104", ">最初の本文", refs = arrayOf(QuoteReference(">最初の本文", listOf("100")))),
        post("105", ">最初の本文", refs = arrayOf(QuoteReference(">最初の本文", listOf("100"))))
    )
    private val replies = futaberReplyIndex(posts)

    @Test
    fun noFilterListsEveryPostWithItsOwnNumber() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(), replies)
        assertEquals(posts.indices.toList(), rows.map { it.ordinal })
        assertFalse(FutaberViewFilter().isActive)
        assertFalse(FutaberViewFilter(query = "  ").isActive)
    }

    @Test
    fun searchLooksAtBodySubjectNameIdAndNumberIgnoringWidthAndKana() {
        fun hits(q: String) = futaberVisibleRows(posts, FutaberViewFilter(query = q), replies).map { it.post.id }
        assertEquals(listOf("100", "103", "104", "105"), hits("最初"))
        assertEquals(listOf("101"), hits("ネコ"))
        assertEquals(listOf("102"), hits("abc とイヌ"))
        assertEquals(listOf("102"), hits("題名"))
        assertEquals(listOf("101"), hits("名無し"))
        assertEquals(listOf("101"), hits("id:abc"))
        assertEquals(listOf("104"), hits("104"))
        assertTrue(hits("存在しない").isEmpty())
    }

    @Test
    fun manyRepliesKeepsOnlyPostsRepliedToAtLeastTheThresholdTimes() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(repliesOnly = true), replies)
        assertEquals(listOf("100"), rows.map { it.post.id })
        assertEquals(0, rows.single().ordinal)
        assertEquals(
            listOf("100"),
            futaberVisibleRows(posts, FutaberViewFilter(repliesOnly = true), replies, manyRepliesThreshold = 3).map { it.post.id }
        )
        assertEquals(
            listOf("100"),
            futaberVisibleRows(posts, FutaberViewFilter(repliesOnly = true), replies, manyRepliesThreshold = 1).map { it.post.id }
        )
        assertTrue(futaberVisibleRows(posts, FutaberViewFilter(repliesOnly = true), replies, manyRepliesThreshold = 4).isEmpty())
    }

    @Test
    fun hiddenPostsAreDroppedAndTheRestKeepTheirNumbers() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(), replies, hidden = setOf("101", "104"))
        assertEquals(listOf(0, 2, 3, 5), rows.map { it.ordinal })
        val both = futaberVisibleRows(posts, FutaberViewFilter(query = "最初"), replies, hidden = setOf("103"))
        assertEquals(listOf(0, 4, 5), both.map { it.ordinal })
    }

    @Test
    fun scrollingToANumberLandsOnTheNextRowThatIsStillListed() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(), replies, hidden = setOf("102", "103"))
        assertEquals(2, futaberRowPositionAtOrAfter(rows, 2))
        assertEquals(0, futaberRowPositionAtOrAfter(rows, 0))
        assertNull(futaberRowPositionAtOrAfter(rows, 99))
    }

    @Test
    fun aPostIsFoundInTheRowsOfTheMomentByItsId() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(query = "最初"), replies)
        // The list was narrowed after reading began: the rows now are 100, 103, 104, 105.
        assertEquals(0, futaberRowPositionOfPost(rows, "100"))
        assertEquals(2, futaberRowPositionOfPost(rows, "104"))
        assertNull(futaberRowPositionOfPost(rows, "101"))
        assertNull(futaberRowPositionOfPost(rows, null))
    }

    @Test
    fun theReadingPositionIsTheRowsOwnPostAndIsNotKeptWhileNarrowed() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(), replies, hidden = setOf("101"))
        assertEquals(FutaberScrollPosition(2, 14, "102", posts.size), futaberScrollPositionToSave(rows, 1, 14, posts.size, narrowed = false))
        assertNull(futaberScrollPositionToSave(rows, 1, 14, posts.size, narrowed = true))
        assertNull(futaberScrollPositionToSave(rows, 99, 0, posts.size, narrowed = false))
        assertNull(futaberScrollPositionToSave(rows, 0, 0, 0, narrowed = false))
    }

    @Test
    fun theUnsavedPositionIsTakenOnceByWhoeverSavesFirst() {
        val pending = FutaberPendingScrollSave()
        assertNull(pending.take())
        pending.record(FutaberScrollPosition(3, 0, "103", 6))
        pending.record(FutaberScrollPosition(4, 8, "104", 6))
        // The newest scroll wins; leaving the screen after the debounced save finds nothing more to write.
        assertEquals(FutaberScrollPosition(4, 8, "104", 6), pending.take())
        assertNull(pending.take())
        // A narrowed list clears what was pending: nothing is saved for it.
        pending.record(FutaberScrollPosition(1, 0, "101", 6))
        pending.record(null)
        assertNull(pending.take())
    }

    @Test
    fun ordinalsAreLookedUpByIdAndTheFirstOfARepeatedIdWins() {
        val ordinals = futaberOrdinalsById(posts + post("100"))
        assertEquals(0, ordinals["100"])
        assertEquals(5, ordinals["105"])
        assertNull(ordinals["999"])
        assertEquals(posts.size, ordinals.size)
    }
}
