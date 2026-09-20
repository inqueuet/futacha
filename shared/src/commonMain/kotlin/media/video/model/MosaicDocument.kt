package com.valoser.futacha.shared.media.video.model


internal enum class MosaicShape { RECTANGLE, ELLIPSE }
internal enum class MosaicStyle { PIXELATE, BLACK }

/** Coordinates are normalized to the upright video, excluding the player's letterboxing. */
internal data class MosaicBounds(val centerX: Float, val centerY: Float, val width: Float, val height: Float) {
    fun constrained(): MosaicBounds {
        val w = width.coerceIn(0.025f, 1f)
        val h = height.coerceIn(0.025f, 1f)
        return MosaicBounds(centerX.coerceIn(w / 2, 1 - w / 2), centerY.coerceIn(h / 2, 1 - h / 2), w, h)
    }
    fun contains(x: Float, y: Float, shape: MosaicShape): Boolean {
        val dx = (x - centerX) / (width / 2)
        val dy = (y - centerY) / (height / 2)
        return if (shape == MosaicShape.ELLIPSE) dx * dx + dy * dy <= 1 else kotlin.math.abs(dx) <= 1 && kotlin.math.abs(dy) <= 1
    }
    fun interpolate(other: MosaicBounds, fraction: Float): MosaicBounds {
        fun mix(a: Float, b: Float) = a + (b - a) * fraction
        return MosaicBounds(mix(centerX, other.centerX), mix(centerY, other.centerY), mix(width, other.width), mix(height, other.height))
    }
    companion object { val DEFAULT = MosaicBounds(0.5f, 0.5f, 0.3f, 0.3f) }
}

internal data class MosaicKeyframe(val timeUs: Long, val bounds: MosaicBounds)

internal data class MosaicRegion(
    val id: String,
    val shape: MosaicShape = MosaicShape.RECTANGLE,
    val startUs: Long = 0,
    val endUs: Long,
    /** Block width relative to the shorter video edge; independent of preview resolution. */
    val blockFraction: Float = 0.035f,
    val interpolate: Boolean = true,
    val keyframes: List<MosaicKeyframe> = listOf(MosaicKeyframe(0, MosaicBounds.DEFAULT)),
    val trajectory: MosaicTrajectory? = null,
    val label: String? = null,
    /** Black overlay applied after pixelation. 0 = mosaic, 1 = opaque black. */
    val darkness: Float = 0f,
    val style: MosaicStyle = MosaicStyle.PIXELATE,
    val masks: List<MosaicMaskKeyframe> = emptyList(),
    val maskMargin: Int = 2
) {
    init {
        require(startUs >= 0 && endUs > startUs)
        require(blockFraction.isFinite() && blockFraction in 0.005f..0.2f)
        require(darkness.isFinite() && darkness in 0f..1f)
        require(maskMargin in 0..MosaicMask.MAX_MARGIN)
        require(masks.size<=MosaicMask.MAX_SAMPLES && masks.all { it.timeUs>=0 } && masks.zipWithNext().all { (a,b)->a.timeUs<b.timeUs })
        require(keyframes.isNotEmpty() && keyframes.zipWithNext().all { (a,b) -> a.timeUs < b.timeUs })
        require(keyframes.all { it.timeUs >= 0 && it.bounds.let { b -> listOf(b.centerX,b.centerY,b.width,b.height).all(Float::isFinite) && b == b.constrained() } })
    }
    fun activeAt(timeUs: Long) = timeUs >= startUs && timeUs < endUs
    fun maskAt(timeUs: Long): MosaicMask? {
        val found=masks.binarySearchBy(timeUs) { it.timeUs }
        return masks.getOrNull(if(found>=0)found else -found-2)?.mask
    }
    fun withMask(timeUs: Long, mask: MosaicMask?): MosaicRegion = copy(masks=(masks.filterNot { it.timeUs==timeUs }+MosaicMaskKeyframe(timeUs,mask)).sortedBy { it.timeUs })
    fun hasEmptyActiveMask(): Boolean = masks.indices.any { i ->
        masks[i].mask?.isEmpty==true && maxOf(startUs,masks[i].timeUs)<minOf(endUs,masks.getOrNull(i+1)?.timeUs?:endUs)
    }
    fun boundsAt(timeUs: Long): MosaicBounds {
        // Manual corrections take precedence at their sample. Re-tracking propagates them.
        val trackedTime = trajectory?.takeIf { timeUs in it.firstTimeUs..it.lastTimeUs }?.let { it.timeAt(it.indexAt(timeUs)) }
        val found = keyframes.binarySearchBy(trackedTime ?: timeUs) { it.timeUs }
        if (found >= 0) return keyframes[found].bounds
        if (trackedTime != null) return trajectory!!.boundsAt(trackedTime)
        val insertion = -found - 1
        val next = if (insertion == keyframes.size) -1 else insertion
        if (next == 0) return keyframes.first().bounds
        if (next < 0) return keyframes.last().bounds
        val before = keyframes[next - 1]; val after = keyframes[next]
        if (!interpolate) return before.bounds
        return before.bounds.interpolate(after.bounds, (timeUs - before.timeUs).toFloat() / (after.timeUs - before.timeUs))
    }
    fun withBounds(timeUs: Long, bounds: MosaicBounds): MosaicRegion {
        val frame = MosaicKeyframe(timeUs.coerceAtLeast(0), bounds.constrained())
        return copy(keyframes = (keyframes.filterNot { it.timeUs == frame.timeUs } + frame).sortedBy { it.timeUs })
    }
    fun withoutKeyframe(timeUs: Long) = if (keyframes.size == 1) this else copy(keyframes = keyframes.filterNot { it.timeUs == timeUs })
}

