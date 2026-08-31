package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogWatchAlertRefresherTest {
    @Test
    fun refresh_returnsWatchWordMatchesWithoutAddingThemToHistory() = runBlocking {
        val board = watchBoard()
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(board))
            setHistory(emptyList())
            setWatchWords(listOf("gif"))
        }
        val repository = FakeCatalogWatchRepository().apply {
            catalogs[board.url to CatalogMode.New] = listOf(
                catalogItem(id = "100", title = "ＧＩＦスレ", replyCount = 4),
                catalogItem(id = "101", title = "雑談")
            )
            catalogs[board.url to CatalogMode.Old] = listOf(
                catalogItem(id = "102", title = "gif 古い")
            )
        }

        val result = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(listOf("100", "102"), result.matches.map { it.threadId })
        assertEquals(emptyList(), store.history.first().map { it.threadId })
        assertEquals(CatalogMode.watchSourceModes.toSet(), repository.calls.map { it.second }.toSet())
    }

    @Test
    fun refresh_deduplicatesMatchesWithinRunOnly() = runBlocking {
        val board = watchBoard()
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(board))
            setHistory(emptyList())
            setWatchWords(listOf("cat"))
        }
        val repository = FakeCatalogWatchRepository().apply {
            catalogs[board.url to CatalogMode.New] = listOf(catalogItem(id = "100", title = "cat"))
            catalogs[board.url to CatalogMode.Old] = listOf(catalogItem(id = "100", title = "cat"))
        }

        val result = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(listOf("100"), result.matches.map { it.threadId })
        assertEquals(emptyList(), store.history.first().map { it.threadId })
    }

    @Test
    fun refresh_usesBoardSpecificWatchWordsForEachBoard() = runBlocking {
        val boardA = watchBoard(id = "b", url = "https://may.2chan.net/b/futaba.php")
        val boardB = watchBoard(id = "c", name = "料理", url = "https://may.2chan.net/c/futaba.php")
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(boardA, boardB))
            setHistory(emptyList())
            setWatchWords(listOf("global"))
            setBoardWatchWords(boardA.id, listOf("cat"))
            setBoardWatchWords(boardB.id, listOf("car"))
        }
        val repository = FakeCatalogWatchRepository().apply {
            catalogs[boardA.url to CatalogMode.New] = listOf(
                catalogItem(id = "100", title = "cat thread"),
                catalogItem(id = "101", title = "car should not match board a"),
                catalogItem(id = "102", title = "global should not match overridden board a")
            )
            catalogs[boardB.url to CatalogMode.New] = listOf(
                catalogItem(id = "200", title = "car thread"),
                catalogItem(id = "201", title = "cat should not match board b")
            )
        }

        val result = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(listOf("100", "200"), result.matches.map { it.threadId })
        assertEquals(listOf(boardA.id, boardB.id), result.matches.map { it.boardId })
    }

    @Test
    fun refresh_respectsEmptyBoardOverrideAndCanReturnToGlobalWords() = runBlocking {
        val board = watchBoard()
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(board))
            setHistory(emptyList())
            setWatchWords(listOf("cat"))
            setBoardWatchWords(board.id, emptyList())
        }
        val repository = FakeCatalogWatchRepository().apply {
            catalogs[board.url to CatalogMode.New] = listOf(catalogItem(id = "100", title = "cat thread"))
        }

        val overriddenResult = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(emptyList(), overriddenResult.matches.map { it.threadId })

        store.clearBoardWatchWordsOverride(board.id)
        val inheritedResult = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(listOf("100"), inheritedResult.matches.map { it.threadId })
    }

    @Test
    fun refresh_skipsMatchesAlreadyInHistoryWithoutMutatingHistory() = runBlocking {
        val board = watchBoard()
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(board))
            setHistory(
                listOf(
                    com.valoser.futacha.shared.model.ThreadHistoryEntry(
                        threadId = "100",
                        boardId = board.id,
                        title = "existing",
                        titleImageUrl = "",
                        boardName = board.name,
                        boardUrl = board.url,
                        lastVisitedEpochMillis = 1L,
                        replyCount = 1
                    )
                )
            )
            setWatchWords(listOf("cat"))
        }
        val repository = FakeCatalogWatchRepository().apply {
            catalogs[board.url to CatalogMode.New] = listOf(catalogItem(id = "100", title = "cat"))
            catalogs[board.url to CatalogMode.Old] = listOf(catalogItem(id = "101", title = "cat"))
        }

        val result = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(listOf("101"), result.matches.map { it.threadId })
        assertEquals(listOf("100"), store.history.first().map { it.threadId })
    }

    @Test
    fun refreshCapsPathologicalMatchBursts() = runBlocking {
        val board = watchBoard()
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(board))
            setHistory(emptyList())
            setWatchWords(listOf("watch"))
        }
        val repository = FakeCatalogWatchRepository().apply {
            catalogs[board.url to CatalogMode.New] =
                List(MAX_WATCH_ALERT_MATCHES_PER_RUN + 100) { index ->
                    catalogItem(id = (index + 1).toString(), title = "watch $index")
                }
        }

        val result = CatalogWatchAlertRefresher(
            stateStore = store,
            repository = repository,
            dispatcher = Dispatchers.Default
        ).refresh()

        assertEquals(MAX_WATCH_ALERT_MATCHES_PER_RUN, result.matches.size)
    }

    @Test
    fun refreshDoesNotSpinWhenConcurrencyIsPathologicallyLarge() = runBlocking {
        val board = watchBoard()
        val store = AppStateStore(FakePlatformStateStorage()).apply {
            setBoards(listOf(board))
            setHistory(emptyList())
            setWatchWords(listOf("watch"))
        }

        val result = withTimeout(2_000L) {
            CatalogWatchAlertRefresher(
                stateStore = store,
                repository = FakeCatalogWatchRepository(),
                dispatcher = Dispatchers.Default,
                maxConcurrency = Int.MAX_VALUE
            ).refresh()
        }

        assertEquals(0, result.matches.size)
    }
}

private fun watchBoard(
    id: String = "b",
    name: String = "二次元裏",
    url: String = "https://may.2chan.net/b/futaba.php"
): BoardSummary = BoardSummary(
    id = id,
    name = name,
    category = "test",
    url = url,
    description = "test"
)

private fun catalogItem(
    id: String,
    title: String,
    replyCount: Int = 0
): CatalogItem = CatalogItem(
    id = id,
    threadUrl = "https://may.2chan.net/b/res/$id.htm",
    title = title,
    thumbnailUrl = null,
    fullImageUrl = null,
    replyCount = replyCount
)

private class FakeCatalogWatchRepository : BoardRepository {
    val catalogs = mutableMapOf<Pair<String, CatalogMode>, List<CatalogItem>>()
    val calls = mutableListOf<Pair<String, CatalogMode>>()
    private val callsMutex = Mutex()

    override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
        callsMutex.withLock {
            calls += board to mode
        }
        return catalogs[board to mode].orEmpty()
    }

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? = null
    override suspend fun getThread(board: String, threadId: String): ThreadPage = error("not used")
    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = error("not used")
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
    override fun closeAsync(): kotlinx.coroutines.Job = kotlinx.coroutines.Job().also { it.complete() }
    override suspend fun clearOpImageCache(board: String?, threadId: String?) = Unit
    override suspend fun invalidateCookies(board: String) = Unit
}
