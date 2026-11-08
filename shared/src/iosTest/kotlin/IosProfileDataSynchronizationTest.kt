package com.valoser.futacha.shared

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.state.PlatformStateStorage
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosProfileDataSynchronizationTest {
    @Test fun modernBoardWriteFailureAllowsLaterBoardsAndHistory() = verifyFailure(Path.MODERN_BOARDS)
    @Test fun compatibilityBoardFailureAllowsLaterBoardsAndHistory() = verifyFailure(Path.COMPAT_BOARDS)
    @Test fun historyFailureAllowsLaterHistoryAndBoards() = verifyFailure(Path.HISTORY)

    private enum class Path { MODERN_BOARDS, COMPAT_BOARDS, HISTORY }

    /**
     * D1 (D-B1): a board restored into the compatibility store, with what hangs under it, survives the synchronization
     * of the AppState modes (it used to be deleted with its tab and board NG rule) and of としあき(仮); a deletion in the
     * mode's own list still takes it from both.
     */
    @Test fun aRestoredBoardSurvivesTheAppStateModesSynchronization() = verifyRestoredBoard(usesAppStateData = true)
    @Test fun aRestoredBoardSurvivesToshiakisSynchronization() = verifyRestoredBoard(usesAppStateData = false)

    private fun verifyRestoredBoard(usesAppStateData: Boolean) = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        val modern = AppStateStore(FakePlatformStateStorage())
        val compat = IosCompatibilityStore(fs)
        val may = BoardSummary("b", "may", "", "https://may.2chan.net/b/futaba.php", "")
        val img = BoardSummary("img", "img", "", "https://img.2chan.net/b/futaba.php", "")
        val imgKey = compatBoardKey("https://img.2chan.net/b/")
        val thread = "https://img.2chan.net/b/res/200.htm"
        val tab = CompatTab(compatTabKey(thread), thread, thread, imgKey, "img", "200", "スレ", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
        val rule = CompatNgRule(compatNgRuleId(CompatNgKind.CATALOG_WORD, imgKey, "荒らし"), CompatNgKind.CATALOG_WORD, imgKey, "荒らし", 2)
        suspend fun synchronizeUntil(timeoutMillis: Long = 10_000, done: suspend () -> Boolean): Boolean {
            val job = launch { synchronizeIosProfileData(modern, compat, usesAppStateData) { _, error -> throw error } }
            val reached = withTimeoutOrNull(timeoutMillis) { while (!done()) delay(20); delay(100); true } ?: false
            job.cancelAndJoin()
            return reached
        }
        try {
            compat.initialize()
            modern.setBoards(listOf(may))
            compat.upsertBoards(modernBoardsToCompatibility(listOf(may)))
            synchronizeUntil { true }
            compat.importSettingsBackup(
                encodeCompatSettingsBackup(
                    CompatSettingsBackup(
                        exportedAtEpochMillis = 1, boards = modernBoardsToCompatibility(listOf(img)), tabs = listOf(tab),
                        ngRules = listOf(rule), workspace = null
                    )
                )
            )
            assertTrue(synchronizeUntil { modern.boards.first().any { it.url == img.url || it.id == imgKey } })
            assertTrue(compat.boards.first().any { it.key == imgKey }, "the restored board stays in the store")
            assertTrue(compat.tabs.first().any { it.key == tab.key }, "with its tab")
            assertTrue(compat.ngRules.first().any { it.id == rule.id }, "and its NG rule")
            if (usesAppStateData) modern.updateBoards { boards -> boards.filterNot { it.url == img.url || it.id == imgKey } }
            else compat.deleteBoard(imgKey)
            assertTrue(synchronizeUntil {
                modern.boards.first().none { it.url == img.url || it.id == imgKey } && compat.boards.first().none { it.key == imgKey }
            }, "a deletion in the mode's own list takes it from both")
            synchronizeUntil(timeoutMillis = 500) { false }
            assertTrue(modern.boards.first().none { it.url == img.url || it.id == imgKey }, "and it stays deleted")
        } finally {
            modern.close()
            fs.deleteRecursively("compatibility").getOrThrow()
        }
    }

    private fun verifyFailure(path: Path) = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        val backing = FakePlatformStateStorage()
        val failure = IllegalStateException("injected synchronization storage failure")
        var nextBoardReadFailure: Exception? = null
        val storage = object : PlatformStateStorage by backing {
            override val boardsJson: Flow<String?> = flow {
                val error = nextBoardReadFailure
                nextBoardReadFailure = null
                if (error != null) throw error
                emitAll(backing.boardsJson)
            }
        }
        val modern = AppStateStore(storage)
        val original = BoardSummary("may", "元の板", "分類", "https://may.2chan.net/b/", "説明")
        modern.setBoards(listOf(original))
        val real = IosCompatibilityStore(fs)
        real.initialize()
        // The board sync skips a write when nothing changed (audit D5), so for the failing modern write the
        // compatibility copy starts with an older name: the first sync then has a real change to write.
        val seeded = if (path == Path.MODERN_BOARDS) original.copy(name = "同期前の板") else original
        real.upsertBoards(modernBoardsToCompatibility(listOf(seeded)))
        val boards = Channel<List<CompatBoard>>(Channel.UNLIMITED)
        val history = Channel<List<CompatHistoryEntry>>(Channel.UNLIMITED)
        val reported = Channel<Throwable>(Channel.UNLIMITED)
        var escaped: Throwable? = null
        var failNextWrite = path == Path.MODERN_BOARDS
        val compat = object : CompatibilityStore by real {
            override val boards = if (path == Path.MODERN_BOARDS) real.boards else boards.receiveAsFlow()
            override val history = history.receiveAsFlow()
            override suspend fun upsertBoards(boards: List<CompatBoard>) {
                if (failNextWrite) {
                    failNextWrite = false
                    throw failure
                }
                real.upsertBoards(boards)
            }
        }
        val job = launch {
            try {
                synchronizeIosProfileData(modern, compat, path == Path.MODERN_BOARDS) { _, error ->
                    reported.trySend(error)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                escaped = error
                reported.trySend(error)
            }
        }
        val entry = CompatHistoryEntry(
            "https://may.2chan.net/b/res/123456.htm", "https://may.2chan.net/b/res/123456.htm",
            compatBoardKey(original.url), original.name, "123456", "同期する履歴",
            contentUpdatedAtEpochMillis = 1_000, lastVisitedEpochMillis = 1_000
        )
        try {
            when (path) {
                Path.MODERN_BOARDS -> Unit // The initial modern-board emission tries the failing write.
                Path.COMPAT_BOARDS -> {
                    nextBoardReadFailure = failure
                    boards.send(real.boards.first())
                }
                Path.HISTORY -> {
                    nextBoardReadFailure = failure
                    history.send(listOf(entry))
                }
            }
            assertSame(failure, withTimeout(10_000) { reported.receive() })
            assertNull(escaped, "A failed synchronization must not escape the profile effect")
            assertTrue(job.isActive, "The other collector must remain active")
            assertEquals(listOf(original), modern.boards.first())

            val renamed = original.copy(name = "更新した板")
            if (path == Path.MODERN_BOARDS) {
                modern.setBoards(listOf(renamed))
                withTimeout(10_000) { real.boards.first { it.singleOrNull()?.name == renamed.name } }
            } else {
                boards.send(modernBoardsToCompatibility(listOf(renamed)))
                withTimeout(10_000) { modern.boards.first { it.singleOrNull()?.name == renamed.name } }
            }
            history.send(listOf(entry.copy(title = "回復後の履歴", lastVisitedEpochMillis = 2_000)))
            withTimeout(10_000) { modern.history.first { it.singleOrNull()?.title == "回復後の履歴" } }
            assertTrue(job.isActive)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(reported.tryReceive().isFailure, "Profile cancellation must not be logged as storage failure")
        } finally {
            job.cancelAndJoin()
            modern.close()
            boards.close()
            history.close()
            reported.close()
            fs.deleteRecursively("compatibility").getOrThrow()
        }
    }
}
