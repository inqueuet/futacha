package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

@Stable
internal data class CatalogScreenRuntimeObjectsBundle(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val isDrawerOpen: Boolean,
    val archiveSearchJson: Json,
    val catalogGridState: LazyGridState,
    val catalogListState: LazyListState
)

@Composable
internal fun rememberCatalogScreenRuntimeObjectsBundle(): CatalogScreenRuntimeObjectsBundle {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    val archiveSearchJson = remember {
        Json {
            ignoreUnknownKeys = true
        }
    }
    val catalogGridState = rememberLazyGridState()
    val catalogListState = rememberLazyListState()
    return CatalogScreenRuntimeObjectsBundle(
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        drawerState = drawerState,
        isDrawerOpen = isDrawerOpen,
        archiveSearchJson = archiveSearchJson,
        catalogGridState = catalogGridState,
        catalogListState = catalogListState
    )
}
