package com.valoser.futacha.shared.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() = Unit

actual fun getPlatformDiskCacheDirectory(platformContext: Any?): String? = null
