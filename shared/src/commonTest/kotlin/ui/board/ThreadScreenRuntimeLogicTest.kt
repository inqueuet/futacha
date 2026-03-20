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

class ThreadScreenRuntimeLogicTest {
    @Test
    fun threadMessageRuntimeHelpers_showOptionalMessageAndApplySaveErrorState() = runBlocking {
        val shownMessages = mutableListOf<String>()
        var manualSaveDirectory: String? = null
        var saveDirectorySelection: com.valoser.futacha.shared.util.SaveDirectorySelection? = null
        var pickerOpened = false

        showThreadMessage("a") { shownMessages += it }
        showOptionalThreadMessage(null) { shownMessages += it }
        showOptionalThreadMessage("b") { shownMessages += it }
        applyThreadSaveErrorState(
            errorState = ThreadManualSaveErrorState(
                message = "err",
                shouldResetManualSaveDirectory = true,
                shouldResetSaveDirectorySelection = true,
                shouldOpenDirectoryPicker = true
            ),
            onManualSaveDirectoryChanged = { manualSaveDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onShowMessage = { shownMessages += it },
            onOpenSaveDirectoryPicker = { pickerOpened = true }
        )

        assertEquals(listOf("a", "b", "err"), shownMessages)
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, manualSaveDirectory)
        assertEquals(com.valoser.futacha.shared.util.SaveDirectorySelection.MANUAL_INPUT, saveDirectorySelection)
        assertTrue(pickerOpened)
    }

    @Test
    fun threadScreenPlatformRuntimeSupport_handlesRegisteredAndExternalUrls() {
        val launchedUrls = mutableListOf<String>()

        handleThreadUrlClick(
            url = "https://example.com/a",
            onRegisteredThreadUrlClick = { false },
            onLaunchExternalUrl = { launchedUrls += it }
        )
        handleThreadUrlClick(
            url = "https://example.com/b",
            onRegisteredThreadUrlClick = { it.endsWith("/b") },
            onLaunchExternalUrl = { launchedUrls += it }
        )

        assertEquals(listOf("https://example.com/a"), launchedUrls)
    }

    @Test
    fun threadScreenPersistentBindingsSupport_buildsDeleteKeySelfPostAndNgState() {
        assertEquals("123", buildThreadScopedSelfPostKey(boardId = "", threadId = "123"))
        assertEquals("b::123", buildThreadScopedSelfPostKey(boardId = "b", threadId = "123"))

        val persistedIdentifiers = buildPersistedSelfPostIdentifiers(
            persistedSelfPostMap = mapOf(
                "b::123" to listOf(" abc ", ""),
                "123" to listOf("def")
            ),
            scopedSelfPostKey = "b::123",
            threadId = "123"
        )
        val selfPostIdentifierSet = buildSelfPostIdentifierSet(persistedIdentifiers)

        assertEquals(listOf(" abc ", "", "def"), persistedIdentifiers)
        assertEquals(setOf("abc", "def"), selfPostIdentifierSet)
        assertTrue(isThreadSelfPost(selfPostIdentifierSet, " abc "))
        assertFalse(isThreadSelfPost(selfPostIdentifierSet, "ghi"))
    }

