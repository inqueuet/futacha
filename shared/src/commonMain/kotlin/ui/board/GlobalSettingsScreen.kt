package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

internal enum class GlobalSettingsAction {
    Cookies,
    Email,
    X,
    Developer,
    PrivacyPolicy
}

@Composable
internal fun GlobalSettingsScreen(
    onBack: () -> Unit,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    onOpenCookieManager: (() -> Unit)? = null,
    historyEntries: List<ThreadHistoryEntry>,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null
) {
    val runtime = rememberGlobalSettingsScreenRuntime(
        onBack = onBack,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        onOpenCookieManager = onOpenCookieManager,
        historyEntries = historyEntries,
        fileSystem = fileSystem,
        autoSavedThreadRepository = autoSavedThreadRepository
    )

    PlatformBackHandler(onBack = onBack)
    GlobalSettingsScaffold(bindings = runtime.scaffoldBindings)
    GlobalSettingsFileManagerPickerHost(
        isVisible = runtime.isFileManagerPickerVisible,
        onDismiss = runtime.onDismissFileManagerPicker,
        onFileManagerSelected = runtime.onFileManagerSelected
    )
}

/**
 * Platform-specific file manager picker dialog
 */
@Composable
expect fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
)
