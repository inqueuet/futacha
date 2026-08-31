package com.valoser.futacha.shared.ui.compat

/** Keeps cancellation out of the failure UI while always reopening the save gate. */
internal suspend fun <T> runCompatPageSaveWithCleanup(
    onFinished: () -> Unit,
    block: suspend () -> T
): T = try {
    block()
} finally {
    onFinished()
}
