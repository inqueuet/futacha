package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import com.valoser.futacha.shared.media.video.model.binarySearch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Decode a half-open interval on the editor's presentation timeline, never a nominal FPS. */
internal class VideoAnalysisRequest(
    val frames: VideoFrameIndex,
    startUs: Long,
    endUs: Long,
    val maximumEdge: Int,
    val includeRgb: Boolean
) {
    init {
        require(startUs >= 0 && endUs >= startUs && endUs <= frames.durationUs)
        require(maximumEdge in 1..1024)
    }
    val firstIndex = lowerBound(startUs)
    val endIndex = lowerBound(endUs)
    val isEmpty get() = firstIndex == endIndex
    private fun lowerBound(timeUs: Long): Int {
        var low = 0; var high = frames.size
        while (low < high) {
            val mid = low + (high - low) / 2
            if (frames.timeAt(mid) < timeUs) low = mid + 1 else high = mid
        }
        return low
    }
}

/** Missing/duplicate frames invalidate the operation; callers commit only after this returns. */
internal class VideoAnalysisTimingException(val expectedTimeUs: Long?, val actualTimeUs: Long) :
    IllegalStateException("解析中のフレーム時刻が元動画と一致しません。結果は反映していません。")

internal class VideoAnalysisCursor(private val request: VideoAnalysisRequest) {
    private var next = request.firstIndex
    fun accept(frame: AnalysisFrame) {
        val expected = if (next < request.endIndex) request.frames.timeAt(next) else null
        if (frame.timeUs != expected) throw VideoAnalysisTimingException(expected, frame.timeUs)
        check(maxOf(frame.width, frame.height) <= request.maximumEdge && frame.gray.size == frame.width * frame.height)
        check(if (request.includeRgb) frame.rgb.size == frame.width * frame.height * 3 else frame.rgb.isEmpty())
        next++
    }
    fun finish() { check(next == request.endIndex) { "解析中にフレームが欠落しました。結果は反映していません。" } }
}

/** Android's extractor and Media3 can apply MP4 edit-list offsets differently. Verify every
 * relative PTS before mapping; a missing sample must never be hidden by a constant offset. */
internal class VideoDecodeTimeline(decodedTimes: List<Long>, private val original: VideoFrameIndex) {
    private val times = decodedTimes.sorted().toLongArray()
    init {
        require(times.size == original.size && times.isNotEmpty() && times.first() >= 0) {
            "解析用のフレーム数が元動画と一致しません"
        }
        require(times.indices.all { i ->
            (i == 0 || times[i] > times[i - 1]) &&
                kotlin.math.abs((times[i] - times[0]) - (original.timeAt(i) - original.timeAt(0))) <= 1
        }) { "解析用のフレーム時刻が元動画と一致しません" }
    }
    fun decodedTime(index: Int): Long = times[index]
    fun originalTime(decodedTime: Long): Long {
        val index = times.binarySearch(decodedTime)
        check(index >= 0) { "デコーダーが未知のフレーム時刻を返しました" }
        return original.timeAt(index)
    }
}

internal suspend fun VideoEditSource.analysisFrames(
    info: VideoEditInfo,
    startUs: Long = info.frames.timeAt(0),
    endUs: Long = info.frames.durationUs,
    maximumEdge: Int = 640,
    includeRgb: Boolean = true,
    consume: suspend (AnalysisFrame) -> Unit
) = useFile { path -> readVideoAnalysisFrames(path, info, startUs, endUs, maximumEdge, includeRgb, consume) }

internal suspend fun readVideoAnalysisFrames(
    path: String,
    info: VideoEditInfo,
    startUs: Long = info.frames.timeAt(0),
    endUs: Long = info.frames.durationUs,
    maximumEdge: Int = 640,
    includeRgb: Boolean = true,
    consume: suspend (AnalysisFrame) -> Unit
) {
    require(com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath(path)) { "端末内の動画を選択してください" }
    require(!info.hdr) { "HDR動画の自動解析にはSDR変換が必要です" }
    val request = VideoAnalysisRequest(info.frames, startUs, endUs, maximumEdge, includeRgb)
    currentCoroutineContext().ensureActive()
    if (request.isEmpty) return
    val cursor = VideoAnalysisCursor(request)
    decodeDeviceVideoFrames(path, info, request) { frame ->
        currentCoroutineContext().ensureActive()
        cursor.accept(frame)
        consume(frame)
        currentCoroutineContext().ensureActive()
    }
    currentCoroutineContext().ensureActive()
    cursor.finish()
}

internal expect suspend fun decodeDeviceVideoFrames(
    path: String, info: VideoEditInfo, request: VideoAnalysisRequest, consume: suspend (AnalysisFrame) -> Unit
)
