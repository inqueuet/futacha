package com.valoser.futacha.shared.util

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A provider may commit its write on close. Never report success before that succeeds. */
internal suspend fun <T> withFileWriteCompletion(
    close: suspend () -> Unit,
    write: suspend () -> T
): T {
    var failure: Throwable? = null
    try {
        return write()
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            withContext(NonCancellable) { close() }
        } catch (closeError: Throwable) {
            val writeError = failure
            if (writeError == null) throw closeError
            if (writeError !== closeError) writeError.addSuppressed(closeError)
        }
    }
}
