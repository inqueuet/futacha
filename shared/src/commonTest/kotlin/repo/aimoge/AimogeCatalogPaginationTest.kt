package com.valoser.futacha.shared.repo.aimoge

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.ui.compat.compatCatalogFetchSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AimogeCatalogPaginationTest {
    @Test fun countAboveThreeHundredUsesBoundedPagesAndHonorsTheRequestedTotal() = runTest {
        val seed = Json.parseToJsonElement(AimogeFixtures.CATALOG).jsonObject.getValue("threads").jsonArray.first().jsonObject
        val pages = mutableListOf<Int>()
        val client = HttpClient(MockEngine { request ->
            val page = request.url.parameters["page"]?.toInt() ?: 1
            pages += page
            assertEquals("300", request.url.parameters["limit"])
            val rows = (1..300).map { n ->
                JsonObject(seed + ("id" to JsonPrimitive("01a1ab6e-57e8-755b-a01c-" + (n + (page - 1) * 300).toString().padStart(12, '0'))))
            }
            respond(buildJsonObject {
                put("threads", JsonArray(rows))
                put("pagination", buildJsonObject { put("hasNextPage", page < 2) })
            }.toString(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val repo = AimogeBoardRepository(client)
        try {
            val result = repo.getCatalogPageWithSettings("https://nijiurachan.net/b/ai2/", CatalogMode.Catalog, compatCatalogFetchSettings(500))
            assertEquals(listOf(1, 2), pages)
            assertEquals(500, result.items.size)
            assertEquals(500, result.items.map { it.id }.toSet().size)
        } finally { repo.close(); client.close() }
    }

    @Test fun noNextPageStopsWithoutTryingToInventMissingThreads() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond(AimogeFixtures.CATALOG, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        val repo = AimogeBoardRepository(client)
        try {
            val page = repo.getCatalogPageWithSettings("https://nijiurachan.net/b/ai2/", CatalogMode.Catalog, compatCatalogFetchSettings(2000))
            assertEquals(1, calls)
            assertTrue(page.items.size < 2000)
        } finally { repo.close(); client.close() }
    }
}
