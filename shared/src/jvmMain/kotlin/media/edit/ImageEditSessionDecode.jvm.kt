package com.valoser.futacha.shared.media.edit

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.pxOrElse
import com.valoser.futacha.shared.media.MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE
import okio.Buffer
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.ImageInfo
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.min

/** Up to this many source pixels the unchanged Skia decode is used (at most 64MB of pixels). */
internal const val JVM_IMAGE_EDIT_FULL_DECODE_PIXELS = 4096L * 4096

/**
 * Skia cannot decode at a reduced size, so a source ImageIO cannot subsample (WebP, HEIF, CMYK
 * JPEG and similar) is decoded at full resolution: up to this many pixels (256MB of pixels).
 */
internal const val JVM_IMAGE_EDIT_SKIA_DECODE_MAX_PIXELS = 8192L * 8192

internal actual fun ImageRequest.Builder.boundedImageEditDecoding(bytes: ByteArray): ImageRequest.Builder =
    decoderFactory(JvmBoundedImageEditDecoder.Factory())

/**
 * Skia decodes the whole image before scaling it. A larger source is read by ImageIO with
 * source subsampling (never the full-resolution raster), then oriented with Skia's own EXIF
 * reading and scaled to the requested edge. Formats ImageIO cannot read keep the Skia decode
 * up to [skiaDecodePixels] and are refused beyond it instead of exhausting memory.
 */
internal class JvmBoundedImageEditDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val fullDecodePixels: Long,
    private val skiaDecodePixels: Long = JVM_IMAGE_EDIT_SKIA_DECODE_MAX_PIXELS
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val maximumSide = maxOf(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })
        decodeSubsampledImageEditBitmap(bytes, maximumSide, fullDecodePixels)?.let {
            return DecodeResult(image = it.asImage(), isSampled = true)
        }
        requireSkiaImageEditDecodable(bytes, skiaDecodePixels)
        return SkiaImageDecoder(ImageSource(Buffer().write(bytes), options.fileSystem), options).decode()
    }

    class Factory(
        private val fullDecodePixels: Long = JVM_IMAGE_EDIT_FULL_DECODE_PIXELS,
        private val skiaDecodePixels: Long = JVM_IMAGE_EDIT_SKIA_DECODE_MAX_PIXELS
    ) : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            JvmBoundedImageEditDecoder(result.source, options, fullDecodePixels, skiaDecodePixels)
    }
}

/** Returns null when the source is small enough for Skia, or ImageIO cannot read it. */
internal fun decodeSubsampledImageEditBitmap(bytes: ByteArray, maximumSide: Int, fullDecodePixels: Long): Bitmap? {
    if (maximumSide <= 0) return null
    val (width, height, origin) = encodedImageHeader(bytes) ?: return null
    if (width.toLong() * height <= fullDecodePixels) return null
    // Keep the decoded edge within (maximumSide, 2 * maximumSide] so the final scale stays smooth.
    val step = ceil(maxOf(width, height) / (2.0 * maximumSide)).toInt().coerceAtLeast(1)
    val raw = readSubsampled(bytes, step) ?: return null
    val swap = origin.swapsWidthHeight()
    val orientedWidth = if (swap) raw.height else raw.width
    val orientedHeight = if (swap) raw.width else raw.height
    val scale = min(1.0, maximumSide.toDouble() / maxOf(orientedWidth, orientedHeight))
    val outWidth = maxOf(1, (orientedWidth * scale).toInt())
    val outHeight = maxOf(1, (orientedHeight * scale).toInt())
    val out = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB_PRE)
    val graphics = out.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.composite = AlphaComposite.Src
        graphics.scale(outWidth.toDouble() / orientedWidth, outHeight.toDouble() / orientedHeight)
        graphics.transform(orientationTransform(origin, raw.width.toDouble(), raw.height.toDouble()))
        graphics.drawImage(raw, 0, 0, null)
    } finally { graphics.dispose() }
    raw.flush()
    val argb = (out.raster.dataBuffer as DataBufferInt).data
    // TYPE_INT_ARGB_PRE stored little-endian is Skia's premultiplied BGRA_8888.
    val pixels = ByteArray(argb.size * 4)
    argb.forEachIndexed { i, color ->
        pixels[i * 4] = color.toByte(); pixels[i * 4 + 1] = (color ushr 8).toByte()
        pixels[i * 4 + 2] = (color ushr 16).toByte(); pixels[i * 4 + 3] = (color ushr 24).toByte()
    }
    val bitmap = Bitmap()
    if (!bitmap.installPixels(ImageInfo(outWidth, outHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL), pixels, outWidth * 4)) {
        bitmap.close()
        return null
    }
    bitmap.setImmutable()
    return bitmap
}

/** Refuses, before Skia allocates it, a full-resolution bitmap of more than [maxPixels] pixels (B4-4). */
internal fun requireSkiaImageEditDecodable(bytes: ByteArray, maxPixels: Long) {
    val (width, height) = encodedImageHeader(bytes) ?: return
    check(width.toLong() * height <= maxPixels) { MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE }
}

/** Header only: Skia's codec reads the dimensions and EXIF origin without decoding pixels. */
private fun encodedImageHeader(bytes: ByteArray): Triple<Int, Int, EncodedOrigin>? {
    val data = Data.makeFromBytes(bytes)
    try {
        val codec = try { Codec.makeFromData(data) } catch (_: Exception) { return null }
        try {
            return if (codec.width > 0 && codec.height > 0) Triple(codec.width, codec.height, codec.encodedOrigin) else null
        } finally { codec.close() }
    } finally { data.close() }
}

private fun readSubsampled(bytes: ByteArray, step: Int): BufferedImage? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    try {
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.setInput(input, true, true)
            val param = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            // CMYK JPEG and similar inputs ImageIO cannot convert fall back to Skia.
            return try { reader.read(0, param) } catch (_: Exception) { null }
        } finally { reader.dispose() }
    } finally { input.close() }
}

/** Maps raw pixel coordinates to upright ones for each EXIF origin (width/height are raw). */
private fun orientationTransform(origin: EncodedOrigin, width: Double, height: Double): AffineTransform = when (origin) {
    EncodedOrigin.TOP_RIGHT -> AffineTransform(-1.0, 0.0, 0.0, 1.0, width, 0.0)
    EncodedOrigin.BOTTOM_RIGHT -> AffineTransform(-1.0, 0.0, 0.0, -1.0, width, height)
    EncodedOrigin.BOTTOM_LEFT -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, height)
    EncodedOrigin.LEFT_TOP -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
    EncodedOrigin.RIGHT_TOP -> AffineTransform(0.0, 1.0, -1.0, 0.0, height, 0.0)
    EncodedOrigin.RIGHT_BOTTOM -> AffineTransform(0.0, -1.0, -1.0, 0.0, height, width)
    EncodedOrigin.LEFT_BOTTOM -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, width)
    else -> AffineTransform()
}
