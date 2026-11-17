@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey

/** No filesystem access: safe to seed the first composition before storage discovery. */
internal fun iosMemoryPerformanceProfile(): DevicePerformanceProfile = iosMemoryProfile

private val iosMemoryProfile: DevicePerformanceProfile by lazy {
    iosMemoryPerformanceProfile((NSProcessInfo.processInfo.physicalMemory / (1024uL * 1024uL)).toInt())
}

@OptIn(ExperimentalForeignApi::class)
public actual fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile {
    val homePath = NSHomeDirectory()
    // NSFileSystemFreeSize excludes purgeable space (caches, offloadable data) that
    // iOS frees on demand, so it reports far less than the user can actually use.
    val importantUsageBytes = runCatching {
        NSURL.fileURLWithPath(homePath)
            .resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), null)
            ?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? NSNumber
    }.getOrNull()?.longValue?.takeIf { it > 0L }
    val freeSizeBytes = importantUsageBytes ?: run {
        val attrs = runCatching {
            NSFileManager.defaultManager.attributesOfFileSystemForPath(homePath, null)
        }.getOrNull()
        (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longValue
    }
    val availableMb = freeSizeBytes?.div(1024 * 1024)
    // 空き容量が1GB未満なら低ストレージ扱い
    val isLowStorage = availableMb != null && availableMb in 0..1024

    return iosMemoryPerformanceProfile().copy(
        isLowStorage = isLowStorage,
        availableStorageMb = availableMb
    )
}
