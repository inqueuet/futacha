package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem

internal data class ThreadScreenCoreSetupBundle(
    val environmentBundle: ThreadScreenEnvironmentBundle,
    val runtimeObjectBundle: ThreadScreenRuntimeObjectBundle,
    val platformRuntimeBindings: ThreadScreenPlatformRuntimeBindings,
    val persistentBindings: ThreadScreenPersistentBindings,
    val isPrivacyFilterEnabled: Boolean
)

@Composable
internal fun rememberThreadScreenCoreSetupBundle(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    repository: BoardRepository?,
    fileSystem: FileSystem?,
    stateStore: AppStateStore?,
    autoSavedThreadRepository: SavedThreadRepository?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedThreadUrlOverride: String?,
    onRegisteredThreadUrlClick: (String) -> Boolean
): ThreadScreenCoreSetupBundle {
    val environmentBundle = remember(
        repository,
        autoSavedThreadRepository,
        fileSystem,
        manualSaveDirectory,
        manualSaveLocation,
        history,
        threadId,
        board,
        resolvedThreadUrlOverride
    ) {
        buildThreadScreenEnvironmentBundle(
            repository = repository,
            autoSavedThreadRepository = autoSavedThreadRepository,
            fileSystem = fileSystem,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            history = history,
            threadId = threadId,
            board = board,
            resolvedThreadUrlOverride = resolvedThreadUrlOverride
        )
    }
    val runtimeObjectBundle = rememberThreadScreenRuntimeObjectBundle(
        threadId = threadId,
        initialHistoryEntry = environmentBundle.initialHistoryEntry
    )
    val platformRuntimeBindings = rememberThreadScreenPlatformRuntimeBindings(
        platformContext = coil3.compose.LocalPlatformContext.current,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick
    )
    val persistentBindings = rememberThreadScreenPersistentBindings(
        stateStore = stateStore,
        coroutineScope = runtimeObjectBundle.coroutineScope,
        boardId = board.id,
        threadId = threadId
    )
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    return remember(
        environmentBundle,
        runtimeObjectBundle,
        platformRuntimeBindings,
        persistentBindings,
        isPrivacyFilterEnabled
    ) {
        ThreadScreenCoreSetupBundle(
            environmentBundle = environmentBundle,
            runtimeObjectBundle = runtimeObjectBundle,
            platformRuntimeBindings = platformRuntimeBindings,
            persistentBindings = persistentBindings,
            isPrivacyFilterEnabled = isPrivacyFilterEnabled
        )
    }
}
