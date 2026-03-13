package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.service.buildThreadStorageId

internal fun isThreadSaveLocationPermissionIssue(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    return message.contains("cannot resolve tree uri") ||
        message.contains("write permission lost for tree uri") ||
        message.contains("please select the folder again") ||
        message.contains("invalid bookmark data") ||
        message.contains("failed to resolve bookmark") ||
        message.contains("bookmark url has no filesystem path")
}

internal fun requiresThreadManualSaveLocationSelection(
    isAndroidPlatform: Boolean,
    manualSaveLocation: SaveLocation?
): Boolean {
    return isAndroidPlatform &&
        manualSaveLocation !is SaveLocation.TreeUri &&
        manualSaveLocation !is SaveLocation.Path
}

internal enum class ThreadAutoSaveAvailability {
    MissingPage,
    OfflineCopy,
    ThreadMismatch,
    MissingDependencies,
    InProgress,
    Throttled,
    Ready
}

internal fun resolveThreadAutoSaveAvailability(
    pageThreadId: String?,
    expectedThreadId: String,
    isShowingOfflineCopy: Boolean,
    hasAutoSaveRepository: Boolean,
    hasHttpClient: Boolean,
    hasFileSystem: Boolean,
    isAutoSaveInProgress: Boolean,
    lastAutoSaveTimestampMillis: Long,
    nowMillis: Long,
    minIntervalMillis: Long
): ThreadAutoSaveAvailability {
    return when {
        pageThreadId == null -> ThreadAutoSaveAvailability.MissingPage
        isShowingOfflineCopy -> ThreadAutoSaveAvailability.OfflineCopy
        pageThreadId != expectedThreadId -> ThreadAutoSaveAvailability.ThreadMismatch
        !hasAutoSaveRepository || !hasHttpClient || !hasFileSystem -> ThreadAutoSaveAvailability.MissingDependencies
        isAutoSaveInProgress -> ThreadAutoSaveAvailability.InProgress
        nowMillis - lastAutoSaveTimestampMillis < minIntervalMillis -> ThreadAutoSaveAvailability.Throttled
        else -> ThreadAutoSaveAvailability.Ready
    }
}

internal data class ThreadAutoSaveCompletionState(
    val nextTimestampMillis: Long,
    val savedThread: SavedThread? = null,
    val failureMessage: String? = null,
    val failure: Throwable? = null
)

internal fun buildThreadAutoSaveFailureMessage(threadId: String): String {
    return "Auto-save failed for thread $threadId"
}

internal fun buildThreadAutoSaveIndexFailureMessage(threadId: String): String {
    return "Failed to index auto-saved thread $threadId"
}

internal fun resolveThreadAutoSaveCompletionState(
    threadId: String,
    saveResult: Result<SavedThread>,
    previousTimestampMillis: Long,
    attemptStartedAtMillis: Long,
    completionTimestampMillis: Long
): ThreadAutoSaveCompletionState {
    return saveResult.fold(
        onSuccess = { savedThread ->
            ThreadAutoSaveCompletionState(
                nextTimestampMillis = completionTimestampMillis,
                savedThread = savedThread
            )
        },
        onFailure = { error ->
            ThreadAutoSaveCompletionState(
                nextTimestampMillis = maxOf(previousTimestampMillis, attemptStartedAtMillis),
                failureMessage = buildThreadAutoSaveFailureMessage(threadId),
                failure = error
            )
        }
    )
}

internal fun buildThreadSaveBusyMessage(): String = "保存処理を実行中です…"

internal fun buildThreadSaveLocationRequiredMessage(): String {
    return "保存先が未選択です。設定からフォルダを選択してください。"
}

internal fun buildThreadSaveUnavailableMessage(): String = "保存機能が利用できません"

internal fun buildThreadSaveNotReadyMessage(): String = "スレッドの読み込みが完了していません"

internal fun buildThreadSavePermissionLostMessage(): String {
    return "保存先の権限が失われました。フォルダを再選択してください。"
}

