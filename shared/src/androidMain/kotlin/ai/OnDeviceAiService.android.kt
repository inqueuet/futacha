package com.valoser.futacha.shared.ai

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

private const val AICORE_PACKAGE = "com.google.android.aicore"

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return AndroidOnDeviceAiService(platformContext as? Context)
}

private class AndroidOnDeviceAiService(
    private val context: Context?
) : OnDeviceAiService {
    override suspend fun getAvailability(): AiAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return AiAvailability(
                isAvailable = false,
                unavailableReason = "Gemini Nano 要約には Android 8.0 以降が必要です。",
                providerLabel = "Gemini Nano"
            )
        }
        val appContext = context?.applicationContext ?: return AiAvailability(
            isAvailable = false,
            unavailableReason = "Android Context がないため端末AIを確認できません。",
            providerLabel = "Gemini Nano"
        )
        val hasAiCore = runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
        }.isSuccess

        if (!hasAiCore) {
            return AiAvailability(
                isAvailable = false,
                unavailableReason = "Gemini Nano / AICore がこの端末で確認できません。",
                providerLabel = "Gemini Nano"
            )
        }
        return withContext(AppDispatchers.io) {
            val summarizer = runCatching {
                Summarization.getClient(buildSummarizerOptions(appContext))
            }.getOrElse { error ->
                return@withContext AiAvailability(
                    isAvailable = false,
                    unavailableReason = error.message ?: "Gemini Nano 要約の可用性を確認できませんでした。",
                    providerLabel = "Gemini Nano"
                )
            }
            try {
                val summaryStatus = summarizer.checkFeatureStatus().get()
                val promptStatus = runCatching {
                    val promptModel = Generation.getClient()
                    try {
                        promptModel.checkStatus()
                    } finally {
                        promptModel.close()
                    }
                }.getOrNull()
                when (summaryStatus) {
                    FeatureStatus.AVAILABLE -> AiAvailability(
                        isAvailable = true,
                        supportsThreadSummary = true,
                        supportsPostModeration = promptStatus == FeatureStatus.AVAILABLE,
                        providerLabel = "Gemini Nano"
                    )
                    FeatureStatus.DOWNLOADABLE -> AiAvailability(
                        isAvailable = false,
                        unavailableReason = "Gemini Nano のモデルを準備中です。しばらくしてから再確認してください。",
                        providerLabel = "Gemini Nano"
                    )
                    FeatureStatus.DOWNLOADING -> AiAvailability(
                        isAvailable = false,
                        unavailableReason = "Gemini Nano のモデルをダウンロード中です。完了後に利用できます。",
                        providerLabel = "Gemini Nano"
                    )
                    else -> AiAvailability(
                        isAvailable = false,
                        unavailableReason = "この端末構成では Gemini Nano 要約を利用できません。",
                        providerLabel = "Gemini Nano"
                    )
                }
            } catch (error: Throwable) {
                AiAvailability(
                    isAvailable = false,
                    unavailableReason = error.message ?: "Gemini Nano 要約の可用性を確認できませんでした。",
                    providerLabel = "Gemini Nano"
                )
            } finally {
                summarizer.close()
            }
        }
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        val appContext = context?.applicationContext
            ?: return Result.failure(IllegalStateException("Android Context is not available"))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(IllegalStateException("Android 8.0 or later is required"))
        }
        val sourceText = buildThreadSummarySourceText(input)
        if (sourceText.isBlank()) {
            return Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
        }
        return withContext(AppDispatchers.io) {
            val summarizer = runCatching {
                Summarization.getClient(buildSummarizerOptions(appContext))
            }.getOrElse {
                return@withContext Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
            }
            try {
                val status = summarizer.checkFeatureStatus().get()
                if (status != FeatureStatus.AVAILABLE) {
                    return@withContext Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
                }
                val request = SummarizationRequest.builder(sourceText).build()
                val result = summarizer.runInference(request).get()
                Result.success(
                    parseGeneratedThreadSummary(
                        input = input,
                        generatedText = result.getSummary(),
                        providerLabel = "Gemini Nano",
                        generatedTextHasHeadline = false
                    )
                )
            } catch (error: Throwable) {
                Result.success(buildExtractiveThreadSummary(input, providerLabel = "Gemini Nano"))
            } finally {
                summarizer.close()
            }
        }
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || input.posts.isEmpty()) {
            return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
        }
        val sourceText = buildPostModerationSourceText(input)
        if (sourceText.isBlank()) {
            return Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
        }
        return withContext(AppDispatchers.io) {
            val promptModel = runCatching {
                Generation.getClient()
            }.getOrElse {
                return@withContext Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
            }
            try {
                if (promptModel.checkStatus() != FeatureStatus.AVAILABLE) {
                    return@withContext Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
                }
                val response = promptModel.generateContent(buildPostModerationPrompt(sourceText))
                val responseText = response.candidates.firstOrNull()?.text.orEmpty()
                val detected = parsePostModerationResponse(responseText)
                Result.success(
                    input.posts.map { post ->
                        detected[post.id] ?: PostModerationResult(postId = post.id, shouldHide = false)
                    }
                )
            } catch (error: Throwable) {
                Result.success(input.posts.map { PostModerationResult(postId = it.id, shouldHide = false) })
            } finally {
                promptModel.close()
            }
        }
    }
}

private fun buildSummarizerOptions(context: Context): SummarizerOptions {
    return SummarizerOptions.builder(context)
        .setInputType(SummarizerOptions.InputType.CONVERSATION)
        .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
        .setLanguage(SummarizerOptions.Language.JAPANESE)
        .build()
}

private fun buildPostModerationPrompt(posts: String): String {
    return """
        You are a local moderation assistant for a Japanese imageboard thread.
        Hide only posts that are clearly disruptive spam, harassment, repeated flooding, threats, or unrelated vandalism.
        Do not hide ordinary disagreement, jokes, criticism, short replies, or quoted text.
        Do not hide the opening post. Prefer hiding no posts when uncertain. Return at most 8 hidden posts.
        Return one line per hidden post only.
        Format exactly: postId<TAB>HIDE<TAB>short Japanese reason
        Posts:
        $posts
    """.trimIndent()
}
