package com.valoser.futacha.shared.media.video.model

/** Immutable packed samples. Histories share these arrays; playback uses binary search, not a scan. */
internal class MosaicTrajectory private constructor(
    private val times: LongArray,
    private val boxes: FloatArray,
    private val uncertain: BooleanArray
) {
    val size get() = times.size
    val byteSize get() = size.toLong() * 25
    val firstTimeUs get() = times.first()
    val lastTimeUs get() = times.last()
    fun timeAt(index: Int) = times[index]
    fun uncertainAt(index: Int) = uncertain[index]
    fun indexAt(timeUs: Long): Int {
        val found = times.binarySearch(timeUs)
        return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
    }
    fun boundsAt(timeUs: Long) = boundsAtIndex(indexAt(timeUs))
    fun boundsAtIndex(index: Int): MosaicBounds {
        val i = index * 4
        return MosaicBounds(boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3])
    }

    class Builder {
        private var times = LongArray(256)
        private var boxes = FloatArray(1024)
        private var flags = BooleanArray(256)
        var size = 0; private set
        fun add(timeUs: Long, bounds: MosaicBounds, uncertain: Boolean = false) {
            require(timeUs >= 0 && (size == 0 || timeUs > times[size - 1]))
            require(bounds == bounds.constrained() && listOf(bounds.centerX, bounds.centerY, bounds.width, bounds.height).all(Float::isFinite))
            check(size < MAX_SAMPLES) { "追尾データの上限です。短い区間に分けて解析してください。" }
            if (size == times.size) {
                val capacity = minOf(MAX_SAMPLES, size * 2)
                times = times.copyOf(capacity); boxes = boxes.copyOf(capacity * 4); flags = flags.copyOf(capacity)
            }
            times[size] = timeUs; flags[size] = uncertain
            val i = size * 4
            boxes[i] = bounds.centerX; boxes[i + 1] = bounds.centerY; boxes[i + 2] = bounds.width; boxes[i + 3] = bounds.height
            size++
        }
        fun build(): MosaicTrajectory {
            require(size > 0)
            return MosaicTrajectory(times.copyOf(size), boxes.copyOf(size * 4), flags.copyOf(size))
        }
    }
    companion object { const val MAX_SAMPLES = 120_000 }
}

internal data class MosaicAnalysisIssue(val timeUs: Long, val endUs: Long, val reason: String)

/** A completed analysis still requires explicit visual review, including stretches with no detections. */
internal data class MosaicReview(
    val startUs: Long,
    val endUs: Long,
    val frameCount: Int,
    val description: String,
    val issues: List<MosaicAnalysisIssue>,
    val confirmed: Boolean = false
)
