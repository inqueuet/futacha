package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection

data class ScreenPreferencesState(
    val appVersion: String,
    val isBackgroundRefreshEnabled: Boolean = false,
    val isAdsEnabled: Boolean = false,
    val isLightweightModeEnabled: Boolean = false,
    val manualSaveDirectory: String = DEFAULT_MANUAL_SAVE_ROOT,
    val manualSaveLocation: SaveLocation? = null,
    val resolvedManualSaveDirectory: String? = null,
    val attachmentPickerPreference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    val saveDirectorySelection: SaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
    val preferredFileManagerPackage: String? = null,
    val preferredFileManagerLabel: String? = null,
    val threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    val catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries()
)

data class ScreenPreferencesCallbacks(
    val onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    val onAdsEnabledChanged: (Boolean) -> Unit = {},
    val onLightweightModeChanged: (Boolean) -> Unit = {},
    val onManualSaveDirectoryChanged: (String) -> Unit = {},
    val onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit = {},
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit = {},
    val onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    val onFileManagerSelected: ((packageName: String, label: String) -> Unit)? = null,
    val onClearPreferredFileManager: (() -> Unit)? = null,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {}
)
