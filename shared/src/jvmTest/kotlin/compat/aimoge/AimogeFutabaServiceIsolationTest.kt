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
    fun theCacheSearchSendsNothingForAnAimogeBoard() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("") })
        var archiveCalls = 0
        var legacyCalls = 0
        val result = searchCompatCatalogCacheRemote(
            httpClient = client,
            boardUrl = aBoard,
            query = "テスト",
            searchArchive = { _, _ -> archiveCalls++; emptyList() },
            searchLegacy = { legacyCalls++; Result.success(emptyList()) }
        )
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
        assertEquals(0, archiveCalls)
        assertEquals(0, legacyCalls)
        assertEquals(0, requests)
        client.close()
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
