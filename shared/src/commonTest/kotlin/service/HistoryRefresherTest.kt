package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.parser.HtmlParser
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryRefresherTest {
    @Test
    fun refresh_updatesHistoryEntryFromFetchedThread() = runBlocking {
        val board = boardSummary()
        val entry = historyEntry(threadId = "123", title = "old")
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry))
        val repository = FakeHistoryBoardRepository().apply {
            threadPages[board.url to "123"] = threadPage(
                threadId = "123",
                boardTitle = "board-new",
                titleLine = "new title",
                thumbnailUrl = "thumb-new",
                replyCount = 2
            )
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            maxConcurrency = 1
        )

        refresher.refresh(
            boardsSnapshot = listOf(board),
            historySnapshot = listOf(entry)
        )

        val updated = store.history.first().single()
        assertEquals("new title", updated.title)
        assertEquals("thumb-new", updated.titleImageUrl)
        assertEquals("board-new", updated.boardName)
        assertEquals(2, updated.replyCount)
        assertEquals(1, repository.getThreadCalls)
    }

    @Test
    fun refresh_skips404ThreadOnSubsequentRuns() = runBlocking {
        val board = boardSummary()
        val entry = historyEntry(threadId = "404")
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry))
        val repository = FakeHistoryBoardRepository().apply {
            threadErrors[board.url to "404"] = NetworkException("gone", statusCode = 404)
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            maxConcurrency = 1
        )

        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))
        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))

        assertEquals(1, repository.getThreadCalls)
        assertEquals(entry, store.history.first().single())
    }

    @Test
    fun refresh_usesArchiveFallbackWhenThreadIsMissing() = runBlocking {
        val board = boardSummary()
        val entry = historyEntry(threadId = "555", title = "old archive title")
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry))
        val repository = FakeHistoryBoardRepository().apply {
            threadErrors[board.url to "555"] = NetworkException("gone", statusCode = 404)
            threadPagesByUrl["https://may.2chan.net/b/res/555.htm"] = threadPage(
                threadId = "555",
                boardTitle = "archive-board",
                titleLine = "archive title",
                thumbnailUrl = "archive-thumb",
                replyCount = 1
            )
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            httpClient = createArchiveClient(
                """
                    {"results":[
                      {"threadId":"555","server":"may","board":"b","title":"archive candidate","htmlUrl":"https://may.2chan.net/b/res/555.htm","thumbUrl":"https://may.2chan.net/thumb/555s.jpg"}
                    ]}
                """.trimIndent()
            ),
            maxConcurrency = 1
        )

        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))

        val updated = store.history.first().single()
        assertEquals("archive title", updated.title)
        assertEquals("archive-thumb", updated.titleImageUrl)
        assertEquals("archive-board", updated.boardName)
        assertEquals("https://may.2chan.net/b", updated.boardUrl)
        assertEquals(1, repository.getThreadCalls)
        assertEquals(1, repository.getThreadByUrlCalls)
    }

    @Test
    fun refresh_autoSavesThreadAndMarksHistoryEntry() = runBlocking {
        val board = boardSummary()
        val entry = historyEntry(threadId = "777")
        val fileSystem = InMemoryFileSystem()
        val autoSavedRepository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = AUTO_SAVE_DIRECTORY
        )
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry))
        val repository = FakeHistoryBoardRepository().apply {
            threadPages[board.url to "777"] = threadPage(
                threadId = "777",
                boardTitle = "board-auto",
                titleLine = "auto saved title",
                thumbnailUrl = "thumb-auto",
                replyCount = 1
            )
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            autoSavedThreadRepository = autoSavedRepository,
            httpClient = createThreadHtmlClient("<html><body>thread html</body></html>"),
            fileSystem = fileSystem,
            maxConcurrency = 1,
            autoSaveMaxConcurrency = 1,
            maxAutoSavesPerRefresh = 1
        )

        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))

        waitUntil("auto-save to complete") {
            autoSavedRepository.getAllThreads().isNotEmpty() &&
                store.history.first().single().hasAutoSave
        }

        val saved = autoSavedRepository.getAllThreads().single()
        val updated = store.history.first().single()
        assertEquals("777", saved.threadId)
        assertTrue(updated.hasAutoSave)
        assertEquals("auto saved title", updated.title)
    }

    @Test
    fun refresh_doesNotMarkHistoryEntryAutoSavedWhenIndexWriteFails() = runBlocking {
        val board = boardSummary()
        val entry = historyEntry(threadId = "777")
        val fileSystem = IndexWriteFailingFileSystem()
        val autoSavedRepository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = AUTO_SAVE_DIRECTORY
        )
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry))
        val repository = FakeHistoryBoardRepository().apply {
            threadPages[board.url to "777"] = threadPage(
                threadId = "777",
                boardTitle = "board-auto",
                titleLine = "auto saved title",
                thumbnailUrl = "thumb-auto",
                replyCount = 1
            )
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            autoSavedThreadRepository = autoSavedRepository,
            httpClient = createThreadHtmlClient("<html><body>thread html</body></html>"),
            fileSystem = fileSystem,
            maxConcurrency = 1,
            autoSaveMaxConcurrency = 1,
            maxAutoSavesPerRefresh = 1
        )

        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))

        waitUntil("auto-save write attempt to complete") {
            fileSystem.exists(
                "${AUTO_SAVE_DIRECTORY}/${buildThreadStorageId(board.id, "777")}/metadata.json"
            )
        }

        assertTrue(autoSavedRepository.getAllThreads().isEmpty())
        assertFalse(store.history.first().single().hasAutoSave)
    }

    @Test
    fun refresh_skipListIsScopedPerBoardForSameThreadId() = runBlocking {
        val missingBoard = boardSummary(id = "b1", name = "board-1", url = "https://may.2chan.net/b/futaba.php")
        val activeBoard = boardSummary(id = "b2", name = "board-2", url = "https://dat.2chan.net/img/futaba.php")
        val missingEntry = historyEntry(
            threadId = "900",
            boardId = missingBoard.id,
            boardName = missingBoard.name,
            boardUrl = "https://may.2chan.net/b/res/900.htm"
        )
        val activeEntry = historyEntry(
            threadId = "900",
            boardId = activeBoard.id,
            boardName = activeBoard.name,
            boardUrl = "https://dat.2chan.net/img/res/900.htm"
        )
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(missingEntry, activeEntry))
        val repository = FakeHistoryBoardRepository().apply {
            threadErrors[missingBoard.url to "900"] = NetworkException("gone", statusCode = 404)
            threadPages[activeBoard.url to "900"] = threadPage(
                threadId = "900",
                boardTitle = "active-board",
                titleLine = "active title",
                thumbnailUrl = "active-thumb",
                replyCount = 3
            )
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            maxConcurrency = 1
        )

        refresher.refresh(
            boardsSnapshot = listOf(missingBoard, activeBoard),
            historySnapshot = listOf(missingEntry, activeEntry)
        )
        refresher.refresh(
            boardsSnapshot = listOf(missingBoard, activeBoard),
            historySnapshot = store.history.first()
        )

        assertEquals(3, repository.getThreadCalls)
        val updated = store.history.first()
        assertEquals("title-900", updated.first { it.boardId == missingBoard.id }.title)
        assertEquals("active title", updated.first { it.boardId == activeBoard.id }.title)
    }

    @Test
    fun clearSkippedThreads_allowsRetryAfter404() = runBlocking {
        val board = boardSummary()
        val entry = historyEntry(threadId = "404")
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry))
        val repository = FakeHistoryBoardRepository().apply {
            threadErrors[board.url to "404"] = NetworkException("gone", statusCode = 404)
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            maxConcurrency = 1
        )

        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))
        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))
        refresher.clearSkippedThreads()
        refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = listOf(entry))

        assertEquals(2, repository.getThreadCalls)
    }

    @Test
    fun refresh_recordsPartialFailuresWithoutDiscardingSuccessfulUpdates() = runBlocking {
        val okBoard = boardSummary(id = "ok", name = "ok-board", url = "https://may.2chan.net/ok/futaba.php")
        val failingBoard = boardSummary(id = "ng", name = "ng-board", url = "https://may.2chan.net/ng/futaba.php")
        val okEntry = historyEntry(threadId = "101", boardId = okBoard.id, boardName = okBoard.name, boardUrl = "https://may.2chan.net/ok/res/101.htm")
        val failingEntry = historyEntry(threadId = "202", boardId = failingBoard.id, boardName = failingBoard.name, boardUrl = "https://may.2chan.net/ng/res/202.htm")
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(okEntry, failingEntry))
        val repository = FakeHistoryBoardRepository().apply {
            threadPages[okBoard.url to "101"] = threadPage(
                threadId = "101",
                boardTitle = "ok-board-new",
                titleLine = "updated ok title",
                thumbnailUrl = "ok-thumb",
                replyCount = 4
            )
            threadErrors[failingBoard.url to "202"] = IllegalStateException("backend down")
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            maxConcurrency = 1
        )

        refresher.refresh(
            boardsSnapshot = listOf(okBoard, failingBoard),
            historySnapshot = listOf(okEntry, failingEntry)
        )

        val updated = store.history.first()
        assertEquals("updated ok title", updated.first { it.threadId == "101" }.title)
        assertEquals("title-202", updated.first { it.threadId == "202" }.title)
        val error = refresher.lastRefreshError.first()
        assertEquals(1, error?.errorCount)
        assertEquals(2, error?.totalThreads)
        assertEquals(1, error?.stageCounts?.get("thread_refresh"))
        assertEquals("202", error?.errors?.single()?.threadId)
    }

    @Test
    fun refresh_recordsArchiveLookupFailuresAsPartialErrors() = runBlocking {
        val archiveBoard = boardSummary(id = "b", name = "archive-board", url = "https://may.2chan.net/b/futaba.php")
        val okBoard = boardSummary(id = "img", name = "img-board", url = "https://dat.2chan.net/img/futaba.php")
        val archiveEntry = historyEntry(threadId = "404", boardId = archiveBoard.id, boardName = archiveBoard.name, boardUrl = "https://may.2chan.net/b/res/404.htm")
        val okEntry = historyEntry(threadId = "505", boardId = okBoard.id, boardName = okBoard.name, boardUrl = "https://dat.2chan.net/img/res/505.htm")
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(archiveEntry, okEntry))
        val repository = FakeHistoryBoardRepository().apply {
            threadErrors[archiveBoard.url to "404"] = NetworkException("gone", statusCode = 404)
            threadPages[okBoard.url to "505"] = threadPage(
                threadId = "505",
                boardTitle = "img-new",
                titleLine = "ok title",
                thumbnailUrl = "ok-thumb",
                replyCount = 2
            )
        }
        val refresher = HistoryRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default,
            httpClient = createFailingArchiveClient("archive unavailable"),
            maxConcurrency = 1
        )

        refresher.refresh(
            boardsSnapshot = listOf(archiveBoard, okBoard),
            historySnapshot = listOf(archiveEntry, okEntry)
        )

        val updated = store.history.first()
        assertEquals("ok title", updated.first { it.threadId == "505" }.title)
        assertEquals("title-404", updated.first { it.threadId == "404" }.title)
        val error = refresher.lastRefreshError.first()
        assertEquals(1, error?.stageCounts?.get("archive_lookup"))
        assertEquals("404", error?.errors?.single()?.threadId)
    }
}

