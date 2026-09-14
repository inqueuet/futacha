package com.valoser.futacha.shared.ui.image

import okio.BufferedSource

/** Consumes a peek, never the caller's image source or compressed pixel data. */
internal fun hasCompatAnimatedPngHeader(source: BufferedSource, maxBytes: Long = 32L * 1024 * 1024): Boolean {
    if (!source.request(8) || source.readLong() != 0x89504e470d0a1a0auL.toLong()) return false
    var consumed = 8L
    // A corrupt sequence of empty chunks must not spend millions of iterations
    // in a decoder factory before the bounded body decoder gets a chance to run.
    repeat(4096) {
        if (!source.request(8)) return false
        val length = source.readInt().toLong() and 0xffffffffL
        val type = source.readUtf8(4)
        consumed += 8
        // PNG requires acTL before IDAT. Static PNG detection stops here,
        // without buffering its IDAT payload merely to identify the format.
        if (type == "IDAT" || type == "IEND") return false
        require(length + 4 <= maxBytes - consumed) { "Animated PNG header exceeds $maxBytes bytes" }
        if (!source.request(length + 4)) return false
        if (type == "acTL") return length == 8L
        source.skip(length + 4)
        consumed += length + 4
    }
    error("Animated PNG has too many header chunks")
}

/** Extended WebP declares animation in the first VP8X chunk's flags. */
internal fun hasCompatAnimatedWebpHeader(source: BufferedSource): Boolean {
    if (!source.request(12) || source.readUtf8(4) != "RIFF") return false
    source.skip(4)
    if (source.readUtf8(4) != "WEBP" || !source.request(18)) return false
    if (source.readUtf8(4) != "VP8X" || source.readIntLe() != 10) return false
    return source.readByte().toInt() and 0x02 != 0
}
