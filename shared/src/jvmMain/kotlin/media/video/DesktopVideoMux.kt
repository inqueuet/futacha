package com.valoser.futacha.shared.media.video

import org.bytedeco.ffmpeg.avformat.*
import org.bytedeco.ffmpeg.avcodec.*
import org.bytedeco.ffmpeg.avutil.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.Pointer
import kotlinx.coroutines.*
import java.io.File
import org.bytedeco.javacpp.Loader

private fun checkAv(result: Int) { check(result >= 0) { "動画の保存に失敗しました (FFmpeg $result)" } }
private fun openMuxInput(path: String): AVFormatContext {
    val context = AVFormatContext(null as Pointer?)
    checkAv(avformat_open_input(context, path, null, null))
    try { checkAv(avformat_find_stream_info(context, null as org.bytedeco.javacpp.PointerPointer<*>?)); return context }
    catch (failure: Throwable) { avformat_close_input(context); throw failure }
}

internal suspend fun muxDesktopVideoTimeline(encodedPath: String, originalPath: String, output: String, info: VideoEditInfo) {
    val video = openMuxInput(encodedPath)
    var convertedAudio: File? = null
    var original = try { openMuxInput(originalPath) } catch (failure: Throwable) { avformat_close_input(video); throw failure }
    try {
        val audio = (0 until original.nb_streams()).firstOrNull { original.streams(it).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO }
        if (audio != null && original.streams(audio).codecpar().codec_id() !in setOf(AV_CODEC_ID_AAC, AV_CODEC_ID_OPUS, AV_CODEC_ID_MP3, AV_CODEC_ID_ALAC)) {
            convertedAudio = File.createTempFile("audio-", ".m4a", File(output).parentFile)
            transcodeDesktopAudio(originalPath, convertedAudio.absolutePath)
            avformat_close_input(original)
            original = openMuxInput(convertedAudio.absolutePath)
        }
    } catch (failure: Throwable) {
        avformat_close_input(video); avformat_close_input(original); convertedAudio?.delete(); throw failure
    }
    val out = AVFormatContext(null as Pointer?)
    val packet = av_packet_alloc()
    var io: AVIOContext? = null
    try {
        checkAv(avformat_alloc_output_context2(out, null, "mp4", output))
        val videoIndex = (0 until video.nb_streams()).first { video.streams(it).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO }
        val inputVideo = video.streams(videoIndex)
        val outputVideo = avformat_new_stream(out, null) ?: error("映像トラックを作成できません")
        checkAv(avcodec_parameters_copy(outputVideo.codecpar(), inputVideo.codecpar()))
        outputVideo.codecpar().codec_tag(0)
        outputVideo.time_base().num(1).den(1_000_000)
        val audioIndex = (0 until original.nb_streams()).firstOrNull { original.streams(it).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO }
        val inputAudio = audioIndex?.let { original.streams(it) }
        val outputAudio = inputAudio?.let { source ->
            require(source.codecpar().codec_id() in setOf(AV_CODEC_ID_AAC, AV_CODEC_ID_OPUS, AV_CODEC_ID_MP3, AV_CODEC_ID_ALAC)) {
                "この動画の音声形式をMP4に保存できません"
            }
            val stream = avformat_new_stream(out, null) ?: error("音声トラックを作成できません")
            checkAv(avcodec_parameters_copy(stream.codecpar(), source.codecpar()))
            stream.codecpar().codec_tag(0)
            stream.time_base().num(source.time_base().num()).den(source.time_base().den())
            stream
        }
        io = AVIOContext(null as Pointer?)
        checkAv(avio_open(io, output, AVIO_FLAG_WRITE)); out.pb(io)
        checkAv(avformat_write_header(out, null as AVDictionary?))
        val micros = AVRational().num(1).den(1_000_000)
        val start = info.frames.timeAt(0)
        var index = 0
        while (av_read_frame(video, packet) >= 0) {
            currentCoroutineContext().ensureActive()
            try {
                if (packet.stream_index() != videoIndex) continue
                check(index < info.frames.size) { "保存フレーム数が元動画と一致しません" }
                val time = info.frames.timeAt(index)
                val end = if (index + 1 < info.frames.size) info.frames.timeAt(index + 1) else info.frames.durationUs
                packet.pts(time - start).dts(time - start).duration(end - time)
                av_packet_rescale_ts(packet, micros, outputVideo.time_base())
                packet.stream_index(outputVideo.index()).pos(-1)
                checkAv(av_interleaved_write_frame(out, packet)); index++
            } finally { av_packet_unref(packet) }
        }
        check(index == info.frames.size) { "保存フレーム数が元動画と一致しません" }
        if (inputAudio != null && outputAudio != null) {
            val offset = av_rescale_q(start, micros, inputAudio.time_base())
            while (av_read_frame(original, packet) >= 0) {
                currentCoroutineContext().ensureActive()
                try {
                    if (packet.stream_index() != audioIndex) continue
                    if (packet.pts() != AV_NOPTS_VALUE) packet.pts(packet.pts() - offset)
                    if (packet.dts() != AV_NOPTS_VALUE) packet.dts(packet.dts() - offset)
                    av_packet_rescale_ts(packet, inputAudio.time_base(), outputAudio.time_base())
                    packet.stream_index(outputAudio.index()).pos(-1)
                    checkAv(av_interleaved_write_frame(out, packet))
                } finally { av_packet_unref(packet) }
            }
        }
        micros.close()
        checkAv(av_write_trailer(out))
    } finally {
        av_packet_free(packet)
        if (io != null) avio_closep(io)
        if (!out.isNull) avformat_free_context(out)
        avformat_close_input(video); avformat_close_input(original); convertedAudio?.delete()
    }
}

/** Preserve source timestamps when converting audio that MP4 cannot carry directly (e.g. Vorbis). */
private suspend fun transcodeDesktopAudio(input: String, output: String) = coroutineScope {
    val executable = Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java)
    val process = ProcessBuilder(executable, "-nostdin", "-v", "error", "-y", "-copyts", "-i", input,
        "-map", "0:a:0", "-vn", "-c:a", "aac", "-b:a", "192k", output).redirectErrorStream(true).start()
    val log = async(Dispatchers.IO) {
        val result = StringBuilder()
        process.inputStream.bufferedReader().use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer); if (count < 0) break
                result.append(buffer, 0, count)
                if (result.length > 8192) result.delete(0, result.length - 8192)
            }
        }
        result.toString()
    }
    try {
        withTimeout(15 * 60_000L) { while (process.isAlive) delay(50) }
        check(process.exitValue() == 0) { "音声を書き出せませんでした: ${log.await().takeLast(500)}" }
        log.await()
        Unit
    } finally { process.destroyForcibly(); withContext(NonCancellable + Dispatchers.IO) { process.waitFor() } }
}
