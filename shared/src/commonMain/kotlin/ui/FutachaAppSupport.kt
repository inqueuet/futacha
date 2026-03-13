package com.valoser.futacha.shared.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.BoardManagementScreenDependencies
import com.valoser.futacha.shared.ui.board.CatalogScreenDependencies
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import com.valoser.futacha.shared.ui.board.resolveRegisteredThreadNavigation
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val FUTACHA_APP_SUPPORT_LOG_TAG = "FutachaApp"

internal data class RepositoryHolder(
    val repository: BoardRepository,
    val ownsRepository: Boolean
)

internal data class FutachaSavedThreadsRepositories(
    val currentRepository: SavedThreadRepository?,
    val legacyRepository: SavedThreadRepository?
)

internal data class FutachaPreferenceMutationCallbacks(
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val onLightweightModeChanged: (Boolean) -> Unit,
    val onManualSaveDirectoryChanged: (String) -> Unit,
    val onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onManualSaveLocationChanged: (SaveLocation) -> Unit,
    val onFileManagerSelected: (packageName: String, label: String) -> Unit,
    val onClearPreferredFileManager: () -> Unit,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
)

internal data class FutachaHistoryMutationCallbacks(
    val onDismissHistoryEntry: (ThreadHistoryEntry) -> Unit,
    val onUpdateHistoryEntry: (ThreadHistoryEntry) -> Unit,
    val onClearHistory: () -> Unit
)

internal data class FutachaThreadMutationCallbacks(
    val onScrollPositionPersist: (String, Int, Int) -> Unit
)

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

internal data class FutachaBoardScreenCallbacks(
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit
)

internal data class FutachaNavigationCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onSavedThreadSelected: (SavedThread) -> Unit,
    val onCatalogThreadSelected: (
        threadId: String,
        title: String?,
        replies: Int?,
        thumbnailUrl: String?,
        threadUrl: String?
    ) -> Unit,
    val onSavedThreadsDismissed: () -> Unit,
    val onBoardSelectionCleared: () -> Unit,
    val onThreadDismissed: () -> Unit,
    val onRegisteredThreadUrlClick: (String) -> Boolean
)

internal data class FutachaThreadSelection(
    val boardId: String,
    val threadId: String,
    val threadTitle: String?,
    val threadReplies: Int?,
    val threadThumbnailUrl: String?,
    val threadUrl: String?,
    val isSavedThreadsVisible: Boolean = false
)

internal data class FutachaThreadHistoryContext(
    val title: String,
    val threadUrl: String,
    val replyCount: Int,
    val thumbnailUrl: String
)

internal data class FutachaNavigationState(
    val selectedBoardId: String? = null,
    val selectedThreadId: String? = null,
    val selectedThreadTitle: String? = null,
    val selectedThreadReplies: Int? = null,
    val selectedThreadThumbnailUrl: String? = null,
    val selectedThreadUrl: String? = null,
    val isSavedThreadsVisible: Boolean = false
) {
    companion object {
        val Saver: Saver<FutachaNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.selectedBoardId,
                    state.selectedThreadId,
                    state.selectedThreadTitle,
                    state.selectedThreadReplies,
                    state.selectedThreadThumbnailUrl,
                    state.selectedThreadUrl,
                    state.isSavedThreadsVisible
                )
            },
            restore = { restored ->
                FutachaNavigationState(
                    selectedBoardId = restored.getOrNull(0) as String?,
                    selectedThreadId = restored.getOrNull(1) as String?,
                    selectedThreadTitle = restored.getOrNull(2) as String?,
                    selectedThreadReplies = restored.getOrNull(3) as Int?,
                    selectedThreadThumbnailUrl = restored.getOrNull(4) as String?,
                    selectedThreadUrl = restored.getOrNull(5) as String?,
                    isSavedThreadsVisible = restored.getOrNull(6) as? Boolean ?: false
                )
            }
        )
    }
}

internal sealed interface FutachaDestination {
    data object SavedThreads : FutachaDestination
    data object BoardManagement : FutachaDestination
    data class MissingBoard(val missingBoardId: String) : FutachaDestination
    data class Catalog(val board: BoardSummary) : FutachaDestination
    data class Thread(val board: BoardSummary, val threadId: String) : FutachaDestination
}

