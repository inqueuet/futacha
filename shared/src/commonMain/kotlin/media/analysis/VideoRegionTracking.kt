package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import kotlinx.coroutines.*

/** Return a complete edit, or throw without changing the caller's document/history. */
internal suspend fun trackVideoRegion(
    source: VideoEditSource, info: VideoEditInfo, document: MosaicDocument, regionId: String,
    anchorUs: Long, forward: Boolean, progress: (String, Float?) -> Unit
): MosaicDocument = withContext(Dispatchers.Default) {
    currentCoroutineContext().ensureActive(); source.checkActive()
    val region = document.regions.first { it.id == regionId }
    require(region.activeAt(anchorUs) && info.frames.atOrBefore(anchorUs) == anchorUs) {
        "追尾を開始するコマを適用範囲内に移動してください"
    }
    val start = if (forward) anchorUs else region.startUs
    val end = if (forward) region.endUs else info.frames.endAfter(anchorUs)
    val request = VideoAnalysisRequest(info.frames, start, end, 640, false)
    val count = request.endIndex - request.firstIndex
    require(count in 1..MosaicTrajectory.MAX_SAMPLES) { "短い区間に分けて追尾してください" }
    val samples = arrayOfNulls<TrackingStep>(count)
    val times = info.frames.timestampsUs
    val anchors = region.keyframes.associateBy { it.timeUs }
    OpticalFlowTracker().use { tracker ->
        var previous: AnalysisFrame? = null
        var processed = 0
        suspend fun consume(frame: AnalysisFrame) {
            currentCoroutineContext().ensureActive(); source.checkActive()
            val manual = anchors[frame.timeUs]
            val step = if (previous == null || manual != null) {
                val bounds = manual?.bounds ?: region.boundsAt(anchorUs)
                tracker.seed(frame, bounds)
                TrackingStep(bounds, false)
            } else tracker.step(frame, sceneChanged(previous, frame))
            currentCoroutineContext().ensureActive(); source.checkActive()
            samples[times.binarySearch(frame.timeUs) - request.firstIndex] = step
            previous = frame; processed++
            if (processed == 1 || processed % 4 == 0 || processed == count) {
                progress(if (forward) "前方へ追尾しています" else "逆方向へ追尾しています", processed.toFloat() / count)
            }
        }
        if (forward) source.analysisFrames(info, start, end, includeRgb = false, consume = ::consume)
        else {
            // Native readers run forward; only one small grayscale chunk is retained for reverse flow.
            // All chunks share one reader, so its timeline and decoder are set up once per run.
            val ranges = ArrayList<Pair<Long, Long>>()
            var chunkEnd = request.endIndex
            while (chunkEnd > request.firstIndex) {
                val chunkStart = maxOf(request.firstIndex, chunkEnd - 32)
                ranges += times[chunkStart] to (times.getOrNull(chunkEnd) ?: info.frames.durationUs)
                chunkEnd = chunkStart
            }
            source.analysisFrameChunks(info, ranges, includeRgb = false) { frames ->
                for (frame in frames.asReversed()) consume(frame)
            }
        }
    }
    currentCoroutineContext().ensureActive(); source.checkActive()
    val builder = MosaicTrajectory.Builder()
    val old = region.trajectory
    if (old != null) for (i in 0 until old.size) {
        if (old.timeAt(i) < start) builder.add(old.timeAt(i), old.boundsAtIndex(i), old.uncertainAt(i))
    }
    val issues = VideoAnalysisIssues()
    samples.forEachIndexed { i, sample ->
        val step = checkNotNull(sample) { "追尾フレームが欠落しました" }
        val time = times[request.firstIndex + i]
        builder.add(time, step.bounds, step.uncertain)
        if (step.uncertain) issues.add(time, info.frames.endAfter(time), "追尾が不確かです。枠を修正して再追尾してください")
    }
    if (old != null) for (i in 0 until old.size) {
        if (old.timeAt(i) >= end) builder.add(old.timeAt(i), old.boundsAtIndex(i), old.uncertainAt(i))
    }
    document.update(regionId) { it.copy(trajectory = builder.build()) }
        .copy(review = mergeVideoReview(document.review, start, end, count, "手動指定範囲の追尾", issues.items))
        .also { currentCoroutineContext().ensureActive(); source.checkActive() }
}

/** Bounded navigation hints. Approval always covers the entire analysed interval. */
internal class VideoAnalysisIssues {
    val items = mutableListOf<MosaicAnalysisIssue>()
    fun add(start: Long, end: Long, reason: String) {
        val index = items.indexOfLast { it.reason == reason }
        val last = items.getOrNull(index)
        if (last != null && start <= last.endUs) {
            items[index] = last.copy(timeUs = minOf(start, last.timeUs), endUs = maxOf(end, last.endUs))
        } else if (items.size < 200) items += MosaicAnalysisIssue(start, end, reason)
        else items[items.lastIndex] = items.last().copy(endUs = maxOf(end, items.last().endUs),
            reason = "確認箇所が多数あります。この区間を通して確認してください")
    }
}

internal fun mergeVideoReview(old: MosaicReview?, start: Long, end: Long, count: Int, description: String,
    issues: List<MosaicAnalysisIssue>): MosaicReview {
    val collector = VideoAnalysisIssues()
    (old?.issues.orEmpty() + issues).sortedBy { it.timeUs }.forEach { collector.add(it.timeUs, it.endUs, it.reason) }
    return MosaicReview(minOf(old?.startUs ?: start, start), maxOf(old?.endUs ?: end, end), count, description, collector.items)
}
