package com.valoser.futacha.shared.ui.image

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Size
import coil3.size.pxOrElse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class ImageMemoryPressureLevel(val severity: Int) {
    NORMAL(0),
    MODERATE(1),
    CRITICAL(2)
}

internal data class ImageMemoryPressureSignal(
    val level: ImageMemoryPressureLevel = ImageMemoryPressureLevel.NORMAL,
    val generation: Long = 0L
)

internal fun ImageMemoryPressureSignal.withPressure(
    pressure: ImageMemoryPressureLevel
): ImageMemoryPressureSignal = ImageMemoryPressureSignal(
    level = if (pressure.severity > level.severity) pressure else level,
    generation = generation + 1L
)

internal fun imageMemoryPressureRecoveryDelayMillis(level: ImageMemoryPressureLevel): Long = when (level) {
    ImageMemoryPressureLevel.NORMAL -> 0L
    ImageMemoryPressureLevel.MODERATE -> 30_000L
    ImageMemoryPressureLevel.CRITICAL -> 60_000L
}

internal interface ImageMemoryPressureMonitor {
    fun close()
}

internal expect fun createImageMemoryPressureMonitor(
    platformContext: Any?,
    onPressure: (ImageMemoryPressureLevel) -> Unit
): ImageMemoryPressureMonitor

internal enum class ImageRequestWorkload {
    BOUNDED,
    LARGE,
    ORIGINAL,
    ANIMATED,
    VIDEO
}

internal data class ImageMemoryPressurePolicy(
    val level: ImageMemoryPressureLevel,
    val maxParallelism: Int,
    val memoryCacheBytes: Long,
    val heavyRequestWeight: Int
) {
    fun weightFor(workload: ImageRequestWorkload): Int = when {
        level == ImageMemoryPressureLevel.NORMAL -> 1
        workload == ImageRequestWorkload.BOUNDED -> 1
        else -> heavyRequestWeight.coerceIn(1, maxParallelism)
    }
}

private const val ONE_MIB = 1024L * 1024L
private const val LARGE_IMAGE_PIXEL_COUNT = 2_000_000L
private val ANIMATED_IMAGE_EXTENSIONS = setOf("gif", "apng", "webp")
private val VIDEO_IMAGE_EXTENSIONS = setOf("webm", "mp4", "m4v", "mov")

internal fun resolveImageMemoryPressurePolicy(
    baseParallelism: Int,
    baseMemoryCacheBytes: Long,
    memoryClassMb: Int?,
    level: ImageMemoryPressureLevel
): ImageMemoryPressurePolicy {
    val normalizedParallelism = baseParallelism.coerceAtLeast(1)
    val normalizedCacheBytes = baseMemoryCacheBytes.coerceAtLeast(ONE_MIB)
    if (level == ImageMemoryPressureLevel.NORMAL) {
        return ImageMemoryPressurePolicy(
            level = level,
            maxParallelism = normalizedParallelism,
            memoryCacheBytes = normalizedCacheBytes,
            heavyRequestWeight = 1
        )
    }

    val parallelism = when (level) {
        ImageMemoryPressureLevel.NORMAL -> normalizedParallelism
        ImageMemoryPressureLevel.MODERATE -> when {
            memoryClassMb != null && memoryClassMb <= 128 -> 1
            memoryClassMb != null && memoryClassMb <= 256 -> minOf(2, normalizedParallelism)
            memoryClassMb != null && memoryClassMb <= 512 -> minOf(3, normalizedParallelism)
            else -> ((normalizedParallelism + 1) / 2).coerceAtLeast(1)
        }
        ImageMemoryPressureLevel.CRITICAL -> 1
    }
    val cacheDivisor = when (level) {
        ImageMemoryPressureLevel.NORMAL -> 1
        ImageMemoryPressureLevel.MODERATE -> when {
            memoryClassMb != null && memoryClassMb <= 128 -> 4
            memoryClassMb != null && memoryClassMb <= 256 -> 3
            else -> 2
        }
        ImageMemoryPressureLevel.CRITICAL -> 8
    }
    val cacheBytes = (normalizedCacheBytes / cacheDivisor)
        .coerceAtLeast(ONE_MIB)
        .coerceAtMost(normalizedCacheBytes)
    return ImageMemoryPressurePolicy(
        level = level,
        maxParallelism = parallelism,
        memoryCacheBytes = cacheBytes,
        heavyRequestWeight = if (level == ImageMemoryPressureLevel.MODERATE) {
            minOf(2, parallelism)
        } else {
            parallelism
        }
    )
}

