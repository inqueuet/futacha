package com.valoser.futacha.shared.ai

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return JvmOnDeviceAiService()
}

private class JvmOnDeviceAiService : OnDeviceAiService {
    override suspend fun getAvailability(): AiAvailability {
        return AiAvailability(
            isAvailable = false,
            unavailableReason = "JVM 実行環境では端末AIを利用できません。"
        )
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        return Result.success(buildExtractiveThreadSummary(input, providerLabel = "JVM"))
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
    }
}
