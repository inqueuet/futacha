package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the 2026-10-01 audit items C1-C9 (thread loading). */
class ThreadLoadAuditFixesTest {
    private val board = BoardSummary(
        id = "b",
        name = "二次元裏",
        category = "cat",
        url = "https://may.2chan.net/b/futaba.php",
        description = "desc"
    )

    private fun post(id: String, imageUrl: String? = null) = Post(
        id = id, author = null, subject = id, timestamp = "now",
        messageHtml = "body $id", imageUrl = imageUrl, thumbnailUrl = null
    )

    private fun page(vararg ids: String, imageUrlFor: (String) -> String? = { null }) = ThreadPage(
        threadId = "123", boardTitle = "board", expiresAtLabel = null, deletedNotice = null,
        posts = ids.map { post(it, imageUrlFor(it)) }
    )

    private fun config(override: String? = null) = buildThreadLoadRunnerConfig(
        threadId = "123",
        effectiveBoardUrl = board.url,
        threadUrlOverride = override,
        allowOfflineFallback = true,
        archiveFallbackTimeoutMillis = 1_000L,
        offlineFallbackTimeoutMillis = 1_000L,
        remoteLoadTimeoutMillis = 1_000L
    )

    @Test
    fun archiveFallbackResultIsMarkedAndDoesNotConfirmAlive() = runBlocking {
        val result = performThreadLoadWithOfflineFallback(
            config = config(),
            callbacks = ThreadLoadRunnerCallbacks(
                loadRemoteByUrl = { error("unexpected") },
                loadRemoteByBoard = { _, _ -> throw NetworkException("gone", statusCode = 404) },
                loadArchiveFallback = {
                    ArchiveFallbackOutcome.Success(page("1"), "https://may.inqueuet.com/b/res/123.htm")
                },
                loadOfflineFallback = { null }
            )
        )
        assertTrue(result.fromArchive)
        assertFalse(result.usedOffline)

        val existing = ThreadHistoryEntry(
            threadId = "123", boardId = "b", title = "t", titleImageUrl = "", boardName = "二次元裏",
            boardUrl = board.url, lastVisitedEpochMillis = 1L, replyCount = 1,
            lastConfirmedAliveEpochMillis = 5L, isAutoRefreshDisabled = true
        )
        val outcome = buildThreadInitialLoadUiOutcome(
            page = result.page, history = listOf(existing), threadId = "123", threadTitle = null,
            board = board, overrideThreadUrl = result.nextThreadUrlOverride, usedOffline = false,
            fromArchive = result.fromArchive
        )
        assertEquals(5L, outcome.historyEntry?.lastConfirmedAliveEpochMillis)
        assertEquals(true, outcome.historyEntry?.isAutoRefreshDisabled)
    }

    @Test
    fun loadOfInqueuetArchiveUrlIsMarkedAsArchive() = runBlocking {
        val result = performThreadLoadWithOfflineFallback(
            config = config(override = "https://may.inqueuet.com/b/res/123.htm"),
            callbacks = ThreadLoadRunnerCallbacks(
                loadRemoteByUrl = { ThreadPageContent(page("1")) },
                loadRemoteByBoard = { _, _ -> error("unexpected") },
                loadArchiveFallback = { error("unexpected") },
                loadOfflineFallback = { null }
            )
        )
        assertTrue(result.fromArchive)
    }

    @Test
    fun archiveOverLocalCopyKeepsLocalPostsAndStaysOffline() {
        val local = page("1", "2", "3") { "/data/futacha/autosave/$it.jpg" }
        val archive = ThreadLoadExecutionResult(
            page = page("1", "2") { "https://may.inqueuet.com/b/src/$it.jpg" },
            usedOffline = false,
            nextThreadUrlOverride = "https://may.inqueuet.com/b/res/123.htm",
            fromArchive = true
        )

        val reconciled = reconcileArchiveLoadWithVisiblePage(archive, "123", local, visibleIsLocalCopy = true)

        assertEquals(listOf("1", "2", "3"), reconciled.page.posts.map { it.id })
        assertEquals("/data/futacha/autosave/1.jpg", reconciled.page.posts.first().imageUrl)
        assertTrue(reconciled.usedOffline, "auto-save must keep skipping a page built on a local copy")
    }