internal fun classifyImageRequestWorkload(
    widthPx: Int?,
    heightPx: Int?,
    isOriginalSize: Boolean,
    data: String
): ImageRequestWorkload {
    val extension = data
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    if (extension in VIDEO_IMAGE_EXTENSIONS) return ImageRequestWorkload.VIDEO
    if (extension in ANIMATED_IMAGE_EXTENSIONS) return ImageRequestWorkload.ANIMATED
    if (isOriginalSize) return ImageRequestWorkload.ORIGINAL
    val pixelCount = if (widthPx != null && heightPx != null) {
        widthPx.toLong() * heightPx.toLong()
    } else {
        0L
    }
    return if (pixelCount >= LARGE_IMAGE_PIXEL_COUNT) {
        ImageRequestWorkload.LARGE
    } else {
        ImageRequestWorkload.BOUNDED
    }
}

/**
 * A cancellation-safe weighted gate. Normal policy is byte-for-byte equivalent
 * to the configured parallelism; only an OS pressure signal reduces capacity.
 */
internal class AdaptiveImageRequestGate(initialPolicy: ImageMemoryPressurePolicy) {
    private data class Waiter(
        val workload: ImageRequestWorkload,
        val signal: CompletableDeferred<Unit> = CompletableDeferred(),
        var grantedWeight: Int = 0
    )

    private val mutex = Mutex()
    private val waiters = mutableListOf<Waiter>()
    private var activeWeight = 0

    @kotlin.concurrent.Volatile
    private var currentPolicy = initialPolicy

    fun policy(): ImageMemoryPressurePolicy = currentPolicy

    suspend fun updatePolicy(policy: ImageMemoryPressurePolicy) {
        mutex.withLock {
            currentPolicy = policy
            grantWaitersLocked()
        }
    }

    suspend fun <T> withPermit(
        workload: ImageRequestWorkload,
        block: suspend () -> T
    ): T {
        var reservedWeight = 0
        val waiter = mutex.withLock {
            val weight = currentPolicy.weightFor(workload)
            if (waiters.isEmpty() && activeWeight + weight <= currentPolicy.maxParallelism) {
                activeWeight += weight
                reservedWeight = weight
                null
            } else {
                Waiter(workload).also(waiters::add)
            }
        }
        if (waiter != null) {
            try {
                waiter.signal.await()
                reservedWeight = waiter.grantedWeight
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    mutex.withLock {
                        val wasQueued = waiters.remove(waiter)
                        if (!wasQueued && waiter.grantedWeight > 0) {
                            activeWeight = (activeWeight - waiter.grantedWeight).coerceAtLeast(0)
                            waiter.grantedWeight = 0
                        }
                        grantWaitersLocked()
                    }
                }
                throw error
            }
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    activeWeight = (activeWeight - reservedWeight).coerceAtLeast(0)
                    grantWaitersLocked()
                }
            }
        }
    }

    private fun grantWaitersLocked() {
        while (waiters.isNotEmpty()) {
            val next = waiters.first()
            val weight = currentPolicy.weightFor(next.workload)
            if (activeWeight + weight > currentPolicy.maxParallelism) return
            waiters.removeAt(0)
            activeWeight += weight
            next.grantedWeight = weight
            next.signal.complete(Unit)
        }
    }
}

internal class ImageMemoryPressureInterceptor(
    private val gate: AdaptiveImageRequestGate
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // Preserve the existing request pipeline exactly while memory is healthy.
        // The gate is only introduced for requests that start during pressure.
        if (gate.policy().level == ImageMemoryPressureLevel.NORMAL) return chain.proceed()
        val width = chain.size.width.pxOrElse { 0 }.takeIf { it > 0 }
        val height = chain.size.height.pxOrElse { 0 }.takeIf { it > 0 }
        val workload = classifyImageRequestWorkload(
            widthPx = width,
            heightPx = height,
            isOriginalSize = chain.size == Size.ORIGINAL,
            data = chain.request.data.toString()
        )
        return gate.withPermit(workload) { chain.proceed() }
    }
}