internal fun buildThreadSaveFailureMessage(error: Throwable): String {
    return "保存に失敗しました: ${error.message}"
}

internal fun buildThreadSaveUnexpectedErrorMessage(error: Throwable): String {
    return "エラーが発生しました: ${error.message}"
}

internal data class ThreadManualSaveSuccessState(
    val displayedSavePath: String,
    val message: String
)

internal sealed interface ThreadManualSaveUiOutcome {
    data class Success(
        val savedThread: SavedThread,
        val successState: ThreadManualSaveSuccessState,
        val indexFailureMessage: String
    ) : ThreadManualSaveUiOutcome

    data class Failure(
        val errorState: ThreadManualSaveErrorState
    ) : ThreadManualSaveUiOutcome
}

internal fun buildThreadManualSaveSuccessState(
    savedThread: SavedThread,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?
): ThreadManualSaveSuccessState {
    val storageDirectory = savedThread.storageId
        ?: buildThreadStorageId(savedThread.boardId, savedThread.threadId)
    val displayedSavePath = buildDisplayedSavePathValue(
        manualSaveDirectory = manualSaveDirectory,
        manualSaveLocation = manualSaveLocation,
        resolvedManualSaveDirectory = resolvedManualSaveDirectory,
        relativePath = storageDirectory
    )
    return ThreadManualSaveSuccessState(
        displayedSavePath = displayedSavePath,
        message = "スレッドを保存しました: $displayedSavePath"
    )
}

internal fun buildThreadManualSaveIndexFailureMessage(threadId: String): String {
    return "Failed to index manually saved thread $threadId"
}

internal data class ThreadManualSaveErrorState(
    val message: String,
    val shouldResetManualSaveDirectory: Boolean = false,
    val shouldResetSaveDirectorySelection: Boolean = false,
    val shouldOpenDirectoryPicker: Boolean = false
)

internal fun resolveThreadManualSaveErrorState(
    error: Throwable,
    isUnexpected: Boolean
): ThreadManualSaveErrorState {
    return if (isThreadSaveLocationPermissionIssue(error)) {
        ThreadManualSaveErrorState(
            message = buildThreadSavePermissionLostMessage(),
            shouldResetManualSaveDirectory = true,
            shouldResetSaveDirectorySelection = true,
            shouldOpenDirectoryPicker = true
        )
    } else {
        ThreadManualSaveErrorState(
            message = if (isUnexpected) {
                buildThreadSaveUnexpectedErrorMessage(error)
            } else {
                buildThreadSaveFailureMessage(error)
            }
        )
    }
}

internal fun resolveThreadManualSaveUiOutcome(
    saveResult: ThreadManualSaveRunResult,
    threadId: String,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?
): ThreadManualSaveUiOutcome {
    return when (saveResult) {
        is ThreadManualSaveRunResult.Success -> ThreadManualSaveUiOutcome.Success(
            savedThread = saveResult.savedThread,
            successState = buildThreadManualSaveSuccessState(
                savedThread = saveResult.savedThread,
                manualSaveDirectory = manualSaveDirectory,
                manualSaveLocation = manualSaveLocation,
                resolvedManualSaveDirectory = resolvedManualSaveDirectory
            ),
            indexFailureMessage = buildThreadManualSaveIndexFailureMessage(threadId)
        )
        is ThreadManualSaveRunResult.Failure -> ThreadManualSaveUiOutcome.Failure(
            errorState = resolveThreadManualSaveErrorState(
                error = saveResult.error,
                isUnexpected = saveResult.isUnexpected
            )
        )
    }
}

internal data class ThreadSingleMediaSaveSuccessState(
    val mediaLabel: String,
    val displayedSavePath: String,
    val message: String
)

internal sealed interface ThreadSingleMediaSaveUiOutcome {
    data class Success(
        val successState: ThreadSingleMediaSaveSuccessState
    ) : ThreadSingleMediaSaveUiOutcome