    @Test
    fun archiveOverRemotePageFillsOnlyMissingPosts() {
        val visible = page("1", "2", "3")
        val archive = ThreadLoadExecutionResult(
            page = page("1", "3", "4") { "https://may.inqueuet.com/b/src/$it.jpg" },
            usedOffline = false,
            nextThreadUrlOverride = null,
            fromArchive = true
        )

        val reconciled = reconcileArchiveLoadWithVisiblePage(archive, "123", visible, visibleIsLocalCopy = false)

        assertEquals(listOf("1", "2", "3", "4"), reconciled.page.posts.map { it.id })
        assertEquals("https://may.inqueuet.com/b/src/1.jpg", reconciled.page.posts.first().imageUrl)
        assertFalse(reconciled.usedOffline)
        // Nothing to reconcile for a non-archive result.
        val live = archive.copy(fromArchive = false)
        assertEquals(live, reconcileArchiveLoadWithVisiblePage(live, "123", visible, visibleIsLocalCopy = true))
    }

    @Test
    fun remoteLoadTimeoutIsTypedAndFallsBackToOfflineCopy() = runBlocking {
        val offline = page("1")
        val result = performThreadLoadWithOfflineFallback(
            config = config(),
            callbacks = ThreadLoadRunnerCallbacks(
                loadRemoteByUrl = { error("unexpected") },
                loadRemoteByBoard = { _, _ -> delay(5_000L); error("unreachable") },
                loadArchiveFallback = { error("unexpected") },
                loadOfflineFallback = { offline }
            )
        )
        assertTrue(result.usedOffline)
        assertEquals(offline, result.page)

        val timeout = ThreadLoadTimeoutException("Thread load timed out after 75000ms")
        assertTrue(isOfflineFallbackCandidate(timeout))
        assertTrue(isOfflineFallbackCandidate(IllegalStateException("Read timed out")))
        assertEquals("タイムアウト: サーバーが応答しません", buildThreadInitialLoadErrorMessage(timeout, null))
    }

    @Test
    fun effectiveBoardUrlStaysOnTheBoardForArchiveOverrides() {
        assertEquals(board.url, resolveThreadScreenEffectiveBoardUrl(null, board.url))
        assertEquals(board.url, resolveThreadScreenEffectiveBoardUrl("https://may.inqueuet.com/b/res/123.htm", board.url))
        assertEquals(board.url, resolveThreadScreenEffectiveBoardUrl("https://may.2chan.net/b/res/123.htm", board.url))
        assertEquals(
            "https://img.2chan.net/b",
            resolveThreadScreenEffectiveBoardUrl("https://img.2chan.net/b/res/123.htm", board.url)
        )
        assertEquals(
            "https://may.2chan.net/b/res/123.htm",
            resolveFutabaSourceUrlFromArchiveUrl("https://may.inqueuet.com/b/res/123.htm")
        )
    }

    @Test
    fun autoSaveRefusesPagesThatReferenceLocalMedia() = runBlocking {
        var saveCalls = 0
        fun configWith(posts: List<Post>) = buildThreadAutoSaveRunnerConfig(
            threadId = "123", boardId = "b", boardName = "b", boardUrl = board.url, title = "t",
            expiresAtLabel = null, posts = posts, previousTimestampMillis = 0L,
            attemptStartedAtMillis = 10L, completionTimestampMillis = 10L
        )
        val callbacks = ThreadAutoSaveRunnerCallbacks(saveThread = { _, _ ->
            saveCalls++
            Result.failure<SavedThread>(IllegalStateException("not reached in the local case"))
        })

        val local = performThreadAutoSave(configWith(listOf(post("1", "/storage/emulated/0/futacha/1.jpg"))), callbacks)
        assertIs<ThreadAutoSaveLocalCopyException>(local.completionState.failure)
        assertEquals(0, saveCalls)

        performThreadAutoSave(configWith(listOf(post("1", "https://may.2chan.net/b/src/1.jpg"))), callbacks)
        assertEquals(1, saveCalls)
        assertTrue(isLocalThreadMediaReference("content://tree/doc/1.jpg"))
        assertFalse(isLocalThreadMediaReference("https://may.2chan.net/b/src/1.jpg"))
        assertFalse(isLocalThreadMediaReference(null))
    }

    private class LoadHarness(initial: ThreadUiState, offline: Boolean) {
        var uiState: ThreadUiState = initial
        var isShowingOfflineCopy = offline
        var refreshThreadJob: Job? = null
        val offlineFlagHistory = mutableListOf<Boolean>()
        val messages = mutableListOf<String>()
        var initialFailures = 0
    }

