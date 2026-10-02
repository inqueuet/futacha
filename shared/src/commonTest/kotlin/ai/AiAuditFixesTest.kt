package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Regression tests for docs/code-audit-2026-10-01.md (AI area). */
class AiAuditFixesTest {
    private fun post(id: String, body: String) =
        Post(id, author = null, subject = null, timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)
    private fun scores(count: Int, harassment: (Int) -> Boolean = { false }) = buildJsonObject {
        putJsonArray("results") { repeat(count) { index -> add(buildJsonObject {
            putJsonObject("category_scores") {
                OPENAI_MODERATION_CATEGORIES.keys.forEach { put(it, 0.01) }
                put("harassment", if (harassment(index)) 0.95 else 0.01)
            }
        }) } }
    }.toString()
    private class Clock(var now: Long = 1_000_000L) {
        val queue = OpenAiRequestQueue({ now }, { now += it }, { 0 })
    }
    private suspend fun store(clock: Clock = Clock(), storage: MemoryAiStorage = MemoryAiStorage(), keys: Int = 1) =
        AiConnectionStore(storage, clock.queue).also { store ->
            store.load()
            repeat(keys) { store.saveApiKey(null, "接続${it + 1}", "test-audit-${it + 1}") }
            store.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL)
        }
    private fun texts(request: io.ktor.client.request.HttpRequestData) =
        Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray.map { it.jsonPrimitive.content }

    // A4
    @Test fun unreadableSettingsRefuseSaveAndCanBeResetExplicitly() = runBlocking {
        val storage = MemoryAiStorage().apply { credentials = "{broken" }
        val store = AiConnectionStore(storage).also { it.load() }
        assertNotNull(store.state.value.storageError)
        assertFailsWith<IllegalStateException> { store.save(AiProvider.DEVICE, AiProvider.DEVICE, DEFAULT_OPENAI_SUMMARY_MODEL) }
        assertEquals("{broken", storage.credentials, "A refused save must not overwrite the stored keys")
        store.resetUnreadableSettings()
        assertNull(store.state.value.storageError)
        store.saveApiKey(null, "再登録", "test-reregistered")
        assertEquals(1, store.state.value.apiKeys.size)
    }

    // A13
    @Test fun allKeysWithShort429WaitForEarliestKeyLikeSingleKey() = runBlocking {
        val clock = Clock()
        val store = store(clock, keys = 2)
        val calls = mutableListOf<String>()
        var limited = 2
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            calls += request.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
            if (limited-- > 0) respond("""{"error":{"type":"requests"}}""", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "2"))
            else respond(scores(1))
        }))
        assertTrue(service.classifyPosts(PostModerationInput("t", listOf(post("1", "本文")))).isSuccess)
        assertEquals(listOf("test-audit-1", "test-audit-2", "test-audit-1"), calls)
        service.close()
    }

    // A14
    @Test fun duplicateLongBodiesKeepHideFromAnySplitPart() = runBlocking {
        val store = store()
        // The HIDE half comes first: keeping only the last split result would show this post.
        val body = "攻撃".repeat(700) + "\n" + "あ".repeat(1700)
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val inputs = texts(request)
            respond(scores(inputs.size) { inputs[it].contains("攻撃") })
        }))
        val results = service.classifyPosts(PostModerationInput("t", listOf(post("1", body), post("2", body)))).getOrThrow()
        assertEquals(setOf("1", "2"), results.filter { it.shouldHide }.map { it.postId }.toSet())
        service.close()
    }

    // A17
    @Test fun stringErrorFieldStillRecordsWaitAndSwitchesKey() = runBlocking {
        val store = store(keys = 2)
        val calls = mutableListOf<String>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val key = request.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
            calls += key
            if (key == "test-audit-1") respond("""{"error":"rate limited"}""", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "600"))
            else respond(scores(1))
        }))
        assertTrue(service.classifyPosts(PostModerationInput("t", listOf(post("1", "本文")))).isSuccess)
        assertEquals(listOf("test-audit-1", "test-audit-2"), calls)
        val first = store.state.value.apiKeys.first().id
        assertTrue(store.usage.state.value.keys.getValue(first).waitUntil > 0L)
        service.close()
    }

    // A19 + A22
    @Test fun renamingKeyKeepsRevisionAndMidRequestChangeStillRecords429() = runBlocking {
        val store = store()
        val revision = store.state.value.revision
        val id = store.state.value.apiKeys.single().id
        store.saveApiKey(id, "改名")
        store.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL)
        assertEquals(revision, store.state.value.revision, "Non-effective changes must not recreate AI services")
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine {
            store.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL, moderationThreshold = 0.5f)
            respond("{}", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "600"))
        }))
        try { service.classifyPosts(PostModerationInput("t", listOf(post("1", "本文")))) } catch (_: CancellationException) {}
        assertNotEquals(revision, store.state.value.revision)
        assertEquals(429, store.usage.state.value.keys.getValue(id).lastStatus)
        assertTrue(store.usage.state.value.keys.getValue(id).waitUntil > 0L)
        service.close()
    }

    // A15 + A18
    @Test fun cacheIsNeverRewrittenUnreadOrUnchanged() {
        var writes = 0
        val storage = object : AiConnectionStorage {
            override fun read(): String? = null
            override fun write(value: String) = Unit
            override fun readCache() = Json.encodeToString(mapOf("k" to AiCachedResult(kotlin.time.Clock.System.now().toEpochMilliseconds(), JsonPrimitive(1))))
            override fun writeCache(value: String) { writes++; assertContains(value, "\"k\"") }
        }
        val cache = OpenAiAnalysisCache(storage)
        cache.flush()
        assertEquals(0, writes)
        assertNotNull(cache.get("k"))
        cache.flush()
        assertEquals(0, writes)
        cache.put("n", JsonPrimitive(2))
        cache.flush()
        cache.flush()
        assertEquals(1, writes)
        val second = OpenAiAnalysisCache(storage)
        second.put("m", JsonPrimitive(3))
        second.flush()
        assertEquals(2, writes)
    }

    // A20
    @Test fun clockRollbackKeepsRemainingWaitInsteadOfExtendingIt() = runBlocking {
        val clock = Clock(10_000_000L)
        val store = store(clock)
        val id = store.state.value.apiKeys.single().id
        store.usage.keyResponse(id, 429, headersOf("Retry-After", "60"), false)
        clock.now -= 3_600_000L
        store.usage.observeClock()
        val wait = store.usage.state.value.keys.getValue(id).requiredWait(clock.now, 1)
        assertTrue(wait in 59_000L..60_000L, "wait=$wait")
    }

    // A25
    @Test fun localLimitDoesNotReplaceLongerServerWaitDisplay() = runBlocking {
        val clock = Clock()
        val store = store(clock)
        store.usage.cooldown(600_000L, "server_retry")
        repeat(3) { store.usage.attempt(3_000) }
        assertTrue(store.usage.requiredWait(3_000, localLimitsOnly = true) > 0L)
        assertEquals("server_retry", store.usage.state.value.waitReason)
        assertEquals(clock.now + 600_000L, store.usage.state.value.waitUntil)
    }

    private class FakeLocal(var available: Boolean = true, val answer: (PostModerationInput) -> List<PostModerationResult>) : OnDeviceAiService {
        var calls = 0
        var availabilityChecks = 0
        override suspend fun getAvailability(): AiAvailability { availabilityChecks++; return AiAvailability(available, supportsPostModeration = available) }
        override suspend fun summarizeThread(input: ThreadSummaryInput) = Result.failure<ThreadSummary>(IllegalStateException("unused"))
        override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> { calls++; return Result.success(answer(input)) }
    }

    // A10
    @Test fun undecidedDeviceAnswerCompletesHybridWithoutReinference() = runBlocking {
        val local = FakeLocal { emptyList() }
        val cloud = FakeLocal { input -> input.posts.map { PostModerationResult(it.id, false) } }
        val service = HybridModerationService(local, cloud)
        val input = PostModerationInput("t", listOf(post("1", "本文")))
        assertTrue(service.classifyPosts(input).getOrThrow().single().isComplete)
        assertTrue(service.classifyPosts(input).getOrThrow().single().isComplete)
        assertEquals(1, local.calls)
    }

    // A16
    @Test fun hybridRechecksUnavailableDeviceInsteadOfCachingForever() = runBlocking {
        val local = FakeLocal(available = false) { input -> input.posts.map { PostModerationResult(it.id, false) } }
        val cloud = FakeLocal { input -> input.posts.map { PostModerationResult(it.id, false) } }
        val time = TestTimeSource()
        val service = HybridModerationService(local, cloud, time)
        val input = PostModerationInput("t", listOf(post("1", "本文")))
        service.classifyPosts(input).getOrThrow()
        assertEquals(0, local.calls)
        local.available = true
        service.classifyPosts(input).getOrThrow()
        assertEquals(0, local.calls, "Re-checks are rate limited")
        time += 61.seconds
        service.classifyPosts(input).getOrThrow()
        assertEquals(1, local.calls)
    }
}
