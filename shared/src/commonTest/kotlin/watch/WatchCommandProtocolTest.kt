package com.valoser.futacha.shared.watch

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchCommandProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun selectBoardCommand_serializesWithStableEnumNameAndBoardFields() {
        val encoded = json.encodeToString(
            WatchCommand.serializer(),
            WatchCommand(
                type = WatchCommandType.SelectBoard,
                boardId = "b",
                boardUrl = "https://may.2chan.net/b/"
            )
        )

        assertTrue(encoded.contains("\"type\":\"SelectBoard\""))
        assertTrue(encoded.contains("\"boardId\":\"b\""))
        assertTrue(encoded.contains("\"boardUrl\":\"https://may.2chan.net/b/\""))

        val decoded = json.decodeFromString(WatchCommand.serializer(), encoded)
        assertEquals(WatchCommandType.SelectBoard, decoded.type)
        assertEquals("b", decoded.boardId)
        assertEquals("https://may.2chan.net/b/", decoded.boardUrl)
    }

    @Test
    fun openThreadCommand_serializesWithStableEnumNameAndThreadFields() {
        val encoded = json.encodeToString(
            WatchCommand.serializer(),
            WatchCommand(
                type = WatchCommandType.OpenThreadOnPhone,
                boardId = "b",
                boardUrl = "https://may.2chan.net/b/",
                threadId = "123"
            )
        )

        assertTrue(encoded.contains("\"type\":\"OpenThreadOnPhone\""))

        val decoded = json.decodeFromString(WatchCommand.serializer(), encoded)
        assertEquals(WatchCommandType.OpenThreadOnPhone, decoded.type)
        assertEquals("b", decoded.boardId)
        assertEquals("https://may.2chan.net/b/", decoded.boardUrl)
        assertEquals("123", decoded.threadId)
    }

    @Test
    fun readAloudCommands_serializeWithStableEnumNames() {
        val commandTypes = listOf(
            WatchCommandType.StartReadAloudOnPhone,
            WatchCommandType.PauseReadAloudOnPhone,
            WatchCommandType.StopReadAloudOnPhone,
            WatchCommandType.NextReadAloudOnPhone,
            WatchCommandType.PreviousReadAloudOnPhone
        )

        commandTypes.forEach { type ->
            val encoded = json.encodeToString(
                WatchCommand.serializer(),
                WatchCommand(
                    type = type,
                    boardId = "b",
                    boardUrl = "https://may.2chan.net/b/",
                    threadId = "123"
                )
            )

            assertTrue(encoded.contains("\"type\":\"${type.name}\""))
            val decoded = json.decodeFromString(WatchCommand.serializer(), encoded)
            assertEquals(type, decoded.type)
            assertEquals("123", decoded.threadId)
        }
    }

    @Test
    fun protocolConstants_keepDataLayerAndWatchConnectivityKeysStable() {
        assertEquals("/futacha/watch_snapshot", WATCH_SNAPSHOT_PATH)
        assertEquals("/futacha/watch_snapshot_ack", WATCH_SNAPSHOT_ACK_PATH)
        assertEquals("/futacha/request_snapshot", WATCH_REQUEST_SNAPSHOT_PATH)
        assertEquals("/futacha/command", WATCH_COMMAND_PATH)
        assertEquals("/futacha/read_aloud_status", WATCH_READ_ALOUD_STATUS_PATH)
        assertEquals("snapshot", WATCH_SNAPSHOT_KEY)
        assertEquals("snapshotAck", WATCH_SNAPSHOT_ACK_KEY)
        assertEquals("command", WATCH_COMMAND_KEY)
        assertEquals("readAloudStatus", WATCH_READ_ALOUD_STATUS_KEY)
        assertEquals("updatedAtMillis", WATCH_UPDATED_AT_KEY)
        assertEquals(10 * 60 * 1000L, WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS)
        assertEquals(30 * 60 * 1000L, WATCH_SNAPSHOT_STALE_AGE_MILLIS)
        assertEquals(5 * 60 * 1000L, WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS)
        assertEquals(80, WATCH_SNAPSHOT_MAX_BOARDS)
        assertEquals(20, WATCH_SNAPSHOT_MAX_THREADS)
        assertEquals(50, WATCH_SNAPSHOT_MAX_WATCH_WORDS)
        assertEquals(5, WATCH_SNAPSHOT_MAX_PREVIEW_POSTS_PER_THREAD)
    }

    @Test
    fun snapshotAcceptance_rejectsFuturePoisoningAndRecoversFromPoisonedCurrentValue() {
        val nowMillis = 1_000_000L

        assertTrue(shouldAcceptWatchSnapshot(null, nowMillis, nowMillis))
        assertTrue(shouldAcceptWatchSnapshot(nowMillis, nowMillis, nowMillis))
        assertFalse(shouldAcceptWatchSnapshot(nowMillis, nowMillis - 1L, nowMillis))
        assertFalse(
            shouldAcceptWatchSnapshot(
                currentGeneratedAtMillis = nowMillis,
                incomingGeneratedAtMillis = nowMillis + WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS + 1L,
                nowMillis = nowMillis
            )
        )
        assertTrue(
            shouldAcceptWatchSnapshot(
                currentGeneratedAtMillis = Long.MAX_VALUE,
                incomingGeneratedAtMillis = nowMillis,
                nowMillis = nowMillis
            )
        )
    }

    @Test
    fun snapshotAcceptance_handlesEpochArithmeticBoundaries() {
        assertFalse(shouldAcceptWatchSnapshot(null, 1L, -1L))
        assertFalse(shouldAcceptWatchSnapshot(null, 1L, 1L, maxFutureSkewMillis = -1L))
        assertTrue(
            shouldAcceptWatchSnapshot(
                currentGeneratedAtMillis = Long.MAX_VALUE - 1L,
                incomingGeneratedAtMillis = Long.MAX_VALUE,
                nowMillis = Long.MAX_VALUE - 2L,
                maxFutureSkewMillis = 10L
            )
        )
    }

    @Test
    fun snapshotTransportShape_rejectsOversizedCollectionsAndInconsistentCounters() {
        val valid = WatchSnapshot(
            generatedAtMillis = 1L,
            boards = emptyList(),
            threads = emptyList(),
            watchWords = emptyList(),
            unreadTotal = 0,
            watchMatchTotal = 0
        )

        assertTrue(valid.hasValidTransportShape())
        assertFalse(
            valid.copy(
                boards = List(WATCH_SNAPSHOT_MAX_BOARDS + 1) {
                    WatchBoard("b$it", "board", "cat", "https://example.com/", false)
                }
            ).hasValidTransportShape()
        )
        assertFalse(valid.copy(unreadTotal = 1).hasValidTransportShape())
        assertFalse(valid.copy(watchMatchTotal = 1).hasValidTransportShape())
        assertFalse(valid.copy(generatedAtMillis = 0L).hasValidTransportShape())
    }
}
