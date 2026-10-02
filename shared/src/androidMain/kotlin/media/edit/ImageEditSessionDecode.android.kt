package com.valoser.futacha.shared.media.edit

import coil3.request.ImageRequest

// BitmapFactory/ImageDecoder already subsample to the requested size while decoding.
internal actual fun ImageRequest.Builder.boundedImageEditDecoding(bytes: ByteArray): ImageRequest.Builder = this
