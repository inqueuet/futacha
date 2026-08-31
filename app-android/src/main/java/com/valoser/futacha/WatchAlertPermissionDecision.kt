package com.valoser.futacha

internal enum class WatchAlertPermissionAction {
    DISABLE,
    ENABLE_IMMEDIATELY,
    EXPLAIN_AND_REQUEST_PERMISSION
}

/**
 * Resolves a user-initiated watch-alert toggle without observing persisted state.
 *
 * Keeping this decision at the toggle boundary prevents a cold-started process from
 * mistaking an already-enabled preference for a new permission request.
 */
internal fun resolveWatchAlertPermissionAction(
    requestedEnabled: Boolean,
    runtimePermissionRequired: Boolean,
    permissionGranted: Boolean
): WatchAlertPermissionAction = when {
    !requestedEnabled -> WatchAlertPermissionAction.DISABLE
    !runtimePermissionRequired || permissionGranted -> WatchAlertPermissionAction.ENABLE_IMMEDIATELY
    else -> WatchAlertPermissionAction.EXPLAIN_AND_REQUEST_PERMISSION
}
