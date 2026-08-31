package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AiAvailability(
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
    val supportsThreadSummary: Boolean = false,
    val supportsPostModeration: Boolean = false,
    val providerLabel: String = "端末AI",
    val isDownloadInProgress: Boolean = false,
    val downloadedBytes: Long? = null,
    val downloadTotalBytes: Long? = null
) {
    val downloadProgress: Float?
        get() {
            val downloaded = downloadedBytes ?: return null
            val total = downloadTotalBytes ?: return null
            if (total <= 0L) return null
            return (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

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
    fun observeAvailability(): Flow<AiAvailability> = flow {
        emit(getAvailability())
    }

    suspend fun getAvailability(): AiAvailability
    suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary>
    suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>>
    fun cancelActiveRequests() = Unit
}

expect fun createOnDeviceAiService(platformContext: Any? = null): OnDeviceAiService
