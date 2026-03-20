package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.BoardManagementScreenDependencies
import com.valoser.futacha.shared.ui.board.CatalogScreenDependencies
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.ui.board.autoSavedThreadRepository
import com.valoser.futacha.shared.ui.board.fileSystem
import com.valoser.futacha.shared.ui.board.stateStore
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FutachaAppTest {
    @Test
    fun futachaMutationCallbackBuilders_launchExpectedActions() = runBlocking {
        var backgroundRefreshEnabled: Boolean? = null
        var lightweightEnabled: Boolean? = null
        var manualSaveDirectory: String? = null
        var attachmentPickerPreference: AttachmentPickerPreference? = null
        var saveDirectorySelection: SaveDirectorySelection? = null
        var manualSaveLocation: com.valoser.futacha.shared.model.SaveLocation? = null
        var preferredFileManager: Pair<String?, String?>? = null
        var threadMenuEntries: List<ThreadMenuEntryConfig>? = null
        var catalogNavEntries: List<CatalogNavEntryConfig>? = null
        var dismissedEntry: ThreadHistoryEntry? = null
        var updatedEntry: ThreadHistoryEntry? = null
        var historyCleared = false
        val entry = historyEntry()
        val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
            coroutineScope = this,
            inputs = FutachaPreferenceMutationInputs(
                setBackgroundRefreshEnabled = { backgroundRefreshEnabled = it },
                setLightweightModeEnabled = { lightweightEnabled = it },
                setManualSaveDirectory = { manualSaveDirectory = it },
                setAttachmentPickerPreference = { attachmentPickerPreference = it },
                setSaveDirectorySelection = { saveDirectorySelection = it },
                setManualSaveLocation = { manualSaveLocation = it },
                setPreferredFileManager = { packageName, label -> preferredFileManager = packageName to label },
                setThreadMenuEntries = { threadMenuEntries = it },
                setCatalogNavEntries = { catalogNavEntries = it }
            )
        )
        val historyMutations = buildFutachaHistoryMutationCallbacks(
            coroutineScope = this,
            dismissHistoryEntry = { dismissedEntry = it },
            updateHistoryEntry = { updatedEntry = it },
            clearHistory = { historyCleared = true }
        )
        var pickerOpened = false
        val screenPreferencesCallbacks = buildFutachaScreenPreferencesCallbacks(
            FutachaScreenPreferencesCallbackInputs(
                preferenceMutations = preferenceMutations,
                onOpenSaveDirectoryPicker = { pickerOpened = true }
            )
        )

        preferenceMutations.onBackgroundRefreshChanged(true)
        preferenceMutations.onLightweightModeChanged(false)
        preferenceMutations.onManualSaveDirectoryChanged("/tmp/save")
        preferenceMutations.onAttachmentPickerPreferenceChanged(AttachmentPickerPreference.DOCUMENT)
        preferenceMutations.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        preferenceMutations.onManualSaveLocationChanged(com.valoser.futacha.shared.model.SaveLocation.Path("/tmp"))
        preferenceMutations.onFileManagerSelected("pkg", "label")
        preferenceMutations.onClearPreferredFileManager()
        preferenceMutations.onThreadMenuEntriesChanged(emptyList())
        preferenceMutations.onCatalogNavEntriesChanged(emptyList())
        historyMutations.onDismissHistoryEntry(entry)
        historyMutations.onUpdateHistoryEntry(entry)
        historyMutations.onClearHistory()
        screenPreferencesCallbacks.onOpenSaveDirectoryPicker?.invoke()
        yield()
        delay(1)

        assertEquals(true, backgroundRefreshEnabled)
        assertEquals(false, lightweightEnabled)
        assertEquals("/tmp/save", manualSaveDirectory)
        assertEquals(AttachmentPickerPreference.DOCUMENT, attachmentPickerPreference)
        assertEquals(SaveDirectorySelection.PICKER, saveDirectorySelection)
        assertEquals(com.valoser.futacha.shared.model.SaveLocation.Path("/tmp"), manualSaveLocation)
        assertEquals(null to null, preferredFileManager)
        assertEquals(emptyList(), threadMenuEntries)
        assertEquals(emptyList(), catalogNavEntries)
        assertEquals(entry, dismissedEntry)
        assertEquals(entry, updatedEntry)
        assertTrue(historyCleared)
        assertTrue(pickerOpened)
        assertEquals(preferenceMutations.onBackgroundRefreshChanged, screenPreferencesCallbacks.onBackgroundRefreshChanged)
    }

    @Test
    fun futachaHistoryCallbacks_builder_mapsNavigationHistoryAndRefreshActions() = runBlocking {
        var selectedEntry: ThreadHistoryEntry? = null
        var dismissedEntry: ThreadHistoryEntry? = null
        var updatedEntry: ThreadHistoryEntry? = null
        var refreshCount = 0
        var clearCount = 0
        val entry = historyEntry()
        val navigationCallbacks = FutachaNavigationCallbacks(
            onHistoryEntrySelected = { selectedEntry = it },
            onSavedThreadSelected = {},
            onCatalogThreadSelected = { _, _, _, _, _ -> },
            onSavedThreadsDismissed = {},
            onBoardSelectionCleared = {},
            onThreadDismissed = {},
            onRegisteredThreadUrlClick = { false }
        )
        val historyMutations = FutachaHistoryMutationCallbacks(
            onDismissHistoryEntry = { dismissedEntry = it },
            onUpdateHistoryEntry = { updatedEntry = it },
            onClearHistory = { clearCount += 1 }
        )
        val historyCallbacks = buildFutachaScreenHistoryCallbacks(
            FutachaScreenHistoryCallbackInputs(
                navigationCallbacks = navigationCallbacks,
                historyMutations = historyMutations,
                onHistoryRefresh = { refreshCount += 1 }
            )
        )

        historyCallbacks.onHistoryEntrySelected(entry)
        historyCallbacks.onHistoryEntryDismissed(entry)
        historyCallbacks.onHistoryEntryUpdated(entry)
        historyCallbacks.onHistoryRefresh()
        historyCallbacks.onHistoryCleared()

        assertEquals(entry, selectedEntry)
        assertEquals(entry, dismissedEntry)
        assertEquals(entry, updatedEntry)
        assertEquals(1, refreshCount)
        assertEquals(1, clearCount)
        assertEquals(ScreenHistoryCallbacks()::class, historyCallbacks::class)
    }

    @Test
    fun futachaScreenBindingsBundle_buildsSharedContractsFromSingleInputBundle() = runBlocking {
        var backgroundRefreshEnabled: Boolean? = null
        var selectedBoardId: String? = null
        var pickerOpened = false
        val boards = listOf(board(id = "img"))
        val entry = historyEntry(boardId = "img")
        val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
            coroutineScope = this,
            inputs = FutachaPreferenceMutationInputs(
                setBackgroundRefreshEnabled = { backgroundRefreshEnabled = it },
                setLightweightModeEnabled = {},
                setManualSaveDirectory = {},
                setAttachmentPickerPreference = {},
                setSaveDirectorySelection = {},
                setManualSaveLocation = {},
                setPreferredFileManager = { _, _ -> },
                setThreadMenuEntries = {},
                setCatalogNavEntries = {}
            )
        )
        val historyMutations = FutachaHistoryMutationCallbacks(
            onDismissHistoryEntry = {},
            onUpdateHistoryEntry = {},
            onClearHistory = {}
        )
        val bundle = buildFutachaScreenBindingsBundle(
            coroutineScope = this,
            inputs = FutachaScreenBindingsInputs(
                history = listOf(entry),
                currentBoards = { boards },
                currentNavigationState = { FutachaNavigationState() },
                setNavigationState = { selectedBoardId = it.selectedBoardId },
                updateBoards = { it(boards) },
                preferenceMutations = preferenceMutations,
                historyMutations = historyMutations,
                preferencesStateInputs = FutachaScreenPreferencesStateInputs(
                    appVersion = "1.2.3",
                    isBackgroundRefreshEnabled = false,
                    isLightweightModeEnabled = true,
                    manualSaveDirectory = "manual",
                    manualSaveLocation = SaveLocation.Path("manual"),
                    resolvedManualSaveDirectory = "/virtual/manual",
                    attachmentPickerPreference = AttachmentPickerPreference.DOCUMENT,
                    saveDirectorySelection = SaveDirectorySelection.PICKER,
                    preferredFileManagerPackage = "pkg",
                    preferredFileManagerLabel = "Files",
                    threadMenuEntries = emptyList(),
                    catalogNavEntries = emptyList()
                ),
                onOpenSaveDirectoryPicker = { pickerOpened = true },
                onHistoryRefresh = {}
            )
        )

        bundle.screenPreferencesCallbacks.onBackgroundRefreshChanged(true)
        bundle.screenPreferencesCallbacks.onOpenSaveDirectoryPicker?.invoke()
        bundle.screenHistoryCallbacks.onHistoryEntrySelected(entry)
        yield()
        delay(1)

        assertEquals(true, backgroundRefreshEnabled)
        assertTrue(pickerOpened)
        assertEquals("img", selectedBoardId)
        assertEquals(listOf(entry), bundle.screenContract.history)
        assertSame(bundle.screenPreferencesCallbacks, bundle.screenContract.preferencesCallbacks)
        assertSame(
            bundle.screenHistoryCallbacks.onHistoryEntrySelected,
            bundle.screenContract.historyCallbacks.onHistoryEntrySelected
        )
        assertSame(
            bundle.screenHistoryCallbacks.onHistoryEntryDismissed,
            bundle.screenContract.historyCallbacks.onHistoryEntryDismissed
        )
        assertSame(
            bundle.screenHistoryCallbacks.onHistoryEntryUpdated,
            bundle.screenContract.historyCallbacks.onHistoryEntryUpdated
        )
        assertSame(
            bundle.screenHistoryCallbacks.onHistoryRefresh,
            bundle.screenContract.historyCallbacks.onHistoryRefresh
        )
        assertSame(
            bundle.screenHistoryCallbacks.onHistoryCleared,
            bundle.screenContract.historyCallbacks.onHistoryCleared
        )
        assertEquals("1.2.3", bundle.screenPreferencesState.appVersion)
        assertSame(bundle.screenPreferencesState, bundle.screenContract.preferencesState)
    }

    @Test
    fun futachaRuntimeHelpers_buildRepositoryHolderAndCloseOwnedRepository() {
        val sharedRepository = TestBoardRepository()
        val ownedRepository = TestBoardRepository()
        val existingAutoSaveRepository = SavedThreadRepository(InMemoryFileSystem())
        val sharedHolder = buildFutachaRepositoryHolder(
            FutachaRepositoryHolderInputs(
                httpClient = null,
                cookieRepository = null,
                createSharedRepository = { _, _ -> error("should not create shared repository") },
                createOwnedRepository = { ownedRepository }
            )
        )
        val explicitSharedHolder = buildFutachaRepositoryHolder(
            FutachaRepositoryHolderInputs(
                httpClient = io.ktor.client.HttpClient(),
                cookieRepository = null,
                createSharedRepository = { _, _ -> sharedRepository },
                createOwnedRepository = { error("should not create owned repository") }
            )
        )

        assertTrue(sharedHolder.ownsRepository)
        assertSame(ownedRepository, sharedHolder.repository)
        assertFalse(explicitSharedHolder.ownsRepository)
        assertSame(sharedRepository, explicitSharedHolder.repository)

        closeOwnedFutachaRepository(explicitSharedHolder) { error("unexpected: $it") }
        closeOwnedFutachaRepository(sharedHolder) { error("unexpected: $it") }

        assertEquals(0, sharedRepository.closeAsyncCalls)
        assertEquals(1, ownedRepository.closeAsyncCalls)
        assertSame(
            existingAutoSaveRepository,
            buildFutachaAutoSavedThreadRepository(
                FutachaAutoSavedThreadRepositoryInputs(
                    fileSystem = InMemoryFileSystem(),
                    existingRepository = existingAutoSaveRepository
                )
            )
        )
    }

    @Test
    fun futachaRuntimeHelpers_fetchUpdateInfoHandlesSuccessFailureAndNull() = runBlocking {
        val updateInfo = UpdateInfo(
            currentVersion = "1.0.0",
            latestVersion = "1.1.0",
            message = "update"
        )
        var failure: Throwable? = null

        assertNull(fetchFutachaUpdateInfo(null))
        assertEquals(
            updateInfo,
            fetchFutachaUpdateInfo(
                versionChecker = TestVersionChecker(updateInfo = updateInfo)
            )
        )
        assertNull(
            fetchFutachaUpdateInfo(
                versionChecker = TestVersionChecker(failure = IllegalStateException("boom")),
                onFailure = { failure = it }
            )
        )
        assertEquals("boom", failure?.message)
    }

    @Test
    fun futachaUiAssemblyHelpers_buildPreferencesResolveRepositoryAndRecoverMissingBoard() {
        val screenPreferencesState = buildFutachaScreenPreferencesState(
            FutachaScreenPreferencesStateInputs(
                appVersion = "1.2.3",
                isBackgroundRefreshEnabled = true,
                isAdsEnabled = false,
                isLightweightModeEnabled = false,
                manualSaveDirectory = "custom",
                manualSaveLocation = SaveLocation.Path("custom"),
                resolvedManualSaveDirectory = "/virtual/custom",
                attachmentPickerPreference = AttachmentPickerPreference.DOCUMENT,
                saveDirectorySelection = SaveDirectorySelection.PICKER,
                preferredFileManagerPackage = "pkg",
                preferredFileManagerLabel = "Files",
                threadMenuEntries = emptyList(),
                catalogNavEntries = emptyList()
            )
        )
        assertEquals("1.2.3", screenPreferencesState.appVersion)
        assertEquals(false, screenPreferencesState.isAdsEnabled)
        assertEquals("/virtual/custom", screenPreferencesState.resolvedManualSaveDirectory)
        assertEquals(AttachmentPickerPreference.DOCUMENT, screenPreferencesState.attachmentPickerPreference)

        val repository = TestBoardRepository()
        assertSame(
            repository,
            resolveFutachaBoardRepository(
                board = board(id = "img", url = "https://may.2chan.net/img/futaba.php"),
                sharedRepository = repository
            )
        )
        assertNull(
            resolveFutachaBoardRepository(
                board = board(id = "mock", url = "https://example.com/mock/futaba.php"),
                sharedRepository = repository
            )
        )

        val services = buildScreenServiceDependencies(
            stateStore = AppStateStore(FakePlatformStateStorage()),
            autoSavedThreadRepository = SavedThreadRepository(InMemoryFileSystem()),
            fileSystem = InMemoryFileSystem()
        )
        val boardDependencies = BoardManagementScreenDependencies(services = services)
        val catalogDependencies = CatalogScreenDependencies(repository = repository, services = services)
        val threadDependencies = ThreadScreenDependencies(repository = repository, services = services)
        assertSame(services.fileSystem, boardDependencies.fileSystem)
        assertSame(services.stateStore, catalogDependencies.stateStore)
        assertSame(services.autoSavedThreadRepository, threadDependencies.autoSavedThreadRepository)

        val baseState = FutachaNavigationState(
            selectedBoardId = "img",
            selectedThreadId = "123",
            selectedThreadTitle = "title",
            isSavedThreadsVisible = true
        )
        assertEquals(
            FutachaNavigationState(isSavedThreadsVisible = true),
            resolveMissingBoardRecoveryState(
                state = baseState,
                missingBoardId = "img",
                boards = emptyList()
            )
        )
        assertNull(
            resolveMissingBoardRecoveryState(
                state = baseState,
                missingBoardId = "img",
                boards = listOf(board(id = "img"))
            )
        )
    }

    @Test
    fun futachaApp_screenContractContext_keepsCallbacksAndStateAligned() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val entry = historyEntry(boardId = "img", boardName = "img")
        store.setHistory(listOf(entry))
        var navigationState = FutachaNavigationState()
        var pickerOpened = false
        var refreshCount = 0
        var skippedCleared = false
        val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
            coroutineScope = this,
            inputs = FutachaPreferenceMutationInputs(
                setBackgroundRefreshEnabled = store::setBackgroundRefreshEnabled,
                setAdsEnabled = store::setAdsEnabled,
                setLightweightModeEnabled = store::setLightweightModeEnabled,
                setManualSaveDirectory = store::setManualSaveDirectory,
                setAttachmentPickerPreference = store::setAttachmentPickerPreference,
                setSaveDirectorySelection = store::setSaveDirectorySelection,
                setManualSaveLocation = store::setManualSaveLocation,
                setPreferredFileManager = store::setPreferredFileManager,
                setThreadMenuEntries = store::setThreadMenuEntries,
                setCatalogNavEntries = store::setCatalogNavEntries
            )
        )
        val historyMutations = buildFutachaHistoryMutationCallbacks(
            coroutineScope = this,
            dismissHistoryEntry = {},
            updateHistoryEntry = store::upsertHistoryEntry,
            clearHistory = { skippedCleared = true }
        )
        val navigationCallbacks = buildFutachaNavigationCallbacks(
            currentBoards = { listOf(board(id = "img", name = "img")) },
            currentNavigationState = { navigationState },
            setNavigationState = { navigationState = it }
        )
        val boardScreenCallbacks = buildFutachaBoardScreenCallbacks(
            coroutineScope = this,
            inputs = FutachaBoardScreenCallbackInputs(
                currentNavigationState = { navigationState },
                setNavigationState = { navigationState = it },
                updateBoards = store::updateBoards
            )
        )
        val screenPreferencesState = buildFutachaScreenPreferencesState(
            FutachaScreenPreferencesStateInputs(
                appVersion = "1.2.3",
                isBackgroundRefreshEnabled = false,
                isLightweightModeEnabled = true,
                manualSaveDirectory = "custom",
                manualSaveLocation = SaveLocation.Path("custom"),
                resolvedManualSaveDirectory = "/virtual/custom",
                attachmentPickerPreference = AttachmentPickerPreference.MEDIA,
                saveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
                preferredFileManagerPackage = "pkg",
                preferredFileManagerLabel = "Files",
                threadMenuEntries = emptyList(),
                catalogNavEntries = emptyList()
            )
        )
        val screenPreferencesCallbacks = buildFutachaScreenPreferencesCallbacks(
            FutachaScreenPreferencesCallbackInputs(
                preferenceMutations = preferenceMutations,
                onOpenSaveDirectoryPicker = { pickerOpened = true }
            )
        )
        val screenHistoryCallbacks = buildFutachaScreenHistoryCallbacks(
            FutachaScreenHistoryCallbackInputs(
                navigationCallbacks = navigationCallbacks,
                historyMutations = historyMutations,
                onHistoryRefresh = { refreshCount += 1 }
            )
        )
        val screenContract = buildFutachaScreenContractContext(
            history = store.history.first(),
            historyCallbacks = screenHistoryCallbacks,
            preferencesState = screenPreferencesState,
            preferencesCallbacks = screenPreferencesCallbacks
        )

        boardScreenCallbacks.onMenuAction(BoardManagementMenuAction.SAVED_THREADS)
        screenPreferencesCallbacks.onBackgroundRefreshChanged(true)
        screenPreferencesCallbacks.onAdsEnabledChanged(false)
        screenPreferencesCallbacks.onOpenSaveDirectoryPicker?.invoke()
        screenHistoryCallbacks.onHistoryRefresh()
        screenHistoryCallbacks.onHistoryCleared()
        yield()
        delay(1)

        assertTrue(navigationState.isSavedThreadsVisible)
        assertTrue(store.isBackgroundRefreshEnabled.first())
        assertEquals(false, store.isAdsEnabled.first())
        assertTrue(pickerOpened)
        assertEquals(1, refreshCount)
        assertTrue(skippedCleared)
        assertEquals("1.2.3", screenPreferencesState.appVersion)
        assertEquals(listOf(entry), screenContract.history)
        assertSame(screenPreferencesCallbacks, screenContract.preferencesCallbacks)
        assertSame(
            screenHistoryCallbacks.onHistoryEntrySelected,
            screenContract.historyCallbacks.onHistoryEntrySelected
        )
        assertSame(
            screenHistoryCallbacks.onHistoryEntryDismissed,
            screenContract.historyCallbacks.onHistoryEntryDismissed
        )
        assertSame(
            screenHistoryCallbacks.onHistoryEntryUpdated,
            screenContract.historyCallbacks.onHistoryEntryUpdated
        )
        assertSame(
            screenHistoryCallbacks.onHistoryRefresh,
            screenContract.historyCallbacks.onHistoryRefresh
        )
        assertSame(
            screenHistoryCallbacks.onHistoryCleared,
            screenContract.historyCallbacks.onHistoryCleared
        )
    }

    @Test
    fun futachaDestinationProps_directComposition_mapsScreenContractsAndRepositories() {
        val manualRepository = SavedThreadRepository(InMemoryFileSystem())
        val sharedRepository = TestBoardRepository()
        val destinationCoroutineScope = CoroutineScope(Job())
        val board = board(id = "img", name = "img", url = "https://may.2chan.net/img/futaba.php")
        val mockBoard = board(id = "mock", name = "mock", url = "https://example.com/mock/futaba.php")
        val history = listOf(historyEntry(boardId = "img", boardName = "img"))
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val preferencesState = buildFutachaScreenPreferencesState(
            FutachaScreenPreferencesStateInputs(
                appVersion = "1.2.3",
                isBackgroundRefreshEnabled = false,
                isLightweightModeEnabled = true,
                manualSaveDirectory = "custom",
                manualSaveLocation = SaveLocation.Path("custom"),
                resolvedManualSaveDirectory = "/virtual/custom",
                attachmentPickerPreference = AttachmentPickerPreference.MEDIA,
                saveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
                preferredFileManagerPackage = null,
                preferredFileManagerLabel = null,
                threadMenuEntries = emptyList(),
                catalogNavEntries = emptyList()
            )
        )
        val preferencesCallbacks = ScreenPreferencesCallbacks()
        val historyCallbacks = ScreenHistoryCallbacks()
        val screenContract = buildFutachaScreenContractContext(
            history = history,
            historyCallbacks = historyCallbacks,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks
        )
        var selectedBoard: BoardSummary? = null
        val boardCallbacks = FutachaBoardScreenCallbacks(
            onBoardSelected = { selectedBoard = it },
            onAddBoard = { _, _ -> },
            onMenuAction = {},
            onBoardDeleted = {},
            onBoardsReordered = {}
        )
        var selectedSavedThread: SavedThread? = null
        var savedThreadsDismissed = false
        var catalogSelection: List<Any?>? = null
        var threadDismissed = false
        var registeredUrl: String? = null
        val navigationCallbacks = FutachaNavigationCallbacks(
            onHistoryEntrySelected = {},
            onSavedThreadSelected = { selectedSavedThread = it },
            onCatalogThreadSelected = { threadId, title, replies, thumbnailUrl, threadUrl ->
                catalogSelection = listOf(threadId, title, replies, thumbnailUrl, threadUrl)
            },
            onSavedThreadsDismissed = { savedThreadsDismissed = true },
            onBoardSelectionCleared = {},
            onThreadDismissed = { threadDismissed = true },
            onRegisteredThreadUrlClick = { url ->
                registeredUrl = url
                true
            }
        )

        val screenBindings = FutachaScreenBindingsBundle(
            navigationCallbacks = navigationCallbacks,
            boardScreenCallbacks = boardCallbacks,
            screenPreferencesState = ScreenPreferencesState(appVersion = "1.2.3"),
            screenPreferencesCallbacks = ScreenPreferencesCallbacks(),
            screenHistoryCallbacks = ScreenHistoryCallbacks(),
            screenContract = screenContract
        )
        val assemblyContext = buildFutachaDestinationAssemblyContext(
            screenBindings = screenBindings,
            updateNavigationState = {},
            stateStore = stateStore,
            sharedRepository = sharedRepository,
            httpClient = null,
            fileSystem = null,
            cookieRepository = null,
            autoSavedThreadRepository = null,
            navigationState = FutachaNavigationState(
                selectedBoardId = board.id,
                selectedThreadId = "321",
                selectedThreadTitle = "thread title",
                selectedThreadReplies = 12,
                selectedThreadUrl = "https://may.2chan.net/img/res/321.htm"
            )
        )

        val savedProps = buildFutachaSavedThreadsDestinationProps(
            repository = manualRepository,
            navigationCallbacks = navigationCallbacks
        )
        val savedThread = savedThread(threadId = "saved-123", boardId = "img", boardName = "img")
        savedProps.onThreadClick(savedThread)
        savedProps.onBack()
        assertSame(manualRepository, savedProps.repository)
        assertEquals(savedThread, selectedSavedThread)
        assertTrue(savedThreadsDismissed)

        val boardProps = buildFutachaBoardManagementDestinationProps(
            boards = listOf(board),
            context = assemblyContext
        )
        boardProps.onBoardSelected(board)
        assertEquals(board, selectedBoard)
        assertSame(screenContract, boardProps.screenContract)
        assertNull(boardProps.dependencies.fileSystem)
        assertNull(boardProps.dependencies.autoSavedThreadRepository)

        val catalogProps = buildFutachaCatalogDestinationProps(
            board = board,
            context = assemblyContext
        )
        catalogProps.onThreadSelected(
            FutachaThreadSelection(
                boardId = board.id,
                threadId = "321",
                threadTitle = "catalog-title",
                threadReplies = 7,
                threadThumbnailUrl = "catalog-thumb",
                threadUrl = "https://may.2chan.net/img/res/321.htm"
            )
        )
        assertSame(sharedRepository, catalogProps.dependencies.repository)
        assertNull(catalogProps.dependencies.fileSystem)
        assertEquals("catalog-img", catalogProps.saveableStateKey)
        assertEquals(
            listOf(
                "321",
                "catalog-title",
                7,
                "catalog-thumb",
                "https://may.2chan.net/img/res/321.htm"
            ),
            catalogSelection
        )
        assertNull(
            buildFutachaCatalogDestinationProps(
                board = mockBoard,
                context = assemblyContext
            ).dependencies.repository
        )

        val threadContext = FutachaThreadHistoryContext(
            title = "thread title",
            threadUrl = "https://may.2chan.net/img/res/321.htm",
            replyCount = 12,
            thumbnailUrl = "thumb"
        )
        val threadProps = buildFutachaThreadDestinationProps(
            board = board,
            threadId = "321",
            historyContext = threadContext,
            onScrollPositionPersist = { _, _, _ -> },
            onScrollPositionPersistImmediately = { _, _, _ -> },
            context = assemblyContext
        )
        assertEquals(threadContext, threadProps.historyContext)
        assertSame(sharedRepository, threadProps.dependencies.repository)
        assertNotNull(threadProps.dependencies.stateStore)
        assertEquals("thread title", threadProps.threadTitle)
        assertEquals(12, threadProps.initialReplyCount)
        assertTrue(threadProps.onRegisteredThreadUrlClick("https://may.2chan.net/img/res/999.htm"))
        assertEquals("https://may.2chan.net/img/res/999.htm", registeredUrl)
        threadProps.onBack()
        assertTrue(threadDismissed)

        val savedContent = buildFutachaResolvedDestinationContent(
            destination = FutachaDestination.SavedThreads,
            boards = listOf(board),
            activeSavedThreadsRepository = manualRepository,
            assemblyContext = assemblyContext,
            coroutineScope = destinationCoroutineScope
        )
        assertIs<FutachaResolvedDestinationContent.SavedThreads>(savedContent)
        assertFalse(savedContent.isAdBannerVisible)
        assertEquals("SavedThreads", savedContent.adSyncLabel)
        assertSame(manualRepository, savedContent.props?.repository)

        val catalogContent = buildFutachaResolvedDestinationContent(
            destination = FutachaDestination.Catalog(board),
            boards = listOf(board),
            activeSavedThreadsRepository = manualRepository,
            assemblyContext = assemblyContext,
            coroutineScope = destinationCoroutineScope
        )
        assertIs<FutachaResolvedDestinationContent.Catalog>(catalogContent)
        assertFalse(catalogContent.isAdBannerVisible)
        assertEquals("Catalog(img)", catalogContent.adSyncLabel)
        assertEquals("catalog-img", catalogContent.props.saveableStateKey)

        val threadContent = buildFutachaResolvedDestinationContent(
            destination = FutachaDestination.Thread(board, "321"),
            boards = listOf(board),
            activeSavedThreadsRepository = manualRepository,
            assemblyContext = assemblyContext,
            coroutineScope = destinationCoroutineScope
        )
        assertIs<FutachaResolvedDestinationContent.Thread>(threadContent)
        assertTrue(threadContent.isAdBannerVisible)
        assertEquals("Thread(board=img, thread=321)", threadContent.adSyncLabel)
        assertEquals("thread title", threadContent.props.historyContext.title)
    }

    @Test
    fun futachaBoardScreenCallbacks_handleSelectionAndBoardMutations() = runBlocking {
        var currentBoards = listOf(
            board(id = "img", name = "img", url = "https://may.2chan.net/img/futaba.php"),
            board(id = "dat", name = "dat", url = "https://may.2chan.net/dat/futaba.php")
        )
        var navigationState = FutachaNavigationState()
        val savedBoardsSnapshots = mutableListOf<List<BoardSummary>>()
        val callbacks = buildFutachaBoardScreenCallbacks(
            coroutineScope = this,
            inputs = FutachaBoardScreenCallbackInputs(
                currentNavigationState = { navigationState },
                setNavigationState = { navigationState = it },
                updateBoards = { transform ->
                    val updatedBoards = transform(currentBoards)
                    if (updatedBoards != currentBoards) {
                        currentBoards = updatedBoards
                        savedBoardsSnapshots += currentBoards
                    }
                }
            )
        )

        callbacks.onBoardSelected(currentBoards[0])
        callbacks.onMenuAction(BoardManagementMenuAction.SAVED_THREADS)
        callbacks.onAddBoard("jun", "may.2chan.net/jun")
        callbacks.onAddBoard("duplicate", "https://may.2chan.net/img/futaba.php")
        callbacks.onBoardDeleted(currentBoards.first { it.id == "img" })
        callbacks.onBoardsReordered(currentBoards.reversed())
        yield()
        delay(1)

        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "img",
                isSavedThreadsVisible = true
            ),
            navigationState
        )
        assertEquals(
            listOf("img", "dat", "jun"),
            savedBoardsSnapshots[0].map { it.id }
        )
        assertEquals(
            listOf("dat", "jun"),
            savedBoardsSnapshots[1].map { it.id }
        )
        assertEquals(
            listOf("jun", "dat"),
            savedBoardsSnapshots[2].map { it.id }
        )
    }

    @Test
    fun futachaNavigationCallbacks_handleCrossScreenSelectionAndBackActions() {
        val boards = listOf(
            board(id = "img", name = "img", url = "https://may.2chan.net/img/futaba.php"),
            board(id = "dat", name = "dat", url = "https://may.2chan.net/dat/futaba.php")
        )
        var navigationState = FutachaNavigationState(isSavedThreadsVisible = true)
        val callbacks = buildFutachaNavigationCallbacks(
            currentBoards = { boards },
            currentNavigationState = { navigationState },
            setNavigationState = { navigationState = it }
        )

        callbacks.onSavedThreadsDismissed()
        assertEquals(FutachaNavigationState(), navigationState)

        callbacks.onHistoryEntrySelected(
            historyEntry(
                threadId = "123",
                boardId = "dat",
                boardName = "dat",
                boardUrl = "https://may.2chan.net/dat/res/123.htm"
            )
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "dat",
                selectedThreadId = "123",
                selectedThreadTitle = "title-123",
                selectedThreadReplies = 10,
                selectedThreadThumbnailUrl = "thumb-123",
                selectedThreadUrl = "https://may.2chan.net/dat/res/123.htm"
            ),
            navigationState
        )

        callbacks.onThreadDismissed()
        assertEquals(
            FutachaNavigationState(selectedBoardId = "dat"),
            navigationState
        )

        callbacks.onCatalogThreadSelected(
            "999",
            "catalog-title",
            7,
            "catalog-thumb",
            "https://may.2chan.net/dat/res/999.htm"
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "dat",
                selectedThreadId = "999",
                selectedThreadTitle = "catalog-title",
                selectedThreadReplies = 7,
                selectedThreadThumbnailUrl = "catalog-thumb",
                selectedThreadUrl = "https://may.2chan.net/dat/res/999.htm"
            ),
            navigationState
        )

        callbacks.onBoardSelectionCleared()
        assertEquals(FutachaNavigationState(), navigationState)

        callbacks.onSavedThreadSelected(
            savedThread(
                threadId = "456",
                boardId = "img",
                boardName = "img"
            )
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "img",
                selectedThreadId = "456",
                selectedThreadTitle = "saved-title-456",
                selectedThreadReplies = 10
            ),
            navigationState
        )
    }

    @Test
    fun futachaNavigationCallbacks_handleRegisteredThreadUrlsAndIgnoreUnknownTargets() {
        val boards = listOf(
            board(id = "img", name = "img", url = "https://may.2chan.net/img/futaba.php"),
            board(id = "dat", name = "dat", url = "https://may.2chan.net/dat/futaba.php")
        )
        var navigationState = FutachaNavigationState(
            selectedBoardId = "img",
            selectedThreadId = "123",
            selectedThreadUrl = "https://may.2chan.net/img/res/123.htm"
        )
        val callbacks = buildFutachaNavigationCallbacks(
            currentBoards = { boards },
            currentNavigationState = { navigationState },
            setNavigationState = { navigationState = it }
        )

        assertFalse(callbacks.onRegisteredThreadUrlClick("https://jun.2chan.net/jun/res/456.htm"))
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "img",
                selectedThreadId = "123",
                selectedThreadUrl = "https://may.2chan.net/img/res/123.htm"
            ),
            navigationState
        )

        assertTrue(callbacks.onRegisteredThreadUrlClick("https://may.2chan.net/dat/res/456.htm"))
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "dat",
                selectedThreadId = "456",
                selectedThreadUrl = "https://may.2chan.net/dat/res/456.htm"
            ),
            navigationState
        )
    }

    @Test
    fun savedThreadRepositoryHelpers_buildResolveAndPreferRepositories() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val customLocation = SaveLocation.Path("custom")
        val repositories = buildFutachaSavedThreadsRepositories(
            FutachaSavedThreadsRepositoryInputs(
                fileSystem = fileSystem,
                manualSaveDirectory = "custom",
                manualSaveLocation = customLocation
            )
        )
        val currentRepository = assertNotNull(repositories.currentRepository)
        val legacyRepository = assertNotNull(repositories.legacyRepository)

        legacyRepository.addThreadToIndex(savedThread(threadId = "legacy", boardId = "img")).getOrThrow()

        assertSame(
            legacyRepository,
            selectPreferredSavedThreadsRepository(
                currentRepository = currentRepository,
                legacyRepository = legacyRepository,
                currentCount = 0,
                legacyCount = 1
            )
        )
        assertSame(
            legacyRepository,
            resolveActiveSavedThreadsRepository(
                FutachaActiveSavedThreadsRepositoryInputs(
                    currentRepository = currentRepository,
                    legacyRepository = legacyRepository
                )
            )
        )
        assertEquals(
            "/virtual/custom",
            resolveFutachaManualSaveDirectoryDisplay(
                fileSystem = fileSystem,
                manualSaveDirectory = "custom",
                manualSaveLocation = customLocation
            )
        )
        assertFalse(shouldResetInaccessibleManualSaveBookmark(fileSystem, customLocation))
    }

    @Test
    fun savedThreadRepositoryHelpers_skipLegacyForDefaultPathAndDetectBookmarkFallback() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val defaultLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
        val repositories = buildFutachaSavedThreadsRepositories(
            FutachaSavedThreadsRepositoryInputs(
                fileSystem = fileSystem,
                manualSaveDirectory = DEFAULT_MANUAL_SAVE_ROOT,
                manualSaveLocation = defaultLocation
            )
        )

        assertNotNull(repositories.currentRepository)
        assertNull(repositories.legacyRepository)
        assertEquals(
            "SAF: content://picked/tree",
            resolveFutachaManualSaveDirectoryDisplay(
                fileSystem = fileSystem,
                manualSaveDirectory = DEFAULT_MANUAL_SAVE_ROOT,
                manualSaveLocation = SaveLocation.TreeUri("content://picked/tree")
            )
        )
        assertEquals(
            "Bookmark: 保存先が選択済みです",
            resolveFutachaManualSaveDirectoryDisplay(
                fileSystem = fileSystem,
                manualSaveDirectory = DEFAULT_MANUAL_SAVE_ROOT,
                manualSaveLocation = SaveLocation.Bookmark("bookmark-token")
            )
        )
        assertTrue(
            shouldResetInaccessibleManualSaveBookmark(
                fileSystem = fileSystem,
                manualSaveLocation = SaveLocation.Bookmark("bookmark-token")
            )
        )
    }

    @Test
    fun futachaThreadMutationCallbacks_persistScrollPositionWithThreadContext() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val board = board(id = "img-b", name = "img", url = "https://may.2chan.net/img/futaba.php")
        val context = FutachaThreadHistoryContext(
            title = "thread title",
            threadUrl = "https://may.2chan.net/img/res/123.htm",
            replyCount = 12,
            thumbnailUrl = "thumb"
        )
        store.setHistory(
            listOf(
                historyEntry(
                    threadId = "123",
                    boardId = "img-b",
                    boardName = "img",
                    boardUrl = "https://may.2chan.net/img/res/123.htm"
                )
            )
        )
        val callbacks = buildFutachaThreadMutationCallbacks(
            coroutineScope = this,
            stateStore = store,
            board = board,
            historyContext = context
        )

        callbacks.onScrollPositionPersist("123", 9, 21)
        yield()
        delay(1)

        assertEquals(
            9,
            store.history.first().first().lastReadItemIndex
        )
        assertEquals(
            21,
            store.history.first().first().lastReadItemOffset
        )
    }

    @Test
    fun futachaThreadMutationCallbacks_forceImmediateScrollPersistForShortNearbyMove() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val board = board(id = "img-b", name = "img", url = "https://may.2chan.net/img/futaba.php")
        val context = FutachaThreadHistoryContext(
            title = "thread title",
            threadUrl = "https://may.2chan.net/img/res/123.htm",
            replyCount = 12,
            thumbnailUrl = "thumb"
        )
        store.setHistory(
            listOf(
                historyEntry(
                    threadId = "123",
                    boardId = "img-b",
                    boardName = "img",
                    boardUrl = "https://may.2chan.net/img/res/123.htm"
                ).copy(
                    lastVisitedEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    lastReadItemIndex = 9,
                    lastReadItemOffset = 21
                )
            )
        )
        val callbacks = buildFutachaThreadMutationCallbacks(
            coroutineScope = this,
            stateStore = store,
            board = board,
            historyContext = context
        )

        callbacks.onScrollPositionPersistImmediately("123", 10, 40)
        yield()
        delay(1)

        assertEquals(
            10,
            store.history.first().first().lastReadItemIndex
        )
        assertEquals(
            40,
            store.history.first().first().lastReadItemOffset
        )
    }

    @Test
    fun recordFutachaVisitedThread_recordsAndSkipsUsingHistoryPolicy() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val board = board(id = "img-b", name = "img", url = "https://may.2chan.net/img/futaba.php")
        val context = FutachaThreadHistoryContext(
            title = "thread title",
            threadUrl = "https://may.2chan.net/img/res/123.htm",
            replyCount = 12,
            thumbnailUrl = "thumb"
        )
        val existing = historyEntry(
            threadId = "123",
            boardId = "img-b",
            boardName = "img",
            boardUrl = "https://may.2chan.net/img/res/123.htm"
        ).copy(
            lastVisitedEpochMillis = 100_000L,
            lastReadItemIndex = 7,
            lastReadItemOffset = 11
        )
        store.setHistory(listOf(existing))

        assertFalse(
            recordFutachaVisitedThread(
                stateStore = store,
                history = store.history.first(),
                threadId = "123",
                board = board,
                context = context,
                currentTimeMillis = 120_000L
            )
        )
        assertEquals(existing, store.history.first().first())

        assertTrue(
            recordFutachaVisitedThread(
                stateStore = store,
                history = store.history.first(),
                threadId = "123",
                board = board,
                context = context,
                currentTimeMillis = 170_000L
            )
        )
        assertEquals(
            ThreadHistoryEntry(
                threadId = "123",
                boardId = "img-b",
                title = "thread title",
                titleImageUrl = "thumb",
                boardName = "img",
                boardUrl = "https://may.2chan.net/img/res/123.htm",
                lastVisitedEpochMillis = 170_000L,
                replyCount = 12,
                lastReadItemIndex = 7,
                lastReadItemOffset = 11
            ),
            store.history.first().first()
        )
    }

    @Test
    fun futachaNavigationState_helpers_clearAndApplySelection() {
        val base = FutachaNavigationState(
            selectedBoardId = "img-b",
            selectedThreadId = "123",
            selectedThreadTitle = "title",
            selectedThreadReplies = 42,
            selectedThreadThumbnailUrl = "thumb",
            selectedThreadUrl = "https://may.2chan.net/img/res/123.htm",
            isSavedThreadsVisible = true
        )

        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "img-b",
                isSavedThreadsVisible = true
            ),
            clearFutachaThreadSelection(base)
        )
        assertEquals(
            FutachaNavigationState(isSavedThreadsVisible = true),
            clearFutachaThreadSelection(base, clearBoardSelection = true)
        )
        assertEquals(
            FutachaNavigationState(selectedBoardId = "dat", isSavedThreadsVisible = false),
            selectFutachaBoard(base, "dat")
        )

        val selection = FutachaThreadSelection(
            boardId = "jun",
            threadId = "456",
            threadTitle = "next",
            threadReplies = 12,
            threadThumbnailUrl = "next-thumb",
            threadUrl = "https://jun.2chan.net/jun/res/456.htm",
            isSavedThreadsVisible = false
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "jun",
                selectedThreadId = "456",
                selectedThreadTitle = "next",
                selectedThreadReplies = 12,
                selectedThreadThumbnailUrl = "next-thumb",
                selectedThreadUrl = "https://jun.2chan.net/jun/res/456.htm",
                isSavedThreadsVisible = false
            ),
            applyFutachaThreadSelection(FutachaNavigationState(), selection)
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "img-b",
                selectedThreadId = "789",
                selectedThreadTitle = "catalog",
                selectedThreadReplies = 7,
                selectedThreadThumbnailUrl = "catalog-thumb",
                selectedThreadUrl = "https://may.2chan.net/img/res/789.htm",
                isSavedThreadsVisible = false
            ),
            selectCatalogThread(
                state = base,
                threadId = "789",
                title = "catalog",
                replies = 7,
                thumbnailUrl = "catalog-thumb",
                threadUrl = "https://may.2chan.net/img/res/789.htm"
            )
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "jun",
                selectedThreadId = "456",
                selectedThreadTitle = "next",
                selectedThreadReplies = 12,
                selectedThreadThumbnailUrl = "next-thumb",
                selectedThreadUrl = "https://jun.2chan.net/jun/res/456.htm",
                isSavedThreadsVisible = false
            ),
            selectCatalogThread(base, selection.copy(isSavedThreadsVisible = true))
        )
        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "img-b",
                isSavedThreadsVisible = false
            ),
            dismissSavedThreads(
                FutachaNavigationState(
                    selectedBoardId = "img-b",
                    isSavedThreadsVisible = true
                )
            )
        )
    }

    @Test
    fun resolveFutachaDestination_routesByNavigationState() {
        val board = board(id = "img-b")
        assertEquals(
            FutachaDestination.BoardManagement,
            resolveFutachaDestination(FutachaNavigationState(), listOf(board))
        )
        assertEquals(
            FutachaDestination.SavedThreads,
            resolveFutachaDestination(
                FutachaNavigationState(isSavedThreadsVisible = true),
                listOf(board)
            )
        )
        assertEquals(
            FutachaDestination.MissingBoard("missing"),
            resolveFutachaDestination(
                FutachaNavigationState(selectedBoardId = "missing"),
                listOf(board)
            )
        )
        assertEquals(
            FutachaDestination.Catalog(board),
            resolveFutachaDestination(
                FutachaNavigationState(selectedBoardId = "img-b"),
                listOf(board)
            )
        )
        assertEquals(
            FutachaDestination.Thread(board, "123"),
            resolveFutachaDestination(
                FutachaNavigationState(
                    selectedBoardId = "img-b",
                    selectedThreadId = "123"
                ),
                listOf(board)
            )
        )
    }

    @Test
    fun applyRegisteredThreadNavigation_switchesBoardAndClearsThreadDecorations() {
        val base = FutachaNavigationState(
            selectedBoardId = "old",
            selectedThreadId = "123",
            selectedThreadTitle = "old",
            selectedThreadReplies = 99,
            selectedThreadThumbnailUrl = "thumb",
            selectedThreadUrl = "https://old/res/123.htm",
            isSavedThreadsVisible = true
        )
        val target = RegisteredThreadNavigation(
            board = board(id = "new"),
            threadId = "456",
            threadUrl = "https://new/res/456.htm"
        )

        assertEquals(
            FutachaNavigationState(
                selectedBoardId = "new",
                selectedThreadId = "456",
                selectedThreadUrl = "https://new/res/456.htm",
                isSavedThreadsVisible = false
            ),
            applyRegisteredThreadNavigation(base, target)
        )
    }

    @Test
    fun futachaThreadHistory_helpers_buildMatchAndSkipCorrectly() {
        val board = board(id = "img-b", name = "img", url = "https://may.2chan.net/img/futaba.php")
        val navigationState = FutachaNavigationState(
            selectedThreadTitle = "thread title",
            selectedThreadReplies = 12,
            selectedThreadThumbnailUrl = "thumb",
            selectedThreadUrl = "https://may.2chan.net/img/res/123.htm"
        )
        val context = buildFutachaThreadHistoryContext(board, navigationState)
        assertEquals(
            FutachaThreadHistoryContext(
                title = "thread title",
                threadUrl = "https://may.2chan.net/img/res/123.htm",
                replyCount = 12,
                thumbnailUrl = "thumb"
            ),
            context
        )

        val existing = ThreadHistoryEntry(
            threadId = "123",
            boardId = "img-b",
            title = "title-123",
            titleImageUrl = "thumb-123",
            boardName = "img",
            boardUrl = "https://may.2chan.net/img/res/123.htm",
            lastVisitedEpochMillis = 90_000L,
            replyCount = 10,
            lastReadItemIndex = 7,
            lastReadItemOffset = 11
        )
        val fallbackBoardUrlMatch = ThreadHistoryEntry(
            threadId = "123",
            boardId = "",
            title = "title-123",
            titleImageUrl = "thumb-123",
            boardName = "img",
            boardUrl = "https://may.2chan.net/img/res/123.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )

        assertEquals(
            existing,
            findFutachaVisitedHistoryEntry(
                history = listOf(existing, fallbackBoardUrlMatch),
                threadId = "123",
                boardId = "img-b",
                boardUrl = "https://may.2chan.net/img/res/123.htm"
            )
        )
        assertEquals(
            fallbackBoardUrlMatch,
            findFutachaVisitedHistoryEntry(
                history = listOf(fallbackBoardUrlMatch),
                threadId = "123",
                boardId = "img-b",
                boardUrl = "https://may.2chan.net/img/res/123.htm"
            )
        )
        assertTrue(
            shouldSkipFutachaVisitedHistoryUpdate(
                existingEntry = existing,
                boardId = "img-b",
                currentTimeMillis = 100_000L
            )
        )
        assertFalse(
            shouldSkipFutachaVisitedHistoryUpdate(
                existingEntry = existing,
                boardId = "other",
                currentTimeMillis = 100_000L
            )
        )

        assertEquals(
            ThreadHistoryEntry(
                threadId = "123",
                boardId = "img-b",
                title = "thread title",
                titleImageUrl = "thumb",
                boardName = "img",
                boardUrl = "https://may.2chan.net/img/res/123.htm",
                lastVisitedEpochMillis = 100_000L,
                replyCount = 12,
                lastReadItemIndex = 7,
                lastReadItemOffset = 11
            ),
            buildFutachaVisitedHistoryEntry(
                threadId = "123",
                board = board,
                context = context,
                currentTimeMillis = 100_000L,
                existingEntry = existing
            )
        )
    }

    @Test
    fun resolveHistoryEntrySelection_prefersBoardIdAndKeepsThreadUrlWhenResUrl() {
        val targetBoard = board(
            id = "img-b",
            name = "img",
            url = "https://may.2chan.net/img/futaba.php"
        )
        val entry = historyEntry(
            boardId = "img-b",
            boardName = "fallback-name",
            boardUrl = "https://may.2chan.net/img/res/123.htm"
        )

        val selection = resolveHistoryEntrySelection(entry, listOf(targetBoard, board(id = "other", name = "fallback-name")))

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
        assertEquals("123", selection.threadId)
        assertEquals(entry.title, selection.threadTitle)
        assertEquals(10, selection.threadReplies)
        assertEquals(entry.titleImageUrl, selection.threadThumbnailUrl)
        assertEquals("https://may.2chan.net/img/res/123.htm", selection.threadUrl)
    }

    @Test
    fun resolveHistoryEntrySelection_fallsBackToBoardUrlAndDropsNonThreadUrl() {
        val board = board(
            id = "img-b",
            name = "img",
            url = "https://may.2chan.net/img/futaba.php?mode=cat"
        )
        val entry = historyEntry(
            boardId = "",
            boardName = "different",
            boardUrl = "https://may.2chan.net/img/futaba.php"
        )

        val selection = resolveHistoryEntrySelection(entry, listOf(board))

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
        assertNull(selection.threadUrl)
    }

    @Test
    fun resolveHistoryEntrySelection_returnsNullWhenNoBoardMatches() {
        val entry = historyEntry(boardId = "missing", boardName = "missing", boardUrl = "https://nope.invalid/res/1.htm")

        val selection = resolveHistoryEntrySelection(entry, listOf(board()))

        assertNull(selection)
    }

    @Test
    fun resolveSavedThreadSelection_prefersBoardIdAndHidesSavedThreads() {
        val savedThread = savedThread(boardId = "img-b", boardName = "fallback")

        val selection = resolveSavedThreadSelection(
            thread = savedThread,
            boards = listOf(board(id = "img-b", name = "img"))
        )

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
        assertEquals(savedThread.threadId, selection.threadId)
        assertEquals(savedThread.title, selection.threadTitle)
        assertEquals(savedThread.postCount, selection.threadReplies)
        assertNull(selection.threadThumbnailUrl)
        assertNull(selection.threadUrl)
        assertFalse(selection.isSavedThreadsVisible)
    }

    @Test
    fun resolveSavedThreadSelection_fallsBackToBoardName() {
        val savedThread = savedThread(boardId = "missing", boardName = "img")

        val selection = resolveSavedThreadSelection(
            thread = savedThread,
            boards = listOf(board(id = "img-b", name = "img"))
        )

        requireNotNull(selection)
        assertEquals("img-b", selection.boardId)
    }

    @Test
    fun shouldApplyRegisteredThreadNavigation_onlySkipsExactSameTarget() {
        val target = RegisteredThreadNavigation(
            board = board(id = "img-b"),
            threadId = "123",
            threadUrl = "https://may.2chan.net/img/res/123.htm"
        )

        assertFalse(
            shouldApplyRegisteredThreadNavigation(
                currentBoardId = "img-b",
                currentThreadId = "123",
                currentThreadUrl = "https://may.2chan.net/img/res/123.htm",
                target = target
            )
        )
        assertTrue(
            shouldApplyRegisteredThreadNavigation(
                currentBoardId = "img-b",
                currentThreadId = "123",
                currentThreadUrl = "https://may.2chan.net/img/res/123.html",
                target = target
            )
        )
    }

    @Test
    fun normalizeBoardUrl_addsSchemeAndFutabaPathWhilePreservingQueryAndFragment() {
        assertEquals(
            "https://may.2chan.net/b/futaba.php",
            normalizeBoardUrl("may.2chan.net/b")
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat#frag",
            normalizeBoardUrl("https://may.2chan.net/b?mode=cat#frag")
        )
        assertEquals(
            "http://may.2chan.net/b/futaba.php",
            normalizeBoardUrl("http://may.2chan.net/b/")
        )
    }

    @Test
    fun appSupport_helpers_detectDefaultSaveRootAndMockBoards() {
        assertTrue(isDefaultManualSaveRoot("./Documents/"))
        assertFalse(isDefaultManualSaveRoot("/tmp/custom"))
        assertTrue(board(url = "https://example.com/futaba.php").isMockBoard())
        assertFalse(board(url = "https://may.2chan.net/b/futaba.php").isMockBoard())
    }

    @Test
    fun isSelectedBoardStillMissing_requiresSameSelectionAndAbsentBoard() {
        val boards = listOf(board(id = "img-b"))

        assertTrue(
            isSelectedBoardStillMissing(
                selectedBoardId = "missing",
                missingBoardId = "missing",
                boards = boards
            )
        )
        assertFalse(
            isSelectedBoardStillMissing(
                selectedBoardId = "img-b",
                missingBoardId = "missing",
                boards = boards
            )
        )
        assertFalse(
            isSelectedBoardStillMissing(
                selectedBoardId = "missing",
                missingBoardId = "img-b",
                boards = boards
            )
        )
    }

    @Test
    fun resolveHistoryEntryBoardId_usesFallbackSlugWhenBoardIdIsBlank() {
        val entry = historyEntry(
            boardId = "",
            boardUrl = "https://may.2chan.net/img/res/123.htm"
        )

        val resolved = resolveHistoryEntryBoardId(entry)

        assertEquals("img", resolved)
    }

    @Test
    fun dismissHistoryEntry_removesHistorySelfIdentifiersAndAutoSavedThread() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "saved_threads")
        val entry = historyEntry(boardId = "", boardUrl = "https://may.2chan.net/img/res/123.htm")
        store.setHistory(listOf(entry))
        store.addSelfPostIdentifier(threadId = entry.threadId, identifier = "ID:abc", boardId = "img")
        repository.addThreadToIndex(savedThread(boardId = "img")).getOrThrow()

        dismissHistoryEntry(
            stateStore = store,
            autoSavedThreadRepository = repository,
            entry = entry
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
        assertFalse(repository.threadExists(entry.threadId, "img"))
    }

    @Test
    fun dismissHistoryEntry_preservesStateChangesWhenAutoSavedDeleteFails() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(
            DeleteRecursivelyFailingFileSystem(InMemoryFileSystem()),
            baseDirectory = "saved_threads"
        )
        val entry = historyEntry(boardId = "img")
        store.setHistory(listOf(entry))
        store.addSelfPostIdentifier(threadId = entry.threadId, identifier = "ID:abc", boardId = "img")
        repository.addThreadToIndex(savedThread(boardId = "img")).getOrThrow()
        dismissHistoryEntry(
            stateStore = store,
            autoSavedThreadRepository = repository,
            entry = entry
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
    }

    @Test
    fun clearHistory_clearsStateInvokesSkippedCallbackAndDeletesAutoSavedThreads() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "saved_threads")
        val firstEntry = historyEntry(threadId = "123", boardId = "img")
        val secondEntry = historyEntry(threadId = "456", boardId = "dat")
        store.setHistory(listOf(firstEntry, secondEntry))
        store.addSelfPostIdentifier(threadId = "123", identifier = "ID:abc", boardId = "img")
        store.addSelfPostIdentifier(threadId = "456", identifier = "ID:def", boardId = "dat")
        repository.addThreadToIndex(savedThread(threadId = "123", boardId = "img")).getOrThrow()
        repository.addThreadToIndex(savedThread(threadId = "456", boardId = "dat")).getOrThrow()
        var clearedCount = 0

        clearHistory(
            stateStore = store,
            autoSavedThreadRepository = repository,
            onSkippedThreadsCleared = { clearedCount += 1 }
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
        assertEquals(1, clearedCount)
        assertFalse(repository.threadExists("123", "img"))
        assertFalse(repository.threadExists("456", "dat"))
    }

    @Test
    fun clearHistory_reportsAutoSavedDeleteFailureButStillClearsStoreState() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val repository = SavedThreadRepository(
            DeleteRecursivelyFailingFileSystem(InMemoryFileSystem()),
            baseDirectory = "saved_threads"
        )
        val entry = historyEntry(threadId = "123", boardId = "img")
        store.setHistory(listOf(entry))
        store.addSelfPostIdentifier(threadId = "123", identifier = "ID:abc", boardId = "img")
        repository.addThreadToIndex(savedThread(threadId = "123", boardId = "img")).getOrThrow()
        clearHistory(
            stateStore = store,
            autoSavedThreadRepository = repository,
            onSkippedThreadsCleared = {}
        )

        assertEquals(emptyList(), store.history.first())
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
    }

    private fun board(
        id: String = "img-b",
        name: String = "img",
        url: String = "https://may.2chan.net/img/futaba.php"
    ): BoardSummary {
        return BoardSummary(
            id = id,
            name = name,
            category = "",
            url = url,
            description = ""
        )
    }

    private fun historyEntry(
        threadId: String = "123",
        boardId: String = "img-b",
        boardName: String = "img",
        boardUrl: String = "https://may.2chan.net/img/res/123.htm"
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = boardId,
            title = "title-$threadId",
            titleImageUrl = "thumb-$threadId",
            boardName = boardName,
            boardUrl = boardUrl,
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )
    }

    private fun savedThread(
        threadId: String = "123",
        boardId: String = "img-b",
        boardName: String = "img"
    ): SavedThread {
        return SavedThread(
            threadId = threadId,
            boardId = boardId,
            boardName = boardName,
            title = "saved-title-$threadId",
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 10,
            imageCount = 0,
            videoCount = 0,
            totalSize = 100L,
            status = SaveStatus.COMPLETED
        )
    }
}

private class DeleteRecursivelyFailingFileSystem(
    private val delegate: InMemoryFileSystem
) : FileSystem by delegate {
    override suspend fun deleteRecursively(path: String): Result<Unit> {
        return Result.failure(IllegalStateException("cannot delete"))
    }
}

private class TestVersionChecker(
    private val updateInfo: UpdateInfo? = null,
    private val failure: Throwable? = null
) : VersionChecker {
    override fun getCurrentVersion(): String = "1.0.0"

    override suspend fun checkForUpdate(): UpdateInfo? {
        failure?.let { throw it }
        return updateInfo
    }
}

private class TestBoardRepository : BoardRepository {
    var closeAsyncCalls = 0

    override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = emptyList()

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? = null

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        error("not needed")
    }

    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
        error("not needed")
    }

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

    override fun close() = Unit

    override fun closeAsync(): kotlinx.coroutines.Job {
        closeAsyncCalls += 1
        return CompletableDeferred(Unit)
    }

    override suspend fun clearOpImageCache(board: String?, threadId: String?) = Unit

    override suspend fun invalidateCookies(board: String) = Unit
}
