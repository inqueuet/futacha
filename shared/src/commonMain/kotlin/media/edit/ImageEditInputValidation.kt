package com.valoser.futacha.shared.media.edit

/** Do not silently turn an animated device image into a single edited frame. */
internal fun validateEditableImage(bytes: ByteArray) {
    require(bytes.size in 3..32 * 1024 * 1024) { "編集する画像は32MB以内にしてください" }
    fun byte(at: Int) = bytes[at].toInt() and 255
    fun tag(at: Int, value: String) = at >= 0 && at + value.length <= bytes.size && value.indices.all { byte(at + it) == value[it].code }
    fun u32(at: Int, little: Boolean): Long = (0..3).fold(0L) { value, i -> value or (byte(at + i).toLong() shl (if (little) i * 8 else (3 - i) * 8)) }
    if (byte(0) == 255 && byte(1) == 216 && byte(2) == 255) return
    if (bytes.size >= 8 && byte(0) == 137 && tag(1, "PNG\r\n\u001a\n")) {
        var p = 8
        while (p + 12 <= bytes.size) {
            val length = u32(p, false)
            require(length <= bytes.size - p - 12) { "PNGが壊れています" }
            require(!tag(p + 4, "acTL")) { "アニメーション画像には対応していません。静止画像を選んでください" }
            if (tag(p + 4, "IEND")) return
            p += length.toInt() + 12
        }
        error("PNGが壊れています")
    }
    if (bytes.size >= 12 && tag(0, "RIFF") && tag(8, "WEBP")) {
        var p = 12
        while (p + 8 <= bytes.size) {
            val length = u32(p + 4, true)
            require(length <= bytes.size - p - 8) { "WebPが壊れています" }
            require(!tag(p, "ANIM") && !(tag(p, "VP8X") && length > 0 && byte(p + 8) and 2 != 0)) {
                "アニメーション画像には対応していません。静止画像を選んでください"
            }
            p += length.toInt() + 8 + (length.toInt() and 1)
        }
        return
    }
    error("JPEG・PNG・静止WebPの画像を選んでください")
}
