package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IosCompatibilityStoreStateRecordTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = "compat_board_may"
    private var clock = 1_000L

    @Test
    fun oldMetadataCollectionsMigrateIntoStateRecords(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            fileSystem.createDirectory("compatibility").getOrThrow()
            val item = CompatCatalogSnapshotItem(
                id = "555",
                threadUrl = "${boardUrl}res/555.htm",
                title = "落ちたスレ",
                thumbnailUrl = null,
                fullImageUrl = null,
                thumbnailWidth = null,
                thumbnailHeight = null,
                replyCount = 4,
                expiresAtEpochMillis = null
            )
            val payload = buildJsonObject {
                put("version", 1)
                put("partitionedCaches", true)
                put("boards", JsonArray(listOf(Json.encodeToJsonElement(CompatBoard.serializer(), board()))))
                put("preferences", buildJsonObject {
                    put("compat.thread.fontSize", JsonPrimitive("18"))
                    put("compat.watch.results", JsonPrimitive("[1,2,3]"))
                })
                put("archiveRows", JsonArray(listOf(buildJsonObject {
                    put("threadId", "may_b_123")
                    put("threadUrl", "${boardUrl}res/123.htm")
                    put("state", "pending")
                    put("firstSeenAt", 10L)
                    put("nextAttemptAt", 20L)
                })))
                put("droppedItems", JsonArray(listOf(buildJsonObject {
                    put("boardKey", boardKey)
                    put("threadId", "555")
                    put("item", Json.encodeToJsonElement(CompatCatalogSnapshotItem.serializer(), item))
                    put("droppedAtEpochMillis", 30L)
                    put("lastSeenAtEpochMillis", 25L)
                    put("classification", "DIE")
                })))
            }.toString()
            val seed = IosCompatibilityDatabase(fileSystem)
            try {
                seed.writePayload(payload, 1L)
            } finally {
                seed.close()
            }

            val store = IosCompatibilityStore(fileSystem, nowMillis = { ++clock })
            store.initialize()
            assertLoaded(store)

            val check = IosCompatibilityDatabase(fileSystem)
            try {
                val metadata = Json.parseToJsonElement(requireNotNull(check.readPayload())).jsonObject
                assertEquals(JsonPrimitive(true), metadata["stateRecordsPartitioned"])
                assertEquals(JsonObject(emptyMap()), metadata["preferences"])
                assertEquals(JsonArray(emptyList()), metadata["archiveRows"])
                assertEquals(JsonArray(emptyList()), metadata["droppedItems"])
                val kinds = check.readStateRecords().records.groupBy(CompatibilityStateRecord::kind)
                assertEquals(setOf("compat.thread.fontSize", "compat.watch.results"), kinds.getValue("pref").map { it.key }.toSet())
                assertEquals(listOf("may_b_123"), kinds.getValue("archive").map { it.key })
                assertEquals(listOf(boardKey), kinds.getValue("dropped").map { it.key })
            } finally {
                check.close()
            }

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { ++clock })
            reopened.initialize()
            assertLoaded(reopened)
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun preferenceAndOutboxChangesDoNotRewriteTheMetadataRow(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { ++clock })
            store.initialize()
            store.upsertBoard(board())
            store.savePreference("compat.thread.fontSize", "18")
            val metadataWrittenAt = metadataUpdatedAt(fileSystem)

            store.savePreference("compat.catalog.columns", "5")
            store.savePreferences(mapOf("compat.thread.fontSize" to null))
            val enqueued = store.enqueueArchiveReport("${boardUrl}res/123.htm", clock)
            assertTrue(enqueued.inserted)
            assertEquals(metadataWrittenAt, metadataUpdatedAt(fileSystem))

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { ++clock })
            reopened.initialize()
            assertEquals(mapOf("compat.catalog.columns" to "5"), reopened.preferences.first())
            assertEquals(1, reopened.archiveReportOutboxStats().total)

            // A metadata change is still written.
            reopened.upsertHistory(
                CompatHistoryEntry(
                    canonicalUrl = "${boardUrl}res/123.htm",
                    originalUrl = "${boardUrl}res/123.htm",
                    boardKey = boardKey,
                    boardName = "虹裏",
                    threadNo = "123",
                    title = "スレ",
                    contentUpdatedAtEpochMillis = 5L
                )
            )
            assertNotEquals(metadataWrittenAt, metadataUpdatedAt(fileSystem))
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    private suspend fun assertLoaded(store: IosCompatibilityStore) {
        val preferences = store.preferences.first()
        assertEquals("18", preferences["compat.thread.fontSize"])
        assertEquals("[1,2,3]", preferences["compat.watch.results"])
        assertEquals(1, store.archiveReportOutboxStats().total)
        assertEquals(listOf("555"), store.loadDroppedCatalogItems(boardKey).map { it.item.id })
    }

    private fun metadataUpdatedAt(fileSystem: com.valoser.futacha.shared.util.FileSystem): Long? {
        val database = IosCompatibilityDatabase(fileSystem)
        return try {
            database.payloadUpdatedAt()
        } finally {
            database.close()
        }
    }

    private fun board() = CompatBoard(
        key = boardKey,
        name = "虹裏",
        canonicalUrl = boardUrl,
        originalUrl = boardUrl,
        sortOrder = 0
    )
}
