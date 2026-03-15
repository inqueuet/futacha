package com.valoser.futacha.shared.ui.image

import coil3.decode.Decoder

actual fun getPlatformDecoders(): List<Decoder.Factory> = emptyList()

actual fun getPlatformDiskCacheDirectory(platformContext: Any?): String? = null