internal fun clearFutachaThreadSelection(
    state: FutachaNavigationState,
    clearBoardSelection: Boolean = false
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = if (clearBoardSelection) null else state.selectedBoardId,
        selectedThreadId = null,
        selectedThreadTitle = null,
        selectedThreadReplies = null,
        selectedThreadThumbnailUrl = null,
        selectedThreadUrl = null
    )
}

internal fun selectFutachaBoard(
    state: FutachaNavigationState,
    boardId: String
): FutachaNavigationState {
    return clearFutachaThreadSelection(
        state = state.copy(
            selectedBoardId = boardId,
            isSavedThreadsVisible = false
        ),
        clearBoardSelection = false
    )
}

internal fun applyFutachaThreadSelection(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = selection.boardId,
        selectedThreadId = selection.threadId,
        selectedThreadTitle = selection.threadTitle,
        selectedThreadReplies = selection.threadReplies,
        selectedThreadThumbnailUrl = selection.threadThumbnailUrl,
        selectedThreadUrl = selection.threadUrl,
        isSavedThreadsVisible = selection.isSavedThreadsVisible
    )
}

internal fun selectCatalogThread(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return applyFutachaThreadSelection(
        state = state.copy(isSavedThreadsVisible = false),
        selection = selection.copy(isSavedThreadsVisible = false)
    )
}

internal fun selectCatalogThread(
    state: FutachaNavigationState,
    threadId: String,
    title: String?,
    replies: Int?,
    thumbnailUrl: String?,
    threadUrl: String?
): FutachaNavigationState {
    return state.copy(
        selectedThreadId = threadId,
        selectedThreadTitle = title,
        selectedThreadReplies = replies,
        selectedThreadThumbnailUrl = thumbnailUrl,
        selectedThreadUrl = threadUrl,
        isSavedThreadsVisible = false
    )
}

internal fun applyRegisteredThreadNavigation(
    state: FutachaNavigationState,
    target: RegisteredThreadNavigation
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = target.board.id,
        selectedThreadId = target.threadId,
        selectedThreadTitle = null,
        selectedThreadReplies = null,
        selectedThreadThumbnailUrl = null,
        selectedThreadUrl = target.threadUrl,
        isSavedThreadsVisible = false
    )
}

internal fun dismissSavedThreads(state: FutachaNavigationState): FutachaNavigationState {
    return state.copy(isSavedThreadsVisible = false)
}

internal fun resolveFutachaDestination(
    navigationState: FutachaNavigationState,
    boards: List<BoardSummary>
): FutachaDestination {
    if (navigationState.selectedBoardId == null) {
        return if (navigationState.isSavedThreadsVisible) {
            FutachaDestination.SavedThreads
        } else {
            FutachaDestination.BoardManagement
        }
    }
    val selectedBoard = boards.firstOrNull { it.id == navigationState.selectedBoardId }
    if (selectedBoard == null) {
        return FutachaDestination.MissingBoard(navigationState.selectedBoardId)
    }
    return if (navigationState.selectedThreadId == null) {
        FutachaDestination.Catalog(selectedBoard)
    } else {
        FutachaDestination.Thread(
            board = selectedBoard,
            threadId = navigationState.selectedThreadId
        )
    }
}

