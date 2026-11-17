package com.valoser.futacha.shared.ui.image

internal actual fun createImageMemoryPressureMonitor(
    platformContext: Any?,
    onPressure: (ImageMemoryPressureLevel) -> Unit
): ImageMemoryPressureMonitor = object : ImageMemoryPressureMonitor {
    override fun close() = Unit
}
