package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

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

internal data class FutachaBoardScreenCallbacks(
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit
)


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