private class FakeHistoryBoardRepository : BoardRepository {
    val threadPages = mutableMapOf<Pair<String, String>, ThreadPage>()
    val threadErrors = mutableMapOf<Pair<String, String>, Throwable>()
    val threadPagesByUrl = mutableMapOf<String, ThreadPage>()
    var getThreadCalls = 0
    var getThreadByUrlCalls = 0

    override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = emptyList()

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? = null

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        getThreadCalls += 1
        threadErrors[board to threadId]?.let { throw it }
        return threadPages[board to threadId] ?: error("Missing thread for $board/$threadId")
    }

    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
        getThreadByUrlCalls += 1
        return threadPagesByUrl[threadUrl] ?: error("Missing thread for $threadUrl")
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) = Unit

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) = Unit

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) = Unit

    override suspend fun replyToThread(
        board: String,
        threadId: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String? = null

    override suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String? = null

    override fun close() = Unit

    override fun closeAsync() = kotlinx.coroutines.Job().apply { complete() }

    override suspend fun clearOpImageCache(board: String?, threadId: String?) = Unit

    override suspend fun invalidateCookies(board: String) = Unit
}

private fun createArchiveClient(
    responseBody: String
): HttpClient {
    val engine = MockEngine(
        MockEngineConfig().apply {
            addHandler { archiveJsonResponse(responseBody) }
        }
    )
    return HttpClient(engine)
}

