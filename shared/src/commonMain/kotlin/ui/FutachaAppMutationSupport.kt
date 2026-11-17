package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsEnabledValue
import com.valoser.futacha.shared.analytics.analyticsPresentValue
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadGalleryThumbnailMode
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.ui.board.ALPHA_AI_COMMAND_ENABLED
import com.valoser.futacha.shared.ui.board.ALPHA_AI_POST_FILTER_ENABLED
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal data class FutachaPreferenceMutationInputs(
    val setUpdateCheckEnabled: suspend (Boolean) -> Unit = {},
    val setBackgroundRefreshEnabled: suspend (Boolean) -> Unit,
    val setWatchAlertEnabled: suspend (Boolean) -> Unit = {},
    val setLightweightModeEnabled: suspend (Boolean) -> Unit,
    val setThreadSummaryModeEnabled: suspend (Boolean) -> Unit = {},
    val setAiPostFilterEnabled: suspend (Boolean) -> Unit = {},
    val setAiCommandEnabled: suspend (Boolean) -> Unit = {},
    val setTelemetryCollectionEnabled: suspend (Boolean) -> Unit = {},
    val setAppLockPassword: suspend (String) -> Unit = {},
    val clearAppLockPassword: suspend () -> Unit = {},
    val setManualSaveDirectory: suspend (String) -> Unit,
    val setAttachmentPickerPreference: suspend (AttachmentPickerPreference) -> Unit,
    val setSaveDirectorySelection: suspend (SaveDirectorySelection) -> Unit,
    val setThreadGalleryTapAction: suspend (ThreadGalleryTapAction) -> Unit = {},
    val setThreadGalleryThumbnailMode: suspend (ThreadGalleryThumbnailMode) -> Unit = {},
    val setThemeMode: suspend (ThemeMode) -> Unit = {},
    val setThemePalette: suspend (ThemePalette) -> Unit = {},
    val setAppIconVariant: suspend (AppIconVariant) -> Unit = {},
    val setThreadDisplayMode: suspend (ThreadDisplayMode) -> Unit = {},
    val setThreadBodyTextSize: suspend (ThreadBodyTextSize) -> Unit = {},
    val setThreadPostImageSize: suspend (ThreadPostImageSize) -> Unit = {},
    val setCompactThreadHeaderEnabled: suspend (Boolean) -> Unit = {},
    val setCatalogFetchRows: suspend (Int) -> Unit = {},
    val setManualSaveLocation: suspend (SaveLocation) -> Unit,
    val setPreferredFileManager: suspend (String?, String?) -> Unit,
    val setThreadMenuEntries: suspend (List<ThreadMenuEntryConfig>) -> Unit,
    val setCatalogNavEntries: suspend (List<CatalogNavEntryConfig>) -> Unit
)