internal fun buildFutachaPreferenceMutationCallbacks(
    coroutineScope: CoroutineScope,
    setBackgroundRefreshEnabled: suspend (Boolean) -> Unit,
    setLightweightModeEnabled: suspend (Boolean) -> Unit,
    setManualSaveDirectory: suspend (String) -> Unit,
    setAttachmentPickerPreference: suspend (AttachmentPickerPreference) -> Unit,
    setSaveDirectorySelection: suspend (SaveDirectorySelection) -> Unit,
    setManualSaveLocation: suspend (SaveLocation) -> Unit,
    setPreferredFileManager: suspend (String?, String?) -> Unit,
    setThreadMenuEntries: suspend (List<ThreadMenuEntryConfig>) -> Unit,
    setCatalogNavEntries: suspend (List<CatalogNavEntryConfig>) -> Unit
): FutachaPreferenceMutationCallbacks {
    return FutachaPreferenceMutationCallbacks(
        onBackgroundRefreshChanged = { enabled ->
            coroutineScope.launch { setBackgroundRefreshEnabled(enabled) }
        },
        onLightweightModeChanged = { enabled ->
            coroutineScope.launch { setLightweightModeEnabled(enabled) }
        },
        onManualSaveDirectoryChanged = { directory ->
            coroutineScope.launch { setManualSaveDirectory(directory) }
        },
        onAttachmentPickerPreferenceChanged = { preference ->
            coroutineScope.launch { setAttachmentPickerPreference(preference) }
        },
        onSaveDirectorySelectionChanged = { selection ->
            coroutineScope.launch { setSaveDirectorySelection(selection) }
        },
        onManualSaveLocationChanged = { location ->
            coroutineScope.launch { setManualSaveLocation(location) }
        },
        onFileManagerSelected = { packageName, label ->
            coroutineScope.launch { setPreferredFileManager(packageName, label) }
        },
        onClearPreferredFileManager = {
            coroutineScope.launch { setPreferredFileManager(null, null) }
        },
        onThreadMenuEntriesChanged = { entries ->
            coroutineScope.launch { setThreadMenuEntries(entries) }
        },
        onCatalogNavEntriesChanged = { entries ->
            coroutineScope.launch { setCatalogNavEntries(entries) }
        }
    )
}

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
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
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
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
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
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
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
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
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
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
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
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
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

internal fun buildFutachaRepositoryHolder(
    httpClient: HttpClient?,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository?,
    createSharedRepository: (HttpClient, com.valoser.futacha.shared.repository.CookieRepository?) -> BoardRepository =
        { client, cookies -> createRemoteBoardRepository(client, cookieRepository = cookies) },
    createOwnedRepository: () -> BoardRepository = { createRemoteBoardRepository() }
): RepositoryHolder {
    return if (httpClient != null) {
        RepositoryHolder(
            repository = createSharedRepository(httpClient, cookieRepository),
            ownsRepository = false
        )
    } else {
        RepositoryHolder(
            repository = createOwnedRepository(),
            ownsRepository = true
        )
    }
}

internal fun buildFutachaAutoSavedThreadRepository(
    fileSystem: FileSystem?,
    existingRepository: SavedThreadRepository?
): SavedThreadRepository? {
    return existingRepository ?: fileSystem?.let {
        SavedThreadRepository(it, baseDirectory = AUTO_SAVE_DIRECTORY)
    }
}

internal fun buildFutachaHistoryRefresher(
    stateStore: AppStateStore,
    repository: BoardRepository,
    autoSavedThreadRepository: SavedThreadRepository?,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    shouldUseLightweightMode: Boolean
): HistoryRefresher {
    return HistoryRefresher(
        stateStore = stateStore,
        repository = repository,
        dispatcher = AppDispatchers.io,
        autoSavedThreadRepository = autoSavedThreadRepository,
        httpClient = httpClient,
        fileSystem = fileSystem,
        maxConcurrency = if (shouldUseLightweightMode) 2 else 4
    )
}

internal suspend fun fetchFutachaUpdateInfo(
    versionChecker: VersionChecker?,
    onFailure: (Throwable) -> Unit = {}
): UpdateInfo? {
    if (versionChecker == null) {
        return null
    }
    return try {
        versionChecker.checkForUpdate()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        null
    }
}

internal fun closeOwnedFutachaRepository(
    repositoryHolder: RepositoryHolder,
    onCloseFailure: (Throwable) -> Unit
) {
    if (!repositoryHolder.ownsRepository) {
        return
    }
    runCatching {
        repositoryHolder.repository.closeAsync().invokeOnCompletion { error ->
            if (error != null && error !is CancellationException) {
                onCloseFailure(error)
            }
        }
    }.onFailure(onCloseFailure)
}

internal fun resolveFutachaBoardRepository(
    board: BoardSummary,
    sharedRepository: BoardRepository
): BoardRepository? {
    return board.takeUnless { it.isMockBoard() }?.let { sharedRepository }
}

