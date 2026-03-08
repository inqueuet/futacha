package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.NetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadScreenLogicTest {
    @Test
    fun normalizeArchiveQuery_trimsWhitespaceAndMaxLength() {
        assertEquals("hello world", normalizeArchiveQuery("  hello   world  ", maxLength = 32))
        assertEquals("abc d", normalizeArchiveQuery("abc   def", maxLength = 5))
        assertEquals("", normalizeArchiveQuery("   ", maxLength = 10))
    }

    @Test
    fun isOfflineFallbackCandidate_detectsNetworkConditions() {
        assertTrue(isOfflineFallbackCandidate(NetworkException("HTTP error", statusCode = 404)))
        assertTrue(isOfflineFallbackCandidate(IllegalStateException("connection timeout")))
        assertTrue(isOfflineFallbackCandidate(RuntimeException("wrapper", NetworkException("dns failure"))))
        assertFalse(isOfflineFallbackCandidate(IllegalArgumentException("bad input")))
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
        assertEquals(listOf(0..0, 6..6), matches[1].highlightRanges)
    }

    @Test
    fun nextAndPreviousThreadSearchResultIndex_wrapAround() {
        assertEquals(0, nextThreadSearchResultIndex(currentIndex = 2, matchCount = 3))
        assertEquals(2, previousThreadSearchResultIndex(currentIndex = 0, matchCount = 3))
        assertEquals(0, nextThreadSearchResultIndex(currentIndex = 0, matchCount = 0))
        assertEquals(0, previousThreadSearchResultIndex(currentIndex = 0, matchCount = 0))
    }

    @Test
    fun threadRefreshAndLoadMessages_mapKnownCases() {
        assertEquals(
            "ネットワーク接続不可: ローカルコピーを表示しています",
            buildThreadRefreshSuccessMessage(usedOffline = true)
        )
        assertEquals(
            "スレッドを更新しました",
            buildThreadRefreshSuccessMessage(usedOffline = false)
        )
        assertEquals(
            "更新に失敗しました: スレッドが見つかりません (404)",
            buildThreadRefreshFailureMessage(IllegalStateException("x"), 404)
        )
        assertEquals(
            "更新に失敗しました: スレッドは削除済みです (410)",
            buildThreadRefreshFailureMessage(IllegalStateException("x"), 410)
        )
        assertEquals(
            "更新に失敗しました: boom",
            buildThreadRefreshFailureMessage(IllegalStateException("boom"), null)
        )
        assertEquals(
            "タイムアウト: サーバーが応答しません",
            buildThreadInitialLoadErrorMessage(IllegalStateException("request timeout"), null)
        )
        assertEquals(
            "スレッドが見つかりません (404)",
            buildThreadInitialLoadErrorMessage(IllegalStateException("x"), 404)
        )
        assertEquals(
            "スレッドは削除済みです (410)",
            buildThreadInitialLoadErrorMessage(IllegalStateException("x"), 410)
        )
        assertEquals(
            "サーバーエラー (503)",
            buildThreadInitialLoadErrorMessage(IllegalStateException("x"), 503)
        )
        assertEquals(
            "ネットワークエラー: HTTP error: reset",
            buildThreadInitialLoadErrorMessage(IllegalStateException("HTTP error: reset"), null)
        )
        assertEquals(
            "データサイズが大きすぎます",
            buildThreadInitialLoadErrorMessage(IllegalStateException("body exceeds maximum size"), null)
        )
        assertEquals(
            "スレッドを読み込めませんでした: boom",
            buildThreadInitialLoadErrorMessage(IllegalStateException("boom"), null)
        )
    }

    @Test
    fun threadSaveAndReadAloudMessages_matchUiCopy() {
        assertEquals("保存処理を実行中です…", buildThreadSaveBusyMessage())
        assertEquals("保存先が未選択です。設定からフォルダを選択してください。", buildThreadSaveLocationRequiredMessage())
        assertEquals("保存機能が利用できません", buildThreadSaveUnavailableMessage())
        assertEquals("スレッドの読み込みが完了していません", buildThreadSaveNotReadyMessage())
        assertEquals("保存先の権限が失われました。フォルダを再選択してください。", buildThreadSavePermissionLostMessage())
        assertEquals("保存に失敗しました: boom", buildThreadSaveFailureMessage(IllegalStateException("boom")))
        assertEquals("エラーが発生しました: boom", buildThreadSaveUnexpectedErrorMessage(IllegalStateException("boom")))
        assertEquals("読み上げ対象がありません", buildReadAloudNoTargetMessage())
        assertEquals("読み上げを一時停止しました", buildReadAloudPausedMessage())
        assertEquals("読み上げを完了しました", buildReadAloudCompletedMessage())
        assertEquals("読み上げを停止しました", buildReadAloudStoppedMessage())
        assertEquals(
            "読み上げ中にエラーが発生しました: boom",
            buildReadAloudFailureMessage(IllegalStateException("boom"))
        )
    }

    @Test
    fun threadSavePreconditions_andPermissionIssueDetection_workAsExpected() {
        assertTrue(requiresThreadManualSaveLocationSelection(isAndroidPlatform = true, manualSaveLocation = null))
        assertFalse(
            requiresThreadManualSaveLocationSelection(
                isAndroidPlatform = true,
                manualSaveLocation = SaveLocation.Path("/tmp")
            )
        )
        assertFalse(
            requiresThreadManualSaveLocationSelection(
                isAndroidPlatform = true,
                manualSaveLocation = SaveLocation.TreeUri("content://tree")
            )
        )
        assertFalse(
            requiresThreadManualSaveLocationSelection(
                isAndroidPlatform = false,
                manualSaveLocation = null
            )
        )
        assertTrue(isThreadSaveLocationPermissionIssue(IllegalStateException("cannot resolve tree uri")))
        assertTrue(isThreadSaveLocationPermissionIssue(IllegalStateException("invalid bookmark data")))
        assertFalse(isThreadSaveLocationPermissionIssue(IllegalStateException("other error")))
    }

    @Test
    fun threadActionAndValidationMessages_matchUiCopy() {
        assertEquals(
            "返信の送信に失敗しました: boom",
            buildThreadActionFailureMessage("返信の送信に失敗しました", IllegalStateException("boom"))
        )
        assertEquals(
            "返信の送信に失敗しました",
            buildThreadActionFailureMessage("返信の送信に失敗しました", IllegalStateException(""))
        )
        assertEquals("自分のレスにはそうだねできません", buildSelfSaidaneBlockedMessage())
        assertEquals("IDが見つかりませんでした", buildMissingPosterIdMessage())
        assertEquals("削除キーを入力してください", buildDeletePasswordRequiredMessage())
        assertEquals("コメントを入力してください", buildReplyCommentRequiredMessage())
    }

    @Test
    fun threadDeleteAndReplyValidation_enforcesRequiredFields() {
        assertEquals(
            "削除キーを入力してください",
            validateThreadDeletePassword("   ")
        )
        assertEquals(null, validateThreadDeletePassword("pass"))

        assertEquals(
            "削除キーを入力してください",
            validateThreadReplyForm(password = " ", comment = "body")
        )
        assertEquals(
            "コメントを入力してください",
            validateThreadReplyForm(password = "pass", comment = "   ")
        )
        assertEquals(
            null,
            validateThreadReplyForm(password = "pass", comment = "body")
        )
    }

    @Test
    fun updateThreadFilterSelection_enforcesSingleSortOption() {
        val (withSaidane, sortA) = updateThreadFilterSelection(
            selectedOptions = setOf(ThreadFilterOption.Url),
            selectedSortOption = null,
            toggledOption = ThreadFilterOption.HighSaidane
        )
        assertEquals(setOf(ThreadFilterOption.Url, ThreadFilterOption.HighSaidane), withSaidane)
        assertEquals(ThreadFilterSortOption.Saidane, sortA)

        val (withReplies, sortB) = updateThreadFilterSelection(
            selectedOptions = withSaidane,
            selectedSortOption = sortA,
            toggledOption = ThreadFilterOption.HighReplies
        )
        assertEquals(setOf(ThreadFilterOption.Url, ThreadFilterOption.HighReplies), withReplies)
        assertEquals(ThreadFilterSortOption.Replies, sortB)

        val (withoutReplies, sortC) = updateThreadFilterSelection(
            selectedOptions = withReplies,
            selectedSortOption = sortB,
            toggledOption = ThreadFilterOption.HighReplies
        )
        assertEquals(setOf(ThreadFilterOption.Url), withoutReplies)
        assertEquals(null, sortC)
    }

    @Test
    fun threadFilterHelpers_matchExpectedPostsAndSorts() {
        val posts = listOf(
            Post(
                id = "1",
                author = "Alice",
                subject = "猫",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:a",
                messageHtml = "https://example.com 猫",
                imageUrl = "https://img/1.jpg",
                thumbnailUrl = "https://img/1s.jpg",
                saidaneLabel = "そうだね 5",
                referencedCount = 1
            ),
            Post(
                id = "2",
                author = "Bob",
                subject = "犬",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:b",
                messageHtml = "本文",
                imageUrl = null,
                thumbnailUrl = null,
                isDeleted = true,
                saidaneLabel = "そうだね 1",
                referencedCount = 9
            ),
            Post(
                id = "3",
                author = "Carol",
                subject = "鳥",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:c",
                messageHtml = "猫だけ",
                imageUrl = null,
                thumbnailUrl = null,
                saidaneLabel = "そうだね 9",
                referencedCount = 3
            )
        )
        val page = ThreadPage(
            threadId = "100",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = posts
        )

        val urlFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.Url),
                keyword = "",
                selfPostIdentifiers = emptyList(),
                sortOption = null
            )
        )
        assertEquals(listOf("1"), urlFiltered.posts.map { it.id })

        val keywordFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.Keyword),
                keyword = "猫",
                selfPostIdentifiers = emptyList(),
                sortOption = null
            )
        )
        assertEquals(listOf("1", "3"), keywordFiltered.posts.map { it.id })

        val selfFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.SelfPosts),
                keyword = "",
                selfPostIdentifiers = listOf("2"),
                sortOption = null
            )
        )
        assertEquals(listOf("2"), selfFiltered.posts.map { it.id })

        val deletedFiltered = applyThreadFilters(
            page = page,
            criteria = ThreadFilterCriteria(
                options = setOf(ThreadFilterOption.Deleted),
                keyword = "",
                selfPostIdentifiers = emptyList(),
                sortOption = null
            )
        )
        assertEquals(listOf("2"), deletedFiltered.posts.map { it.id })

        val sortedBySaidane = sortThreadPosts(posts, ThreadFilterSortOption.Saidane)
        assertEquals(listOf("3", "1", "2"), sortedBySaidane.map { it.id })

        val sortedByReplies = sortThreadPosts(posts, ThreadFilterSortOption.Replies)
        assertEquals(listOf("2", "3", "1"), sortedByReplies.map { it.id })
    }

    @Test
    fun threadFilterPrimitiveHelpers_coverKeywordsHeadersAndCounts() {
        assertTrue(matchesKeyword(lowerText = "本文 猫", subject = "犬", keywordInput = "猫"))
        assertTrue(matchesKeyword(lowerText = "本文", subject = "猫 subject", keywordInput = "猫"))
        assertFalse(matchesKeyword(lowerText = "本文", subject = "犬", keywordInput = "猫"))

        val post = Post(
            id = "12",
            author = "Alice",
            subject = "件名",
            timestamp = "24/01/01(月)00:00:00",
            posterId = "ID:abc",
            messageHtml = "www.example.com",
            imageUrl = null,
            thumbnailUrl = null
        )
        assertTrue(buildPostHeaderText(post).contains("alice"))
        assertTrue(THREAD_FILTER_URL_REGEX.containsMatchIn("go to https://example.com"))
        assertEquals(42, parseSaidaneCount("そうだね 42"))
        assertEquals(null, parseSaidaneCount("なし"))
        assertTrue(matchesSelfFilter(post.copy(id = "99"), setOf("99")))
        assertFalse(matchesSelfFilter(post.copy(id = "98"), setOf("99")))
        assertEquals(
            mapOf("12" to "www.example.com"),
            buildLowerBodyByPostId(listOf(post))
        )
    }

    @Test
    fun quoteSelectionHelpers_buildDefaultsAndAppendQuotedLines() {
        val post = Post(
            id = "12",
            author = "Alice",
            subject = "件名",
            timestamp = "24/01/01(月)00:00:00",
            posterId = "ID:abc",
            messageHtml = "一行目<br>二行目",
            imageUrl = "https://may.2chan.net/b/src/abc123.png",
            thumbnailUrl = null
        )

        val items = buildQuoteSelectionItems(post)

        assertEquals(
            listOf("number-12", "file-12", "line-0", "line-1"),
            items.map { it.id }
        )
        assertEquals(setOf("number-12"), defaultQuoteSelectionIds(items))
        assertEquals(
            "既存\n>No.12\n>一行目\n",
            appendSelectedQuoteLines("既存", listOf(">No.12", ">一行目"))
        )
        assertEquals(
            ">No.12\n",
            appendSelectedQuoteLines("", listOf(">No.12"))
        )
        assertEquals(
            null,
            appendSelectedQuoteLines("既存", emptyList())
        )
    }

    @Test
    fun ngFilterHelpers_filterHeadersAndWords_andHonorDisabledFlag() {
        val posts = listOf(
            Post(
                id = "1",
                author = "Alice",
                subject = "件名",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:abc",
                messageHtml = "本文 猫",
                imageUrl = null,
                thumbnailUrl = null
            ),
            Post(
                id = "2",
                author = "Bob",
                subject = "別件",
                timestamp = "24/01/01(月)00:00:00",
                posterId = "ID:def",
                messageHtml = "安全",
                imageUrl = null,
                thumbnailUrl = null
            )
        )
        val page = ThreadPage(
            threadId = "100",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = posts
        )

        val lowerBodies = buildLowerBodyByPostId(posts)
        assertTrue(
            matchesNgFilters(
                post = posts[0],
                headerFilters = listOf("alice"),
                wordFilters = emptyList(),
                lowerBodyByPostId = lowerBodies
            )
        )
        assertTrue(
            matchesNgFilters(
                post = posts[0],
                headerFilters = emptyList(),
                wordFilters = listOf("猫"),
                lowerBodyByPostId = lowerBodies
            )
        )
        assertFalse(
            matchesNgFilters(
                post = posts[1],
                headerFilters = listOf("alice"),
                wordFilters = listOf("猫"),
                lowerBodyByPostId = lowerBodies
            )
        )

        val filtered = applyNgFilters(
            page = page,
            ngHeaders = listOf("alice"),
            ngWords = listOf("猫"),
            enabled = true,
            precomputedLowerBodyByPostId = lowerBodies
        )
        assertEquals(listOf("2"), filtered.posts.map { it.id })

        val unfiltered = applyNgFilters(
            page = page,
            ngHeaders = listOf("alice"),
            ngWords = listOf("猫"),
            enabled = false,
            precomputedLowerBodyByPostId = lowerBodies
        )
        assertEquals(listOf("1", "2"), unfiltered.posts.map { it.id })
    }

    @Test
    fun stableFingerprints_normalizeCaseWhitespaceAndOptionOrder() {
        assertEquals(
            stableNormalizedListFingerprint(listOf(" Alice ", "BOB")),
            stableNormalizedListFingerprint(listOf("alice", " bob "))
        )
        assertEquals(
            stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Url, ThreadFilterOption.Keyword)),
            stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Keyword, ThreadFilterOption.Url))
        )
        assertFalse(
            stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Url)) ==
                stableThreadFilterOptionSetFingerprint(setOf(ThreadFilterOption.Keyword))
        )
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
