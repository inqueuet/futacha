package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

private const val APPLE_INTELLIGENCE_AVAILABLE_KEY = "futacha_apple_intelligence_available"
private const val APPLE_INTELLIGENCE_REASON_KEY = "futacha_apple_intelligence_unavailable_reason"
private const val SUMMARY_NOTIFICATION_NAME = "futacha.appleIntelligence.summary.requested"
private const val SUMMARY_RESPONSE_NOTIFICATION_NAME = "futacha.appleIntelligence.summary.completed"
private const val SUMMARY_CANCEL_NOTIFICATION_NAME = "futacha.appleIntelligence.summary.cancelled"
private const val SUMMARY_REQUEST_ID_KEY = "futacha_apple_intelligence_summary_request_id"
private const val SUMMARY_REQUEST_TEXT_KEY = "futacha_apple_intelligence_summary_request_text"
private const val SUMMARY_RESPONSE_ID_KEY = "futacha_apple_intelligence_summary_response_id"
private const val SUMMARY_RESPONSE_TEXT_KEY = "futacha_apple_intelligence_summary_response_text"
private const val SUMMARY_RESPONSE_ERROR_KEY = "futacha_apple_intelligence_summary_response_error"
private const val MODERATION_NOTIFICATION_NAME = "futacha.appleIntelligence.postModeration.requested"
private const val MODERATION_RESPONSE_NOTIFICATION_NAME = "futacha.appleIntelligence.postModeration.completed"
private const val MODERATION_CANCEL_NOTIFICATION_NAME = "futacha.appleIntelligence.postModeration.cancelled"
private const val MODERATION_REQUEST_ID_KEY = "futacha_apple_intelligence_moderation_request_id"
private const val MODERATION_REQUEST_TEXT_KEY = "futacha_apple_intelligence_moderation_request_text"
private const val MODERATION_RESPONSE_ID_KEY = "futacha_apple_intelligence_moderation_response_id"
private const val MODERATION_RESPONSE_TEXT_KEY = "futacha_apple_intelligence_moderation_response_text"
private const val MODERATION_RESPONSE_ERROR_KEY = "futacha_apple_intelligence_moderation_response_error"
private const val AI_REQUEST_TIMEOUT_MILLIS = 45_000L

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return IosOnDeviceAiService()
}

private class IosOnDeviceAiService : OnDeviceAiService {
    override suspend fun getAvailability(): AiAvailability = withContext(AppDispatchers.io) {
        val defaults = NSUserDefaults.standardUserDefaults()
        val hasAvailability = defaults.objectForKey(APPLE_INTELLIGENCE_AVAILABLE_KEY) != null
        if (!hasAvailability) {
            return@withContext AiAvailability(
                isAvailable = false,
                unavailableReason = "Apple Intelligence の機種判定ブリッジは未接続です。",
                providerLabel = "Apple Intelligence"
            )
        }
        val available = defaults.boolForKey(APPLE_INTELLIGENCE_AVAILABLE_KEY)
        val reason = defaults.stringForKey(APPLE_INTELLIGENCE_REASON_KEY)
        AiAvailability(
            isAvailable = available,
            unavailableReason = if (available) null else reason,
            supportsThreadSummary = available,
            supportsPostModeration = available,
            providerLabel = "Apple Intelligence"
        )
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        val sourceText = withContext(AppDispatchers.parsing) {
            buildThreadSummarySourceText(input)
        }
        if (sourceText.isBlank()) {
            return Result.success(
                withContext(AppDispatchers.parsing) {
                    buildExtractiveThreadSummary(input, providerLabel = "Apple Intelligence")
                }
            )
        }
        val generatedText = requestFoundationModelsSummary(sourceText).getOrElse {
            return Result.success(
                withContext(AppDispatchers.parsing) {
                    buildExtractiveThreadSummary(input, providerLabel = "Apple Intelligence")
                }
            )
        }
        return Result.success(
            withContext(AppDispatchers.parsing) {
                parseGeneratedThreadSummary(
                    input = input,
                    generatedText = generatedText,
                    providerLabel = "Apple Intelligence",
                    generatedTextHasHeadline = true
                )
            }
        )
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        if (input.posts.isEmpty()) {
            return Result.success(emptyList())
        }
        val sourceText = withContext(AppDispatchers.parsing) {
            buildPostModerationSourceText(input)
        }
        if (sourceText.isBlank()) {
            return Result.success(emptyList())
        }
        val responseText = requestFoundationModelsPostModeration(sourceText).getOrElse {
            return Result.success(emptyList())
        }
        val detected = withContext(AppDispatchers.parsing) {
            parsePostModerationResponse(responseText)
        }
        return Result.success(detected.values.toList())
    }
}

