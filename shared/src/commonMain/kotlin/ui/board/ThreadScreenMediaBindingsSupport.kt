package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ThreadScreenMediaBindings(
    val normalizePreviewState: () -> Unit,
    val onMediaClick: (String, MediaType) -> Unit
)

internal fun buildThreadScreenMediaBindings(
    coroutineScope: CoroutineScope,
    currentPreviewState: () -> ThreadMediaPreviewState,
    setPreviewState: (ThreadMediaPreviewState) -> Unit,
    currentEntries: () -> List<MediaPreviewEntry>,
    ensureMediaPreviewCollection: suspend () -> MediaPreviewCollection,
    analyticsContext: Map<String, String> = emptyMap()
): ThreadScreenMediaBindings {
    return ThreadScreenMediaBindings(
        normalizePreviewState = {
            resolveThreadMediaPreviewNormalizationState(
                currentState = currentPreviewState(),
                totalCount = currentEntries().size
            )?.let(setPreviewState)
        },
        onMediaClick = { url, mediaType ->
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val collection = ensureMediaPreviewCollection()
                    val nextPreviewState = resolveThreadMediaClickState(
                        currentState = currentPreviewState(),
                        entries = collection.entries,
                        indexByKey = collection.indexByKey,
                        url = url,
                        mediaType = mediaType
                    )
                    if (nextPreviewState != null) {
                        AnalyticsTracker.event(
                            "media_fullscreen_opened",
                            analyticsContext + mapOf(
                                "media_type" to mediaType.name.lowercase(),
                                "media_context" to analyticsSessionContextId("media", url),
                                "collection_size_bucket" to analyticsCountBucket(collection.entries.size)
                            )
                        )
                        setPreviewState(nextPreviewState)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Logger.e(
                        "ThreadMediaPreview",
                        "Failed to open media preview for $url",
                        error
                    )
                }
            }
        }
    )
}
