package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.BoardManagementScreenDependencies
import com.valoser.futacha.shared.ui.board.CatalogScreenDependencies
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

internal data class FutachaAppScreenBindings(
    val preferenceMutations: FutachaPreferenceMutationCallbacks,
    val boardScreenCallbacks: FutachaBoardScreenCallbacks,
    val navigationCallbacks: FutachaNavigationCallbacks,
    val screenPreferencesState: ScreenPreferencesState,
    val screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    val screenHistoryCallbacks: ScreenHistoryCallbacks
)

internal data class FutachaSavedThreadsDestinationProps(
    val repository: SavedThreadRepository,
    val onThreadClick: (SavedThread) -> Unit,
    val onBack: () -> Unit
)

internal data class FutachaBoardManagementDestinationProps(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val historyCallbacks: ScreenHistoryCallbacks,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    val dependencies: BoardManagementScreenDependencies,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks
)

internal data class FutachaCatalogDestinationProps(
    val board: BoardSummary,
    val history: List<ThreadHistoryEntry>,
    val onBack: () -> Unit,
    val onThreadSelected: (FutachaThreadSelection) -> Unit,
    val historyCallbacks: ScreenHistoryCallbacks,
    val dependencies: CatalogScreenDependencies,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val saveableStateKey: String
)

internal data class FutachaThreadDestinationProps(
    val board: BoardSummary,
    val history: List<ThreadHistoryEntry>,
    val threadId: String,
    val historyContext: FutachaThreadHistoryContext,
    val threadTitle: String?,
    val threadUrlOverride: String?,
    val initialReplyCount: Int?,
    val onBack: () -> Unit,
    val historyCallbacks: ScreenHistoryCallbacks,
    val onScrollPositionPersist: (String, Int, Int) -> Unit,
    val dependencies: ThreadScreenDependencies,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val onRegisteredThreadUrlClick: (String) -> Boolean
)

internal fun buildFutachaScreenPreferencesCallbacks(
    mutations: FutachaPreferenceMutationCallbacks,
    onOpenSaveDirectoryPicker: () -> Unit
): ScreenPreferencesCallbacks {
    return ScreenPreferencesCallbacks(
        onBackgroundRefreshChanged = mutations.onBackgroundRefreshChanged,
        onLightweightModeChanged = mutations.onLightweightModeChanged,
        onManualSaveDirectoryChanged = mutations.onManualSaveDirectoryChanged,
        onAttachmentPickerPreferenceChanged = mutations.onAttachmentPickerPreferenceChanged,
        onSaveDirectorySelectionChanged = mutations.onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        onFileManagerSelected = mutations.onFileManagerSelected,
        onClearPreferredFileManager = mutations.onClearPreferredFileManager,
        onThreadMenuEntriesChanged = mutations.onThreadMenuEntriesChanged,
        onCatalogNavEntriesChanged = mutations.onCatalogNavEntriesChanged
    )
}

