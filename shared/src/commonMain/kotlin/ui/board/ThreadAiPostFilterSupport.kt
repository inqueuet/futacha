package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.model.Post

private const val AI_HIDDEN_REASON_MAX_CHARS = 80
private const val AI_HIDDEN_MAX_POSTS = 8
private const val AI_HIDDEN_MAX_PERCENT = 20

internal data class AiHiddenPostState(
    val postIds: Set<String> = emptySet(),
    val reasons: Map<String, String> = emptyMap()
)

internal fun resolveAiHiddenPostState(
    posts: List<Post>,
    moderationResults: List<PostModerationResult>,
    selfPostIdentifiers: Set<String> = emptySet()
): AiHiddenPostState {
    if (posts.isEmpty() || moderationResults.isEmpty()) {
        return AiHiddenPostState()
    }
    val existingPostIds = posts.map { it.id }.toSet()
    val opPostId = posts.firstOrNull()?.id
    val maxHiddenPosts = resolveAiHiddenPostLimit(posts.size)
    val hiddenPostIds = linkedSetOf<String>()
    val reasons = linkedMapOf<String, String>()
    moderationResults.forEach { result ->
        val postId = result.postId.trim()
        if (!result.shouldHide ||
            postId.isBlank() ||
            postId !in existingPostIds ||
            postId == opPostId ||
            postId in selfPostIdentifiers
        ) {
            return@forEach
        }
        if (hiddenPostIds.size >= maxHiddenPosts) {
            return@forEach
        }
        hiddenPostIds += postId
        result.reason
            ?.normalizeAiHiddenReason()
            ?.takeIf { it.isNotBlank() }
            ?.let { reasons[postId] = it }
    }
    return AiHiddenPostState(
        postIds = hiddenPostIds,
        reasons = reasons
    )
}

internal fun resolveAiHiddenPostLimit(postCount: Int): Int {
    if (postCount <= 1) return 0
    val percentageLimit = (postCount * AI_HIDDEN_MAX_PERCENT) / 100
    return percentageLimit
        .coerceAtLeast(1)
        .coerceAtMost(AI_HIDDEN_MAX_POSTS)
}

private fun String.normalizeAiHiddenReason(): String {
    return lineSequence()
        .joinToString(separator = " ") { it.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { normalized ->
            if (normalized.length <= AI_HIDDEN_REASON_MAX_CHARS) {
                normalized
            } else {
                normalized.take(AI_HIDDEN_REASON_MAX_CHARS - 1).trimEnd() + "…"
            }
        }
}
