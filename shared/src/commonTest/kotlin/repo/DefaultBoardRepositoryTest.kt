package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.parser.HtmlParser
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.PersistentCookieStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultBoardRepositoryTest {
    @Test
    fun getCatalog_skipsCookieSetupWhenMatchingCookieAlreadyExists() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "cxyl", value = "ok", domain = "dec.2chan.net", path = "/")
        )
        val api = FakeBoardApi()
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(storage)
        )

        repository.getCatalog(boardUrl, CatalogMode.Catalog)

        assertEquals(0, api.fetchCatalogSetupCalls)
        assertEquals(1, api.fetchCatalogCalls)
    }

    @Test
    fun getCatalog_runsCookieSetupOnlyOnceUntilInvalidated() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi()
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH))
        )

        repository.getCatalog(boardUrl, CatalogMode.Catalog)
        repository.getCatalog(boardUrl, CatalogMode.New)
        repository.invalidateCookies(boardUrl)
        repository.getThread(boardUrl, "123")

        assertEquals(2, api.fetchCatalogSetupCalls)
        assertEquals(2, api.fetchCatalogCalls)
        assertEquals(1, api.fetchThreadCalls)
    }

    @Test
    fun fetchOpImageUrl_usesCacheUntilCleared() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi(threadHeadHtml = "<img src=\"/src/a.jpg\">")
        val parser = FakeHtmlParser(opImageUrl = "https://dec.2chan.net/src/a.jpg")
        val repository = DefaultBoardRepository(api = api, parser = parser)

        val first = repository.fetchOpImageUrl(boardUrl, "123")
        val second = repository.fetchOpImageUrl(boardUrl, "123")
        repository.clearOpImageCache(board = boardUrl, threadId = "123")
        val third = repository.fetchOpImageUrl(boardUrl, "123")

        assertEquals("https://dec.2chan.net/src/a.jpg", first)
        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(2, api.fetchThreadHeadCalls)
        assertEquals(2, parser.extractOpImageUrlCalls)
    }

    @Test
    fun fetchOpImageUrl_cachesMisses() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi(threadHeadHtml = "<html></html>")
        val parser = FakeHtmlParser(opImageUrl = null)
        val repository = DefaultBoardRepository(api = api, parser = parser)

        val first = repository.fetchOpImageUrl(boardUrl, "456")
        val second = repository.fetchOpImageUrl(boardUrl, "456")

        assertNull(first)
        assertNull(second)
        assertEquals(1, api.fetchThreadHeadCalls)
        assertEquals(1, parser.extractOpImageUrlCalls)
    }

    @Test
    fun voteDeleteOperations_forwardArgumentsAfterSingleCookieSetup() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi()
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH))
        )

        repository.voteSaidane(boardUrl, "123", "10")
        repository.requestDeletion(boardUrl, "123", "11", "120")
        repository.deleteByUser(boardUrl, "123", "12", "pass", imageOnly = true)

        assertEquals(1, api.fetchCatalogSetupCalls)
        assertEquals(VoteCall(boardUrl, "123", "10"), api.voteCalls.single())
        assertEquals(DeletionCall(boardUrl, "123", "11", "120"), api.requestDeletionCalls.single())
        assertEquals(
            DeleteByUserCall(
                board = boardUrl,
                threadId = "123",
                postId = "12",
                password = "pass",
                imageOnly = true
            ),
            api.deleteByUserCalls.single()
        )
    }

    @Test
    fun replyAndCreateThread_returnApiValuesAndReuseCookieSetup() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi(
            replyResult = "5001",
            createThreadResult = "9001"
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH))
        )
        val imageBytes = byteArrayOf(1, 2, 3)

        val replyResult = repository.replyToThread(
            board = boardUrl,
            threadId = "321",
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "comment",
            password = "pw",
            imageFile = imageBytes,
            imageFileName = "a.png",
            textOnly = false
        )
        val createResult = repository.createThread(
            board = boardUrl,
            name = "name2",
            email = "",
            subject = "subject2",
            comment = "comment2",
            password = "pw2",
            imageFile = null,
            imageFileName = null,
            textOnly = true
        )

        assertEquals("5001", replyResult)
        assertEquals("9001", createResult)
        assertEquals(1, api.fetchCatalogSetupCalls)
        assertEquals(
            ReplyCall(
                board = boardUrl,
                threadId = "321",
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = "pw",
                imageFile = imageBytes,
                imageFileName = "a.png",
                textOnly = false
            ),
            api.replyCalls.single()
        )
        assertEquals(
            CreateThreadCall(
                board = boardUrl,
                name = "name2",
                email = "",
                subject = "subject2",
                comment = "comment2",
                password = "pw2",
                imageFile = null,
                imageFileName = null,
                textOnly = true
            ),
            api.createThreadCalls.single()
        )
    }

    @Test
    fun postOperationsRetryCookieSetupWhenSetupNeverSucceeds() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi(fetchCatalogSetupError = IllegalStateException("setup failed"))
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH))
        )

        repository.voteSaidane(boardUrl, "1", "10")
        repository.voteSaidane(boardUrl, "1", "11")

        assertEquals(2, api.fetchCatalogSetupCalls)
        assertEquals(2, api.voteCalls.size)
        assertTrue(api.voteCalls.contains(VoteCall(boardUrl, "1", "10")))
        assertTrue(api.voteCalls.contains(VoteCall(boardUrl, "1", "11")))
    }

    @Test
    fun defaultBoardRepositorySupport_handlesCookieStateAndOpImageCache() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val cookieRepository = CookieRepository(storage)
        val initializedBoards = mutableSetOf<String>()
        val boardInitMutex = Mutex()

        assertTrue(
            resolveDefaultBoardRepositoryCookieInitializationState(
                initializedBoards = initializedBoards,
                board = boardUrl,
                cookieRepository = cookieRepository,
                boardInitMutex = boardInitMutex
            )
        )

        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "cxyl", value = "ok", domain = "dec.2chan.net", path = "/")
        )
        assertFalse(
            resolveDefaultBoardRepositoryCookieInitializationState(
                initializedBoards = mutableSetOf(),
                board = boardUrl,
                cookieRepository = cookieRepository,
                boardInitMutex = Mutex()
            )
        )
        assertTrue(hasDefaultBoardRepositoryCookies(cookieRepository, boardUrl))
        assertTrue(isDefaultBoardRepositoryLikelyCookieAuthFailure(NetworkException("forbidden", 403)))

        val cache = createDefaultBoardRepositoryOpImageCache(maxEntries = 2)
        val key1 = DefaultBoardRepositoryOpImageKey(boardUrl, "1")
        val key2 = DefaultBoardRepositoryOpImageKey(boardUrl, "2")
        val key3 = DefaultBoardRepositoryOpImageKey(boardUrl, "3")
        saveDefaultBoardRepositoryOpImageUrlToCache(cache, key1, "a", now = 100L, hitTtlMillis = 50L, missTtlMillis = 10L)
        saveDefaultBoardRepositoryOpImageUrlToCache(cache, key2, null, now = 105L, hitTtlMillis = 50L, missTtlMillis = 10L)

        assertEquals(
            "a",
            resolveDefaultBoardRepositoryCachedOpImageUrl(cache, key1, now = 120L)?.url
        )
        assertNull(resolveDefaultBoardRepositoryCachedOpImageUrl(cache, key2, now = 116L))

        saveDefaultBoardRepositoryOpImageUrlToCache(cache, key3, "c", now = 130L, hitTtlMillis = 50L, missTtlMillis = 10L)
        assertNull(resolveDefaultBoardRepositoryCachedOpImageUrl(cache, key2, now = 130L))
        assertEquals("c", resolveDefaultBoardRepositoryCachedOpImageUrl(cache, key3, now = 131L)?.url)

        assertTrue(shouldClearDefaultBoardRepositoryOpImageCacheEntry(key1, boardUrl, "1"))
        assertFalse(shouldClearDefaultBoardRepositoryOpImageCacheEntry(key1, boardUrl, "9"))

        clearDefaultBoardRepositoryOpImageCache(
            cacheMutex = Mutex(),
            cache = cache,
            board = boardUrl,
            threadId = "1"
        )
        assertNull(resolveDefaultBoardRepositoryCachedOpImageUrl(cache, key1, now = 131L))

        val closeState = DefaultBoardRepositoryCloseState()
        assertTrue(beginDefaultBoardRepositoryClose(Mutex(), closeState))
        assertFalse(beginDefaultBoardRepositoryClose(Mutex(), closeState))
    }

    @Test
    fun defaultBoardRepositoryExecutionSupport_retriesAndWrapsCookieExecution() = runBlocking {
        val initializedBoards = mutableSetOf<String>()
        val boardInitMutex = Mutex()
        var setupCalls = 0
        initializeDefaultBoardRepositoryCookies(
            board = "https://dec.2chan.net/b/",
            logTag = "DefaultBoardRepositoryTest",
            initializedBoards = initializedBoards,
            cookieRepository = null,
            boardInitMutex = boardInitMutex,
            fetchCatalogSetup = { setupCalls += 1 }
        )
        assertEquals(1, setupCalls)
        assertTrue("https://dec.2chan.net/b/" in initializedBoards)

        val storageWithCookie = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        storageWithCookie.addCookie(
            io.ktor.http.Url("https://dec.2chan.net/b/"),
            io.ktor.http.Cookie(name = "cxyl", value = "ok", domain = "dec.2chan.net", path = "/")
        )
        setupCalls = 0
        initializeDefaultBoardRepositoryCookies(
            board = "https://dec.2chan.net/b/",
            logTag = "DefaultBoardRepositoryTest",
            initializedBoards = mutableSetOf(),
            cookieRepository = CookieRepository(storageWithCookie),
            boardInitMutex = Mutex(),
            fetchCatalogSetup = { setupCalls += 1 }
        )
        assertEquals(0, setupCalls)

        var ensureCalls = 0
        var invalidateCalls = 0
        var actionCalls = 0

        val result = withDefaultBoardRepositoryAuthRetry(
            board = "https://dec.2chan.net/b/",
            logTag = "DefaultBoardRepositoryTest",
            ensureCookiesInitialized = { ensureCalls += 1 },
            invalidateCookies = { invalidateCalls += 1 }
        ) {
            actionCalls += 1
            if (actionCalls == 1) {
                throw NetworkException("forbidden", 403)
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, ensureCalls)
        assertEquals(1, invalidateCalls)
        assertEquals(2, actionCalls)

        var commitCalls = 0
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val cookieRepository = CookieRepository(storage)
        val wrapped = runDefaultBoardRepositoryWithInitializedCookies(
            board = "https://dec.2chan.net/b/",
            cookieRepository = cookieRepository,
            ensureCookiesInitialized = {}
        ) {
            commitCalls += 1
            "done"
        }
        assertEquals("done", wrapped)
        assertEquals(1, commitCalls)

        val opImage = fetchDefaultBoardRepositoryOpImageWithPermit(
            threadId = "123",
            semaphoreTimeoutMillis = 100L,
            semaphore = Semaphore(1),
            logTag = "DefaultBoardRepositoryTest"
        ) {
            resolveDefaultBoardRepositoryOpImageUrl(
                threadId = "123",
                logTag = "DefaultBoardRepositoryTest",
                fetchThreadHead = { "<img src=\"/src/a.jpg\">" },
                extractOpImageUrl = { "https://dec.2chan.net/src/a.jpg" }
            )
        }
        assertEquals("https://dec.2chan.net/src/a.jpg", opImage?.url)
    }

    companion object {
        private const val STORAGE_PATH = "private/cookies/default-board-repository-test.json"
    }
}