private suspend fun requestFoundationModelsSummary(sourceText: String): Result<String> {
    return requestFoundationModelsResponse(
        requestNotificationName = SUMMARY_NOTIFICATION_NAME,
        cancelNotificationName = SUMMARY_CANCEL_NOTIFICATION_NAME,
        requestIdKey = SUMMARY_REQUEST_ID_KEY,
        requestTextKey = SUMMARY_REQUEST_TEXT_KEY,
        responseNotificationName = SUMMARY_RESPONSE_NOTIFICATION_NAME,
        responseIdKey = SUMMARY_RESPONSE_ID_KEY,
        textKey = SUMMARY_RESPONSE_TEXT_KEY,
        errorKey = SUMMARY_RESPONSE_ERROR_KEY,
        sourceText = sourceText,
        emptyMessage = "Apple Intelligence returned an empty summary",
        timeoutMessage = "Apple Intelligence summary timed out"
    )
}

private suspend fun requestFoundationModelsPostModeration(sourceText: String): Result<String> {
    return requestFoundationModelsResponse(
        requestNotificationName = MODERATION_NOTIFICATION_NAME,
        cancelNotificationName = MODERATION_CANCEL_NOTIFICATION_NAME,
        requestIdKey = MODERATION_REQUEST_ID_KEY,
        requestTextKey = MODERATION_REQUEST_TEXT_KEY,
        responseNotificationName = MODERATION_RESPONSE_NOTIFICATION_NAME,
        responseIdKey = MODERATION_RESPONSE_ID_KEY,
        textKey = MODERATION_RESPONSE_TEXT_KEY,
        errorKey = MODERATION_RESPONSE_ERROR_KEY,
        sourceText = sourceText,
        emptyMessage = null,
        timeoutMessage = "Apple Intelligence post moderation timed out"
    )
}

private suspend fun requestFoundationModelsResponse(
    requestNotificationName: String,
    cancelNotificationName: String,
    requestIdKey: String,
    requestTextKey: String,
    responseNotificationName: String,
    responseIdKey: String,
    textKey: String,
    errorKey: String,
    sourceText: String,
    emptyMessage: String?,
    timeoutMessage: String
): Result<String> {
    val requestId = NSUUID().UUIDString()
    val deferred = CompletableDeferred<Result<String>>()
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = responseNotificationName,
        `object` = null,
        queue = null
    ) { notification ->
        val userInfo = notification?.userInfo ?: return@addObserverForName
        val responseId = userInfo[responseIdKey] as? String
        if (responseId != requestId) return@addObserverForName
        val error = (userInfo[errorKey] as? String).orEmpty().trim()
        val text = (userInfo[textKey] as? String).orEmpty().trim()
        if (error.isNotBlank()) {
            deferred.complete(Result.failure(IllegalStateException(error)))
        } else if (emptyMessage != null && text.isBlank()) {
            deferred.complete(Result.failure(IllegalStateException(emptyMessage)))
        } else {
            deferred.complete(Result.success(text))
        }
    }
    return try {
        withContext(Dispatchers.Main) {
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = requestNotificationName,
                `object` = null,
                userInfo = mapOf(
                    requestIdKey to requestId,
                    requestTextKey to sourceText
                )
            )
        }
        val response = withTimeoutOrNull(AI_REQUEST_TIMEOUT_MILLIS) {
            deferred.await()
        }
        if (response == null) {
            postFoundationModelsCancellation(cancelNotificationName, requestIdKey, requestId)
            Result.failure(IllegalStateException(timeoutMessage))
        } else {
            response
        }
    } catch (error: CancellationException) {
        postFoundationModelsCancellation(cancelNotificationName, requestIdKey, requestId)
        throw error
    } finally {
        NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
}

private suspend fun postFoundationModelsCancellation(
    cancelNotificationName: String,
    requestIdKey: String,
    requestId: String
) {
    withContext(NonCancellable + Dispatchers.Main) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = cancelNotificationName,
            `object` = null,
            userInfo = mapOf(requestIdKey to requestId)
        )
    }
}
