package com.valoser.futacha.shared.ui.compat

internal enum class CompatViewerLoadPresentation {
    EMPTY,
    LOADING,
    SOURCE,
    THUMBNAIL_FALLBACK,
    ERROR
}

internal fun resolveCompatViewerLoadPresentation(
    hasSource: Boolean,
    sourceReady: Boolean,
    sourceFailed: Boolean,
    hasThumbnailFallback: Boolean,
    thumbnailReady: Boolean,
    thumbnailFailed: Boolean
): CompatViewerLoadPresentation = when {
    !hasSource -> CompatViewerLoadPresentation.EMPTY
    sourceReady -> CompatViewerLoadPresentation.SOURCE
    sourceFailed && thumbnailReady -> CompatViewerLoadPresentation.THUMBNAIL_FALLBACK
    sourceFailed && (!hasThumbnailFallback || thumbnailFailed) -> CompatViewerLoadPresentation.ERROR
    else -> CompatViewerLoadPresentation.LOADING
}
