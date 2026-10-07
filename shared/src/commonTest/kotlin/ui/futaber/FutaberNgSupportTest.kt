package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FutaberNgSupportTest {
    private fun post(id: String, html: String, author: String? = null, poster: String? = null) = Post(
        id = id, author = author, subject = null, timestamp = "25/10/05(日)12:00:00", posterId = poster,
        messageHtml = html, imageUrl = null, thumbnailUrl = null
    )

    private val posts = listOf(
        post("100", "普通の話"),
        post("101", "ここに禁止ワードがある"),
        post("102", "別の話", author = "荒らし", poster = "ID:bad001"),
        post("103", "また普通")
    )

    private fun rule(kind: CompatNgKind, value: String, scope: String = "*") =
        CompatNgRule("r-$kind-$value", kind, scope, value, 0L)

    @Test
    fun nothingIsHiddenWithoutRules() {
        assertTrue(futaberNgHiddenIds(posts, emptyList(), emptyList(), emptyList(), "tab", "board").isEmpty())
        assertTrue(futaberNgHiddenIds(emptyList(), listOf("x"), emptyList(), emptyList(), "tab", "board").isEmpty())
        assertTrue(futaberNgHiddenIds(posts, listOf("  "), listOf(""), emptyList(), "tab", "board").isEmpty())
    }

    @Test
    fun theSharedWordAndHeaderListsHidePostsByBodyAndByName() {
        assertEquals(setOf("101"), futaberNgHiddenIds(posts, listOf("禁止ワード"), emptyList(), emptyList(), "t", "b"))
        assertEquals(setOf("102"), futaberNgHiddenIds(posts, emptyList(), listOf("荒らし"), emptyList(), "t", "b"))
        assertEquals(setOf("101", "102"), futaberNgHiddenIds(posts, listOf("禁止"), listOf("bad001"), emptyList(), "t", "b"))
    }

    @Test
    fun threadRulesFromOtherModesHideByNumberAndByPosterOnlyForTheirThread() {
        val byNumber = rule(CompatNgKind.THREAD_POST_NO, "103", scope = "tab-A")
        assertEquals(setOf("103"), futaberNgHiddenIds(posts, emptyList(), emptyList(), listOf(byNumber), "tab-A", "b"))
        // The same rule does not touch another thread.
        assertTrue(futaberNgHiddenIds(posts, emptyList(), emptyList(), listOf(byNumber), "tab-B", "b").isEmpty())
        // A global ("*") word rule applies everywhere.
        val global = rule(CompatNgKind.THREAD_WORD, "禁止ワード")
        assertEquals(setOf("101"), futaberNgHiddenIds(posts, emptyList(), emptyList(), listOf(global), "tab-B", "b"))
    }

    @Test
    fun addingTrimsRefusesDuplicatesAndBoundsTheList() {
        val one = futaberAddNgEntry(emptyList(), "  語  ")
        assertEquals(listOf("語"), one)
        assertSame(one, futaberAddNgEntry(one, "語"))
        assertSame(one, futaberAddNgEntry(one, "   "))
        assertEquals(listOf("abc"), futaberAddNgEntry(emptyList(), "abc"))
        assertEquals(1, futaberAddNgEntry(listOf("ABC"), "abc").size)
        assertEquals(FUTABER_NG_MAX_LENGTH, futaberAddNgEntry(emptyList(), "あ".repeat(500)).single().length)
        val full = (1..FUTABER_NG_MAX_ENTRIES).map { "w$it" }
        assertSame(full, futaberAddNgEntry(full, "new"))
        assertEquals(listOf("b"), futaberRemoveNgEntry(listOf("a", "b"), "a"))
    }

    @Test
    fun aFullListSaysSoAndTheWordLimitDoesNotCutAnEmoji() {
        assertEquals(null, futaberNgFullMessage(emptyList()))
        assertEquals(null, futaberNgFullMessage((1 until FUTABER_NG_MAX_ENTRIES).map { "w$it" }))
        assertTrue(futaberNgFullMessage((1..FUTABER_NG_MAX_ENTRIES).map { "w$it" })!!.contains("$FUTABER_NG_MAX_ENTRIES"))
        val emoji = "\uD83D\uDE00"
        val added = futaberAddNgEntry(emptyList(), "a".repeat(FUTABER_NG_MAX_LENGTH - 1) + emoji).single()
        assertEquals(FUTABER_NG_MAX_LENGTH - 1, added.length)
    }
}
