package com.valoser.futacha.shared.ai

import kotlinx.coroutines.delay
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

private const val APPLE_INTELLIGENCE_AVAILABLE_KEY = "futacha_apple_intelligence_available"
private const val APPLE_INTELLIGENCE_REASON_KEY = "futacha_apple_intelligence_unavailable_reason"
private const val SUMMARY_NOTIFICATION_NAME = "futacha.appleIntelligence.summary.requested"
private const val SUMMARY_REQUEST_ID_KEY = "futacha_apple_intelligence_summary_request_id"
private const val SUMMARY_REQUEST_TITLE_KEY = "futacha_apple_intelligence_summary_request_title"
private const val SUMMARY_REQUEST_TEXT_KEY = "futacha_apple_intelligence_summary_request_text"
private const val SUMMARY_RESPONSE_ID_KEY = "futacha_apple_intelligence_summary_response_id"
private const val SUMMARY_RESPONSE_TEXT_KEY = "futacha_apple_intelligence_summary_response_text"
private const val SUMMARY_RESPONSE_ERROR_KEY = "futacha_apple_intelligence_summary_response_error"
private const val MODERATION_NOTIFICATION_NAME = "futacha.appleIntelligence.postModeration.requested"
private const val MODERATION_REQUEST_ID_KEY = "futacha_apple_intelligence_moderation_request_id"
private const val MODERATION_REQUEST_TEXT_KEY = "futacha_apple_intelligence_moderation_request_text"
private const val MODERATION_RESPONSE_ID_KEY = "futacha_apple_intelligence_moderation_response_id"
private const val MODERATION_RESPONSE_TEXT_KEY = "futacha_apple_intelligence_moderation_response_text"
private const val MODERATION_RESPONSE_ERROR_KEY = "futacha_apple_intelligence_moderation_response_error"
private const val AI_REQUEST_POLL_INTERVAL_MILLIS = 120L
private const val AI_REQUEST_TIMEOUT_MILLIS = 45_000L

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return IosOnDeviceAiService()
}

private class IosOnDeviceAiService : OnDeviceAiService {
    override suspend fun getAvailability(): AiAvailability {
        val defaults = NSUserDefaults.standardUserDefaults()
        val hasAvailability = defaults.objectForKey(APPLE_INTELLIGENCE_AVAILABLE_KEY) != null
        if (!hasAvailability) {
            return AiAvailability(
                isAvailable = false,
                unavailableReason = "Apple Intelligence の機種判定ブリッジは未接続です。",
                providerLabel = "Apple Intelligence"
            )
        }
        val available = defaults.boolForKey(APPLE_INTELLIGENCE_AVAILABLE_KEY)
        val reason = defaults.stringForKey(APPLE_INTELLIGENCE_REASON_KEY)
        return AiAvailability(
            isAvailable = available,
            unavailableReason = if (available) null else reason,
            supportsThreadSummary = available,
            supportsPostModeration = available,
            providerLabel = "Apple Intelligence"
        )
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        val sourceText = buildThreadSummarySourceText(input)
        if (sourceText.isBlank()) {
            return Result.success(buildExtractiveThreadSummary(input, providerLabel = "Apple Intelligence"))
        }
        val generatedText = requestFoundationModelsSummary(
            title = input.title.orEmpty(),
            sourceText = sourceText
        ).getOrElse {
            return Result.success(buildExtractiveThreadSummary(input, providerLabel = "Apple Intelligence"))
        }
        return Result.success(
            parseGeneratedThreadSummary(
                input = input,
                generatedText = generatedText,
                providerLabel = "Apple Intelligence",
                generatedTextHasHeadline = true
            )
        )
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        if (input.posts.isEmpty()) {
            return Result.success(emptyList())
        }
        val sourceText = buildPostModerationSourceText(input)
        if (sourceText.isBlank()) {
            return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
        }
        val responseText = requestFoundationModelsPostModeration(sourceText).getOrElse {
            return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
        }
        val detected = parsePostModerationResponse(responseText)
        return Result.success(
            input.posts.map { post ->
                detected[post.id] ?: PostModerationResult(postId = post.id, shouldHide = false)
            }
        )
    }
}

