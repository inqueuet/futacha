package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class ThreadGlobalSettingsCallbacks(
    val onBack: () -> Unit,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val onOpenCookieManager: (() -> Unit)?,
) {
    val onBackgroundRefreshChanged: (Boolean) -> Unit
        get() = preferencesCallbacks.onBackgroundRefreshChanged
    val onAdsEnabledChanged: (Boolean) -> Unit
        get() = preferencesCallbacks.onAdsEnabledChanged
    val onLightweightModeChanged: (Boolean) -> Unit
        get() = preferencesCallbacks.onLightweightModeChanged
    val onManualSaveDirectoryChanged: (String) -> Unit
        get() = preferencesCallbacks.onManualSaveDirectoryChanged
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit
        get() = preferencesCallbacks.onSaveDirectorySelectionChanged
    val onOpenSaveDirectoryPicker: (() -> Unit)?
        get() = preferencesCallbacks.onOpenSaveDirectoryPicker
    val onFileManagerSelected: ((packageName: String, label: String) -> Unit)?
        get() = preferencesCallbacks.onFileManagerSelected
    val onClearPreferredFileManager: (() -> Unit)?
        get() = preferencesCallbacks.onClearPreferredFileManager
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit
        get() = preferencesCallbacks.onThreadMenuEntriesChanged
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
        get() = preferencesCallbacks.onCatalogNavEntriesChanged
}

internal fun buildThreadGlobalSettingsCallbacks(
    onBack: () -> Unit,
    onOpenCookieManager: (() -> Unit)?,
    preferencesCallbacks: ScreenPreferencesCallbacks
): ThreadGlobalSettingsCallbacks {
    return ThreadGlobalSettingsCallbacks(
        onBack = onBack,
        preferencesCallbacks = preferencesCallbacks,
        onOpenCookieManager = onOpenCookieManager,
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
