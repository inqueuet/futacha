package com.valoser.futacha.shared.media.video.model

internal enum class MosaicReviewFilter { DETECTED, ALL, SELECTED }

/** Half-open source time interval. Review never changes the document or export time base. */
internal data class MosaicReviewInterval(val startUs: Long, val endUs: Long)

/** Small index of region lifetimes, rebuilt on edits/selection, never from dense tracking samples. */
internal class MosaicReviewIndex private constructor(
    val regions: List<MosaicRegion>,
    val intervals: List<MosaicReviewInterval>
) {
    fun atOrAfter(timeUs: Long): Long? = intervals.firstOrNull { it.endUs > timeUs }
        ?.let { maxOf(timeUs, it.startUs) }

    fun atOrBefore(timeUs: Long, frames: VideoFrameIndex): Long? = intervals.lastOrNull { it.startUs <= timeUs }
        ?.let { minOf(timeUs, frames.atOrBefore(it.endUs - 1)) }

    // Wrap at the ends; going back from inside an interval returns to that interval's start.
    fun nextStart(timeUs: Long): Long? = (intervals.firstOrNull { it.startUs > timeUs } ?: intervals.firstOrNull())?.startUs
    fun previousStart(timeUs: Long): Long? = (intervals.lastOrNull { it.startUs < timeUs } ?: intervals.lastOrNull())?.startUs

    companion object {
        val EMPTY = MosaicReviewIndex(emptyList(), emptyList())

        fun create(document: MosaicDocument, filter: MosaicReviewFilter, selectedId: String?, frames: VideoFrameIndex): MosaicReviewIndex {
            val regions = document.regions.filter {
                when (filter) {
                    MosaicReviewFilter.DETECTED -> it.label != null
                    MosaicReviewFilter.ALL -> true
                    MosaicReviewFilter.SELECTED -> it.id == selectedId
                }
            }
            val timestamps = frames.timestampsUs
            val spans = regions.mapNotNull { region ->
                val found = timestamps.binarySearch(region.startUs)
                val start = timestamps.getOrNull(if (found >= 0) found else -found - 1) ?: return@mapNotNull null
                val end = minOf(region.endUs, frames.durationUs)
                if (start < end) MosaicReviewInterval(start, end) else null
            }.sortedBy { it.startUs }
            val merged = mutableListOf<MosaicReviewInterval>()
            for (span in spans) {
                val last = merged.lastOrNull()
                if (last != null && span.startUs <= last.endUs) {
                    merged[merged.lastIndex] = last.copy(endUs = maxOf(last.endUs, span.endUs))
                } else merged += span
            }
            return MosaicReviewIndex(regions, merged)
        }
    }
}
