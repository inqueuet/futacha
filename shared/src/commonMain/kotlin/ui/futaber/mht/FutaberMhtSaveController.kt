package com.valoser.futacha.shared.ui.futaber.mht

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface MhtSaveStep {
    data object Choose : MhtSaveStep
    data class Running(val done: Int, val total: Int) : MhtSaveStep
    data class Done(val result: FutaberMhtSaveResult) : MhtSaveStep
    data class Failed(val message: String) : MhtSaveStep
}

/**
 * The steps of one MHT save, apart from how they are drawn: each mode shows them in its own look
 * (ふたばー風モード as the original's alert, the other two as Material dialogs). The save itself is the
 * protected one: a single user save at a time, kept alive in the background.
 *
 * [request] is read once, when the save starts: the thread may be reloaded while the file is written, and
 * that must neither start the save again nor change what is being saved.
 */
internal class FutaberMhtSaveController(
    private val library: FutaberMhtLibrary,
    private val request: () -> FutaberMhtSaveRequest,
    private val scope: CoroutineScope,
    initial: MhtSaveStep = MhtSaveStep.Choose
) {
    constructor(
        library: FutaberMhtLibrary,
        request: FutaberMhtSaveRequest,
        scope: CoroutineScope,
        initial: MhtSaveStep = MhtSaveStep.Choose
    ) : this(library, { request }, scope, initial)

    var step: MhtSaveStep by mutableStateOf(initial)
        private set

    private var job: Job? = null

    val busy: Boolean get() = step is MhtSaveStep.Running

    /** Starts the save; ignored unless the choice is still open. */
    fun start(fullImages: Boolean) {
        if (step !is MhtSaveStep.Choose) return
        step = MhtSaveStep.Running(0, 0)
        val saved = request()
        job = scope.launch {
            try {
                library.saveProtected(saved, fullImages, Clock.System.now().toEpochMilliseconds()) { done, total ->
                    step = MhtSaveStep.Running(done, total)
                }.onSuccess { step = MhtSaveStep.Done(it) }
                    .onFailure { step = MhtSaveStep.Failed(it.message ?: "MHTを保存できませんでした") }
            } catch (cancelled: CancellationException) {
                // Stopped from outside (the system's save notification) while the dialog is still shown: say so, instead of leaving it spinning.
                if (step is MhtSaveStep.Running) step = MhtSaveStep.Failed("保存を中止しました")
                throw cancelled
            }
        }
    }

    /**
     * Stops a save that is running. The file that was being written is removed (a file saved earlier for the same
     * thread is kept as it was); nothing is reported, since the person asked for it.
     */
    fun cancel() {
        if (step !is MhtSaveStep.Running) return
        job?.cancel()
        job = null
    }

    fun absolutePath(result: FutaberMhtSaveResult): String = library.absolutePath(result.entry)
}

@Composable
internal fun rememberFutaberMhtSaveController(
    library: FutaberMhtLibrary,
    request: FutaberMhtSaveRequest,
    /** Null waits for the choice; a value starts the save at once (the other modes ask in their own dialog). */
    startWith: Boolean? = null
): FutaberMhtSaveController {
    val scope = rememberCoroutineScope()
    // The request is read when the save starts. A new request (the thread was reloaded) does not make a new controller:
    // that would start the save a second time ("別の保存を実行中です") and lose the running one's result.
    val latestRequest by rememberUpdatedState(request)
    return remember(library, startWith) {
        FutaberMhtSaveController(library, { latestRequest }, scope).also { controller -> startWith?.let(controller::start) }
    }
}
