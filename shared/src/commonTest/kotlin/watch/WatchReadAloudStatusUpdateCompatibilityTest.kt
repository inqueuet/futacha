package com.valoser.futacha.shared.watch

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchReadAloudStatusUpdateCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun updateFromAnOlderPhoneWithoutSequenceDecodesWithDefaults() {
        val decoded = json.decodeFromString(
            WatchReadAloudStatusUpdate.serializer(),
            """{"status":null,"updatedAtMillis":123}"""
        )
        assertEquals(123L, decoded.updatedAtMillis)
        assertEquals(0L, decoded.sessionId)
        assertEquals(0L, decoded.sequence)
    }

    @Test
    fun sequencedUpdateRoundTripsAndAnOlderWatchIgnoresTheNewFields() {
        val update = WatchReadAloudStatusUpdate(status = null, updatedAtMillis = 5L, sessionId = 9L, sequence = 3L)
        val encoded = json.encodeToString(WatchReadAloudStatusUpdate.serializer(), update)
        assertEquals(update, json.decodeFromString(WatchReadAloudStatusUpdate.serializer(), encoded))
        // An older watch build only knows status/updatedAtMillis; ignoreUnknownKeys drops the rest.
        val oldShape = json.parseToJsonElement(encoded)
        assertEquals(true, oldShape.toString().contains("\"sequence\":3"))
    }
}
