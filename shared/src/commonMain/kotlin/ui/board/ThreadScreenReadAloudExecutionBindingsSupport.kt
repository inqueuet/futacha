package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class ThreadScreenReadAloudStateBindings(
    val currentState: () -> ThreadReadAloudRuntimeState,
    val setState: (ThreadReadAloudRuntimeState) -> Unit
)

internal data class ThreadScreenReadAloudStateInputs(
    val currentJob: () -> Job?,
    val currentStatus: () -> ReadAloudStatus,
    val currentIndex: () -> Int,
    val currentCancelRequestedByUser: () -> Boolean,
    val setJob: (Job?) -> Unit,
    val setStatus: (ReadAloudStatus) -> Unit,
    val setIndex: (Int) -> Unit,
    val setCancelRequestedByUser: (Boolean) -> Unit
)

internal fun buildThreadScreenReadAloudStateBindings(
    inputs: ThreadScreenReadAloudStateInputs
): ThreadScreenReadAloudStateBindings {
    return ThreadScreenReadAloudStateBindings(
        currentState = {
            ThreadReadAloudRuntimeState(
                job = inputs.currentJob(),
                status = inputs.currentStatus(),
                currentIndex = inputs.currentIndex(),
                cancelRequestedByUser = inputs.currentCancelRequestedByUser()
            )
        },
        setState = { state ->
            inputs.setJob(state.job)
            inputs.setStatus(state.status)
            inputs.setIndex(state.currentIndex)
            inputs.setCancelRequestedByUser(state.cancelRequestedByUser)
        }
    )
}

internal data class ThreadScreenReadAloudCallbacks(
    val showMessage: (String) -> Unit,
    val showOptionalMessage: (String?) -> Unit,
    val scrollToPostIndex: suspend (Int) -> Unit,
    val speakText: suspend (String) -> Unit,
    val cancelActiveReadAloud: () -> Unit
)

internal data class ThreadScreenReadAloudDependencies(
    val currentSegments: () -> List<ReadAloudSegment>,
    val runSession: suspend (
        startIndex: Int,
        segments: List<ReadAloudSegment>,
        isRunnerActive: () -> Boolean,
        wasCancelledByUser: () -> Boolean,
        callbacks: ThreadReadAloudRunnerCallbacks
    ) -> ThreadReadAloudRunResult = ::runThreadReadAloudSession,
    val resolveFinalState: (Boolean, ReadAloudStatus) -> ThreadReadAloudFinalState = ::resolveThreadReadAloudFinalState
)

internal data class ThreadScreenReadAloudBindings(
    val startReadAloud: () -> Unit,
    val seekReadAloudToIndex: (Int, Boolean) -> Unit
)

internal fun buildThreadScreenReadAloudBindings(
    coroutineScope: CoroutineScope,
    stateBindings: ThreadScreenReadAloudStateBindings,
    callbacks: ThreadScreenReadAloudCallbacks,
    dependencies: ThreadScreenReadAloudDependencies
): ThreadScreenReadAloudBindings {
    fun updateState(transform: (ThreadReadAloudRuntimeState) -> ThreadReadAloudRuntimeState) {
        stateBindings.setState(transform(stateBindings.currentState()))
    }

    lateinit var startReadAloud: () -> Unit
    startReadAloud = start@{
        val segments = dependencies.currentSegments()
        val currentState = stateBindings.currentState()
        val startState = resolveReadAloudStartState(
            segmentCount = segments.size,
            currentIndex = currentState.currentIndex,
            isJobRunning = currentState.job != null
        )
        if (!startState.canStart) {
            callbacks.showOptionalMessage(startState.message)
            return@start
        }
        if (startState.normalizedIndex != currentState.currentIndex) {
            updateState { it.copy(currentIndex = startState.normalizedIndex) }
        }
        updateState { it.copy(cancelRequestedByUser = false) }
        val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            var completedNormally = false
            try {
                val runResult = dependencies.runSession(
                    stateBindings.currentState().currentIndex,
                    segments,
                    { isActive },
                    { stateBindings.currentState().cancelRequestedByUser },
                    buildThreadReadAloudRunnerCallbacks(
                        onSegmentStart = { segment, index ->
                            updateState {
                                it.copy(
                                    status = ReadAloudStatus.Speaking(segment),
                                    currentIndex = index
                                )
                            }
                        },
                        scrollToPostIndex = callbacks.scrollToPostIndex,
                        speakText = callbacks.speakText,
                        onFailure = { error ->
                            callbacks.showMessage(buildReadAloudFailureMessage(error))
                        }
                    )
                )
                completedNormally = runResult.completedNormally
                updateState { it.copy(currentIndex = runResult.nextIndex) }
            } finally {
                updateState { it.copy(cancelRequestedByUser = false) }
                val finalState = dependencies.resolveFinalState(
                    completedNormally,
                    stateBindings.currentState().status
                )
                updateState {
                    it.copy(
                        job = null,
                        status = finalState.status
                    )
                }
                callbacks.showOptionalMessage(finalState.message)
            }
        }
        updateState { it.copy(job = nextJob) }
        nextJob.start()
    }

    val seekReadAloudToIndex: (Int, Boolean) -> Unit = seek@{ targetIndex, shouldScroll ->
        val seekState = resolveReadAloudSeekState(
            segments = dependencies.currentSegments(),
            status = stateBindings.currentState().status,
            targetIndex = targetIndex
        ) ?: return@seek
        callbacks.cancelActiveReadAloud()
        updateState {
            it.copy(
                job = null,
                status = ReadAloudStatus.Idle,
                currentIndex = seekState.targetIndex
            )
        }
        val targetSegment = seekState.targetSegment
        if (shouldScroll && targetSegment != null) {
            coroutineScope.launch {
                callbacks.scrollToPostIndex(targetSegment.postIndex)
            }
        }
        if (seekState.shouldRestart) {
            startReadAloud()
        }
    }

    return ThreadScreenReadAloudBindings(
        startReadAloud = startReadAloud,
        seekReadAloudToIndex = seekReadAloudToIndex
    )
}
