package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
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

@Composable
internal fun rememberCatalogScreenCoreBindingsBundle(
    stateStore: AppStateStore?,
    boardId: String?,
    initialArchiveSearchScope: ArchiveSearchScope?
): CatalogScreenCoreBindingsBundle {
    val runtimeObjects = rememberCatalogScreenRuntimeObjectsBundle()
    val persistentBindings = rememberCatalogScreenPersistentBindings(
        stateStore = stateStore,
        coroutineScope = runtimeObjects.coroutineScope,
        boardId = boardId
    )
    val initialStateInputs = resolveCatalogScreenInitialMutableStateInputs(
        boardId = boardId,
        persistedCatalogModes = persistentBindings.persistedCatalogModes,
        initialArchiveSearchScope = initialArchiveSearchScope,
        displayStyle = persistentBindings.persistentDisplayStyle,
        gridColumns = persistentBindings.persistentGridColumns
    )
    val mutableStateBundle = rememberCatalogScreenMutableStateBundle(
        boardId = boardId,
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
