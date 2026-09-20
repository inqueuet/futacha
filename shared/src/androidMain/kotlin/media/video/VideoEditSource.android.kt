package com.valoser.futacha.shared.media.video

import android.media.MediaExtractor
import android.media.MediaFormat
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal actual suspend fun inspectDeviceVideo(path: String): VideoEditInfo = withContext(AppDispatchers.io) {
    require(path.startsWith('/')) { "端末内の動画を選択してください" }
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
        val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
        val videoTracks = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        require(videoTracks.size == 1) { "映像トラックが1つの動画を選択してください" }
        require(formats.count { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } <= 1) { "音声トラックが複数ある動画はまだ編集できません" }
        val track = videoTracks.single()
        val format = formats[track]
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
        val normalizedRotation = ((rotation % 360) + 360) % 360
        require(normalizedRotation in setOf(0, 90, 180, 270)) { "この動画の回転情報に対応していません" }
        val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0
        val hdr = format.containsKey(MediaFormat.KEY_COLOR_TRANSFER) && format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) in
            setOf(MediaFormat.COLOR_TRANSFER_ST2084, MediaFormat.COLOR_TRANSFER_HLG)
        val frames = readVideoMp4Timeline(path, duration) ?: run {
            extractor.selectTrack(track)
            val timestamps = ArrayList<Long>()
            while (extractor.sampleTime >= 0) {
                currentCoroutineContext().ensureActive()
                require(timestamps.size < VideoFrameIndex.MAX_FRAMES) { "この動画は長すぎるため編集できません" }
                timestamps += extractor.sampleTime
                if (!extractor.advance()) break
            }
            buildVideoFrameIndex(timestamps, duration)
        }
        VideoEditInfo(
            if (normalizedRotation % 180 == 0) width else height,
            if (normalizedRotation % 180 == 0) height else width,
            frames,
            formats.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }, hdr, normalizedRotation
        )
    } finally { extractor.release() }
}
