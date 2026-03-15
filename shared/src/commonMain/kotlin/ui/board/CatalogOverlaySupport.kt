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

private fun CatalogOverlayState.withModeDialogVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(showModeDialog = isVisible)
}

private fun CatalogOverlayState.withDisplayStyleDialogVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(showDisplayStyleDialog = isVisible)
}

private fun CatalogOverlayState.withCreateThreadDialogVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(showCreateThreadDialog = isVisible)
}

private fun CatalogOverlayState.withSettingsMenuVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(showSettingsMenu = isVisible)
}

private fun CatalogOverlayState.withPastThreadSearchDialogVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(showPastThreadSearchDialog = isVisible)
}

private fun CatalogOverlayState.withGlobalSettingsVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(isGlobalSettingsVisible = isVisible)
}

private fun CatalogOverlayState.withCookieManagementVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(isCookieManagementVisible = isVisible)
}

private fun CatalogOverlayState.withNgManagementVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(isNgManagementVisible = isVisible)
}

private fun CatalogOverlayState.withWatchWordsVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(isWatchWordsVisible = isVisible)
}

private fun CatalogOverlayState.withPastSearchSheetVisible(isVisible: Boolean): CatalogOverlayState {
    return copy(isPastSearchSheetVisible = isVisible)
}

internal fun setCatalogModeDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withModeDialogVisible(isVisible)

internal fun setCatalogDisplayStyleDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withDisplayStyleDialogVisible(isVisible)

internal fun setCatalogCreateThreadDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withCreateThreadDialogVisible(isVisible)

internal fun setCatalogSettingsMenuVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withSettingsMenuVisible(isVisible)

internal fun setCatalogPastThreadSearchDialogVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withPastThreadSearchDialogVisible(isVisible)

internal fun setCatalogGlobalSettingsVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withGlobalSettingsVisible(isVisible)

internal fun setCatalogCookieManagementVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withCookieManagementVisible(isVisible)

internal fun setCatalogNgManagementVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withNgManagementVisible(isVisible)

internal fun setCatalogWatchWordsVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withWatchWordsVisible(isVisible)

internal fun setCatalogPastSearchSheetVisible(
    state: CatalogOverlayState,
    isVisible: Boolean
): CatalogOverlayState = state.withPastSearchSheetVisible(isVisible)

internal fun resetCatalogPastSearchOverlayState(state: CatalogOverlayState): CatalogOverlayState {
    return state
        .withPastThreadSearchDialogVisible(false)
        .withPastSearchSheetVisible(false)
}
