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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultBoardRepositoryTest {
    @Test
    fun getCatalog_skipsCookieSetupWhenPostingCookieAlreadyExists() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "posttime", value = "1782122070707", domain = ".2chan.net", path = "/")
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
    fun getCatalog_runsCookieSetupWhenOnlyCatalogSettingsCookieExists() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "cxyl", value = "5x60x4x0x0", domain = "dec.2chan.net", path = "/b/")
        )
        val api = FakeBoardApi(
            onFetchCatalogSetup = {
                storage.addCookie(
                    io.ktor.http.Url(boardUrl),
                    io.ktor.http.Cookie(name = "posttime", value = "1782122070707", domain = ".2chan.net", path = "/")
                )
            }
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(storage)
        )

        repository.getCatalog(boardUrl, CatalogMode.Catalog)

        assertEquals(1, api.fetchCatalogSetupCalls)
        assertEquals(1, api.fetchCatalogCalls)
    }

    @Test
    fun getCatalog_runsCookieSetupOnlyOnceUntilInvalidated() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val api = FakeBoardApi(
            onFetchCatalogSetup = {
                storage.addCookie(
                    io.ktor.http.Url(boardUrl),
                    io.ktor.http.Cookie(name = "posttime", value = "1782122070707", domain = ".2chan.net", path = "/")
                )
            }
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(storage)
        )

        repository.getCatalog(boardUrl, CatalogMode.Catalog)
        repository.getCatalog(boardUrl, CatalogMode.New)
        repository.invalidateCookies(boardUrl)
        repository.getThread(boardUrl, "123")

        assertEquals(1, api.fetchCatalogSetupCalls)
        assertEquals(2, api.fetchCatalogCalls)
        assertEquals(1, api.fetchThreadCalls)
    }

    @Test
    fun getCatalog_runsCookieSetupOnlyOnceForConcurrentRequests() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val api = FakeBoardApi(
            fetchCatalogSetupDelayMillis = 50L,
            onFetchCatalogSetup = {
                storage.addCookie(
                    io.ktor.http.Url(boardUrl),
                    io.ktor.http.Cookie(name = "posttime", value = "1782122070707", domain = ".2chan.net", path = "/")
                )
            }
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(storage)
        )

        (1..8)
            .map {
                async {
                    repository.getCatalog(boardUrl, CatalogMode.Catalog)
                }
            }
            .awaitAll()

        assertEquals(1, api.fetchCatalogSetupCalls)
        assertEquals(8, api.fetchCatalogCalls)
    }

    @Test
    fun getCatalog_skipsRecentFailedCookieSetupWhenSetupDoesNotPersistPostingCookie() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi()
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH))
        )

        repository.getCatalog(boardUrl, CatalogMode.Catalog)
        repository.getCatalog(boardUrl, CatalogMode.New)

        assertEquals(1, api.fetchCatalogSetupCalls)
        assertEquals(2, api.fetchCatalogCalls)
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
    fun fetchOpImageUrl_cachesHelperTimeoutsAsMisses() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val api = FakeBoardApi(fetchThreadHeadDelayMillis = 50L)
        val parser = FakeHtmlParser(opImageUrl = "https://dec.2chan.net/src/late.jpg")
        val repository = DefaultBoardRepository(
            api = api,
            parser = parser,
            helperFetchTimeoutMillis = 1L
        )

        val first = repository.fetchOpImageUrl(boardUrl, "789")
        val second = repository.fetchOpImageUrl(boardUrl, "789")

        assertNull(first)
        assertNull(second)
        assertEquals(1, api.fetchThreadHeadCalls)
        assertEquals(0, parser.extractOpImageUrlCalls)
    }

    @Test
    fun resolveCatalogDisplayTitle_usesSmallThreadHeadFirst() = runBlocking {
        val boardUrl = "https://img.2chan.net/b/"
        val api = FakeBoardApi(
            threadHeadHtmlByMaxLines = { maxLines ->
                if (maxLines == 16) {
                    "<blockquote>補完タイトル</blockquote>"
                } else {
                    "<blockquote>大きい取得</blockquote>"
                }
            }
        )
        val repository = DefaultBoardRepository(api = api, parser = FakeHtmlParser())
        val item = CatalogItem(
            id = "123",
            threadUrl = "$boardUrl/res/123.htm",
            title = "10",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 10
        )

        val title = repository.resolveCatalogDisplayTitle(boardUrl, item)

        assertEquals("補完タイトル", title)
        assertEquals(listOf(16), api.fetchThreadHeadMaxLines)
    }

    @Test
    fun resolveCatalogDisplayTitle_fetchesLargerThreadHeadOnlyWhenInitialHeadHasNoTitle() = runBlocking {
        val boardUrl = "https://img.2chan.net/b/"
        val api = FakeBoardApi(
            threadHeadHtmlByMaxLines = { maxLines ->
                if (maxLines == 16) {
                    "<html></html>"
                } else {
                    "<blockquote>フォールバックタイトル</blockquote>"
                }
            }
        )
        val repository = DefaultBoardRepository(api = api, parser = FakeHtmlParser())
        val item = CatalogItem(
            id = "456",
            threadUrl = "$boardUrl/res/456.htm",
            title = "20",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 20
        )

        val title = repository.resolveCatalogDisplayTitle(boardUrl, item)

        assertEquals("フォールバックタイトル", title)
        assertEquals(listOf(16, 65), api.fetchThreadHeadMaxLines)
    }

    @Test
    fun resolveCatalogDisplayTitle_skipsLargerThreadHeadWhenFallbackIsDisabled() = runBlocking {
        val boardUrl = "https://img.2chan.net/b/"
        val api = FakeBoardApi(
            threadHeadHtmlByMaxLines = { maxLines ->
                if (maxLines == 16) {
                    "<html></html>"
                } else {
                    error("unexpected fallback thread head fetch")
                }
            }
        )
        val repository = DefaultBoardRepository(api = api, parser = FakeHtmlParser())
        val item = CatalogItem(
            id = "457",
            threadUrl = "$boardUrl/res/457.htm",
            title = "20",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 20
        )

        val title = repository.resolveCatalogDisplayTitle(
            board = boardUrl,
            item = item,
            allowFallbackHeadScan = false
        )

        assertEquals("20", title)
        assertEquals(listOf(16), api.fetchThreadHeadMaxLines)
    }

    @Test
    fun resolveCatalogDisplayTitle_skipsHeadFetchForNonPlaceholderTitle() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val api = FakeBoardApi(
            threadHeadHtmlByMaxLines = {
                error("unexpected thread head fetch")
            }
        )
        val repository = DefaultBoardRepository(api = api, parser = FakeHtmlParser())
        val item = CatalogItem(
            id = "789",
            threadUrl = "$boardUrl/res/789.htm",
            title = "通常タイトル",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 30
        )

        val title = repository.resolveCatalogDisplayTitle(boardUrl, item)

        assertEquals("通常タイトル", title)
        assertEquals(emptyList(), api.fetchThreadHeadMaxLines)
    }

    @Test
    fun resolveCatalogDisplayTitle_cachesHelperTimeoutsAsMisses() = runBlocking {
        val boardUrl = "https://img.2chan.net/b/"
        val api = FakeBoardApi(
            fetchThreadHeadDelayMillis = 50L,
            threadHeadHtmlByMaxLines = { "<blockquote>遅い補完</blockquote>" }
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            helperFetchTimeoutMillis = 1L
        )
        val item = CatalogItem(
            id = "790",
            threadUrl = "$boardUrl/res/790.htm",
            title = "20",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 20
        )

        val first = repository.resolveCatalogDisplayTitle(boardUrl, item)
        val second = repository.resolveCatalogDisplayTitle(boardUrl, item)

        assertEquals("20", first)
        assertEquals("20", second)
        assertEquals(listOf(16), api.fetchThreadHeadMaxLines)
    }

    @Test
    fun voteDeleteOperations_forwardArgumentsAfterSingleCookieSetup() = runBlocking {
        val boardUrl = "https://dec.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val api = FakeBoardApi(
            onFetchCatalogSetup = {
                storage.addCookie(
                    io.ktor.http.Url(boardUrl),
                    io.ktor.http.Cookie(name = "posttime", value = "1782122070707", domain = ".2chan.net", path = "/")
                )
            }
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(storage)
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
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val api = FakeBoardApi(
            replyResult = "5001",
            createThreadResult = "9001",
            onFetchCatalogSetup = {
                storage.addCookie(
                    io.ktor.http.Url(boardUrl),
                    io.ktor.http.Cookie(name = "posttime", value = "1782122070707", domain = ".2chan.net", path = "/")
                )
            }
        )
        val repository = DefaultBoardRepository(
            api = api,
            parser = FakeHtmlParser(),
            cookieRepository = CookieRepository(storage)
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
        assertTrue(
            resolveDefaultBoardRepositoryCookieInitializationState(
                initializedBoards = mutableSetOf(),
                board = boardUrl,
                cookieRepository = cookieRepository,
                boardInitMutex = Mutex()
            )
        )
        assertFalse(hasDefaultBoardRepositoryCookies(cookieRepository, boardUrl))
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "ptmt", value = "token", domain = ".2chan.net", path = "/")
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

        assertTrue(
            shouldSkipDefaultBoardRepositoryCookieSetup(
                failure = DefaultBoardRepositoryCookieSetupFailure(recordedAtMillis = 1_000L),
                nowMillis = 1_999L,
                negativeCacheTtlMillis = 1_000L
            )
        )
        assertFalse(
            shouldSkipDefaultBoardRepositoryCookieSetup(
                failure = DefaultBoardRepositoryCookieSetupFailure(recordedAtMillis = 1_000L),
                nowMillis = 2_000L,
                negativeCacheTtlMillis = 1_000L
            )
        )
        assertFalse(
            shouldSkipDefaultBoardRepositoryCookieSetup(
                failure = DefaultBoardRepositoryCookieSetupFailure(recordedAtMillis = 2_000L),
                nowMillis = 1_000L,
                negativeCacheTtlMillis = 1_000L
            )
        )

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
            io.ktor.http.Cookie(name = "ptmt", value = "token", domain = ".2chan.net", path = "/")
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
            ensureCookiesInitialized = { _, forceSetup ->
                ensureCalls += 1
                if (ensureCalls == 1) {
                    assertFalse(forceSetup)
                } else {
                    assertTrue(forceSetup)
                }
            },
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
            ensureCookiesInitialized = { _, forceSetup -> assertTrue(forceSetup) }
        ) {
            commitCalls += 1
            "done"
        }
        assertEquals("done", wrapped)
        assertEquals(1, commitCalls)

        val postingStorage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val postingCookieRepository = CookieRepository(postingStorage)
        val boardUrl = "https://dec.2chan.net/b/"
        val postingSuccess = runDefaultBoardRepositoryPostingWithInitializedCookies(
            board = boardUrl,
            cookieRepository = postingCookieRepository,
            ensureCookiesInitialized = { _, forceSetup ->
                assertTrue(forceSetup)
                postingStorage.addCookie(
                    io.ktor.http.Url(boardUrl),
                    io.ktor.http.Cookie(name = "posttime", value = "ok", domain = "dec.2chan.net", path = "/")
                )
            }
        ) {
            "posted"
        }
        assertEquals("posted", postingSuccess)
        assertTrue(postingCookieRepository.hasValidCookieFor(boardUrl, preferredNames = setOf("posttime")))

        val failedPostingStorage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val failedPostingCookieRepository = CookieRepository(failedPostingStorage)
        try {
            runDefaultBoardRepositoryPostingWithInitializedCookies(
                board = boardUrl,
                cookieRepository = failedPostingCookieRepository,
                ensureCookiesInitialized = { _, forceSetup ->
                    assertTrue(forceSetup)
                    failedPostingStorage.addCookie(
                        io.ktor.http.Url(boardUrl),
                        io.ktor.http.Cookie(name = "posttime", value = "staged", domain = "dec.2chan.net", path = "/")
                    )
                }
            ) {
                throw IllegalStateException("post failed")
            }
        } catch (error: IllegalStateException) {
            assertEquals("post failed", error.message)
        }
        assertTrue(failedPostingCookieRepository.hasValidCookieFor(boardUrl, preferredNames = setOf("posttime")))

        val opImage = fetchDefaultBoardRepositoryOpImageWithPermit(
            semaphoreTimeoutMillis = 100L,
            fetchTimeoutMillis = 100L,
            semaphore = Semaphore(1),
        ) {
            resolveDefaultBoardRepositoryOpImageUrl(
                threadId = "123",
                logTag = "DefaultBoardRepositoryTest",
                fetchThreadHead = { "<img src=\"/src/a.jpg\">" },
                extractOpImageUrl = { "https://dec.2chan.net/src/a.jpg" }
            )
        }
        assertEquals("https://dec.2chan.net/src/a.jpg", opImage.url)
        assertFalse(opImage.timedOut)

        val busySemaphore = Semaphore(1)
        busySemaphore.acquire()
        try {
            val timedOutOpImage = fetchDefaultBoardRepositoryOpImageWithPermit(
                semaphoreTimeoutMillis = 1L,
                fetchTimeoutMillis = 100L,
                semaphore = busySemaphore
            ) {
                "unused"
            }
            assertNull(timedOutOpImage.url)
            assertTrue(timedOutOpImage.timedOut)
        } finally {
            busySemaphore.release()
        }
    }

    @Test
    fun defaultBoardRepositoryExecutionSupport_replacesIpRestrictionWithEstimatedWaitFromPosttime() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val cookieRepository = CookieRepository(storage)
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "posttime", value = "1000000000", domain = ".2chan.net", path = "/")
        )

        val enriched = enrichDefaultBoardRepositoryPostingFailureWithPosttimeEstimate(
            board = boardUrl,
            cookieRepository = cookieRepository,
            error = NetworkException("返信に失敗しました: あなたのIPからは投稿できません"),
            nowMillis = 1_000_100_000L
        )

        assertTrue(enriched is NetworkException)
        assertTrue(enriched.message!!.startsWith("あと約58分20秒投稿できません"))
        assertFalse(enriched.message!!.contains("あなたのIPからは投稿できません"))
        assertFalse(enriched.message!!.contains("posttime"))
    }

    @Test
    fun defaultBoardRepositoryExecutionSupport_doesNotAppendEstimateWhenServerProvidesWaitSeconds() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val cookieRepository = CookieRepository(storage)
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "posttime", value = "1000000000", domain = ".2chan.net", path = "/")
        )

        val original = NetworkException("返信に失敗しました: あと120秒投稿できません")
        val enriched = enrichDefaultBoardRepositoryPostingFailureWithPosttimeEstimate(
            board = boardUrl,
            cookieRepository = cookieRepository,
            error = original,
            nowMillis = 1_000_100_000L
        )

        assertTrue(enriched === original)
    }

    @Test
    fun defaultBoardRepositoryExecutionSupport_suggestsCookieDeletionWhenPosttimeIsTooOld() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val cookieRepository = CookieRepository(storage)
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "posttime", value = "1000000000", domain = ".2chan.net", path = "/")
        )
        storage.addCookie(
            io.ktor.http.Url(boardUrl),
            io.ktor.http.Cookie(name = "ptmt", value = "token", domain = ".2chan.net", path = "/")
        )

        val enriched = enrichDefaultBoardRepositoryPostingFailureWithPosttimeEstimate(
            board = boardUrl,
            cookieRepository = cookieRepository,
            error = NetworkException("返信に失敗しました: あなたのIPからは投稿できません"),
            nowMillis = 1_004_000_000L
        )

        assertTrue(enriched is NetworkException)
        assertEquals(
            "投稿用 Cookie が古い可能性があります。Cookie 画面で posttime と ptmt を削除してから、もう一度投稿してください",
            enriched.message
        )
    }

    companion object {
        private const val STORAGE_PATH = "private/cookies/default-board-repository-test.json"
    }
}

