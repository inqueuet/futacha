package com.valoser.futacha.shared.util

import android.app.ActivityManager
import android.content.Context
import android.os.Environment

public actual fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile {
    val context = platformContext as? Context
    val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryClassMb = activityManager?.memoryClass
    // 少ないRAM端末: 128MB以下を目安にする（old/low-endデバイス）
    val isLowRam = (activityManager?.isLowRamDevice == true) ||
        (memoryClassMb != null && memoryClassMb <= 128)

    val cacheDir = context?.cacheDir ?: Environment.getDataDirectory()
    val availableBytes = runCatching { cacheDir.usableSpace }.getOrDefault(0L)
    val availableMb = availableBytes / (1024 * 1024)
    // 空き容量が1GB未満なら低ストレージ扱い
    val isLowStorage = availableMb in 0..1024

    return DevicePerformanceProfile(
        isLowRam = isLowRam,
        isLowStorage = isLowStorage,
        totalRamMb = memoryClassMb,
        availableStorageMb = availableMb
    )
}
