package com.valoser.futacha.shared.ui.board

internal data class CatalogOverlayState(
    val showModeDialog: Boolean = false,
    val showDisplayStyleDialog: Boolean = false,
    val showCreateThreadDialog: Boolean = false,
    val showSettingsMenu: Boolean = false,
    val showPastThreadSearchDialog: Boolean = false,
    val isGlobalSettingsVisible: Boolean = false,
    val isCookieManagementVisible: Boolean = false,
    val isNgManagementVisible: Boolean = false,
    val isWatchWordsVisible: Boolean = false,
    val isPastSearchSheetVisible: Boolean = false
)

internal fun setCatalogModeDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(showModeDialog = isVisible)

internal fun setCatalogDisplayStyleDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(showDisplayStyleDialog = isVisible)

internal fun setCatalogCreateThreadDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(showCreateThreadDialog = isVisible)

internal fun setCatalogSettingsMenuVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(showSettingsMenu = isVisible)

internal fun setCatalogPastThreadSearchDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(showPastThreadSearchDialog = isVisible)

internal fun setCatalogGlobalSettingsVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(isGlobalSettingsVisible = isVisible)

internal fun setCatalogCookieManagementVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(isCookieManagementVisible = isVisible)

internal fun setCatalogNgManagementVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(isNgManagementVisible = isVisible)

internal fun setCatalogWatchWordsVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(isWatchWordsVisible = isVisible)

internal fun setCatalogPastSearchSheetVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.copy(isPastSearchSheetVisible = isVisible)

internal fun resetCatalogPastSearchOverlayState(state: CatalogOverlayState): CatalogOverlayState {
    return state.copy(
        showPastThreadSearchDialog = false,
        isPastSearchSheetVisible = false
    )
}
