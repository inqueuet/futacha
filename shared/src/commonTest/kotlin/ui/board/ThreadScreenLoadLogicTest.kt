package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadScreenLoadLogicTest {
    @Test
    fun performThreadLoadWithOfflineFallback_usesArchiveAndOfflinePaths() = runBlocking {
        val remoteFailure = NetworkException("HTTP error", statusCode = 404)
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "may/b",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = emptyList()
        )
        assertEquals(
            ThreadLoadExecutionResult(
                page = page,
                usedOffline = false,
                nextThreadUrlOverride = "https://may.2chan.net/b/res/123.htm"
            ),
            performThreadLoadWithOfflineFallback(
                config = buildThreadLoadRunnerConfig(
                    threadId = "123",
                    effectiveBoardUrl = "https://may.2chan.net/b",
                    threadUrlOverride = null,
                    allowOfflineFallback = true,
                    archiveFallbackTimeoutMillis = 100L,
                    offlineFallbackTimeoutMillis = 100L
                ),
                callbacks = ThreadLoadRunnerCallbacks(
                    loadRemoteByUrl = { error("unused") },
                    loadRemoteByBoard = { _, _ -> throw remoteFailure },
                    loadArchiveFallback = {
                        ArchiveFallbackOutcome.Success(
                            page = page,
                            threadUrl = "https://may.2chan.net/b/res/123.htm"
                        )
                    },
                    loadOfflineFallback = { error("unused") }
                )
            )
        )

        var offlineMissCalled = false
        assertEquals(
            ThreadLoadExecutionResult(
                page = page,
                usedOffline = true,
                nextThreadUrlOverride = "https://may.2chan.net/b/res/123.htm"
            ),
            performThreadLoadWithOfflineFallback(
                config = buildThreadLoadRunnerConfig(
                    threadId = "123",
                    effectiveBoardUrl = "https://may.2chan.net/b",
                    threadUrlOverride = "https://may.2chan.net/b/res/123.htm",
                    allowOfflineFallback = true,
                    archiveFallbackTimeoutMillis = 100L,
                    offlineFallbackTimeoutMillis = 100L
                ),
                callbacks = ThreadLoadRunnerCallbacks(
                    loadRemoteByUrl = { throw IllegalStateException("network timeout") },
                    loadRemoteByBoard = { _, _ -> error("unused") },
                    loadArchiveFallback = { ArchiveFallbackOutcome.NoMatch },
                    loadOfflineFallback = { page },
                    onOfflineFallbackMiss = { offlineMissCalled = true }
                )
            )
        )
        assertFalse(offlineMissCalled)
    }

    @Test
    fun runThreadReadAloudSession_runsSegmentsAndHandlesUserCancellation() = runBlocking {
        val segments = listOf(
            ReadAloudSegment(postIndex = 1, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 2, postId = "20", body = "b")
        )
        val events = mutableListOf<String>()

        val successResult = runThreadReadAloudSession(
            startIndex = 0,
            segments = segments,
            isRunnerActive = { true },
            wasCancelledByUser = { false },
            callbacks = ThreadReadAloudRunnerCallbacks(
                onSegmentStart = { segment, index ->
                    events += "start:$index:${segment.postId}"
                },
                scrollToSegment = { segment ->
                    events += "scroll:${segment.postId}"
                },
                speakSegment = { segment ->
                    events += "speak:${segment.postId}"
                }
            )
        )

        assertEquals(
            listOf(
                "start:0:10",
                "scroll:10",
                "speak:10",
                "start:1:20",
                "scroll:20",
                "speak:20"
            ),
            events
        )
        assertEquals(
            ThreadReadAloudRunResult(
                completedNormally = true,
                nextIndex = 0
            ),
            successResult
        )

        var failureMessage: String? = null
        val cancelledResult = runThreadReadAloudSession(
            startIndex = 1,
            segments = segments,
            isRunnerActive = { true },
            wasCancelledByUser = { true },
            callbacks = ThreadReadAloudRunnerCallbacks(
                speakSegment = {
                    throw kotlinx.coroutines.CancellationException("cancelled")
                },
                onFailure = { error ->
                    failureMessage = error.message
                }
            )
        )

        assertEquals(
            ThreadReadAloudRunResult(
                completedNormally = false,
                nextIndex = 1
            ),
            cancelledResult
        )
        assertEquals(null, failureMessage)
    }

    @Test
    fun readAloudRunnerHelpers_buildCallbacksAndFinalState() = runBlocking {
        val events = mutableListOf<String>()
        val segment = ReadAloudSegment(postIndex = 3, postId = "30", body = "hello")
        val callbacks = buildThreadReadAloudRunnerCallbacks(
            onSegmentStart = { started, index ->
                events += "start:$index:${started.postId}"
            },
            scrollToPostIndex = { postIndex ->
                events += "scroll:$postIndex"
            },
            speakText = { text ->
                events += "speak:$text"
            },
            onFailure = { error ->
                events += "failure:${error.message}"
            }
        )

        callbacks.onSegmentStart(segment, 2)
        callbacks.scrollToSegment(segment)
        callbacks.speakSegment(segment)
        callbacks.onFailure(IllegalStateException("boom"))

        assertEquals(
            listOf(
                "start:2:30",
                "scroll:3",
                "speak:hello",
                "failure:boom"
            ),
            events
        )
        assertEquals(
            ThreadReadAloudFinalState(
                status = ReadAloudStatus.Idle,
                message = buildReadAloudCompletedMessage()
            ),
            resolveThreadReadAloudFinalState(
                completedNormally = true,
                currentStatus = ReadAloudStatus.Speaking(segment)
            )
        )
        assertEquals(
            ThreadReadAloudFinalState(
                status = ReadAloudStatus.Paused(segment),
                message = null
            ),
            resolveThreadReadAloudFinalState(
                completedNormally = false,
                currentStatus = ReadAloudStatus.Paused(segment)
            )
        )
        assertEquals(
            ThreadReadAloudFinalState(
                status = ReadAloudStatus.Idle,
                message = null
            ),
            resolveThreadReadAloudFinalState(
                completedNormally = false,
                currentStatus = ReadAloudStatus.Speaking(segment)
            )
        )
    }

    @Test
    fun buildPosterIdLabels_countsDuplicatePosterIds() {
        val posts = listOf(
            post(id = "1", posterId = "ID:abc123"),
            post(id = "2", posterId = "abc123"),
            post(id = "3", posterId = "ID:def456"),
            post(id = "4", posterId = " abc123 ")
        )

        val labels = buildPosterIdLabels(posts)

        assertEquals("ID:abc123(1/3)", labels.getValue("1").text)
        assertFalse(labels.getValue("1").highlight)
        assertEquals("ID:abc123(2/3)", labels.getValue("2").text)
        assertTrue(labels.getValue("2").highlight)
        assertEquals("ID:def456(1/1)", labels.getValue("3").text)
        assertFalse(labels.getValue("3").highlight)
        assertEquals("ID:abc123(3/3)", labels.getValue("4").text)
        assertTrue(labels.getValue("4").highlight)
    }

    @Test
    fun buildReadAloudSegments_skipsDeletedQuotesUrlsAndDeletionNotices() {
        val posts = listOf(
            post(
                id = "1",
                messageHtml = "通常文<br>https://example.com/test"
            ),
            post(
                id = "2",
                messageHtml = "&gt;&gt;1<br>引用のみ"
            ),
            post(
                id = "3",
                messageHtml = "書き込みをした人によって削除されました"
            ),
            post(
                id = "4",
                messageHtml = "通常文2",
                isDeleted = true
            )
        )

        val segments = buildReadAloudSegments(posts)

        assertEquals(2, segments.size)
        assertEquals("1", segments[0].postId)
        assertEquals("通常文", segments[0].body)
        assertEquals("2", segments[1].postId)
        assertEquals("引用のみ", segments[1].body)
    }

    @Test
    fun findFirstVisibleReadAloudSegmentIndex_returnsNearestVisibleSegment() {
        val segments = listOf(
            ReadAloudSegment(postIndex = 1, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 3, postId = "30", body = "b"),
            ReadAloudSegment(postIndex = 7, postId = "70", body = "c")
        )

        assertEquals(0, findFirstVisibleReadAloudSegmentIndex(segments, firstVisibleItemIndex = 0))
        assertEquals(1, findFirstVisibleReadAloudSegmentIndex(segments, firstVisibleItemIndex = 2))
        assertEquals(2, findFirstVisibleReadAloudSegmentIndex(segments, firstVisibleItemIndex = 5))
        assertEquals(-1, findFirstVisibleReadAloudSegmentIndex(segments, firstVisibleItemIndex = 9))
    }

    @Test
    fun normalizeBoardUrlForOfflineLookup_normalizesBaseUrl() {
        assertEquals(
            "https://may.2chan.net/b",
            normalizeBoardUrlForOfflineLookup("HTTPS://MAY.2CHAN.NET/b/futaba.php?mode=cat")
        )
        assertEquals(
            "https://may.2chan.net/b",
            normalizeBoardUrlForOfflineLookup("https://may.2chan.net/b/")
        )
    }

    @Test
    fun matchesBoardForOfflineFallback_checksNormalizedBoardKeys() {
        val metadata = SavedThreadMetadata(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            title = "title",
            storageId = "b_123",
            savedAt = 1L,
            expiresAtLabel = null,
            posts = listOf(
                SavedPost(
                    id = "1",
                    order = 1,
                    author = null,
                    subject = null,
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    originalImageUrl = null,
                    localImagePath = null,
                    originalVideoUrl = null,
                    localVideoPath = null,
                    originalThumbnailUrl = null,
                    localThumbnailPath = null
                )
            ),
            totalSize = 1L
        )

        assertTrue(
            metadata.matchesBoardForOfflineFallback(setOf("https://may.2chan.net/b"))
        )
        assertFalse(
            metadata.matchesBoardForOfflineFallback(setOf("https://may.2chan.net/img"))
        )
    }

    @Test
    fun buildOfflineBoardIdCandidates_prefersKnownIdsThenDerivedIdsThenNull() {
        assertEquals(
            listOf("b", "hist", "img", null),
            buildOfflineBoardIdCandidates(
                boardId = "b",
                initialHistoryBoardId = "hist",
                effectiveBoardUrl = "https://dec.2chan.net/img/futaba.php",
                initialHistoryBoardUrl = "https://dec.2chan.net/img/res/123.htm"
            )
        )
    }

    @Test
    fun buildExpectedOfflineBoardKeys_normalizesDistinctUrls() {
        assertEquals(
            setOf("https://may.2chan.net/b", "https://dec.2chan.net/img"),
            buildExpectedOfflineBoardKeys(
                effectiveBoardUrl = "https://may.2chan.net/b/futaba.php",
                boardUrl = "https://MAY.2CHAN.NET/b/",
                initialHistoryBoardUrl = "https://dec.2chan.net/img/res/123.htm?foo=1"
            )
        )
    }

    @Test
    fun offlineLookupAndSourceHelpers_buildExpectedState() {
        val lookupContext = buildOfflineThreadLookupContext(
            boardId = "b",
            initialHistoryBoardId = "hist",
            effectiveBoardUrl = "https://may.2chan.net/b/futaba.php",
            boardUrl = "https://MAY.2CHAN.NET/b/",
            initialHistoryBoardUrl = "https://dec.2chan.net/img/res/123.htm?foo=1"
        )

        assertEquals(listOf("b", "hist", "img", null), lookupContext.boardIdCandidates)
        assertEquals(
            setOf("https://may.2chan.net/b", "https://dec.2chan.net/img"),
            lookupContext.expectedBoardKeys
        )

        val sources = buildThreadOfflineSources(
            autoSaveRepository = null,
            manualSaveRepository = null,
            legacyManualSaveRepository = null,
            manualSaveDirectory = "/manual",
            manualSaveLocation = SaveLocation.Path("/manual")
        )
        assertEquals(AUTO_SAVE_DIRECTORY, sources[0].baseDirectory)
        assertEquals("/manual", sources[1].baseDirectory)
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, sources[2].baseDirectory)
        assertFalse(hasOfflineThreadSources(sources))
        assertEquals(
            "Skip offline metadata due to board mismatch: threadId=123 boardUrl=https://may.2chan.net/b/futaba.php",
            buildOfflineMetadataBoardMismatchLogMessage(
                threadId = "123",
                boardUrl = "https://may.2chan.net/b/futaba.php"
            )
        )
        assertEquals(
            "Offline metadata not found for threadId=123 boardIdCandidates=[b, hist, null]",
            buildOfflineMetadataNotFoundLogMessage(
                threadId = "123",
                boardIdCandidates = listOf("b", "hist", null)
            )
        )
    }

    @Test
    fun threadScrollRestoreHelpers_clampAndFormatFailureMessage() {
        assertEquals(
            ThreadScrollRestoreTarget(index = 0, offset = 4),
            resolveThreadScrollRestoreTarget(
                savedIndex = -2,
                savedOffset = 4,
                totalItems = 3
            )
        )
        assertEquals(
            ThreadScrollRestoreTarget(index = 2, offset = 0),
            resolveThreadScrollRestoreTarget(
                savedIndex = 9,
                savedOffset = -5,
                totalItems = 3
            )
        )
        assertEquals(
            null,
            resolveThreadScrollRestoreTarget(
                savedIndex = 0,
                savedOffset = 0,
                totalItems = 0
            )
        )
        assertEquals(
            "Failed to restore scroll position index=2 offset=10: boom",
            buildThreadScrollRestoreFailureMessage(
                index = 2,
                offset = 10,
                error = IllegalStateException("boom")
            )
        )
    }

    @Test
    fun shouldUseOfflineMetadataCandidate_allowsExplicitBoardId_andValidFallbackBoardKeyOnly() {
        val metadata = SavedThreadMetadata(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            title = "title",
            storageId = "b_123",
            savedAt = 1L,
            expiresAtLabel = null,
            posts = listOf(
                SavedPost(
                    id = "1",
                    order = 1,
                    author = null,
                    subject = null,
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    originalImageUrl = null,
                    localImagePath = null,
                    originalVideoUrl = null,
                    localVideoPath = null,
                    originalThumbnailUrl = null,
                    localThumbnailPath = null
                )
            ),
            totalSize = 1L
        )

        assertTrue(
            shouldUseOfflineMetadataCandidate(
                candidateBoardId = "b",
                metadata = metadata,
                expectedBoardKeys = setOf("https://other.invalid")
            )
        )
        assertTrue(
            shouldUseOfflineMetadataCandidate(
                candidateBoardId = null,
                metadata = metadata,
                expectedBoardKeys = setOf("https://may.2chan.net/b")
            )
        )
        assertFalse(
            shouldUseOfflineMetadataCandidate(
                candidateBoardId = null,
                metadata = metadata,
                expectedBoardKeys = setOf("https://may.2chan.net/img")
            )
        )
    }

    @Test
    fun buildThreadSearchTargets_includesSubjectAuthorPosterIdAndBody() {
        val targets = buildThreadSearchTargets(
            listOf(
                Post(
                    id = "10",
                    author = "Alice",
                    subject = "件名",
                    timestamp = "24/01/01(月)00:00:00",
                    posterId = "ID:abc",
                    messageHtml = "本文<br>二行目",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )

        assertEquals(1, targets.size)
        assertEquals("10", targets[0].postId)
        assertEquals("本文\n二行目", targets[0].messagePlainText)
        assertTrue(targets[0].searchableText.contains("件名"))
        assertTrue(targets[0].searchableText.contains("alice"))
        assertTrue(targets[0].searchableText.contains("id:abc"))
        assertTrue(targets[0].searchableText.contains("本文"))
    }

    @Test
    fun buildThreadSearchMatches_returnsPostMatchesAndHighlightRanges() {
        val targets = buildThreadSearchTargets(
            listOf(
                post(id = "1", messageHtml = "猫と犬"),
                post(id = "2", messageHtml = "犬だけ"),
                post(id = "3", messageHtml = "猫が二回 猫")
            )
        )

        val matches = buildThreadSearchMatches(targets, "猫")

        assertEquals(listOf("1", "3"), matches.map { it.postId })
        assertEquals(listOf(0..0), matches[0].highlightRanges)
        assertEquals(listOf(0..0, 5..5), matches[1].highlightRanges)
    }

    @Test
    fun quotePreviewHelpers_resolveTargetsAndRespectScrollingState() {
        val posts = listOf(
            post(id = "1", messageHtml = "one"),
            post(id = "2", messageHtml = "two")
        )
        val postIndex = posts.associateBy { it.id }
        val posterIdLabels = mapOf(
            "1" to PosterIdLabel(text = "ID:abc(1/1)", highlight = false)
        )

        assertEquals(
            listOf(posts[1], posts[0]),
            resolveQuotePreviewTargets(listOf("2", "1", "missing"), postIndex)
        )

        val visibleState = resolveQuotePreviewState(
            isScrolling = false,
            quoteText = ">>1",
            targetPosts = listOf(posts[0]),
            posterIdLabels = posterIdLabels
        )
        assertEquals(">>1", visibleState?.quoteText)
        assertEquals(listOf(posts[0]), visibleState?.targetPosts)
        assertEquals(posterIdLabels, visibleState?.posterIdLabels)

        assertEquals(
            null,
            resolveQuotePreviewState(
                isScrolling = true,
                quoteText = ">>1",
                targetPosts = listOf(posts[0]),
                posterIdLabels = posterIdLabels
            )
        )
        assertEquals(
            null,
            resolveQuotePreviewState(
                isScrolling = false,
                quoteText = ">>1",
                targetPosts = emptyList(),
                posterIdLabels = posterIdLabels
            )
        )
        assertFalse(canHandleThreadPostLongPress(visibleState))
        assertTrue(canHandleThreadPostLongPress(null))
    }

    @Test
    fun nextAndPreviousThreadSearchResultIndex_wrapAround() {
        assertEquals(0, nextThreadSearchResultIndex(currentIndex = 2, matchCount = 3))
        assertEquals(2, previousThreadSearchResultIndex(currentIndex = 0, matchCount = 3))
        assertEquals(0, nextThreadSearchResultIndex(currentIndex = 0, matchCount = 0))
        assertEquals(0, previousThreadSearchResultIndex(currentIndex = 0, matchCount = 0))
        assertEquals(1, normalizeThreadSearchResultIndex(currentIndex = 1, matchCount = 3))
        assertEquals(0, normalizeThreadSearchResultIndex(currentIndex = 3, matchCount = 3))
        assertEquals(0, normalizeThreadSearchResultIndex(currentIndex = 0, matchCount = 0))
    }

    @Test
    fun threadSearchNavigationHelpers_resolveIndexAndScrollTarget() {
        val matches = listOf(
            ThreadSearchMatch(postId = "1", postIndex = 10, highlightRanges = emptyList()),
            ThreadSearchMatch(postId = "2", postIndex = 20, highlightRanges = emptyList()),
            ThreadSearchMatch(postId = "3", postIndex = 30, highlightRanges = emptyList())
        )

        assertEquals(
            ThreadSearchNavigationState(
                nextIndex = 1,
                targetPostIndex = 20,
                shouldScroll = true
            ),
            focusThreadSearchMatch(currentIndex = 1, matches = matches)
        )
        assertEquals(
            ThreadSearchNavigationState(
                nextIndex = 0,
                targetPostIndex = 10,
                shouldScroll = true
            ),
            moveToNextThreadSearchMatch(currentIndex = 2, matches = matches)
        )
        assertEquals(
            ThreadSearchNavigationState(
                nextIndex = 2,
                targetPostIndex = 30,
                shouldScroll = true
            ),
            moveToPreviousThreadSearchMatch(currentIndex = 0, matches = matches)
        )
        assertEquals(
            ThreadSearchNavigationState(
                nextIndex = 0,
                targetPostIndex = null,
                shouldScroll = false
            ),
            moveToNextThreadSearchMatch(currentIndex = 0, matches = emptyList())
        )
    }

    @Test
    fun threadPostActionHelpers_resolveSaidaneDelAndNgRegistrationState() {
        assertEquals(
            ThreadSaidaneActionState(
                shouldProceed = false,
                blockedMessage = "自分のレスにはそうだねできません",
                updatedLabel = null
            ),
            resolveThreadSaidaneActionState(
                isSelfPost = true,
                currentLabel = "そうだねx2"
            )
        )
        val saidaneState = resolveThreadSaidaneActionState(
            isSelfPost = false,
            currentLabel = "そうだねx2"
        )
        assertEquals(true, saidaneState.shouldProceed)
        assertEquals("そうだねを送信しました", saidaneState.successMessage)
        assertEquals("そうだねに失敗しました", saidaneState.failurePrefix)
        assertEquals(incrementSaidaneLabel("そうだねx2"), saidaneState.updatedLabel)

        assertEquals(
            ThreadDelRequestActionState(),
            resolveThreadDelRequestActionState()
        )

        val postWithId = Post(
            id = "1",
            author = "name",
            subject = null,
            timestamp = "24/01/01(月)00:00:00 ID:abc123",
            posterId = "ID:abc123",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        assertEquals(
            ThreadNgRegistrationActionState(
                prefillValue = "abc123",
                message = null,
                shouldShowNgManagement = true
            ),
            resolveThreadNgRegistrationActionState(postWithId)
        )
        val postWithoutId = postWithId.copy(posterId = null)
        assertEquals(
            ThreadNgRegistrationActionState(
                prefillValue = null,
                message = "IDが見つかりませんでした",
                shouldShowNgManagement = true
            ),
            resolveThreadNgRegistrationActionState(postWithoutId)
        )
    }

    @Test
    fun threadPostDialogHelpers_buildOpenDismissAndConfirmState() {
        val post = Post(
            id = "1",
            author = "name",
            subject = null,
            timestamp = "24/01/01(月)00:00:00",
            posterId = null,
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )

        assertEquals(
            ThreadPostActionSelectionState(
                targetPost = post,
                isActionSheetVisible = true
            ),
            openThreadPostActionSheet(post)
        )
        assertEquals(
            ThreadPostActionSelectionState(
                targetPost = null,
                isActionSheetVisible = false
            ),
            dismissThreadPostActionSheet()
        )
        assertEquals(
            ThreadDeleteDialogState(
                targetPost = post,
                password = "stored",
                imageOnly = false
            ),
            openThreadDeleteDialog(post, "stored")
        )
        assertEquals(
            ThreadDeleteDialogState(
                targetPost = null,
                password = "",
                imageOnly = false
            ),
            dismissThreadDeleteDialog()
        )
        assertEquals(
            ThreadDeleteConfirmState(
                normalizedPassword = "pass",
                imageOnly = true,
                nextDialogState = dismissThreadDeleteDialog()
            ),
            confirmThreadDeleteDialog("  pass  ", imageOnly = true)
        )
        assertEquals(
            ThreadQuoteSelectionState(targetPost = post),
            openThreadQuoteSelection(post)
        )
        assertEquals(
            ThreadQuoteSelectionState(targetPost = null),
            dismissThreadQuoteSelection()
        )
    }

    @Test
    fun threadLoadStateHelpers_buildSuccessAndFailureState() {
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "may/b",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "1",
                    author = "name",
                    subject = "subject",
                    timestamp = "24/01/01(月)00:00:00",
                    posterId = null,
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        val board = BoardSummary(
            id = "may-b",
            name = "may/b",
            category = "cat",
            url = "https://may.2chan.net/b/futaba.php",
            description = "desc",
            pinned = false
        )
        val history = listOf(
            ThreadHistoryEntry(
                threadId = "123",
                boardId = "may-b",
                title = "old",
                titleImageUrl = "",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b/futaba.php",
                lastVisitedEpochMillis = 1L,
                replyCount = 1
            )
        )

        val successState = buildThreadLoadSuccessState(
            page = page,
            history = history,
            threadId = "123",
            threadTitle = "thread title",
            board = board,
            overrideThreadUrl = null
        )

        assertEquals(ThreadUiState.Success(page), successState.uiState)
        assertEquals("123", successState.historyEntry.threadId)
        assertEquals("may-b", successState.historyEntry.boardId)
        assertEquals("body", successState.historyEntry.title)

        val initialOutcome = buildThreadInitialLoadUiOutcome(
            page = page,
            history = history,
            threadId = "123",
            threadTitle = "thread title",
            board = board,
            overrideThreadUrl = null,
            usedOffline = true
        )
        assertEquals(ThreadUiState.Success(page), initialOutcome.uiState)
        assertEquals("123", initialOutcome.historyEntry?.threadId)
        assertEquals("may-b", initialOutcome.historyEntry?.boardId)
        assertEquals("body", initialOutcome.historyEntry?.title)
        assertEquals("ローカルコピーを表示しています", initialOutcome.snackbarMessage)

        val refreshOutcome = buildThreadManualRefreshUiOutcome(
            page = page,
            history = history,
            threadId = "123",
            threadTitle = "thread title",
            board = board,
            overrideThreadUrl = null,
            usedOffline = false
        )
        assertEquals(ThreadUiState.Success(page), refreshOutcome.uiState)
        assertEquals("123", refreshOutcome.historyEntry?.threadId)
        assertEquals("may-b", refreshOutcome.historyEntry?.boardId)
        assertEquals("body", refreshOutcome.historyEntry?.title)
        assertEquals("スレッドを更新しました", refreshOutcome.snackbarMessage)

        assertEquals(
            ThreadUiState.Error("スレッドが見つかりません (404)"),
            buildThreadInitialLoadFailureState(
                error = IllegalStateException("x"),
                statusCode = 404
            )
        )
        assertEquals(
            ThreadLoadUiOutcome(
                uiState = ThreadUiState.Error("スレッドが見つかりません (404)"),
                snackbarMessage = "スレッドが見つかりません (404)"
            ),
            buildThreadInitialLoadFailureUiOutcome(
                error = IllegalStateException("x"),
                statusCode = 404
            )
        )
        assertEquals(
            ThreadLoadUiOutcome(
                snackbarMessage = "更新に失敗しました: スレッドは削除済みです (410)"
            ),
            buildThreadManualRefreshFailureUiOutcome(
                error = IllegalStateException("x"),
                statusCode = 410
            )
        )
    }

    @Test
    fun threadRefreshAvailability_andBackAction_followUiState() {
        assertEquals(
            ThreadRefreshAvailability.Busy,
            resolveThreadRefreshAvailability(isRefreshing = true)
        )
        assertEquals(
            ThreadRefreshAvailability.Ready,
            resolveThreadRefreshAvailability(isRefreshing = false)
        )
        assertEquals(
            ThreadBackAction.CloseDrawer,
            resolveThreadBackAction(isDrawerOpen = true)
        )
        assertEquals(
            ThreadBackAction.NavigateBack,
            resolveThreadBackAction(isDrawerOpen = false)
        )
        assertEquals("更新中です…", buildThreadRefreshBusyMessage())
        assertEquals(
            ThreadHistoryRefreshAvailability.Busy,
            resolveThreadHistoryRefreshAvailability(isHistoryRefreshing = true)
        )
        assertEquals(
            ThreadHistoryRefreshAvailability.Ready,
            resolveThreadHistoryRefreshAvailability(isHistoryRefreshing = false)
        )
    }

    @Test
    fun resolveThreadMenuActionState_mapsEntriesToUiIntents() {
        assertEquals(
            ThreadMenuActionState(
                showReplyDialog = true,
                applyReplyDeleteKeyAutofill = true
            ),
            resolveThreadMenuActionState(ThreadMenuEntryId.Reply, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(scrollTarget = ThreadScrollTarget.Top),
            resolveThreadMenuActionState(ThreadMenuEntryId.ScrollToTop, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(scrollTarget = ThreadScrollTarget.Bottom),
            resolveThreadMenuActionState(ThreadMenuEntryId.ScrollToBottom, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(showRefreshBusyMessage = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.Refresh, isRefreshing = true)
        )
        assertEquals(
            ThreadMenuActionState(startRefresh = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.Refresh, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(delegateToSaveHandler = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.Save, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(showSettingsSheet = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.Settings, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(showNgManagement = true, clearNgHeaderPrefill = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.NgManagement, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(openExternalApp = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.ExternalApp, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(showReadAloudControls = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.ReadAloud, isRefreshing = false)
        )
        assertEquals(
            ThreadMenuActionState(togglePrivacy = true),
            resolveThreadMenuActionState(ThreadMenuEntryId.Privacy, isRefreshing = false)
        )
    }

    @Test
    fun threadAutoSaveAvailability_prioritizes_blocking_conditions() {
        assertEquals(
            ThreadAutoSaveAvailability.MissingPage,
            resolveThreadAutoSaveAvailability(
                pageThreadId = null,
                expectedThreadId = "123",
                isShowingOfflineCopy = false,
                hasAutoSaveRepository = true,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = false,
                lastAutoSaveTimestampMillis = 0L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
        assertEquals(
            ThreadAutoSaveAvailability.OfflineCopy,
            resolveThreadAutoSaveAvailability(
                pageThreadId = "123",
                expectedThreadId = "123",
                isShowingOfflineCopy = true,
                hasAutoSaveRepository = true,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = false,
                lastAutoSaveTimestampMillis = 0L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
        assertEquals(
            ThreadAutoSaveAvailability.ThreadMismatch,
            resolveThreadAutoSaveAvailability(
                pageThreadId = "999",
                expectedThreadId = "123",
                isShowingOfflineCopy = false,
                hasAutoSaveRepository = true,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = false,
                lastAutoSaveTimestampMillis = 0L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
        assertEquals(
            ThreadAutoSaveAvailability.MissingDependencies,
            resolveThreadAutoSaveAvailability(
                pageThreadId = "123",
                expectedThreadId = "123",
                isShowingOfflineCopy = false,
                hasAutoSaveRepository = false,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = false,
                lastAutoSaveTimestampMillis = 0L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
        assertEquals(
            ThreadAutoSaveAvailability.InProgress,
            resolveThreadAutoSaveAvailability(
                pageThreadId = "123",
                expectedThreadId = "123",
                isShowingOfflineCopy = false,
                hasAutoSaveRepository = true,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = true,
                lastAutoSaveTimestampMillis = 0L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
        assertEquals(
            ThreadAutoSaveAvailability.Throttled,
            resolveThreadAutoSaveAvailability(
                pageThreadId = "123",
                expectedThreadId = "123",
                isShowingOfflineCopy = false,
                hasAutoSaveRepository = true,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = false,
                lastAutoSaveTimestampMillis = 30_000L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
        assertEquals(
            ThreadAutoSaveAvailability.Ready,
            resolveThreadAutoSaveAvailability(
                pageThreadId = "123",
                expectedThreadId = "123",
                isShowingOfflineCopy = false,
                hasAutoSaveRepository = true,
                hasHttpClient = true,
                hasFileSystem = true,
                isAutoSaveInProgress = false,
                lastAutoSaveTimestampMillis = 1_000L,
                nowMillis = 61_000L,
                minIntervalMillis = 60_000L
            )
        )
    }

    @Test
    fun threadAutoSaveCompletionState_updatesTimestampAndCarriesFailure() {
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            title = "title",
            storageId = "storage",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 1,
            videoCount = 0,
            totalSize = 100L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        val successState = resolveThreadAutoSaveCompletionState(
            threadId = "123",
            saveResult = Result.success(savedThread),
            previousTimestampMillis = 10L,
            attemptStartedAtMillis = 20L,
            completionTimestampMillis = 30L
        )
        assertEquals(30L, successState.nextTimestampMillis)
        assertEquals(savedThread, successState.savedThread)
        assertEquals(null, successState.failureMessage)
        assertEquals(null, successState.failure)

        val failure = IllegalStateException("boom")
        val failureState = resolveThreadAutoSaveCompletionState(
            threadId = "123",
            saveResult = Result.failure(failure),
            previousTimestampMillis = 10L,
            attemptStartedAtMillis = 20L,
            completionTimestampMillis = 30L
        )
        assertEquals(20L, failureState.nextTimestampMillis)
        assertEquals(null, failureState.savedThread)
        assertEquals("Auto-save failed for thread 123", failureState.failureMessage)
        assertEquals(failure, failureState.failure)
        assertEquals("Auto-save failed for thread 123", buildThreadAutoSaveFailureMessage("123"))
        assertEquals("Failed to index auto-saved thread 123", buildThreadAutoSaveIndexFailureMessage("123"))
    }

    @Test
    fun threadAutoSaveRunner_wrapsCompletionState() = runBlocking {
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            title = "title",
            storageId = "may-b__123",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 1,
            videoCount = 0,
            totalSize = 100L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )
        val config = ThreadAutoSaveRunnerConfig(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b",
            title = "title",
            expiresAtLabel = "12:34",
            posts = emptyList(),
            previousTimestampMillis = 10L,
            attemptStartedAtMillis = 20L,
            completionTimestampMillis = 30L
        )

        assertEquals(
            ThreadAutoSaveRunResult(
                completionState = ThreadAutoSaveCompletionState(
                    nextTimestampMillis = 30L,
                    savedThread = savedThread
                )
            ),
            performThreadAutoSave(
                config = config,
                callbacks = ThreadAutoSaveRunnerCallbacks(
                    saveThread = { Result.success(savedThread) }
                )
            )
        )
        val failureResult = performThreadAutoSave(
            config = config,
            callbacks = ThreadAutoSaveRunnerCallbacks(
                saveThread = { Result.failure(IllegalStateException("boom")) }
            )
        )
        assertEquals(20L, failureResult.completionState.nextTimestampMillis)
        assertEquals(null, failureResult.completionState.savedThread)
        assertEquals("Auto-save failed for thread 123", failureResult.completionState.failureMessage)
        assertEquals("boom", failureResult.completionState.failure?.message)
    }

    @Test
    fun mediaPreviewHelpers_buildEntries_detectTypes_and_wrapIndices() {
        val posts = listOf(
            Post(
                id = "1",
                author = null,
                subject = "件名",
                timestamp = "24/01/01(月)00:00:00",
                posterId = null,
                messageHtml = "本文1<br>二行目",
                imageUrl = "https://may.2chan.net/b/src/1.png",
                thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg"
            ),
            Post(
                id = "2",
                author = null,
                subject = null,
                timestamp = "24/01/01(月)00:00:00",
                posterId = null,
                messageHtml = "",
                imageUrl = "https://may.2chan.net/b/src/2.webm?foo=1",
                thumbnailUrl = null
            ),
            Post(
                id = "3",
                author = null,
                subject = null,
                timestamp = "24/01/01(月)00:00:00",
                posterId = null,
                messageHtml = "   ",
                imageUrl = null,
                thumbnailUrl = "https://may.2chan.net/b/thumb/3s.jpg"
            ),
            Post(
                id = "4",
                author = null,
                subject = null,
                timestamp = "24/01/01(月)00:00:00",
                posterId = null,
                messageHtml = "本文なし",
                imageUrl = null,
                thumbnailUrl = null
            )
        )

        val entries = buildMediaPreviewEntries(posts)

        assertEquals(3, entries.size)
        assertEquals(MediaType.Image, determineMediaType("https://may.2chan.net/b/src/1.png"))
        assertEquals(MediaType.Video, determineMediaType("https://may.2chan.net/b/src/2.webm?foo=1"))
        assertTrue(isRemoteMediaUrl("https://may.2chan.net/b/src/1.png"))
        assertTrue(isRemoteMediaUrl("http://may.2chan.net/b/src/1.png"))
        assertFalse(isRemoteMediaUrl("/local/file.png"))
        assertEquals("本文1", entries[0].title)
        assertEquals("2", entries[1].postId)
        assertEquals(MediaType.Video, entries[1].mediaType)
        assertEquals("No.3", entries[2].title)
        assertEquals(1, normalizeMediaPreviewIndex(currentIndex = 1, totalCount = 3))
        assertEquals(null, normalizeMediaPreviewIndex(currentIndex = 3, totalCount = 3))
        assertEquals(0, nextMediaPreviewIndex(currentIndex = 2, totalCount = 3))
        assertEquals(2, previousMediaPreviewIndex(currentIndex = 0, totalCount = 3))
        assertEquals(1, nextMediaPreviewIndex(currentIndex = null, totalCount = 2))
        assertEquals(1, previousMediaPreviewIndex(currentIndex = null, totalCount = 2))
        assertEquals(null, nextMediaPreviewIndex(currentIndex = 0, totalCount = 0))
    }


    private fun post(
        id: String,
        posterId: String? = null,
        messageHtml: String = "本文",
        isDeleted: Boolean = false
    ): Post {
        return Post(
            id = id,
            author = null,
            subject = null,
            timestamp = "24/01/01(月)00:00:00",
            posterId = posterId,
            messageHtml = messageHtml,
            imageUrl = null,
            thumbnailUrl = null,
            isDeleted = isDeleted
        )
    }
}
