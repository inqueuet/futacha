package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The thread caches live in their own rows, so unrelated changes do not rewrite them. */
class DesktopCompatibilityCachePartitionTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private val url = "${boardUrl}res/1.htm"
    // The tab needs its board, or the repair on the next start drops it with its snapshot.
    private val tab = CompatTab(compatTabKey(url), url, url, compatBoardKey(boardUrl), "板", "1", "スレ",
        insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)
    private suspend fun DesktopCompatibilityStore.openBoardAndTab() {
        importModernBoards(listOf(BoardSummary("modern-board", "板", "", boardUrl, "")))
        openTab(tab)
    }
    private fun snapshot(text: String = "x".repeat(2_000)) = CompatThreadSnapshot(
        tabKey = tab.key, revision = 1L, fetchedAtEpochMillis = 1L,
        posts = listOf(CompatPostSnapshot(position = 0, postNo = "1", timestamp = "", messageHtml = text))
    )

    private fun cacheRowIds(root: File): Map<String, Long> {
        val db = File(root, "compatibility/compatibility.db")
        return DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT key, rowid FROM compat_cache_record").use { r ->
                    buildMap { while (r.next()) put(r.getString(1), r.getLong(2)) }
                }
            }
        }
    }

    @Test
    fun snapshotsAreStoredAsRowsOutsideTheProfilePayloadAndSurviveReopening() = runBlocking {
        val root = Files.createTempDirectory("compat-partition").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.openBoardAndTab()
            assertTrue(store.saveThreadSnapshot(snapshot()))
            store.close()

            val database = DesktopCompatibilityDatabase(fs)
            val payload = Json.parseToJsonElement(assertNotNull(database.readPayload())).jsonObject
            assertEquals(true, payload.getValue("partitionedCaches").jsonPrimitive.boolean)
            assertEquals(0, payload.getValue("snapshots").jsonArray.size)
            assertEquals(setOf("thread:${tab.key}"), database.readCacheRecords().records.keys)
            database.close()

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals("x".repeat(2_000), assertNotNull(store.loadThreadSnapshot(tab.key)).posts.single().messageHtml)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun anUnrelatedChangeLeavesTheCacheRowsUntouchedAndAReplacedSnapshotIsRewritten() = runBlocking {
        val root = Files.createTempDirectory("compat-partition-rowid").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.openBoardAndTab()
            assertTrue(store.saveThreadSnapshot(snapshot()))
            val before = cacheRowIds(root)
            assertEquals(1, before.size)

            store.savePreference("compat.test.other", "1")
            store.upsertNgRule(CompatNgRule("rule", CompatNgKind.THREAD_IGNORE, tab.key, "x", 1L))
            assertEquals(before, cacheRowIds(root))

            assertTrue(store.saveThreadSnapshot(snapshot("y".repeat(2_000)).copy(revision = 2L)))
            val after = cacheRowIds(root)
            assertEquals(before.keys, after.keys)
            assertTrue(after.getValue("thread:${tab.key}") != before.getValue("thread:${tab.key}"))

            store.clearThreadSnapshotCache()
            assertEquals(emptyMap<String, Long>(), cacheRowIds(root))
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun aProfileStoredWithTheCachesInsideThePayloadIsReadAndMovedToRowsOnTheNextChange(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-partition-legacy").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.openBoardAndTab()
            assertTrue(store.saveThreadSnapshot(snapshot()))
            store.close()

            // Rewrite the database the way an earlier build did: caches embedded, no rows.
            val database = DesktopCompatibilityDatabase(fs)
            val payload = Json.parseToJsonElement(assertNotNull(database.readPayload())).jsonObject
            val embedded = database.readCacheRecords().records.values.map { Json.parseToJsonElement(it) }
            val oldStyle = JsonObject(payload + mapOf(
                "partitionedCaches" to JsonPrimitive(false),
                "snapshots" to JsonArray(embedded)
            ))
            database.clearCacheRecords()
            database.writePayload(oldStyle.toString(), 2L)
            database.close()

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals("x".repeat(2_000), assertNotNull(store.loadThreadSnapshot(tab.key)).posts.single().messageHtml)
            store.savePreference("compat.test.other", "1")
            store.close()

            val migrated = DesktopCompatibilityDatabase(fs)
            val migratedPayload = Json.parseToJsonElement(assertNotNull(migrated.readPayload())).jsonObject
            assertEquals(0, migratedPayload.getValue("snapshots").jsonArray.size)
            assertEquals(setOf("thread:${tab.key}"), migrated.readCacheRecords().records.keys)
            migrated.close()

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertNotNull(store.loadThreadSnapshot(tab.key))
        } finally { store.close(); root.deleteRecursively() }
    }
}
