@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.valoser.futacha.shared.media.video

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.extractor.*
import androidx.media3.extractor.mp4.Mp4Extractor
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.RandomAccessFile

/** Same edit-list interpretation as Transformer, including API 26's otherwise shifted B-frame PTS.
 * Reads the MP4 sample tables only; payloads, prompts and audio do not need to be decoded. */
internal suspend fun readVideoMp4Timeline(path: String, fallbackDurationUs: Long): VideoFrameIndex? {
    val coroutine = currentCoroutineContext()
    RandomAccessFile(path, "r").use { file ->
        val data = DataReader { bytes, offset, length -> coroutine.ensureActive(); file.read(bytes, offset, length) }
        var input = DefaultExtractorInput(data, 0, file.length())
        val extractor = Mp4Extractor(Mp4Extractor.FLAG_DISABLE_ARTWORK_METADATA)
        try {
            if (!extractor.sniff(input)) return null
            input.resetPeekPosition()
            var complete = false; var videoId = -1; var durationUs = fallbackDurationUs
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput {
                    val discard = DiscardingTrackOutput()
                    if (type != C.TRACK_TYPE_VIDEO) return discard
                    require(videoId == -1) { "映像トラックが1つの動画を選択してください" }; videoId = id
                    return object : TrackOutput by discard {
                        override fun durationUs(value: Long) { if (value > 0) durationUs = value }
                    }
                }
                override fun endTracks() { complete = true }
                override fun seekMap(seekMap: SeekMap) = Unit
            })
            val position = PositionHolder()
            while (!complete) {
                coroutine.ensureActive()
                when (extractor.read(input, position)) {
                    Extractor.RESULT_END_OF_INPUT -> error("動画の時刻情報がありません")
                    Extractor.RESULT_SEEK -> {
                        require(position.position in 0..file.length())
                        file.seek(position.position); input = DefaultExtractorInput(data, position.position, file.length())
                    }
                }
            }
            require(videoId >= 0) { "動画に映像がありません" }
            val times = extractor.getSampleTimestampsUs(videoId)
            require(times.size in 1..VideoFrameIndex.MAX_FRAMES) { "この動画は長すぎるため編集できません" }
            return buildVideoFrameIndex(times.toList(), durationUs)
        } finally { extractor.release() }
    }
}
