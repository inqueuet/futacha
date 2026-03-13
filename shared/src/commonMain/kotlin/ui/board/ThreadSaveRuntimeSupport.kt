package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class ThreadSaveRuntime(
    val saveService: ThreadSaveService,
    val manualCallbacks: ThreadManualSaveRunnerCallbacks,
    val autoCallbacks: ThreadAutoSaveRunnerCallbacks
)

internal fun buildThreadSaveRuntime(
    saveService: ThreadSaveService
): ThreadSaveRuntime {
    return ThreadSaveRuntime(
        saveService = saveService,
        manualCallbacks = buildThreadManualSaveRunnerCallbacks(saveService),
        autoCallbacks = buildThreadAutoSaveRunnerCallbacks(saveService)
    )
}

internal fun buildThreadSaveRuntime(
    httpClient: HttpClient,
    fileSystem: FileSystem
): ThreadSaveRuntime {
    return buildThreadSaveRuntime(
        ThreadSaveService(
            httpClient = httpClient,
            fileSystem = fileSystem
        )
    )
}

internal fun buildOptionalThreadSingleMediaSaveRunnerCallbacks(
    httpClient: HttpClient?,
    fileSystem: FileSystem?
): ThreadSingleMediaSaveRunnerCallbacks? {
    if (httpClient == null || fileSystem == null) {
        return null
    }
    return buildThreadSingleMediaSaveRunnerCallbacks(
        httpClient = httpClient,
        fileSystem = fileSystem
    )
}

internal suspend fun indexSavedThreadOrLog(
    repository: SavedThreadRepository?,
    savedThread: SavedThread?,
    logTag: String,
    failureMessage: String?
) {
    if (repository == null || savedThread == null || failureMessage == null) {
        return
    }
    repository.addThreadToIndex(savedThread)
        .onFailure { Logger.e(logTag, failureMessage, it) }
}

internal fun CoroutineScope.launchThreadSaveProgressCollector(
    saveService: ThreadSaveService,
    onProgress: (SaveProgress?) -> Unit
): Job {
    return launch {
        saveService.saveProgress.collect { progress ->
            onProgress(progress)
        }
    }
}

internal fun resolveTrackedJobAfterCompletion(
    trackedJob: Job?,
    runningJob: Job?
): Job? {
    return if (runningJob != null && trackedJob == runningJob) {
        null
    } else {
        trackedJob
    }
}

internal data class ThreadSaveProgressDialogCallbacks(
    val onDismissRequest: () -> Unit,
    val onCancelRequest: () -> Unit
)

internal fun buildThreadSaveProgressDialogCallbacks(
    onDismissRequest: () -> Unit,
    onCancelRequest: () -> Unit
): ThreadSaveProgressDialogCallbacks {
    return ThreadSaveProgressDialogCallbacks(
        onDismissRequest = onDismissRequest,
        onCancelRequest = onCancelRequest
    )
}
