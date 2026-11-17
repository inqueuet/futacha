package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.OpenAiModerationScores
import com.valoser.futacha.shared.model.Post
import kotlin.test.*

class OpenAiFilterIntegrationTest {
    @Test fun thresholdAndAutoHideDetermineVisiblePostsWhileKeepingCandidateCounts() {
        val posts = (1..3).map {
            Post(it.toString(), author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        }
        val context = buildAiHiddenPostResolutionContext(posts, selfPostIdentifiers = setOf("3"))
        val scores = OpenAiModerationScores(mapOf("harassment" to 0.8f, "harassment/threatening" to 0.1f, "hate" to 0.1f, "hate/threatening" to 0.1f))
        val candidates = posts.map { scores.classify(it.id, 0.8f) }
        assertEquals(setOf("2"), resolveAiHiddenPostState(context, candidates, automaticallyHide = true).postIds)
        assertTrue(resolveAiHiddenPostState(context, candidates, automaticallyHide = false).postIds.isEmpty())
        assertEquals(3, AiPostModerationUiState(results = candidates).hiddenCandidateCount)
        val stricter = posts.map { scores.classify(it.id, 0.85f) }
        assertTrue(resolveAiHiddenPostState(context, stricter, automaticallyHide = true).postIds.isEmpty())
    }
}