internal fun buildFutachaSavedThreadsRepositories(
    fileSystem: FileSystem?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation
): FutachaSavedThreadsRepositories {
    val currentRepository = fileSystem?.let { fs ->
        SavedThreadRepository(
            fs,
            baseDirectory = manualSaveDirectory,
            baseSaveLocation = manualSaveLocation
        )
    }
    val shouldUseLegacyFallback = fileSystem != null &&
        !(manualSaveLocation is SaveLocation.Path && isDefaultManualSaveRoot(manualSaveDirectory))
    val legacyRepository = if (!shouldUseLegacyFallback) {
        null
    } else {
        SavedThreadRepository(
            fileSystem,
            baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
            baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
        )
    }
    return FutachaSavedThreadsRepositories(
        currentRepository = currentRepository,
        legacyRepository = legacyRepository
    )
}

internal suspend fun shouldResetInaccessibleManualSaveBookmark(
    fileSystem: FileSystem?,
    manualSaveLocation: SaveLocation
): Boolean {
    if (manualSaveLocation !is SaveLocation.Bookmark || fileSystem == null) {
        return false
    }
    return !runCatching { fileSystem.exists(manualSaveLocation, "") }.getOrDefault(false)
}

internal fun resolveFutachaManualSaveDirectoryDisplay(
    fileSystem: FileSystem?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation
): String? {
    return when (manualSaveLocation) {
        is SaveLocation.TreeUri -> "SAF: ${manualSaveLocation.uri}"
        is SaveLocation.Bookmark -> "Bookmark: 保存先が選択済みです"
        is SaveLocation.Path -> {
            runCatching { fileSystem?.resolveAbsolutePath(manualSaveDirectory) }.getOrNull()
        }
    }
}

internal fun selectPreferredSavedThreadsRepository(
    currentRepository: SavedThreadRepository?,
    legacyRepository: SavedThreadRepository?,
    currentCount: Int,
    legacyCount: Int
): SavedThreadRepository? {
    if (currentRepository == null) {
        return legacyRepository
    }
    if (legacyRepository == null) {
        return currentRepository
    }
    return when {
        currentCount <= 0 && legacyCount > 0 -> legacyRepository
        legacyCount > currentCount -> legacyRepository
        else -> currentRepository
    }
}

internal suspend fun resolveActiveSavedThreadsRepository(
    currentRepository: SavedThreadRepository?,
    legacyRepository: SavedThreadRepository?
): SavedThreadRepository? {
    if (currentRepository == null) {
        return legacyRepository
    }
    val currentCount = runCatching { currentRepository.getThreadCount() }.getOrDefault(0)
    val legacyCount = legacyRepository?.let {
        runCatching { it.getThreadCount() }.getOrDefault(0)
    } ?: 0
    return selectPreferredSavedThreadsRepository(
        currentRepository = currentRepository,
        legacyRepository = legacyRepository,
        currentCount = currentCount,
        legacyCount = legacyCount
    )
}

internal fun buildFutachaHistoryMutationCallbacks(
    coroutineScope: CoroutineScope,
    dismissHistoryEntry: suspend (ThreadHistoryEntry) -> Unit,
    updateHistoryEntry: suspend (ThreadHistoryEntry) -> Unit,
    clearHistory: suspend () -> Unit
): FutachaHistoryMutationCallbacks {
    return FutachaHistoryMutationCallbacks(
        onDismissHistoryEntry = { entry ->
            coroutineScope.launch { dismissHistoryEntry(entry) }
        },
        onUpdateHistoryEntry = { entry ->
            coroutineScope.launch { updateHistoryEntry(entry) }
        },
        onClearHistory = {
            coroutineScope.launch { clearHistory() }
        }
    )
}

internal fun buildFutachaThreadMutationCallbacks(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore,
    board: BoardSummary,
    historyContext: FutachaThreadHistoryContext
): FutachaThreadMutationCallbacks {
    return FutachaThreadMutationCallbacks(
        onScrollPositionPersist = { threadId, index, offset ->
            coroutineScope.launch {
                persistFutachaThreadScrollPosition(
                    stateStore = stateStore,
                    threadId = threadId,
                    index = index,
                    offset = offset,
                    board = board,
                    context = historyContext
                )
            }
        }
    )
}

