package com.valoser.futacha.shared.ui.board
internal actual fun logVideoPlaybackDiagnostic(event: String, detail: String) = Unit

internal actual fun platformVideoPlaybackCapabilityError(
    videoUrl: String,
    resolvedInfo: com.valoser.futacha.shared.media.source.OriginalMediaInfo?
): VideoPlaybackError? = null
