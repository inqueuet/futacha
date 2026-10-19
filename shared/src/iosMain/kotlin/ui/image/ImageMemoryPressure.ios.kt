package com.valoser.futacha.shared.ui.image

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import com.valoser.futacha.shared.media.video.trimIosVideoPreviewMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

private val mediaTrimScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
// Accessed only on main: two loader observers coalesce into one resource trim.
private var mediaTrimJob: Job? = null

@OptIn(NativeRuntimeApi::class)
internal fun requestIosMediaMemoryTrim() {
    dispatch_async(dispatch_get_main_queue()) {
        if (mediaTrimJob?.isActive != true) {
            mediaTrimJob = mediaTrimScope.launch {
                trimIosVideoThumbnailMemory()
                trimIosVideoPreviewMemory()
                // NSData / CI objects are off-heap; their wrappers may otherwise survive the warning.
                GC.schedule()
            }
        }
    }
}

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
        requestIosMediaMemoryTrim()
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
