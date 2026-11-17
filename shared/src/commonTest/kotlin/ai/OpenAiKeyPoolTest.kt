package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.openAiKeyStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiKeyPoolTest {
    @Test fun correctingCredentialsRestartsModerationButRenamingAndOrderingDoNot() = runBlocking {
        val store = AiConnectionStore(MemoryAiStorage()).also { it.load() }
        store.saveApiKey(null, "one", "test-first-key")
        store.saveApiKey(null, "two", "test-second-key")
        val id = store.state.value.apiKeys.first().id
        val revision = store.state.value.revision
        store.saveApiKey(id, "renamed")
        store.moveApiKey(id, 1)
        assertEquals(revision, store.state.value.revision)
        store.saveApiKey(id, "renamed", "test-repaired-key")
        assertTrue(store.state.value.revision > revision)
    }

    @Test fun settingsFromAnotherBuildKeepKeysDespiteUnknownFieldsAndProviders() = runBlocking {
        val storage = MemoryAiStorage().apply {
            credentials = """{"summaryProvider":"CLOUD_X","moderationProvider":"FUTURE_PROVIDER",
                "summaryModel":"gpt-4.1","moderationThreshold":0.5,"newTopLevelField":{"a":1},
                "apiKeys":[{"id":"api-1","name":"個人用","key":"test-kept-secret","enabled":true,"quotaHint":3}]}"""
        }
        val store = AiConnectionStore(storage).also { it.load() }
        val state = store.state.value
        assertNull(state.storageError)
        assertEquals(listOf("api-1"), state.apiKeys.map { it.id })
        assertEquals(AiProvider.DEVICE, state.moderationProvider)
        assertEquals(0.5f, state.moderationThreshold)
        assertEquals("test-kept-secret", store.apiKey(state.revision))
        // Saving afterwards rewrites the readable form instead of being refused.
        store.saveApiKey("api-1", "改名")
        assertTrue(storage.credentials!!.contains("test-kept-secret"))
    }

    @Test fun repeatedUnreadableSettingsKeepTheSameServiceRevision() = runBlocking {
        val storage = object : AiConnectionStorage {
            override fun read(): String? = error("unreadable")
            override fun write(value: String) = Unit
        }
        val store = AiConnectionStore(storage)
        store.load()
        val failed = store.state.value
        repeat(3) { store.load() }
        assertEquals(failed, store.state.value)
    }

    private class Fixture(val storage: MemoryAiStorage = MemoryAiStorage()) {
        var now = 1_000_000L
        var pause: suspend (Long) -> Unit = { now += it }
        lateinit var store: AiConnectionStore
        suspend fun load(keys: Int = 2) {
            store = AiConnectionStore(storage, OpenAiRequestQueue({ now }, { pause(it) }, { 0 }))
            store.load()
            if (store.state.value.apiKeys.isEmpty()) repeat(keys) { store.saveApiKey(null, "接続${it + 1}", "test-pool-${it + 1}") }
            store.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL)
        }
        fun service(handler: MockRequestHandleScope.(String) -> HttpResponseData) = OpenAiService(store, store.state.value,
            HttpClient(MockEngine { handler(it.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")) }))
    }
    private fun input(id: Int = 1, body: String = "本文$id") = PostModerationInput("thread", listOf(
        Post("$id", author = null, subject = null, timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)))
    private fun success() = buildJsonObject { putJsonArray("results") { add(buildJsonObject {
        putJsonObject("category_scores") { OPENAI_MODERATION_CATEGORIES.keys.forEach { put(it, 0.01) } }
    }) } }.toString()
    private fun MockRequestHandleScope.limited(seconds: String = "600") = respond(
        """{"error":{"type":"invalid_request_error","message":"Too Many Requests"}}""",
        HttpStatusCode.TooManyRequests, headersOf("Retry-After", seconds))

    @Test fun limitSwitchesToNextKeyAndSuccessfulKeyIsReusedWithoutExtraCachedCalls() = runBlocking {
        val f = Fixture().also { it.load(3) }
        val calls = mutableListOf<String>()
        val service = f.service { key -> calls += key; if (key == "test-pool-1") limited() else respond(success()) }
        assertTrue(service.classifyPosts(input()).isSuccess)
        assertEquals(listOf("test-pool-1", "test-pool-2"), calls)
        service.classifyPosts(input()).getOrThrow()
        assertEquals(2, calls.size)
        service.classifyPosts(input(2)).getOrThrow()
        assertEquals("test-pool-2", calls.last())
        assertEquals(3, calls.size)
        val ids = f.store.state.value.apiKeys.map { it.id }
        assertEquals(1_600_000L, f.store.usage.state.value.keys.getValue(ids[0]).waitUntil)
        assertEquals(ids[1], f.store.usage.state.value.activeKeyId)
        service.close()
    }
    @Test fun allLimitedKeysAreTriedOnceAndTheirDeadlinesSurviveRestart() = runBlocking {
        val f = Fixture().also { it.load() }
        var calls = 0
        val first = f.service { calls++; limited() }
        assertTrue(first.classifyPosts(input()).isFailure)
        assertEquals(2, calls)
        first.close()
        f.load()
        val restored = f.service { calls++; respond(success()) }
        assertTrue(restored.classifyPosts(input()).isFailure)
        assertEquals(2, calls)
        f.now += 600_001
        assertTrue(restored.classifyPosts(input()).isSuccess)
        assertEquals(3, calls)
        assertTrue(f.store.usage.state.value.keys.values.all { it.attempts >= 1 })
        assertFalse(f.storage.usage!!.contains("test-pool"))
        assertFalse(f.storage.usage!!.contains("本文"))
        restored.close()
    }
    @Test fun authenticationAndPermanentQuotaSkipKeysWithoutRetryingOrInventingExpiry() = runBlocking {
        val f = Fixture().also { it.load(3) }
        val calls = mutableListOf<String>()
        val service = f.service { key ->
            calls += key
            when (key) {
                "test-pool-1" -> respond("{}", HttpStatusCode.Unauthorized)
                "test-pool-2" -> respond("""{"error":{"code":"project_spend_limit_exceeded"}}""", HttpStatusCode.TooManyRequests)
                else -> respond(success())
            }
        }
        service.classifyPosts(input()).getOrThrow()
        service.classifyPosts(input(2)).getOrThrow()
        assertEquals(listOf("test-pool-1", "test-pool-2", "test-pool-3", "test-pool-3"), calls)
        val keys = f.store.usage.state.value.keys.values.toList()
        assertTrue(keys.take(2).all { it.blockedReason.isNotBlank() && it.waitUntil == 0L })
        service.close()
    }
    @Test fun serverRemainingTokensSwitchBefore429AndGlobalBudgetStillApplies() = runBlocking {
        val f = Fixture().also { it.load() }
        val starts = mutableListOf<Pair<String, Long>>()
        val service = f.service { key ->
            starts += key to f.now
            respond(success(), headers = if (key == "test-pool-1") headersOf(
                "x-ratelimit-remaining-tokens" to listOf("0"), "x-ratelimit-reset-tokens" to listOf("5m")) else headersOf())
        }
        service.classifyPosts(input(1, "あ".repeat(1900))).getOrThrow()
        service.classifyPosts(input(2, "い".repeat(1900))).getOrThrow()
        assertEquals(listOf("test-pool-1", "test-pool-2"), starts.map { it.first })
        assertTrue(starts[1].second - starts[0].second >= 60_000, "Changing keys must not bypass the app-wide token budget")
        assertEquals(2, f.store.usage.state.value.hours.sumOf { it.requests })
        service.close()
    }
    @Test fun disableReorderAndDeleteControlWhichKeyIsSelected() = runBlocking {
        val f = Fixture().also { it.load(3) }
        val keys = f.store.state.value.apiKeys
        f.store.setApiKeyEnabled(keys[0].id, false)
        f.store.moveApiKey(keys[2].id, -1)
        val calls = mutableListOf<String>()
        var service = f.service { key -> calls += key; respond(success()) }
        service.classifyPosts(input()).getOrThrow()
        assertEquals(listOf("test-pool-3"), calls)
        service.close()
        f.store.deleteApiKey(keys[2].id)
        service = f.service { key -> calls += key; respond(success()) }
        service.classifyPosts(input(2)).getOrThrow()
        assertEquals("test-pool-2", calls.last())
        service.close()
        f.store.setApiKeyEnabled(keys[1].id, false)
        assertFalse(f.store.state.value.hasApiKey)
        assertEquals(AiProvider.DEVICE, f.store.state.value.moderationProvider)
    }
    @Test fun cancellationWhileSwitchingDoesNotSendNextKeyOrLoseFirstCooldown() = runBlocking {
        val f = Fixture().also { it.load() }
        val waiting = CompletableDeferred<Unit>()
        f.pause = { waiting.complete(Unit); awaitCancellation() }
        var calls = 0
        val service = f.service { calls++; limited() }
        val job = launch { service.classifyPosts(input()) }
        withTimeout(2_000) { waiting.await() }
        job.cancelAndJoin()
        assertEquals(1, calls)
        assertTrue(f.store.usage.state.value.keys.values.single().waitUntil > f.now)
        service.close()
    }
    @Test fun legacyKeyAndCooldownMigrateWithoutPuttingSecretsIntoPublicState() = runBlocking {
        val storage = MemoryAiStorage().apply {
            credentials = Json.encodeToString(StoredAiConnection.serializer(), StoredAiConnection(apiKey = "legacy-test-secret", moderationProvider = AiProvider.OPENAI))
            usage = Json.encodeToString(OpenAiUsageState(waitUntil = 9_000_000L, waitReason = "server_retry", lastStatus = 429))
        }
        val f = Fixture(storage).also { it.load() }
        assertEquals("legacy", f.store.state.value.apiKeys.single().id)
        assertFalse(f.store.state.value.toString().contains("legacy-test-secret"))
        assertFalse(Json.parseToJsonElement(storage.credentials!!).jsonObject.containsKey("apiKey"))
        var calls = 0
        val service = f.service { calls++; respond(success()) }
        assertTrue(service.classifyPosts(input()).isFailure)
        assertEquals(0, calls)
        assertEquals(9_000_000L, f.store.usage.state.value.keys.getValue("legacy").waitUntil)
        service.close()
    }
    @Test fun duplicateKeysAndFailedWritesDoNotDestroySavedPool() = runBlocking {
        var saved: String? = null
        var fail = false
        val storage = object : AiConnectionStorage {
            override fun read() = saved
            override fun write(value: String) { check(!fail); saved = value }
        }
        val store = AiConnectionStore(storage).also { it.load(); it.saveApiKey(null, "個人用", "test-secret") }
        assertFailsWith<IllegalArgumentException> { store.saveApiKey(null, "重複", "test-secret") }
        val before = store.state.value
        fail = true
        assertFailsWith<IllegalStateException> { store.deleteApiKey(before.apiKeys.single().id) }
        assertEquals(before, store.state.value)
        assertEquals("test-secret", store.apiKey(before.revision))
    }
    @Test fun maximumPoolReloadsAndRenamingKeepsCredentialsWithoutRevealingThem() = runBlocking {
        val storage = MemoryAiStorage()
        val store = AiConnectionStore(storage).also { it.load() }
        repeat(MAX_OPENAI_KEYS) { store.saveApiKey(null, "接続$it", "test-$it-" + "x".repeat(1000)) }
        assertTrue(storage.credentials!!.encodeToByteArray().size > 8192)
        assertFailsWith<IllegalArgumentException> { store.saveApiKey(null, "超過", "test-overflow") }
        val id = store.state.value.apiKeys.first().id
        store.saveApiKey(id, "改名")
        val reopened = AiConnectionStore(storage).also { it.load() }
        assertEquals(MAX_OPENAI_KEYS, reopened.state.value.apiKeys.size)
        assertEquals("改名", reopened.state.value.apiKeys.first().name)
        assertEquals("test-0-" + "x".repeat(1000), reopened.apiKey(reopened.state.value.revision))
        assertTrue(reopened.state.value.apiKeys.all { it.maskedKey == "••••xxxx" })
    }
    @Test fun explicitRetryNeverClearsServerWaitAndReplacingKeyDropsOldHold() = runBlocking {
        val f = Fixture().also { it.load(1) }
        val id = f.store.state.value.apiKeys.single().id
        f.store.usage.keyResponse(id, 429, headersOf("Retry-After", "600"), false)
        val revision = f.store.state.value.revision
        f.store.retryApiKey(id)
        assertEquals(600_000L, f.store.usage.state.value.keys.getValue(id).requiredWait(f.now, 1))
        // Round 3 A-4: the retry is signalled to paused moderation without recreating services.
        assertEquals(1, f.store.keyRetries.value)
        assertEquals(revision, f.store.state.value.revision)
        f.store.saveApiKey(id, "新しいキー", "test-new-secret")
        assertNotEquals(id, f.store.state.value.apiKeys.single().id)
        assertFalse(id in f.store.usage.state.value.keys)
        val info = f.store.state.value.apiKeys.single()
        assertEquals("無効", openAiKeyStatus(info.copy(enabled = false), null, null, f.now))
        assertContains(openAiKeyStatus(info, OpenAiKeyUsage(waitUntil = f.now + 10_000), null, f.now), "待機中")
    }
}
