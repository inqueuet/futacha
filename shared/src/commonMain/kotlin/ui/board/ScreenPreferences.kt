package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.DEFAULT_CATALOG_FETCH_ROWS
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadGalleryThumbnailMode
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.ai.AiAvailability

data class ScreenPreferencesState(
    val appVersion: String,
    val isUpdateCheckEnabled: Boolean = true,
    val isBackgroundRefreshEnabled: Boolean = false,
    val isWatchAlertEnabled: Boolean = false,
    val isLightweightModeEnabled: Boolean = false,
    val isThreadSummaryModeEnabled: Boolean = false,
    val isAiPostFilterEnabled: Boolean = false,
    val isAiCommandEnabled: Boolean = false,
    val isTelemetryCollectionEnabled: Boolean = true,
    val isAppLockEnabled: Boolean = false,
    val aiAvailability: AiAvailability = AiAvailability(
        isAvailable = false,
        unavailableReason = "端末AIを確認中です。"
    ),
    val manualSaveDirectory: String = DEFAULT_MANUAL_SAVE_ROOT,
    val manualSaveLocation: SaveLocation? = null,
    val resolvedManualSaveDirectory: String? = null,
    val attachmentPickerPreference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    val saveDirectorySelection: SaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
    val threadGalleryTapAction: ThreadGalleryTapAction = ThreadGalleryTapAction.OpenMedia,
    val threadGalleryThumbnailMode: ThreadGalleryThumbnailMode = ThreadGalleryThumbnailMode.CropSquare,
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.FutabaClassic,
    val appIconVariant: AppIconVariant = AppIconVariant.Current,
    val threadDisplayMode: ThreadDisplayMode = ThreadDisplayMode.Flat,
    val threadBodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    val threadPostImageSize: ThreadPostImageSize = ThreadPostImageSize.Small,
    val isCompactThreadHeaderEnabled: Boolean = false,
    val catalogFetchRows: Int = DEFAULT_CATALOG_FETCH_ROWS,
    val preferredFileManagerPackage: String? = null,
    val preferredFileManagerLabel: String? = null,
    val threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    val catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries()
)

data class ScreenPreferencesCallbacks(
    val onUpdateCheckChanged: (Boolean) -> Unit = {},
    val onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    val onWatchAlertChanged: (Boolean) -> Unit = {},
    val onLightweightModeChanged: (Boolean) -> Unit = {},
    val onThreadSummaryModeChanged: (Boolean) -> Unit = {},
    val onAiPostFilterChanged: (Boolean) -> Unit = {},
    val onAiCommandChanged: (Boolean) -> Unit = {},
    val onTelemetryCollectionChanged: (Boolean) -> Unit = {},
    val onAppLockPasswordChanged: (String) -> Unit = {},
    val onAppLockCleared: () -> Unit = {},
    val onManualSaveDirectoryChanged: (String) -> Unit = {},
    val onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit = {},
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit = {},
    val onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit = {},
    val onThreadGalleryThumbnailModeChanged: (ThreadGalleryThumbnailMode) -> Unit = {},
    val onThemeModeChanged: (ThemeMode) -> Unit = {},
    val onThemePaletteChanged: (ThemePalette) -> Unit = {},
    val onAppIconVariantChanged: (AppIconVariant) -> Unit = {},
    val onThreadDisplayModeChanged: (ThreadDisplayMode) -> Unit = {},
    val onThreadBodyTextSizeChanged: (ThreadBodyTextSize) -> Unit = {},
    val onThreadPostImageSizeChanged: (ThreadPostImageSize) -> Unit = {},
    val onCompactThreadHeaderChanged: (Boolean) -> Unit = {},
    val onCatalogFetchRowsChanged: (Int) -> Unit = {},
    val onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    val onFileManagerSelected: ((packageName: String, label: String) -> Unit)? = null,
    val onClearPreferredFileManager: (() -> Unit)? = null,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {}
)
