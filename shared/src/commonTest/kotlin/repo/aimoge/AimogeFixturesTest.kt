package com.valoser.futacha.shared.repo.aimoge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AM-B0: the あいもげ fixtures are well-formed, hold the synthesized cases and no secret. */
class AimogeFixturesTest {
    private val all = listOf(
        AimogeFixtures.BOARDS, AimogeFixtures.CLIENT_POLICY_AIMG, AimogeFixtures.CATALOG, AimogeFixtures.THREAD,
        AimogeFixtures.THREAD_CLOSED, AimogeFixtures.ARCHIVE_LIST, AimogeFixtures.SNAPSHOT,
        AimogeFixtures.ERROR_OBJECT, AimogeFixtures.ERROR_SUCCESS_FALSE
    )

    @Test
    fun everyJsonFixtureParsesAndStaysSmall() {
        all.forEach { text ->
            assertTrue(Json.parseToJsonElement(text) is JsonObject)
            assertTrue(text.encodeToByteArray().size < 60_000)
        }
        assertTrue(AimogeFixtures.ERROR_PLAIN_HTML.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun theThreadHoldsTheSynthesizedCases() {
        val posts = Json.parseToJsonElement(AimogeFixtures.THREAD).jsonObject.getValue("posts").jsonArray.map { it.jsonObject }
        assertEquals("Ab3dE9xQ", posts[3].getValue("displayId").jsonPrimitive.content)
        assertEquals("null", posts[4].getValue("postSequenceNo").toString())
        assertEquals("shadowed", posts[5].getValue("status").jsonPrimitive.content)
        assertTrue(posts[6].getValue("body").jsonPrimitive.content.any { it in ''..'' })
        val closed = Json.parseToJsonElement(AimogeFixtures.THREAD_CLOSED).jsonObject
        assertFalse(closed.getValue("closedAt").toString() == "null")
    }

    @Test
    fun noFixtureHoldsASecret() {
        (all + AimogeFixtures.ERROR_PLAIN_HTML).forEach { text ->
            // Field names of the secrets; `maxDeleteKeyLength` in the board policy is a limit, not a key.
            listOf("\"installationId\"", "\"codeVerifier\"", "\"verifier\"", "\"credential\"", "\"deleteKey\"", "x-aimg-token", "x-aimg-delete-key")
                .forEach { word -> assertFalse(text.contains(word, ignoreCase = true), word) }
        }
    }
}
