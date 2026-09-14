package com.valoser.futacha.shared.ui.image

import android.os.Build
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.gif.isGif
import coil3.request.Options
import okio.Buffer

/**
 * Mirrors 1.apk's GIF fallback: distinguish one-frame GIFs up front, and if
 * ImageDecoder flattens a multi-frame GIF to a Bitmap, retry through Movie.
 */
internal class CompatFallbackGifDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {
    override suspend fun decode(): DecodeResult {
        // Bound the input before scanning frame descriptors: peeking an
        // arbitrarily long extension block can otherwise buffer the whole file.
        val bytes = source.source().use { it.readBoundedCompatAnimatedImageBytes() }
        requireSafeCompatAnimatedImageCanvas(bytes, CompatAnimatedImageFormat.GIF)
        val fileSystem = source.fileSystem
        fun freshSource() = ImageSource(Buffer().write(bytes), fileSystem)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return freshSource().use { GifDecoder(it, options).decode() }
        }
        if (!hasMultipleGifFrames(Buffer().write(bytes))) {
            return freshSource().use { AnimatedImageDecoder(it, options).decode() }
        }

        val primary = freshSource().use { AnimatedImageDecoder(it, options).decode() }
        if (primary.image !is BitmapImage) return primary
        return freshSource().use { GifDecoder(it, options).decode() }
    }

    internal class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            if (!DecodeUtils.isGif(result.source.source())) return null
            return CompatFallbackGifDecoder(result.source, options)
        }
    }
}
