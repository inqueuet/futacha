package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

/** Immutable encoded source and poster; each visible painter owns its own decoding session. */
internal class IosGifImage(val bytes: ByteArray, private val poster: coil3.Image) : coil3.Image {
    override val width get() = poster.width
    override val height get() = poster.height
    override val size get() = poster.size + bytes.size
    override val shareable = true
    override fun draw(canvas: coil3.Canvas) = poster.draw(canvas)
}

internal fun decodeIosGif(bytes: ByteArray): DecodeResult {
    requireSafeCompatAnimatedImageCanvas(bytes, CompatAnimatedImageFormat.GIF)
    Data.makeFromBytes(bytes).useGifNative { data ->
        Codec.makeFromData(data).useGifNative { codec ->
            val bitmap = codec.readPixels()
            val poster = bitmap.asImage()
            return DecodeResult(if (codec.frameCount > 1) IosGifImage(bytes, poster) else poster, false)
        }
    }
}

internal class IosGifDecoderFactory : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val peek = result.source.source().peek()
        val gif = peek.request(6) && peek.readUtf8(6) in setOf("GIF87a", "GIF89a")
        if (!gif) return null
        return Decoder { options.withIosImageProcessingPermit {
            decodeIosGif(result.source.source().use { it.readBoundedCompatAnimatedImageBytes() })
        } }
    }
}

internal inline fun <T : org.jetbrains.skia.impl.Managed, R> T.useGifNative(block: (T) -> R): R =
    try { block(this) } finally { close() }