internal fun buildFutachaBoardScreenCallbacks(
    coroutineScope: CoroutineScope,
    currentNavigationState: () -> FutachaNavigationState,
    setNavigationState: (FutachaNavigationState) -> Unit,
    updateBoards: suspend ((List<BoardSummary>) -> List<BoardSummary>) -> Unit
): FutachaBoardScreenCallbacks {
    return FutachaBoardScreenCallbacks(
        onBoardSelected = { board ->
            setNavigationState(selectFutachaBoard(currentNavigationState(), board.id))
        },
        onAddBoard = { name, url ->
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val normalizedUrl = normalizeBoardUrl(url)
                updateBoards { boards ->
                    if (boards.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
                        boards
                    } else {
                        boards + createCustomBoardSummary(
                            name = name,
                            url = normalizedUrl,
                            existingBoards = boards
                        )
                    }
                }
            }
        },
        onMenuAction = { action ->
            if (action == BoardManagementMenuAction.SAVED_THREADS) {
                setNavigationState(currentNavigationState().copy(isSavedThreadsVisible = true))
            }
        },
        onBoardDeleted = { board ->
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                updateBoards { boards ->
                    boards.filter { it.id != board.id }
                }
            }
        },
        onBoardsReordered = { reorderedBoards ->
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                updateBoards {
                    reorderedBoards
                }
            }
        }
    )
}

internal fun buildFutachaNavigationCallbacks(
    currentBoards: () -> List<BoardSummary>,
    currentNavigationState: () -> FutachaNavigationState,
    setNavigationState: (FutachaNavigationState) -> Unit
): FutachaNavigationCallbacks {
    return FutachaNavigationCallbacks(
        onHistoryEntrySelected = { entry ->
            resolveHistoryEntrySelection(entry, currentBoards())?.let { selection ->
                setNavigationState(
                    applyFutachaThreadSelection(currentNavigationState(), selection)
                )
            }
        },
        onSavedThreadSelected = { thread ->
            resolveSavedThreadSelection(thread, currentBoards())?.let { selection ->
                setNavigationState(
                    selectCatalogThread(currentNavigationState(), selection)
                )
            }
        },
        onCatalogThreadSelected = { threadId, title, replies, thumbnailUrl, threadUrl ->
            setNavigationState(
                selectCatalogThread(
                    state = currentNavigationState(),
                    threadId = threadId,
                    title = title,
                    replies = replies,
                    thumbnailUrl = thumbnailUrl,
                    threadUrl = threadUrl
                )
            )
        },
        onSavedThreadsDismissed = {
            setNavigationState(dismissSavedThreads(currentNavigationState()))
        },
        onBoardSelectionCleared = {
            setNavigationState(
                clearFutachaThreadSelection(
                    state = currentNavigationState(),
                    clearBoardSelection = true
                )
            )
        },
        onThreadDismissed = {
            setNavigationState(clearFutachaThreadSelection(currentNavigationState()))
        },
        onRegisteredThreadUrlClick = { url ->
            val target = resolveRegisteredThreadNavigation(url, currentBoards())
            if (target == null) {
                false
            } else {
                val navigationState = currentNavigationState()
                if (
                    shouldApplyRegisteredThreadNavigation(
                        currentBoardId = navigationState.selectedBoardId,
                        currentThreadId = navigationState.selectedThreadId,
                        currentThreadUrl = navigationState.selectedThreadUrl,
                        target = target
                    )
                ) {
                    setNavigationState(applyRegisteredThreadNavigation(navigationState, target))
                }
                true
            }
        }
    )
}

internal fun buildFutachaThreadHistoryContext(
    board: BoardSummary,
    navigationState: FutachaNavigationState
): FutachaThreadHistoryContext {
    return FutachaThreadHistoryContext(
        title = navigationState.selectedThreadTitle ?: "無題",
        threadUrl = navigationState.selectedThreadUrl ?: board.url,
        replyCount = navigationState.selectedThreadReplies ?: 0,
        thumbnailUrl = navigationState.selectedThreadThumbnailUrl.orEmpty()
    )
}

