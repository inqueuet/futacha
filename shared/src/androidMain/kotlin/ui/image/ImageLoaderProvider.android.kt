package com.valoser.futacha.shared.ui.image

import coil3.decode.Decoder
import coil3.video.VideoFrameDecoder
import coil3.gif.GifDecoder

actual fun getPlatformDecoders(): List<Decoder.Factory> {
    return listOf(
        VideoFrameDecoder.Factory(),
        GifDecoder.Factory()
    )
}
