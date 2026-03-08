package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.parser.HtmlParser
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.network.PersistentCookieStorage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    companion object {
        private const val STORAGE_PATH = "private/cookies/default-board-repository-test.json"
    }
}

private class FakeBoardApi(
    private val threadHeadHtml: String = "<img src=\"/src/default.jpg\">"
) : BoardApi {
    var fetchCatalogSetupCalls = 0
    var fetchCatalogCalls = 0
    var fetchThreadHeadCalls = 0
    var fetchThreadCalls = 0

    override suspend fun fetchCatalogSetup(board: String) {
        fetchCatalogSetupCalls += 1
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
}

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
