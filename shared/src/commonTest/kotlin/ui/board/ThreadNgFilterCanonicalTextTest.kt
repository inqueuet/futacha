package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadNgFilterCanonicalTextTest {
    private fun post(id: String, body: String, subject: String? = null) = Post(
        id = id,
        author = "名無し",
        subject = subject,
        timestamp = "25/01/01(水)00:00:00",
        messageHtml = body,
        imageUrl = null,
        thumbnailUrl = null
    )

    @Test
    fun cp932AliasesMatchInBothDirectionsForEveryRule() {
        // U+301C / U+FF5E and U+2212 / U+FF0D are the same CP932 characters.
        val waveBody = post("1", "今日は〜晴れ")
        val minusBody = post("2", "気温は－3度")
        val other = post("3", "関係ない本文")
        val subject = post("4", "本文", subject = "雑談〜スレ")
        val lowerBodies = buildLowerBodyByPost(listOf(waveBody, minusBody, other, subject))
        val rules = listOf("無関係", "は～晴", "は−3", "別の語")

        assertTrue(matchesNgFilters(waveBody, emptyList(), rules, lowerBodies))
        assertTrue(matchesNgFilters(minusBody, emptyList(), rules, lowerBodies))
        assertFalse(matchesNgFilters(other, emptyList(), rules, lowerBodies))
        assertTrue(matchesNgFilters(subject, listOf("雑談～"), emptyList(), lowerBodies))

        val page = ThreadPage(
            threadId = "1",
            boardTitle = null,
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(waveBody, minusBody, other, subject)
        )
        val filtered = applyNgFilters(page, listOf("雑談～"), rules, enabled = true)
        assertEquals(listOf("3"), filtered.posts.map { it.id })
        val result = applyThreadFilterResult(
            page = page,
            criteria = ThreadFilterCriteria(emptySet(), "", emptyList(), null),
            ngHeaders = listOf("雑談～"),
            ngWords = rules,
            ngEnabled = true
        )
        assertEquals(listOf(2), result.postIndices)
    }
}