    private fun bindings(
        scope: kotlinx.coroutines.CoroutineScope,
        harness: LoadHarness,
        offlinePage: ThreadPage? = null,
        localStalePage: ThreadPage? = null,
        reloadOnOpen: Boolean = true,
        loadRemote: suspend () -> ThreadPageContent
    ) = buildThreadScreenLoadBindings(
        coroutineScope = scope,
        loadRunnerConfig = buildThreadLoadRunnerConfig(
            threadId = "123", effectiveBoardUrl = board.url, threadUrlOverride = null,
            allowOfflineFallback = true, archiveFallbackTimeoutMillis = 1L, offlineFallbackTimeoutMillis = 1L
        ),
        loadRunnerCallbacks = ThreadLoadRunnerCallbacks(
            loadRemoteByUrl = { error("unexpected") },
            loadRemoteByBoard = { _, _ -> loadRemote() },
            loadArchiveFallback = { ArchiveFallbackOutcome.NotFound },
            loadOfflineFallback = { offlinePage },
            loadLocalStalePage = { localStalePage },
            reloadOnOpenEnabled = { reloadOnOpen }
        ),
        history = emptyList(),
        threadId = "123",
        threadTitle = null,
        board = board,
        stateBindings = ThreadScreenLoadStateBindings(
            currentRefreshThreadJob = { harness.refreshThreadJob },
            setRefreshThreadJob = { harness.refreshThreadJob = it },
            currentManualRefreshGeneration = { 0L },
            setManualRefreshGeneration = {},
            setIsRefreshing = {},
            setUiState = { harness.uiState = it },
            setResolvedThreadUrlOverride = {},
            setIsShowingOfflineCopy = {
                harness.isShowingOfflineCopy = it
                harness.offlineFlagHistory += it
            },
            currentUiState = { harness.uiState },
            currentIsShowingOfflineCopy = { harness.isShowingOfflineCopy }
        ),
        uiCallbacks = ThreadScreenLoadUiCallbacks(
            onManualRefreshSuccess = { outcome, _, _ -> outcome.uiState?.let { harness.uiState = it } },
            onManualRefreshFailure = { outcome -> outcome.snackbarMessage?.let(harness.messages::add) },
            onInitialLoadSuccess = { outcome -> outcome.uiState?.let { harness.uiState = it } },
            onInitialLoadFailure = { outcome ->
                harness.initialFailures++
                outcome.uiState?.let { harness.uiState = it }
            }
        )
    )

    @Test
    fun reopenToggleUsesSavedCopyOnlyWhenOffAndManualRefreshStillFetches() = runBlocking {
        for (reload in listOf(false, true)) {
            var requests = 0
            val saved = page("1")
            val newest = page("1", "2")
            val harness = LoadHarness(ThreadUiState.Loading, offline = false)
            val binding = bindings(this, harness, localStalePage = saved, reloadOnOpen = reload) {
                requests++
                ThreadPageContent(newest)
            }
            binding.refreshThread()
            harness.refreshThreadJob?.join()
            assertEquals(if (reload) 1 else 0, requests)
            assertEquals(if (reload) newest else saved, (harness.uiState as ThreadUiState.Success).page)
            assertEquals(!reload, harness.isShowingOfflineCopy)
            binding.startManualRefresh(0, 0)
            harness.refreshThreadJob?.join()
            assertEquals(if (reload) 2 else 1, requests)
            assertEquals(newest, (harness.uiState as ThreadUiState.Success).page)
            // Posting/explicit reload still fetches with a page already visible.
            binding.refreshThread()
            harness.refreshThreadJob?.join()
            assertEquals(if (reload) 3 else 2, requests)
        }
    }

    @Test
    fun openingUncachedThreadFetchesEvenWhenReopenToggleIsOff() = runBlocking {
        for (saved in listOf(null, page())) {
            var requests = 0
            val harness = LoadHarness(ThreadUiState.Loading, offline = false)
            val binding = bindings(this, harness, localStalePage = saved, reloadOnOpen = false) {
                requests++
                ThreadPageContent(page("1"))
            }
            binding.refreshThread()
            harness.refreshThreadJob?.join()
            assertEquals(1, requests)
            assertEquals(page("1"), (harness.uiState as ThreadUiState.Success).page)
        }
    }

