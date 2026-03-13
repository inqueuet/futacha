package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.DrawerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class CatalogScreenLifecycleBindings(
    val backAction: CatalogBackAction,
    val onCloseDrawerBack: () -> Unit,
    val onExitSearchBack: () -> Unit,
    val onNavigateBack: () -> Unit,
    val onInitialLoad: () -> Unit,
    val onDispose: () -> Unit
)

internal fun buildCatalogScreenLifecycleBindings(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    isDrawerOpen: Boolean,
    isSearchActive: Boolean,
    setSearchActive: (Boolean) -> Unit,
    setSearchQuery: (String) -> Unit,
    onBack: () -> Unit,
    onInitialLoad: () -> Unit,
    currentCatalogLoadJob: () -> kotlinx.coroutines.Job?,
    setCatalogLoadJob: (kotlinx.coroutines.Job?) -> Unit,
    currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit
): CatalogScreenLifecycleBindings {
    return CatalogScreenLifecycleBindings(
        backAction = resolveCatalogBackAction(
            isDrawerOpen = isDrawerOpen,
            isSearchActive = isSearchActive
        ),
        onCloseDrawerBack = {
            coroutineScope.launch { drawerState.close() }
        },
        onExitSearchBack = {
            setSearchActive(false)
            setSearchQuery("")
        },
        onNavigateBack = onBack,
        onInitialLoad = onInitialLoad,
        onDispose = {
            currentCatalogLoadJob()?.cancel()
            setCatalogLoadJob(null)
            currentPastSearchRuntimeState().job?.cancel()
            setPastSearchRuntimeState(currentPastSearchRuntimeState().copy(job = null))
        }
    )
}

@Composable
internal fun rememberCatalogScreenLifecycleBindings(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    isDrawerOpen: Boolean,
    isSearchActive: Boolean,
    onBack: () -> Unit,
    onInitialLoad: () -> Unit,
    currentCatalogLoadJob: () -> kotlinx.coroutines.Job?,
    setCatalogLoadJob: (kotlinx.coroutines.Job?) -> Unit,
    currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit,
    setSearchActive: (Boolean) -> Unit,
    setSearchQuery: (String) -> Unit
): CatalogScreenLifecycleBindings {
    return remember(
        coroutineScope,
        drawerState,
        isDrawerOpen,
        isSearchActive,
        onBack,
        onInitialLoad,
        currentCatalogLoadJob(),
        currentPastSearchRuntimeState()
    ) {
        buildCatalogScreenLifecycleBindings(
            coroutineScope = coroutineScope,
            drawerState = drawerState,
            isDrawerOpen = isDrawerOpen,
            isSearchActive = isSearchActive,
            setSearchActive = setSearchActive,
            setSearchQuery = setSearchQuery,
            onBack = onBack,
            onInitialLoad = onInitialLoad,
            currentCatalogLoadJob = currentCatalogLoadJob,
            setCatalogLoadJob = setCatalogLoadJob,
            currentPastSearchRuntimeState = currentPastSearchRuntimeState,
            setPastSearchRuntimeState = setPastSearchRuntimeState
        )
    }
}