private fun createThreadHtmlClient(
    responseBody: String
): HttpClient {
    val engine = MockEngine(
        MockEngineConfig().apply {
            addHandler { threadHtmlResponse(responseBody) }
        }
    )
    return HttpClient(engine)
}

private fun createFailingArchiveClient(
    message: String
): HttpClient {
    val engine = MockEngine(
        MockEngineConfig().apply {
            addHandler { throw IllegalStateException(message) }
        }
    )
    return HttpClient(engine)
}

private fun MockRequestHandleScope.archiveJsonResponse(
    body: String
): HttpResponseData = respond(
    content = body,
    status = HttpStatusCode.OK,
    headers = Headers.build {
        append(HttpHeaders.ContentType, "application/json")
    }
)

private fun MockRequestHandleScope.threadHtmlResponse(
    body: String
): HttpResponseData = respond(
    content = body,
    status = HttpStatusCode.OK,
    headers = Headers.build {
        append(HttpHeaders.ContentType, "text/html; charset=UTF-8")
        append(HttpHeaders.ContentLength, body.encodeToByteArray().size.toString())
    }
)

private fun boardSummary(): BoardSummary {
    return boardSummary(
        id = "b",
        name = "board",
        url = "https://may.2chan.net/b/futaba.php"
    )
}