    @Test
    fun threadScreenMutableStateSupport_buildsExpectedDefaults() {
        val defaults = buildThreadScreenMutableStateDefaults()

        assertNull(defaults.resolvedThreadUrlOverride)
        assertEquals(ThreadUiState.Loading, defaults.uiState)
        assertEquals(ReadAloudStatus.Idle, defaults.readAloudStatus)
        assertEquals(emptyThreadSheetOverlayState(), defaults.sheetOverlayState)
        assertEquals(0, defaults.currentReadAloudIndex)
        assertFalse(defaults.readAloudCancelRequestedByUser)
        assertFalse(defaults.isManualSaveInProgress)
        assertFalse(defaults.isSingleMediaSaveInProgress)
        assertEquals(0L, defaults.lastAutoSaveTimestamp)
        assertFalse(defaults.isShowingOfflineCopy)
        assertFalse(defaults.actionInProgress)
        assertEquals(0L, defaults.lastBusyActionNoticeAtMillis)
        assertEquals(emptyThreadPostOverlayState(), defaults.postOverlayState)
        assertFalse(defaults.isReplyDialogVisible)
        assertEquals(emptySet(), defaults.selectedThreadFilterOptions)
        assertNull(defaults.selectedThreadSortOption)
        assertEquals("", defaults.threadFilterKeyword)
        assertEquals(emptyThreadModalOverlayState(), defaults.modalOverlayState)
        assertTrue(defaults.ngFilteringEnabled)
        assertEquals("", defaults.replyName)
        assertEquals("", defaults.replyEmail)
        assertEquals("", defaults.replySubject)
        assertEquals("", defaults.replyComment)
        assertEquals("", defaults.replyPassword)
        assertNull(defaults.replyImageData)
        assertFalse(defaults.isRefreshing)
        assertEquals(0L, defaults.manualRefreshGeneration)
        assertFalse(defaults.isHistoryRefreshing)
        assertNull(defaults.saveProgress)
        assertFalse(defaults.hasRestoredInitialScroll)
        assertFalse(defaults.isSearchActive)
        assertEquals("", defaults.searchQuery)
        assertEquals(0, defaults.currentSearchResultIndex)
        assertEquals(emptyThreadMediaPreviewState(), defaults.mediaPreviewState)
    }

    @Test
    fun threadScreenMutableStateSupport_preservesInitialThreadUrlOverride() {
        val defaults = buildThreadScreenMutableStateDefaults(
            threadUrlOverride = "https://may.2chan.net/b/res/123.htm"
        )

        assertEquals("https://may.2chan.net/b/res/123.htm", defaults.resolvedThreadUrlOverride)
        assertEquals(ThreadUiState.Loading, defaults.uiState)
    }

    @Test
    fun threadScreenRuntimeObjectsSupport_buildsLazyListSeedFromHistoryEntry() {
        assertEquals(
            ThreadScreenRuntimeObjectSeed(
                initialListIndex = 0,
                initialListOffset = 0
            ),
            buildThreadScreenRuntimeObjectSeed(null)
        )
        assertEquals(
            ThreadScreenRuntimeObjectSeed(
                initialListIndex = 12,
                initialListOffset = 34
            ),
            buildThreadScreenRuntimeObjectSeed(
                ThreadHistoryEntry(
                    threadId = "123",
                    boardId = "b",
                    title = "Thread",
                    titleImageUrl = "",
                    boardName = "Board",
                    boardUrl = "https://may.2chan.net/b/res/123.htm",
                    lastVisitedEpochMillis = 1L,
                    replyCount = 10,
                    lastReadItemIndex = 12,
                    lastReadItemOffset = 34
                )
            )
        )
    }

    @Test
    fun threadScreenDerivedRuntimeSupport_buildsBackSwipeMetrics() {
        assertEquals(
            ThreadBackSwipeMetrics(
                edgePx = 96f,
                triggerPx = 192f
            ),
            buildThreadBackSwipeMetrics(Density(2f))
        )
    }