private class FakeBoardApi(
    private val threadHeadHtml: String = "<img src=\"/src/default.jpg\">",
    private val threadHeadHtmlByMaxLines: ((Int) -> String)? = null,
    private val fetchThreadHeadDelayMillis: Long = 0L,
    private val fetchCatalogSetupError: Exception? = null,
    private val fetchCatalogSetupDelayMillis: Long = 0L,
    private val replyResult: String? = null,
    private val createThreadResult: String? = null,
    private val onFetchCatalogSetup: suspend () -> Unit = {}
) : BoardApi {
    private val callsMutex = Mutex()
    var fetchCatalogSetupCalls = 0
    var fetchCatalogCalls = 0
    var fetchThreadHeadCalls = 0
    val fetchThreadHeadMaxLines = mutableListOf<Int>()
    var fetchThreadCalls = 0
    val voteCalls = mutableListOf<VoteCall>()
    val requestDeletionCalls = mutableListOf<DeletionCall>()
    val deleteByUserCalls = mutableListOf<DeleteByUserCall>()
    val replyCalls = mutableListOf<ReplyCall>()
    val createThreadCalls = mutableListOf<CreateThreadCall>()

    override suspend fun fetchCatalogSetup(board: String) {
        callsMutex.withLock {
            fetchCatalogSetupCalls += 1
        }
        if (fetchCatalogSetupDelayMillis > 0L) {
            delay(fetchCatalogSetupDelayMillis)
        }
        fetchCatalogSetupError?.let { throw it }
        onFetchCatalogSetup()
    }

    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        callsMutex.withLock {
            fetchCatalogCalls += 1
        }
        return "<catalog mode='${mode.name}'>$board</catalog>"
    }

    override suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int): String {
        fetchThreadHeadCalls += 1
        fetchThreadHeadMaxLines += maxLines
        if (fetchThreadHeadDelayMillis > 0L) {
            delay(fetchThreadHeadDelayMillis)
        }
        return threadHeadHtmlByMaxLines?.invoke(maxLines) ?: threadHeadHtml
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

    override suspend fun parseThread(html: String, baseUrl: String?): ThreadPage {
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
