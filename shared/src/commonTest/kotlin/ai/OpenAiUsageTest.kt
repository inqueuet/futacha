package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.openAiUsageLines
import com.valoser.futacha.shared.ui.board.openAiWaitText
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiUsageTest {
    private fun post(text: String) = Post(id = "1", author = "", subject = null, timestamp = "", messageHtml = text, imageUrl = null, thumbnailUrl = null)
    private fun response(count: Int) = buildJsonObject {
        putJsonArray("results") { repeat(count) { add(buildJsonObject {
            putJsonObject("category_scores") { OPENAI_MODERATION_CATEGORIES.keys.forEach { put(it, 0.01) } }
        }) } }
    }.toString()

    @Test fun real429ShapePersists17419SecondsAcrossRestartAndNeverStoresSecrets() = runBlocking {
        var now = 1_000_000L
        val storage = MemoryAiStorage()
        suspend fun store() = AiConnectionStore(storage, OpenAiRequestQueue({ now }, { now += it }, { 0 })).also {
            it.load(); it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-secret")
        }
        var calls = 0
        val first = store()
        val service = OpenAiService(first, first.state.value, HttpClient(MockEngine {
            calls++
            respond("""{"error":{"message":"Too Many Requests test-secret private-body","type":"invalid_request_error","code":null}}""",
                HttpStatusCode.TooManyRequests, headersOf("Retry-After", "17419"))
        }))
        assertTrue(service.classifyPosts(PostModerationInput("t", listOf(post("private-body")))).isFailure)
        service.close()
        assertEquals(1, calls)
        assertEquals(18_419_000L, first.usage.state.value.waitUntil)
        assertEquals(17_419_000L, first.usage.state.value.serverRetryAfterMillis)
        assertEquals(1L, first.usage.state.value.rateLimitedRequests)
        assertNull(first.usage.state.value.remainingTokens)
        assertFalse(storage.usage!!.contains("test-secret"))
        assertFalse(storage.usage!!.contains("private-body"))
        now += 1_000
        val restored = store()
        val blocked = OpenAiService(restored, restored.state.value, HttpClient(MockEngine { calls++; respond(response(1)) }))
        assertTrue(blocked.classifyPosts(PostModerationInput("t", listOf(post("private-body")))).isFailure)
        assertEquals(1, calls)
        assertEquals("4時間50分18秒", openAiWaitText(restored.usage.state.value.waitUntil, now))
        now = restored.usage.state.value.waitUntil
        assertTrue(blocked.classifyPosts(PostModerationInput("t", listOf(post("private-body")))).isSuccess)
        assertEquals(2, calls)
        blocked.close()
    }

    @Test fun tokenBudgetDelaysAndCacheDoesNotConsumeIt() = runBlocking {
        var now = 0L
        val store = AiConnectionStore(MemoryAiStorage(), OpenAiRequestQueue({ now }, { now += it }, { 0 })).also {
            it.load(); it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-only-key")
        }
        val starts = mutableListOf<Long>()
        val bodies = mutableListOf<String>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            starts += now
            val inputs = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray
            assertTrue(inputs.sumOf { estimateModerationTokens(it.jsonPrimitive.content) } <= 8_000)
            bodies += inputs.map { it.jsonPrimitive.content }
            respond(response(inputs.size))
        }))
        val text = (1..900).joinToString("") { "日本語😀$it" }
        val input = PostModerationInput("t", listOf(post(text)))
        assertTrue(service.classifyPosts(input).isSuccess)
        assertEquals(text, bodies.joinToString(""))
        assertTrue(starts.size >= 2)
        assertTrue(starts.last() >= 60_000)
        val attempts = store.usage.state.value.hours.sumOf { it.requests }
        val tokens = store.usage.state.value.hours.sumOf { it.tokens }
        assertTrue(service.classifyPosts(input).isSuccess)
        assertEquals(attempts, store.usage.state.value.hours.sumOf { it.requests })
        assertEquals(tokens, store.usage.state.value.hours.sumOf { it.tokens })
        service.close()
    }

    @Test fun sameBodyInOneBatchIsSentOnceAndEveryPostGetsItsDecision() = runBlocking {
        var now = 0L
        val store = AiConnectionStore(MemoryAiStorage(), OpenAiRequestQueue({ now }, { now += it }, { 0 })).also {
            it.load(); it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-only-key")
        }
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val inputs = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray
            assertEquals(1, inputs.size)
            respond(response(1))
        }))
        val body = "同じ本文を別のレス番号で繰り返す場合も通信量を節約するためのテストです"
        val posts = (1..20).map { post(body).copy(id = "$it") }
        val decisions = service.classifyPosts(PostModerationInput("t", posts)).getOrThrow()
        assertEquals(posts.map { it.id }, decisions.map { it.postId })
        assertEquals(estimateModerationTokens(body).toLong(), store.usage.state.value.hours.sumOf { it.tokens })
        service.close()
    }

    @Test fun serverTokenAndRequestResetAreRespectedEvenOnSuccessAndMissingHeadersStayUnknown() = runBlocking {
        var now = 1_000L
        val usage = OpenAiUsageTracker(MemoryAiStorage()) { now }
        usage.response(200, headersOf(
            "x-ratelimit-limit-requests" to listOf("250"), "x-ratelimit-remaining-requests" to listOf("0"),
            "x-ratelimit-reset-requests" to listOf("1m2.5s"),
            "x-ratelimit-remaining-tokens" to listOf("10"), "x-ratelimit-reset-tokens" to listOf("10s"),
            "x-ratelimit-remaining-project-tokens" to listOf("1"), "x-ratelimit-reset-project-tokens" to listOf("2m")
        ), false)
        assertEquals(120_000L, usage.requiredWait(100))
        now += 120_000
        assertEquals(0L, usage.requiredWait(100))
        usage.response(429, headersOf(), false)
        assertNull(usage.state.value.remainingRequests)
        assertNull(usage.state.value.remainingTokens)
        assertNull(usage.state.value.serverRetryAfterMillis)
        val lines = openAiUsageLines(usage.state.value, now).joinToString("\n")
        assertContains(lines, "実消費量・課金額ではありません")
        assertContains(lines, "上限不明")
        assertContains(lines, "未通知")
    }

    @Test fun dailyBudgetAndCancelledWaitCannotSend() = runBlocking {
        var now = 1_000L
        val storage = MemoryAiStorage().apply {
            usage = Json.encodeToString(OpenAiUsageState(hours = listOf(AiUsageBucket(0, 4_500, 2_000))))
        }
        val tracker = OpenAiUsageTracker(storage) { now }
        val queue = OpenAiRequestQueue({ now }, { now += it }, { 0 })
        var calls = 0
        assertFailsWith<OpenAiFailure> { queue.execute(20, tracker) { calls++ } }
        assertEquals(0, calls)
        assertEquals("local_requests", tracker.state.value.waitReason)
        now = 25 * 3_600_000
        queue.execute(20, tracker) { calls++ }
        assertEquals(1, calls)
        tracker.attempt(7_980)
        val waiting = CompletableDeferred<Unit>()
        val cancelledQueue = OpenAiRequestQueue({ now }, { waiting.complete(Unit); awaitCancellation() }, { 0 })
        val task = launch { cancelledQueue.execute(100, tracker) { calls++ } }
        waiting.await(); task.cancelAndJoin()
        assertEquals(1, calls)
        assertEquals(2, tracker.state.value.hours.sumOf { it.requests })
    }

    @Test fun storageFailureAndPermanentQuotaAreExplainedWithoutInventingCountdown() = runBlocking {
        val tracker = OpenAiUsageTracker(object : AiConnectionStorage {
            override fun read(): String? = null
            override fun write(value: String) = Unit
            override fun writeUsage(value: String) { error("disk full") }
        }) { 1_000L }
        tracker.response(429, headersOf(), true)
        val lines = openAiUsageLines(tracker.state.value, 1_000).joinToString("\n")
        assertContains(lines, "時間経過だけで解除されるとは限りません")
        assertContains(lines, "保存・復元できませんでした")
        assertFalse(lines.contains("待機は終了"))
    }

    @Test fun parsesResetDurationsAndRoundsCountdownUp() {
        assertEquals(62_501L, openAiResetMillis("1m2.5001s"))
        assertEquals(3_600_005L, openAiResetMillis("1h5ms"))
        assertNull(openAiResetMillis("-1s"))
        assertNull(openAiResetMillis("1s junk"))
        assertNull(openAiResetMillis("NaN"))
        assertEquals("0時間0分1秒", openAiWaitText(1, 0))
        assertEquals("0時間0分0秒", openAiWaitText(1, 2))
    }
}