    data class Failure(
        val errorState: ThreadManualSaveErrorState
    ) : ThreadSingleMediaSaveUiOutcome
}

internal fun buildThreadSingleMediaSaveSuccessState(
    savedMedia: SavedMediaFile,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?
): ThreadSingleMediaSaveSuccessState {
    val mediaLabel = when (savedMedia.mediaType) {
        SavedMediaType.VIDEO -> "動画"
        SavedMediaType.IMAGE -> "画像"
    }
    val displayedSavePath = buildDisplayedSavePathValue(
        manualSaveDirectory = manualSaveDirectory,
        manualSaveLocation = manualSaveLocation,
        resolvedManualSaveDirectory = resolvedManualSaveDirectory,
        relativePath = savedMedia.relativePath
    )
    return ThreadSingleMediaSaveSuccessState(
        mediaLabel = mediaLabel,
        displayedSavePath = displayedSavePath,
        message = "${mediaLabel}を保存しました: $displayedSavePath"
    )
}

internal fun resolveThreadSingleMediaSaveErrorState(
    error: Throwable,
    isUnexpected: Boolean
): ThreadManualSaveErrorState {
    return resolveThreadManualSaveErrorState(
        error = error,
        isUnexpected = isUnexpected
    )
}

internal fun resolveThreadSingleMediaSaveUiOutcome(
    saveResult: ThreadSingleMediaSaveRunResult,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?
): ThreadSingleMediaSaveUiOutcome {
    return when (saveResult) {
        is ThreadSingleMediaSaveRunResult.Success -> ThreadSingleMediaSaveUiOutcome.Success(
            successState = buildThreadSingleMediaSaveSuccessState(
                savedMedia = saveResult.savedMedia,
                manualSaveDirectory = manualSaveDirectory,
                manualSaveLocation = manualSaveLocation,
                resolvedManualSaveDirectory = resolvedManualSaveDirectory
            )
        )
        is ThreadSingleMediaSaveRunResult.Failure -> ThreadSingleMediaSaveUiOutcome.Failure(
            errorState = resolveThreadSingleMediaSaveErrorState(
                error = saveResult.error,
                isUnexpected = saveResult.isUnexpected
            )
        )
    }
}

internal data class ThreadAutoSaveUiApplyState(
    val nextTimestampMillis: Long,
    val savedThread: SavedThread? = null,
    val indexFailureMessage: String? = null,
    val failureMessage: String? = null,
    val failure: Throwable? = null
)

internal fun buildThreadAutoSaveUiApplyState(
    completionState: ThreadAutoSaveCompletionState,
    threadId: String
): ThreadAutoSaveUiApplyState {
    return ThreadAutoSaveUiApplyState(
        nextTimestampMillis = completionState.nextTimestampMillis,
        savedThread = completionState.savedThread,
        indexFailureMessage = completionState.savedThread?.let {
            buildThreadAutoSaveIndexFailureMessage(threadId)
        },
        failureMessage = completionState.failureMessage,
        failure = completionState.failure
    )
}

internal enum class ThreadSaveAvailability {
    Busy,
    LocationRequired,
    Unavailable,
    NotReady,
    Ready
}

internal fun resolveThreadSaveAvailability(
    isAnySaveInProgress: Boolean,
    requiresManualLocationSelection: Boolean,
    hasStorageDependencies: Boolean,
    isThreadReady: Boolean
): ThreadSaveAvailability {
    return when {
        isAnySaveInProgress -> ThreadSaveAvailability.Busy
        requiresManualLocationSelection -> ThreadSaveAvailability.LocationRequired
        !isThreadReady -> ThreadSaveAvailability.NotReady
        !hasStorageDependencies -> ThreadSaveAvailability.Unavailable
        else -> ThreadSaveAvailability.Ready
    }
}

internal fun buildThreadActionFailureMessage(failurePrefix: String, error: Throwable): String {
    val detail = error.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    return "$failurePrefix$detail"
}
