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
    return BoardSummary(
        id = "b",
        name = "board",
        category = "",
        url = "https://may.2chan.net/b/futaba.php",
        description = ""
    )
}

private fun historyEntry(
    threadId: String,
    title: String = "title-$threadId"
): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = "b",
        title = title,
        titleImageUrl = "thumb-old",
        boardName = "board-old",
        boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
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
