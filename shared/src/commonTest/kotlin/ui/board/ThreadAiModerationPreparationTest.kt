package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.model.Post
import kotlin.test.*

class ThreadAiModerationPreparationTest {
    private fun post(id: Int, body: String = "本文 $id") = Post(
        id = "$id", author = null, subject = null, timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)

    @Test fun localCacheIsReusedAcrossRunsAndInvalidatedByChangedContext() {
        val local = linkedMapOf<ThreadPostModerationCacheKey, PostModerationResult>()
        val external = linkedMapOf<String, Pair<String, PostModerationResult>>()
        val posts = (1..6).map(::post)
        val first = prepareThreadAiModeration("b/1", "スレ", posts, false, false, "device", local, external)
        assertTrue(first.results.isEmpty())
        commitThreadAiModeration(first, posts.associate { it.id to PostModerationResult(it.id, it.id == "4") },
            posts.size, local, external)
        val second = prepareThreadAiModeration("b/1", "スレ", posts, false, false, "device", local, external)
        assertEquals(posts.map { it.id }, second.results.keys.toList())
        assertTrue(second.results.getValue("4").shouldHide)
        // Editing No.3 changes its own key and the context of the two posts after it.
        val edited = posts.map { if (it.id == "3") post(3, "編集") else it }
        val third = prepareThreadAiModeration("b/1", "スレ", edited, false, false, "device", local, external)
        assertEquals(listOf("1", "2", "6"), third.results.keys.toList())
        assertTrue(external.isEmpty())
    }

    @Test fun externalCacheKeepsOffscreenDecisionsAndDropsEditedOrRemovedBodies() {
        val local = linkedMapOf<ThreadPostModerationCacheKey, PostModerationResult>()
        val external = linkedMapOf<String, Pair<String, PostModerationResult>>()
        val posts = (1..4).map(::post)
        val first = prepareThreadAiModeration("b/1", null, posts, true, false, "openai", local, external)
        commitThreadAiModeration(first, posts.associate { it.id to PostModerationResult(it.id, true) },
            posts.size, local, external)
        val refreshed = listOf(post(1), post(2, "変更"), post(4), post(5))
        val second = prepareThreadAiModeration("b/1", null, refreshed, true, false, "openai", local, external)
        assertEquals(setOf("1", "4"), second.results.keys)
        assertEquals(setOf("1", "4"), external.keys)
        assertTrue(local.isEmpty())
    }
}
