package com.valoser.futacha.shared.ui.board

/** Track headers only, at most 256 KiB / 512 elements / eight nesting levels. No bitstream decoder. */
internal data class WebmPlaybackInfo(
    val videoCodec: String?, val audioCodec: String?,
    val width: Int?, val height: Int?, val channels: Int?, val sampleRate: Int?, val bitDepth: Int?
) {
    val mimeType: String get() {
        val video = when (videoCodec) { "V_VP8" -> "vp8"; "V_VP9" -> "vp9"; else -> return "video/webm" }
        val audio = when (audioCodec) { "A_OPUS" -> "opus"; "A_VORBIS" -> "vorbis"; null -> null; else -> return "video/webm" }
        return "video/webm; codecs=\"${listOfNotNull(video, audio).joinToString(", ")}\""
    }
    fun mediaInfo() = VideoMediaInfo(videoCodec = videoCodec, width = width, height = height,
        audioCodec = audioCodec, sampleRate = sampleRate, channelCount = channels)
}

internal const val WEBM_TRACK_PROBE_BYTES = 256 * 1024

/** Unknown/truncated header is not evidence of codec support. WebKit still validates the file. */
internal fun readWebmPlaybackInfo(prefix: ByteArray): WebmPlaybackInfo? = runCatching {
    require(prefix.size in 4..WEBM_TRACK_PROBE_BYTES)
    val scan = WebmTrackScan(prefix)
    scan.read()
}.getOrNull()

private class WebmTrackScan(val bytes: ByteArray) {
    var elements = 0
    data class Element(val id: Long, val start: Int, val end: Int)
    fun vint(offset: Int, id: Boolean): Pair<Long, Int> {
        require(offset in bytes.indices)
        val first = bytes[offset].toInt() and 255
        require(first != 0)
        var length = 1
        while (first and (0x80 shr (length - 1)) == 0) length++
        require(length <= if (id) 4 else 8)
        require(offset <= bytes.size - length)
        var value = (if (id) first else first and (0xff shr length)).toLong()
        for (i in 1 until length) value = (value shl 8) or (bytes[offset + i].toLong() and 255)
        return (if (!id && value == (1L shl (7 * length)) - 1) -1 else value) to length
    }
    fun element(offset: Int, end: Int): Element {
        require(++elements <= 512)
        val (id, idLength) = vint(offset, true)
        val (size, sizeLength) = vint(offset + idLength, false)
        val start = offset + idLength + sizeLength
        require(start <= end && size >= -1)
        // The prefix may end in the segment/cluster body. Never allocate its declared length.
        val limit = if (size == -1L) end else minOf(end.toLong(), start + size).toInt()
        return Element(id, start, limit)
    }
    fun children(parent: Element, depth: Int = 0, visit: (Element) -> Unit) {
        require(depth <= 8)
        var offset = parent.start
        while (offset < parent.end) {
            val child = element(offset, parent.end)
            require(child.end > offset)
            visit(child)
            offset = child.end
        }
    }
    fun uint(e: Element): Int? {
        if (e.end - e.start !in 1..4) return null
        var n = 0L
        for (i in e.start until e.end) n = (n shl 8) or (bytes[i].toLong() and 255)
        return n.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }
    fun text(e: Element): String? = if (e.end - e.start in 1..64) bytes.decodeToString(e.start, e.end) else null
    fun sampleRate(e: Element): Int? {
        var bits = 0L
        for (i in e.start until e.end) { if (i - e.start >= 8) return null; bits = (bits shl 8) or (bytes[i].toLong() and 255) }
        val value = when (e.end - e.start) { 4 -> Float.fromBits(bits.toInt()).toDouble(); 8 -> Double.fromBits(bits); else -> return null }
        return value.takeIf { it.isFinite() && it in 1.0..768000.0 }?.toInt()
    }
    fun read(): WebmPlaybackInfo? {
        val header = element(0, bytes.size)
        if (header.id != 0x1a45dfa3L) return null
        var doc: String? = null
        children(header) { if (it.id == 0x4282L) doc = text(it) }
        if (doc != "webm") return null
        var result: WebmPlaybackInfo? = null
        children(Element(0, header.end, bytes.size)) { segment ->
            if (segment.id == 0x18538067L) children(segment, 1) { tracks ->
                if (tracks.id == 0x1654ae6bL) {
                    var video: String? = null; var audio: String? = null
                    var width: Int? = null; var height: Int? = null; var channels: Int? = null
                    var rate: Int? = null; var bits: Int? = null
                    children(tracks, 2) { entry -> if (entry.id == 0xaeL) {
                        var type: Int? = null; var codec: String? = null
                        var w: Int? = null; var h: Int? = null; var ch: Int? = null
                        var sr: Int? = null; var bd: Int? = null
                        children(entry, 3) { field -> when (field.id) {
                            0x83L -> type = uint(field)
                            0x86L -> codec = text(field)
                            0xe0L -> children(field, 4) { v -> when (v.id) {
                                0xb0L -> w = uint(v); 0xbaL -> h = uint(v)
                                0x55b0L -> children(v, 5) { if (it.id == 0x55b2L) bd = uint(it) }
                            } }
                            0xe1L -> children(field, 4) { a -> when (a.id) {
                                0x9fL -> ch = uint(a); 0xb5L -> sr = sampleRate(a)
                            } }
                        } }
                        if (type == 1 && video == null) { video = codec; width = w; height = h; bits = bd }
                        if (type == 2 && audio == null) { audio = codec; channels = ch ?: 1; rate = sr }
                    } }
                    result = WebmPlaybackInfo(video, audio, width, height, channels, rate, bits)
                }
            }
        }
        return result
    }
}
