package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.apng.decode.APNGParser
import com.github.penfeizhou.animation.io.StreamReader
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import java.nio.ByteBuffer

/**
 * APNG support used by the reference compatibility client.
 *
 * Android's ImageDecoder covers GIF and animated WebP on API 28+, but APNG is
 * not part of that API. Keep this decoder ahead of the normal bitmap decoders
 * so an APNG is not accidentally treated as a static PNG.
 */
internal class CompatApngDecoder(
    private val source: ImageSource,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readBoundedCompatAnimatedImageBytes() }
        val drawable = APNGDrawable(object : ByteBufferLoader() {
            override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
        }).apply {
            // The reference client opens the first frame and only animates
            // when the viewer requests it.
            setAutoPlay(false)
        }
        return DecodeResult(drawable.asImage(), isSampled = false)
    }

    internal class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val isApng = runCatching {
                APNGParser.isAPNG(
                    StreamReader(result.source.source().peek().inputStream())
                )
            }.getOrDefault(false)
            return CompatApngDecoder(result.source).takeIf { isApng }
        }
    }
}
