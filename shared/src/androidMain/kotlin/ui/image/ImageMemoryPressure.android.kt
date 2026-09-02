@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.valoser.futacha.shared.ui.image

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration

internal fun androidTrimMemoryPressureLevel(level: Int): ImageMemoryPressureLevel? = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> ImageMemoryPressureLevel.MODERATE

    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
    ComponentCallbacks2.TRIM_MEMORY_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> ImageMemoryPressureLevel.CRITICAL

    else -> null
}

internal actual fun createImageMemoryPressureMonitor(
    platformContext: Any?,
    onPressure: (ImageMemoryPressureLevel) -> Unit
): ImageMemoryPressureMonitor {
    val context = (platformContext as? Context)?.applicationContext
        ?: return object : ImageMemoryPressureMonitor {
            override fun close() = Unit
        }
    val callbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            androidTrimMemoryPressureLevel(level)?.let(onPressure)
        }

        override fun onLowMemory() {
            onPressure(ImageMemoryPressureLevel.CRITICAL)
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit
    }
    context.registerComponentCallbacks(callbacks)
    return object : ImageMemoryPressureMonitor {
        private var isClosed = false

        override fun close() {
            if (isClosed) return
            isClosed = true
            runCatching { context.unregisterComponentCallbacks(callbacks) }
        }
    }
}
