package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post

data class AiAvailability(
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
    val supportsThreadSummary: Boolean = false,
    val supportsPostModeration: Boolean = false,
    val providerLabel: String = "端末AI"
)

data class ThreadSummaryInput(
    val threadId: String,
    val title: String?,
    val posts: List<Post>
)

data class ThreadSummary(
    val headline: String,
    val bullets: List<String>,
    val providerLabel: String
)

data class PostModerationInput(
    val threadId: String,
    val posts: List<Post>
)

data class PostModerationResult(
    val postId: String,
    val shouldHide: Boolean,
    val reason: String? = null,
    val confidence: Float = 0f
)

interface OnDeviceAiService {
    suspend fun getAvailability(): AiAvailability
    suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary>
    suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>>
}

expect fun createOnDeviceAiService(platformContext: Any? = null): OnDeviceAiService
