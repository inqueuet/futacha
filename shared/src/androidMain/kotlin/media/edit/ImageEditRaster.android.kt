package com.valoser.futacha.shared.media.edit

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

internal actual fun configureImageEditDecode(builder: ImageRequest.Builder) = builder.allowHardware(false)
internal actual fun imageEditPixels(image: Image): EditRaster {
    require(image.width > 0 && image.height > 0)
    val scale = min(1f, IMAGE_EDIT_MAX_EDGE.toFloat() / maxOf(image.width, image.height))
    val bitmap = image.toBitmap(maxOf(1, (image.width * scale).roundToInt()), maxOf(1, (image.height * scale).roundToInt()))
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return EditRaster(bitmap.width, bitmap.height, pixels)
    } finally { bitmap.recycle() }
}
internal actual fun imageEditBitmap(raster: EditRaster): ImageBitmap =
    Bitmap.createBitmap(raster.argb, raster.width, raster.height, Bitmap.Config.ARGB_8888).asImageBitmap()
internal actual fun encodeImageEditJpeg(raster: EditRaster): ByteArray {
    val bitmap = Bitmap.createBitmap(raster.argb, raster.width, raster.height, Bitmap.Config.ARGB_8888)
    return try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "画像を書き出せませんでした" }
            output.toByteArray()
        }
    } finally { bitmap.recycle() }
}
