package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SiteRefreshPausePersistenceTest {
    @Test fun concurrentPersistsCannotReplaceANewerPauseWithAnOlderSnapshot() = runTest {
        val files = InMemoryFileSystem()
        val firstWriting = CompletableDeferred<Unit>()
        val allowFirstWrite = CompletableDeferred<Unit>()
        var writes = 0
        val slow = object : FileSystem by files {
            override suspend fun writeString(path: String, content: String): Result<Unit> {
                if (++writes == 1) { firstWriting.complete(Unit); allowFirstWrite.await() }
                return files.writeString(path, content)
            }
        }
        try {
            SiteRefreshPause.reset()
            SiteRefreshPause.nowMillis = { 1L }
            SiteRefreshPause.recordSiteError(500, null)
            val first = async { SiteRefreshPause.persist(slow) }
            firstWriting.await()
            SiteRefreshPause.recordSiteError(429, null)
            val second = async { SiteRefreshPause.persist(slow) }
            // Let the newer caller attempt its write while the old one is blocked.
            testScheduler.runCurrent()
            allowFirstWrite.complete(Unit)
            first.await(); second.await()
            val expected = SiteRefreshPause.snapshot()
            SiteRefreshPause.reset()
            SiteRefreshPause.nowMillis = { 1L }
            SiteRefreshPause.load(files)
            assertEquals(expected, SiteRefreshPause.snapshot())
        } finally { SiteRefreshPause.reset() }
    }

    @Test fun persistingRemovesExpiredBoardsFromMemoryToo() = runTest {
        try {
            SiteRefreshPause.reset()
            var now = 1L
            SiteRefreshPause.nowMillis = { now }
            SiteRefreshPause.recordBoardGone("board")
            now += SITE_BOARD_PAUSE_MILLIS + 1
            SiteRefreshPause.persist(InMemoryFileSystem())
            assertTrue(SiteRefreshPause.snapshot().boards.isEmpty())
        } finally { SiteRefreshPause.reset() }
    }
}