internal data class MosaicDocument(val regions: List<MosaicRegion> = emptyList(), val review: MosaicReview? = null) {
    init {
        require(regions.size <= MAX_REGIONS && regions.distinctBy { it.id }.size == regions.size)
        require(regions.sumOf { it.trajectory?.size?.toLong() ?: 0L } <= MosaicTrajectory.MAX_SAMPLES) { "追尾データの合計上限です。短い区間で追尾してください。" }
        require(regions.sumOf { it.masks.size.toLong() }<=MosaicMask.MAX_SAMPLES) { "輪郭データの上限です。短い区間で抽出してください。" }
    }
    fun update(id: String, change: (MosaicRegion) -> MosaicRegion) = copy(regions = regions.map { if (it.id == id) change(it) else it }, review = review?.copy(confirmed = false))
    companion object { const val MAX_REGIONS = 16 }
}

/** One history entry per gesture; slider/drag previews must not consume all undo slots. */
internal class MosaicHistory(initial: MosaicDocument = MosaicDocument()) {
    var current = initial; private set
    private val undo = ArrayDeque<MosaicDocument>()
    private val redo = ArrayDeque<MosaicDocument>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun commit(value: MosaicDocument) {
        if (current == value) return
        undo.addLast(current); if (undo.size > 50) undo.removeFirst()
        current = value; redo.clear()
        // A few long analyses must not multiply native-sized trajectories across fifty undo entries.
        while (undo.size > 1 && retainedTrajectoryBytes() > 32L * 1024 * 1024) undo.removeFirst()
    }
    fun undo(): MosaicDocument { if (canUndo) { redo.addLast(current); current = undo.removeLast() }; return current }
    fun redo(): MosaicDocument { if (canRedo) { undo.addLast(current); current = redo.removeLast() }; return current }
    private fun retainedTrajectoryBytes(): Long {
        val regions=(undo.asSequence()+sequenceOf(current)).flatMap { it.regions.asSequence() }.toList()
        return regions.mapNotNull { it.trajectory }.distinct().sumOf { it.byteSize } +
            regions.flatMap { it.masks }.mapNotNull { it.mask }.distinct().sumOf { it.byteSize*2 } // Includes cached dilation.
    }
}

/** Real presentation timestamps support frame stepping even for variable frame rate input. */
internal class VideoFrameIndex(timestampsUs: LongArray, val durationUs: Long) {
    private val samples = timestampsUs.copyOf()
    val timestampsUs: LongArray get() = samples.copyOf()
    val size: Int get() = samples.size
    fun timeAt(index: Int): Long = samples[index]
    init {
        require(samples.isNotEmpty() && samples.size <= MAX_FRAMES)
        require(samples.first() >= 0 && durationUs > samples.last())
        require((1 until samples.size).all { samples[it - 1] < samples[it] })
    }
    fun atOrBefore(timeUs: Long): Long {
        val index = samples.binarySearch(timeUs)
        return samples[if (index >= 0) index else (-index - 2).coerceAtLeast(0)]
    }
    fun step(timeUs: Long, forward: Boolean): Long {
        val index = samples.binarySearch(atOrBefore(timeUs))
        return samples[(index + if (forward) 1 else -1).coerceIn(0, samples.lastIndex)]
    }
    fun endAfter(timeUs: Long): Long {
        val next = step(timeUs, true)
        return if (next <= timeUs) durationUs else next
    }
    companion object { const val MAX_FRAMES = 500_000 }
}
