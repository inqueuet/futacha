package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.state.AppStateStore

internal data class CatalogScreenInitialMutableStateInputs(
    val catalogMode: com.valoser.futacha.shared.model.CatalogMode,
    val archiveSearchScope: ArchiveSearchScope?,
    val displayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    val gridColumns: Int
)

internal fun resolveCatalogScreenInitialMutableStateInputs(
    boardId: String?,
    persistedCatalogModes: Map<String, com.valoser.futacha.shared.model.CatalogMode>,
    initialArchiveSearchScope: ArchiveSearchScope?,
    displayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    gridColumns: Int
): CatalogScreenInitialMutableStateInputs {
    return CatalogScreenInitialMutableStateInputs(
        catalogMode = resolveInitialCatalogMode(boardId, persistedCatalogModes),
        archiveSearchScope = initialArchiveSearchScope,
        displayStyle = displayStyle,
        gridColumns = gridColumns
    )
}

internal data class CatalogScreenCoreBindingsBundle(
    val runtimeObjects: CatalogScreenRuntimeObjectsBundle,
    val persistentBindings: CatalogScreenPersistentBindings,
    val mutableStateBundle: CatalogScreenMutableStateBundle
)

internal data class CatalogScreenDisplaySettings(
    val displayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    val gridColumns: Int
)

internal fun resolveCatalogScreenDisplaySettings(
    hasStateStore: Boolean,
    persistentDisplayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    localDisplayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    persistentGridColumns: Int,
    localGridColumns: Int
): CatalogScreenDisplaySettings {
    return if (hasStateStore) {
        CatalogScreenDisplaySettings(
            displayStyle = persistentDisplayStyle,
            gridColumns = persistentGridColumns
        )
    } else {
        CatalogScreenDisplaySettings(
            displayStyle = localDisplayStyle,
            gridColumns = localGridColumns
        )
    }
}

@Composable
internal fun rememberCatalogScreenCoreBindingsBundle(
    stateStore: AppStateStore?,
    boardId: String?,
    boardUrl: String?,
    initialArchiveSearchScope: ArchiveSearchScope?
): CatalogScreenCoreBindingsBundle {
    val runtimeObjects = rememberCatalogScreenRuntimeObjectsBundle()
    val saveableKey = remember(boardId, boardUrl) {
        resolveCatalogScreenSaveableKey(boardId, boardUrl)
    }
    val persistentBindings = rememberCatalogScreenPersistentBindings(
        stateStore = stateStore,
        coroutineScope = runtimeObjects.coroutineScope,
        saveableKey = saveableKey
    )
    val initialStateInputs = resolveCatalogScreenInitialMutableStateInputs(
        boardId = boardId,
        persistedCatalogModes = persistentBindings.persistedCatalogModes,
        initialArchiveSearchScope = initialArchiveSearchScope,
        displayStyle = persistentBindings.persistentDisplayStyle,
        gridColumns = persistentBindings.persistentGridColumns
    )
    val mutableStateBundle = rememberCatalogScreenMutableStateBundle(
        saveableKey = saveableKey,
        initialCatalogMode = initialStateInputs.catalogMode,
        initialArchiveSearchScope = initialStateInputs.archiveSearchScope,
        initialDisplayStyle = initialStateInputs.displayStyle,
        initialGridColumns = initialStateInputs.gridColumns
    )
    return CatalogScreenCoreBindingsBundle(
        runtimeObjects = runtimeObjects,
        persistentBindings = persistentBindings,
        mutableStateBundle = mutableStateBundle
    )
}
