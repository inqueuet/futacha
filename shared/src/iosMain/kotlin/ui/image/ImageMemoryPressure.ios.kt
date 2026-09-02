package com.valoser.futacha.shared.ui.image

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

internal actual fun createImageMemoryPressureMonitor(
    platformContext: Any?,
    onPressure: (ImageMemoryPressureLevel) -> Unit
): ImageMemoryPressureMonitor {
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(
        name = UIApplicationDidReceiveMemoryWarningNotification,
        `object` = null,
        queue = null
    ) {
        onPressure(ImageMemoryPressureLevel.CRITICAL)
    }
    return object : ImageMemoryPressureMonitor {
        private var isClosed = false

        override fun close() {
            if (isClosed) return
            isClosed = true
            center.removeObserver(observer)
        }
    }
}
