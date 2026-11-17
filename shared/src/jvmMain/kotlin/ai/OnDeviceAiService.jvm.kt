package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.desktop.DesktopPlatform
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

actual fun createOnDeviceAiService(platformContext: Any?): OnDeviceAiService {
    return if (DesktopPlatform.isMac) MacOnDeviceAiService() else JvmOnDeviceAiService()
}

private class JvmOnDeviceAiService : OnDeviceAiService {
    override suspend fun getAvailability(): AiAvailability {
        return AiAvailability(
            isAvailable = false,
            unavailableReason = "この環境の端末内AIには未対応です。OpenAIの荒らし判定は利用できます。"
        )
    }

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
        return Result.failure(IllegalStateException("この環境では端末内要約を利用できません。"))
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
        return Result.failure(IllegalStateException("端末内判定を利用できません。設定でOpenAIを選択してください。"))
    }
}

internal class MacOnDeviceAiService(
    private val call: (String, Array<out Pair<String, String>>) -> JsonObject = { op, fields -> MacAiNative.call(op, *fields) }
) : OnDeviceAiService {
    private val active = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var closed = false

    override suspend fun getAvailability(): AiAvailability = withContext(Dispatchers.IO) {
        try {
            val response = call("availability", emptyArray())
            val summary = response["summary"]?.jsonPrimitive?.booleanOrNull == true
            val moderation = response["moderation"]?.jsonPrimitive?.booleanOrNull == true
            AiAvailability(summary || moderation,
                response["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                summary, moderation, "Apple Intelligence")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { unavailable() }
        catch (_: LinkageError) { unavailable() }
    }

    private fun unavailable() = AiAvailability(false,
        "Macの端末内AIを利用できません。OpenAIの荒らし判定は利用できます。", providerLabel = "Apple Intelligence")

    override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> = result {
        val source = buildThreadSummarySourceText(input)
        require(source.isNotBlank()) { "要約できる本文がありません。" }
        val text = generate("summary", source)
        check(text.isNotBlank()) { "端末AIから要約を取得できませんでした。" }
        parseGeneratedThreadSummary(input, text, "Apple Intelligence", generatedTextHasHeadline = true)
    }

    override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> = result {
        buildPostModerationSourceChunks(input).flatMap { source ->
            parsePostModerationBatchResponse(generate("moderation", source), source)
        }.distinctBy { it.postId }
    }

    private suspend fun generate(operation: String, text: String): String = withContext(Dispatchers.IO) {
        check(!closed)
        val id = UUID.randomUUID().toString()
        active.add(id)
        try {
            withTimeout(45_000) {
                var response = call(operation, arrayOf("id" to id, "text" to text))
                while (response["status"]?.jsonPrimitive?.content == "pending") {
                    ensureActive()
                    check(!closed && id in active) { "AI処理を中止しました。" }
                    delay(100)
                    response = call("poll", arrayOf("id" to id))
                }
                check(response["status"]?.jsonPrimitive?.content == "done") { "AI処理を中止しました。" }
                check(!closed && id in active) { "AI処理を中止しました。" }
                response["text"]?.jsonPrimitive?.content ?: error("端末AIの応答が不正です。")
            }
        } finally {
            active.remove(id)
            withContext(NonCancellable) { runCatching { call("cancel", arrayOf("id" to id)) } }
        }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
    catch (_: LinkageError) { Result.failure(IllegalStateException("MacのAI接続を読み込めませんでした。")) }

    override fun cancelActiveRequests() {
        active.toList().forEach { id ->
            active.remove(id)
            runCatching { call("cancel", arrayOf("id" to id)) }
        }
    }
    override fun close() { closed = true; cancelActiveRequests() }
}
