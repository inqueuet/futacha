package compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.compat.CompatSearchTextRange
import com.valoser.futacha.shared.ui.compat.findCompatOverlappingRanges
import com.valoser.futacha.shared.ui.compat.findCompatThreadSearchHits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatThreadSearchTest {
    private fun post(
        index: Int,
        body: String,
        mail: String? = null
    ) = CompatPostSnapshot(
        position = index,
        postNo = (100 + index).toString(),
        mail = mail,
        timestamp = "08/06 12:00",
        messageHtml = body
    )

    @Test
    fun overlappingBodyRangesMatchTargetIndexOfAll() {
        assertEquals(
            listOf(CompatSearchTextRange(0, 2), CompatSearchTextRange(1, 3)),
            findCompatOverlappingRanges("aaa", "aa")
        )
    }

    @Test
    fun pathologicalMatchCountsAreBounded() {
        assertEquals(64, findCompatOverlappingRanges("a".repeat(10_000), "a").size)
    }

    @Test
    fun countsPostsRatherThanRangesAndIncludesMailOnlyHit() {
        val hits = findCompatThreadSearchHits(
            listOf(
                post(0, "<b>aaa</b>"),
                post(1, "本文", mail = "aa@example.test"),
                post(2, "AAA")
            ),
            "aa"
        )
        assertEquals(listOf(0, 1), hits.map { it.postIndex })
        assertEquals(2, hits[0].textRanges.size)
        assertTrue(hits[1].textRanges.isEmpty())
    }

    @Test
    fun emptyAndCaseDifferentQueriesHaveNoHits() {
        val posts = listOf(post(0, "Alpha", mail = "Mail"))
        assertTrue(findCompatThreadSearchHits(posts, "").isEmpty())
        assertTrue(findCompatThreadSearchHits(posts, "alpha").isEmpty())
        assertTrue(findCompatThreadSearchHits(posts, "mail").isEmpty())
    }
}