internal fun findFutachaVisitedHistoryEntry(
    history: List<ThreadHistoryEntry>,
    threadId: String,
    boardId: String,
    boardUrl: String
): ThreadHistoryEntry? {
    return history.firstOrNull { historyEntry ->
        if (historyEntry.threadId != threadId) {
            false
        } else if (historyEntry.boardId.isNotBlank()) {
            historyEntry.boardId == boardId
        } else {
            historyEntry.boardUrl == boardUrl
        }
    }
}

internal fun shouldSkipFutachaVisitedHistoryUpdate(
    existingEntry: ThreadHistoryEntry?,
    boardId: String,
    currentTimeMillis: Long,
    minimumIntervalMillis: Long = 60_000L
): Boolean {
    return existingEntry != null &&
        existingEntry.boardId == boardId &&
        (currentTimeMillis - existingEntry.lastVisitedEpochMillis) < minimumIntervalMillis
}

internal fun buildFutachaVisitedHistoryEntry(
    threadId: String,
    board: BoardSummary,
    context: FutachaThreadHistoryContext,
    currentTimeMillis: Long,
    existingEntry: ThreadHistoryEntry?
): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = board.id,
        title = context.title,
        titleImageUrl = context.thumbnailUrl,
        boardName = board.name,
        boardUrl = context.threadUrl,
        lastVisitedEpochMillis = currentTimeMillis,
        replyCount = context.replyCount,
        lastReadItemIndex = existingEntry?.lastReadItemIndex ?: 0,
        lastReadItemOffset = existingEntry?.lastReadItemOffset ?: 0
    )
}

internal suspend fun recordFutachaVisitedThread(
    stateStore: AppStateStore,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    board: BoardSummary,
    context: FutachaThreadHistoryContext,
    currentTimeMillis: Long,
    minimumIntervalMillis: Long = 60_000L
): Boolean {
    val existingEntry = findFutachaVisitedHistoryEntry(
        history = history,
        threadId = threadId,
        boardId = board.id,
        boardUrl = context.threadUrl
    )
    if (
        shouldSkipFutachaVisitedHistoryUpdate(
            existingEntry = existingEntry,
            boardId = board.id,
            currentTimeMillis = currentTimeMillis,
            minimumIntervalMillis = minimumIntervalMillis
        )
    ) {
        return false
    }
    stateStore.prependOrReplaceHistoryEntry(
        buildFutachaVisitedHistoryEntry(
            threadId = threadId,
            board = board,
            context = context,
            currentTimeMillis = currentTimeMillis,
            existingEntry = existingEntry
        )
    )
    return true
}

internal suspend fun persistFutachaThreadScrollPosition(
    stateStore: AppStateStore,
    threadId: String,
    index: Int,
    offset: Int,
    board: BoardSummary,
    context: FutachaThreadHistoryContext
) {
    stateStore.updateHistoryScrollPosition(
        threadId = threadId,
        index = index,
        offset = offset,
        boardId = board.id,
        title = context.title,
        titleImageUrl = context.thumbnailUrl,
        boardName = board.name,
        boardUrl = context.threadUrl,
        replyCount = context.replyCount
    )
}

internal fun resolveHistoryEntrySelection(
    entry: ThreadHistoryEntry,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val entryBoardUrlKey = entry.boardUrl
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
    val targetBoard = boards.firstOrNull { entry.boardId.isNotBlank() && it.id == entry.boardId }
        ?: boards.firstOrNull {
            it.url.trim().substringBefore('?').trimEnd('/').lowercase() == entryBoardUrlKey
        }
        ?: boards.firstOrNull { it.name == entry.boardName }

    return targetBoard?.let { board ->
        FutachaThreadSelection(
            boardId = board.id,
            threadId = entry.threadId,
            threadTitle = entry.title,
            threadReplies = entry.replyCount,
            threadThumbnailUrl = entry.titleImageUrl,
            threadUrl = entry.boardUrl.takeIf { url ->
                Regex("""/res/\d+\.html?""", RegexOption.IGNORE_CASE).containsMatchIn(url)
            }
        )
    }
}