    @Test
    fun timedOutManualRefreshKeepsNewerVisibleRepliesAndAutoSaveState() = runBlocking {
        val shown = ThreadUiState.Success(page("1", "2", "3"))
        val harness = LoadHarness(shown, offline = false)
        val binding = bindings(this, harness, offlinePage = page("1")) {
            throw ThreadLoadTimeoutException("timed out")
        }
        binding.startManualRefresh(0, 0)
        harness.refreshThreadJob?.join()
        assertEquals(shown, harness.uiState)
        assertFalse(harness.isShowingOfflineCopy)
        assertEquals(1, harness.messages.size)
    }

    @Test
    fun timedOutReloadAfterAReplyKeepsNewerVisibleRepliesOverTheAutoSaveCopy() = runBlocking {
        val shown = ThreadUiState.Success(page("1", "2", "3"))
        val harness = LoadHarness(shown, offline = false)
        val binding = bindings(this, harness, offlinePage = page("1")) {
            throw ThreadLoadTimeoutException("timed out")
        }
        binding.refreshThread()
        harness.refreshThreadJob?.join()
        assertEquals(shown, harness.uiState)
        assertFalse(harness.isShowingOfflineCopy)
        assertEquals(0, harness.initialFailures)
        assertEquals(1, harness.messages.size)
    }

    @Test
    fun failedManualRefreshKeepsTheOfflineCopyMarked() = runBlocking {
        val localCopy = ThreadUiState.Success(page("1") { "/data/futacha/$it.jpg" })
        val harness = LoadHarness(localCopy, offline = true)
        val loadBindings = bindings(this, harness) { throw NetworkException("down", statusCode = 503) }

        loadBindings.startManualRefresh(0, 0)
        harness.refreshThreadJob?.join()

        assertTrue(harness.isShowingOfflineCopy, "auto-save must not start on the local copy (C2)")
        assertTrue(harness.offlineFlagHistory.none { !it })
        assertEquals(localCopy, harness.uiState)
        assertEquals(1, harness.messages.size)
    }

    @Test
    fun failedReloadWhileAPageIsShownKeepsThePage() = runBlocking {
        val shown = ThreadUiState.Success(page("1", "2"))
        val harness = LoadHarness(shown, offline = false)
        val loadBindings = bindings(this, harness) { throw NetworkException("gone", statusCode = 404) }

        loadBindings.refreshThread()
        harness.refreshThreadJob?.join()

        assertEquals(shown, harness.uiState, "a reload after a reply must not replace the thread with an error (C3)")
        assertEquals(0, harness.initialFailures)
        assertEquals(listOf("更新に失敗しました: スレッドが見つかりません (404)"), harness.messages)

        // Without a page the error screen remains.
        val empty = LoadHarness(ThreadUiState.Loading, offline = false)
        val emptyBindings = bindings(this, empty) { throw NetworkException("gone", statusCode = 404) }
        emptyBindings.refreshThread()
        empty.refreshThreadJob?.join()
        assertIs<ThreadUiState.Error>(empty.uiState)
        assertNull(empty.messages.firstOrNull())
    }

    @Test
    fun provisionalScrollRestoreWaitsForTheSavedPost() {
        val local = ThreadDisplayedPostsLayout(posts = listOf(post("1"), post("2")))
        assertFalse(shouldCompleteThreadInitialScrollRestore("5", local, isProvisionalContent = true))
        assertTrue(shouldCompleteThreadInitialScrollRestore("2", local, isProvisionalContent = true))
        assertTrue(shouldCompleteThreadInitialScrollRestore("5", local, isProvisionalContent = false))
        assertTrue(shouldCompleteThreadInitialScrollRestore(null, local, isProvisionalContent = true))
    }

    @Test
    fun jumpToPostUsesTheDisplayedLayout() {
        val all = listOf(post("1"), post("2"), post("3"), post("4"))
        // Filtered to 1, 3, 4 behind one summary row.
        val layout = ThreadDisplayedPostsLayout(posts = listOf(all[0], all[2], all[3]), itemsBeforePosts = 1)

        assertEquals(2, resolveThreadLazyListIndexForDisplayedPost("3", all, layout))
        assertEquals(2, resolveThreadLazyListIndexForDisplayedPost("2", all, layout), "a hidden post jumps to the next shown one")
        assertNull(resolveThreadLazyListIndexForDisplayedPost("3", all, ThreadDisplayedPostsLayout()))
    }
}
