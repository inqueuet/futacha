package com.valoser.futacha.shared.compat

import kotlin.coroutines.cancellation.CancellationException

/**
 * The journal left by an earlier, interrupted mode switch could not be
 * completed (M4-1). Nothing was rolled back and the journal stays, so every
 * commit gate keeps refusing work until a later recovery succeeds. The cause is
 * also recorded as suppressed: [modeSwitchFailureMessage] then advises a
 * restart instead of "現在のモードのまま使用できます".
 */
class ModeSwitchRecoveryException(cause: Throwable) :
    IllegalStateException("前回のモード切替を完了できませんでした", cause) {
    init {
        addSuppressed(cause)
    }
}

/**
 * Runs a coordinator's recovery of an interrupted switch, reporting a failure
 * as [ModeSwitchRecoveryException]. Cancellation is not a recovery failure.
 */
suspend fun <T> recoverInterruptedModeSwitch(recover: suspend () -> T): T = try {
    recover()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    throw failure as? ModeSwitchRecoveryException ?: ModeSwitchRecoveryException(failure)
}
