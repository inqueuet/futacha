package com.valoser.futacha.shared.util

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager

public actual fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile {
    val context = platformContext as? Context
    val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryClassMb = activityManager?.memoryClass
    // 少ないRAM端末: 128MB以下を目安にする（old/low-endデバイス）
    val isLowRam = (activityManager?.isLowRamDevice == true) ||
        (memoryClassMb != null && memoryClassMb <= 128)

    val cacheDir = context?.cacheDir ?: Environment.getDataDirectory()
    // Allocatable bytes include cached data the system can clear on demand, which is
    // what the user can actually use; usableSpace alone flagged devices whose space
    // was mostly reclaimable cache. An unknown value is not treated as low.
    val allocatableBytes = context?.let { ctx ->
        runCatching {
            val storageManager = ctx.getSystemService(StorageManager::class.java)
            storageManager?.getAllocatableBytes(storageManager.getUuidForPath(cacheDir))
        }.getOrNull()
    }
    val availableBytes = allocatableBytes
        ?: runCatching { cacheDir.usableSpace }.getOrNull()?.takeIf { it > 0L }
    val availableMb = availableBytes?.div(1024 * 1024)
    // 空き容量が1GB未満なら低ストレージ扱い
    val isLowStorage = availableMb != null && availableMb in 0..1024

    return DevicePerformanceProfile(
        isLowRam = isLowRam,
        isLowStorage = isLowStorage,
        totalRamMb = memoryClassMb,
        availableStorageMb = availableMb
    )
}