private class FakeBoardApi(
    private val threadHeadHtml: String = "<img src=\"/src/default.jpg\">",
    private val fetchCatalogSetupError: Exception? = null,
    private val replyResult: String? = null,
    private val createThreadResult: String? = null
) : BoardApi {
    var fetchCatalogSetupCalls = 0
    var fetchCatalogCalls = 0
    var fetchThreadHeadCalls = 0
    var fetchThreadCalls = 0
    val voteCalls = mutableListOf<VoteCall>()
    val requestDeletionCalls = mutableListOf<DeletionCall>()
    val deleteByUserCalls = mutableListOf<DeleteByUserCall>()
    val replyCalls = mutableListOf<ReplyCall>()
    val createThreadCalls = mutableListOf<CreateThreadCall>()

    override suspend fun fetchCatalogSetup(board: String) {
        fetchCatalogSetupCalls += 1
        fetchCatalogSetupError?.let { throw it }
    }

    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        fetchCatalogCalls += 1
        return "<catalog mode='${mode.name}'>$board</catalog>"
    }

    override suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int): String {
        fetchThreadHeadCalls += 1
        return threadHeadHtml
    }

    override suspend fun fetchThread(board: String, threadId: String): String {
        fetchThreadCalls += 1
        return "<thread id='$threadId'/>"
    }

    override suspend fun fetchThreadByUrl(threadUrl: String): String = "<thread url='$threadUrl'/>"
    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        voteCalls += VoteCall(board, threadId, postId)
    }
    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        requestDeletionCalls += DeletionCall(board, threadId, postId, reasonCode)
    }
    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        deleteByUserCalls += DeleteByUserCall(board, threadId, postId, password, imageOnly)
    }
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
    ): String? {
        replyCalls += ReplyCall(
            board = board,
            threadId = threadId,
            name = name,
            email = email,
            subject = subject,
            comment = comment,
            password = password,
            imageFile = imageFile,
            imageFileName = imageFileName,
            textOnly = textOnly
        )
        return replyResult
    }
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
    ): String? {
        createThreadCalls += CreateThreadCall(
            board = board,
            name = name,
            email = email,
            subject = subject,
            comment = comment,
            password = password,
            imageFile = imageFile,
            imageFileName = imageFileName,
            textOnly = textOnly
        )
        return createThreadResult
    }
}