    @Test
    fun threadScreenLifecycleBindingsSupport_routesPauseDisposeBackAndRefresh() = runBlocking {
        val shownMessages = mutableListOf<String>()
        var stoppedReadAloud = false
        var closedSpeaker = false
        var resetJobs = false
        var cancelledJobs = false
        var closedDrawer = false
        var navigatedBack = false
        var refreshed = false
        var persistedScroll = 0

        val bindings = buildThreadScreenLifecycleBindings(
            coroutineScope = this,
            resolvePauseMessage = { "paused" },
            onShowPauseMessage = { shownMessages += it },
            onStopReadAloud = { stoppedReadAloud = true },
            onCloseTextSpeaker = { closedSpeaker = true },
            onResetJobsForThreadChange = { resetJobs = true },
            onCancelAllJobs = { cancelledJobs = true },
            isDrawerOpen = { true },
            onCloseDrawer = { closedDrawer = true },
            onPersistCurrentScrollPosition = { persistedScroll += 1 },
            onBack = { navigatedBack = true },
            onRefreshThread = { refreshed = true }
        )

        bindings.pauseReadAloud()
        yield()
        bindings.onTextSpeakerDispose()
        bindings.onThreadChanged()
        bindings.onScreenDispose()
        bindings.onBackPressed()
        yield()
        bindings.onInitialRefresh()

        assertEquals(listOf("paused"), shownMessages)
        assertTrue(stoppedReadAloud)
        assertTrue(closedSpeaker)
        assertTrue(resetJobs)
        assertTrue(cancelledJobs)
        assertTrue(closedDrawer)
        assertFalse(navigatedBack)
        assertTrue(refreshed)
        assertEquals(1, persistedScroll)

        val backBindings = buildThreadScreenLifecycleBindings(
            coroutineScope = this,
            resolvePauseMessage = { null },
            onShowPauseMessage = { shownMessages += it },
            onStopReadAloud = {},
            onCloseTextSpeaker = {},
            onResetJobsForThreadChange = {},
            onCancelAllJobs = {},
            isDrawerOpen = { false },
            onCloseDrawer = { closedDrawer = true },
            onPersistCurrentScrollPosition = { persistedScroll += 1 },
            onBack = { navigatedBack = true },
            onRefreshThread = {}
        )
        backBindings.onBackPressed()
        assertTrue(navigatedBack)
        assertEquals(2, persistedScroll)
    }

    @Test
    fun threadMediaSupport_resolvesPreviewNormalizationAndClickState() {
        val entries = listOf(
            MediaPreviewEntry(
                url = "https://example.com/a.jpg",
                mediaType = MediaType.Image,
                postId = "1",
                title = "a"
            ),
            MediaPreviewEntry(
                url = "https://example.com/b.mp4",
                mediaType = MediaType.Video,
                postId = "2",
                title = "b"
            )
        )
        assertEquals(
            ThreadMediaPreviewState(previewMediaIndex = 1),
            resolveThreadMediaClickState(
                currentState = emptyThreadMediaPreviewState(),
                entries = entries,
                url = "https://example.com/b.mp4",
                mediaType = MediaType.Video
            )
        )
        assertNull(
            resolveThreadMediaClickState(
                currentState = emptyThreadMediaPreviewState(),
                entries = entries,
                url = "https://example.com/missing.jpg",
                mediaType = MediaType.Image
            )
        )
        assertEquals(
            emptyThreadMediaPreviewState(),
            resolveThreadMediaPreviewNormalizationState(
                currentState = ThreadMediaPreviewState(previewMediaIndex = 9),
                totalCount = entries.size
            )
        )
        assertNull(
            resolveThreadMediaPreviewNormalizationState(
                currentState = ThreadMediaPreviewState(previewMediaIndex = 1),
                totalCount = entries.size
            )
        )
    }

    @Test
    fun threadScreenMediaBindingsSupport_updatesPreviewState() {
        val entries = listOf(
            MediaPreviewEntry(
                url = "https://example.com/a.jpg",
                mediaType = MediaType.Image,
                postId = "1",
                title = "a"
            ),
            MediaPreviewEntry(
                url = "https://example.com/b.mp4",
                mediaType = MediaType.Video,
                postId = "2",
                title = "b"
            )
        )
        var previewState = ThreadMediaPreviewState(previewMediaIndex = 9)

        val bindings = buildThreadScreenMediaBindings(
            currentPreviewState = { previewState },
            setPreviewState = { previewState = it },
            currentEntries = { entries }
        )

        bindings.normalizePreviewState()
        assertEquals(emptyThreadMediaPreviewState(), previewState)

        bindings.onMediaClick("https://example.com/b.mp4", MediaType.Video)
        assertEquals(ThreadMediaPreviewState(previewMediaIndex = 1), previewState)
    }

