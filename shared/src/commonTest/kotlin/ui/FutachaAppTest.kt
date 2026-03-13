package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutachaAppTest {
    @Test
    fun resolveHistoryEntrySelection_prefersBoardIdAndKeepsThreadUrlWhenResUrl() {
        val targetBoard = board(
            id = "img-b",
            name = "img",
            url = "https://may.2chan.net/img/futaba.php"
        )
        val entry = historyEntry(
            boardId = "img-b",
            boardName = "fallback-name",
            boardUrl = "https://may.2chan.net/img/res/123.htm"
        )

        val selection = resolveHistoryEntrySelection(entry, listOf(targetBoard, board(id = "other", name = "fallback-name")))

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
        assertEquals("123", selection.threadId)
        assertEquals(entry.title, selection.threadTitle)
        assertEquals(10, selection.threadReplies)
        assertEquals(entry.titleImageUrl, selection.threadThumbnailUrl)
        assertEquals("https://may.2chan.net/img/res/123.htm", selection.threadUrl)
    }

    @Test
    fun resolveHistoryEntrySelection_fallsBackToBoardUrlAndDropsNonThreadUrl() {
        val board = board(
            id = "img-b",
            name = "img",
            url = "https://may.2chan.net/img/futaba.php?mode=cat"
        )
        val entry = historyEntry(
            boardId = "",
            boardName = "different",
            boardUrl = "https://may.2chan.net/img/futaba.php"
        )

        val selection = resolveHistoryEntrySelection(entry, listOf(board))

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
        assertNull(selection.threadUrl)
    }

    @Test
    fun resolveHistoryEntrySelection_returnsNullWhenNoBoardMatches() {
        val entry = historyEntry(boardId = "missing", boardName = "missing", boardUrl = "https://nope.invalid/res/1.htm")

        val selection = resolveHistoryEntrySelection(entry, listOf(board()))

        assertNull(selection)
    }

    @Test
    fun resolveSavedThreadSelection_prefersBoardIdAndHidesSavedThreads() {
        val savedThread = savedThread(boardId = "img-b", boardName = "fallback")

        val selection = resolveSavedThreadSelection(
            thread = savedThread,
            boards = listOf(board(id = "img-b", name = "img"))
        )

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
        assertEquals(savedThread.threadId, selection.threadId)
        assertEquals(savedThread.title, selection.threadTitle)
        assertEquals(savedThread.postCount, selection.threadReplies)
        assertNull(selection.threadThumbnailUrl)
        assertNull(selection.threadUrl)
        assertFalse(selection.isSavedThreadsVisible)
    }

    @Test
    fun resolveSavedThreadSelection_fallsBackToBoardName() {
        val savedThread = savedThread(boardId = "missing", boardName = "img")

        val selection = resolveSavedThreadSelection(
            thread = savedThread,
            boards = listOf(board(id = "img-b", name = "img"))
        )

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
    }

    @Test
    fun shouldApplyRegisteredThreadNavigation_onlySkipsExactSameTarget() {
        val target = RegisteredThreadNavigation(
            board = board(id = "img-b"),
            threadId = "123",
            threadUrl = "https://may.2chan.net/img/res/123.htm"
        )

        assertFalse(
            shouldApplyRegisteredThreadNavigation(
                currentBoardId = "img-b",
                currentThreadId = "123",
                currentThreadUrl = "https://may.2chan.net/img/res/123.htm",
                target = target
            )
        )
        assertTrue(
            shouldApplyRegisteredThreadNavigation(
                currentBoardId = "img-b",
                currentThreadId = "123",
                currentThreadUrl = "https://may.2chan.net/img/res/123.html",
                target = target
            )
        )
    }

    @Test
    fun normalizeBoardUrl_addsSchemeAndFutabaPathWhilePreservingQueryAndFragment() {
        assertEquals(
            "https://may.2chan.net/b/futaba.php",
            normalizeBoardUrl("may.2chan.net/b")
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat#frag",
            normalizeBoardUrl("https://may.2chan.net/b?mode=cat#frag")
        )
        assertEquals(
            "http://may.2chan.net/b/futaba.php",
            normalizeBoardUrl("http://may.2chan.net/b/")
        )
    }

    @Test
    fun appSupport_helpers_detectDefaultSaveRootAndMockBoards() {
        assertTrue(isDefaultManualSaveRoot("./Documents/"))
        assertFalse(isDefaultManualSaveRoot("/tmp/custom"))
        assertTrue(board(url = "https://example.com/futaba.php").isMockBoard())
        assertFalse(board(url = "https://may.2chan.net/b/futaba.php").isMockBoard())
    }

    @Test
    fun isSelectedBoardStillMissing_requiresSameSelectionAndAbsentBoard() {
        val boards = listOf(board(id = "img-b"))

        assertTrue(
            isSelectedBoardStillMissing(
                selectedBoardId = "missing",
                missingBoardId = "missing",
                boards = boards
            )
        )
        assertFalse(
            isSelectedBoardStillMissing(
                selectedBoardId = "img-b",
                missingBoardId = "missing",
                boards = boards
            )
        )
        assertFalse(
            isSelectedBoardStillMissing(
                selectedBoardId = "missing",
                missingBoardId = "img-b",
                boards = boards
            )
        )
    }

    @Test
    fun resolveHistoryEntryBoardId_usesFallbackSlugWhenBoardIdIsBlank() {
        val entry = historyEntry(
            boardId = "",
            boardUrl = "https://may.2chan.net/img/res/123.htm"
        )

        val resolved = resolveHistoryEntryBoardId(entry)

        assertEquals("img", resolved)
    }

    @Test
    fun dismissHistoryEntry_removesHistorySelfIdentifiersAndAutoSavedThread() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "saved_threads")
        val entry = historyEntry(boardId = "", boardUrl = "https://may.2chan.net/img/res/123.htm")
        store.setHistory(listOf(entry))
        store.addSelfPostIdentifier(threadId = entry.threadId, identifier = "ID:abc", boardId = "img")
        repository.addThreadToIndex(savedThread(boardId = "img")).getOrThrow()

        dismissHistoryEntry(
            stateStore = store,
            autoSavedThreadRepository = repository,
            entry = entry
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
        assertFalse(repository.threadExists(entry.threadId, "img"))
    }

    @Test
    fun dismissHistoryEntry_preservesStateChangesWhenAutoSavedDeleteFails() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(
            DeleteRecursivelyFailingFileSystem(InMemoryFileSystem()),
            baseDirectory = "saved_threads"
        )
        val entry = historyEntry(boardId = "img")
        store.setHistory(listOf(entry))
        store.addSelfPostIdentifier(threadId = entry.threadId, identifier = "ID:abc", boardId = "img")
        repository.addThreadToIndex(savedThread(boardId = "img")).getOrThrow()
        dismissHistoryEntry(
            stateStore = store,
            autoSavedThreadRepository = repository,
            entry = entry
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
    }

    @Test
    fun clearHistory_clearsStateInvokesSkippedCallbackAndDeletesAutoSavedThreads() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "saved_threads")
        val firstEntry = historyEntry(threadId = "123", boardId = "img")
        val secondEntry = historyEntry(threadId = "456", boardId = "dat")
        store.setHistory(listOf(firstEntry, secondEntry))
        store.addSelfPostIdentifier(threadId = "123", identifier = "ID:abc", boardId = "img")
        store.addSelfPostIdentifier(threadId = "456", identifier = "ID:def", boardId = "dat")
        repository.addThreadToIndex(savedThread(threadId = "123", boardId = "img")).getOrThrow()
        repository.addThreadToIndex(savedThread(threadId = "456", boardId = "dat")).getOrThrow()
        var clearedCount = 0

        clearHistory(
            stateStore = store,
            autoSavedThreadRepository = repository,
            onSkippedThreadsCleared = { clearedCount += 1 }
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
        assertEquals(1, clearedCount)
        assertFalse(repository.threadExists("123", "img"))
        assertFalse(repository.threadExists("456", "dat"))
    }

    @Test
    fun clearHistory_reportsAutoSavedDeleteFailureButStillClearsStoreState() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(
            DeleteRecursivelyFailingFileSystem(InMemoryFileSystem()),
            baseDirectory = "saved_threads"
        )
        val entry = historyEntry(threadId = "123", boardId = "img")
        store.setHistory(listOf(entry))
        store.addSelfPostIdentifier(threadId = "123", identifier = "ID:abc", boardId = "img")
        repository.addThreadToIndex(savedThread(threadId = "123", boardId = "img")).getOrThrow()
        clearHistory(
            stateStore = store,
            autoSavedThreadRepository = repository,
            onSkippedThreadsCleared = {}
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
    }

    private fun board(
        id: String = "img-b",
        name: String = "img",
        url: String = "https://may.2chan.net/img/futaba.php"
    ): BoardSummary {
        return BoardSummary(
            id = id,
            name = name,
            category = "",
            url = url,
            description = ""
        )
    }

    private fun historyEntry(
        threadId: String = "123",
        boardId: String = "img-b",
        boardName: String = "img",
        boardUrl: String = "https://may.2chan.net/img/res/123.htm"
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = boardId,
            title = "title-$threadId",
            titleImageUrl = "thumb-$threadId",
            boardName = boardName,
            boardUrl = boardUrl,
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )
    }

    private fun savedThread(
        threadId: String = "123",
        boardId: String = "img-b",
        boardName: String = "img"
    ): SavedThread {
        return SavedThread(
            threadId = threadId,
            boardId = boardId,
            boardName = boardName,
            title = "saved-title-$threadId",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 0,
            videoCount = 0,
            totalSize = 100L,
            status = SaveStatus.COMPLETED
        )
    }
}

private class DeleteRecursivelyFailingFileSystem(
    private val delegate: InMemoryFileSystem
) : FileSystem by delegate {
    override suspend fun deleteRecursively(path: String): Result<Unit> {
        return Result.failure(IllegalStateException("cannot delete"))
    }
}