internal fun buildFutachaAppScreenBindings(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    currentBoards: () -> List<BoardSummary>,
    currentNavigationState: () -> FutachaNavigationState,
    setNavigationState: (FutachaNavigationState) -> Unit,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean,
    isLightweightModeEnabled: Boolean,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation,
    resolvedManualSaveDirectory: String?,
    attachmentPickerPreference: AttachmentPickerPreference,
    saveDirectorySelection: SaveDirectorySelection,
    preferredFileManagerPackage: String?,
    preferredFileManagerLabel: String?,
    threadMenuEntries: List<ThreadMenuEntryConfig>,
    catalogNavEntries: List<CatalogNavEntryConfig>,
    onOpenSaveDirectoryPicker: () -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    onSkippedThreadsCleared: () -> Unit,
    onAutoSavedThreadDeleteFailure: (ThreadHistoryEntry, Throwable) -> Unit,
    onAutoSavedThreadClearFailure: (Throwable) -> Unit
): FutachaAppScreenBindings {
    val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
        coroutineScope = coroutineScope,
        setBackgroundRefreshEnabled = stateStore::setBackgroundRefreshEnabled,
        setLightweightModeEnabled = stateStore::setLightweightModeEnabled,
        setManualSaveDirectory = stateStore::setManualSaveDirectory,
        setAttachmentPickerPreference = stateStore::setAttachmentPickerPreference,
        setSaveDirectorySelection = stateStore::setSaveDirectorySelection,
        setManualSaveLocation = stateStore::setManualSaveLocation,
        setPreferredFileManager = stateStore::setPreferredFileManager,
        setThreadMenuEntries = stateStore::setThreadMenuEntries,
        setCatalogNavEntries = stateStore::setCatalogNavEntries
    )
    val historyMutations = buildFutachaHistoryMutationCallbacks(
        coroutineScope = coroutineScope,
        dismissHistoryEntry = { entry ->
            dismissHistoryEntry(
                stateStore = stateStore,
                autoSavedThreadRepository = autoSavedThreadRepository,
                entry = entry,
                onAutoSavedThreadDeleteFailure = {
                    onAutoSavedThreadDeleteFailure(entry, it)
                }
            )
        },
        updateHistoryEntry = stateStore::upsertHistoryEntry,
        clearHistory = {
            clearHistory(
                stateStore = stateStore,
                autoSavedThreadRepository = autoSavedThreadRepository,
                onSkippedThreadsCleared = onSkippedThreadsCleared,
                onAutoSavedThreadDeleteFailure = onAutoSavedThreadClearFailure
            )
        }
    )
    val navigationCallbacks = buildFutachaNavigationCallbacks(
        currentBoards = currentBoards,
        currentNavigationState = currentNavigationState,
        setNavigationState = setNavigationState
    )
    return FutachaAppScreenBindings(
        preferenceMutations = preferenceMutations,
        boardScreenCallbacks = buildFutachaBoardScreenCallbacks(
            coroutineScope = coroutineScope,
            currentNavigationState = currentNavigationState,
            setNavigationState = setNavigationState,
            updateBoards = stateStore::updateBoards
        ),
        navigationCallbacks = navigationCallbacks,
        screenPreferencesState = buildFutachaScreenPreferencesState(
            appVersion = appVersion,
            isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
            isLightweightModeEnabled = isLightweightModeEnabled,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
            attachmentPickerPreference = attachmentPickerPreference,
            saveDirectorySelection = saveDirectorySelection,
            preferredFileManagerPackage = preferredFileManagerPackage,
            preferredFileManagerLabel = preferredFileManagerLabel,
            threadMenuEntries = threadMenuEntries,
            catalogNavEntries = catalogNavEntries
        ),
        screenPreferencesCallbacks = buildFutachaScreenPreferencesCallbacks(
            mutations = preferenceMutations,
            onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker
        ),
        screenHistoryCallbacks = buildFutachaScreenHistoryCallbacks(
            navigationCallbacks = navigationCallbacks,
            historyMutations = historyMutations,
            onHistoryRefresh = onHistoryRefresh
        )
    )
}

internal fun buildFutachaSavedThreadsDestinationProps(
    repository: SavedThreadRepository,
    navigationCallbacks: FutachaNavigationCallbacks
): FutachaSavedThreadsDestinationProps {
    return FutachaSavedThreadsDestinationProps(
        repository = repository,
        onThreadClick = navigationCallbacks.onSavedThreadSelected,
        onBack = navigationCallbacks.onSavedThreadsDismissed
    )
}

internal fun buildFutachaBoardManagementDestinationProps(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    cookieRepository: CookieRepository?,
    boardScreenCallbacks: FutachaBoardScreenCallbacks,
    historyCallbacks: ScreenHistoryCallbacks,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?
): FutachaBoardManagementDestinationProps {
    return FutachaBoardManagementDestinationProps(
        boards = boards,
        history = history,
        onBoardSelected = boardScreenCallbacks.onBoardSelected,
        onAddBoard = boardScreenCallbacks.onAddBoard,
        onMenuAction = boardScreenCallbacks.onMenuAction,
        historyCallbacks = historyCallbacks,
        onBoardDeleted = boardScreenCallbacks.onBoardDeleted,
        onBoardsReordered = boardScreenCallbacks.onBoardsReordered,
        dependencies = buildFutachaBoardManagementScreenDependencies(
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository
        ),
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks
    )
}

internal fun buildFutachaCatalogDestinationProps(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    navigationCallbacks: FutachaNavigationCallbacks,
    historyCallbacks: ScreenHistoryCallbacks,
    sharedRepository: BoardRepository,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    cookieRepository: CookieRepository?,
    httpClient: HttpClient?
): FutachaCatalogDestinationProps {
    return FutachaCatalogDestinationProps(
        board = board,
        history = history,
        onBack = navigationCallbacks.onBoardSelectionCleared,
        onThreadSelected = { selection ->
            navigationCallbacks.onCatalogThreadSelected(
                selection.threadId,
                selection.threadTitle,
                selection.threadReplies,
                selection.threadThumbnailUrl,
                selection.threadUrl
            )
        },
        historyCallbacks = historyCallbacks,
        dependencies = buildFutachaCatalogScreenDependencies(
            board = board,
            sharedRepository = sharedRepository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            httpClient = httpClient
        ),
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        saveableStateKey = "catalog-${board.id}"
    )
}