    @Test
    fun threadRuntimeBindings_bundleMessageSaveErrorAndNgPersistence() = runBlocking {
        val shownMessages = mutableListOf<String>()
        var manualSaveDirectory: String? = null
        var saveDirectorySelection: com.valoser.futacha.shared.util.SaveDirectorySelection? = null
        var pickerOpened = false
        val messageRuntime = buildThreadMessageRuntimeBindings(
            coroutineScope = this,
            showSnackbar = { shownMessages += it },
            onManualSaveDirectoryChanged = { manualSaveDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onOpenSaveDirectoryPicker = { pickerOpened = true }
        )

        messageRuntime.showMessage("a")
        messageRuntime.showOptionalMessage(null)
        messageRuntime.showOptionalMessage("b")
        messageRuntime.applySaveErrorState(
            ThreadManualSaveErrorState(
                message = "err",
                shouldResetManualSaveDirectory = true,
                shouldResetSaveDirectorySelection = true,
                shouldOpenDirectoryPicker = true
            )
        )
        yield()

        assertEquals(listOf("a", "b", "err"), shownMessages)
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, manualSaveDirectory)
        assertEquals(com.valoser.futacha.shared.util.SaveDirectorySelection.MANUAL_INPUT, saveDirectorySelection)
        assertTrue(pickerOpened)

        var fallbackHeaders = emptyList<String>()
        var fallbackWords = emptyList<String>()
        val ngPersistenceBindings = buildThreadNgPersistenceBindings(
            coroutineScope = this,
            stateStore = null,
            onFallbackHeadersChanged = { fallbackHeaders = it },
            onFallbackWordsChanged = { fallbackWords = it }
        )
        ngPersistenceBindings.persistHeaders(listOf("h"))
        ngPersistenceBindings.persistWords(listOf("w"))
        yield()

        assertEquals(listOf("h"), fallbackHeaders)
        assertEquals(listOf("w"), fallbackWords)
    }

