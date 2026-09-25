package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosCompatibilityStoreSnapshotTrimTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private var clock = 1_000L

    @Test
    fun launchTrimKeepsTheMostRecentlyUsedSnapshotsAndResetsDroppedTabs(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { ++clock }, maxThreadSnapshots = 10)
            store.initialize()
            store.upsertBoard(board())
            val urls = (0 until 5).map { index -> "${boardUrl}res/${100 + index}.htm" }
            urls.forEachIndexed { index, url ->
                store.openTab(tab(url, "${100 + index}", insertedAt = index.toLong(), revision = 7L), null)
                assertTrue(store.saveThreadSnapshot(snapshot(compatTabKey(url), revision = 7L, fetchedAt = clock)))
            }
            // The oldest row becomes the most recently used one.
            assertNotNull(store.loadThreadSnapshot(compatTabKey(urls[0])))

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { ++clock }, maxThreadSnapshots = 3)
            reopened.initialize()
            val kept = listOf(urls[0], urls[3], urls[4])
            val dropped = listOf(urls[1], urls[2])
            kept.forEach { url -> assertNotNull(reopened.loadThreadSnapshot(compatTabKey(url)), url) }
            dropped.forEach { url -> assertNull(reopened.loadThreadSnapshot(compatTabKey(url)), url) }
            val tabs = reopened.tabs.first().associateBy(CompatTab::canonicalUrl)
            kept.forEach { url -> assertEquals(7L, tabs.getValue(url).snapshotRevision) }
            dropped.forEach { url -> assertEquals(0L, tabs.getValue(url).snapshotRevision) }
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun runtimeSavesEvictTheLeastRecentlyUsedSnapshotBeyondTheCap(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { ++clock }, maxThreadSnapshots = 3)
            store.initialize()
            store.upsertBoard(board())
            val urls = (0 until 4).map { index -> "${boardUrl}res/${200 + index}.htm" }
            urls.forEachIndexed { index, url ->
                store.openTab(tab(url, "${200 + index}", insertedAt = index.toLong(), revision = 3L), null)
                store.saveThreadSnapshot(snapshot(compatTabKey(url), revision = 3L, fetchedAt = clock))
            }
            assertNull(store.loadThreadSnapshot(compatTabKey(urls[0])))
            urls.drop(1).forEach { url -> assertNotNull(store.loadThreadSnapshot(compatTabKey(url))) }
            assertEquals(0L, store.tabs.first().first { it.canonicalUrl == urls[0] }.snapshotRevision)

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { ++clock }, maxThreadSnapshots = 3)
            reopened.initialize()
            assertNull(reopened.loadThreadSnapshot(compatTabKey(urls[0])))
            assertNotNull(reopened.loadThreadSnapshot(compatTabKey(urls[3])))
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    private fun board() = CompatBoard(
        key = "compat_board_may",
        name = "虹裏",
        canonicalUrl = boardUrl,
        originalUrl = boardUrl,
        sortOrder = 0
    )

    private fun tab(url: String, threadNo: String, insertedAt: Long, revision: Long) = CompatTab(
        key = compatTabKey(url),
        canonicalUrl = url,
        originalUrl = url,
        boardKey = "compat_board_may",
        boardName = "虹裏",
        threadNo = threadNo,
        title = "スレ$threadNo",
        insertedAtEpochMillis = insertedAt,
        contentUpdatedAtEpochMillis = insertedAt,
        snapshotRevision = revision
    )

    private fun snapshot(tabKey: String, revision: Long, fetchedAt: Long) = CompatThreadSnapshot(
        tabKey = tabKey,
        revision = revision,
        fetchedAtEpochMillis = fetchedAt,
        posts = emptyList()
    )
}
