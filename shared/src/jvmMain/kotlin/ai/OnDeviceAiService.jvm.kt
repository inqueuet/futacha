package com.valoser.futacha.shared.ai

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return JvmOnDeviceAiService()
}

private class JvmOnDeviceAiService : OnDeviceAiService {
    override suspend fun getAvailability(): AiAvailability {
        return AiAvailability(
            isAvailable = false,
            unavailableReason = "デスクトップ版の端末AIによる生成にはまだ対応していません。"
        )
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        return Result.success(buildExtractiveThreadSummary(input, providerLabel = "本文からの抜粋"))
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
    }
}
