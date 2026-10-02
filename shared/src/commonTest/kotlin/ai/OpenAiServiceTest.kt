package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

internal class MemoryAiStorage : AiConnectionStorage {
    var credentials: String? = null
    var cache: String? = null
    var usage: String? = null
    override fun readUsage() = usage
    override fun writeUsage(value: String) { usage = value }
    override fun read() = credentials
    override fun write(value: String) { credentials = value }
    override fun readCache() = cache
    override fun writeCache(value: String) { cache = value }
}

class OpenAiServiceTest {
    private fun post(id: Int, text: String = "本文$id") = Post(
        id = id.toString(), author = "送信しない名前", subject = null, timestamp = "",
        messageHtml = text, imageUrl = "https://example.invalid/private.jpg", thumbnailUrl = null, mail = "送信しないメール"
    )
    private suspend fun store(storage: MemoryAiStorage = MemoryAiStorage()): AiConnectionStore =
        AiConnectionStore(storage, run {
            var now = 0L
            OpenAiRequestQueue({ now }, { now += it }, { 0L })
        }).also {
            it.load()
            it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-only-key")
        }
    private fun summaryResponse() = buildJsonObject {
        put("status", "completed")
        putJsonArray("output") {
            add(buildJsonObject {
                put("type", "message")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "output_text")
                        put("text", """{"headline":"全体の要約","bullets":["最後の話題まで含める"]}""")
                    })
                }
            })
        }
    }.toString()
    private fun moderationResponse(count: Int, harassment: Boolean = false, sexual: Boolean = false) = buildJsonObject {
        putJsonArray("results") {
            repeat(count) {
                add(buildJsonObject {
                    put("flagged", harassment || sexual)
                    putJsonObject("categories") {
                        put("harassment", harassment); put("harassment/threatening", false)
                        put("hate", false); put("hate/threatening", false); put("sexual", sexual)
                    }
                    putJsonObject("category_scores") {
                        OPENAI_MODERATION_CATEGORIES.keys.forEach { put(it, 0.01) }
                        put("harassment", if (harassment) 0.8 else 0.02); put("harassment/threatening", 0.01)
                        put("hate", 0.01); put("hate/threatening", 0.01); put("sexual", if (sexual) 0.99 else 0.01)
                    }
                })
            }
        }
    }.toString()

    @Test fun moderationExcludesQuotedLinesButKeepsAuthoredReply() = runBlocking {
        val store = store()
        var calls = 0
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            calls++
            val texts = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray
            assertEquals(listOf("通常の返答"), texts.map { it.jsonPrimitive.content })
            respond(moderationResponse(1))
        }))
        val results = service.classifyPosts(PostModerationInput("t", listOf(
            post(1, "&#62;引用内容<br>＞引用だけ"),
            post(2, "&gt;引用内容<br>通常の返答")
        ))).getOrThrow()
        assertEquals(1, calls)
        assertEquals(2, results.size)
        assertTrue(results.none { it.shouldHide })
        service.close()
    }

    @Test fun summarySendsAll1000PostsAndFullBodiesWithoutPrivateMetadata() = runBlocking {
        val store = store()
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            assertEquals("/v1/responses", request.url.encodedPath)
            assertEquals("Bearer test-only-key", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val text = body["input"]!!.jsonPrimitive.content
            assertContains(text, "No.1000")
            assertContains(text, "末尾" + "長文".repeat(8000))
            assertFalse(text.contains("送信しない"))
            assertFalse(text.contains("private.jpg"))
            assertEquals(false, body["store"]!!.jsonPrimitive.boolean)
            assertEquals("gpt-4.1-mini", body["model"]!!.jsonPrimitive.content)
            respond(summaryResponse())
        })
        val service = OpenAiService(store, store.state.value, client)
        try {
            val input = ThreadSummaryInput("123", "タイトル", (1..1000).map { post(it, if (it == 1000) "末尾" + "長文".repeat(8000) else "本文$it") })
            assertTrue(service.summarizeThread(input).isSuccess)
            assertTrue(service.summarizeThread(input).isSuccess)
            assertEquals(1, calls)
        } finally { service.close() }
    }

    @Test fun moderationSendsMoreThanEightPostsAndCachesOnlyUnchangedBodies() = runBlocking {
        val store = store()
        val sizes = mutableListOf<Int>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            assertEquals("/v1/moderations", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val input = body["input"]!!.jsonArray
            sizes += input.size
            if (input.size > 1) assertContains(input.last().jsonPrimitive.content, "終端")
            respond(moderationResponse(input.size, harassment = true))
        }))
        try {
            val posts = (1..100).map { post(it, "本文$it" + "長".repeat(1000) + "終端") }
            val first = service.classifyPosts(PostModerationInput("t", posts)).getOrThrow()
            assertEquals(posts.map { it.id }.toSet(), first.map { it.postId }.toSet())
            assertTrue(first.all { it.shouldHide })
            service.classifyPosts(PostModerationInput("t", posts + post(101))).getOrThrow()
            assertEquals(List(50) { 2 } + 1, sizes)
        } finally { service.close() }
    }

    @Test fun moderationRetries429ThroughSharedQueueAndCachesSuccessfulResponse() = runBlocking {
        var now = 0L
        val queue = OpenAiRequestQueue({ now }, { now += it }, { 0L })
        val store = AiConnectionStore(MemoryAiStorage(), queue).also {
            it.load()
            it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-only-key")
        }
        val starts = mutableListOf<Long>()
        fun client() = HttpClient(MockEngine {
            starts += now
            if (starts.size == 1) respond("""{"error":{"code":"rate_limit_exceeded"}}""",
                HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "5"))
            else respond(moderationResponse(2))
        })
        val input = PostModerationInput("t", listOf(post(1), post(2)))
        val first = OpenAiService(store, store.state.value, client())
        try { assertEquals(2, first.classifyPosts(input).getOrThrow().size) } finally { first.close() }
        val second = OpenAiService(store, store.state.value, client())
        try { assertEquals(2, second.classifyPosts(input).getOrThrow().size) } finally { second.close() }
        assertEquals(listOf(0L, 5_000L), starts)
    }

    @Test fun sexualFlagAloneDoesNotBecomeHarassmentAndWrongResultCountFails() {
        val onlySexual = parseOpenAiModeration(Json.parseToJsonElement(moderationResponse(1, sexual = true)).jsonObject, listOf("1"))
        assertFalse(onlySexual.single().shouldHide)
        assertFails { parseOpenAiModeration(Json.parseToJsonElement(moderationResponse(0)).jsonObject, listOf("1")) }
    }

    @Test fun capacityFailureSplitsAdaptivelyWithoutDroppingPosts() = runBlocking {
        val store = store()
        val accepted = mutableListOf<String>()
        val attempted = mutableListOf<Int>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val values = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray
            attempted += values.size
            if (values.size > 7) respond("""{"error":{"code":"too_many_inputs"}}""", HttpStatusCode.BadRequest)
            else {
                accepted += values.map { it.jsonPrimitive.content }
                respond(moderationResponse(values.size))
            }
        }))
        try {
            val posts = (1..21).map { post(it) }
            assertEquals(21, service.classifyPosts(PostModerationInput("t", posts)).getOrThrow().size)
            assertEquals(21, attempted.first())
            assertEquals(posts.map(::openAiPostText), accepted)
        } finally { service.close() }
    }

    @Test fun longSinglePostSplitsIntoSeparateCallsAndKeepsFlagFromAnyPart() = runBlocking {
        val store = store()
        val accepted = mutableListOf<String>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val text = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray.single().jsonPrimitive.content
            if (text.length > 8) respond("{}", HttpStatusCode.PayloadTooLarge)
            else {
                accepted += text
                respond(moderationResponse(1, harassment = "攻撃" in text))
            }
        }))
        try {
            val text = "あいうえおかきく攻撃さしすせ"
            val result = service.classifyPosts(PostModerationInput("t", listOf(post(1, text)))).getOrThrow()
            assertEquals(text, accepted.joinToString(""))
            assertTrue(result.single().shouldHide)
        } finally { service.close() }
    }

    @Test fun summaryCapacityFailureSummarizesEveryPartThenCombines() = runBlocking {
        val store = store()
        val inputs = mutableListOf<String>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val text = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonPrimitive.content
            inputs += text
            if (inputs.size == 1) respond("""{"error":{"code":"context_length_exceeded"}}""", HttpStatusCode.BadRequest)
            else respond(summaryResponse())
        }))
        try {
            assertTrue(service.summarizeThread(ThreadSummaryInput("t", null, (1..90).map { post(it) })).isSuccess)
            assertEquals(4, inputs.size)
            assertEquals(inputs.first(), inputs[1] + inputs[2])
            assertContains(inputs.last(), "部分要約")
        } finally { service.close() }
    }

    @Test fun quotaErrorNeverRetriesOrLeaksResponseAndApiKey() = runBlocking {
        val store = store()
        var calls = 0
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine {
            calls++
            respond("""{"error":{"message":"test-only-key private body","code":"insufficient_quota"}}""", HttpStatusCode.TooManyRequests)
        }))
        try {
            val result = service.summarizeThread(ThreadSummaryInput("t", null, listOf(post(1))))
            assertTrue(result.isFailure)
            assertEquals(1, calls)
            assertFalse(result.exceptionOrNull().toString().contains("test-only-key"))
            assertContains(result.exceptionOrNull()!!.message!!, "利用制限")
        } finally { service.close() }
    }

    @Test fun incompleteRefusalAndTruncatedSourceNeverCountAsSuccess() = runBlocking {
        assertFails { parseOpenAiSummary(Json.parseToJsonElement("""{"status":"incomplete","output":[]}""").jsonObject, "OpenAI") }
        assertFails { parseOpenAiSummary(Json.parseToJsonElement("""{"status":"completed","output":[{"content":[{"type":"refusal"}]}]}""").jsonObject, "OpenAI") }
        val store = store()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { error("Must not send truncated source") }))
        try {
            assertContains(service.summarizeThread(ThreadSummaryInput("t", null, listOf(post(1)), isTruncated = true)).exceptionOrNull()!!.message!!, "途中")
        } finally { service.close() }
    }

    @Test fun changingProviderWhileRequestRunsDiscardsTheOldResponse() = runBlocking {
        val store = store()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine {
            store.save(AiProvider.DEVICE, AiProvider.DEVICE, "gpt-4.1-mini")
            respond(summaryResponse())
        }))
        try {
            assertFailsWith<CancellationException> { service.summarizeThread(ThreadSummaryInput("t", null, listOf(post(1)))) }
            Unit
        } finally { service.close() }
    }

    @Test fun persistentCacheSurvivesServiceRecreationAndContainsNoKeyOrSourceBody() = runBlocking {
        for (shouldHide in listOf(false, true)) {
            val storage = MemoryAiStorage()
            val firstStore = store(storage)
            val input = PostModerationInput("t", listOf(post(1, "秘密の入力本文")))
            val first = OpenAiService(firstStore, firstStore.state.value, HttpClient(MockEngine { respond(moderationResponse(1, harassment = shouldHide)) }))
            try { assertEquals(shouldHide, first.classifyPosts(input).getOrThrow().single().shouldHide) }
            finally { first.close() }
            assertFalse(storage.cache!!.contains("秘密の入力本文"))
            assertFalse(storage.cache!!.contains("test-only-key"))
            val secondStore = AiConnectionStore(storage).also { it.load() }
            val second = OpenAiService(secondStore, secondStore.state.value, HttpClient(MockEngine { error("Must use persistent cache") }))
            try { assertEquals(shouldHide, second.classifyPosts(input).getOrThrow().single().shouldHide) }
            finally { second.close() }
        }
    }

    @Test fun independentSelectionsPersistAndDeletingKeyRequiresLocalChoices() = runBlocking {
        val storage = MemoryAiStorage()
        val first = store(storage)
        first.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1")
        val next = AiConnectionStore(storage).also { it.load() }
        assertEquals(AiProvider.DEVICE, next.state.value.summaryProvider)
        assertEquals(AiProvider.OPENAI, next.state.value.moderationProvider)
        assertEquals("gpt-4.1", next.state.value.summaryModel)
        assertFailsWith<IllegalArgumentException> { next.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1", "") }
        next.save(AiProvider.DEVICE, AiProvider.DEVICE, "gpt-4.1", "")
        assertFalse(next.state.value.hasApiKey)
        assertFalse(next.state.value.toString().contains("test-only-key"))
    }

    @Test fun splitPreservesNewlinesAndUnicode() {
        listOf("本文\n長い文章\n末尾", "あ😀いう", "😀😀").forEach { input ->
            val (a, b) = splitAiText(input)
            assertEquals(input, a + b)
            assertFalse(a.last().isHighSurrogate())
            assertFalse(b.first().isLowSurrogate())
        }
    }

    @Test fun externalSummaryCannotBeSelectedOrRestoredFromOldSettings() = runBlocking {
        val storage = MemoryAiStorage()
        val store = store(storage)
        assertFailsWith<IllegalArgumentException> { store.save(AiProvider.OPENAI, AiProvider.OPENAI, "gpt-4.1-mini") }
        storage.credentials = storage.credentials!!.replace("\"summaryProvider\":\"DEVICE\"", "\"summaryProvider\":\"OPENAI\"")
        // Defaults may be omitted by serialization; explicitly add the old selection.
        storage.credentials = storage.credentials!!.replaceFirst("{", "{\"summaryProvider\":\"OPENAI\",")
        val restored = AiConnectionStore(storage).also { it.load() }
        assertEquals(AiProvider.DEVICE, restored.state.value.summaryProvider)
        assertEquals(AiProvider.OPENAI, restored.state.value.moderationProvider)
        assertTrue(restored.state.value.hasApiKey)
    }

    @Test fun categoryChangesReuseCachedScoresAndOnlySelectedCategoriesCanHide() = runBlocking {
        val storage = MemoryAiStorage()
        val store = store(storage)
        val input = PostModerationInput("t", listOf(post(1)))
        val first = OpenAiService(store, store.state.value, HttpClient(MockEngine { respond(moderationResponse(1, sexual = true)) }))
        try { assertFalse(first.classifyPosts(input).getOrThrow().single().shouldHide) } finally { first.close() }
        store.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", moderationCategories = setOf("sexual"))
        val restored = AiConnectionStore(storage).also { it.load() }
        assertEquals(setOf("sexual"), restored.state.value.moderationCategories)
        val second = OpenAiService(restored, restored.state.value, HttpClient(MockEngine { error("Category changes must reuse scores") }))
        try { assertTrue(second.classifyPosts(input).getOrThrow().single().shouldHide) } finally { second.close() }
    }

    @Test fun thresholdChangesUseCachedScoresAndAutomaticHidingIsIndependent() = runBlocking {
        val store = store()
        val input = PostModerationInput("t", listOf(post(1)))
        val first = OpenAiService(store, store.state.value, HttpClient(MockEngine { respond(moderationResponse(1, harassment = true)) }))
        try { assertTrue(first.classifyPosts(input).getOrThrow().single().shouldHide) } finally { first.close() }
        store.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", moderationThreshold = 0.9f, moderationAutoHide = false)
        val second = OpenAiService(store, store.state.value, HttpClient(MockEngine { error("Threshold changes must not resend unchanged posts") }))
        try {
            assertFalse(second.classifyPosts(input).getOrThrow().single().shouldHide)
            assertFalse(second.automaticallyHideModeratedPosts)
        } finally { second.close() }
        store.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", moderationThreshold = 0.8f, moderationAutoHide = false)
        val third = OpenAiService(store, store.state.value, HttpClient(MockEngine { error("Must re-evaluate cached scores") }))
        try {
            assertTrue(third.classifyPosts(input).getOrThrow().single().shouldHide)
            assertFalse(third.automaticallyHideModeratedPosts)
        } finally { third.close() }
    }

    @Test fun invalidThresholdAndInvalidScoresAreRejected() = runBlocking {
        val store = store()
        for (threshold in listOf(Float.NaN, -1f, 0f, 1.1f)) {
            assertFailsWith<IllegalArgumentException> {
                store.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", moderationThreshold = threshold)
            }
        }
        val invalid = Json.parseToJsonElement(moderationResponse(1).replace("0.02", "1.02")).jsonObject
        assertFails { parseOpenAiModeration(invalid, listOf("1")) }
        Unit
    }
}
