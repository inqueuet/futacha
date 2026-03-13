package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class ThreadGlobalSettingsCallbacks(
    val onBack: () -> Unit,
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val onLightweightModeChanged: (Boolean) -> Unit,
    val onManualSaveDirectoryChanged: (String) -> Unit,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onOpenSaveDirectoryPicker: (() -> Unit)?,
    val onOpenCookieManager: (() -> Unit)?,
    val onFileManagerSelected: ((packageName: String, label: String) -> Unit)?,
    val onClearPreferredFileManager: (() -> Unit)?,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
)

internal fun buildThreadGlobalSettingsCallbacks(
    onBack: () -> Unit,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onLightweightModeChanged: (Boolean) -> Unit,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    onOpenCookieManager: (() -> Unit)?,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)?,
    onClearPreferredFileManager: (() -> Unit)?,
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
): ThreadGlobalSettingsCallbacks {
    return ThreadGlobalSettingsCallbacks(
        onBack = onBack,
        onBackgroundRefreshChanged = onBackgroundRefreshChanged,
        onLightweightModeChanged = onLightweightModeChanged,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        onOpenCookieManager = onOpenCookieManager,
        onFileManagerSelected = onFileManagerSelected,
        onClearPreferredFileManager = onClearPreferredFileManager,
        onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
        onCatalogNavEntriesChanged = onCatalogNavEntriesChanged
    )
}

internal data class ThreadCookieManagementCallbacks(
    val onBack: () -> Unit
)

internal fun buildThreadCookieManagementCallbacks(
    onBack: () -> Unit
): ThreadCookieManagementCallbacks {
    return ThreadCookieManagementCallbacks(onBack = onBack)
}
