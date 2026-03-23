package com.valoser.futacha.shared.ui.board

internal enum class MediaSaveAvailability {
    Busy,
    Unsupported,
    LocationRequired,
    Unavailable,
    Ready
}

internal fun resolveMediaSaveAvailability(
    isAnySaveInProgress: Boolean,
    isRemoteMedia: Boolean,
    requiresManualLocationSelection: Boolean,
    hasStorageDependencies: Boolean
): MediaSaveAvailability {
    return when {
        isAnySaveInProgress -> MediaSaveAvailability.Busy
        !isRemoteMedia -> MediaSaveAvailability.Unsupported
        requiresManualLocationSelection -> MediaSaveAvailability.LocationRequired
        !hasStorageDependencies -> MediaSaveAvailability.Unavailable
        else -> MediaSaveAvailability.Ready
    }
}

internal data class ThreadMediaSaveRequestState(
    val canStartSave: Boolean,
    val message: String? = null,
    val shouldOpenSaveDirectoryPicker: Boolean = false
)

internal fun resolveThreadMediaSaveRequestState(
    isAnySaveInProgress: Boolean,
    isRemoteMedia: Boolean,
    requiresManualLocationSelection: Boolean,
    hasStorageDependencies: Boolean
): ThreadMediaSaveRequestState {
    return when (
        resolveMediaSaveAvailability(
            isAnySaveInProgress = isAnySaveInProgress,
            isRemoteMedia = isRemoteMedia,
            requiresManualLocationSelection = requiresManualLocationSelection,
            hasStorageDependencies = hasStorageDependencies
        )
    ) {
        MediaSaveAvailability.Busy -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = buildThreadSaveBusyMessage()
        )
        MediaSaveAvailability.Unsupported -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = "このメディアは保存に対応していません"
        )
        MediaSaveAvailability.LocationRequired -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = buildThreadSaveLocationRequiredMessage(),
            shouldOpenSaveDirectoryPicker = true
        )
        MediaSaveAvailability.Unavailable -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = buildThreadSaveUnavailableMessage()
        )
        MediaSaveAvailability.Ready -> ThreadMediaSaveRequestState(canStartSave = true)
    }
}