internal data class FutachaPreferenceMutationCallbacks(
    val onUpdateCheckChanged: (Boolean) -> Unit,
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val onWatchAlertChanged: (Boolean) -> Unit,
    val onLightweightModeChanged: (Boolean) -> Unit,
    val onThreadSummaryModeChanged: (Boolean) -> Unit,
    val onAiPostFilterChanged: (Boolean) -> Unit,
    val onAiCommandChanged: (Boolean) -> Unit,
    val onTelemetryCollectionChanged: (Boolean) -> Unit,
    val onAppLockPasswordChanged: (String) -> Unit,
    val onAppLockCleared: () -> Unit,
    val onManualSaveDirectoryChanged: (String) -> Unit,
    val onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit,
    val onThreadGalleryThumbnailModeChanged: (ThreadGalleryThumbnailMode) -> Unit,
    val onThemeModeChanged: (ThemeMode) -> Unit,
    val onThemePaletteChanged: (ThemePalette) -> Unit,
    val onAppIconVariantChanged: (AppIconVariant) -> Unit,
    val onThreadDisplayModeChanged: (ThreadDisplayMode) -> Unit,
    val onThreadBodyTextSizeChanged: (ThreadBodyTextSize) -> Unit,
    val onThreadPostImageSizeChanged: (ThreadPostImageSize) -> Unit,
    val onCompactThreadHeaderChanged: (Boolean) -> Unit,
    val onCatalogFetchRowsChanged: (Int) -> Unit,
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
        onUpdateCheckChanged = { enabled ->
            recordPreferenceChanged("update_check", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setUpdateCheckEnabled(enabled)
            }
        },
        onBackgroundRefreshChanged = { enabled ->
            recordPreferenceChanged("background_refresh", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setBackgroundRefreshEnabled(enabled)
            }
        },
        onWatchAlertChanged = { enabled ->
            recordPreferenceChanged("watch_alert", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setWatchAlertEnabled(enabled)
            }
        },
        onLightweightModeChanged = { enabled ->
            recordPreferenceChanged("lightweight_mode", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setLightweightModeEnabled(enabled)
            }
        },
        onThreadSummaryModeChanged = { enabled ->
            recordPreferenceChanged("thread_summary", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadSummaryModeEnabled(enabled)
            }
        },
        onAiPostFilterChanged = { enabled ->
            recordPreferenceChanged("ai_post_filter", analyticsEnabledValue(enabled && ALPHA_AI_POST_FILTER_ENABLED))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAiPostFilterEnabled(enabled && ALPHA_AI_POST_FILTER_ENABLED)
            }
        },
        onAiCommandChanged = { enabled ->
            recordPreferenceChanged("ai_command", analyticsEnabledValue(enabled && ALPHA_AI_COMMAND_ENABLED))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAiCommandEnabled(enabled && ALPHA_AI_COMMAND_ENABLED)
            }
        },
        onTelemetryCollectionChanged = { enabled ->
            recordPreferenceChanged("telemetry_collection", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setTelemetryCollectionEnabled(enabled)
            }
        },
        onAppLockPasswordChanged = { password ->
            recordPreferenceChanged("app_lock", analyticsPresentValue(password.takeIf { it.isNotBlank() }))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAppLockPassword(password)
            }
        },
        onAppLockCleared = {
            recordPreferenceChanged("app_lock", "cleared")
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.clearAppLockPassword()
            }
        },
        onManualSaveDirectoryChanged = { directory ->
            recordPreferenceChanged("manual_save_directory", analyticsPresentValue(directory.takeIf { it.isNotBlank() }))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setManualSaveDirectory(directory)
            }
        },
        onAttachmentPickerPreferenceChanged = { preference ->
            recordPreferenceChanged("attachment_picker", preference.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAttachmentPickerPreference(preference)
            }
        },
        onSaveDirectorySelectionChanged = { selection ->
            recordPreferenceChanged("save_directory_selection", selection.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setSaveDirectorySelection(selection)
            }
        },
        onThreadGalleryTapActionChanged = { action ->
            recordPreferenceChanged("thread_gallery_tap", action.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadGalleryTapAction(action)
            }
        },
        onThreadGalleryThumbnailModeChanged = { mode ->
            recordPreferenceChanged("thread_gallery_thumbnail", mode.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadGalleryThumbnailMode(mode)
            }
        },
        onThemeModeChanged = { mode ->
            recordPreferenceChanged("theme_mode", mode.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThemeMode(mode)
            }
        },
        onThemePaletteChanged = { palette ->
            recordPreferenceChanged("theme_palette", palette.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThemePalette(palette)
            }
        },
        onAppIconVariantChanged = { variant ->
            recordPreferenceChanged("app_icon", variant.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAppIconVariant(variant)
            }
        },
        onThreadDisplayModeChanged = { mode ->
            recordPreferenceChanged("thread_display_mode", mode.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadDisplayMode(mode)
            }
        },
        onThreadBodyTextSizeChanged = { size ->
            recordPreferenceChanged("thread_body_text_size", size.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadBodyTextSize(size)
            }
        },
        onThreadPostImageSizeChanged = { size ->
            recordPreferenceChanged("thread_post_image_size", size.name.lowercase())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadPostImageSize(size)
            }
        },
        onCompactThreadHeaderChanged = { enabled ->
            recordPreferenceChanged("compact_thread_header", analyticsEnabledValue(enabled))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setCompactThreadHeaderEnabled(enabled)
            }
        },
        onCatalogFetchRowsChanged = { rows ->
            recordPreferenceChanged("catalog_fetch_rows", rows.toString())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setCatalogFetchRows(rows)
            }
        },
        onManualSaveLocationChanged = { location ->
            recordPreferenceChanged("manual_save_location", location.analyticsKind())
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setManualSaveLocation(location)
            }
        },
        onFileManagerSelected = { packageName, label ->
            recordPreferenceChanged("preferred_file_manager", analyticsPresentValue(packageName.takeIf { it.isNotBlank() }))
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setPreferredFileManager(packageName, label)
            }
        },
        onClearPreferredFileManager = {
            recordPreferenceChanged("preferred_file_manager", "cleared")
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setPreferredFileManager(null, null)
            }
        },
        onThreadMenuEntriesChanged = { entries ->
            AnalyticsTracker.event(
                "menu_config_changed",
                mapOf(
                    "menu" to "thread",
                    "entry_count_bucket" to analyticsCountBucket(entries.size)
                )
            )
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadMenuEntries(entries)
            }
        },
        onCatalogNavEntriesChanged = { entries ->
            AnalyticsTracker.event(
                "menu_config_changed",
                mapOf(
                    "menu" to "catalog",
                    "entry_count_bucket" to analyticsCountBucket(entries.size)
                )
            )
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setCatalogNavEntries(entries)
            }
        }
    )
}

private fun recordPreferenceChanged(name: String, value: String) {
    AnalyticsTracker.event(
        "preference_changed",
        mapOf(
            "preference" to name,
            "value" to value
        )
    )
}

private fun SaveLocation.analyticsKind(): String {
    return when (this) {
        is SaveLocation.Path -> "path"
        is SaveLocation.TreeUri -> "tree_uri"
        is SaveLocation.Bookmark -> "bookmark"
    }
}
