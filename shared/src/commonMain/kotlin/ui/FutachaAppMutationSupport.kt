package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal data class FutachaPreferenceMutationInputs(
    val setBackgroundRefreshEnabled: suspend (Boolean) -> Unit,
    val setAdsEnabled: suspend (Boolean) -> Unit = {},
    val setLightweightModeEnabled: suspend (Boolean) -> Unit,
    val setManualSaveDirectory: suspend (String) -> Unit,
    val setAttachmentPickerPreference: suspend (AttachmentPickerPreference) -> Unit,
    val setSaveDirectorySelection: suspend (SaveDirectorySelection) -> Unit,
    val setManualSaveLocation: suspend (SaveLocation) -> Unit,
    val setPreferredFileManager: suspend (String?, String?) -> Unit,
    val setThreadMenuEntries: suspend (List<ThreadMenuEntryConfig>) -> Unit,
    val setCatalogNavEntries: suspend (List<CatalogNavEntryConfig>) -> Unit
)

internal data class FutachaPreferenceMutationCallbacks(
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val onAdsEnabledChanged: (Boolean) -> Unit,
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

internal fun launchFutachaCallbackMutation(
    coroutineScope: CoroutineScope,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend () -> Unit
) {
    coroutineScope.launch(start = start) { block() }
}

internal fun buildFutachaPreferenceMutationCallbacks(
    coroutineScope: CoroutineScope,
    inputs: FutachaPreferenceMutationInputs
): FutachaPreferenceMutationCallbacks {
    return FutachaPreferenceMutationCallbacks(
        onBackgroundRefreshChanged = { enabled ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setBackgroundRefreshEnabled(enabled)
            }
        },
        onAdsEnabledChanged = { enabled ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAdsEnabled(enabled)
            }
        },
        onLightweightModeChanged = { enabled ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setLightweightModeEnabled(enabled)
            }
        },
        onManualSaveDirectoryChanged = { directory ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setManualSaveDirectory(directory)
            }
        },
        onAttachmentPickerPreferenceChanged = { preference ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAttachmentPickerPreference(preference)
            }
        },
        onSaveDirectorySelectionChanged = { selection ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setSaveDirectorySelection(selection)
            }
        },
        onManualSaveLocationChanged = { location ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setManualSaveLocation(location)
            }
        },
        onFileManagerSelected = { packageName, label ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setPreferredFileManager(packageName, label)
            }
        },
        onClearPreferredFileManager = {
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setPreferredFileManager(null, null)
            }
        },
        onThreadMenuEntriesChanged = { entries ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadMenuEntries(entries)
            }
        },
        onCatalogNavEntriesChanged = { entries ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setCatalogNavEntries(entries)
            }
        }
    )
}
