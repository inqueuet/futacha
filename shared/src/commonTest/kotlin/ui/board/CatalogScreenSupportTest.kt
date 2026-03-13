package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.model.SaveLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogScreenSupportTest {
    @Test
    fun catalogExecutionSupport_buildsExpectedMessages() {
        assertEquals("履歴を更新しました", buildCatalogHistoryRefreshSuccessMessage())
        assertEquals("履歴更新はすでに実行中です", buildCatalogHistoryRefreshBusyMessage())
        assertEquals(
            "履歴の更新に失敗しました: boom",
            buildCatalogHistoryRefreshFailureMessage(IllegalStateException("boom"))
        )
        assertEquals("カタログを更新しました", buildCatalogRefreshSuccessMessage())
        assertEquals("ネットワーククライアントが利用できません", buildCatalogPastThreadSearchClientUnavailableMessage())
    }

    @Test
    fun catalogCoreBindingsSupport_resolvesInitialMutableStateInputs() {
        val inputs = resolveCatalogScreenInitialMutableStateInputs(
            boardId = "may",
            persistedCatalogModes = mapOf("may" to CatalogMode.Many),
            initialArchiveSearchScope = ArchiveSearchScope(server = "may", board = "may"),
            displayStyle = CatalogDisplayStyle.List,
            gridColumns = 3
        )

        assertEquals(CatalogMode.Many, inputs.catalogMode)
        assertEquals(CatalogDisplayStyle.List, inputs.displayStyle)
        assertEquals(3, inputs.gridColumns)
        assertEquals("may", inputs.archiveSearchScope?.board)
    }

    @Test
    fun catalogExecutionSupport_persistsNgWordsAndWatchWordsWithoutStore() = runBlocking {
        var ngWords = emptyList<String>()
        var watchWords = emptyList<String>()
        val bindings = buildCatalogPersistenceBindings(
            coroutineScope = this,
            stateStore = null,
            onFallbackCatalogNgWordsChanged = { ngWords = it },
            onFallbackWatchWordsChanged = { watchWords = it }
        )

        bindings.persistCatalogNgWords(listOf("spam"))
        bindings.persistWatchWords(listOf("watch"))
        yield()

        assertEquals(listOf("spam"), ngWords)
        assertEquals(listOf("watch"), watchWords)
    }

    @Test
    fun catalogExecutionSupport_initialLoadShowsBoardMissingError() = runBlocking {
        var generation = 2L
        var runningJob: Job? = null
        var isRefreshing = true
        var uiState: CatalogUiState = CatalogUiState.Loading
        var lastItemCount = 1
        val bindings = buildCatalogInitialLoadBindings(
            coroutineScope = this,
            currentBoard = { null },
            currentCatalogMode = { CatalogMode.default },
            currentCatalogLoadGeneration = { generation },
            setCatalogLoadGeneration = { generation = it },
            currentCatalogLoadJob = { runningJob },
            setCatalogLoadJob = { runningJob = it },
            setIsRefreshing = { isRefreshing = it },
            setCatalogUiState = { uiState = it },
            setLastCatalogItems = { lastItemCount = it.size },
            loadCatalogItems = { _, _ -> error("should not load catalog without board") }
        )

        bindings.loadInitialCatalog()
        yield()

        assertEquals(3L, generation)
        assertEquals(null, runningJob)
        assertFalse(isRefreshing)
        assertEquals(CatalogUiState.Error("板が選択されていません"), uiState)
        assertEquals(1, lastItemCount)
    }

    @Test
    fun catalogExecutionSupport_initialLoadAppliesSuccessState() = runBlocking {
        val board = BoardSummary(
            id = "b",
            name = "Board",
            category = "cat",
            url = "https://may.2chan.net/b/futaba.php",
            description = ""
        )
        var generation = 0L
        var runningJob: Job? = null
        var isRefreshing = false
        var uiState: CatalogUiState = CatalogUiState.Error("before")
        var lastItemCount = -1
        val expectedItems = emptyList<com.valoser.futacha.shared.model.CatalogItem>()
        val bindings = buildCatalogInitialLoadBindings(
            coroutineScope = this,
            currentBoard = { board },
            currentCatalogMode = { CatalogMode.default },
            currentCatalogLoadGeneration = { generation },
            setCatalogLoadGeneration = { generation = it },
            currentCatalogLoadJob = { runningJob },
            setCatalogLoadJob = { runningJob = it },
            setIsRefreshing = { isRefreshing = it },
            setCatalogUiState = { uiState = it },
            setLastCatalogItems = { lastItemCount = it.size },
            loadCatalogItems = { _, _ -> expectedItems }
        )

        bindings.loadInitialCatalog()
        repeat(3) { yield() }

        assertEquals(1L, generation)
        assertEquals(null, runningJob)
        assertFalse(isRefreshing)
        assertEquals(CatalogUiState.Success(expectedItems), uiState)
        assertEquals(0, lastItemCount)
    }

    @Test
    fun catalogExecutionSupport_createThreadSubmitResetsDraftAndRefreshes() = runBlocking {
        val board = BoardSummary(
            id = "b",
            name = "Board",
            category = "cat",
            url = "https://may.2chan.net/b/futaba.php",
            description = ""
        )
        var draft = CreateThreadDraft(
            name = "name",
            email = "sage",
            title = "title",
            comment = "comment",
            password = "  delete-key  "
        )
        var image: ImageData? = ImageData(byteArrayOf(1, 2, 3), "sample.jpg")
        var showDialog = true
        var savedDeleteKey = ""
        var snackbarMessage: String? = null
        var refreshCount = 0
        val bindings = buildCatalogCreateThreadBindings(
            coroutineScope = this,
            activeRepository = FakeBoardRepository(),
            currentBoard = { board },
            currentDraft = { draft },
            currentImage = { image },
            setCreateThreadDraft = { draft = it },
            setCreateThreadImage = { image = it },
            setShowCreateThreadDialog = { showDialog = it },
            updateLastUsedDeleteKey = { savedDeleteKey = it },
            showSnackbar = { snackbarMessage = it },
            performRefresh = { refreshCount += 1 }
        )

        bindings.submitCreateThread()
        repeat(3) { yield() }

        assertFalse(showDialog)
        assertEquals("delete-key", savedDeleteKey)
        assertEquals(emptyCreateThreadDraft(), draft)
        assertEquals(null, image)
        assertEquals("スレッドを作成しました (ID: 1234567890)", snackbarMessage)
        assertEquals(1, refreshCount)
    }

    @Test
    fun catalogExecutionSupport_createThreadSubmitShowsBoardMissingMessage() = runBlocking {
        var showDialog = true
        var snackbarMessage: String? = null
        var refreshCount = 0
        val bindings = buildCatalogCreateThreadBindings(
            coroutineScope = this,
            activeRepository = FakeBoardRepository(),
            currentBoard = { null },
            currentDraft = {
                CreateThreadDraft(
                    name = "name",
                    title = "title",
                    comment = "comment",
                    password = "password"
                )
            },
            currentImage = { null },
            setCreateThreadDraft = {},
            setCreateThreadImage = {},
            setShowCreateThreadDialog = { showDialog = it },
            updateLastUsedDeleteKey = { error("should not save delete key") },
            showSnackbar = { snackbarMessage = it },
            performRefresh = { refreshCount += 1 }
        )

        bindings.submitCreateThread()
        yield()

        assertFalse(showDialog)
        assertEquals("板が選択されていません", snackbarMessage)
        assertEquals(0, refreshCount)
    }

    @Test
    fun catalogBindingsSupport_navigationCallbacks_openDialogAndRouteActions() {
        var password = ""
        var showCreateThreadDialog = false
        var scrollToTopCount = 0
        var refreshCount = 0
        var showPastThreadSearchDialog = false
        var showModeDialog = false
        var showSettingsMenu = false
        val callbacks = buildCatalogNavigationCallbacks(
            lastUsedDeleteKey = "saved-key",
            currentCreateThreadPassword = { password },
            setCreateThreadPassword = { password = it },
            setShowCreateThreadDialog = { showCreateThreadDialog = it },
            scrollCatalogToTop = { scrollToTopCount += 1 },
            performRefresh = { refreshCount += 1 },
            setShowPastThreadSearchDialog = { showPastThreadSearchDialog = it },
            setShowModeDialog = { showModeDialog = it },
            setShowSettingsMenu = { showSettingsMenu = it }
        )

        callbacks.onNavigate(CatalogNavEntryId.CreateThread)
        callbacks.onNavigate(CatalogNavEntryId.ScrollToTop)
        callbacks.onNavigate(CatalogNavEntryId.RefreshCatalog)
        callbacks.onNavigate(CatalogNavEntryId.PastThreadSearch)
        callbacks.onNavigate(CatalogNavEntryId.Mode)
        callbacks.onNavigate(CatalogNavEntryId.Settings)

        assertEquals("saved-key", password)
        assertTrue(showCreateThreadDialog)
        assertEquals(1, scrollToTopCount)
        assertEquals(1, refreshCount)
        assertTrue(showPastThreadSearchDialog)
        assertTrue(showModeDialog)
        assertTrue(showSettingsMenu)
    }

    @Test
    fun catalogBindingsSupport_pastThreadSearchResultCallbacks_dismissRetryAndSelect() {
        var generation = 4L
        var currentJobCancelled = false
        val currentJob = kotlinx.coroutines.SupervisorJob().also { job ->
            job.invokeOnCompletion { currentJobCancelled = true }
        }
        var storedJob: Job? = currentJob
        var sheetVisible = true
        var retriedQuery: String? = null
        var retriedScope: ArchiveSearchScope? = null
        var selectedThreadId: String? = null
        val item = ArchiveSearchItem(
            threadId = "123",
            server = "may",
            board = "b",
            title = "Title",
            htmlUrl = "https://may.2chan.net/b/res/123.htm"
        )
        val callbacks = buildCatalogPastThreadSearchResultCallbacks(
            currentPastSearchGeneration = { generation },
            currentPastSearchJob = { storedJob },
            setPastSearchGeneration = { generation = it },
            setPastSearchJob = { storedJob = it },
            setIsPastSearchSheetVisible = { sheetVisible = it },
            runPastThreadSearch = { query, scope ->
                retriedQuery = query
                retriedScope = scope
                true
            },
            currentArchiveSearchQuery = { "query" },
            currentLastArchiveSearchScope = { ArchiveSearchScope("may", "b") },
            onThreadSelected = { selectedThreadId = it.id }
        )

        callbacks.onRetry()
        assertEquals("query", retriedQuery)
        assertEquals(ArchiveSearchScope("may", "b"), retriedScope)

        callbacks.onDismiss()
        assertTrue(currentJobCancelled)
        assertEquals(5L, generation)
        assertEquals(null, storedJob)
        assertFalse(sheetVisible)

        currentJobCancelled = false
        storedJob = kotlinx.coroutines.SupervisorJob().also { job ->
            job.invokeOnCompletion { currentJobCancelled = true }
        }
        sheetVisible = true
        callbacks.onItemSelected(item)

        assertTrue(currentJobCancelled)
        assertEquals(6L, generation)
        assertEquals(null, storedJob)
        assertFalse(sheetVisible)
        assertEquals("123", selectedThreadId)
    }

    @Test
    fun catalogEffectSupport_resolvesModeSyncPastSearchResetAndDebounceQuery() {
        assertEquals(
            CatalogMode.New,
            resolveCatalogModeSyncValue(
                boardId = "b",
                persistedCatalogModes = mapOf("b" to CatalogMode.New)
            )
        )
        assertEquals(
            null,
            resolveCatalogModeSyncValue(
                boardId = "   ",
                persistedCatalogModes = mapOf("b" to CatalogMode.New)
            )
        )

        val overlayState = CatalogOverlayState(
            showPastThreadSearchDialog = true,
            isPastSearchSheetVisible = true
        )
        val resetState = resolveCatalogPastSearchResetState(
            scope = ArchiveSearchScope("may", "b"),
            overlayState = overlayState
        )
        assertEquals(ArchiveSearchScope("may", "b"), resetState.runtimeState.lastArchiveSearchScope)
        assertFalse(resetState.overlayState.showPastThreadSearchDialog)
        assertFalse(resetState.overlayState.isPastSearchSheetVisible)

        assertEquals("query", resolveCatalogDebouncedSearchQuery("  query  "))
        assertEquals("", resolveCatalogDebouncedSearchQuery("   "))
    }

    @Test
    fun catalogDerivedRuntimeSupport_buildsVisibleItemsRequest() {
        val items = emptyList<CatalogItem>()
        val request = buildCatalogVisibleItemsRequest(
            items = items,
            mode = CatalogMode.New,
            watchWords = listOf("watch"),
            catalogNgWords = listOf("ng"),
            catalogNgFilteringEnabled = true,
            query = "query"
        )

        assertEquals(items, request.items)
        assertEquals(CatalogMode.New, request.mode)
        assertEquals(listOf("watch"), request.watchWords)
        assertEquals(listOf("ng"), request.catalogNgWords)
        assertTrue(request.catalogNgFilteringEnabled)
        assertEquals("query", request.query)
    }

    @Test
    fun catalogDialogBindingsSupport_modeAndDisplayCallbacks_updateVisibilityAndState() {
        var selectedMode: CatalogMode? = null
        var showModeDialog = true
        val modeCallbacks = buildCatalogModeDialogCallbacks(
            persistCatalogMode = { selectedMode = it },
            setShowModeDialog = { showModeDialog = it }
        )

        modeCallbacks.onModeSelected(CatalogMode.New)
        assertEquals(CatalogMode.New, selectedMode)
        assertFalse(showModeDialog)

        var selectedStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle? = null
        var selectedColumns: Int? = null
        var showDisplayStyleDialog = true
        val displayCallbacks = buildCatalogDisplayStyleDialogCallbacks(
            updateCatalogDisplayStyle = { selectedStyle = it },
            updateCatalogGridColumns = { selectedColumns = it },
            setShowDisplayStyleDialog = { showDisplayStyleDialog = it }
        )

        displayCallbacks.onStyleSelected(com.valoser.futacha.shared.model.CatalogDisplayStyle.List)
        displayCallbacks.onGridColumnsSelected(6)
        displayCallbacks.onDismiss()
        assertEquals(com.valoser.futacha.shared.model.CatalogDisplayStyle.List, selectedStyle)
        assertEquals(6, selectedColumns)
        assertFalse(showDisplayStyleDialog)
    }

    @Test
    fun catalogDialogBindingsSupport_pastSearchDialogCallbacks_updateSearchState() {
        var lastScope: ArchiveSearchScope? = null
        var archiveQuery = ""
        var showDialog = true
        var showSheet = false
        var retriedQuery: String? = null
        var retriedScope: ArchiveSearchScope? = null
        val callbacks = buildCatalogPastThreadSearchDialogCallbacks(
            currentArchiveSearchScope = { ArchiveSearchScope("may", "b") },
            setLastArchiveSearchScope = { lastScope = it },
            setArchiveSearchQuery = { archiveQuery = it },
            setShowPastThreadSearchDialog = { showDialog = it },
            setIsPastSearchSheetVisible = { showSheet = it },
            runPastThreadSearch = { query, scope ->
                retriedQuery = query
                retriedScope = scope
                true
            }
        )

        callbacks.onSearch("  futaba ")
        assertEquals(ArchiveSearchScope("may", "b"), lastScope)
        assertEquals("futaba", archiveQuery)
        assertFalse(showDialog)
        assertTrue(showSheet)
        assertEquals("futaba", retriedQuery)
        assertEquals(ArchiveSearchScope("may", "b"), retriedScope)
    }

    @Test
    fun catalogDialogBindingsSupport_globalNgAndWatchCallbacks_forwardActions() {
        var dismissedGlobalSettings = false
        var openedCookieManagement = false
        val globalCallbacks = buildCatalogGlobalSettingsCallbacks(
            cookieRepository = CookieRepository(PersistentCookieStorage(NoOpFileSystem())),
            setIsGlobalSettingsVisible = { dismissedGlobalSettings = !it },
            setIsCookieManagementVisible = { openedCookieManagement = it }
        )

        globalCallbacks.onBack()
        assertTrue(dismissedGlobalSettings)
        globalCallbacks.onOpenCookieManager?.invoke()
        assertTrue(openedCookieManagement)

        var ngDismissed = false
        val ngEvents = mutableListOf<String>()
        val ngCallbacks = buildCatalogNgManagementCallbacks(
            setIsNgManagementVisible = { ngDismissed = !it },
            onAddWord = { ngEvents += "add:$it" },
            onRemoveWord = { ngEvents += "remove:$it" },
            onToggleFiltering = { ngEvents += "toggle" }
        )
        ngCallbacks.onAddWord("spam")
        ngCallbacks.onRemoveWord("spam")
        ngCallbacks.onToggleFiltering()
        ngCallbacks.onDismiss()
        assertEquals(listOf("add:spam", "remove:spam", "toggle"), ngEvents)
        assertTrue(ngDismissed)

        var watchDismissed = false
        val watchEvents = mutableListOf<String>()
        val watchCallbacks = buildCatalogWatchWordsCallbacks(
            onAddWord = { watchEvents += "add:$it" },
            onRemoveWord = { watchEvents += "remove:$it" },
            setIsWatchWordsVisible = { watchDismissed = !it }
        )
        watchCallbacks.onAddWord("watch")
        watchCallbacks.onRemoveWord("watch")
        watchCallbacks.onDismiss()
        assertEquals(listOf("add:watch", "remove:watch"), watchEvents)
        assertTrue(watchDismissed)
    }

    @Test
    fun catalogOverlayBindingsBundle_forwardsRepresentativeCallbacks() {
        var selectedMode: CatalogMode? = null
        var showModeDialog = true
        var globalSettingsVisible = true
        var cookieManagementVisible = true
        val ngEvents = mutableListOf<String>()
        val watchEvents = mutableListOf<String>()
        val bundle = buildCatalogScreenOverlayBindingsBundle(
            persistCatalogMode = { selectedMode = it },
            updateCatalogDisplayStyle = {},
            updateCatalogGridColumns = {},
            currentArchiveSearchScope = { null },
            setLastArchiveSearchScope = {},
            setArchiveSearchQuery = {},
            setShowPastThreadSearchDialog = {},
            setIsPastSearchSheetVisible = {},
            runPastThreadSearch = { _, _ -> true },
            currentPastSearchGeneration = { 0L },
            currentPastSearchJob = { null },
            setPastSearchGeneration = {},
            setPastSearchJob = {},
            currentArchiveSearchQuery = { "" },
            currentLastArchiveSearchScope = { null },
            onThreadSelected = {},
            board = { null },
            catalogMode = { CatalogMode.default },
            urlLauncher = {},
            stateStore = null,
            isPrivacyFilterEnabled = { false },
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            scrollCatalogToTop = {},
            setShowModeDialog = { showModeDialog = it },
            setShowDisplayStyleDialog = {},
            setIsNgManagementVisible = {},
            setIsWatchWordsVisible = {},
            setShowSettingsMenu = {},
            cookieRepository = CookieRepository(PersistentCookieStorage(NoOpFileSystem())),
            setIsGlobalSettingsVisible = { globalSettingsVisible = it },
            setIsCookieManagementVisible = { cookieManagementVisible = it },
            onAddNgWord = { ngEvents += "add:$it" },
            onRemoveNgWord = { ngEvents += "remove:$it" },
            onToggleNgFiltering = { ngEvents += "toggle" },
            onAddWatchWord = { watchEvents += "add:$it" },
            onRemoveWatchWord = { watchEvents += "remove:$it" }
        )

        bundle.modeDialogCallbacks.onModeSelected(CatalogMode.New)
        bundle.globalSettingsCallbacks.onBack()
        bundle.globalSettingsCallbacks.onOpenCookieManager?.invoke()
        bundle.ngManagementCallbacks.onAddWord("spam")
        bundle.ngManagementCallbacks.onToggleFiltering()
        bundle.watchWordsCallbacks.onRemoveWord("watch")
        bundle.onCookieManagementBack()

        assertEquals(CatalogMode.New, selectedMode)
        assertFalse(showModeDialog)
        assertFalse(globalSettingsVisible)
        assertFalse(cookieManagementVisible)
        assertEquals(listOf("add:spam", "toggle"), ngEvents)
        assertEquals(listOf("remove:watch"), watchEvents)
    }

    @Test
    fun catalogOverlaySupport_updatesDialogAndSheetVisibility() {
        val initial = CatalogOverlayState()

        assertEquals(
            CatalogOverlayState(showModeDialog = true),
            setCatalogModeDialogVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(showDisplayStyleDialog = true),
            setCatalogDisplayStyleDialogVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(showCreateThreadDialog = true),
            setCatalogCreateThreadDialogVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(showSettingsMenu = true),
            setCatalogSettingsMenuVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(showPastThreadSearchDialog = true),
            setCatalogPastThreadSearchDialogVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(isGlobalSettingsVisible = true),
            setCatalogGlobalSettingsVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(isCookieManagementVisible = true),
            setCatalogCookieManagementVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(isNgManagementVisible = true),
            setCatalogNgManagementVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(isWatchWordsVisible = true),
            setCatalogWatchWordsVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(isPastSearchSheetVisible = true),
            setCatalogPastSearchSheetVisible(initial, true)
        )
        assertEquals(
            CatalogOverlayState(
                showModeDialog = true,
                showCreateThreadDialog = true,
                showPastThreadSearchDialog = false,
                isPastSearchSheetVisible = false
            ),
            resetCatalogPastSearchOverlayState(
                CatalogOverlayState(
                    showModeDialog = true,
                    showCreateThreadDialog = true,
                    showPastThreadSearchDialog = true,
                    isPastSearchSheetVisible = true
                )
            )
        )
    }

    @Test
    fun resolveInitialCatalogMode_usesPersistedValueOrDefault() {
        assertEquals(
            CatalogMode.New,
            resolveInitialCatalogMode(
                boardId = "b",
                persistedModes = mapOf("b" to CatalogMode.New)
            )
        )
        assertEquals(
            CatalogMode.default,
            resolveInitialCatalogMode(
                boardId = "missing",
                persistedModes = emptyMap()
            )
        )
    }

    @Test
    fun nextCatalogRequestGeneration_incrementsByOne() {
        assertEquals(6L, nextCatalogRequestGeneration(5L))
    }

    @Test
    fun shouldApplyCatalogRequestResult_requiresActiveAndMatchingGeneration() {
        assertTrue(shouldApplyCatalogRequestResult(isActive = true, currentGeneration = 3L, requestGeneration = 3L))
        assertFalse(shouldApplyCatalogRequestResult(isActive = false, currentGeneration = 3L, requestGeneration = 3L))
        assertFalse(shouldApplyCatalogRequestResult(isActive = true, currentGeneration = 4L, requestGeneration = 3L))
    }

    @Test
    fun shouldFinalizeCatalogRefresh_requiresMatchingJobAndGeneration() {
        assertTrue(shouldFinalizeCatalogRefresh(isSameRunningJob = true, currentGeneration = 2L, requestGeneration = 2L))
        assertFalse(shouldFinalizeCatalogRefresh(isSameRunningJob = false, currentGeneration = 2L, requestGeneration = 2L))
        assertFalse(shouldFinalizeCatalogRefresh(isSameRunningJob = true, currentGeneration = 3L, requestGeneration = 2L))
    }

    @Test
    fun buildCatalogRefreshFailureMessage_matchesUiCopy() {
        assertEquals("更新に失敗しました", buildCatalogRefreshFailureMessage())
    }

    @Test
    fun resolveCatalogRefreshAvailability_mapsBusyAndReady() {
        assertEquals(
            CatalogRefreshAvailability.Busy,
            resolveCatalogRefreshAvailability(isRefreshing = true)
        )
        assertEquals(
            CatalogRefreshAvailability.Ready,
            resolveCatalogRefreshAvailability(isRefreshing = false)
        )
    }

    @Test
    fun createThreadHelpers_matchUiRules() {
        assertTrue(canSubmitCreateThread(title = "件名", comment = ""))
        assertTrue(canSubmitCreateThread(title = "", comment = "本文"))
        assertFalse(canSubmitCreateThread(title = "", comment = ""))
        assertEquals(
            CreateThreadDraft(name = "n2"),
            updateCreateThreadDraftName(CreateThreadDraft(), "n2")
        )
        assertEquals(
            CreateThreadDraft(email = "sage"),
            updateCreateThreadDraftEmail(CreateThreadDraft(), "sage")
        )
        assertEquals(
            CreateThreadDraft(title = "subject"),
            updateCreateThreadDraftTitle(CreateThreadDraft(), "subject")
        )
        assertEquals(
            CreateThreadDraft(comment = "body"),
            updateCreateThreadDraftComment(CreateThreadDraft(), "body")
        )
        assertEquals(
            CreateThreadDraft(password = "key"),
            updateCreateThreadDraftPassword(CreateThreadDraft(), "key")
        )
        assertEquals(
            "last-key",
            resolveCreateThreadDialogOpenPassword(
                currentPassword = "",
                lastUsedDeleteKey = "last-key"
            )
        )
        assertEquals(
            "custom",
            resolveCreateThreadDialogOpenPassword(
                currentPassword = "custom",
                lastUsedDeleteKey = "last-key"
            )
        )
        assertEquals(CreateThreadDraft(), emptyCreateThreadDraft())
        assertEquals("abc123", normalizeCreateThreadPasswordForSubmit("  abc123  "))
        assertEquals("板が選択されていません", buildCreateThreadBoardMissingMessage())
        assertEquals(
            "スレッドを作成しました。カタログ更新で確認してください",
            buildCreateThreadSuccessMessage(null)
        )
        assertEquals(
            "スレッドを作成しました (ID: 12345)",
            buildCreateThreadSuccessMessage("12345")
        )
        assertEquals(
            "スレッド作成に失敗しました: boom",
            buildCreateThreadFailureMessage(IllegalStateException("boom"))
        )
        assertEquals(
            "スレッド作成に失敗しました: 不明なエラー",
            buildCreateThreadFailureMessage(IllegalStateException())
        )
        var draft = CreateThreadDraft()
        var selectedImage: ImageData? = null
        var dialogVisible = true
        var submitted = false
        var cleared = false
        val createThreadCallbacks = buildCatalogCreateThreadDialogCallbacks(
            currentDraft = { draft },
            setDraft = { draft = it },
            setImage = { selectedImage = it },
            setShowCreateThreadDialog = { dialogVisible = it },
            onSubmit = { submitted = true },
            onClear = { cleared = true }
        )
        createThreadCallbacks.onNameChange("name")
        createThreadCallbacks.onEmailChange("sage")
        createThreadCallbacks.onTitleChange("title")
        createThreadCallbacks.onCommentChange("comment")
        createThreadCallbacks.onPasswordChange("password")
        createThreadCallbacks.onImageSelected(ImageData(byteArrayOf(1), "a.jpg"))
        createThreadCallbacks.onDismiss()
        createThreadCallbacks.onSubmit()
        createThreadCallbacks.onClear()
        assertEquals("name", draft.name)
        assertEquals("sage", draft.email)
        assertEquals("title", draft.title)
        assertEquals("comment", draft.comment)
        assertEquals("password", draft.password)
        assertEquals("a.jpg", selectedImage?.fileName)
        assertFalse(dialogVisible)
        assertTrue(submitted)
        assertTrue(cleared)
        assertEquals(
            CatalogPastSearchRuntimeState(lastArchiveSearchScope = ArchiveSearchScope("may", "b")),
            resetCatalogPastSearchRuntimeState(ArchiveSearchScope("may", "b"))
        )
    }

    @Test
    fun watchWordHelpers_validateDeduplicateAndRemove() {
        assertEquals(
            WatchWordMutationState(
                updatedWords = listOf("夏休み"),
                message = "監視ワードを入力してください",
                shouldPersist = false
            ),
            addWatchWord(existingWords = listOf("夏休み"), input = "   ")
        )
        assertEquals(
            WatchWordMutationState(
                updatedWords = listOf("夏休み"),
                message = "そのワードはすでに登録されています",
                shouldPersist = false
            ),
            addWatchWord(existingWords = listOf("夏休み"), input = " 夏休み ")
        )
        assertEquals(
            WatchWordMutationState(
                updatedWords = listOf("夏休み", "宿題"),
                message = "監視ワードを追加しました",
                shouldPersist = true
            ),
            addWatchWord(existingWords = listOf("夏休み"), input = " 宿題 ")
        )
        assertEquals(
            WatchWordMutationState(
                updatedWords = listOf("宿題"),
                message = "監視ワードを削除しました",
                shouldPersist = true
            ),
            removeWatchWord(existingWords = listOf("夏休み", "宿題"), entry = "夏休み")
        )
    }

    @Test
    fun catalogNgHelpers_validateDeduplicateToggleAndSheetState() {
        assertEquals(
            CatalogNgMutationState(
                updatedWords = listOf("spam"),
                message = "NGワードに含める文字を入力してください",
                shouldPersist = false
            ),
            addCatalogNgWord(existingWords = listOf("spam"), input = "   ")
        )
        assertEquals(
            CatalogNgMutationState(
                updatedWords = listOf("spam"),
                message = "そのNGワードはすでに登録されています",
                shouldPersist = false
            ),
            addCatalogNgWord(existingWords = listOf("spam"), input = " Spam ")
        )
        assertEquals(
            CatalogNgMutationState(
                updatedWords = listOf("spam", "ads"),
                message = "NGワードを追加しました",
                shouldPersist = true
            ),
            addCatalogNgWord(existingWords = listOf("spam"), input = " ads ")
        )
        assertEquals(
            CatalogNgMutationState(
                updatedWords = listOf("ads"),
                message = "NGワードを削除しました",
                shouldPersist = true
            ),
            removeCatalogNgWord(existingWords = listOf("spam", "ads"), entry = "spam")
        )
        assertEquals(
            CatalogNgFilterToggleState(
                isEnabled = false,
                message = "NG表示を無効にしました"
            ),
            toggleCatalogNgFiltering(currentEnabled = true)
        )
        assertEquals(
            CatalogNgFilterToggleState(
                isEnabled = true,
                message = "NG表示を有効にしました"
            ),
            toggleCatalogNgFiltering(currentEnabled = false)
        )
        assertEquals(
            CatalogNgManagementSheetState(
                ngHeaders = emptyList(),
                ngWords = listOf("spam"),
                ngFilteringEnabled = true,
                includeHeaderSection = false
            ),
            resolveCatalogNgManagementSheetState(
                ngWords = listOf("spam"),
                ngFilteringEnabled = true
            )
        )
    }

    @Test
    fun buildCatalogExternalAppUrl_usesCatalogUrlRules() {
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat",
            buildCatalogExternalAppUrl("https://may.2chan.net/b/futaba.php", com.valoser.futacha.shared.model.CatalogMode.Catalog)
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat&sort=1",
            buildCatalogExternalAppUrl("https://may.2chan.net/b/futaba.php", com.valoser.futacha.shared.model.CatalogMode.New)
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat",
            buildCatalogExternalAppUrl("https://may.2chan.net/b/futaba.php", com.valoser.futacha.shared.model.CatalogMode.WatchWords)
        )
    }

    @Test
    fun buildCatalogLoadErrorMessage_mapsKnownCases() {
        assertEquals(
            "タイムアウト: サーバーが応答しません",
            buildCatalogLoadErrorMessage(IllegalStateException("request timeout"))
        )
        assertEquals(
            "板が見つかりません (404)",
            buildCatalogLoadErrorMessage(IllegalStateException("HTTP 404"))
        )
        assertEquals(
            "サーバーエラー (500)",
            buildCatalogLoadErrorMessage(IllegalStateException("HTTP 500"))
        )
        assertEquals(
            "ネットワークエラー: HTTP error: refused",
            buildCatalogLoadErrorMessage(IllegalStateException("HTTP error: refused"))
        )
        assertEquals(
            "データサイズが大きすぎます",
            buildCatalogLoadErrorMessage(IllegalStateException("response exceeds maximum size"))
        )
        assertEquals(
            "カタログを読み込めませんでした: boom",
            buildCatalogLoadErrorMessage(IllegalStateException("boom"))
        )
        assertEquals(
            "カタログを読み込めませんでした: 不明なエラー",
            buildCatalogLoadErrorMessage(IllegalStateException())
        )
    }

    @Test
    fun resolveCatalogBackAction_prioritizesDrawerThenSearchThenBack() {
        assertEquals(
            CatalogBackAction.CloseDrawer,
            resolveCatalogBackAction(isDrawerOpen = true, isSearchActive = true)
        )
        assertEquals(
            CatalogBackAction.ExitSearch,
            resolveCatalogBackAction(isDrawerOpen = false, isSearchActive = true)
        )
        assertEquals(
            CatalogBackAction.NavigateBack,
            resolveCatalogBackAction(isDrawerOpen = false, isSearchActive = false)
        )
    }

    @Test
    fun catalogLifecycleBindings_handleBackAndDispose() {
        val scope = CoroutineScope(SupervisorJob())
        var searchActive = true
        var searchQuery = "query"
        var navigatedBack = false
        var initialLoadCount = 0
        val loadJob = Job()
        val pastSearchJob = Job()
        var catalogLoadJob: Job? = loadJob
        var pastSearchRuntimeState = CatalogPastSearchRuntimeState(job = pastSearchJob)
        val bindings = buildCatalogScreenLifecycleBindings(
            coroutineScope = scope,
            drawerState = androidx.compose.material3.DrawerState(
                initialValue = androidx.compose.material3.DrawerValue.Open,
                confirmStateChange = { true }
            ),
            isDrawerOpen = false,
            isSearchActive = true,
            setSearchActive = { searchActive = it },
            setSearchQuery = { searchQuery = it },
            onBack = { navigatedBack = true },
            onInitialLoad = { initialLoadCount += 1 },
            currentCatalogLoadJob = { catalogLoadJob },
            setCatalogLoadJob = { catalogLoadJob = it },
            currentPastSearchRuntimeState = { pastSearchRuntimeState },
            setPastSearchRuntimeState = { pastSearchRuntimeState = it }
        )

        assertEquals(CatalogBackAction.ExitSearch, bindings.backAction)
        bindings.onExitSearchBack()
        bindings.onNavigateBack()
        bindings.onInitialLoad()
        bindings.onDispose()

        assertFalse(searchActive)
        assertEquals("", searchQuery)
        assertTrue(navigatedBack)
        assertEquals(1, initialLoadCount)
        assertTrue(loadJob.isCancelled)
        assertTrue(pastSearchJob.isCancelled)
        assertEquals(null, catalogLoadJob)
        assertEquals(null, pastSearchRuntimeState.job)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun resolveCatalogSettingsActionState_mapsEachMenuItem() {
        assertEquals(
            CatalogSettingsActionState(scrollToTop = true),
            resolveCatalogSettingsActionState(CatalogSettingsMenuItem.ScrollToTop)
        )
        assertEquals(
            CatalogSettingsActionState(showDisplayStyleDialog = true),
            resolveCatalogSettingsActionState(CatalogSettingsMenuItem.DisplayStyle)
        )
        assertEquals(
            CatalogSettingsActionState(showNgManagement = true),
            resolveCatalogSettingsActionState(CatalogSettingsMenuItem.NgManagement)
        )
        assertEquals(
            CatalogSettingsActionState(showWatchWords = true),
            resolveCatalogSettingsActionState(CatalogSettingsMenuItem.WatchWords)
        )
        assertEquals(
            CatalogSettingsActionState(openExternalApp = true),
            resolveCatalogSettingsActionState(CatalogSettingsMenuItem.ExternalApp)
        )
        assertEquals(
            CatalogSettingsActionState(togglePrivacy = true),
            resolveCatalogSettingsActionState(CatalogSettingsMenuItem.Privacy)
        )
    }

    @Test
    fun catalogNavigationHelpers_resolveBarEntriesAndLabels() {
        val entries = listOf(
            CatalogNavEntryConfig(
                id = CatalogNavEntryId.Settings,
                order = 9,
                placement = CatalogNavEntryPlacement.BAR
            ),
            CatalogNavEntryConfig(
                id = CatalogNavEntryId.RefreshCatalog,
                order = 1,
                placement = CatalogNavEntryPlacement.BAR
            ),
            CatalogNavEntryConfig(
                id = CatalogNavEntryId.Mode,
                order = 2,
                placement = CatalogNavEntryPlacement.HIDDEN
            )
        )

        val resolved = resolveCatalogNavBarEntries(entries)

        assertEquals(
            listOf(
                CatalogNavEntryId.CreateThread,
                CatalogNavEntryId.ScrollToTop,
                CatalogNavEntryId.RefreshCatalog,
                CatalogNavEntryId.Settings
            ),
            resolved.map { it.id }
        )
        assertEquals("カタログ更新", CatalogNavEntryId.RefreshCatalog.toMeta().label)
        assertEquals("設定", CatalogMenuAction.Settings.label)
        assertEquals("NG管理", CatalogSettingsMenuItem.NgManagement.label)
    }

    @Test
    fun overscrollHelpers_consumeDirectionalEdgeDrag_andTriggerByThreshold() {
        assertEquals(
            CatalogOverscrollDragState(
                totalDrag = 30f,
                overscrollTarget = 12f,
                shouldConsume = true
            ),
            updateCatalogOverscrollDragState(
                totalDrag = 0f,
                dragAmount = 30f,
                isRefreshing = false,
                isAtTop = true,
                isAtBottom = false,
                maxOverscrollPx = 64f
            )
        )
        assertEquals(
            CatalogOverscrollDragState(
                totalDrag = -20f,
                overscrollTarget = -8f,
                shouldConsume = true
            ),
            updateCatalogOverscrollDragState(
                totalDrag = 0f,
                dragAmount = -20f,
                isRefreshing = false,
                isAtTop = false,
                isAtBottom = true,
                maxOverscrollPx = 64f
            )
        )
        assertEquals(
            CatalogOverscrollDragState(
                totalDrag = 10f,
                overscrollTarget = 4f,
                shouldConsume = false
            ),
            updateCatalogOverscrollDragState(
                totalDrag = 10f,
                dragAmount = -3f,
                isRefreshing = false,
                isAtTop = false,
                isAtBottom = false,
                maxOverscrollPx = 64f
            )
        )
        assertTrue(shouldTriggerCatalogOverscrollRefresh(totalDrag = 57f, refreshTriggerPx = 56f))
        assertFalse(shouldTriggerCatalogOverscrollRefresh(totalDrag = 56f, refreshTriggerPx = 56f))
    }
}

private class NoOpFileSystem : FileSystem {
    override suspend fun createDirectory(path: String): Result<Unit> = Result.success(Unit)

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = Result.success(Unit)

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = Result.success(Unit)

    override suspend fun writeString(path: String, content: String): Result<Unit> = Result.success(Unit)

    override suspend fun readBytes(path: String): Result<ByteArray> = Result.failure(IllegalStateException("unused"))

    override suspend fun readString(path: String): Result<String> = Result.failure(IllegalStateException("unused"))

    override suspend fun delete(path: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteRecursively(path: String): Result<Unit> = Result.success(Unit)

    override suspend fun exists(path: String): Boolean = false

    override suspend fun getFileSize(path: String): Long = 0L

    override suspend fun listFiles(directory: String): List<String> = emptyList()

    override fun getAppDataDirectory(): String = "/tmp"

    override fun resolveAbsolutePath(relativePath: String): String = "/tmp/$relativePath"

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> = Result.success(Unit)

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> =
        Result.success(Unit)

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> =
        Result.success(Unit)

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> =
        Result.failure(IllegalStateException("unused"))

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = false

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> = Result.success(Unit)
}
