package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The thread snapshot count is capped by recent use, on every save and at launch, as on iOS (L84). */
class DesktopCompatibilityStoreSnapshotTrimTest {
    private val boardUrl = "https://may.2chan.net/b/"
    // DesktopCompatibilityStore.MAX_THREAD_SNAPSHOTS
    private val cap = 512
    private var clock = 1_000L

    @Test
    fun runtimeSavesEvictTheLeastRecentlyUsedSnapshotBeyondTheCap(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-snapshot-trim-runtime").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
        try {
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", boardUrl, "")))
            val urls = (0 until cap + 2).map { index -> "${boardUrl}res/${1_000 + index}.htm" }
            store.openTab(tab(urls[0], revision = 3L))
            urls.forEach { url -> assertTrue(store.saveThreadSnapshot(snapshot(url, revision = 3L))) }

            assertNull(store.loadThreadSnapshot(compatTabKey(urls[0])))
            assertNull(store.loadThreadSnapshot(compatTabKey(urls[1])))
            assertNotNull(store.loadThreadSnapshot(compatTabKey(urls[2])))
            assertNotNull(store.loadThreadSnapshot(compatTabKey(urls.last())))
            // The open tab no longer claims the revision of its evicted snapshot.
            assertEquals(0L, store.tabs.first().single().snapshotRevision)
            store.close()

            store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
            store.initialize()
            assertNull(store.loadThreadSnapshot(compatTabKey(urls[0])))
            assertNotNull(store.loadThreadSnapshot(compatTabKey(urls.last())))
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun launchTrimKeepsTheMostRecentlyUsedSnapshotsAndResetsDroppedTabs(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-snapshot-trim-launch").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
        val urls = (0 until cap + 3).map { index -> "${boardUrl}res/${2_000 + index}.htm" }
        try {
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", boardUrl, "")))
            store.openTab(tab(urls[0], revision = 7L))
            store.openTab(tab(urls.last(), revision = 7L))
            store.close()

            // As an older build left it: more rows than the cap, the oldest written first.
            val database = DesktopCompatibilityDatabase(fs)
            try {
                val payload = assertNotNull(database.readPayload())
                database.writePayload(
                    payload,
                    updatedAtMillis = 2L,
                    cacheUpdates = urls.withIndex().associate { (index, url) ->
                        val row = snapshot(url, revision = 7L).copy(fetchedAtEpochMillis = 10_000L + index)
                        "thread:${row.tabKey}" to Json.encodeToString(CompatThreadSnapshot.serializer(), row)
                    }
                )
            } finally { database.close() }

            store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
            store.initialize()
            urls.take(3).forEach { url -> assertNull(store.loadThreadSnapshot(compatTabKey(url)), url) }
            urls.drop(3).forEach { url -> assertNotNull(store.loadThreadSnapshot(compatTabKey(url)), url) }
            val tabs = store.tabs.first().associateBy(CompatTab::canonicalUrl)
            assertEquals(0L, tabs.getValue(urls[0]).snapshotRevision)
            assertEquals(7L, tabs.getValue(urls.last()).snapshotRevision)
        } finally { store.close(); root.deleteRecursively() }
    }

    private fun tab(url: String, revision: Long) = CompatTab(
        key = compatTabKey(url),
        canonicalUrl = url,
        originalUrl = url,
        boardKey = compatBoardKey(boardUrl),
        boardName = "板",
        threadNo = url.substringAfterLast('/').substringBefore('.'),
        title = "スレ",
        insertedAtEpochMillis = 1L,
        contentUpdatedAtEpochMillis = 1L,
        snapshotRevision = revision
    )

    private fun snapshot(url: String, revision: Long) = CompatThreadSnapshot(
        tabKey = compatTabKey(url),
        revision = revision,
        fetchedAtEpochMillis = clock,
        posts = emptyList()
    )
}
