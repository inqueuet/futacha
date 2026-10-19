package com.valoser.futacha.shared.audio.voicevox

internal data class VoicePcm(val offset: Int, val bytes: Int, val sampleRate: Int, val channels: Int)

internal fun voicePcm(wav: ByteArray): VoicePcm {
    fun word(at: Int): Int = (wav[at].toInt() and 255) or ((wav[at + 1].toInt() and 255) shl 8)
    fun integer(at: Int): Long = word(at).toLong() or (word(at + 2).toLong() shl 16)
    fun label(at: Int) = wav.copyOfRange(at, at + 4).decodeToString()
    require(wav.size >= 44 && label(0) == "RIFF" && label(8) == "WAVE") { "音声ファイルの形式が不正です" }
    require(integer(4) + 8 == wav.size.toLong())
    var offset = 12
    var rate = 0
    var channels = 0
    while (offset + 8 <= wav.size) {
        val size = integer(offset + 4)
        require(size <= wav.size.toLong() - offset - 8)
        when (label(offset)) {
            "fmt " -> {
                require(size >= 16 && word(offset + 8) == 1 && word(offset + 22) == 16)
                channels = word(offset + 10)
                rate = integer(offset + 12).toInt()
                require(channels == 1 && rate == 24000)
            }
            "data" -> {
                require(rate == 24000 && channels == 1 && size > 0 && size % 2 == 0L)
                return VoicePcm(offset + 8, size.toInt(), rate, channels)
            }
        }
        offset += 8 + size.toInt() + (size.toInt() and 1)
    }
    error("音声のサンプルがありません")
}