private suspend fun requestFoundationModelsSummary(
    title: String,
    sourceText: String
): Result<String> {
    val defaults = NSUserDefaults.standardUserDefaults()
    val requestId = NSUUID().UUIDString()
    defaults.removeObjectForKey(SUMMARY_RESPONSE_ID_KEY)
    defaults.removeObjectForKey(SUMMARY_RESPONSE_TEXT_KEY)
    defaults.removeObjectForKey(SUMMARY_RESPONSE_ERROR_KEY)
    defaults.setObject(requestId, forKey = SUMMARY_REQUEST_ID_KEY)
    defaults.setObject(title, forKey = SUMMARY_REQUEST_TITLE_KEY)
    defaults.setObject(sourceText, forKey = SUMMARY_REQUEST_TEXT_KEY)
    NSNotificationCenter.defaultCenter.postNotificationName(SUMMARY_NOTIFICATION_NAME, `object` = null)

    var waitedMillis = 0L
    while (waitedMillis < AI_REQUEST_TIMEOUT_MILLIS) {
        val responseId = defaults.stringForKey(SUMMARY_RESPONSE_ID_KEY)
        if (responseId == requestId) {
            val error = defaults.stringForKey(SUMMARY_RESPONSE_ERROR_KEY)
            val text = defaults.stringForKey(SUMMARY_RESPONSE_TEXT_KEY).orEmpty().trim()
            clearSummaryBridgeKeys(defaults)
            if (!error.isNullOrBlank()) {
                return Result.failure(IllegalStateException(error))
            }
            return if (text.isBlank()) {
                Result.failure(IllegalStateException("Apple Intelligence returned an empty summary"))
            } else {
                Result.success(text)
            }
        }
        delay(AI_REQUEST_POLL_INTERVAL_MILLIS)
        waitedMillis += AI_REQUEST_POLL_INTERVAL_MILLIS
    }
    clearSummaryBridgeKeys(defaults)
    return Result.failure(IllegalStateException("Apple Intelligence summary timed out"))
}

private suspend fun requestFoundationModelsPostModeration(sourceText: String): Result<String> {
    val defaults = NSUserDefaults.standardUserDefaults()
    val requestId = NSUUID().UUIDString()
    defaults.removeObjectForKey(MODERATION_RESPONSE_ID_KEY)
    defaults.removeObjectForKey(MODERATION_RESPONSE_TEXT_KEY)
    defaults.removeObjectForKey(MODERATION_RESPONSE_ERROR_KEY)
    defaults.setObject(requestId, forKey = MODERATION_REQUEST_ID_KEY)
    defaults.setObject(sourceText, forKey = MODERATION_REQUEST_TEXT_KEY)
    NSNotificationCenter.defaultCenter.postNotificationName(MODERATION_NOTIFICATION_NAME, `object` = null)

    var waitedMillis = 0L
    while (waitedMillis < AI_REQUEST_TIMEOUT_MILLIS) {
        val responseId = defaults.stringForKey(MODERATION_RESPONSE_ID_KEY)
        if (responseId == requestId) {
            val error = defaults.stringForKey(MODERATION_RESPONSE_ERROR_KEY)
            val text = defaults.stringForKey(MODERATION_RESPONSE_TEXT_KEY).orEmpty().trim()
            clearModerationBridgeKeys(defaults)
            if (!error.isNullOrBlank()) {
                return Result.failure(IllegalStateException(error))
            }
            return Result.success(text)
        }
        delay(AI_REQUEST_POLL_INTERVAL_MILLIS)
        waitedMillis += AI_REQUEST_POLL_INTERVAL_MILLIS
    }
    clearModerationBridgeKeys(defaults)
    return Result.failure(IllegalStateException("Apple Intelligence post moderation timed out"))
}

private fun clearSummaryBridgeKeys(defaults: NSUserDefaults) {
    defaults.removeObjectForKey(SUMMARY_REQUEST_ID_KEY)
    defaults.removeObjectForKey(SUMMARY_REQUEST_TITLE_KEY)
    defaults.removeObjectForKey(SUMMARY_REQUEST_TEXT_KEY)
    defaults.removeObjectForKey(SUMMARY_RESPONSE_ID_KEY)
    defaults.removeObjectForKey(SUMMARY_RESPONSE_TEXT_KEY)
    defaults.removeObjectForKey(SUMMARY_RESPONSE_ERROR_KEY)
}

private fun clearModerationBridgeKeys(defaults: NSUserDefaults) {
    defaults.removeObjectForKey(MODERATION_REQUEST_ID_KEY)
    defaults.removeObjectForKey(MODERATION_REQUEST_TEXT_KEY)
    defaults.removeObjectForKey(MODERATION_RESPONSE_ID_KEY)
    defaults.removeObjectForKey(MODERATION_RESPONSE_TEXT_KEY)
    defaults.removeObjectForKey(MODERATION_RESPONSE_ERROR_KEY)
}
