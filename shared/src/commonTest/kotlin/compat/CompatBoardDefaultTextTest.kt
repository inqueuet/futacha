package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatBoardDefaultTextTest {
    @Test
    fun learnsOnlyAStrictMajorityAfterTwentyPostsLikeReferenceApk() {
        val current = CompatBoardDefaultText()
        val tooFew = List(19) { post(it, author = "山田", subject = "定型") }
        assertEquals(current, learnCompatBoardDefaultText(current, tooFew))

        val learned = learnCompatBoardDefaultText(
            current,
            List(11) { post(it, author = "山田", subject = "定型") } +
                List(9) { post(it + 11, author = "佐藤", subject = "別題") }
        )
        assertEquals(CompatBoardDefaultText(defaultSubject = "定型", defaultName = "山田"), learned)

        val tied = learnCompatBoardDefaultText(
            current,
            List(10) { post(it, author = "山田", subject = "定型") } +
                List(10) { post(it + 10, author = "佐藤", subject = "別題") }
        )
        assertEquals(current, tied)
    }

    @Test
    fun hidePolicyCoversLearnedValuesAndReferenceFallbacks() {
        val learned = CompatBoardDefaultText(defaultSubject = "定型題", defaultName = "定型名")
        assertTrue(shouldHideCompatDefaultSubject(" <b>定型題</b>　", learned))
        assertTrue(shouldHideCompatDefaultSubject("無念", learned))
        assertTrue(shouldHideCompatDefaultName("定型名", learned))
        assertTrue(shouldHideCompatDefaultName("としあき", learned))
        assertFalse(shouldHideCompatDefaultName("別人", learned))
        assertFalse(shouldHideCompatDefaultSubject("別題", learned))
    }

    private fun post(
        position: Int,
        author: String,
        subject: String
    ) = CompatPostSnapshot(
        position = position,
        postNo = (100 + position).toString(),
        timestamp = "",
        messageHtml = "本文",
        author = author,
        subject = subject
    )
}
