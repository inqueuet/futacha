package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.isAndroid
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope

private const val GLOBAL_SETTINGS_AUTO_SAVE_STATS_FETCH_TIMEOUT_MS = 4_000L

internal data class GlobalSettingsMutableStateBundle(
    val isFileManagerPickerVisible: MutableState<Boolean>,
    val autoSavedCount: MutableState<Int?>,
    val autoSavedSize: MutableState<Long?>,
    val manualSaveInput: MutableState<String>,
    val localThreadMenuEntries: MutableState<List<ThreadMenuEntryConfig>>,
    val localCatalogNavEntries: MutableState<List<CatalogNavEntryConfig>>
)

@Composable
internal fun rememberGlobalSettingsMutableStateBundle(
    manualSaveDirectory: String,
    threadMenuEntries: List<ThreadMenuEntryConfig>,
    catalogNavEntries: List<CatalogNavEntryConfig>
): GlobalSettingsMutableStateBundle {
    return GlobalSettingsMutableStateBundle(
        isFileManagerPickerVisible = rememberSaveable { mutableStateOf(false) },
        autoSavedCount = remember { mutableStateOf(null) },
        autoSavedSize = remember { mutableStateOf(null) },
        manualSaveInput = rememberSaveable(manualSaveDirectory) {
            mutableStateOf(manualSaveDirectory)
        },
        localThreadMenuEntries = remember(threadMenuEntries) {
            mutableStateOf(resolveThreadMenuConfigState(threadMenuEntries).allEntries)
        },
        localCatalogNavEntries = remember(catalogNavEntries) {
            mutableStateOf(resolveCatalogMenuConfigState(catalogNavEntries).allEntries)
        }
    )
}

internal data class GlobalSettingsRuntimeBundle(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val isAndroidPlatform: Boolean,
    val availableSaveDirectorySelections: List<com.valoser.futacha.shared.util.SaveDirectorySelection>
)

@Composable
internal fun rememberGlobalSettingsRuntimeBundle(): GlobalSettingsRuntimeBundle {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val isAndroidPlatform = remember { isAndroid() }
    val availableSaveDirectorySelections = remember {
        com.valoser.futacha.shared.util.SaveDirectorySelection.entries.toList()
    }
    return GlobalSettingsRuntimeBundle(
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        isAndroidPlatform = isAndroidPlatform,
        availableSaveDirectorySelections = availableSaveDirectorySelections
    )
}

@Composable
internal fun rememberGlobalSettingsAutoSavedRepository(
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?
): SavedThreadRepository? {
    return remember(fileSystem, autoSavedThreadRepository) {
        autoSavedThreadRepository ?: fileSystem?.let {
            SavedThreadRepository(it, baseDirectory = AUTO_SAVE_DIRECTORY)
        }
    }
}

internal data class GlobalSettingsAutoSavedStatsUpdate(
    val autoSavedCount: Int?,
    val autoSavedSize: Long?,
    val shouldApply: Boolean
)

internal fun resolveGlobalSettingsAutoSavedStatsUpdate(
    hasRepository: Boolean,
    stats: SavedThreadRepository.SavedThreadStats?
): GlobalSettingsAutoSavedStatsUpdate {
    if (!hasRepository) {
        return GlobalSettingsAutoSavedStatsUpdate(
            autoSavedCount = null,
            autoSavedSize = null,
            shouldApply = true
        )
    }
    return if (stats != null) {
        GlobalSettingsAutoSavedStatsUpdate(
            autoSavedCount = stats.threadCount,
            autoSavedSize = stats.totalSize,
            shouldApply = true
        )
    } else {
        GlobalSettingsAutoSavedStatsUpdate(
            autoSavedCount = null,
            autoSavedSize = null,
            shouldApply = false
        )
    }
}

internal suspend fun loadGlobalSettingsAutoSavedStatsUpdate(
    repository: SavedThreadRepository?
): GlobalSettingsAutoSavedStatsUpdate {
    if (repository == null) {
        return resolveGlobalSettingsAutoSavedStatsUpdate(
            hasRepository = false,
            stats = null
        )
    }
    val stats = runCatching {
        withTimeoutOrNull(GLOBAL_SETTINGS_AUTO_SAVE_STATS_FETCH_TIMEOUT_MS) {
            withContext(AppDispatchers.io) {
                repository.getStats()
            }
        }
    }.getOrNull()
    return resolveGlobalSettingsAutoSavedStatsUpdate(
        hasRepository = true,
        stats = stats
    )
}
