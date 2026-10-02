package com.valoser.futacha.shared.ui.image

import coil3.request.ImageRequest

// The desktop registers only Skia's still-image decoder.
internal actual fun ImageRequest.Builder.staticImageDecoding(): ImageRequest.Builder = this

// Desktop memory is not the constraint the iOS decoder addresses.
internal actual fun ImageRequest.Builder.boundedOriginalDecoding(): ImageRequest.Builder = this
