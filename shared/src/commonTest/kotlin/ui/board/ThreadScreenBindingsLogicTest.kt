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

class ThreadScreenBindingsLogicTest {
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
                currentFilterOptions = { filterOptions },
                currentFilterSortOption = { filterSortOption },
                currentFilterKeyword = { filterKeyword },
                setFilterOptions = { filterOptions = it },
                setFilterSortOption = { filterSortOption = it },
                setFilterKeyword = { filterKeyword = it },
                currentReplyName = { replyName },
                currentReplyEmail = { replyEmail },
                currentReplySubject = { replySubject },
                currentReplyComment = { replyComment },
                currentReplyPassword = { replyPassword },
                currentReplyImageData = { replyImageData },
                setReplyName = { replyName = it },
                setReplyEmail = { replyEmail = it },
                setReplySubject = { replySubject = it },
                setReplyComment = { replyComment = it },
                setReplyPassword = { replyPassword = it },
                setReplyImageData = { replyImageData = it },
                isReplyDialogVisible = { isReplyDialogVisible },
                setReplyDialogVisible = { isReplyDialogVisible = it }
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
        val formBindings = buildThreadScreenFormBindings(
            ThreadScreenFormInputs(
                currentFilterOptions = { filterOptions },
                currentFilterSortOption = { filterSortOption },
                currentFilterKeyword = { filterKeyword },
                setFilterOptions = { filterOptions = it },
                setFilterSortOption = { filterSortOption = it },
                setFilterKeyword = { filterKeyword = it },
                currentReplyName = { replyName },
                currentReplyEmail = { replyEmail },
                currentReplySubject = { replySubject },
                currentReplyComment = { replyComment },
                currentReplyPassword = { replyPassword },
                currentReplyImageData = { replyImageData },
                setReplyName = { replyName = it },
                setReplyEmail = { replyEmail = it },
                setReplySubject = { replySubject = it },
                setReplyComment = { replyComment = it },
                setReplyPassword = { replyPassword = it },
                setReplyImageData = { replyImageData = it },
                isReplyDialogVisible = { isReplyDialogVisible },
                setReplyDialogVisible = { isReplyDialogVisible = it }
            )
        )

        messageNgBindings.ngMutationCallbacks.onAddHeader("header")
        formBindings.threadFilterBinding.setState(
            ThreadFilterUiState(
                options = setOf(ThreadFilterOption.Image),
                sortOption = ThreadFilterSortOption.Saidane,
                keyword = "abc"
            )
        )
        formBindings.replyDialogBinding.setState(
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
                loadRemoteByBoard = { _, _ -> ThreadPageContent(page = page) },
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
                    Result.success(Unit)
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
                    Result.success(Unit)
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
    fun threadScreenExecutionBindingsSupport_warnsWhenManualSaveIndexingFails() = runBlocking {
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
            status = SaveStatus.COMPLETED
        )
        var manualSaveJob: Job? = null
        var isManualSaveInProgress = false
        val shownMessages = mutableListOf<String>()

        val bindings = buildThreadScreenManualSaveBindings(
            coroutineScope = this,
            stateBindings = ThreadScreenManualSaveStateBindings(
                currentManualSaveJob = { manualSaveJob },
                setManualSaveJob = { manualSaveJob = it },
                setIsManualSaveInProgress = { isManualSaveInProgress = it },
                currentIsManualSaveInProgress = { isManualSaveInProgress },
                currentIsSingleMediaSaveInProgress = { false },
                setSaveProgress = { },
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
                performManualSave = { _, _ ->
                    ThreadManualSaveRunResult.Success(savedThread)
                },
                indexSavedThread = { _, _, _ ->
                    Result.failure(IllegalStateException("index failed"))
                }
            ),
            callbacks = ThreadScreenManualSaveCallbacks(
                showMessage = { shownMessages += it },
                applySaveErrorState = { error("should not apply save error state on index failure") }
            )
        )

        bindings.handleThreadSaveRequest()
        manualSaveJob?.join()

        assertEquals(
            listOf(buildThreadManualSaveIndexWarningMessage("/manual/storage")),
            shownMessages
        )
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
                currentFilterOptions = { filterOptions },
                currentFilterSortOption = { filterSortOption },
                currentFilterKeyword = { filterKeyword },
                setFilterOptions = { filterOptions = it },
                setFilterSortOption = { filterSortOption = it },
                setFilterKeyword = { filterKeyword = it },
                currentReplyName = { replyName },
                currentReplyEmail = { replyEmail },
                currentReplySubject = { replySubject },
                currentReplyComment = { replyComment },
                currentReplyPassword = { replyPassword },
                currentReplyImageData = { replyImageData },
                setReplyName = { replyName = it },
                setReplyEmail = { replyEmail = it },
                setReplySubject = { replySubject = it },
                setReplyComment = { replyComment = it },
                setReplyPassword = { replyPassword = it },
                setReplyImageData = { replyImageData = it },
                isReplyDialogVisible = { isReplyDialogVisible },
                setReplyDialogVisible = { isReplyDialogVisible = it }
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

}