private data class VoteCall(
    val board: String,
    val threadId: String,
    val postId: String
)

private data class DeletionCall(
    val board: String,
    val threadId: String,
    val postId: String,
    val reasonCode: String
)

private data class DeleteByUserCall(
    val board: String,
    val threadId: String,
    val postId: String,
    val password: String,
    val imageOnly: Boolean
)

private data class ReplyCall(
    val board: String,
    val threadId: String,
    val name: String,
    val email: String,
    val subject: String,
    val comment: String,
    val password: String,
    val imageFile: ByteArray?,
    val imageFileName: String?,
    val textOnly: Boolean
)

private data class CreateThreadCall(
    val board: String,
    val name: String,
    val email: String,
    val subject: String,
    val comment: String,
    val password: String,
    val imageFile: ByteArray?,
    val imageFileName: String?,
    val textOnly: Boolean
)

private class FakeHtmlParser(
    private val opImageUrl: String? = "https://dec.2chan.net/src/default.jpg"
) : HtmlParser {
    var extractOpImageUrlCalls = 0

    override suspend fun parseCatalog(html: String, baseUrl: String?): List<CatalogItem> {
        return listOf(
            CatalogItem(
                id = "1",
                threadUrl = "$baseUrl/res/1.htm",
                title = "title",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = 1
            )
        )
    }

    override suspend fun parseThread(html: String): ThreadPage {
        return ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "1",
                    author = "author",
                    subject = null,
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
    }

    override fun extractOpImageUrl(html: String, baseUrl: String?): String? {
        extractOpImageUrlCalls += 1
        return opImageUrl
    }
}
