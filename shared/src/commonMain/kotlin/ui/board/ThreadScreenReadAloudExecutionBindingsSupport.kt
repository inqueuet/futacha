package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
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

/** Skip/seek: cancel the utterance but keep the playback protection and the loaded voice. */
internal fun interruptThreadReadAloud(
    stateBindings: ThreadScreenReadAloudStateBindings,
    interruptPlayback: () -> Unit
) {
    interruptPlayback()
    val current = stateBindings.currentState()
    stateBindings.setState(current.copy(cancelRequestedByUser = true))
    current.job?.cancel()
}

internal data class ThreadScreenReadAloudCallbacks(
    val showMessage: (String) -> Unit,
    val showOptionalMessage: (String?) -> Unit,
    val scrollToPostIndex: suspend (Int) -> Unit,
    val speakText: suspend (String) -> Unit,
    val cancelActiveReadAloud: () -> Unit,
    val interruptActiveReadAloud: () -> Unit = cancelActiveReadAloud
)

internal data class ThreadScreenReadAloudDependencies(
    val currentSegments: () -> List<ReadAloudSegment>,
    val runSession: suspend (
        startIndex: Int,
        segments: List<ReadAloudSegment>,
        isRunnerActive: () -> Boolean,
        wasCancelledByUser: () -> Boolean,
        callbacks: ThreadReadAloudRunnerCallbacks
    ) -> ThreadReadAloudRunResult = { start, segments, active, cancelled, callbacks ->
        runThreadReadAloudSession(start, segments, active, cancelled, callbacks, latestSegments = currentSegments)
    },
    val resolveFinalState: (Boolean, ReadAloudStatus) -> ThreadReadAloudFinalState = ::resolveThreadReadAloudFinalState
)

@Composable
internal fun threadScreenReadAloudDependencies(segments: List<ReadAloudSegment>): ThreadScreenReadAloudDependencies {
    // A running session asks for the list as it is now (L12): the one captured at composition went stale,
    // so posts hidden by NG later were read and new replies were not.
    val latest = rememberUpdatedState(segments)
    return remember { ThreadScreenReadAloudDependencies(currentSegments = { latest.value }) }
}

internal data class ThreadScreenReadAloudBindings(
    val startReadAloud: () -> Unit,
    val seekReadAloudToIndex: (Int, Boolean) -> Unit
)

internal fun buildThreadScreenReadAloudBindings(
    coroutineScope: CoroutineScope,
    stateBindings: ThreadScreenReadAloudStateBindings,
    callbacks: ThreadScreenReadAloudCallbacks,
    dependencies: ThreadScreenReadAloudDependencies,
    analyticsContext: Map<String, String> = emptyMap()
): ThreadScreenReadAloudBindings {
    fun updateState(transform: (ThreadReadAloudRuntimeState) -> ThreadReadAloudRuntimeState) {
        stateBindings.setState(transform(stateBindings.currentState()))
    }

    fun updateStateIfCurrentJob(
        job: Job,
        transform: (ThreadReadAloudRuntimeState) -> ThreadReadAloudRuntimeState
    ): Boolean {
        var updated = false
        stateBindings.setState(
            stateBindings.currentState().let { state ->
                if (state.job === job) {
                    updated = true
                    transform(state)
                } else {
                    state
                }
            }
        )
        return updated
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
            AnalyticsTracker.event(
                "read_aloud_start_result",
                analyticsContext + mapOf(
                    "result" to "blocked",
                    "segment_count_bucket" to analyticsCountBucket(segments.size)
                )
            )
            callbacks.showOptionalMessage(startState.message)
            return@start
        }
        AnalyticsTracker.event(
            "read_aloud_started",
            analyticsContext + mapOf("segment_count_bucket" to analyticsCountBucket(segments.size))
        )
        if (startState.normalizedIndex != currentState.currentIndex) {
            updateState { it.copy(currentIndex = startState.normalizedIndex) }
        }
        updateState { it.copy(cancelRequestedByUser = false) }
        var launchedJob: Job? = null
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
                            AnalyticsTracker.event(
                                "read_aloud_error",
                                analyticsContext + mapOf("error_type" to (error::class.simpleName ?: "unknown"))
                            )
                            callbacks.showMessage(buildReadAloudFailureMessage(error))
                        }
                    )
                )
                completedNormally = runResult.completedNormally
                updateState { it.copy(currentIndex = runResult.nextIndex) }
            } finally {
                val finishedJob = launchedJob
                if (
                    finishedJob != null &&
                    updateStateIfCurrentJob(finishedJob) { it.copy(cancelRequestedByUser = false) }
                ) {
                    val finalState = dependencies.resolveFinalState(
                        completedNormally,
                        stateBindings.currentState().status
                    )
                    updateStateIfCurrentJob(finishedJob) {
                        it.copy(
                            job = null,
                            status = finalState.status
                        )
                    }
                    // Reading ended on its own (finished or failed): stop playback so the
                    // platform returns its audio session and other apps' audio resumes,
                    // instead of holding it until the screen closes (N4-4). With no job
                    // left this only stops playback; the user-cancel flag is restored.
                    // Stop (not interrupt): a finished reading also releases the loaded voice.
                    callbacks.cancelActiveReadAloud()
                    updateState { it.copy(cancelRequestedByUser = false) }
                    callbacks.showOptionalMessage(finalState.message)
                    AnalyticsTracker.event(
                        "read_aloud_finished",
                        analyticsContext + mapOf(
                            "result" to if (completedNormally) "completed" else "interrupted",
                            "segment_count_bucket" to analyticsCountBucket(segments.size)
                        )
                    )
                }
            }
        }
        launchedJob = nextJob
        updateState { it.copy(job = nextJob) }
        nextJob.start()
    }

    val seekReadAloudToIndex: (Int, Boolean) -> Unit = seek@{ targetIndex, shouldScroll ->
        val seekState = resolveReadAloudSeekState(
            segments = dependencies.currentSegments(),
            status = stateBindings.currentState().status,
            targetIndex = targetIndex
        ) ?: return@seek
        AnalyticsTracker.event(
            "read_aloud_seek",
            analyticsContext + mapOf(
                "should_scroll" to shouldScroll.toString(),
                "will_restart" to seekState.shouldRestart.toString()
            )
        )
        // Reading continues from the new position: keep the playback protection and the
        // verified voice. A seek that does not restart has nothing to keep.
        if (seekState.shouldRestart) callbacks.interruptActiveReadAloud() else callbacks.cancelActiveReadAloud()
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