internal fun buildFutachaThreadDestinationProps(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    historyContext: FutachaThreadHistoryContext,
    navigationState: FutachaNavigationState,
    navigationCallbacks: FutachaNavigationCallbacks,
    historyCallbacks: ScreenHistoryCallbacks,
    threadMutations: FutachaThreadMutationCallbacks,
    sharedRepository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks
): FutachaThreadDestinationProps {
    return FutachaThreadDestinationProps(
        board = board,
        history = history,
        threadId = threadId,
        historyContext = historyContext,
        threadTitle = navigationState.selectedThreadTitle,
        threadUrlOverride = navigationState.selectedThreadUrl,
        initialReplyCount = navigationState.selectedThreadReplies,
        onBack = navigationCallbacks.onThreadDismissed,
        historyCallbacks = historyCallbacks,
        onScrollPositionPersist = threadMutations.onScrollPositionPersist,
        dependencies = buildFutachaThreadScreenDependencies(
            board = board,
            sharedRepository = sharedRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository
        ),
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        onRegisteredThreadUrlClick = navigationCallbacks.onRegisteredThreadUrlClick
    )
}

internal fun buildFutachaBoardManagementScreenDependencies(
    cookieRepository: CookieRepository?,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?
): BoardManagementScreenDependencies {
    return BoardManagementScreenDependencies(
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        autoSavedThreadRepository = autoSavedThreadRepository
    )
}

internal fun buildFutachaCatalogScreenDependencies(
    board: BoardSummary,
    sharedRepository: BoardRepository,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    cookieRepository: CookieRepository?,
    httpClient: HttpClient?,
    fileSystem: FileSystem? = null
): CatalogScreenDependencies {
    return CatalogScreenDependencies(
        repository = resolveFutachaBoardRepository(board, sharedRepository),
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}

internal fun buildFutachaThreadScreenDependencies(
    board: BoardSummary,
    sharedRepository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?
): ThreadScreenDependencies {
    return ThreadScreenDependencies(
        repository = resolveFutachaBoardRepository(board, sharedRepository),
        httpClient = httpClient,
        fileSystem = fileSystem,
        cookieRepository = cookieRepository,
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository
    )
}

internal fun buildFutachaScreenHistoryCallbacks(
    navigationCallbacks: FutachaNavigationCallbacks,
    historyMutations: FutachaHistoryMutationCallbacks,
    onHistoryRefresh: suspend () -> Unit
): ScreenHistoryCallbacks {
    return ScreenHistoryCallbacks(
        onHistoryEntrySelected = navigationCallbacks.onHistoryEntrySelected,
        onHistoryEntryDismissed = historyMutations.onDismissHistoryEntry,
        onHistoryEntryUpdated = historyMutations.onUpdateHistoryEntry,
        onHistoryRefresh = onHistoryRefresh,
        onHistoryCleared = historyMutations.onClearHistory
    )
}

internal fun buildFutachaScreenPreferencesState(
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean,
    isLightweightModeEnabled: Boolean,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation,
    resolvedManualSaveDirectory: String?,
    attachmentPickerPreference: AttachmentPickerPreference,
    saveDirectorySelection: SaveDirectorySelection,
    preferredFileManagerPackage: String?,
    preferredFileManagerLabel: String?,
    threadMenuEntries: List<ThreadMenuEntryConfig>,
    catalogNavEntries: List<CatalogNavEntryConfig>
): ScreenPreferencesState {
    return ScreenPreferencesState(
        appVersion = appVersion,
        isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
        isLightweightModeEnabled = isLightweightModeEnabled,
        manualSaveDirectory = manualSaveDirectory,
        manualSaveLocation = manualSaveLocation,
        resolvedManualSaveDirectory = resolvedManualSaveDirectory,
        attachmentPickerPreference = attachmentPickerPreference,
        saveDirectorySelection = saveDirectorySelection,
        preferredFileManagerPackage = preferredFileManagerPackage,
        preferredFileManagerLabel = preferredFileManagerLabel,
        threadMenuEntries = threadMenuEntries,
        catalogNavEntries = catalogNavEntries
    )
}
