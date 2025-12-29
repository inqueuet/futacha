package com.valoser.futacha.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalForeignApi::class)
public actual fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile {
    val processInfo = NSProcessInfo.processInfo
    val totalRamMb = (processInfo.physicalMemory.toLong() / (1024 * 1024)).toInt()
    // 2GB未満の端末を低RAM扱いにする
    val isLowRam = totalRamMb < 2048

    val homePath = NSHomeDirectory()
    val fileManager = NSFileManager.defaultManager
    val attrs = runCatching {
        fileManager.attributesOfFileSystemForPath(homePath, null)
    }.getOrNull()
    val freeSizeBytes = attrs?.get(NSFileSystemFreeSize) as? NSNumber
    val availableMb = freeSizeBytes?.longValue?.div(1024 * 1024)
    // 空き容量が1GB未満なら低ストレージ扱い
    val isLowStorage = availableMb != null && availableMb in 0..1024

    return DevicePerformanceProfile(
        isLowRam = isLowRam,
        isLowStorage = isLowStorage,
        totalRamMb = totalRamMb,
        availableStorageMb = availableMb
    )
}
