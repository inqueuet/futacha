package com.valoser.futacha.shared.media.edit

import coil3.request.ImageRequest

/**
 * Bounds the editor's decode to [IMAGE_EDIT_MAX_EDGE] while decoding, not after: the Skia decoder
 * used on iOS and the desktop allocates the full-resolution bitmap before Coil scales it, which
 * kills the app for very large device images (B-8). Android's decoder already samples while
 * decoding and leaves the request unchanged. [bytes] are the encoded input of this request.
 */
internal expect fun ImageRequest.Builder.boundedImageEditDecoding(bytes: ByteArray): ImageRequest.Builder
