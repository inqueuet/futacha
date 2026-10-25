package com.valoser.futacha.shared

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.state.PlatformStateStorage
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
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
        real.upsertBoards(modernBoardsToCompatibility(listOf(original)))
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
