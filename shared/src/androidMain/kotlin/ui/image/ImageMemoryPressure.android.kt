@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.valoser.futacha.shared.ui.image

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI_HIDDEN and BACKGROUND only report that the app left the screen (on Android 14+
 * they are the only levels sent). Treating them as pressure kept the gate at 1-3
 * requests and a shrunken memory cache for 30-60 s after every return to the app.
 * Memory sampling detects pressure even when the OS no longer sends RUNNING_*.
 */
internal fun androidTrimMemoryPressureLevel(level: Int): ImageMemoryPressureLevel? = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> ImageMemoryPressureLevel.MODERATE

    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
    ComponentCallbacks2.TRIM_MEMORY_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> ImageMemoryPressureLevel.CRITICAL

    else -> null
}

/** [held] is the level currently in effect; leaving it needs a lower value than entering it. */
internal fun androidHeapMemoryPressureLevel(
    usedBytes: Long,
    maxBytes: Long,
    held: ImageMemoryPressureLevel? = null
): ImageMemoryPressureLevel? {
    if (maxBytes <= 0L || usedBytes < 0L) return null
    val fraction = usedBytes.toDouble() / maxBytes
    return when {
        fraction >= (if (held == ImageMemoryPressureLevel.CRITICAL) 0.85 else 0.90) -> ImageMemoryPressureLevel.CRITICAL
        fraction >= (if (held != null) 0.70 else 0.80) -> ImageMemoryPressureLevel.MODERATE
        else -> null
    }
}

/** One sample of the app's Java heap, its native heap and the device's memory. */
internal data class AndroidMemorySample(
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val systemAvailableBytes: Long,
    val systemThresholdBytes: Long,
    val systemTotalBytes: Long,
    val systemLowMemory: Boolean
)

/**
 * Since Android 8 decoded bitmaps live in the native heap, so the Java heap alone never showed
 * image memory (B4). The device's free memory against the low-memory killer's threshold and the
 * app's native heap against the device's RAM are sampled as well; the most severe level wins.
 */
internal fun androidMemoryPressureLevel(
    sample: AndroidMemorySample,
    held: ImageMemoryPressureLevel? = null
): ImageMemoryPressureLevel? {
    val heap = androidHeapMemoryPressureLevel(sample.heapUsedBytes, sample.heapMaxBytes, held)
    val system = when {
        sample.systemLowMemory -> ImageMemoryPressureLevel.CRITICAL
        sample.systemThresholdBytes > 0L && sample.systemAvailableBytes >= 0L &&
            sample.systemAvailableBytes < sample.systemThresholdBytes * (if (held != null) 2.5 else 2.0) ->
            ImageMemoryPressureLevel.MODERATE
        else -> null
    }
    val native = if (sample.systemTotalBytes > 0L && sample.nativeHeapAllocatedBytes >= 0L &&
        sample.nativeHeapAllocatedBytes >= sample.systemTotalBytes * (if (held != null) 0.20 else 0.25)
    ) ImageMemoryPressureLevel.MODERATE else null
    return listOfNotNull(heap, system, native).maxByOrNull { it.severity }
}

/**
 * Reports a sampled level when it first appears or rises. A level that merely persists is
 * repeated with a doubling back-off: re-reporting it every 5 s kept images throttled for as long
 * as the heap stayed above the threshold, even when the image cache had nothing left to give.
 */
internal class AndroidMemoryPressureSampler(
    private val initialRepeatMillis: Long = 120_000L,
    private val maxRepeatMillis: Long = 960_000L
) {
    private var held: ImageMemoryPressureLevel? = null
    private var lastReportMillis = 0L
    private var repeatMillis = initialRepeatMillis

    fun level(sample: AndroidMemorySample): ImageMemoryPressureLevel? = androidMemoryPressureLevel(sample, held)

    fun report(level: ImageMemoryPressureLevel?, nowMillis: Long): ImageMemoryPressureLevel? {
        val previous = held
        held = level
        if (level == null) {
            repeatMillis = initialRepeatMillis
            return null
        }
        if (previous == null || level.severity > previous.severity) {
            lastReportMillis = nowMillis
            repeatMillis = initialRepeatMillis
            return level
        }
        if (nowMillis - lastReportMillis < repeatMillis) return null
        lastReportMillis = nowMillis
        repeatMillis = (repeatMillis * 2).coerceAtMost(maxRepeatMillis)
        return level
    }
}

private fun readAndroidMemorySample(runtime: Runtime, activityManager: ActivityManager?): AndroidMemorySample {
    val info = ActivityManager.MemoryInfo()
    val hasSystem = activityManager != null && runCatching { activityManager.getMemoryInfo(info) }.isSuccess
    return AndroidMemorySample(
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        heapMaxBytes = runtime.maxMemory(),
        nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
        systemAvailableBytes = if (hasSystem) info.availMem else -1L,
        systemThresholdBytes = if (hasSystem) info.threshold else 0L,
        systemTotalBytes = if (hasSystem) info.totalMem else 0L,
        systemLowMemory = hasSystem && info.lowMemory
    )
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
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    scope.launch {
        val runtime = Runtime.getRuntime()
        val sampler = AndroidMemoryPressureSampler()
        while (isActive) {
            val sample = readAndroidMemorySample(runtime, activityManager)
            sampler.report(sampler.level(sample), SystemClock.elapsedRealtime())?.let(onPressure)
            delay(5_000L)
        }
    }
    return object : ImageMemoryPressureMonitor {
        private var isClosed = false

        override fun close() {
            if (isClosed) return
            isClosed = true
            scope.cancel()
            runCatching { context.unregisterComponentCallbacks(callbacks) }
        }
    }
}
