package com.valoser.futacha.shared.media.edit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import kotlin.math.min
import kotlin.math.roundToInt

internal actual fun configureImageEditDecode(builder: ImageRequest.Builder) = builder
internal actual fun imageEditPixels(image: Image): EditRaster {
    require(image.width > 0 && image.height > 0)
    val scale = min(1f, IMAGE_EDIT_MAX_EDGE.toFloat() / maxOf(image.width, image.height))
    val bitmap = image.toBitmap(maxOf(1, (image.width * scale).roundToInt()), maxOf(1, (image.height * scale).roundToInt()))
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.asComposeImageBitmap().readPixels(pixels)
        return EditRaster(bitmap.width, bitmap.height, pixels)
    } finally { bitmap.close() }
}
private fun skiaImage(raster: EditRaster): org.jetbrains.skia.Image {
    val bytes = ByteArray(raster.argb.size * 4)
    raster.argb.forEachIndexed { i, color ->
        bytes[i * 4] = (color ushr 16).toByte(); bytes[i * 4 + 1] = (color ushr 8).toByte()
        bytes[i * 4 + 2] = color.toByte(); bytes[i * 4 + 3] = (color ushr 24).toByte()
    }
    return org.jetbrains.skia.Image.makeRaster(
        ImageInfo(raster.width, raster.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), bytes, raster.width * 4
    )
}
internal actual fun imageEditBitmap(raster: EditRaster): ImageBitmap {
    val image = skiaImage(raster)
    return try { image.toComposeImageBitmap() } finally { image.close() }
}
internal actual fun encodeImageEditJpeg(raster: EditRaster): ByteArray {
    val image = skiaImage(raster)
    return try {
        val data = requireNotNull(image.encodeToData(EncodedImageFormat.JPEG, 95)) { "画像を書き出せませんでした" }
        try { data.bytes } finally { data.close() }
    } finally { image.close() }
}