    @Test
    fun threadReplyDraftHelpers_applyAutofillAndClearRules() {
        val imageData = ImageData(bytes = byteArrayOf(1, 2, 3), fileName = "a.jpg")
        val draft = ThreadReplyDraft(
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "comment",
            password = "",
            imageData = imageData
        )

        val autofilled = applyReplyDraftDeleteKeyAutofill(draft, lastUsedDeleteKey = "stored")
        val clearedAfterSubmit = clearThreadReplyDraftAfterSubmit(autofilled)

        assertEquals("stored", autofilled.password)
        assertEquals(
            ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "",
                comment = "",
                password = "",
                imageData = null
            ),
            clearedAfterSubmit
        )
        assertEquals(ThreadReplyDraft(), emptyThreadReplyDraft())
    }

    @Test
    fun threadContentSupport_buildsDerivedDataAndFingerprints() {
        val posts = listOf(
            Post(
                id = "1",
                order = 1,
                author = null,
                subject = null,
                timestamp = "now",
                posterId = "ID:abc",
                messageHtml = "body",
                imageUrl = null,
                thumbnailUrl = null,
                quoteReferences = listOf(QuoteReference(text = ">>2", targetPostIds = listOf("2")))
            ),
            Post(
                id = "2",
                order = 2,
                author = null,
                subject = null,
                timestamp = "now",
                posterId = "abc",
                messageHtml = "reply",
                imageUrl = null,
                thumbnailUrl = null
            )
        )

        val derived = buildThreadPostDerivedData(posts)

        assertEquals(setOf("1", "2"), derived.postIndex.keys)
        assertEquals(listOf(posts[0], posts[1]), derived.postsByPosterId["abc"])
        assertEquals(listOf(posts[0]), derived.referencedByMap["2"])
        assertEquals(2, derived.posterIdLabels.size)

        val lightweight = buildLightweightThreadPostListFingerprint(posts)
        val fingerprint = buildThreadPostListFingerprint(posts)
        assertEquals(2, lightweight.size)
        assertEquals("1", lightweight.firstPostId)
        assertEquals("2", fingerprint.lastPostId)
        assertTrue(fingerprint.rollingHash != 0L)
    }

    @Test
    fun threadFilterSupport_buildsComputationStateAndCacheKey() {
        val uiState = ThreadFilterUiState(
            options = setOf(ThreadFilterOption.Keyword, ThreadFilterOption.SelfPosts),
            sortOption = ThreadFilterSortOption.Replies,
            keyword = " Foo "
        )

        val computationState = resolveThreadFilterComputationState(
            uiState = uiState,
            selfPostIdentifiers = listOf("123"),
            ngHeaders = listOf(" ID:abc "),
            ngWords = listOf(" ng "),
            ngFilteringEnabled = true
        )

        assertTrue(computationState.hasNgFilters)
        assertTrue(computationState.hasThreadFilters)
        assertTrue(computationState.shouldComputeFullPostFingerprint)
        assertEquals(setOf(ThreadFilterOption.Keyword, ThreadFilterOption.SelfPosts), computationState.criteria.options)
        assertEquals(listOf("123"), computationState.criteria.selfPostIdentifiers)

        val cacheKey = buildThreadFilterCacheKey(
            postsFingerprint = ThreadPostListFingerprint(
                size = 2,
                firstPostId = "1",
                lastPostId = "2",
                rollingHash = 99L
            ),
            computationState = computationState,
            ngHeaders = listOf(" ID:abc "),
            ngWords = listOf(" ng ")
        )

        assertTrue(cacheKey.ngEnabled)
        assertEquals("foo", cacheKey.keyword)
        assertEquals(ThreadFilterSortOption.Replies, cacheKey.sortOption)

        val idleComputationState = resolveThreadFilterComputationState(
            uiState = ThreadFilterUiState(),
            selfPostIdentifiers = emptyList(),
            ngHeaders = emptyList(),
            ngWords = emptyList(),
            ngFilteringEnabled = false
        )
        assertFalse(idleComputationState.hasNgFilters)
        assertFalse(idleComputationState.hasThreadFilters)
        assertFalse(idleComputationState.shouldComputeFullPostFingerprint)
    }

    @Test
    fun threadHtmlTextSupport_decodesTagsBreaksAndEntities() {
        val html = "<p>abc&lt;1&gt;<br>line2 &#x1F600;</p><span>&amp; &#39;x&#39;</span>"

        assertEquals(
            listOf("abc<1>", "line2 😀", "", "& 'x'"),
            messageHtmlToLines(html)
        )
        assertEquals("abc<1>\nline2 😀\n\n& 'x'", messageHtmlToPlainText(html))
    }

    @Test
    fun threadReplyDialogHelpers_reduceVisibilityDraftQuoteAndSubmitState() {
        val initial = emptyThreadReplyDialogState()
        val opened = openThreadReplyDialog(
            state = initial.copy(
                draft = ThreadReplyDraft(comment = "body")
            ),
            lastUsedDeleteKey = "stored"
        )
        assertTrue(opened.isVisible)
        assertEquals("stored", opened.draft.password)

        val updated = updateThreadReplyDialogDraft(opened) { draft ->
            draft.copy(subject = "subj")
        }
        assertEquals("subj", updated.draft.subject)
        assertEquals(
            ThreadReplyDialogState(
                isVisible = true,
                draft = ThreadReplyDraft(comment = "body", password = "stored", name = "name")
            ),
            updateThreadReplyDialogName(opened, "name")
        )
        assertEquals("name", updateThreadReplyDialogName(updated, "name").draft.name)
        assertEquals("sage", updateThreadReplyDialogEmail(updated, "sage").draft.email)
        assertEquals("subj2", updateThreadReplyDialogSubject(updated, "subj2").draft.subject)
        assertEquals("body2", updateThreadReplyDialogComment(updated, "body2").draft.comment)
        assertEquals("pass", updateThreadReplyDialogPassword(updated, "pass").draft.password)
        assertEquals(
            ImageData(byteArrayOf(9), "b.jpg"),
            updateThreadReplyDialogImage(updated, ImageData(byteArrayOf(9), "b.jpg")).draft.imageData
        )

        val quoted = appendQuoteSelectionToReplyDialog(updated, listOf(">No.1"))
        assertEquals("body\n>No.1\n", quoted?.draft?.comment)
        assertTrue(quoted?.isVisible == true)

        val submitted = completeThreadReplyDialogSubmit(quoted)
        assertFalse(submitted.isVisible)
        assertEquals("", submitted.draft.subject)
        assertEquals("", submitted.draft.comment)
        assertEquals("", submitted.draft.password)

        val dismissed = dismissThreadReplyDialog(updated)
        assertFalse(dismissed.isVisible)
        assertEquals(ThreadReplyDraft(), clearThreadReplyDialog(dismissed).draft)

        assertEquals(
            "削除キーを入力してください",
            resolveThreadDeleteDialogSubmitState(
                password = "",
                imageOnly = false
            ).validationMessage
        )
        val deleteSubmitState = resolveThreadDeleteDialogSubmitState(
            password = " pass ",
            imageOnly = true
        )
        assertEquals("pass", deleteSubmitState.confirmState?.normalizedPassword)
        assertEquals(true, deleteSubmitState.confirmState?.imageOnly)

        assertEquals(
            "コメントを入力してください",
            resolveThreadReplyDialogSubmitState(
                emptyThreadReplyDialogState().copy(
                    draft = ThreadReplyDraft(password = "pass")
                )
            ).validationMessage
        )
        val replySubmitState = resolveThreadReplyDialogSubmitState(
            updated.copy(
                draft = updated.draft.copy(
                    comment = "comment",
                    password = " pass "
                )
            )
        )
        assertEquals("pass", replySubmitState.normalizedPassword)
        assertFalse(replySubmitState.dismissedState?.isVisible ?: true)
        assertFalse(replySubmitState.completedState?.isVisible ?: true)
    }

    @Test
    fun threadReplyAndFilterCallbackBuilders_bindReducersToMutableState() {
        var replyState = emptyThreadReplyDialogState().copy(isVisible = true)
        val replyCallbacks = buildThreadReplyDialogCallbacks(
            currentState = { replyState },
            setState = { replyState = it }
        )
        replyCallbacks.onCommentChange("comment")
        replyCallbacks.onNameChange("name")
        replyCallbacks.onEmailChange("sage")
        replyCallbacks.onSubjectChange("subject")
        replyCallbacks.onPasswordChange("pass")
        replyCallbacks.onImageSelected(ImageData(byteArrayOf(1), "a.jpg"))
        assertEquals("comment", replyState.draft.comment)
        assertEquals("name", replyState.draft.name)
        assertEquals("sage", replyState.draft.email)
        assertEquals("subject", replyState.draft.subject)
        assertEquals("pass", replyState.draft.password)
        assertEquals(ImageData(byteArrayOf(1), "a.jpg"), replyState.draft.imageData)

        replyCallbacks.onClear()
        assertEquals(ThreadReplyDraft(), replyState.draft)
        replyCallbacks.onDismiss()
        assertFalse(replyState.isVisible)

        var filterState = ThreadFilterUiState()
        var dismissed = false
        val filterCallbacks = buildThreadFilterSheetCallbacks(
            currentState = { filterState },
            setState = { filterState = it },
            onDismiss = { dismissed = true }
        )
        filterCallbacks.onOptionToggle(ThreadFilterOption.HighSaidane)
        filterCallbacks.onKeywordChange("abc")
        assertEquals(setOf(ThreadFilterOption.HighSaidane), filterState.options)
        assertEquals(ThreadFilterSortOption.Saidane, filterState.sortOption)
        assertEquals("abc", filterState.keyword)

        filterCallbacks.onClear()
        assertEquals(ThreadFilterUiState(), filterState)
        filterCallbacks.onDismiss()
        assertTrue(dismissed)
    }

    @Test
    fun threadStateBindings_andNgMutationCallbacks_bindMutableState() {
        var replyName = ""
        var replyEmail = ""
        var replySubject = ""
        var replyComment = ""
        var replyPassword = ""
        var replyImageData: ImageData? = null
        val replyDraftBinding = buildThreadReplyDraftBinding(
            currentName = { replyName },
            currentEmail = { replyEmail },
            currentSubject = { replySubject },
            currentComment = { replyComment },
            currentPassword = { replyPassword },
            currentImageData = { replyImageData },
            setName = { replyName = it },
            setEmail = { replyEmail = it },
            setSubject = { replySubject = it },
            setComment = { replyComment = it },
            setPassword = { replyPassword = it },
            setImageData = { replyImageData = it }
        )
        replyDraftBinding.setDraft(
            ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = "pass",
                imageData = ImageData(byteArrayOf(1), "a.jpg")
            )
        )
        assertEquals(
            ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = "pass",
                imageData = ImageData(byteArrayOf(1), "a.jpg")
            ),
            replyDraftBinding.currentDraft()
        )

        var replyVisible = false
        val replyDialogBinding = buildThreadReplyDialogStateBinding(
            isVisible = { replyVisible },
            setVisible = { replyVisible = it },
            draftBinding = replyDraftBinding
        )
        replyDialogBinding.setState(
            ThreadReplyDialogState(
                isVisible = true,
                draft = ThreadReplyDraft(comment = "body")
            )
        )
        assertTrue(replyDialogBinding.currentState().isVisible)
        assertEquals("body", replyDialogBinding.currentState().draft.comment)

        var selectedOptions = emptySet<ThreadFilterOption>()
        var sortOption: ThreadFilterSortOption? = null
        var keyword = ""
        val filterBinding = buildThreadFilterUiStateBinding(
            currentOptions = { selectedOptions },
            currentSortOption = { sortOption },
            currentKeyword = { keyword },
            setOptions = { selectedOptions = it },
            setSortOption = { sortOption = it },
            setKeyword = { keyword = it }
        )
        filterBinding.setState(
            ThreadFilterUiState(
                options = setOf(ThreadFilterOption.HighSaidane),
                sortOption = ThreadFilterSortOption.Saidane,
                keyword = "abc"
            )
        )
        assertEquals(
            ThreadFilterUiState(
                options = setOf(ThreadFilterOption.HighSaidane),
                sortOption = ThreadFilterSortOption.Saidane,
                keyword = "abc"
            ),
            filterBinding.currentState()
        )

        var persistedHeaders = emptyList<String>()
        var persistedWords = emptyList<String>()
        var filteringEnabled = true
        val shownMessages = mutableListOf<String>()
        val ngCallbacks = buildThreadNgMutationCallbacks(
            currentHeaders = { persistedHeaders },
            currentWords = { persistedWords },
            isFilteringEnabled = { filteringEnabled },
            setFilteringEnabled = { filteringEnabled = it },
            persistHeaders = { persistedHeaders = it },
            persistWords = { persistedWords = it },
            showMessage = { shownMessages += it }
        )
        ngCallbacks.onAddHeader("abc")
        ngCallbacks.onAddWord("def")
        ngCallbacks.onToggleFiltering()

        assertEquals(listOf("abc"), persistedHeaders)
        assertEquals(listOf("def"), persistedWords)
        assertFalse(filteringEnabled)
        assertEquals(
            listOf(
                "NGヘッダーを追加しました",
                "NGワードを追加しました",
                "NG表示を無効にしました"
            ),
            shownMessages
        )
    }

}
