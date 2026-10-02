package com.valoser.futacha.shared.ui.image

import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest

// The registered animated decoders (ImageDecoder, APNG, GIF) would play the
// original; BitmapFactory decodes the first frame only.
internal actual fun ImageRequest.Builder.staticImageDecoding(): ImageRequest.Builder =
    decoderFactory(BitmapFactoryDecoder.Factory())

// BitmapFactory and ImageDecoder already subsample to the requested size while decoding.
internal actual fun ImageRequest.Builder.boundedOriginalDecoding(): ImageRequest.Builder = this