internal fun resolveSavedThreadSelection(
    thread: SavedThread,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val targetBoard = boards.firstOrNull { it.id == thread.boardId }
        ?: boards.firstOrNull { it.name == thread.boardName }

    return targetBoard?.let { board ->
        FutachaThreadSelection(
            boardId = board.id,
            threadId = thread.threadId,
            threadTitle = thread.title,
            threadReplies = thread.postCount,
            threadThumbnailUrl = null,
            threadUrl = null,
            isSavedThreadsVisible = false
        )
    }
}

internal fun shouldApplyRegisteredThreadNavigation(
    currentBoardId: String?,
    currentThreadId: String?,
    currentThreadUrl: String?,
    target: RegisteredThreadNavigation
): Boolean {
    return !(currentBoardId == target.board.id &&
        currentThreadId == target.threadId &&
        currentThreadUrl == target.threadUrl)
}

internal fun isSelectedBoardStillMissing(
    selectedBoardId: String?,
    missingBoardId: String,
    boards: List<BoardSummary>
): Boolean {
    return selectedBoardId == missingBoardId && boards.none { it.id == missingBoardId }
}

internal fun resolveMissingBoardRecoveryState(
    state: FutachaNavigationState,
    missingBoardId: String,
    boards: List<BoardSummary>
): FutachaNavigationState? {
    if (!isSelectedBoardStillMissing(state.selectedBoardId, missingBoardId, boards)) {
        return null
    }
    return clearFutachaThreadSelection(
        state = state,
        clearBoardSelection = true
    )
}

internal fun resolveHistoryEntryBoardId(entry: ThreadHistoryEntry): String? {
    val resolvedBoardId = entry.boardId
        .ifBlank { runCatching { BoardUrlResolver.resolveBoardSlug(entry.boardUrl) }.getOrDefault("") }
        .ifBlank { "" }
    return resolvedBoardId.ifBlank { null }
}

internal suspend fun dismissHistoryEntry(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    entry: ThreadHistoryEntry,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    val resolvedBoardId = resolveHistoryEntryBoardId(entry)
    stateStore.removeSelfPostIdentifiersForThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )
    stateStore.removeHistoryEntry(entry)
    autoSavedThreadRepository?.deleteThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )?.onFailure(onAutoSavedThreadDeleteFailure)
}

internal suspend fun clearHistory(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    onSkippedThreadsCleared: () -> Unit,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    stateStore.clearSelfPostIdentifiers()
    stateStore.setHistory(emptyList())
    onSkippedThreadsCleared()
    autoSavedThreadRepository?.deleteAllThreads()?.onFailure(onAutoSavedThreadDeleteFailure)
}

internal fun isDefaultManualSaveRoot(directory: String): Boolean {
    val normalized = directory.trim().removePrefix("./").trimEnd('/')
    return normalized.equals(DEFAULT_MANUAL_SAVE_ROOT, ignoreCase = true)
}

internal fun BoardSummary.isMockBoard(): Boolean {
    return url.contains("example.com", ignoreCase = true)
}

internal fun normalizeBoardUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            Logger.w(
                FUTACHA_APP_SUPPORT_LOG_TAG,
                "HTTP URL detected. Connection may fail if cleartext traffic is disabled: $trimmed"
            )
            trimmed
        }
        else -> "https://$trimmed"
    }

    if (withScheme.contains("futaba.php", ignoreCase = true)) {
        return withScheme
    }

    return runCatching {
        val parsed = Url(withScheme)
        val normalizedPath = when {
            parsed.encodedPath.isBlank() || parsed.encodedPath == "/" -> "/futaba.php"
            parsed.encodedPath.endsWith("/") -> "${parsed.encodedPath}futaba.php"
            else -> "${parsed.encodedPath}/futaba.php"
        }
        URLBuilder(parsed).apply { encodedPath = normalizedPath }.buildString()
    }.getOrElse {
        val fragment = withScheme.substringAfter('#', missingDelimiterValue = "")
        val withoutFragment = withScheme.substringBefore('#')
        val base = withoutFragment.substringBefore('?').trimEnd('/')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        buildString {
            append(base)
            append("/futaba.php")
            if (query.isNotEmpty()) {
                append('?')
                append(query)
            }
            if (fragment.isNotEmpty()) {
                append('#')
                append(fragment)
            }
        }
    }
}
