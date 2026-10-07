package com.valoser.futacha

import com.valoser.futacha.shared.watch.WatchPhoneStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPhoneStatusPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(payload: ByteArray): WatchPhoneStatus =
        json.decodeFromString(WatchPhoneStatus.serializer(), payload.decodeToString())

    @Test
    fun supportedReplyEchoesTheWatchClockWithThePhoneClock() {
        val status = decode(buildWatchPhoneStatusPayload(phoneNowMillis = 2_000L, watchSentAtMillis = 1_000L, isSupported = true))
        assertEquals(2_000L, status.phoneNowMillis)
        assertEquals(1_000L, status.watchSentAtMillis)
        assertTrue(status.isSupported)
    }

    @Test
    fun unsupportedModeReplyIsExplicit() {
        val encoded = buildWatchPhoneStatusPayload(phoneNowMillis = 2_000L, watchSentAtMillis = 0L, isSupported = false)
        assertTrue(encoded.decodeToString().contains("\"isSupported\":false"))
        assertFalse(decode(encoded).isSupported)
        assertTrue(encoded.size < 1024)
    }
}
