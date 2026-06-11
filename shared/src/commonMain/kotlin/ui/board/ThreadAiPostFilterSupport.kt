package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.model.Post

private const val AI_HIDDEN_REASON_MAX_CHARS = 80
private val AI_HIDDEN_REASON_WHITESPACE_REGEX = Regex("\\s+")

internal data class AiHiddenPostState(
    val postIds: Set<String> = emptySet(),
    val reasons: Map<String, String> = emptyMap()
)

internal class AiHiddenPostResolutionContext(
    val existingPostIds: Set<String>,
    val opPostId: String?,
    val maxHiddenPosts: Int,
    val selfPostIdentifiers: Set<String>
)

internal data class AiPostModerationUiState(
    val isEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val processedPosts: Int = 0,
    val totalPosts: Int = 0,
    val failedBatchCount: Int = 0,
    val results: List<PostModerationResult> = emptyList()
) {
    val hiddenCandidateCount: Int
        get() = results.count { it.shouldHide }

    val progress: Float?
        get() {
            if (totalPosts <= 0) return null
            return processedPosts.toFloat()
                .div(totalPosts.toFloat())
                .coerceIn(0f, 1f)
        }
}

internal fun resolveAiHiddenPostState(
    posts: List<Post>,
    moderationResults: List<PostModerationResult>,
    selfPostIdentifiers: Set<String> = emptySet()
): AiHiddenPostState {
    if (posts.isEmpty()) {
        return AiHiddenPostState()
    }
    return resolveAiHiddenPostState(
        context = buildAiHiddenPostResolutionContext(
            posts = posts,
            selfPostIdentifiers = selfPostIdentifiers
        ),
        moderationResults = moderationResults
    )
}

internal fun buildAiHiddenPostResolutionContext(
    posts: List<Post>,
    selfPostIdentifiers: Set<String> = emptySet()
): AiHiddenPostResolutionContext {
    return AiHiddenPostResolutionContext(
        existingPostIds = posts.map { it.id }.toSet(),
        opPostId = posts.firstOrNull()?.id,
        maxHiddenPosts = resolveAiHiddenPostLimit(posts.size),
        selfPostIdentifiers = selfPostIdentifiers.toSet()
    )
}

internal fun resolveAiHiddenPostState(
    context: AiHiddenPostResolutionContext,
    moderationResults: List<PostModerationResult>
): AiHiddenPostState {
    if (context.existingPostIds.isEmpty() || moderationResults.isEmpty()) {
        return AiHiddenPostState()
    }
    val hiddenPostIds = linkedSetOf<String>()
    val reasons = linkedMapOf<String, String>()
    moderationResults.forEach { result ->
        val postId = result.postId.trim()
        if (!result.shouldHide ||
            postId.isBlank() ||
            postId !in context.existingPostIds ||
            postId == context.opPostId ||
            postId in context.selfPostIdentifiers
        ) {
            return@forEach
        }
        if (hiddenPostIds.size >= context.maxHiddenPosts) {
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
    return (postCount - 1).coerceAtLeast(0)
}

private fun String.normalizeAiHiddenReason(): String {
    return lineSequence()
        .joinToString(separator = " ") { it.trim() }
        .replace(AI_HIDDEN_REASON_WHITESPACE_REGEX, " ")
        .trim()
        .let { normalized ->
            if (normalized.length <= AI_HIDDEN_REASON_MAX_CHARS) {
                normalized
            } else {
                normalized.take(AI_HIDDEN_REASON_MAX_CHARS - 1).trimEnd() + "…"
            }
        }
}
