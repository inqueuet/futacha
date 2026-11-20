package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.ui.compat.registerCompatTsumanne
import com.valoser.futacha.shared.ui.compat.searchCompatCatalogCacheRemote
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AM-B4: the Futaba-only outside services never receive an あいもげ address. */
class AimogeFutabaServiceIsolationTest {
    private val aBoard = "https://nijiurachan.net/b/aimoge/"
    private val aThread = "https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"

    @Test
    fun theArchiveSearchScopeIsNullForAimogeAndKeptForFutaba() {
        assertNull(extractArchiveSearchScope(aBoard))
        assertNull(extractArchiveSearchScope(aThread))
        assertNotNull(extractArchiveSearchScope("https://may.2chan.net/b/"))
    }

    @Test
    fun theCacheSearchAsksOnlyAimogesOwnArchiveForAnAimogeBoard() = runBlocking {
        val before = com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled
        com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = true
        try {
            var requests = 0
            val client = HttpClient(MockEngine { requests++; respond("") })
            val scopes = mutableListOf<com.valoser.futacha.shared.network.ArchiveSearchScope>()
            var legacyCalls = 0
            val result = searchCompatCatalogCacheRemote(
                httpClient = client,
                boardUrl = aBoard,
                query = "テスト",
                searchArchive = { _, scope ->
                    scopes += scope
                    listOf(com.valoser.futacha.shared.network.ArchiveSearchItem(
                        threadId = "0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f", server = "aimoge", board = "aimoge",
                        title = "テスト", htmlUrl = aThread
                    ))
                },
                searchLegacy = { legacyCalls++; Result.success(emptyList()) }
            )
            assertEquals(listOf("aimoge"), scopes.map { it.aimogeSlug })
            assertEquals(listOf(aThread), result.getOrThrow().map { it.threadUrl })
            assertEquals(0, legacyCalls)
            assertEquals(0, requests)
            client.close()
        } finally {
            com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = before
        }
    }

    @Test
    fun cacheSearchRetainsBodyMatchesAndFailureBeyondFourTerms() = runBlocking {
        val before = com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled
        com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = true
        val client = HttpClient(MockEngine { error("Unexpected request") })
        try {
            val id = aThread.substringAfterLast('/')
            val row = com.valoser.futacha.shared.network.ArchiveSearchItem(
                threadId = id, server = "aimoge", board = "aimoge", htmlUrl = aThread
            )
            val matched = mutableListOf<String>()
            val failure = IllegalStateException("offline")
            var reported: Throwable? = null
            val results = searchCompatCatalogCacheRemote(client, aBoard, "赤 青 黒 白 紫 緑",
                searchArchive = { term, _ -> if (term == "緑") throw failure else listOf(row) },
                onRemoteTermMatch = { _, term -> matched += term },
                onRemoteFailure = { reported = it })
            assertEquals(listOf(id), results.getOrThrow().map { it.id })
            assertEquals(listOf("赤", "青", "黒", "白", "紫"), matched)
            assertEquals(failure, reported)
        } finally {
            client.close()
            com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = before
        }
    }

    @Test
    fun theCacheSearchSendsNothingWhileAimogeIsOff() = runBlocking {
        val before = com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled
        com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = false
        try {
            var calls = 0
            val client = HttpClient(MockEngine { calls++; respond("") })
            val result = searchCompatCatalogCacheRemote(client, aBoard, "テスト",
                searchArchive = { _, _ -> calls++; emptyList() }, searchLegacy = { calls++; Result.success(emptyList()) })
            assertEquals(emptyList(), result.getOrThrow())
            assertEquals(0, calls)
            client.close()
        } finally {
            com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = before
        }
    }

    @Test
    fun tsumanneRefusesAnAimogeThreadWithoutSending() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("") })
        assertTrue(registerCompatTsumanne(client, aThread, "t").isFailure)
        assertEquals(0, requests)
        client.close()
    }
}
