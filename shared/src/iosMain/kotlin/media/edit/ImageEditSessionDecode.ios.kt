package com.valoser.futacha.shared.media.edit

import coil3.request.ImageRequest
import com.valoser.futacha.shared.ui.compat.readIosEncodedImageSize
import com.valoser.futacha.shared.ui.image.boundedOriginalDecoding

/**
 * Only an image larger than the editor's edge switches to ImageIO's thumbnail decode, which reads
 * at a reduced scale with the EXIF orientation applied; smaller images keep the Skia decode.
 */
internal actual fun ImageRequest.Builder.boundedImageEditDecoding(bytes: ByteArray): ImageRequest.Builder {
    val size = readIosEncodedImageSize(bytes) ?: return this
    return if (maxOf(size.width, size.height) > IMAGE_EDIT_MAX_EDGE) boundedOriginalDecoding() else this
}
