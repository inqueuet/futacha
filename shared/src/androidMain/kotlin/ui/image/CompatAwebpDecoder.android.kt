package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import com.github.penfeizhou.animation.webp.WebPDrawable
import java.nio.ByteBuffer

/** Animated WebP fallback for API 26/27, where Android ImageDecoder is absent. */
internal class CompatAwebpDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readBoundedCompatAnimatedImageBytes() }
        requireSafeCompatAnimatedImageCanvas(bytes, CompatAnimatedImageFormat.WEBP)
        val drawable = WebPDrawable(object : ByteBufferLoader() {
            override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
        }).apply {
            setAutoPlay(false)
        }
        val isSampled = drawable.applyCompatRequestedSize(options)
        return DecodeResult(drawable.asImage(), isSampled = isSampled)
    }

    internal class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val isAnimatedWebp = result.source.source().peek().use { hasCompatAnimatedWebpHeader(it) }
            return CompatAwebpDecoder(result.source, options).takeIf { isAnimatedWebp }
        }
    }
}
