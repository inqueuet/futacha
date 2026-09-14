package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keep recoverable I/O failures inside the screen that started the action. */
internal fun CoroutineScope.launchCompatScreenAction(
    tag: String,
    onFailure: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Logger.e(tag, "Screen action failed", failure)
        onFailure(failure)
    }
}
