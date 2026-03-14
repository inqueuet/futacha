package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
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

class ThreadScreenLogicTest {
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
            onBack = { navigatedBack = true },
            onRefreshThread = {}
        )
        backBindings.onBackPressed()
        assertTrue(navigatedBack)
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

    @Test
    fun threadSettingsAndReadAloudCallbackBuilders_routeActionsAndControls() {
        var dismissed = false
        var appliedSettingsAction: ThreadSettingsActionState? = null
        var openedNg = false
        var openedExternal = false
        var toggledPrivacy = false
        var delegatedMenuEntryId: ThreadMenuEntryId? = null
        val settingsCallbacks = buildThreadSettingsSheetCallbacks(
            onDismiss = { dismissed = true },
            onApplyActionState = { appliedSettingsAction = it },
            onOpenNgManagement = { openedNg = true },
            onOpenExternalApp = { openedExternal = true },
            onTogglePrivacy = { toggledPrivacy = true },
            onDelegateToMainActionHandler = { delegatedMenuEntryId = it }
        )

        settingsCallbacks.onAction(ThreadMenuEntryId.NgManagement)
        assertEquals(
            ThreadSettingsActionState(showNgManagement = true),
            appliedSettingsAction
        )
        assertTrue(openedNg)

        settingsCallbacks.onAction(ThreadMenuEntryId.ExternalApp)
        assertTrue(openedExternal)
        settingsCallbacks.onAction(ThreadMenuEntryId.Privacy)
        assertTrue(toggledPrivacy)
        settingsCallbacks.onAction(ThreadMenuEntryId.Save)
        assertEquals(ThreadMenuEntryId.Save, delegatedMenuEntryId)
        settingsCallbacks.onDismiss()
        assertTrue(dismissed)

        val seekCalls = mutableListOf<Pair<Int, Boolean>>()
        var played = false
        var paused = false
        var stopped = false
        var stoppedMessageShown = false
        var readAloudDismissed = false
        var visibleIndex = 3
        val readAloudCallbacks = buildThreadReadAloudControlCallbacks(
            firstVisibleSegmentIndex = { visibleIndex },
            onSeekToIndex = { index, shouldScroll -> seekCalls += index to shouldScroll },
            onPlay = { played = true },
            onPause = { paused = true },
            onStop = { stopped = true },
            onShowStoppedMessage = { stoppedMessageShown = true },
            onDismiss = { readAloudDismissed = true }
        )

        readAloudCallbacks.onSeek(1)
        readAloudCallbacks.onSeekToVisible()
        visibleIndex = -1
        readAloudCallbacks.onSeekToVisible()
        readAloudCallbacks.onPlay()
        readAloudCallbacks.onPause()
        readAloudCallbacks.onStop()
        readAloudCallbacks.onDismiss()

        assertEquals(listOf(1 to true, 3 to true), seekCalls)
        assertTrue(played)
        assertTrue(paused)
        assertTrue(stopped)
        assertTrue(stoppedMessageShown)
        assertTrue(readAloudDismissed)
    }

    @Test
    fun threadNgAndSaveProgressCallbackBuilders_forwardActions() {
        var ngDismissed = false
        val ngActions = mutableListOf<String>()
        val ngCallbacks = buildThreadNgManagementCallbacks(
            onDismiss = { ngDismissed = true },
            onAddHeader = { ngActions += "addHeader:$it" },
            onAddWord = { ngActions += "addWord:$it" },
            onRemoveHeader = { ngActions += "removeHeader:$it" },
            onRemoveWord = { ngActions += "removeWord:$it" },
            onToggleFiltering = { ngActions += "toggle" }
        )
        ngCallbacks.onAddHeader("h")
        ngCallbacks.onAddWord("w")
        ngCallbacks.onRemoveHeader("h")
        ngCallbacks.onRemoveWord("w")
        ngCallbacks.onToggleFiltering()
        ngCallbacks.onDismiss()

        assertEquals(
            listOf("addHeader:h", "addWord:w", "removeHeader:h", "removeWord:w", "toggle"),
            ngActions
        )
        assertTrue(ngDismissed)

        var saveDismissed = false
        var saveCancelled = false
        val saveCallbacks = buildThreadSaveProgressDialogCallbacks(
            onDismissRequest = { saveDismissed = true },
            onCancelRequest = { saveCancelled = true }
        )
        saveCallbacks.onDismissRequest()
        saveCallbacks.onCancelRequest()

        assertTrue(saveDismissed)
        assertTrue(saveCancelled)
    }

    @Test
    fun threadGlobalSettingsAndCookieCallbacks_forwardActions() {
        var backed = false
        var backgroundRefreshEnabled: Boolean? = null
        var lightweightEnabled: Boolean? = null
        var manualDirectory: String? = null
        var saveDirectorySelection: com.valoser.futacha.shared.util.SaveDirectorySelection? = null
        var pickerOpened = false
        var cookieManagerOpened = false
        var selectedFileManager: Pair<String, String>? = null
        var clearedFileManager = false
        var updatedThreadMenuEntries: List<com.valoser.futacha.shared.model.ThreadMenuEntryConfig>? = null
        var updatedCatalogNavEntries: List<com.valoser.futacha.shared.model.CatalogNavEntryConfig>? = null
        val threadMenuEntries = emptyList<com.valoser.futacha.shared.model.ThreadMenuEntryConfig>()
        val catalogNavEntries = emptyList<com.valoser.futacha.shared.model.CatalogNavEntryConfig>()
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = { backgroundRefreshEnabled = it },
            onLightweightModeChanged = { lightweightEnabled = it },
            onManualSaveDirectoryChanged = { manualDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onOpenSaveDirectoryPicker = { pickerOpened = true },
            onFileManagerSelected = { packageName, label ->
                selectedFileManager = packageName to label
            },
            onClearPreferredFileManager = { clearedFileManager = true },
            onThreadMenuEntriesChanged = { updatedThreadMenuEntries = it },
            onCatalogNavEntriesChanged = { updatedCatalogNavEntries = it }
        )

        val globalSettingsCallbacks = buildThreadGlobalSettingsCallbacks(
            onBack = { backed = true },
            onOpenCookieManager = { cookieManagerOpened = true },
            preferencesCallbacks = preferencesCallbacks
        )

        globalSettingsCallbacks.onBack()
        globalSettingsCallbacks.onBackgroundRefreshChanged(true)
        globalSettingsCallbacks.onLightweightModeChanged(true)
        globalSettingsCallbacks.onManualSaveDirectoryChanged("/tmp/x")
        globalSettingsCallbacks.onSaveDirectorySelectionChanged(
            com.valoser.futacha.shared.util.SaveDirectorySelection.PICKER
        )
        globalSettingsCallbacks.onOpenSaveDirectoryPicker?.invoke()
        globalSettingsCallbacks.onOpenCookieManager?.invoke()
        globalSettingsCallbacks.onFileManagerSelected?.invoke("pkg", "label")
        globalSettingsCallbacks.onClearPreferredFileManager?.invoke()
        globalSettingsCallbacks.onThreadMenuEntriesChanged(threadMenuEntries)
        globalSettingsCallbacks.onCatalogNavEntriesChanged(catalogNavEntries)

        assertTrue(backed)
        assertEquals(true, backgroundRefreshEnabled)
        assertEquals(true, lightweightEnabled)
        assertEquals("/tmp/x", manualDirectory)
        assertEquals(com.valoser.futacha.shared.util.SaveDirectorySelection.PICKER, saveDirectorySelection)
        assertTrue(pickerOpened)
        assertTrue(cookieManagerOpened)
        assertEquals("pkg" to "label", selectedFileManager)
        assertTrue(clearedFileManager)
        assertEquals(threadMenuEntries, updatedThreadMenuEntries)
        assertEquals(catalogNavEntries, updatedCatalogNavEntries)

        var cookieBacked = false
        val cookieCallbacks = buildThreadCookieManagementCallbacks(
            onBack = { cookieBacked = true }
        )
        cookieCallbacks.onBack()
        assertTrue(cookieBacked)
    }

    @Test
    fun threadScreenBindingSupport_wrapsCallbackBuilders() {
        var replyState = emptyThreadReplyDialogState().copy(isVisible = true)
        val replyCallbacks = buildThreadReplyDialogCallbacks(
            currentState = { replyState },
            setState = { replyState = it }
        )
        replyCallbacks.onCommentChange("body")
        assertEquals("body", replyState.draft.comment)

        var filterState = ThreadFilterUiState()
        val filterCallbacks = buildThreadFilterSheetCallbacks(
            currentState = { filterState },
            setState = { filterState = it },
            onDismiss = {}
        )
        filterCallbacks.onKeywordChange("abc")
        assertEquals("abc", filterState.keyword)

        var saveCancelled = false
        val saveProgressCallbacks = buildThreadSaveProgressDialogCallbacks(
            onDismissRequest = {},
            onCancelRequest = { saveCancelled = true }
        )
        saveProgressCallbacks.onCancelRequest()
        assertTrue(saveCancelled)

        var cookieBacked = false
        val cookieCallbacks = buildThreadCookieManagementCallbacks(
            onBack = { cookieBacked = true }
        )
        cookieCallbacks.onBack()
        assertTrue(cookieBacked)
    }

    @Test
    fun threadScreenStateBindingsSupport_bundlesMessageNgAndFormBindings() = runBlocking {
        var fallbackHeaders = emptyList<String>()
        var fallbackWords = emptyList<String>()
        var filteringEnabled = true
        val messageNgBindings = buildThreadScreenMessageNgBindings(
            ThreadScreenMessageNgInputs(
                coroutineScope = this,
                showSnackbar = {},
                screenPreferencesCallbacks = ScreenPreferencesCallbacks(),
                stateStore = null,
                onFallbackHeadersChanged = { fallbackHeaders = it },
                onFallbackWordsChanged = { fallbackWords = it },
                currentHeaders = { fallbackHeaders },
                currentWords = { fallbackWords },
                isFilteringEnabled = { filteringEnabled },
                setFilteringEnabled = { filteringEnabled = it }
            )
        )
        messageNgBindings.ngMutationCallbacks.onAddHeader("header")
        messageNgBindings.ngMutationCallbacks.onAddWord("word")
        messageNgBindings.ngMutationCallbacks.onToggleFiltering()
        yield()

        assertEquals(listOf("header"), fallbackHeaders)
        assertEquals(listOf("word"), fallbackWords)
        assertFalse(filteringEnabled)

        var filterOptions = emptySet<ThreadFilterOption>()
        var filterSortOption: ThreadFilterSortOption? = null
        var filterKeyword = ""
        var replyName = ""
        var replyEmail = ""
        var replySubject = ""
        var replyComment = ""
        var replyPassword = ""
        var replyImageData: ImageData? = null
        var isReplyDialogVisible = false
        val formBindings = buildThreadScreenFormBindings(
            ThreadScreenFormInputs(
                filterInputs = ThreadScreenFilterBindingInputs(
                    currentOptions = { filterOptions },
                    currentSortOption = { filterSortOption },
                    currentKeyword = { filterKeyword },
                    setOptions = { filterOptions = it },
                    setSortOption = { filterSortOption = it },
                    setKeyword = { filterKeyword = it }
                ),
                replyDraftInputs = ThreadScreenReplyDraftInputs(
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
                ),
                replyDialogInputs = ThreadScreenReplyDialogInputs(
                    isVisible = { isReplyDialogVisible },
                    setVisible = { isReplyDialogVisible = it }
                )
            )
        )
        val imageData = ImageData(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "a.png"
        )
        formBindings.threadFilterBinding.setState(
            ThreadFilterUiState(
                options = setOf(ThreadFilterOption.Image, ThreadFilterOption.Keyword),
                sortOption = ThreadFilterSortOption.Replies,
                keyword = "abc"
            )
        )
        formBindings.replyDialogBinding.setState(
            ThreadReplyDialogState(
                isVisible = true,
                draft = ThreadReplyDraft(
                    name = "n",
                    email = "e",
                    subject = "s",
                    comment = "c",
                    password = "p",
                    imageData = imageData
                )
            )
        )

        assertEquals(setOf(ThreadFilterOption.Image, ThreadFilterOption.Keyword), filterOptions)
        assertEquals(ThreadFilterSortOption.Replies, filterSortOption)
        assertEquals("abc", filterKeyword)
        assertTrue(isReplyDialogVisible)
        assertEquals("n", replyName)
        assertEquals("e", replyEmail)
        assertEquals("s", replySubject)
        assertEquals("c", replyComment)
        assertEquals("p", replyPassword)
        assertEquals(imageData, replyImageData)
    }

    @Test
    fun threadScreenStateBindingsSupport_bundlesMessageNgAndFormBuildersTogether() = runBlocking {
        var fallbackHeaders = emptyList<String>()
        var fallbackWords = emptyList<String>()
        var filteringEnabled = true
        var filterOptions = emptySet<ThreadFilterOption>()
        var filterSortOption: ThreadFilterSortOption? = null
        var filterKeyword = ""
        var replyName = ""
        var replyEmail = ""
        var replySubject = ""
        var replyComment = ""
        var replyPassword = ""
        var replyImageData: ImageData? = null
        var isReplyDialogVisible = false

        val bundle = buildThreadScreenMessageFormBindingsBundle(
            ThreadScreenMessageFormInputs(
                messageNgInputs = ThreadScreenMessageNgInputs(
                    coroutineScope = this,
                    showSnackbar = {},
                    screenPreferencesCallbacks = ScreenPreferencesCallbacks(),
                    stateStore = null,
                    onFallbackHeadersChanged = { fallbackHeaders = it },
                    onFallbackWordsChanged = { fallbackWords = it },
                    currentHeaders = { fallbackHeaders },
                    currentWords = { fallbackWords },
                    isFilteringEnabled = { filteringEnabled },
                    setFilteringEnabled = { filteringEnabled = it }
                ),
                formInputs = ThreadScreenFormInputs(
                    filterInputs = ThreadScreenFilterBindingInputs(
                        currentOptions = { filterOptions },
                        currentSortOption = { filterSortOption },
                        currentKeyword = { filterKeyword },
                        setOptions = { filterOptions = it },
                        setSortOption = { filterSortOption = it },
                        setKeyword = { filterKeyword = it }
                    ),
                    replyDraftInputs = ThreadScreenReplyDraftInputs(
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
                    ),
                    replyDialogInputs = ThreadScreenReplyDialogInputs(
                        isVisible = { isReplyDialogVisible },
                        setVisible = { isReplyDialogVisible = it }
                    )
                )
            )
        )

        bundle.messageNgBindings.ngMutationCallbacks.onAddHeader("header")
        bundle.formBindings.threadFilterBinding.setState(
            ThreadFilterUiState(
                options = setOf(ThreadFilterOption.Image),
                sortOption = ThreadFilterSortOption.Saidane,
                keyword = "abc"
            )
        )
        bundle.formBindings.replyDialogBinding.setState(
            ThreadReplyDialogState(
                isVisible = true,
                draft = ThreadReplyDraft(comment = "body")
            )
        )
        yield()

        assertEquals(listOf("header"), fallbackHeaders)
        assertTrue(filteringEnabled)
        assertEquals(setOf(ThreadFilterOption.Image), filterOptions)
        assertEquals(ThreadFilterSortOption.Saidane, filterSortOption)
        assertEquals("abc", filterKeyword)
        assertTrue(isReplyDialogVisible)
        assertEquals("body", replyComment)
    }

    @Test
    fun threadScreenRunnerBindingsSupport_bundlesRunnerBuilders() {
        val bindings = buildThreadScreenRunnerBindings(
            repository = FakeBoardRepository(),
            httpClient = null,
            fileSystem = null,
            threadId = "123",
            threadTitle = "title",
            boardUrl = "https://example.com/test",
            effectiveBoardUrl = "https://example.com/test",
            threadUrlOverride = "https://example.com/test/res/123.htm",
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            offlineLookupContext = OfflineThreadLookupContext(
                boardIdCandidates = listOf("test"),
                expectedBoardKeys = setOf("test")
            ),
            offlineSources = emptyList(),
            currentThreadUrlOverride = { "https://example.com/test/res/123.htm" }
        )

        assertEquals("123", bindings.loadRunnerConfig.threadId)
        assertEquals("https://example.com/test", bindings.loadRunnerConfig.effectiveBoardUrl)
        assertEquals("https://example.com/test/res/123.htm", bindings.loadRunnerConfig.threadUrlOverride)
        assertTrue(bindings.loadRunnerConfig.allowOfflineFallback)
        assertEquals(ARCHIVE_FALLBACK_TIMEOUT_MS, bindings.loadRunnerConfig.archiveFallbackTimeoutMillis)
        assertEquals(OFFLINE_FALLBACK_TIMEOUT_MS, bindings.loadRunnerConfig.offlineFallbackTimeoutMillis)
        assertNull(bindings.singleMediaSaveRunnerCallbacks)
        assertNotNull(bindings.deleteByUserActionCallbacks)
        assertNotNull(bindings.replyActionCallbacks)
        assertNotNull(bindings.loadRunnerCallbacks)
    }

    @Test
    fun threadScreenEnvironmentSupport_buildsEnvironmentBundle() {
        val fileSystem = InMemoryFileSystem()
        val autoSaveRepository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = AUTO_SAVE_DIRECTORY
        )
        val historyEntry = ThreadHistoryEntry(
            threadId = "123",
            title = "Thread",
            titleImageUrl = "",
            boardId = "b",
            boardName = "Board",
            boardUrl = "https://may.2chan.net/b/res/123.htm",
            replyCount = 10,
            lastVisitedEpochMillis = 1L,
            lastReadItemIndex = 4,
            lastReadItemOffset = 20
        )

        val bundle = buildThreadScreenEnvironmentBundle(
            repository = null,
            autoSavedThreadRepository = autoSaveRepository,
            fileSystem = fileSystem,
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            history = listOf(historyEntry),
            threadId = "123",
            board = BoardSummary(
                id = "b",
                name = "Board",
                category = "cat",
                url = "https://may.2chan.net/b/",
                description = "desc"
            ),
            resolvedThreadUrlOverride = "https://may.2chan.net/b/res/123.htm"
        )

        assertTrue(bundle.activeRepository is FakeBoardRepository)
        assertEquals("https://may.2chan.net/b", bundle.effectiveBoardUrl)
        assertEquals(historyEntry, bundle.initialHistoryEntry)
        assertSame(autoSaveRepository, bundle.autoSaveRepository)
        assertNotNull(bundle.manualSaveRepository)
        assertNotNull(bundle.legacyManualSaveRepository)
        assertEquals(listOf("b", null), bundle.offlineLookupContext.boardIdCandidates)
        assertEquals(setOf("https://may.2chan.net/b"), bundle.offlineLookupContext.expectedBoardKeys)
        assertEquals(AUTO_SAVE_DIRECTORY, bundle.offlineSources[0].baseDirectory)
        assertEquals("/tmp/futacha", bundle.offlineSources[1].baseDirectory)
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, bundle.offlineSources[2].baseDirectory)
    }

    @Test
    fun threadScreenAsyncBindingsSupport_bundlesRunnerSaveAndLoadBuilders() {
        runBlocking {
        var autoSaveJob: Job? = null
        var manualSaveJob: Job? = null
        var singleMediaSaveJob: Job? = null
        var refreshThreadJob: Job? = null
        var manualRefreshGeneration = 0L
        var isRefreshing = false
        var isManualSaveInProgress = false
        var isSingleMediaSaveInProgress = false
        var saveProgress: com.valoser.futacha.shared.model.SaveProgress? = null
        var lastAutoSaveTimestamp = 0L
        var isShowingOfflineCopy = false
        var uiState: ThreadUiState = ThreadUiState.Loading
        var shownMessage: String? = null
        var shownOptionalMessage: String? = null
        val runtimeBundle = buildThreadScreenAsyncRuntimeBindingsBundle(
            currentAutoSaveJob = { autoSaveJob },
            setAutoSaveJob = { autoSaveJob = it },
            currentLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp },
            setLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp = it },
            currentIsShowingOfflineCopy = { isShowingOfflineCopy },
            currentManualSaveJob = { manualSaveJob },
            setManualSaveJob = { manualSaveJob = it },
            setIsManualSaveInProgress = { isManualSaveInProgress = it },
            currentIsManualSaveInProgress = { isManualSaveInProgress },
            currentIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress },
            setSaveProgress = { saveProgress = it },
            currentUiState = { uiState },
            showMessage = { shownMessage = it },
            applySaveErrorState = {},
            onOpenSaveDirectoryPicker = null,
            currentSingleMediaSaveJob = { singleMediaSaveJob },
            setSingleMediaSaveJob = { singleMediaSaveJob = it },
            setIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress = it },
            showOptionalMessage = { shownOptionalMessage = it },
            currentRefreshThreadJob = { refreshThreadJob },
            setRefreshThreadJob = { refreshThreadJob = it },
            currentManualRefreshGeneration = { manualRefreshGeneration },
            setManualRefreshGeneration = { manualRefreshGeneration = it },
            setIsRefreshing = { isRefreshing = it },
            setUiState = { uiState = it },
            setResolvedThreadUrlOverride = {},
            setIsShowingOfflineCopy = { isShowingOfflineCopy = it },
            onHistoryEntryUpdated = {},
            onRestoreManualRefreshScroll = { _, _, _ -> }
        )

        val bundle = buildThreadScreenAsyncBindingsBundle(
            coroutineScope = this,
            repository = FakeBoardRepository(),
            history = emptyList(),
            threadId = "123",
            threadTitle = "title",
            board = BoardSummary(
                id = "test",
                name = "Test",
                category = "cat",
                url = "https://example.com/test",
                description = "desc"
            ),
            boardUrl = "https://example.com/test",
            effectiveBoardUrl = "https://example.com/test",
            threadUrlOverride = "https://example.com/test/res/123.htm",
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            offlineLookupContext = OfflineThreadLookupContext(
                boardIdCandidates = listOf("test"),
                expectedBoardKeys = setOf("test")
            ),
            offlineSources = emptyList(),
            currentThreadUrlOverride = { "https://example.com/test/res/123.htm" },
            httpClient = null,
            fileSystem = null,
            autoSaveRepository = null,
            manualSaveRepository = null,
            minAutoSaveIntervalMillis = AUTO_SAVE_INTERVAL_MS,
            autoSaveStateBindings = runtimeBundle.autoSaveStateBindings,
            manualSaveStateBindings = runtimeBundle.manualSaveStateBindings,
            manualSaveDirectory = "/manual",
            manualSaveLocation = null,
            resolvedManualSaveDirectory = null,
            requiresManualLocationSelection = false,
            manualSaveCallbacks = runtimeBundle.manualSaveCallbacks,
            singleMediaSaveStateBindings = runtimeBundle.singleMediaSaveStateBindings,
            singleMediaSaveCallbacks = runtimeBundle.singleMediaSaveCallbacks,
            loadStateBindings = runtimeBundle.loadStateBindings,
            loadUiCallbacks = runtimeBundle.loadUiCallbacks
        )

        assertEquals("123", bundle.runnerBindings.loadRunnerConfig.threadId)
        assertNotNull(bundle.saveExecutionBindings.autoSaveBindings)
        assertNotNull(bundle.saveExecutionBindings.manualSaveBindings)
        assertNotNull(bundle.saveExecutionBindings.singleMediaSaveBindings)
        assertNotNull(bundle.loadBindings)
        runtimeBundle.manualSaveCallbacks.showMessage("msg")
        runtimeBundle.singleMediaSaveCallbacks.showOptionalMessage("opt")
        assertEquals("msg", shownMessage)
        assertEquals("opt", shownOptionalMessage)
        }
    }

    @Test
    fun threadScreenAsyncBindingsSupport_buildsLoadUiCallbacks() = runBlocking {
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "1",
                    author = null,
                    subject = "subject",
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        val successState = ThreadUiState.Success(page)
        val historyEntry = ThreadHistoryEntry(
            threadId = "123",
            boardId = "b",
            title = "title",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 1
        )
        var uiState: ThreadUiState = ThreadUiState.Loading
        var updatedHistory: ThreadHistoryEntry? = null
        var shownMessage: String? = null
        var restoredScroll: Triple<ThreadUiState.Success, Int, Int>? = null
        val callbacks = buildThreadScreenLoadUiCallbacks(
            onUiStateChanged = { uiState = it },
            onHistoryEntryUpdated = { updatedHistory = it },
            onShowOptionalMessage = { shownMessage = it },
            onRestoreManualRefreshScroll = { state, index, offset ->
                restoredScroll = Triple(state, index, offset)
            }
        )

        callbacks.onManualRefreshSuccess(
            ThreadLoadUiOutcome(
                uiState = successState,
                historyEntry = historyEntry,
                snackbarMessage = "updated"
            ),
            4,
            8
        )
        assertEquals(successState, uiState)
        assertEquals(historyEntry, updatedHistory)
        assertEquals("updated", shownMessage)
        assertEquals(Triple(successState, 4, 8), restoredScroll)

        shownMessage = null
        callbacks.onInitialLoadFailure(
            ThreadLoadUiOutcome(
                uiState = ThreadUiState.Error("err"),
                snackbarMessage = "load failed"
            )
        )
        assertEquals(ThreadUiState.Error("err"), uiState)
        assertEquals("load failed", shownMessage)
    }

    @Test
    fun threadScreenLoadBindingsSupport_runsRefreshControllers() = runBlocking {
        var refreshThreadJob: Job? = null
        var manualRefreshGeneration = 0L
        var isRefreshing = false
        var uiState: ThreadUiState = ThreadUiState.Error("initial")
        var resolvedThreadUrlOverride: String? = null
        var isShowingOfflineCopy = true
        val shownMessages = mutableListOf<String>()
        val historyUpdates = mutableListOf<ThreadHistoryEntry>()
        val manualRefreshRequests = mutableListOf<Pair<Int, Int>>()
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "1",
                    author = null,
                    subject = "subject",
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        val bindings = buildThreadScreenLoadBindings(
            coroutineScope = this,
            loadRunnerConfig = buildThreadLoadRunnerConfig(
                threadId = "123",
                effectiveBoardUrl = "https://example.com/test",
                threadUrlOverride = null,
                allowOfflineFallback = true,
                archiveFallbackTimeoutMillis = 1L,
                offlineFallbackTimeoutMillis = 1L
            ),
            loadRunnerCallbacks = ThreadLoadRunnerCallbacks(
                loadRemoteByUrl = { error("unexpected by-url load") },
                loadRemoteByBoard = { _, _ -> page },
                loadArchiveFallback = { error("unexpected archive fallback") },
                loadOfflineFallback = { error("unexpected offline fallback") }
            ),
            history = emptyList(),
            threadId = "123",
            threadTitle = "title",
            board = BoardSummary(
                id = "test",
                name = "Test",
                category = "cat",
                url = "https://example.com/test",
                description = "desc"
            ),
            stateBindings = ThreadScreenLoadStateBindings(
                currentRefreshThreadJob = { refreshThreadJob },
                setRefreshThreadJob = { refreshThreadJob = it },
                currentManualRefreshGeneration = { manualRefreshGeneration },
                setManualRefreshGeneration = { manualRefreshGeneration = it },
                setIsRefreshing = { isRefreshing = it },
                setUiState = { uiState = it },
                setResolvedThreadUrlOverride = { resolvedThreadUrlOverride = it },
                setIsShowingOfflineCopy = { isShowingOfflineCopy = it }
            ),
            uiCallbacks = ThreadScreenLoadUiCallbacks(
                onManualRefreshSuccess = { outcome, savedIndex, savedOffset ->
                    manualRefreshRequests += savedIndex to savedOffset
                    outcome.uiState?.let { uiState = it }
                    outcome.historyEntry?.let(historyUpdates::add)
                    outcome.snackbarMessage?.let(shownMessages::add)
                },
                onManualRefreshFailure = { outcome ->
                    outcome.snackbarMessage?.let(shownMessages::add)
                },
                onInitialLoadSuccess = { outcome ->
                    outcome.uiState?.let { uiState = it }
                    outcome.historyEntry?.let(historyUpdates::add)
                    outcome.snackbarMessage?.let(shownMessages::add)
                },
                onInitialLoadFailure = { outcome ->
                    outcome.uiState?.let { uiState = it }
                    outcome.snackbarMessage?.let(shownMessages::add)
                }
            )
        )

        bindings.refreshThread()
        refreshThreadJob?.join()

        assertTrue(uiState is ThreadUiState.Success)
        assertFalse(isShowingOfflineCopy)
        assertNull(resolvedThreadUrlOverride)
        assertEquals(1, historyUpdates.size)
        assertTrue(shownMessages.isEmpty())
        assertNull(refreshThreadJob)

        bindings.startManualRefresh(4, 8)
        refreshThreadJob?.join()

        assertEquals(1L, manualRefreshGeneration)
        assertFalse(isRefreshing)
        assertEquals(listOf(4 to 8), manualRefreshRequests)
        assertEquals(2, historyUpdates.size)
        assertEquals(listOf("スレッドを更新しました"), shownMessages)
        assertNull(refreshThreadJob)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_runsAutoSaveController() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val autoSaveRepository = SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY)
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "test",
            boardName = "Test",
            title = "title",
            storageId = "storage",
            thumbnailPath = null,
            savedAt = 100L,
            postCount = 1,
            imageCount = 0,
            videoCount = 0,
            totalSize = 10L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )
        var autoSaveJob: Job? = null
        var lastAutoSaveTimestamp = 0L
        val indexedThreads = mutableListOf<SavedThread>()
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "1",
                    author = null,
                    subject = "subject",
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        val bindings = buildThreadScreenAutoSaveBindings(
            coroutineScope = this,
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS,
            stateBindings = ThreadScreenAutoSaveStateBindings(
                currentAutoSaveJob = { autoSaveJob },
                setAutoSaveJob = { autoSaveJob = it },
                currentLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp },
                setLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp = it },
                currentIsShowingOfflineCopy = { false }
            ),
            dependencies = ThreadScreenAutoSaveDependencies(
                autoSaveRepository = autoSaveRepository,
                httpClient = HttpClient(),
                fileSystem = fileSystem,
                threadId = "123",
                threadTitle = "title",
                board = BoardSummary(
                    id = "test",
                    name = "Test",
                    category = "cat",
                    url = "https://example.com/test",
                    description = "desc"
                ),
                effectiveBoardUrl = "https://example.com/test",
                currentTimeMillis = { 100_000L },
                buildSaveRuntime = { _, _ ->
                    buildThreadSaveRuntime(ThreadSaveService(HttpClient(), fileSystem))
                },
                performAutoSave = { config, _ ->
                    assertEquals("123", config.threadId)
                    ThreadAutoSaveRunResult(
                        completionState = ThreadAutoSaveCompletionState(
                            nextTimestampMillis = 200L,
                            savedThread = savedThread
                        )
                    )
                },
                indexSavedThread = { _, indexedThread, failureMessage ->
                    indexedThread?.let(indexedThreads::add)
                    assertEquals(buildThreadAutoSaveIndexFailureMessage("123"), failureMessage)
                }
            )
        )

        bindings.startAutoSave(page)
        autoSaveJob?.join()

        assertEquals(200L, lastAutoSaveTimestamp)
        assertEquals(listOf(savedThread), indexedThreads)
        assertNull(autoSaveJob)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_runsReadAloudControllers() = runBlocking {
        val segments = listOf(
            ReadAloudSegment(postIndex = 0, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 1, postId = "20", body = "b")
        )
        var state = ThreadReadAloudRuntimeState(
            job = null,
            status = ReadAloudStatus.Idle,
            currentIndex = 0,
            cancelRequestedByUser = false
        )
        val messages = mutableListOf<String>()
        val scrollTargets = mutableListOf<Int>()
        val spokenTexts = mutableListOf<String>()
        var cancelCalls = 0
        val bindings = buildThreadScreenReadAloudBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenReadAloudStateBindings(
                currentState = { state },
                setState = { state = it }
            ),
            callbacks = ThreadScreenReadAloudCallbacks(
                showMessage = { messages += "error:$it" },
                showOptionalMessage = { message ->
                    if (message != null) {
                        messages += message
                    }
                },
                scrollToPostIndex = { postIndex ->
                    scrollTargets += postIndex
                },
                speakText = { text ->
                    spokenTexts += text
                },
                cancelActiveReadAloud = {
                    cancelCalls += 1
                    val current = state
                    state = current.copy(cancelRequestedByUser = true)
                    current.job?.cancel()
                }
            ),
            dependencies = ThreadScreenReadAloudDependencies(
                currentSegments = { segments }
            )
        )

        bindings.startReadAloud()
        state.job?.join()

        assertEquals(listOf(0, 1), scrollTargets)
        assertEquals(listOf("a", "b"), spokenTexts)
        assertEquals(ReadAloudStatus.Idle, state.status)
        assertEquals(0, state.currentIndex)
        assertEquals(listOf(buildReadAloudCompletedMessage()), messages)

        state = state.copy(
            status = ReadAloudStatus.Paused(segments.first()),
            currentIndex = 0
        )
        bindings.seekReadAloudToIndex(1, true)
        state.job?.join()

        assertEquals(1, cancelCalls)
        assertEquals(listOf(0, 1, 1, 1), scrollTargets)
        assertEquals(listOf("a", "b", "b"), spokenTexts)
        assertEquals(ReadAloudStatus.Idle, state.status)
        assertEquals(0, state.currentIndex)
        assertEquals(
            listOf(
                buildReadAloudCompletedMessage(),
                buildReadAloudCompletedMessage()
            ),
            messages
        )
    }

    @Test
    fun threadScreenExecutionBindingsSupport_runsManualSaveController() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "test",
            boardName = "Test",
            title = "title",
            storageId = "storage",
            thumbnailPath = null,
            savedAt = 100L,
            postCount = 1,
            imageCount = 0,
            videoCount = 0,
            totalSize = 10L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )
        var manualSaveJob: Job? = null
        var isManualSaveInProgress = false
        var saveProgress: com.valoser.futacha.shared.model.SaveProgress? = null
        val shownMessages = mutableListOf<String>()
        val indexedThreads = mutableListOf<SavedThread>()
        var appliedErrorState: ThreadManualSaveErrorState? = null
        val bindings = buildThreadScreenManualSaveBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenManualSaveStateBindings(
                currentManualSaveJob = { manualSaveJob },
                setManualSaveJob = { manualSaveJob = it },
                setIsManualSaveInProgress = { isManualSaveInProgress = it },
                currentIsManualSaveInProgress = { isManualSaveInProgress },
                currentIsSingleMediaSaveInProgress = { false },
                setSaveProgress = { saveProgress = it },
                currentUiState = {
                    ThreadUiState.Success(
                        ThreadPage(
                            threadId = "123",
                            boardTitle = "board",
                            expiresAtLabel = null,
                            deletedNotice = null,
                            posts = listOf(
                                Post(
                                    id = "1",
                                    author = null,
                                    subject = "subject",
                                    timestamp = "now",
                                    messageHtml = "body",
                                    imageUrl = null,
                                    thumbnailUrl = null
                                )
                            )
                        )
                    )
                }
            ),
            dependencies = ThreadScreenManualSaveDependencies(
                manualSaveRepository = SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY),
                httpClient = HttpClient(),
                fileSystem = fileSystem,
                threadId = "123",
                threadTitle = "title",
                board = BoardSummary(
                    id = "test",
                    name = "Test",
                    category = "cat",
                    url = "https://example.com/test",
                    description = "desc"
                ),
                effectiveBoardUrl = "https://example.com/test",
                manualSaveDirectory = "/manual",
                manualSaveLocation = SaveLocation.Path("/manual"),
                resolvedManualSaveDirectory = "/manual",
                requiresManualLocationSelection = false,
                buildSaveRuntime = { _, _ ->
                    buildThreadSaveRuntime(ThreadSaveService(HttpClient(), fileSystem))
                },
                performManualSave = { config, _ ->
                    assertEquals("123", config.threadId)
                    ThreadManualSaveRunResult.Success(savedThread)
                },
                indexSavedThread = { _, indexedThread, failureMessage ->
                    indexedThread?.let(indexedThreads::add)
                    assertEquals(buildThreadManualSaveIndexFailureMessage("123"), failureMessage)
                }
            ),
            callbacks = ThreadScreenManualSaveCallbacks(
                showMessage = { shownMessages += it },
                applySaveErrorState = { appliedErrorState = it }
            )
        )

        bindings.handleThreadSaveRequest()
        manualSaveJob?.join()

        assertFalse(isManualSaveInProgress)
        assertNull(saveProgress)
        assertNull(manualSaveJob)
        assertNull(appliedErrorState)
        assertEquals(listOf(savedThread), indexedThreads)
        assertEquals(listOf("スレッドを保存しました: /manual/storage"), shownMessages)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_runsHistoryRefreshController() = runBlocking {
        var isHistoryRefreshing = false
        val shownMessages = mutableListOf<String>()
        var refreshCount = 0
        val bindings = buildThreadScreenHistoryRefreshBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenHistoryRefreshStateBindings(
                currentIsHistoryRefreshing = { isHistoryRefreshing },
                setIsHistoryRefreshing = { isHistoryRefreshing = it }
            ),
            onHistoryRefresh = { refreshCount += 1 },
            showMessage = { shownMessages += it }
        )

        bindings.handleHistoryRefresh()
        yield()

        assertEquals(1, refreshCount)
        assertFalse(isHistoryRefreshing)
        assertEquals(listOf(buildThreadHistoryRefreshSuccessMessage()), shownMessages)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_runsActionController() = runBlocking {
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        val shownMessages = mutableListOf<String>()
        val debugLogs = mutableListOf<String>()
        val infoLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        var successValue: String? = null
        val bindings = buildThreadScreenActionBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenActionStateBindings(
                currentActionInProgress = { actionInProgress },
                setActionInProgress = { actionInProgress = it },
                currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
                setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
            ),
            dependencies = ThreadScreenActionDependencies(
                currentTimeMillis = { 1_000L },
                busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
                showMessage = { shownMessages += it },
                onDebugLog = { debugLogs += it },
                onInfoLog = { infoLogs += it },
                onErrorLog = { message, _ -> errorLogs += message }
            )
        )

        val successJob = bindings.launch(
            successMessage = "ok",
            failurePrefix = "ng",
            onSuccess = { value: String -> successValue = value }
        ) {
            ThreadActionRunResult.Success("done")
        }
        successJob?.join()

        assertFalse(actionInProgress)
        assertEquals("done", successValue)
        assertEquals(listOf("ok"), shownMessages)
        assertTrue(debugLogs.single().contains("Starting thread action"))
        assertEquals(listOf("Thread action succeeded: ok"), infoLogs)
        assertTrue(errorLogs.isEmpty())

        actionInProgress = true
        shownMessages.clear()
        bindings.launch<String>(
            successMessage = "ok",
            failurePrefix = "ng"
        ) {
            error("should not launch while busy")
        }

        assertEquals(1_000L, lastBusyNoticeAtMillis)
        assertEquals(listOf(buildThreadActionBusyMessage()), shownMessages)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_runsSingleMediaSaveController() = runBlocking {
        var singleMediaSaveJob: Job? = null
        var isSingleMediaSaveInProgress = false
        val shownMessages = mutableListOf<String>()
        var appliedErrorState: ThreadManualSaveErrorState? = null
        val bindings = buildThreadScreenSingleMediaSaveBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenSingleMediaSaveStateBindings(
                currentSingleMediaSaveJob = { singleMediaSaveJob },
                setSingleMediaSaveJob = { singleMediaSaveJob = it },
                setIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress = it },
                currentIsManualSaveInProgress = { false },
                currentIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress }
            ),
            dependencies = ThreadScreenSingleMediaSaveDependencies(
                saveRunnerCallbacks = ThreadSingleMediaSaveRunnerCallbacks(
                    saveMedia = { error("unused") }
                ),
                boardId = "test",
                threadId = "123",
                manualSaveLocation = SaveLocation.Path("/manual"),
                manualSaveDirectory = "/manual",
                resolvedManualSaveDirectory = "/manual",
                requiresManualLocationSelection = false,
                hasStorageDependencies = true,
                performSingleMediaSave = { config, _ ->
                    assertEquals("https://example.com/src/a.jpg", config.mediaUrl)
                    ThreadSingleMediaSaveRunResult.Success(
                        SavedMediaFile(
                            fileName = "a.jpg",
                            relativePath = "images/a.jpg",
                            mediaType = SavedMediaType.IMAGE,
                            byteSize = 10L,
                            savedAtEpochMillis = 100L
                        )
                    )
                }
            ),
            callbacks = ThreadScreenSingleMediaSaveCallbacks(
                showOptionalMessage = { message ->
                    if (message != null) {
                        shownMessages += message
                    }
                },
                applySaveErrorState = { appliedErrorState = it },
                showMessage = { shownMessages += it }
            )
        )

        bindings.savePreviewMedia(
            MediaPreviewEntry(
                url = "https://example.com/src/a.jpg",
                mediaType = MediaType.Image,
                postId = "1",
                title = "title"
            )
        )
        singleMediaSaveJob?.join()

        assertFalse(isSingleMediaSaveInProgress)
        assertNull(singleMediaSaveJob)
        assertNull(appliedErrorState)
        assertEquals(listOf("画像を保存しました: /manual/images/a.jpg"), shownMessages)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_bundlesSaveControllers() = runBlocking {
        var manualSaveJob: Job? = null
        var autoSaveJob: Job? = null
        var singleMediaSaveJob: Job? = null
        var isManualSaveInProgress = false
        var isSingleMediaSaveInProgress = false
        var saveProgress: com.valoser.futacha.shared.model.SaveProgress? = null
        var lastAutoSaveTimestamp = 0L
        val shownMessages = mutableListOf<String>()

        val bundle = buildThreadScreenSaveExecutionBindingsBundle(
            coroutineScope = this,
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS,
            autoSaveStateBindings = ThreadScreenAutoSaveStateBindings(
                currentAutoSaveJob = { autoSaveJob },
                setAutoSaveJob = { autoSaveJob = it },
                currentLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp },
                setLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp = it },
                currentIsShowingOfflineCopy = { false }
            ),
            autoSaveDependencies = ThreadScreenAutoSaveDependencies(
                autoSaveRepository = null,
                httpClient = null,
                fileSystem = null,
                threadId = "123",
                threadTitle = "title",
                board = BoardSummary(
                    id = "test",
                    name = "Test",
                    category = "cat",
                    url = "https://example.com/test",
                    description = "desc"
                ),
                effectiveBoardUrl = "https://example.com/test"
            ),
            manualSaveStateBindings = ThreadScreenManualSaveStateBindings(
                currentManualSaveJob = { manualSaveJob },
                setManualSaveJob = { manualSaveJob = it },
                setIsManualSaveInProgress = { isManualSaveInProgress = it },
                currentIsManualSaveInProgress = { isManualSaveInProgress },
                currentIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress },
                setSaveProgress = { saveProgress = it },
                currentUiState = { ThreadUiState.Loading }
            ),
            manualSaveDependencies = ThreadScreenManualSaveDependencies(
                manualSaveRepository = null,
                httpClient = null,
                fileSystem = null,
                threadId = "123",
                threadTitle = "title",
                board = BoardSummary(
                    id = "test",
                    name = "Test",
                    category = "cat",
                    url = "https://example.com/test",
                    description = "desc"
                ),
                effectiveBoardUrl = "https://example.com/test",
                manualSaveDirectory = "/manual",
                manualSaveLocation = null,
                resolvedManualSaveDirectory = null,
                requiresManualLocationSelection = false
            ),
            manualSaveCallbacks = ThreadScreenManualSaveCallbacks(
                showMessage = { shownMessages += it },
                applySaveErrorState = {}
            ),
            singleMediaSaveStateBindings = ThreadScreenSingleMediaSaveStateBindings(
                currentSingleMediaSaveJob = { singleMediaSaveJob },
                setSingleMediaSaveJob = { singleMediaSaveJob = it },
                setIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress = it },
                currentIsManualSaveInProgress = { isManualSaveInProgress },
                currentIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress }
            ),
            singleMediaSaveDependencies = ThreadScreenSingleMediaSaveDependencies(
                saveRunnerCallbacks = null,
                boardId = "test",
                threadId = "123",
                manualSaveLocation = null,
                manualSaveDirectory = "/manual",
                resolvedManualSaveDirectory = null,
                requiresManualLocationSelection = false,
                hasStorageDependencies = false
            ),
            singleMediaSaveCallbacks = ThreadScreenSingleMediaSaveCallbacks(
                showOptionalMessage = { message -> message?.let(shownMessages::add) },
                applySaveErrorState = {},
                showMessage = { shownMessages += it }
            )
        )

        bundle.manualSaveBindings.handleThreadSaveRequest()

        assertNotNull(bundle.autoSaveBindings)
        assertNotNull(bundle.manualSaveBindings)
        assertNotNull(bundle.singleMediaSaveBindings)
        assertEquals(listOf(buildThreadSaveNotReadyMessage()), shownMessages)
        assertNull(manualSaveJob)
        assertNull(autoSaveJob)
        assertNull(singleMediaSaveJob)
        assertNull(saveProgress)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_bundlesActionControllers() = runBlocking {
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        var isHistoryRefreshing = false
        var refreshCount = 0
        val shownMessages = mutableListOf<String>()
        var readAloudState = ThreadReadAloudRuntimeState(
            job = null,
            status = ReadAloudStatus.Idle,
            currentIndex = 0,
            cancelRequestedByUser = false
        )

        val bundle = buildThreadScreenActionExecutionBindingsBundle(
            coroutineScope = this,
            actionStateBindings = ThreadScreenActionStateBindings(
                currentActionInProgress = { actionInProgress },
                setActionInProgress = { actionInProgress = it },
                currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
                setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
            ),
            actionDependencies = ThreadScreenActionDependencies(
                currentTimeMillis = { 1_000L },
                busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
                showMessage = { shownMessages += it },
                onDebugLog = {},
                onInfoLog = {},
                onErrorLog = { _, _ -> }
            ),
            historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
                currentIsHistoryRefreshing = { isHistoryRefreshing },
                setIsHistoryRefreshing = { isHistoryRefreshing = it }
            ),
            onHistoryRefresh = { refreshCount += 1 },
            showHistoryRefreshMessage = { shownMessages += it },
            readAloudStateBindings = ThreadScreenReadAloudStateBindings(
                currentState = { readAloudState },
                setState = { readAloudState = it }
            ),
            readAloudCallbacks = ThreadScreenReadAloudCallbacks(
                showMessage = { shownMessages += it },
                showOptionalMessage = { message -> message?.let(shownMessages::add) },
                scrollToPostIndex = {},
                speakText = {},
                cancelActiveReadAloud = {}
            ),
            readAloudDependencies = ThreadScreenReadAloudDependencies(
                currentSegments = { emptyList() }
            )
        )

        bundle.historyRefreshBindings.handleHistoryRefresh()
        yield()
        bundle.readAloudBindings.startReadAloud()

        assertNotNull(bundle.actionBindings)
        assertNotNull(bundle.historyRefreshBindings)
        assertNotNull(bundle.readAloudBindings)
        assertEquals(1, refreshCount)
        assertTrue(shownMessages.contains(buildThreadHistoryRefreshSuccessMessage()))
        assertTrue(shownMessages.contains(buildReadAloudNoTargetMessage()))
        assertEquals(ReadAloudStatus.Idle, readAloudState.status)
    }

    @Test
    fun threadScreenExecutionBindingsSupport_resolvesDeleteAndReplySubmitOutcomes() {
        val targetPost = Post(
            id = "55",
            author = null,
            subject = "subject",
            timestamp = "now",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        val deleteOutcome = resolveThreadScreenDeleteSubmitOutcome(
            overlayState = ThreadPostOverlayState(
                deleteDialogState = ThreadDeleteDialogState(
                    targetPost = targetPost,
                    password = " 1234 ",
                    imageOnly = true
                )
            ),
            targetPost = targetPost,
            boardUrl = "https://example.com/test",
            threadId = "123"
        )

        assertNull(deleteOutcome.validationMessage)
        assertEquals("1234", deleteOutcome.normalizedPassword)
        assertEquals("55", deleteOutcome.actionConfig?.postId)
        assertEquals(true, deleteOutcome.actionConfig?.imageOnly)
        assertEquals(null, deleteOutcome.nextOverlayState?.deleteDialogState?.targetPost)

        val replyState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = " 5678 "
            )
        )
        val replyOutcome = resolveThreadScreenReplySubmitOutcome(
            state = replyState,
            boardUrl = "https://example.com/test",
            threadId = "123"
        )

        assertNull(replyOutcome.validationMessage)
        assertEquals("5678", replyOutcome.normalizedPassword)
        assertEquals("123", replyOutcome.actionConfig?.threadId)
        assertEquals("comment", replyOutcome.actionConfig?.comment)
        assertFalse(replyOutcome.dismissedState?.isVisible ?: true)
        val completedState = requireNotNull(replyOutcome.completedState)
        assertFalse(completedState.isVisible)
        assertEquals("", completedState.draft.comment)
    }

    @Test
    fun threadScreenOverlayActionBindingsSupport_handlesDeleteAndReplySubmit() = runBlocking {
        val targetPost = Post(
            id = "55",
            author = null,
            subject = "subject",
            timestamp = "now",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        var overlayState = ThreadPostOverlayState(
            deleteDialogState = ThreadDeleteDialogState(
                targetPost = targetPost,
                password = " 1234 ",
                imageOnly = true
            ),
            actionSheetState = ThreadPostActionSelectionState(
                isActionSheetVisible = true,
                targetPost = targetPost
            ),
            quoteSelectionState = ThreadQuoteSelectionState(targetPost = targetPost)
        )
        var replyState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = " 5678 "
            )
        )
        val shownMessages = mutableListOf<String>()
        val updatedDeleteKeys = mutableListOf<String>()
        val deleteConfigs = mutableListOf<ThreadDeleteByUserActionConfig>()
        val replyConfigs = mutableListOf<ThreadReplyActionConfig>()
        var refreshCount = 0
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        val actionBindings = buildThreadScreenActionBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenActionStateBindings(
                currentActionInProgress = { actionInProgress },
                setActionInProgress = { actionInProgress = it },
                currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
                setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
            ),
            dependencies = ThreadScreenActionDependencies(
                currentTimeMillis = { 0L },
                busyNoticeIntervalMillis = 1_000L,
                showMessage = { shownMessages += it },
                onDebugLog = {},
                onInfoLog = {},
                onErrorLog = { _, _ -> }
            )
        )

        val callbacks = buildThreadScreenOverlayActionCallbacks(
            ThreadScreenOverlayActionInputs(
                currentPostOverlayState = { overlayState },
                setPostOverlayState = { overlayState = it },
                isSelfPost = { false },
                replyDialogBinding = ThreadReplyDialogStateBinding(
                    currentState = { replyState },
                    setState = { replyState = it }
                ),
                effectiveBoardUrl = "https://example.com/test",
                threadId = "123",
                boardId = "board",
                stateStore = null,
                coroutineScope = this,
                updateLastUsedDeleteKey = { updatedDeleteKeys += it },
                showMessage = { shownMessages += it },
                refreshThread = { refreshCount += 1 },
                actionBindings = actionBindings,
                threadDeleteByUserActionCallbacks = ThreadDeleteByUserActionCallbacks { config ->
                    deleteConfigs += config
                },
                threadReplyActionCallbacks = ThreadReplyActionCallbacks { config ->
                    replyConfigs += config
                    "999"
                }
            )
        )

        callbacks.onDismissPostActionSheet()
        callbacks.onDeleteDialogPasswordChange(" 9999 ")
        callbacks.onDeleteDialogImageOnlyChange(false)
        callbacks.onQuoteSelectionDismiss()
        callbacks.onDeleteDialogConfirm(targetPost)
        callbacks.onReplySubmit()
        yield()
        yield()

        assertFalse(overlayState.actionSheetState.isActionSheetVisible)
        assertNull(overlayState.quoteSelectionState.targetPost)
        assertNull(overlayState.deleteDialogState.targetPost)
        assertEquals("55", deleteConfigs.single().postId)
        assertEquals(false, deleteConfigs.single().imageOnly)
        assertEquals("comment", replyConfigs.single().comment)
        assertEquals(listOf("9999", "5678"), updatedDeleteKeys)
        assertEquals(2, refreshCount)
        assertFalse(replyState.isVisible)
        assertEquals("", replyState.draft.comment)
        assertTrue(shownMessages.contains("本人削除を実行しました"))
        assertTrue(shownMessages.contains("返信を送信しました"))
    }

    @Test
    fun threadScreenDerivedStateSupport_buildsHeaderAndReadAloudState() {
        val successState = ThreadUiState.Success(
            ThreadPage(
                threadId = "123",
                boardTitle = "board",
                posts = listOf(
                    Post(
                        id = "1",
                        author = null,
                        subject = "subject",
                        timestamp = "now",
                        messageHtml = "body",
                        imageUrl = null,
                        thumbnailUrl = null
                    )
                ),
                expiresAtLabel = "2026/03/12 12:00",
                deletedNotice = null
            )
        )
        val derived = buildThreadScreenDerivedUiState(
            currentState = successState,
            initialReplyCount = 5,
            threadTitle = null,
            isReadAloudControlsVisible = false,
            readAloudStatus = ReadAloudStatus.Paused(
                ReadAloudSegment(postIndex = 0, postId = "1", body = "body")
            )
        )

        assertEquals(successState, derived.successState)
        assertEquals(successState.page, derived.currentPage)
        assertEquals(successState.page.posts, derived.currentPosts)
        assertEquals(1, derived.resolvedReplyCount)
        assertEquals("subject", derived.resolvedThreadTitle)
        assertEquals("1レス / 2026/03/12 12:00", derived.statusLabel)
        assertTrue(derived.shouldPrepareReadAloudSegments)

        val loadingDerived = buildThreadScreenDerivedUiState(
            currentState = ThreadUiState.Loading,
            initialReplyCount = 7,
            threadTitle = "fallback",
            isReadAloudControlsVisible = false,
            readAloudStatus = ReadAloudStatus.Idle
        )
        assertNull(loadingDerived.successState)
        assertNull(loadingDerived.currentPage)
        assertTrue(loadingDerived.currentPosts.isEmpty())
        assertEquals(7, loadingDerived.resolvedReplyCount)
        assertEquals("fallback", loadingDerived.resolvedThreadTitle)
        assertEquals("7レス", loadingDerived.statusLabel)
        assertFalse(loadingDerived.shouldPrepareReadAloudSegments)
    }

    @Test
    fun threadScreenDerivedRuntimeSupport_buildsSnapshot() {
        val successState = ThreadUiState.Success(
            ThreadPage(
                threadId = "1",
                boardTitle = "board",
                expiresAtLabel = null,
                deletedNotice = null,
                posts = listOf(
                    Post(
                        id = "1",
                        author = null,
                        subject = "subject",
                        timestamp = "now",
                        messageHtml = "body",
                        imageUrl = null,
                        thumbnailUrl = null
                    )
                )
            )
        )
        val derivedUiState = buildThreadScreenDerivedUiState(
            currentState = successState,
            initialReplyCount = null,
            threadTitle = null,
            isReadAloudControlsVisible = true,
            readAloudStatus = ReadAloudStatus.Idle
        )
        val snapshot = buildThreadScreenDerivedRuntimeSnapshot(
            derivedUiState = derivedUiState,
            isSearchActive = true,
            searchMatches = listOf(
                ThreadSearchMatch(
                    postId = "1",
                    postIndex = 0,
                    highlightRanges = listOf(0..2)
                )
            ),
            readAloudSegments = listOf(
                ReadAloudSegment(postIndex = 0, postId = "1", body = "a"),
                ReadAloudSegment(postIndex = 3, postId = "2", body = "b")
            ),
            firstVisibleItemIndex = 2
        )

        assertEquals(mapOf("1" to listOf(0..2)), snapshot.postHighlightRanges)
        assertEquals(1, snapshot.firstVisibleSegmentIndex)
    }

    @Test
    fun threadScreenEffectSupport_resolvesEffectStates() {
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = emptyList()
        )
        val autoSaveReadyState = resolveThreadAutoSaveEffectState(
            page = page,
            expectedThreadId = "123",
            isShowingOfflineCopy = false,
            hasAutoSaveRepository = true,
            hasHttpClient = true,
            hasFileSystem = true,
            isAutoSaveInProgress = false,
            lastAutoSaveTimestampMillis = 0L,
            nowMillis = AUTO_SAVE_INTERVAL_MS,
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS
        )
        assertEquals(ThreadAutoSaveAvailability.Ready, autoSaveReadyState.availability)
        assertEquals(page, autoSaveReadyState.page)

        val autoSaveBusyState = resolveThreadAutoSaveEffectState(
            page = page,
            expectedThreadId = "123",
            isShowingOfflineCopy = false,
            hasAutoSaveRepository = true,
            hasHttpClient = true,
            hasFileSystem = true,
            isAutoSaveInProgress = true,
            lastAutoSaveTimestampMillis = 0L,
            nowMillis = AUTO_SAVE_INTERVAL_MS,
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS
        )
        assertEquals(ThreadAutoSaveAvailability.InProgress, autoSaveBusyState.availability)
        assertNull(autoSaveBusyState.page)

        val historyEntry = ThreadHistoryEntry(
            threadId = "123",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            lastReadItemIndex = 4,
            lastReadItemOffset = 12,
            replyCount = 0
        )
        val scrollRestoreState = resolveThreadInitialScrollRestoreState(
            hasRestoredInitialScroll = false,
            entry = historyEntry,
            totalItems = 10
        )
        assertTrue(scrollRestoreState.shouldRestore)
        assertEquals(4, scrollRestoreState.savedIndex)
        assertEquals(12, scrollRestoreState.savedOffset)
        assertEquals(10, scrollRestoreState.totalItems)

        assertEquals(
            "https://example.com/test/res/123.htm",
            resolveThreadUrlOverrideSyncState(
                currentResolvedThreadUrlOverride = null,
                incomingThreadUrlOverride = "https://example.com/test/res/123.htm"
            )
        )
        assertNull(
            resolveThreadUrlOverrideSyncState(
                currentResolvedThreadUrlOverride = "https://example.com/test/res/123.htm",
                incomingThreadUrlOverride = "https://example.com/test/res/123.htm"
            )
        )

        val replyDialogState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(password = "")
        )
        assertEquals(
            ThreadReplyDialogState(
                isVisible = true,
                draft = ThreadReplyDraft(password = "stored")
            ),
            resolveThreadReplyDialogAutofillState(
                isReplyDialogVisible = true,
                currentState = replyDialogState,
                lastUsedDeleteKey = "stored"
            )
        )
        assertNull(
            resolveThreadReplyDialogAutofillState(
                isReplyDialogVisible = false,
                currentState = replyDialogState,
                lastUsedDeleteKey = "stored"
            )
        )

        assertEquals(0, resolveThreadReadAloudIndexUpdate(currentIndex = 3, segmentCount = 0))
        assertNull(resolveThreadReadAloudIndexUpdate(currentIndex = 1, segmentCount = 5))
        assertEquals(0, resolveThreadSearchQueryResetIndex(currentIndex = 2))
        assertNull(resolveThreadSearchQueryResetIndex(currentIndex = 0))
        assertEquals(0, resolveThreadSearchResultIndexUpdate(currentIndex = 3, matchCount = 2))
        assertNull(resolveThreadSearchResultIndexUpdate(currentIndex = 1, matchCount = 3))
    }

    @Test
    fun threadScreenStateBindingsSupport_managesReadAloudRuntimeAndJobs() {
        var stopPlaybackCalled = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = Job(),
            status = ReadAloudStatus.Speaking(
                ReadAloudSegment(postIndex = 0, postId = "1", body = "body")
            ),
            currentIndex = 3,
            cancelRequestedByUser = false
        )
        val readAloudBindings = buildThreadReadAloudRuntimeBindings(
            currentState = { readAloudState },
            setState = { readAloudState = it },
            onStopPlayback = { stopPlaybackCalled = true }
        )

        val pauseMessage = readAloudBindings.pause()
        assertEquals(buildReadAloudPausedMessage(), pauseMessage)
        assertTrue(stopPlaybackCalled)
        assertTrue(readAloudState.cancelRequestedByUser)
        assertNull(readAloudState.job)
        assertTrue(readAloudState.status is ReadAloudStatus.Paused)

        stopPlaybackCalled = false
        readAloudBindings.stop()
        assertTrue(stopPlaybackCalled)
        assertNull(readAloudState.job)
        assertEquals(ReadAloudStatus.Idle, readAloudState.status)
        assertEquals(0, readAloudState.currentIndex)

        var autoJob: Job? = Job()
        var manualJob: Job? = Job()
        var singleMediaJob: Job? = Job()
        var refreshJob: Job? = Job()
        var manualSaveInProgress = true
        var singleMediaSaveInProgress = true
        var stopReadAloudCalled = false
        var dismissReadAloudOverlayCalled = false
        val jobBindings = buildThreadScreenJobBindings(
            currentAutoSaveJob = { autoJob },
            setAutoSaveJob = { autoJob = it },
            currentManualSaveJob = { manualJob },
            setManualSaveJob = { manualJob = it },
            currentSingleMediaSaveJob = { singleMediaJob },
            setSingleMediaSaveJob = { singleMediaJob = it },
            currentRefreshThreadJob = { refreshJob },
            setRefreshThreadJob = { refreshJob = it },
            setIsManualSaveInProgress = { manualSaveInProgress = it },
            setIsSingleMediaSaveInProgress = { singleMediaSaveInProgress = it },
            onStopReadAloud = { stopReadAloudCalled = true },
            onDismissReadAloudOverlay = { dismissReadAloudOverlayCalled = true }
        )

        jobBindings.resetForThreadChange()
        assertTrue(stopReadAloudCalled)
        assertTrue(dismissReadAloudOverlayCalled)
        assertNull(autoJob)
        assertNull(manualJob)
        assertNull(singleMediaJob)
        assertNull(refreshJob)
        assertFalse(manualSaveInProgress)
        assertFalse(singleMediaSaveInProgress)
    }

    @Test
    fun threadScreenStateBindingsSupport_bundlesReadAloudRuntimeAndJobs() {
        var stopPlaybackCalled = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = Job(),
            status = ReadAloudStatus.Speaking(
                ReadAloudSegment(postIndex = 0, postId = "1", body = "body")
            ),
            currentIndex = 2,
            cancelRequestedByUser = false
        )
        var autoJob: Job? = Job()
        var manualJob: Job? = Job()
        var singleMediaJob: Job? = Job()
        var refreshJob: Job? = Job()
        var manualSaveInProgress = true
        var singleMediaSaveInProgress = true
        var dismissReadAloudOverlayCalled = false

        val bundle = buildThreadScreenRuntimeJobBindingsBundle(
            ThreadScreenRuntimeJobInputs(
                readAloudStateBindings = ThreadScreenReadAloudStateBindings(
                    currentState = { readAloudState },
                    setState = { readAloudState = it }
                ),
                onStopPlayback = { stopPlaybackCalled = true },
                currentAutoSaveJob = { autoJob },
                setAutoSaveJob = { autoJob = it },
                currentManualSaveJob = { manualJob },
                setManualSaveJob = { manualJob = it },
                currentSingleMediaSaveJob = { singleMediaJob },
                setSingleMediaSaveJob = { singleMediaJob = it },
                currentRefreshThreadJob = { refreshJob },
                setRefreshThreadJob = { refreshJob = it },
                setIsManualSaveInProgress = { manualSaveInProgress = it },
                setIsSingleMediaSaveInProgress = { singleMediaSaveInProgress = it },
                onDismissReadAloudOverlay = { dismissReadAloudOverlayCalled = true }
            )
        )

        assertNotNull(bundle.readAloudRuntimeBindings.pause())
        assertTrue(stopPlaybackCalled)
        bundle.jobBindings.resetForThreadChange()

        assertTrue(dismissReadAloudOverlayCalled)
        assertNull(autoJob)
        assertNull(manualJob)
        assertNull(singleMediaJob)
        assertNull(refreshJob)
        assertFalse(manualSaveInProgress)
        assertFalse(singleMediaSaveInProgress)
        assertEquals(ReadAloudStatus.Idle, readAloudState.status)
    }

    @Test
    fun threadScreenCoreBindingsSupport_bundlesStateRuntimeBindings() = runBlocking {
        var stopPlaybackCalled = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = Job(),
            status = ReadAloudStatus.Speaking(
                ReadAloudSegment(postIndex = 0, postId = "1", body = "body")
            ),
            currentIndex = 1,
            cancelRequestedByUser = false
        )
        var autoJob: Job? = Job()
        var manualJob: Job? = Job()
        var singleMediaJob: Job? = Job()
        var refreshJob: Job? = Job()
        var manualSaveInProgress = true
        var singleMediaSaveInProgress = true
        var dismissReadAloudOverlayCalled = false
        var fallbackHeaders = emptyList<String>()
        var fallbackWords = emptyList<String>()
        var filteringEnabled = true
        var filterOptions = emptySet<ThreadFilterOption>()
        var filterSortOption: ThreadFilterSortOption? = null
        var filterKeyword = ""
        var replyName = ""
        var replyEmail = ""
        var replySubject = ""
        var replyComment = ""
        var replyPassword = ""
        var replyImageData: ImageData? = null
        var isReplyDialogVisible = false

        val readAloudStateBindings = ThreadScreenReadAloudStateBindings(
            currentState = { readAloudState },
            setState = { readAloudState = it }
        )
        val bundle = buildThreadScreenStateRuntimeBindingsBundle(
            ThreadScreenStateRuntimeInputs(
                runtimeJobInputs = ThreadScreenRuntimeJobInputs(
                    readAloudStateBindings = readAloudStateBindings,
                    onStopPlayback = { stopPlaybackCalled = true },
                    currentAutoSaveJob = { autoJob },
                    setAutoSaveJob = { autoJob = it },
                    currentManualSaveJob = { manualJob },
                    setManualSaveJob = { manualJob = it },
                    currentSingleMediaSaveJob = { singleMediaJob },
                    setSingleMediaSaveJob = { singleMediaJob = it },
                    currentRefreshThreadJob = { refreshJob },
                    setRefreshThreadJob = { refreshJob = it },
                    setIsManualSaveInProgress = { manualSaveInProgress = it },
                    setIsSingleMediaSaveInProgress = { singleMediaSaveInProgress = it },
                    onDismissReadAloudOverlay = { dismissReadAloudOverlayCalled = true }
                ),
                messageFormInputs = ThreadScreenMessageFormInputs(
                    messageNgInputs = ThreadScreenMessageNgInputs(
                        coroutineScope = this,
                        showSnackbar = {},
                        screenPreferencesCallbacks = ScreenPreferencesCallbacks(),
                        stateStore = null,
                        onFallbackHeadersChanged = { fallbackHeaders = it },
                        onFallbackWordsChanged = { fallbackWords = it },
                        currentHeaders = { fallbackHeaders },
                        currentWords = { fallbackWords },
                        isFilteringEnabled = { filteringEnabled },
                        setFilteringEnabled = { filteringEnabled = it }
                    ),
                    formInputs = ThreadScreenFormInputs(
                        filterInputs = ThreadScreenFilterBindingInputs(
                            currentOptions = { filterOptions },
                            currentSortOption = { filterSortOption },
                            currentKeyword = { filterKeyword },
                            setOptions = { filterOptions = it },
                            setSortOption = { filterSortOption = it },
                            setKeyword = { filterKeyword = it }
                        ),
                        replyDraftInputs = ThreadScreenReplyDraftInputs(
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
                        ),
                        replyDialogInputs = ThreadScreenReplyDialogInputs(
                            isVisible = { isReplyDialogVisible },
                            setVisible = { isReplyDialogVisible = it }
                        )
                    )
                )
            )
        )

        bundle.threadNgMutationCallbacks.onAddHeader("header")
        bundle.threadFilterBinding.setState(
            ThreadFilterUiState(
                options = setOf(ThreadFilterOption.Image),
                sortOption = ThreadFilterSortOption.Saidane,
                keyword = "abc"
            )
        )
        bundle.replyDialogBinding.setState(
            ThreadReplyDialogState(
                isVisible = true,
                draft = ThreadReplyDraft(comment = "body")
            )
        )
        bundle.readAloudRuntimeBindings.stop()
        bundle.jobBindings.resetForThreadChange()
        yield()

        assertTrue(stopPlaybackCalled)
        assertTrue(dismissReadAloudOverlayCalled)
        assertEquals(listOf("header"), fallbackHeaders)
        assertTrue(filteringEnabled)
        assertEquals(setOf(ThreadFilterOption.Image), filterOptions)
        assertEquals(ThreadFilterSortOption.Saidane, filterSortOption)
        assertEquals("abc", filterKeyword)
        assertTrue(isReplyDialogVisible)
        assertEquals("body", replyComment)
        assertNull(autoJob)
        assertNull(manualJob)
        assertNull(singleMediaJob)
        assertNull(refreshJob)
        assertFalse(manualSaveInProgress)
        assertFalse(singleMediaSaveInProgress)
        assertEquals(ReadAloudStatus.Idle, readAloudState.status)
    }

    @Test
    fun threadScreenBindingSupport_wrapsHistoryGalleryAndQuotePreviewCallbacks() {
        var dismissedEntry: ThreadHistoryEntry? = null
        var selectedEntry: ThreadHistoryEntry? = null
        var boardClicked = false
        var refreshed = false
        var batchDeleted = false
        var settingsOpened = false
        val entry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        val historyCallbacks = buildThreadScreenHistoryDrawerCallbacks(
            onHistoryEntryDismissed = { dismissedEntry = it },
            onHistoryEntrySelected = { selectedEntry = it },
            onBoardClick = { boardClicked = true },
            onRefreshClick = { refreshed = true },
            onBatchDeleteClick = { batchDeleted = true },
            onSettingsClick = { settingsOpened = true }
        )
        historyCallbacks.onHistoryEntryDismissed(entry)
        historyCallbacks.onHistoryEntrySelected(entry)
        historyCallbacks.onBoardClick()
        historyCallbacks.onRefreshClick()
        historyCallbacks.onBatchDeleteClick()
        historyCallbacks.onSettingsClick()
        assertEquals(entry, dismissedEntry)
        assertEquals(entry, selectedEntry)
        assertTrue(boardClicked)
        assertTrue(refreshed)
        assertTrue(batchDeleted)
        assertTrue(settingsOpened)

        var scrolledIndex: Int? = null
        var galleryDismissed = false
        val posts = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "now", messageHtml = "a", imageUrl = null, thumbnailUrl = null),
            Post(id = "2", author = null, subject = null, timestamp = "now", messageHtml = "b", imageUrl = null, thumbnailUrl = null)
        )
        val galleryCallbacks = buildThreadScreenGalleryCallbacks(
            currentPosts = posts,
            onDismiss = { galleryDismissed = true },
            onScrollToPostIndex = { scrolledIndex = it }
        )
        galleryCallbacks.onImageClick(posts[1])
        galleryCallbacks.onDismiss()
        assertEquals(1, scrolledIndex)
        assertTrue(galleryDismissed)

        var quoteDismissed = false
        var shownQuote: Pair<String, List<Post>>? = null
        val postIndex = posts.associateBy { it.id }
        val quoteCallbacks = buildThreadScreenQuotePreviewCallbacks(
            postIndex = postIndex,
            onDismiss = { quoteDismissed = true },
            onShowQuotePreview = { text, targets -> shownQuote = text to targets }
        )
        val quoteReference = QuoteReference(text = ">>1", targetPostIds = listOf("1"))
        quoteCallbacks.onQuoteClick(quoteReference)
        quoteCallbacks.onDismiss()
        assertEquals(">>1" to listOf(posts[0]), shownQuote)
        assertTrue(quoteDismissed)
    }

    @Test
    fun threadScreenBindingSupport_wrapsQuotePreviewPresenterAndMediaPreviewCallbacks() {
        var quotePreviewState: QuotePreviewState? = null
        val quotePresenter = buildThreadScreenQuotePreviewPresenter(
            isScrolling = { false },
            posterIdLabels = emptyMap(),
            setState = { quotePreviewState = it }
        )
        val targets = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "now", messageHtml = "body", imageUrl = null, thumbnailUrl = null)
        )
        quotePresenter(">>1", targets)
        assertEquals(">>1", quotePreviewState?.quoteText)
        assertEquals(targets, quotePreviewState?.targetPosts)

        var mediaPreviewState = ThreadMediaPreviewState(previewMediaIndex = 0)
        var savedEntry: MediaPreviewEntry? = null
        val mediaCallbacks = buildThreadScreenMediaPreviewDialogCallbacks(
            mediaPreviewState = { mediaPreviewState },
            setMediaPreviewState = { mediaPreviewState = it },
            totalCount = 2,
            onSave = { savedEntry = it }
        )
        val mediaEntry = MediaPreviewEntry(
            url = "https://example.com/a.png",
            mediaType = MediaType.Image,
            postId = "1",
            title = "a"
        )
        mediaCallbacks.onNavigateNext()
        assertEquals(1, mediaPreviewState.previewMediaIndex)
        mediaCallbacks.onNavigatePrevious()
        assertEquals(0, mediaPreviewState.previewMediaIndex)
        mediaCallbacks.onSave(mediaEntry)
        assertEquals(mediaEntry, savedEntry)
        mediaCallbacks.onDismiss()
        assertNull(mediaPreviewState.previewMediaIndex)
    }

    @Test
    fun threadScreenBindingSupport_wrapsHistorySelectionAndMenuEntryHandlers() {
        var closedDrawer = false
        var selectedEntry: ThreadHistoryEntry? = null
        val entry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        val historySelectionHandler = buildThreadScreenHistorySelectionHandler(
            onCloseDrawer = { closedDrawer = true },
            onHistoryEntrySelected = { selectedEntry = it }
        )
        historySelectionHandler(entry)
        assertTrue(closedDrawer)
        assertEquals(entry, selectedEntry)

        var openedReply = false
        var scrolledTop = false
        var scrolledBottom = false
        var refreshBusyShown = false
        var startedRefresh = false
        var openedGallery = false
        var delegatedSave = false
        var filterShown = false
        var settingsShown = false
        var ngHeaderCleared = false
        var ngManagementShown = false
        var externalOpened = false
        var readAloudShown = false
        var privacyToggled = false
        val menuEntryHandler = buildThreadScreenMenuEntryHandler(
            isRefreshing = { false },
            onOpenReplyDialog = { openedReply = true },
            onScrollTop = { scrolledTop = true },
            onScrollBottom = { scrolledBottom = true },
            onShowRefreshBusyMessage = { refreshBusyShown = true },
            onStartRefresh = { startedRefresh = true },
            onOpenGallery = { openedGallery = true },
            onDelegateToSaveHandler = { delegatedSave = true },
            onShowFilterSheet = { filterShown = true },
            onShowSettingsSheet = { settingsShown = true },
            onClearNgHeaderPrefill = { ngHeaderCleared = true },
            onShowNgManagement = { ngManagementShown = true },
            onOpenExternalApp = { externalOpened = true },
            onShowReadAloudControls = { readAloudShown = true },
            onTogglePrivacy = { privacyToggled = true }
        )
        menuEntryHandler(ThreadMenuEntryId.Reply)
        menuEntryHandler(ThreadMenuEntryId.ScrollToTop)
        menuEntryHandler(ThreadMenuEntryId.ScrollToBottom)
        menuEntryHandler(ThreadMenuEntryId.Refresh)
        menuEntryHandler(ThreadMenuEntryId.Gallery)
        menuEntryHandler(ThreadMenuEntryId.Save)
        menuEntryHandler(ThreadMenuEntryId.Filter)
        menuEntryHandler(ThreadMenuEntryId.Settings)
        menuEntryHandler(ThreadMenuEntryId.NgManagement)
        menuEntryHandler(ThreadMenuEntryId.ExternalApp)
        menuEntryHandler(ThreadMenuEntryId.ReadAloud)
        menuEntryHandler(ThreadMenuEntryId.Privacy)

        assertTrue(openedReply)
        assertTrue(scrolledTop)
        assertTrue(scrolledBottom)
        assertFalse(refreshBusyShown)
        assertTrue(startedRefresh)
        assertTrue(openedGallery)
        assertTrue(delegatedSave)
        assertTrue(filterShown)
        assertTrue(settingsShown)
        assertTrue(ngHeaderCleared)
        assertTrue(ngManagementShown)
        assertTrue(externalOpened)
        assertTrue(readAloudShown)
        assertTrue(privacyToggled)

        val busyMenuEntryHandler = buildThreadScreenMenuEntryHandler(
            isRefreshing = { true },
            onOpenReplyDialog = {},
            onScrollTop = {},
            onScrollBottom = {},
            onShowRefreshBusyMessage = { refreshBusyShown = true },
            onStartRefresh = { startedRefresh = true },
            onOpenGallery = {},
            onDelegateToSaveHandler = {},
            onShowFilterSheet = {},
            onShowSettingsSheet = {},
            onClearNgHeaderPrefill = {},
            onShowNgManagement = {},
            onOpenExternalApp = {},
            onShowReadAloudControls = {},
            onTogglePrivacy = {}
        )
        refreshBusyShown = false
        startedRefresh = false
        busyMenuEntryHandler(ThreadMenuEntryId.Refresh)
        assertTrue(refreshBusyShown)
        assertFalse(startedRefresh)
    }

    @Test
    fun threadScreenBindingSupport_wrapsPostCardCallbacks() {
        val post = Post(
            id = "10",
            author = null,
            subject = null,
            timestamp = "now",
            posterId = "abc",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        val referencedPost = Post(
            id = "20",
            author = null,
            subject = null,
            timestamp = "now",
            messageHtml = "ref",
            imageUrl = null,
            thumbnailUrl = null
        )
        var shownQuote: Pair<String, List<Post>>? = null
        var quotedPost: Post? = null
        var saidanePost: Post? = null
        var longPressedPost: Post? = null
        val callbacks = buildThreadScreenPostCardCallbacks(
            post = post,
            normalizedPosterId = "abc",
            postIndex = mapOf("20" to referencedPost),
            referencedByMap = mapOf(post.id to listOf(referencedPost)),
            postsByPosterId = mapOf("abc" to listOf(post, referencedPost)),
            quotePreviewState = null,
            onShowQuotePreview = { text, targets -> shownQuote = text to targets },
            onQuoteRequestedForPost = { quotedPost = it },
            onSaidaneClick = { saidanePost = it },
            onMediaClick = null,
            onPostLongPress = { longPressedPost = it }
        )

        callbacks.onQuoteClick(QuoteReference(text = ">>20", targetPostIds = listOf("20")))
        assertEquals(">>20" to listOf(referencedPost), shownQuote)

        shownQuote = null
        callbacks.onPosterIdClick?.invoke()
        assertEquals("ID:abc のレス" to listOf(post, referencedPost), shownQuote)

        shownQuote = null
        callbacks.onReferencedByClick?.invoke()
        assertEquals(">>10 を引用したレス" to listOf(referencedPost), shownQuote)

        callbacks.onQuoteRequested()
        callbacks.onSaidaneClick()
        callbacks.onLongPress()
        assertEquals(post, quotedPost)
        assertEquals(post, saidanePost)
        assertEquals(post, longPressedPost)

        val blockedCallbacks = buildThreadScreenPostCardCallbacks(
            post = post,
            normalizedPosterId = null,
            postIndex = emptyMap(),
            referencedByMap = emptyMap(),
            postsByPosterId = emptyMap(),
            quotePreviewState = QuotePreviewState(
                quoteText = "x",
                targetPosts = listOf(post),
                posterIdLabels = emptyMap()
            ),
            onShowQuotePreview = { _, _ -> },
            onQuoteRequestedForPost = {},
            onSaidaneClick = {},
            onMediaClick = null,
            onPostLongPress = { longPressedPost = it }
        )
        longPressedPost = null
        blockedCallbacks.onLongPress()
        assertNull(longPressedPost)
        assertNull(blockedCallbacks.onPosterIdClick)
        assertNull(blockedCallbacks.onReferencedByClick)
    }

    @Test
    fun threadScreenBindingSupport_wrapsPostActionHandlers() = runBlocking {
        val post = Post(
            id = "10",
            author = null,
            subject = null,
            timestamp = "now",
            posterId = "abc",
            saidaneLabel = "1",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        var overlayState = openThreadPostActionOverlay(
            currentState = emptyThreadPostOverlayState(),
            post = post
        )
        val shownMessages = mutableListOf<String>()
        val launchedActions = mutableListOf<Pair<String, String>>()
        val votedPosts = mutableListOf<Post>()
        val deletedPosts = mutableListOf<Post>()
        val updatedLabels = mutableMapOf<String, String>()
        val handlers = buildThreadScreenPostActionHandlers(
            inputs = ThreadScreenPostActionInputs(
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "stored",
                currentSaidaneLabel = { currentPost -> currentPost.saidaneLabel },
                isSelfPost = { false },
                onShowOptionalMessage = { message ->
                    if (message != null) {
                        shownMessages += message
                    }
                },
                onSaidaneLabelUpdated = { currentPost, updatedLabel ->
                    updatedLabels[currentPost.id] = updatedLabel
                },
                launchUnitAction = { successMessage, failurePrefix, onSuccess, block ->
                    launchedActions += successMessage to failurePrefix
                    runBlocking { block() }
                    onSuccess()
                },
                voteSaidane = { currentPost ->
                    votedPosts += currentPost
                },
                requestDeletion = { currentPost ->
                    deletedPosts += currentPost
                }
            )
        )

        handlers.onSaidane(post)
        assertTrue(overlayState.actionSheetState.targetPost == null)
        assertEquals("そうだねx2", updatedLabels["10"])
        assertEquals(listOf(post), votedPosts)

        overlayState = openThreadPostActionOverlay(emptyThreadPostOverlayState(), post)
        handlers.onDelRequest(post)
        assertTrue(overlayState.actionSheetState.targetPost == null)
        assertEquals(listOf(post), deletedPosts)

        handlers.onOpenDeleteDialog(post)
        assertEquals(post, overlayState.deleteDialogState.targetPost)
        assertEquals("stored", overlayState.deleteDialogState.password)

        handlers.onOpenQuoteSelection(post)
        assertEquals(post, overlayState.quoteSelectionState.targetPost)

        handlers.onNgRegister(post)
        assertTrue(overlayState.isNgManagementVisible)
        assertEquals("abc", overlayState.ngHeaderPrefill)
        assertTrue(shownMessages.isEmpty())
        assertEquals(
            listOf(
                "そうだねを送信しました" to "そうだねに失敗しました",
                "DEL依頼を送信しました" to "DEL依頼に失敗しました"
            ),
            launchedActions
        )
    }

    @Test
    fun threadScreenBindingSupport_wrapsTopBarAndActionBarCallbacks() {
        var searchQuery: String? = null
        var searchedPrev = false
        var searchedNext = false
        var submittedSearch = false
        var closedSearch = false
        var backed = false
        var openedHistory = false
        var startedSearch = false
        var openedSettings = false
        val topBarCallbacks = buildThreadScreenTopBarCallbacks(
            onSearchQueryChange = { searchQuery = it },
            onSearchPrev = { searchedPrev = true },
            onSearchNext = { searchedNext = true },
            onSearchSubmit = { submittedSearch = true },
            onSearchClose = { closedSearch = true },
            onBack = { backed = true },
            onOpenHistory = { openedHistory = true },
            onSearch = { startedSearch = true },
            onMenuSettings = { openedSettings = true }
        )
        topBarCallbacks.onSearchQueryChange("abc")
        topBarCallbacks.onSearchPrev()
        topBarCallbacks.onSearchNext()
        topBarCallbacks.onSearchSubmit()
        topBarCallbacks.onSearchClose()
        topBarCallbacks.onBack()
        topBarCallbacks.onOpenHistory()
        topBarCallbacks.onSearch()
        topBarCallbacks.onMenuSettings()

        assertEquals("abc", searchQuery)
        assertTrue(searchedPrev)
        assertTrue(searchedNext)
        assertTrue(submittedSearch)
        assertTrue(closedSearch)
        assertTrue(backed)
        assertTrue(openedHistory)
        assertTrue(startedSearch)
        assertTrue(openedSettings)

        var actionEntry: ThreadMenuEntryId? = null
        val actionBarCallbacks = buildThreadScreenActionBarCallbacks(
            onAction = { actionEntry = it }
        )
        actionBarCallbacks.onAction(ThreadMenuEntryId.Refresh)
        assertEquals(ThreadMenuEntryId.Refresh, actionEntry)
    }

    @Test
    fun threadScreenBindingSupport_bundlesUiCallbacks() {
        var searchQuery: String? = null
        var searchedPrev = false
        var searchedNext = false
        var submittedSearch = false
        var closedSearch = false
        var backed = false
        var openedHistory = false
        var startedSearch = false
        var openedSettings = false
        var actionEntry: ThreadMenuEntryId? = null

        var overlayState = ThreadPostOverlayState(
            quoteSelectionState = ThreadQuoteSelectionState(
                targetPost = Post(
                    id = "10",
                    author = null,
                    subject = null,
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        var replyDialogState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(comment = "base")
        )
        val replyDialogBinding = ThreadReplyDialogStateBinding(
            currentState = { replyDialogState },
            setState = { replyDialogState = it }
        )
        var mediaPreviewState = ThreadMediaPreviewState(previewMediaIndex = 0)
        val mediaEntry = MediaPreviewEntry(
            url = "https://example.com/a.png",
            mediaType = MediaType.Image,
            postId = "1",
            title = "a"
        )
        var savedMediaEntry: MediaPreviewEntry? = null
        val posts = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "now", messageHtml = "a", imageUrl = null, thumbnailUrl = null),
            Post(id = "2", author = null, subject = null, timestamp = "now", messageHtml = "b", imageUrl = null, thumbnailUrl = null)
        )
        var scrolledIndex: Int? = null
        var galleryDismissed = false
        var settingsDismissed = false
        var openedNgManagement = false
        var filterState = ThreadFilterUiState()
        val filterBinding = ThreadFilterUiStateBinding(
            currentState = { filterState },
            setState = { filterState = it }
        )
        var filterDismissed = false
        var seekTarget: Pair<Int, Boolean>? = null
        var playedReadAloud = false
        var pausedReadAloud = false
        var stoppedReadAloud = false
        var showedStoppedMessage = false
        var dismissedReadAloud = false
        var addedNgHeader: String? = null
        var dismissedNgManagement = false
        var dismissedSaveProgress = false
        var cancelledSaveProgress = false
        var dismissedGlobalSettings = false
        var backgroundRefreshChanged: Boolean? = null
        var lightweightModeChanged: Boolean? = null
        var manualSaveDirectory: String? = null
        var saveDirectorySelection: SaveDirectorySelection? = null
        var openedDirectoryPicker = false
        var openedCookieManager = false
        var selectedFileManager: Pair<String, String>? = null
        var clearedPreferredFileManager = false
        var updatedThreadMenuEntries: List<ThreadMenuEntryConfig>? = null
        var updatedCatalogNavEntries: List<CatalogNavEntryConfig>? = null
        var dismissedCookieManagement = false

        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = { backgroundRefreshChanged = it },
            onLightweightModeChanged = { lightweightModeChanged = it },
            onManualSaveDirectoryChanged = { manualSaveDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onOpenSaveDirectoryPicker = { openedDirectoryPicker = true },
            onFileManagerSelected = { packageName, label ->
                selectedFileManager = packageName to label
            },
            onClearPreferredFileManager = { clearedPreferredFileManager = true },
            onThreadMenuEntriesChanged = { updatedThreadMenuEntries = it },
            onCatalogNavEntriesChanged = { updatedCatalogNavEntries = it }
        )
        val bundle = buildThreadScreenUiBindingsBundle(
            topBarInputs = ThreadScreenTopBarUiInputs(
                searchNavigationCallbacks = ThreadSearchNavigationCallbacks(
                    onSearchSubmit = { submittedSearch = true },
                    onSearchPrev = { searchedPrev = true },
                    onSearchNext = { searchedNext = true }
                ),
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = { closedSearch = true },
                onBack = { backed = true },
                onOpenHistory = { openedHistory = true },
                onSearch = { startedSearch = true },
                onOpenGlobalSettings = { openedSettings = true },
                onAction = { actionEntry = it }
            ),
            overlayInputs = ThreadScreenOverlayUiInputs(
                replyDialogBinding = replyDialogBinding,
                currentPostOverlayState = { overlayState },
                setPostOverlayState = { overlayState = it },
                mediaPreviewState = { mediaPreviewState },
                setMediaPreviewState = { mediaPreviewState = it },
                mediaPreviewEntryCount = 2,
                onSavePreviewMedia = { savedMediaEntry = it },
                galleryPosts = posts,
                onDismissGallery = { galleryDismissed = true },
                onScrollToPostIndex = { scrolledIndex = it },
                threadFilterBinding = filterBinding,
                onDismissSettingsSheet = { settingsDismissed = true },
                onDismissFilterSheet = { filterDismissed = true },
                onApplySettingsActionState = {},
                onOpenNgManagement = { openedNgManagement = true },
                onOpenExternalApp = {},
                onTogglePrivacy = {},
                firstVisibleSegmentIndex = { 1 },
                onSeekToReadAloudIndex = { index, restart -> seekTarget = index to restart },
                onPlayReadAloud = { playedReadAloud = true },
                onPauseReadAloud = { pausedReadAloud = true },
                onStopReadAloud = { stoppedReadAloud = true },
                onShowReadAloudStoppedMessage = { showedStoppedMessage = true },
                onDismissReadAloudControls = { dismissedReadAloud = true },
                onDismissNgManagement = { dismissedNgManagement = true },
                ngMutationCallbacks = ThreadNgMutationCallbacks(
                    onAddHeader = { addedNgHeader = it },
                    onAddWord = {},
                    onRemoveHeader = {},
                    onRemoveWord = {},
                    onToggleFiltering = {}
                ),
                onDismissSaveProgress = { dismissedSaveProgress = true },
                onCancelSaveProgress = { cancelledSaveProgress = true }
            ),
            settingsInputs = ThreadScreenSettingsUiInputs(
                onDismissGlobalSettings = { dismissedGlobalSettings = true },
                screenPreferencesCallbacks = preferencesCallbacks,
                onOpenCookieManager = { openedCookieManager = true },
                onDismissCookieManagement = { dismissedCookieManagement = true }
            )
        )

        bundle.topBarCallbacks.onSearchQueryChange("abc")
        bundle.topBarCallbacks.onSearchPrev()
        bundle.topBarCallbacks.onSearchNext()
        bundle.topBarCallbacks.onSearchSubmit()
        bundle.topBarCallbacks.onSearchClose()
        bundle.topBarCallbacks.onBack()
        bundle.topBarCallbacks.onOpenHistory()
        bundle.topBarCallbacks.onSearch()
        bundle.topBarCallbacks.onMenuSettings()
        bundle.actionBarCallbacks.onAction(ThreadMenuEntryId.Refresh)
        bundle.quoteSelectionConfirm(listOf(">No.1"))
        bundle.mediaPreviewDialogCallbacks.onNavigateNext()
        bundle.mediaPreviewDialogCallbacks.onSave(mediaEntry)
        bundle.galleryCallbacks?.onImageClick(posts[1])
        bundle.galleryCallbacks?.onDismiss()
        bundle.settingsSheetCallbacks.onAction(ThreadMenuEntryId.NgManagement)
        bundle.settingsSheetCallbacks.onDismiss()
        bundle.filterSheetCallbacks.onKeywordChange("filter")
        bundle.filterSheetCallbacks.onDismiss()
        bundle.readAloudControlCallbacks.onSeekToVisible()
        bundle.readAloudControlCallbacks.onPlay()
        bundle.readAloudControlCallbacks.onPause()
        bundle.readAloudControlCallbacks.onStop()
        bundle.readAloudControlCallbacks.onDismiss()
        bundle.ngManagementCallbacks.onAddHeader("header")
        bundle.ngManagementCallbacks.onDismiss()
        bundle.saveProgressDialogCallbacks.onDismissRequest()
        bundle.saveProgressDialogCallbacks.onCancelRequest()
        bundle.globalSettingsCallbacks.onBackgroundRefreshChanged(true)
        bundle.globalSettingsCallbacks.onLightweightModeChanged(false)
        bundle.globalSettingsCallbacks.onManualSaveDirectoryChanged("/tmp/save")
        bundle.globalSettingsCallbacks.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        bundle.globalSettingsCallbacks.onOpenSaveDirectoryPicker?.invoke()
        bundle.globalSettingsCallbacks.onOpenCookieManager?.invoke()
        bundle.globalSettingsCallbacks.onFileManagerSelected?.invoke("pkg", "label")
        bundle.globalSettingsCallbacks.onClearPreferredFileManager?.invoke()
        bundle.globalSettingsCallbacks.onThreadMenuEntriesChanged(emptyList())
        bundle.globalSettingsCallbacks.onCatalogNavEntriesChanged(emptyList())
        bundle.globalSettingsCallbacks.onBack()
        bundle.cookieManagementCallbacks.onBack()

        assertEquals("abc", searchQuery)
        assertTrue(searchedPrev)
        assertTrue(searchedNext)
        assertTrue(submittedSearch)
        assertTrue(closedSearch)
        assertTrue(backed)
        assertTrue(openedHistory)
        assertTrue(startedSearch)
        assertTrue(openedSettings)
        assertEquals(ThreadMenuEntryId.Refresh, actionEntry)
        assertEquals("base\n>No.1\n", replyDialogState.draft.comment)
        assertNull(overlayState.quoteSelectionState.targetPost)
        assertEquals(1, mediaPreviewState.previewMediaIndex)
        assertEquals(mediaEntry, savedMediaEntry)
        assertEquals(1, scrolledIndex)
        assertTrue(galleryDismissed)
        assertTrue(openedNgManagement)
        assertTrue(settingsDismissed)
        assertEquals("filter", filterState.keyword)
        assertTrue(filterDismissed)
        assertEquals(1 to true, seekTarget)
        assertTrue(playedReadAloud)
        assertTrue(pausedReadAloud)
        assertTrue(stoppedReadAloud)
        assertTrue(showedStoppedMessage)
        assertTrue(dismissedReadAloud)
        assertEquals("header", addedNgHeader)
        assertTrue(dismissedNgManagement)
        assertTrue(dismissedSaveProgress)
        assertTrue(cancelledSaveProgress)
        assertEquals(true, backgroundRefreshChanged)
        assertEquals(false, lightweightModeChanged)
        assertEquals("/tmp/save", manualSaveDirectory)
        assertEquals(SaveDirectorySelection.PICKER, saveDirectorySelection)
        assertTrue(openedDirectoryPicker)
        assertTrue(openedCookieManager)
        assertEquals("pkg" to "label", selectedFileManager)
        assertTrue(clearedPreferredFileManager)
        assertEquals(emptyList<ThreadMenuEntryConfig>(), updatedThreadMenuEntries)
        assertEquals(emptyList<CatalogNavEntryConfig>(), updatedCatalogNavEntries)
        assertTrue(dismissedGlobalSettings)
        assertTrue(dismissedCookieManagement)
    }

    @Test
    fun threadScreenBindingSupport_wrapsSearchRefreshAndQuoteSelectionHandlers() {
        val matches = listOf(
            ThreadSearchMatch(
                postId = "1",
                postIndex = 2,
                highlightRanges = listOf(0..1)
            ),
            ThreadSearchMatch(
                postId = "2",
                postIndex = 5,
                highlightRanges = listOf(1..2)
            )
        )
        var currentSearchIndex = 0
        val scrollTargets = mutableListOf<Int?>()
        val searchCallbacks = buildThreadScreenSearchNavigationCallbacks(
            currentIndex = { currentSearchIndex },
            setCurrentIndex = { currentSearchIndex = it },
            matches = { matches },
            onScrollToPostIndex = { scrollTargets += it }
        )

        searchCallbacks.onSearchSubmit()
        assertEquals(0, currentSearchIndex)
        assertEquals(listOf<Int?>(2), scrollTargets)

        searchCallbacks.onSearchNext()
        assertEquals(1, currentSearchIndex)
        assertEquals(listOf<Int?>(2, 5), scrollTargets)

        searchCallbacks.onSearchPrev()
        assertEquals(0, currentSearchIndex)
        assertEquals(listOf<Int?>(2, 5, 2), scrollTargets)

        var startedRefresh: Pair<Int, Int>? = null
        val refreshHandler = buildThreadScreenRefreshHandler(
            isRefreshing = { false },
            currentFirstVisibleItemIndex = { 3 },
            currentFirstVisibleItemOffset = { 12 },
            onStartRefresh = { index, offset -> startedRefresh = index to offset }
        )
        refreshHandler()
        assertEquals(3 to 12, startedRefresh)

        startedRefresh = null
        buildThreadScreenRefreshHandler(
            isRefreshing = { true },
            currentFirstVisibleItemIndex = { 7 },
            currentFirstVisibleItemOffset = { 24 },
            onStartRefresh = { index, offset -> startedRefresh = index to offset }
        )()
        assertNull(startedRefresh)

        var overlayState = ThreadPostOverlayState(
            quoteSelectionState = ThreadQuoteSelectionState(
                targetPost = Post(
                    id = "10",
                    author = null,
                    subject = null,
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        var dialogState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(comment = "base")
        )
        val replyDialogBinding = ThreadReplyDialogStateBinding(
            currentState = { dialogState },
            setState = { dialogState = it }
        )
        val quoteConfirm = buildThreadScreenQuoteSelectionConfirmHandler(
            replyDialogBinding = replyDialogBinding,
            currentOverlayState = { overlayState },
            setOverlayState = { overlayState = it }
        )

        quoteConfirm(listOf(">No.1"))
        assertEquals("base\n>No.1\n", dialogState.draft.comment)
        assertTrue(dialogState.isVisible)
        assertNull(overlayState.quoteSelectionState.targetPost)
    }

    @Test
    fun threadScreenBindingSupport_bundlesInteractionHandlers() = runBlocking {
        var replyOpened = false
        var startedRefreshFromMenu = false
        var startedRefreshFromPull: Pair<Int, Int>? = null
        var selectedHistory: ThreadHistoryEntry? = null
        var drawerClosed = false
        var boardClicked = false
        var historyRefreshed = false
        var batchDeleted = false
        var settingsOpened = false
        val post = Post(
            id = "10",
            author = null,
            subject = null,
            timestamp = "now",
            posterId = "abc",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        val historyEntry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        var overlayState = emptyThreadPostOverlayState()
        var currentSearchIndex = 0
        val scrollTargets = mutableListOf<Int?>()
        val bundle = buildThreadScreenInteractionBindingsBundle(
            menuInputs = ThreadScreenMenuInteractionInputs(
                isRefreshing = { false },
                onOpenReplyDialog = { replyOpened = true },
                onScrollTop = {},
                onScrollBottom = {},
                onShowRefreshBusyMessage = {},
                onStartRefreshFromMenu = { startedRefreshFromMenu = true },
                onOpenGallery = {},
                onDelegateToSaveHandler = {},
                onShowFilterSheet = {},
                onShowSettingsSheet = {},
                onClearNgHeaderPrefill = {},
                onShowNgManagement = {},
                onOpenExternalApp = {},
                onShowReadAloudControls = {},
                onTogglePrivacy = {}
            ),
            searchInputs = ThreadScreenSearchInteractionInputs(
                currentSearchIndex = { currentSearchIndex },
                setCurrentSearchIndex = { currentSearchIndex = it },
                currentSearchMatches = {
                    listOf(
                        ThreadSearchMatch(
                            postId = "1",
                            postIndex = 2,
                            highlightRanges = listOf(0..1)
                        )
                    )
                },
                onScrollToSearchMatchPostIndex = { scrollTargets += it },
                onCloseDrawerAfterHistorySelection = { drawerClosed = true },
                onHistoryEntrySelected = { selectedHistory = it }
            ),
            postActionInputs = ThreadScreenPostActionInputs(
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "stored",
                currentSaidaneLabel = { null },
                isSelfPost = { false },
                onShowOptionalMessage = {},
                onSaidaneLabelUpdated = { _, _ -> },
                launchUnitAction = { _, _, onSuccess, block ->
                    runBlocking { block() }
                    onSuccess()
                },
                voteSaidane = {},
                requestDeletion = {}
            ),
            refreshInputs = ThreadScreenRefreshInteractionInputs(
                currentFirstVisibleItemIndex = { 3 },
                currentFirstVisibleItemOffset = { 12 },
                onStartRefreshFromPull = { index, offset -> startedRefreshFromPull = index to offset }
            ),
            historyDrawerInputs = ThreadScreenHistoryDrawerInputs(
                onHistoryEntryDismissed = {},
                onBoardClick = { boardClicked = true },
                onHistoryRefreshClick = { historyRefreshed = true },
                onHistoryBatchDeleteClick = { batchDeleted = true },
                onHistorySettingsClick = { settingsOpened = true }
            )
        )

        bundle.menuEntryHandler(ThreadMenuEntryId.Reply)
        bundle.menuEntryHandler(ThreadMenuEntryId.Refresh)
        bundle.searchNavigationCallbacks.onSearchSubmit()
        bundle.historySelectionHandler(historyEntry)
        bundle.refreshHandler()
        bundle.postActionHandlers.onOpenDeleteDialog(post)
        bundle.historyDrawerCallbacks.onBoardClick()
        bundle.historyDrawerCallbacks.onRefreshClick()
        bundle.historyDrawerCallbacks.onBatchDeleteClick()
        bundle.historyDrawerCallbacks.onSettingsClick()

        assertTrue(replyOpened)
        assertTrue(startedRefreshFromMenu)
        assertEquals(listOf<Int?>(2), scrollTargets)
        assertEquals(historyEntry, selectedHistory)
        assertTrue(drawerClosed)
        assertEquals(3 to 12, startedRefreshFromPull)
        assertEquals(post, overlayState.deleteDialogState.targetPost)
        assertTrue(boardClicked)
        assertTrue(historyRefreshed)
        assertTrue(batchDeleted)
        assertTrue(settingsOpened)
    }

    @Test
    fun threadScreenControllerBindingsSupport_bundlesActionAndInteraction() = runBlocking {
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        var historyRefreshing = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = null,
            status = ReadAloudStatus.Idle,
            currentIndex = 0,
            cancelRequestedByUser = false
        )
        var replyOpened = false
        var refreshStarted = false
        var historyRefreshCount = 0
        var openedSettings = false
        val entry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        var selectedEntry: ThreadHistoryEntry? = null
        var overlayState = emptyThreadPostOverlayState()
        val actionStateBindings = ThreadScreenActionStateBindings(
            currentActionInProgress = { actionInProgress },
            setActionInProgress = { actionInProgress = it },
            currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
            setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
        )
        val actionDependencies = ThreadScreenActionDependencies(
            busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
            showMessage = {},
            onDebugLog = {},
            onInfoLog = {},
            onErrorLog = { _, _ -> }
        )
        val historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
            currentIsHistoryRefreshing = { historyRefreshing },
            setIsHistoryRefreshing = { historyRefreshing = it }
        )
        val readAloudStateBindings = ThreadScreenReadAloudStateBindings(
            currentState = { readAloudState },
            setState = { readAloudState = it }
        )
        val readAloudCallbacks = ThreadScreenReadAloudCallbacks(
            showMessage = {},
            showOptionalMessage = {},
            scrollToPostIndex = {},
            speakText = {},
            cancelActiveReadAloud = {}
        )
        val readAloudDependencies = ThreadScreenReadAloudDependencies(
            currentSegments = { emptyList() }
        )
        val bundle = buildThreadScreenControllerBindingsBundle(
            actionInputs = ThreadScreenControllerActionInputs(
                coroutineScope = this,
                actionStateBindings = actionStateBindings,
                actionDependencies = actionDependencies,
                historyRefreshStateBindings = historyRefreshStateBindings,
                onHistoryRefresh = { historyRefreshCount++ },
                showHistoryRefreshMessage = {},
                readAloudStateBindings = readAloudStateBindings,
                readAloudCallbacks = readAloudCallbacks,
                readAloudDependencies = readAloudDependencies
            ),
            interactionInputs = ThreadScreenControllerInteractionInputs(
                isRefreshing = { false },
                onOpenReplyDialog = { replyOpened = true },
                onScrollTop = {},
                onScrollBottom = {},
                onShowRefreshBusyMessage = {},
                onStartRefreshFromMenu = { refreshStarted = true },
                onOpenGallery = {},
                onDelegateToSaveHandler = {},
                onShowFilterSheet = {},
                onShowSettingsSheet = { openedSettings = true },
                onClearNgHeaderPrefill = {},
                onShowNgManagement = {},
                onOpenExternalApp = {},
                onShowReadAloudControls = {},
                onTogglePrivacy = {},
                currentSearchIndex = { 0 },
                setCurrentSearchIndex = {},
                currentSearchMatches = { emptyList() },
                onScrollToSearchMatchPostIndex = {},
                onCloseDrawerAfterHistorySelection = {},
                onHistoryEntrySelected = { selectedEntry = it },
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "",
                currentSaidaneLabel = { null },
                isSelfPost = { false },
                onSaidaneLabelUpdated = { _, _ -> },
                repository = FakeBoardRepository(),
                effectiveBoardUrl = "https://example.com/test",
                threadId = "123",
                currentFirstVisibleItemIndex = { 0 },
                currentFirstVisibleItemOffset = { 0 },
                onStartRefreshFromPull = { _, _ -> },
                onHistoryEntryDismissed = {},
                onBoardClick = {},
                onHistoryBatchDeleteClick = {},
                onHistorySettingsClick = { openedSettings = true }
            )
        )

        bundle.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Reply)
        bundle.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Refresh)
        bundle.actionExecutionBindings.historyRefreshBindings.handleHistoryRefresh()
        yield()
        bundle.interactionBindings.historySelectionHandler(entry)
        bundle.interactionBindings.historyDrawerCallbacks.onSettingsClick()

        assertTrue(replyOpened)
        assertTrue(refreshStarted)
        assertEquals(1, historyRefreshCount)
        assertEquals(entry, selectedEntry)
        assertTrue(openedSettings)
    }

    @Test
    fun threadScreenInteractionUiAggregateSupport_bundlesMediaControllerAndUiBindings() = runBlocking {
        var previewState = ThreadMediaPreviewState(previewMediaIndex = 0)
        val mediaEntries = listOf(
            MediaPreviewEntry(
                url = "https://example.com/1.png",
                mediaType = MediaType.Image,
                postId = "1",
                title = "one"
            ),
            MediaPreviewEntry(
                url = "https://example.com/2.png",
                mediaType = MediaType.Image,
                postId = "2",
                title = "two"
            )
        )
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        var historyRefreshing = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = null,
            status = ReadAloudStatus.Idle,
            currentIndex = 0,
            cancelRequestedByUser = false
        )
        var searchQuery = ""
        var searchClosed = false
        var historyOpened = false
        var searchOpened = false
        var globalSettingsOpened = false
        var replyDialogState = ThreadReplyDialogState(isVisible = false)
        val replyDialogBinding = ThreadReplyDialogStateBinding(
            currentState = { replyDialogState },
            setState = { replyDialogState = it }
        )
        var overlayState = emptyThreadPostOverlayState()
        var dismissedGallery = false
        var scrolledToPostIndex: Int? = null
        var filterState = ThreadFilterUiState()
        val filterBinding = ThreadFilterUiStateBinding(
            currentState = { filterState },
            setState = { filterState = it }
        )
        var dismissedNgManagement = false
        var dismissedSaveProgress = false
        var cancelledSaveProgress = false
        var dismissedGlobalSettings = false
        var backgroundRefreshChanged: Boolean? = null
        var lightweightModeChanged: Boolean? = null
        var manualSaveDirectory: String? = null
        var saveDirectorySelection: SaveDirectorySelection? = null
        var openedDirectoryPicker = false
        var openedCookieManager = false
        var selectedFileManager: Pair<String, String>? = null
        var clearedPreferredFileManager = false
        var updatedThreadMenuEntries: List<ThreadMenuEntryConfig>? = null
        var updatedCatalogNavEntries: List<CatalogNavEntryConfig>? = null
        var dismissedCookieManagement = false
        var addedNgHeader: String? = null
        var replyOpened = false
        var startedRefreshFromMenu = false
        val galleryPosts = listOf(
            Post(
                id = "1",
                author = null,
                subject = null,
                timestamp = "now",
                messageHtml = "first",
                imageUrl = null,
                thumbnailUrl = null
            ),
            Post(
                id = "2",
                author = null,
                subject = null,
                timestamp = "now",
                messageHtml = "second",
                imageUrl = null,
                thumbnailUrl = null
            )
        )
        val searchMatches = listOf(
            ThreadSearchMatch(
                postId = "1",
                postIndex = 3,
                highlightRanges = listOf(0..1)
            )
        )
        var currentSearchIndex = 0
        val historyEntry = ThreadHistoryEntry(
            threadId = "thread-1",
            boardId = "board",
            title = "title",
            titleImageUrl = "",
            boardName = "Board",
            boardUrl = "https://example.com/board",
            lastVisitedEpochMillis = 1L,
            replyCount = 2
        )
        var selectedHistoryEntry: ThreadHistoryEntry? = null
        var drawerClosed = false
        val actionStateBindings = ThreadScreenActionStateBindings(
            currentActionInProgress = { actionInProgress },
            setActionInProgress = { actionInProgress = it },
            currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
            setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
        )
        val actionDependencies = ThreadScreenActionDependencies(
            busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
            showMessage = {},
            onDebugLog = {},
            onInfoLog = {},
            onErrorLog = { _, _ -> }
        )
        val historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
            currentIsHistoryRefreshing = { historyRefreshing },
            setIsHistoryRefreshing = { historyRefreshing = it }
        )
        val readAloudStateBindings = ThreadScreenReadAloudStateBindings(
            currentState = { readAloudState },
            setState = { readAloudState = it }
        )
        val readAloudCallbacks = ThreadScreenReadAloudCallbacks(
            showMessage = {},
            showOptionalMessage = {},
            scrollToPostIndex = {},
            speakText = {},
            cancelActiveReadAloud = {}
        )
        val readAloudDependencies = ThreadScreenReadAloudDependencies(
            currentSegments = { emptyList() }
        )
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = { backgroundRefreshChanged = it },
            onLightweightModeChanged = { lightweightModeChanged = it },
            onManualSaveDirectoryChanged = { manualSaveDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onOpenSaveDirectoryPicker = { openedDirectoryPicker = true },
            onFileManagerSelected = { packageName, label ->
                selectedFileManager = packageName to label
            },
            onClearPreferredFileManager = { clearedPreferredFileManager = true },
            onThreadMenuEntriesChanged = { updatedThreadMenuEntries = it },
            onCatalogNavEntriesChanged = { updatedCatalogNavEntries = it }
        )

        val bundle = buildThreadScreenInteractionUiAggregateBundle(
            mediaInputs = ThreadScreenAggregateMediaInputs(
                currentPreviewState = { previewState },
                setPreviewState = { previewState = it },
                currentMediaEntries = { mediaEntries }
            ),
            controllerActionInputs = ThreadScreenControllerActionInputs(
                coroutineScope = this,
                actionStateBindings = actionStateBindings,
                actionDependencies = actionDependencies,
                historyRefreshStateBindings = historyRefreshStateBindings,
                onHistoryRefresh = {},
                showHistoryRefreshMessage = {},
                readAloudStateBindings = readAloudStateBindings,
                readAloudCallbacks = readAloudCallbacks,
                readAloudDependencies = readAloudDependencies
            ),
            controllerInteractionInputs = ThreadScreenControllerInteractionInputs(
                isRefreshing = { false },
                onOpenReplyDialog = { replyOpened = true },
                onScrollTop = {},
                onScrollBottom = {},
                onShowRefreshBusyMessage = {},
                onStartRefreshFromMenu = { startedRefreshFromMenu = true },
                onOpenGallery = {},
                onDelegateToSaveHandler = {},
                onShowFilterSheet = {},
                onShowSettingsSheet = {},
                onClearNgHeaderPrefill = {},
                onShowNgManagement = {},
                onOpenExternalApp = {},
                onShowReadAloudControls = {},
                onTogglePrivacy = {},
                currentSearchIndex = { currentSearchIndex },
                setCurrentSearchIndex = { currentSearchIndex = it },
                currentSearchMatches = { searchMatches },
                onScrollToSearchMatchPostIndex = {},
                onCloseDrawerAfterHistorySelection = { drawerClosed = true },
                onHistoryEntrySelected = { selectedHistoryEntry = it },
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "stored",
                currentSaidaneLabel = { null },
                isSelfPost = { false },
                onSaidaneLabelUpdated = { _, _ -> },
                repository = FakeBoardRepository(),
                effectiveBoardUrl = "https://example.com/board",
                threadId = "thread-1",
                currentFirstVisibleItemIndex = { 0 },
                currentFirstVisibleItemOffset = { 0 },
                onStartRefreshFromPull = { _, _ -> },
                onHistoryEntryDismissed = {},
                onBoardClick = {},
                onHistoryBatchDeleteClick = {},
                onHistorySettingsClick = {}
            ),
            uiInputs = ThreadScreenAggregateUiInputs(
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = { searchClosed = true },
                onBack = {},
                onOpenHistory = { historyOpened = true },
                onSearch = { searchOpened = true },
                onOpenGlobalSettings = { globalSettingsOpened = true },
                replyDialogBinding = replyDialogBinding,
                mediaPreviewEntryCount = mediaEntries.size,
                onSavePreviewMedia = {},
                galleryPosts = galleryPosts,
                onDismissGallery = { dismissedGallery = true },
                onScrollToPostIndex = { scrolledToPostIndex = it },
                threadFilterBinding = filterBinding,
                onDismissSettingsSheet = {},
                onDismissFilterSheet = {},
                onApplySettingsActionState = {},
                firstVisibleSegmentIndex = { 1 },
                onPauseReadAloud = {},
                onStopReadAloud = {},
                onShowReadAloudStoppedMessage = {},
                onDismissReadAloudControls = {},
                onDismissNgManagement = { dismissedNgManagement = true },
                ngMutationCallbacks = ThreadNgMutationCallbacks(
                    onAddHeader = { addedNgHeader = it },
                    onAddWord = {},
                    onRemoveHeader = {},
                    onRemoveWord = {},
                    onToggleFiltering = {}
                ),
                onDismissSaveProgress = { dismissedSaveProgress = true },
                onCancelSaveProgress = { cancelledSaveProgress = true },
                onDismissGlobalSettings = { dismissedGlobalSettings = true },
                screenPreferencesCallbacks = preferencesCallbacks,
                onOpenCookieManager = { openedCookieManager = true },
                onDismissCookieManagement = { dismissedCookieManagement = true }
            )
        )

        bundle.mediaBindings.onMediaClick(mediaEntries[1].url, mediaEntries[1].mediaType)
        bundle.controllerBindings.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Reply)
        bundle.controllerBindings.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Refresh)
        bundle.controllerBindings.interactionBindings.historySelectionHandler(historyEntry)
        bundle.uiBindings.topBarCallbacks.onSearchQueryChange("abc")
        bundle.uiBindings.topBarCallbacks.onOpenHistory()
        bundle.uiBindings.topBarCallbacks.onSearch()
        bundle.uiBindings.topBarCallbacks.onMenuSettings()
        bundle.uiBindings.galleryCallbacks?.onImageClick(galleryPosts[1])
        bundle.uiBindings.galleryCallbacks?.onDismiss()
        bundle.uiBindings.ngManagementCallbacks.onAddHeader("header")
        bundle.uiBindings.ngManagementCallbacks.onDismiss()
        bundle.uiBindings.saveProgressDialogCallbacks.onDismissRequest()
        bundle.uiBindings.saveProgressDialogCallbacks.onCancelRequest()
        bundle.uiBindings.globalSettingsCallbacks.onBackgroundRefreshChanged(true)
        bundle.uiBindings.globalSettingsCallbacks.onLightweightModeChanged(false)
        bundle.uiBindings.globalSettingsCallbacks.onManualSaveDirectoryChanged("/tmp/save")
        bundle.uiBindings.globalSettingsCallbacks.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        bundle.uiBindings.globalSettingsCallbacks.onOpenSaveDirectoryPicker?.invoke()
        bundle.uiBindings.globalSettingsCallbacks.onOpenCookieManager?.invoke()
        bundle.uiBindings.globalSettingsCallbacks.onFileManagerSelected?.invoke("pkg", "label")
        bundle.uiBindings.globalSettingsCallbacks.onClearPreferredFileManager?.invoke()
        bundle.uiBindings.globalSettingsCallbacks.onThreadMenuEntriesChanged(emptyList())
        bundle.uiBindings.globalSettingsCallbacks.onCatalogNavEntriesChanged(emptyList())
        bundle.uiBindings.globalSettingsCallbacks.onBack()
        bundle.uiBindings.cookieManagementCallbacks.onBack()

        assertEquals(1, previewState.previewMediaIndex)
        assertTrue(replyOpened)
        assertTrue(startedRefreshFromMenu)
        assertEquals(historyEntry, selectedHistoryEntry)
        assertTrue(drawerClosed)
        assertEquals("abc", searchQuery)
        assertTrue(searchClosed.not())
        assertTrue(historyOpened)
        assertTrue(searchOpened)
        assertTrue(globalSettingsOpened)
        assertTrue(dismissedGallery)
        assertEquals(1, scrolledToPostIndex)
        assertEquals("header", addedNgHeader)
        assertTrue(dismissedNgManagement)
        assertTrue(dismissedSaveProgress)
        assertTrue(cancelledSaveProgress)
        assertTrue(dismissedGlobalSettings)
        assertEquals(true, backgroundRefreshChanged)
        assertEquals(false, lightweightModeChanged)
        assertEquals("/tmp/save", manualSaveDirectory)
        assertEquals(SaveDirectorySelection.PICKER, saveDirectorySelection)
        assertTrue(openedDirectoryPicker)
        assertTrue(openedCookieManager)
        assertEquals("pkg" to "label", selectedFileManager)
        assertTrue(clearedPreferredFileManager)
        assertEquals(emptyList<ThreadMenuEntryConfig>(), updatedThreadMenuEntries)
        assertEquals(emptyList<CatalogNavEntryConfig>(), updatedCatalogNavEntries)
        assertTrue(dismissedCookieManagement)
    }

    @Test
    fun appendQuoteSelectionToReplyDraft_updatesCommentOnlyWhenSelectionExists() {
        val draft = ThreadReplyDraft(
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "既存",
            password = "pass"
        )

        val updated = appendQuoteSelectionToReplyDraft(draft, listOf(">No.1", ">引用"))

        assertEquals(
            ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "既存\n>No.1\n>引用\n",
                password = "pass"
            ),
            updated
        )
        assertEquals(null, appendQuoteSelectionToReplyDraft(draft, emptyList()))
    }

    @Test
    fun threadPostOverlayHelpers_reduceActionSheetDialogQuoteAndNgState() {
        val post = Post(
            id = "123",
            order = 1,
            author = "name",
            subject = null,
            timestamp = "now",
            posterId = "abc123",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null,
            saidaneLabel = null
        )
        val initial = emptyThreadPostOverlayState()

        val openedAction = openThreadPostActionOverlay(initial, post)
        assertEquals(post, openedAction.actionSheetState.targetPost)
        assertTrue(openedAction.actionSheetState.isActionSheetVisible)

        val openedDelete = openThreadDeleteOverlay(openedAction, post, "stored")
        assertEquals(post, openedDelete.deleteDialogState.targetPost)
        assertEquals("stored", openedDelete.deleteDialogState.password)
        assertFalse(openedDelete.actionSheetState.isActionSheetVisible)

        val updatedDelete = updateThreadDeleteOverlayInput(
            currentState = openedDelete,
            password = "next",
            imageOnly = true
        )
        assertEquals("next", updatedDelete.deleteDialogState.password)
        assertTrue(updatedDelete.deleteDialogState.imageOnly)

        val deleteConfirm = confirmThreadDeleteDialog(" next ", true)
        val confirmedDelete = applyThreadDeleteConfirmState(updatedDelete, deleteConfirm)
        assertEquals(null, confirmedDelete.deleteDialogState.targetPost)
        assertEquals("", confirmedDelete.deleteDialogState.password)

        val openedQuote = openThreadQuoteOverlay(confirmedDelete, post)
        assertEquals(post, openedQuote.quoteSelectionState.targetPost)
        assertEquals(null, dismissThreadQuoteOverlay(openedQuote).quoteSelectionState.targetPost)
        assertFalse(openedQuote.actionSheetState.isActionSheetVisible)
        assertEquals(null, openedQuote.deleteDialogState.targetPost)

        val ngOverlay = resolveThreadNgRegistrationOverlayState(initial, post)
        assertTrue(ngOverlay.overlayState.isNgManagementVisible)
        assertEquals("abc123", ngOverlay.overlayState.ngHeaderPrefill)
        assertEquals(null, ngOverlay.message)

        val dismissedNg = dismissThreadNgManagementOverlay(ngOverlay.overlayState)
        assertFalse(dismissedNg.isNgManagementVisible)
        assertEquals(null, dismissedNg.ngHeaderPrefill)
    }

    @Test
    fun threadSheetOverlayHelpers_reduceSettingsFilterAndReadAloudState() {
        val initial = emptyThreadSheetOverlayState()

        val openedSettings = openThreadSettingsOverlay(initial)
        assertTrue(openedSettings.isSettingsVisible)
        assertFalse(openedSettings.isFilterVisible)

        val openedFilter = openThreadFilterOverlay(openedSettings)
        assertTrue(openedFilter.isFilterVisible)

        val openedReadAloud = openThreadReadAloudOverlay(openedFilter)
        assertTrue(openedReadAloud.isReadAloudControlsVisible)

        val appliedSettingsAction = applyThreadSettingsActionOverlayState(
            currentState = openedSettings,
            actionState = ThreadSettingsActionState(
                closeSheet = true,
                showReadAloudControls = true,
                reopenSettingsSheet = true
            )
        )
        assertTrue(appliedSettingsAction.isSettingsVisible)
        assertTrue(appliedSettingsAction.isReadAloudControlsVisible)

        val dismissedSettings = dismissThreadSettingsOverlay(appliedSettingsAction)
        assertFalse(dismissedSettings.isSettingsVisible)
        val dismissedFilter = dismissThreadFilterOverlay(openedFilter)
        assertFalse(dismissedFilter.isFilterVisible)
        val dismissedReadAloud = dismissThreadReadAloudOverlay(openedReadAloud)
        assertFalse(dismissedReadAloud.isReadAloudControlsVisible)
    }

    @Test
    fun threadModalOverlayHelpers_reduceGallerySettingsAndCookieState() {
        val initial = emptyThreadModalOverlayState()

        val galleryOpened = openThreadGalleryOverlay(initial)
        assertTrue(galleryOpened.isGalleryVisible)
        assertFalse(galleryOpened.isGlobalSettingsVisible)

        val galleryDismissed = dismissThreadGalleryOverlay(galleryOpened)
        assertFalse(galleryDismissed.isGalleryVisible)

        val settingsOpened = openThreadGlobalSettingsOverlay(initial)
        assertTrue(settingsOpened.isGlobalSettingsVisible)
        assertFalse(settingsOpened.isCookieManagementVisible)

        val cookieOpened = openThreadCookieManagementOverlay(settingsOpened)
        assertFalse(cookieOpened.isGlobalSettingsVisible)
        assertTrue(cookieOpened.isCookieManagementVisible)

        val cookieDismissed = dismissThreadCookieManagementOverlay(cookieOpened)
        assertFalse(cookieDismissed.isCookieManagementVisible)

        val settingsDismissed = dismissThreadGlobalSettingsOverlay(settingsOpened)
        assertFalse(settingsDismissed.isGlobalSettingsVisible)
    }

    @Test
    fun threadFilterUiStateHelpers_toggleKeywordAndClear() {
        val initial = ThreadFilterUiState()
        val toggledSort = toggleThreadFilterOption(initial, ThreadFilterOption.HighSaidane)
        assertEquals(setOf(ThreadFilterOption.HighSaidane), toggledSort.options)
        assertEquals(ThreadFilterSortOption.Saidane, toggledSort.sortOption)

        val toggledPlain = toggleThreadFilterOption(toggledSort, ThreadFilterOption.Image)
        assertEquals(
            setOf(ThreadFilterOption.HighSaidane, ThreadFilterOption.Image),
            toggledPlain.options
        )
        assertEquals(ThreadFilterSortOption.Saidane, toggledPlain.sortOption)

        val updatedKeyword = updateThreadFilterKeyword(toggledPlain, "abc")
        assertEquals("abc", updatedKeyword.keyword)

        val cleared = clearThreadFilterUiState(updatedKeyword)
        assertEquals(ThreadFilterUiState(), cleared)
    }

    @Test
    fun readAloudHelpers_resolveStartPauseSeekAndIndexNormalization() {
        val segments = listOf(
            ReadAloudSegment(postIndex = 1, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 3, postId = "30", body = "b")
        )

        assertEquals(
            ReadAloudStartState(
                canStart = false,
                normalizedIndex = 0,
                message = "読み上げ対象がありません"
            ),
            resolveReadAloudStartState(segmentCount = 0, currentIndex = 5, isJobRunning = false)
        )
        assertEquals(
            ReadAloudStartState(
                canStart = false,
                normalizedIndex = 1,
                message = null
            ),
            resolveReadAloudStartState(segmentCount = 2, currentIndex = 1, isJobRunning = true)
        )
        assertEquals(
            ReadAloudStartState(
                canStart = true,
                normalizedIndex = 0,
                message = null
            ),
            resolveReadAloudStartState(segmentCount = 2, currentIndex = 5, isJobRunning = false)
        )
        assertEquals(
            ReadAloudPauseState(
                status = ReadAloudStatus.Paused(segments[0]),
                message = "読み上げを一時停止しました"
            ),
            resolveReadAloudPauseState(ReadAloudStatus.Speaking(segments[0]))
        )
        assertEquals(null, resolveReadAloudPauseState(ReadAloudStatus.Idle))
        assertEquals(
            ReadAloudSeekState(
                targetIndex = 1,
                targetSegment = segments[1],
                shouldRestart = true
            ),
            resolveReadAloudSeekState(
                segments = segments,
                status = ReadAloudStatus.Paused(segments[0]),
                targetIndex = 5
            )
        )
        assertEquals(
            ReadAloudSeekState(
                targetIndex = 0,
                targetSegment = segments[0],
                shouldRestart = false
            ),
            resolveReadAloudSeekState(
                segments = segments,
                status = ReadAloudStatus.Idle,
                targetIndex = -1
            )
        )
        assertEquals(null, resolveReadAloudSeekState(emptyList(), ReadAloudStatus.Idle, 0))
        assertEquals(2, normalizeReadAloudCurrentIndex(currentIndex = 5, segmentCount = 2))
        assertEquals(0, normalizeReadAloudCurrentIndex(currentIndex = -1, segmentCount = 2))
    }

    @Test
    fun threadNgHelpers_validateDeduplicateRemoveAndToggle() {
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("ID:abc"),
                message = "NGヘッダーに含める文字列を入力してください",
                shouldPersist = false
            ),
            addThreadNgHeader(existingEntries = listOf("ID:abc"), input = "   ")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("ID:abc"),
                message = "そのNGヘッダーはすでに登録されています",
                shouldPersist = false
            ),
            addThreadNgHeader(existingEntries = listOf("ID:abc"), input = " id:ABC ")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("ID:abc", "ID:def"),
                message = "NGヘッダーを追加しました",
                shouldPersist = true
            ),
            addThreadNgHeader(existingEntries = listOf("ID:abc"), input = " ID:def ")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("spam"),
                message = "NGワードを削除しました",
                shouldPersist = true
            ),
            removeThreadNgWord(existingEntries = listOf("spam", "ads"), entry = "ads")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("spam"),
                message = "そのNGワードはすでに登録されています",
                shouldPersist = false
            ),
            addThreadNgWord(existingEntries = listOf("spam"), input = " Spam ")
        )
        assertEquals(
            ThreadNgFilterToggleState(
                isEnabled = false,
                message = "NG表示を無効にしました"
            ),
            toggleThreadNgFiltering(currentEnabled = true)
        )
    }

    @Test
    fun normalizeArchiveQuery_trimsWhitespaceAndMaxLength() {
        assertEquals("hello world", normalizeArchiveQuery("  hello   world  ", maxLength = 32))
        assertEquals("abc d", normalizeArchiveQuery("abc   def", maxLength = 5))
        assertEquals("", normalizeArchiveQuery("   ", maxLength = 10))
        assertEquals(
            listOf("12345", "thread title"),
            buildArchiveFallbackQueryCandidates(
                threadId = " 12345 ",
                threadTitle = " thread   title "
            )
        )
        assertEquals(
            listOf("same"),
            buildArchiveFallbackQueryCandidates(
                threadId = "same",
                threadTitle = " same "
            )
        )
    }

    @Test
    fun resolveThreadRemoteFetchRequest_prefersMatchingThreadUrlOnly() {
        assertEquals(
            ThreadRemoteFetchRequest.ByUrl("https://may.2chan.net/b/res/123.htm"),
            resolveThreadRemoteFetchRequest(
                threadUrl = " https://may.2chan.net/b/res/123.htm ",
                targetThreadId = "123",
                boardUrl = "https://may.2chan.net/b"
            )
        )
        assertEquals(
            ThreadRemoteFetchRequest.ByBoard(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123"
            ),
            resolveThreadRemoteFetchRequest(
                threadUrl = "https://may.2chan.net/b/res/999.htm",
                targetThreadId = "123",
                boardUrl = "https://may.2chan.net/b"
            )
        )
        assertEquals(
            ThreadRemoteFetchRequest.ByBoard(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123"
            ),
            resolveThreadRemoteFetchRequest(
                threadUrl = "https://may.2chan.net/b/futaba.php?mode=cat",
                targetThreadId = "123",
                boardUrl = "https://may.2chan.net/b"
            )
        )
    }

    @Test
    fun isOfflineFallbackCandidate_detectsNetworkConditions() {
        assertTrue(isOfflineFallbackCandidate(NetworkException("HTTP error", statusCode = 404)))
        assertTrue(isOfflineFallbackCandidate(IllegalStateException("connection timeout")))
        assertTrue(isOfflineFallbackCandidate(RuntimeException("wrapper", NetworkException("dns failure"))))
        assertFalse(isOfflineFallbackCandidate(IllegalArgumentException("bad input")))
        assertEquals(
            ThreadLoadFallbackState(
                statusCode = 404,
                shouldTryArchiveFallback = true,
                shouldTryOfflineFallback = true,
                shouldThrowWhenArchiveNotFound = false
            ),
            resolveThreadLoadFallbackState(
                error = NetworkException("HTTP error", statusCode = 404),
                allowOfflineFallback = true
            )
        )
        assertEquals(
            ThreadLoadFallbackState(
                statusCode = 410,
                shouldTryArchiveFallback = true,
                shouldTryOfflineFallback = false,
                shouldThrowWhenArchiveNotFound = true
            ),
            resolveThreadLoadFallbackState(
                error = NetworkException("Gone", statusCode = 410),
                allowOfflineFallback = false
            )
        )
        assertEquals(
            "Archive fallback timed out for threadId=123 after 20000ms",
            buildArchiveFallbackTimeoutMessage("123", 20_000L)
        )
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "may/b",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = emptyList()
        )
        assertEquals(
            ArchiveFallbackOutcome.Success(page, "https://may.2chan.net/b/res/123.htm"),
            resolveArchiveThreadFetchOutcome(
                page = page,
                error = null,
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            ArchiveFallbackOutcome.NotFound,
            resolveArchiveThreadFetchOutcome(
                page = null,
                error = NetworkException("Gone", statusCode = 410),
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            ArchiveFallbackOutcome.NoMatch,
            resolveArchiveThreadFetchOutcome(
                page = null,
                error = IllegalStateException("boom"),
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            "Archive search failed for 123: boom",
            buildArchiveSearchFailureLogMessage("123", IllegalStateException("boom"))
        )
        assertEquals(
            "Archive refresh succeeded for 123",
            buildArchiveRefreshSuccessLogMessage("123")
        )
        assertEquals(
            ThreadArchiveFallbackPlan(
                scope = ArchiveSearchScope(server = "may", board = "b"),
                queryCandidates = listOf("123", "thread title")
            ),
            buildThreadArchiveFallbackPlan(
                threadId = "123",
                threadTitle = " thread   title ",
                boardUrl = "https://dec.2chan.net/img/futaba.php",
                threadUrlOverride = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            "https://may.2chan.net/b/res/123.htm",
            resolveArchiveFallbackMatchUrl(
                items = listOf(
                    ArchiveSearchItem(
                        threadId = "123",
                        server = "may",
                        board = "b",
                        htmlUrl = "https://may.2chan.net/b/res/123.htm",
                        uploadedAt = 2L
                    ),
                    ArchiveSearchItem(
                        threadId = "123",
                        server = "may",
                        board = "b",
                        htmlUrl = "https://may.2chan.net/b/res/122.htm",
                        uploadedAt = 1L
                    )
                ),
                threadId = "123"
            )
        )
        assertEquals(
            ArchiveFallbackAttemptState(
                outcome = ArchiveFallbackOutcome.Success(page, "https://may.2chan.net/b/res/123.htm"),
                successLogMessage = "Archive refresh succeeded for 123"
            ),
            resolveArchiveFallbackAttemptState(
                threadId = "123",
                threadUrl = "https://may.2chan.net/b/res/123.htm",
                page = page,
                error = null
            )
        )
        assertEquals(
            ArchiveFallbackAttemptState(
                outcome = ArchiveFallbackOutcome.NotFound,
                successLogMessage = null
            ),
            resolveArchiveFallbackAttemptState(
                threadId = "123",
                threadUrl = "https://may.2chan.net/b/res/123.htm",
                page = null,
                error = NetworkException("Gone", statusCode = 410)
            )
        )
        assertEquals(
            ThreadLoadPostArchiveDecision.TryOffline,
            resolveThreadLoadPostArchiveDecision(
                primaryError = NetworkException("HTTP error", statusCode = 404),
                fallbackState = resolveThreadLoadFallbackState(
                    error = NetworkException("HTTP error", statusCode = 404),
                    allowOfflineFallback = true
                ),
                archiveOutcome = ArchiveFallbackOutcome.NotFound
            )
        )
        val archiveFailureDecision = resolveThreadLoadPostArchiveDecision(
            primaryError = NetworkException("Gone", statusCode = 410),
            fallbackState = resolveThreadLoadFallbackState(
                error = NetworkException("Gone", statusCode = 410),
                allowOfflineFallback = false
            ),
            archiveOutcome = ArchiveFallbackOutcome.NotFound
        )
        assertTrue(archiveFailureDecision is ThreadLoadPostArchiveDecision.Fail)
        val archiveError = archiveFailureDecision.error
        assertTrue(archiveError is NetworkException)
        assertEquals("Gone", archiveError.message)
        assertEquals(410, archiveError.statusCode)
        assertEquals(
            ThreadLoadPostArchiveDecision.UseArchive(
                page = page,
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            ),
            resolveThreadLoadPostArchiveDecision(
                primaryError = IllegalStateException("x"),
                fallbackState = resolveThreadLoadFallbackState(
                    error = IllegalStateException("x"),
                    allowOfflineFallback = true
                ),
                archiveOutcome = ArchiveFallbackOutcome.Success(
                    page = page,
                    threadUrl = "https://may.2chan.net/b/res/123.htm"
                )
            )
        )
        assertEquals(
            ThreadLoadPostOfflineDecision.UseOffline(page),
            resolveThreadLoadPostOfflineDecision(
                primaryError = IllegalStateException("x"),
                offlinePage = page
            )
        )
        val offlineFailureDecision = resolveThreadLoadPostOfflineDecision(
            primaryError = IllegalStateException("x"),
            offlinePage = null
        )
        assertTrue(offlineFailureDecision is ThreadLoadPostOfflineDecision.Fail)
        assertEquals("x", (offlineFailureDecision.error as IllegalStateException).message)
    }

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

    @Test
    fun threadMediaPreviewHelpers_reduceSelectionNavigationAndSaveRequestState() {
        val entries = listOf(
            MediaPreviewEntry(
                url = "https://may.2chan.net/b/src/1.png",
                mediaType = MediaType.Image,
                postId = "1",
                title = "one"
            ),
            MediaPreviewEntry(
                url = "https://may.2chan.net/b/src/2.webm",
                mediaType = MediaType.Video,
                postId = "2",
                title = "two"
            )
        )

        val initial = emptyThreadMediaPreviewState()
        val opened = openThreadMediaPreview(
            currentState = initial,
            entries = entries,
            url = entries[1].url,
            mediaType = MediaType.Video
        )
        assertEquals(1, opened.previewMediaIndex)
        assertEquals(entries[1], currentThreadMediaPreviewEntry(opened, entries))
        assertEquals(
            ThreadMediaPreviewDialogState(
                entry = entries[1],
                currentIndex = 1,
                totalCount = 2,
                isSaveEnabled = true,
                isSaveInProgress = false
            ),
            resolveThreadMediaPreviewDialogState(
                state = opened,
                entries = entries,
                isSaveInProgress = false
            )
        )

        val next = moveToNextThreadMediaPreview(opened, totalCount = entries.size)
        assertEquals(0, next.previewMediaIndex)

        val previous = moveToPreviousThreadMediaPreview(next, totalCount = entries.size)
        assertEquals(1, previous.previewMediaIndex)

        val normalized = normalizeThreadMediaPreviewState(
            currentState = previous,
            totalCount = 1
        )
        assertNull(normalized.previewMediaIndex)
        assertEquals(
            null,
            resolveThreadMediaPreviewNormalizationState(
                currentState = ThreadMediaPreviewState(previewMediaIndex = 0),
                totalCount = 2
            )
        )
        assertEquals(
            ThreadMediaPreviewState(previewMediaIndex = null),
            resolveThreadMediaPreviewNormalizationState(
                currentState = ThreadMediaPreviewState(previewMediaIndex = 0),
                totalCount = 0
            )
        )
        assertEquals(
            ThreadMediaPreviewState(previewMediaIndex = 0),
            resolveThreadMediaClickState(
                currentState = initial,
                entries = entries,
                url = entries[0].url,
                mediaType = MediaType.Image
            )
        )
        assertEquals(
            null,
            resolveThreadMediaClickState(
                currentState = initial,
                entries = entries,
                url = "missing",
                mediaType = MediaType.Image
            )
        )
        assertEquals(
            initial,
            openThreadMediaPreview(
                currentState = initial,
                entries = entries,
                url = "missing",
                mediaType = MediaType.Image
            )
        )
        assertNull(currentThreadMediaPreviewEntry(dismissThreadMediaPreview(opened), entries))
        assertEquals(
            ThreadMediaPreviewDialogState(
                entry = entries[1],
                currentIndex = 1,
                totalCount = 2,
                isSaveEnabled = false,
                isSaveInProgress = true
            ),
            resolveThreadMediaPreviewDialogState(
                state = opened,
                entries = entries,
                isSaveInProgress = true
            )
        )

        assertEquals(
            ThreadMediaSaveRequestState(
                canStartSave = false,
                message = buildThreadSaveBusyMessage()
            ),
            resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = true,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            ThreadMediaSaveRequestState(
                canStartSave = false,
                message = buildThreadSaveLocationRequiredMessage(),
                shouldOpenSaveDirectoryPicker = true
            ),
            resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            ThreadMediaSaveRequestState(canStartSave = true),
            resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
    }

    @Test
    fun threadAndMediaSaveAvailability_prioritizeBlockingReasons() {
        assertEquals(
            ThreadSaveAvailability.Busy,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = false,
                isThreadReady = false
            )
        )
        assertEquals(
            ThreadSaveAvailability.LocationRequired,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true,
                isThreadReady = true
            )
        )
        assertEquals(
            ThreadSaveAvailability.NotReady,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true,
                isThreadReady = false
            )
        )
        assertEquals(
            ThreadSaveAvailability.Unavailable,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = false,
                isThreadReady = true
            )
        )
        assertEquals(
            ThreadSaveAvailability.Ready,
            resolveThreadSaveAvailability(
                isAnySaveInProgress = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true,
                isThreadReady = true
            )
        )

        assertEquals(
            MediaSaveAvailability.Busy,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = true,
                isRemoteMedia = false,
                requiresManualLocationSelection = true,
                hasStorageDependencies = false
            )
        )
        assertEquals(
            MediaSaveAvailability.Unsupported,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.LocationRequired,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.Unavailable,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = false
            )
        )
        assertEquals(
            MediaSaveAvailability.Ready,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
    }

    @Test
    fun resolveReadAloudControlState_derivesPlaybackUiState() {
        val segments = listOf(
            ReadAloudSegment(postIndex = 0, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 1, postId = "20", body = "b"),
            ReadAloudSegment(postIndex = 2, postId = "30", body = "c")
        )

        val speaking = resolveReadAloudControlState(
            segments = segments,
            currentIndex = 1,
            visibleSegmentIndex = 2,
            status = ReadAloudStatus.Speaking(segments[1])
        )
        assertEquals(3, speaking.totalSegments)
        assertEquals(1, speaking.completedSegments)
        assertEquals("20", speaking.currentSegment?.postId)
        assertTrue(speaking.canSeek)
        assertEquals(1f, speaking.sliderValue)
        assertEquals("30", speaking.visiblePostId)
        assertTrue(speaking.canSeekToVisible)
        assertEquals("再生", speaking.playLabel)
        assertFalse(speaking.isPlayEnabled)
        assertTrue(speaking.isPauseEnabled)
        assertTrue(speaking.isStopEnabled)

        val paused = resolveReadAloudControlState(
            segments = segments,
            currentIndex = 5,
            visibleSegmentIndex = -1,
            status = ReadAloudStatus.Paused(segments[2])
        )
        assertEquals(3, paused.completedSegments)
        assertEquals(2f, paused.sliderValue)
        assertEquals("再開", paused.playLabel)
        assertTrue(paused.isPlayEnabled)
        assertFalse(paused.isPauseEnabled)
        assertTrue(paused.isStopEnabled)
        assertFalse(paused.canSeekToVisible)

        val idleEmpty = resolveReadAloudControlState(
            segments = emptyList(),
            currentIndex = 0,
            visibleSegmentIndex = 0,
            status = ReadAloudStatus.Idle
        )
        assertEquals(0, idleEmpty.totalSegments)
        assertEquals(0, idleEmpty.completedSegments)
        assertEquals(null, idleEmpty.currentSegment)
        assertFalse(idleEmpty.canSeek)
        assertEquals(0f, idleEmpty.sliderValue)
        assertEquals(null, idleEmpty.visiblePostId)
        assertFalse(idleEmpty.canSeekToVisible)
        assertFalse(idleEmpty.isPlayEnabled)
        assertFalse(idleEmpty.isPauseEnabled)
        assertFalse(idleEmpty.isStopEnabled)
    }

    @Test
    fun threadActionSheetAndSettingsHelpers_mapExpectedBehavior() {
        assertFalse(resolveThreadPostActionSheetState(isSelfPost = true).isSaidaneEnabled)
        assertTrue(resolveThreadPostActionSheetState(isSelfPost = false).isSaidaneEnabled)

        assertEquals(
            ThreadSettingsActionState(showNgManagement = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.NgManagement)
        )
        assertEquals(
            ThreadSettingsActionState(openExternalApp = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.ExternalApp)
        )
        assertEquals(
            ThreadSettingsActionState(togglePrivacy = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.Privacy)
        )
        assertEquals(
            ThreadSettingsActionState(showReadAloudControls = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.ReadAloud)
        )
        assertEquals(
            ThreadSettingsActionState(reopenSettingsSheet = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.Settings)
        )
        assertEquals(
            ThreadSettingsActionState(delegateToMainActionHandler = true),
            resolveThreadSettingsActionState(ThreadMenuEntryId.Refresh)
        )
        assertEquals(
            "https://may.2chan.net/img/res/123.htm",
            buildThreadExternalAppUrl(
                effectiveBoardUrl = "https://may.2chan.net/img/futaba.php",
                threadId = "123"
            )
        )
    }

    @Test
    fun threadMenuEntryResolvers_filterNormalizedEntriesByPlacement() {
        val entries = defaultThreadMenuEntries()

        assertEquals(
            listOf(
                ThreadMenuEntryId.Reply,
                ThreadMenuEntryId.ScrollToTop,
                ThreadMenuEntryId.ScrollToBottom,
                ThreadMenuEntryId.Refresh,
                ThreadMenuEntryId.Gallery,
                ThreadMenuEntryId.Save,
                ThreadMenuEntryId.Filter,
                ThreadMenuEntryId.Settings
            ),
            resolveThreadActionBarEntries(entries).map { it.id }
        )
        assertEquals(
            listOf(
                ThreadMenuEntryId.NgManagement,
                ThreadMenuEntryId.ExternalApp,
                ThreadMenuEntryId.ReadAloud,
                ThreadMenuEntryId.Privacy
            ),
            resolveThreadSettingsMenuEntries(entries).map { it.id }
        )
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
    fun threadManualSaveHelpers_buildSuccessAndPermissionRecoveryState() {
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            title = "title",
            storageId = null,
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 1,
            videoCount = 0,
            totalSize = 100L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        val successState = buildThreadManualSaveSuccessState(
            savedThread = savedThread,
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )

        val expectedPath = "/tmp/futacha/${buildThreadStorageId(savedThread.boardId, savedThread.threadId)}"
        assertEquals(expectedPath, successState.displayedSavePath)
        assertEquals("スレッドを保存しました: $expectedPath", successState.message)

        assertEquals(
            ThreadManualSaveErrorState(
                message = "保存先の権限が失われました。フォルダを再選択してください。",
                shouldResetManualSaveDirectory = true,
                shouldResetSaveDirectorySelection = true,
                shouldOpenDirectoryPicker = true
            ),
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("cannot resolve tree uri"),
                isUnexpected = false
            )
        )
        assertEquals(
            ThreadManualSaveErrorState(
                message = "保存に失敗しました: boom"
            ),
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("boom"),
                isUnexpected = false
            )
        )
        assertEquals(
            ThreadManualSaveErrorState(
                message = "エラーが発生しました: boom"
            ),
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("boom"),
                isUnexpected = true
            )
        )
    }

    @Test
    fun threadManualSaveRunner_mapsSuccessAndFailureKinds() = runBlocking {
        val config = ThreadManualSaveRunnerConfig(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b",
            title = "title",
            expiresAtLabel = "12:34",
            posts = emptyList(),
            baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
            baseDirectory = "/tmp/futacha"
        )
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "may-b",
            boardName = "may/b",
            title = "title",
            storageId = null,
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 1,
            videoCount = 0,
            totalSize = 100L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        assertEquals(
            ThreadManualSaveRunResult.Success(savedThread),
            performThreadManualSave(
                config = config,
                callbacks = ThreadManualSaveRunnerCallbacks(
                    saveThread = { Result.success(savedThread) }
                )
            )
        )

        val expectedFailure = performThreadManualSave(
            config = config,
            callbacks = ThreadManualSaveRunnerCallbacks(
                saveThread = { Result.failure(IllegalStateException("fail")) }
            )
        )
        assertTrue(expectedFailure is ThreadManualSaveRunResult.Failure)
        assertEquals(false, expectedFailure.isUnexpected)
        assertEquals("fail", expectedFailure.error.message)

        val unexpectedFailure = performThreadManualSave(
            config = config,
            callbacks = ThreadManualSaveRunnerCallbacks(
                saveThread = { throw IllegalStateException("boom") }
            )
        )
        assertTrue(unexpectedFailure is ThreadManualSaveRunResult.Failure)
        assertEquals(true, unexpectedFailure.isUnexpected)
        assertEquals("boom", unexpectedFailure.error.message)
    }

    @Test
    fun threadSaveRunnerConfigBuilders_mapInputsDirectly() {
        assertEquals(
            ThreadManualSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            ),
            buildThreadManualSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            )
        )
        assertEquals(
            ThreadSingleMediaSaveRunnerConfig(
                mediaUrl = "https://may.2chan.net/b/src/1.jpg",
                boardId = "b",
                threadId = "123",
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            ),
            buildThreadSingleMediaSaveRunnerConfig(
                mediaUrl = "https://may.2chan.net/b/src/1.jpg",
                boardId = "b",
                threadId = "123",
                baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
                baseDirectory = "/tmp/futacha"
            )
        )
        assertEquals(
            ThreadAutoSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                previousTimestampMillis = 10L,
                attemptStartedAtMillis = 20L,
                completionTimestampMillis = 30L
            ),
            buildThreadAutoSaveRunnerConfig(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b",
                title = "title",
                expiresAtLabel = "12:34",
                posts = emptyList(),
                previousTimestampMillis = 10L,
                attemptStartedAtMillis = 20L,
                completionTimestampMillis = 30L
            )
        )
    }

    @Test
    fun threadSaveRuntimeHelpers_clearOnlyMatchingTrackedJob() {
        val trackedJob = Job()
        val otherJob = Job()

        assertEquals(
            null,
            resolveTrackedJobAfterCompletion(
                trackedJob = trackedJob,
                runningJob = trackedJob
            )
        )
        assertEquals(
            trackedJob,
            resolveTrackedJobAfterCompletion(
                trackedJob = trackedJob,
                runningJob = otherJob
            )
        )
        assertEquals(
            null,
            resolveTrackedJobAfterCompletion(
                trackedJob = null,
                runningJob = otherJob
            )
        )
    }

    @Test
    fun threadSaveRuntimeHelpers_bundleSaveCallbacksAndOptionalMediaCallbacks() {
        val saveService = ThreadSaveService(
            httpClient = HttpClient(),
            fileSystem = InMemoryFileSystem()
        )

        val runtime = buildThreadSaveRuntime(saveService)

        assertSame(saveService, runtime.saveService)
        assertNotNull(runtime.manualCallbacks)
        assertNotNull(runtime.autoCallbacks)
        assertNull(
            buildOptionalThreadSingleMediaSaveRunnerCallbacks(
                httpClient = null,
                fileSystem = InMemoryFileSystem()
            )
        )
        assertNull(
            buildOptionalThreadSingleMediaSaveRunnerCallbacks(
                httpClient = HttpClient(),
                fileSystem = null
            )
        )
        assertNotNull(
            buildOptionalThreadSingleMediaSaveRunnerCallbacks(
                httpClient = HttpClient(),
                fileSystem = InMemoryFileSystem()
            )
        )
    }

    @Test
    fun threadActionRunnerHelpers_resolveBusyNoticeAndClassifyResults() = runBlocking {
        assertEquals(
            ThreadActionLaunchState(
                shouldLaunch = true,
                nextLastBusyNoticeAtMillis = 100L
            ),
            resolveThreadActionLaunchState(
                actionInProgress = false,
                lastBusyActionNoticeAtMillis = 100L,
                nowMillis = 150L,
                busyNoticeIntervalMillis = 1_000L
            )
        )
        assertEquals(
            ThreadActionLaunchState(
                shouldLaunch = false,
                nextLastBusyNoticeAtMillis = 1_500L,
                busyMessage = "処理中です…"
            ),
            resolveThreadActionLaunchState(
                actionInProgress = true,
                lastBusyActionNoticeAtMillis = 200L,
                nowMillis = 1_500L,
                busyNoticeIntervalMillis = 1_000L
            )
        )
        assertEquals(
            ThreadActionLaunchState(
                shouldLaunch = false,
                nextLastBusyNoticeAtMillis = 900L,
                busyMessage = null
            ),
            resolveThreadActionLaunchState(
                actionInProgress = true,
                lastBusyActionNoticeAtMillis = 900L,
                nowMillis = 1_200L,
                busyNoticeIntervalMillis = 1_000L
            )
        )

        assertEquals(
            ThreadActionRunResult.Success("ok"),
            performThreadAction { "ok" }
        )
        val failure = performThreadAction<String> { error("boom") }
        assertTrue(failure is ThreadActionRunResult.Failure)
        assertEquals("boom", failure.error.message)
        assertEquals("処理中です…", buildThreadActionBusyMessage())
        assertEquals(
            "Starting thread action: success='成功', failure='失敗'",
            buildThreadActionStartLogMessage("成功", "失敗")
        )
        assertEquals(
            "Thread action succeeded: 成功",
            buildThreadActionSuccessLogMessage("成功")
        )
        assertEquals(
            "Thread action failed: 失敗",
            buildThreadActionFailureLogMessage("失敗")
        )

        val busyMessages = mutableListOf<String>()
        val busyLaunchResult = launchManagedThreadAction(
            actionInProgress = true,
            lastBusyActionNoticeAtMillis = 200L,
            nowMillis = 1_500L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "失敗",
            callbacks = ThreadActionRuntimeCallbacks<String>(
                onActionInProgressChanged = {},
                onShowMessage = { busyMessages += it },
                onDebugLog = {},
                onInfoLog = {},
                onErrorLog = { _, _ -> }
            )
        ) {
            ThreadActionRunResult.Success("unused")
        }
        assertEquals(1_500L, busyLaunchResult.nextLastBusyNoticeAtMillis)
        assertEquals(null, busyLaunchResult.launchedJob)
        assertEquals(listOf("処理中です…"), busyMessages)

        val events = mutableListOf<String>()
        val successLaunchResult = launchManagedThreadAction(
            actionInProgress = false,
            lastBusyActionNoticeAtMillis = 100L,
            nowMillis = 150L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "失敗",
            callbacks = ThreadActionRuntimeCallbacks(
                onActionInProgressChanged = { events += "progress:$it" },
                onSuccess = { value: String -> events += "success:$value" },
                onShowMessage = { message -> events += "message:$message" },
                onDebugLog = { message -> events += "debug:$message" },
                onInfoLog = { message -> events += "info:$message" },
                onErrorLog = { message, _ -> events += "error:$message" }
            )
        ) {
            ThreadActionRunResult.Success("ok")
        }
        successLaunchResult.launchedJob?.join()
        assertEquals(100L, successLaunchResult.nextLastBusyNoticeAtMillis)
        assertEquals(
            listOf(
                "progress:true",
                "debug:Starting thread action: success='成功', failure='失敗'",
                "info:Thread action succeeded: 成功",
                "success:ok",
                "message:成功",
                "progress:false"
            ),
            events
        )

        events.clear()
        val failureLaunchResult = launchManagedThreadAction(
            actionInProgress = false,
            lastBusyActionNoticeAtMillis = 100L,
            nowMillis = 150L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "失敗",
            callbacks = ThreadActionRuntimeCallbacks<String>(
                onActionInProgressChanged = { events += "progress:$it" },
                onShowMessage = { message -> events += "message:$message" },
                onDebugLog = { message -> events += "debug:$message" },
                onInfoLog = { message -> events += "info:$message" },
                onErrorLog = { message, error -> events += "error:$message:${error.message}" }
            )
        ) {
            ThreadActionRunResult.Failure(IllegalStateException("boom"))
        }
        failureLaunchResult.launchedJob?.join()
        assertEquals(
            listOf(
                "progress:true",
                "debug:Starting thread action: success='成功', failure='失敗'",
                "error:Thread action failed: 失敗:boom",
                "message:失敗: boom",
                "progress:false"
            ),
            events
        )
    }

    @Test
    fun threadActionConfigHelpers_buildReplyDeleteAndExecuteCallbacks() = runBlocking {
        val draft = ThreadReplyDraft(
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "comment",
            password = "ignored",
            imageData = ImageData(
                bytes = byteArrayOf(1, 2, 3),
                fileName = "a.jpg"
            )
        )

        assertEquals(
            ThreadDeleteByUserActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                postId = "456",
                password = "pass",
                imageOnly = true
            ),
            buildThreadDeleteByUserActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                postId = "456",
                password = "pass",
                imageOnly = true
            )
        )
        assertEquals(
            ThreadReplyActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "comment",
                password = "trimmed",
                imageBytes = draft.imageData!!.bytes,
                imageFileName = "a.jpg",
                textOnly = false
            ),
            buildThreadReplyActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                draft = draft,
                normalizedPassword = "trimmed"
            )
        )

        assertEquals(
            ThreadActionRunResult.Success(Unit),
            performThreadDeleteByUserAction(
                config = buildThreadDeleteByUserActionConfig(
                    boardUrl = "https://may.2chan.net/b",
                    threadId = "123",
                    postId = "456",
                    password = "pass",
                    imageOnly = false
                ),
                callbacks = ThreadDeleteByUserActionCallbacks(
                    deleteByUser = {}
                )
            )
        )

        assertEquals(
            ThreadActionRunResult.Success("789"),
            performThreadReplyAction(
                config = buildThreadReplyActionConfig(
                    boardUrl = "https://may.2chan.net/b",
                    threadId = "123",
                    draft = draft,
                    normalizedPassword = "trimmed"
                ),
                callbacks = ThreadReplyActionCallbacks(
                    replyToThread = { "789" }
                )
            )
        )

        val deleteFailure = performThreadDeleteByUserAction(
            config = buildThreadDeleteByUserActionConfig(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123",
                postId = "456",
                password = "pass",
                imageOnly = false
            ),
            callbacks = ThreadDeleteByUserActionCallbacks(
                deleteByUser = { error("delete failed") }
            )
        )
        assertTrue(deleteFailure is ThreadActionRunResult.Failure)
        assertEquals("delete failed", deleteFailure.error.message)
    }

    @Test
    fun threadSaveUiOutcomeHelpers_resolveManualSingleAndAutoApplyStates() {
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            title = "title",
            storageId = "b__123",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 0,
            imageCount = 0,
            videoCount = 0,
            totalSize = 10L,
            status = com.valoser.futacha.shared.model.SaveStatus.COMPLETED
        )

        val manualOutcome = resolveThreadManualSaveUiOutcome(
            saveResult = ThreadManualSaveRunResult.Success(savedThread),
            threadId = "123",
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )
        assertTrue(manualOutcome is ThreadManualSaveUiOutcome.Success)
        assertEquals("Failed to index manually saved thread 123", manualOutcome.indexFailureMessage)
        assertEquals(
            "スレッドを保存しました: /tmp/futacha/b__123",
            manualOutcome.successState.message
        )

        val singleOutcome = resolveThreadSingleMediaSaveUiOutcome(
            saveResult = ThreadSingleMediaSaveRunResult.Success(
                SavedMediaFile(
                    fileName = "a.jpg",
                    relativePath = "b__123/images/a.jpg",
                    mediaType = SavedMediaType.IMAGE,
                    byteSize = 10L,
                    savedAtEpochMillis = 1L
                )
            ),
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )
        assertTrue(singleOutcome is ThreadSingleMediaSaveUiOutcome.Success)
        assertEquals("画像を保存しました: /tmp/futacha/b__123/images/a.jpg", singleOutcome.successState.message)

        val autoApplyState = buildThreadAutoSaveUiApplyState(
            completionState = ThreadAutoSaveCompletionState(
                nextTimestampMillis = 50L,
                savedThread = savedThread
            ),
            threadId = "123"
        )
        assertEquals(50L, autoApplyState.nextTimestampMillis)
        assertEquals(savedThread, autoApplyState.savedThread)
        assertEquals("Failed to index auto-saved thread 123", autoApplyState.indexFailureMessage)
    }

    @Test
    fun threadSingleMediaSaveHelpers_buildSuccessAndReuseRecoveryState() {
        val savedMedia = SavedMediaFile(
            fileName = "a.jpg",
            relativePath = "board__123/images/a.jpg",
            mediaType = SavedMediaType.IMAGE,
            byteSize = 10L,
            savedAtEpochMillis = 1L
        )

        val successState = buildThreadSingleMediaSaveSuccessState(
            savedMedia = savedMedia,
            manualSaveDirectory = "/tmp/futacha",
            manualSaveLocation = SaveLocation.Path("/tmp/futacha"),
            resolvedManualSaveDirectory = "/tmp/futacha"
        )

        assertEquals("画像", successState.mediaLabel)
        assertEquals("/tmp/futacha/board__123/images/a.jpg", successState.displayedSavePath)
        assertEquals("画像を保存しました: /tmp/futacha/board__123/images/a.jpg", successState.message)
        assertEquals(
            resolveThreadManualSaveErrorState(
                error = IllegalStateException("cannot resolve tree uri"),
                isUnexpected = true
            ),
            resolveThreadSingleMediaSaveErrorState(
                error = IllegalStateException("cannot resolve tree uri"),
                isUnexpected = true
            )
        )
    }

    @Test
    fun threadSingleMediaSaveRunner_mapsSuccessAndFailureKinds() = runBlocking {
        val config = ThreadSingleMediaSaveRunnerConfig(
            mediaUrl = "https://may.2chan.net/b/src/1.jpg",
            boardId = "b",
            threadId = "123",
            baseSaveLocation = SaveLocation.Path("/tmp/futacha"),
            baseDirectory = "/tmp/futacha"
        )
        val savedMedia = SavedMediaFile(
            fileName = "a.jpg",
            relativePath = "board__123/images/a.jpg",
            mediaType = SavedMediaType.IMAGE,
            byteSize = 10L,
            savedAtEpochMillis = 1L
        )

        assertEquals(
            ThreadSingleMediaSaveRunResult.Success(savedMedia),
            performThreadSingleMediaSave(
                config = config,
                callbacks = ThreadSingleMediaSaveRunnerCallbacks(
                    saveMedia = { Result.success(savedMedia) }
                )
            )
        )
        val expectedFailure = performThreadSingleMediaSave(
            config = config,
            callbacks = ThreadSingleMediaSaveRunnerCallbacks(
                saveMedia = { Result.failure(IllegalStateException("fail")) }
            )
        )
        assertTrue(expectedFailure is ThreadSingleMediaSaveRunResult.Failure)
        assertEquals(false, expectedFailure.isUnexpected)
        assertEquals("fail", expectedFailure.error.message)

        val unexpectedFailure = performThreadSingleMediaSave(
            config = config,
            callbacks = ThreadSingleMediaSaveRunnerCallbacks(
                saveMedia = { throw IllegalStateException("boom") }
            )
        )
        assertTrue(unexpectedFailure is ThreadSingleMediaSaveRunResult.Failure)
        assertEquals(true, unexpectedFailure.isUnexpected)
        assertEquals("boom", unexpectedFailure.error.message)
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
        assertEquals("履歴を更新しました", buildThreadHistoryRefreshSuccessMessage())
        assertEquals("履歴更新はすでに実行中です", buildThreadHistoryRefreshAlreadyRunningMessage())
        assertEquals(
            "履歴の更新に失敗しました: boom",
            buildThreadHistoryRefreshFailureMessage(IllegalStateException("boom"))
        )
        assertEquals("履歴を一括削除しました", buildThreadHistoryBatchDeleteMessage())
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
        val withSaidane = updateThreadFilterSelection(
            selectedOptions = setOf(ThreadFilterOption.Url),
            selectedSortOption = null,
            toggledOption = ThreadFilterOption.HighSaidane
        )
        assertEquals(
            setOf(ThreadFilterOption.Url, ThreadFilterOption.HighSaidane),
            withSaidane.selectedOptions
        )
        assertEquals(ThreadFilterSortOption.Saidane, withSaidane.selectedSortOption)

        val withReplies = updateThreadFilterSelection(
            selectedOptions = withSaidane.selectedOptions,
            selectedSortOption = withSaidane.selectedSortOption,
            toggledOption = ThreadFilterOption.HighReplies
        )
        assertEquals(
            setOf(ThreadFilterOption.Url, ThreadFilterOption.HighReplies),
            withReplies.selectedOptions
        )
        assertEquals(ThreadFilterSortOption.Replies, withReplies.selectedSortOption)

        val withoutReplies = updateThreadFilterSelection(
            selectedOptions = withReplies.selectedOptions,
            selectedSortOption = withReplies.selectedSortOption,
            toggledOption = ThreadFilterOption.HighReplies
        )
        assertEquals(setOf(ThreadFilterOption.Url), withoutReplies.selectedOptions)
        assertEquals(null, withoutReplies.selectedSortOption)
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