private fun boardSummary(
    id: String,
    name: String,
    url: String
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
    threadId: String,
    title: String = "title-$threadId",
    boardId: String = "b",
    boardName: String = "board-old",
    boardUrl: String = "https://may.2chan.net/b/res/$threadId.htm"
): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = boardId,
        title = title,
        titleImageUrl = "thumb-old",
        boardName = boardName,
        boardUrl = boardUrl,
        lastVisitedEpochMillis = 1L,
        replyCount = 1
    )
}

private fun threadPage(
    threadId: String,
    boardTitle: String,
    titleLine: String,
    thumbnailUrl: String,
    replyCount: Int
): ThreadPage {
    val posts = buildList {
        add(
            Post(
                id = "1",
                author = "author",
                subject = null,
                timestamp = "24/01/01(月)00:00:00",
                messageHtml = "$titleLine<br>rest",
                imageUrl = "https://may.2chan.net/src/$threadId.jpg",
                thumbnailUrl = thumbnailUrl
            )
        )
        repeat((replyCount - 1).coerceAtLeast(0)) { index ->
            add(
                Post(
                    id = "${index + 2}",
                    author = "author",
                    subject = null,
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "reply-${index + 2}",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        }
    }
    return ThreadPage(
        threadId = threadId,
        boardTitle = boardTitle,
        expiresAtLabel = null,
        deletedNotice = null,
        posts = posts
    )
}

private suspend fun waitUntil(
    message: String,
    attempts: Int = 20,
    delayMillis: Long = 50L,
    predicate: suspend () -> Boolean
) {
    repeat(attempts) { attempt ->
        if (predicate()) return
        if (attempt < attempts - 1) {
            delay(delayMillis)
        }
    }
    error("Timed out waiting for $message")
}

private class IndexWriteFailingFileSystem(
    private val delegate: InMemoryFileSystem = InMemoryFileSystem()
) : FileSystem by delegate {
    override suspend fun writeString(path: String, content: String): Result<Unit> {
        return if (path.endsWith("/index.json") || path.endsWith("/index.json.tmp") || path.endsWith("/index.json.backup")) {
            Result.failure(IllegalStateException("index write failed for $path"))
        } else {
            delegate.writeString(path, content)
        }
    }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> {
        return if (
            relativePath == "index.json" ||
            relativePath == "index.json.tmp" ||
            relativePath == "index.json.backup"
        ) {
            Result.failure(IllegalStateException("index write failed for $relativePath"))
        } else {
            delegate.writeString(base, relativePath, content)
        }
    }
}
