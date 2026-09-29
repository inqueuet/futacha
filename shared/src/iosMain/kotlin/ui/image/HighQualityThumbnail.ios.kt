package com.valoser.futacha.shared.ui.image

import coil3.request.ImageRequest

// iOS registers only Skia's still-image decoder.
internal actual fun ImageRequest.Builder.staticImageDecoding(): ImageRequest.Builder = this
