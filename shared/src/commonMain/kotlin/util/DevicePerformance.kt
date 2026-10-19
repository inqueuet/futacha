package com.valoser.futacha.shared.util

/**
 * 簡易的なデバイス性能プロファイル。
 * 極端に少ないRAMや空きストレージを検知したら低負荷モードの基準に使う。
 */
data class DevicePerformanceProfile(
    val isLowRam: Boolean,
    val isLowStorage: Boolean,
    val totalRamMb: Int? = null,
    val availableStorageMb: Long? = null,
    /** Optional process image budget, split between general and catalog loaders. */
    val imageMemoryBudgetBytes: Long? = null,
    val imageRequestParallelismLimit: Int? = null
) {
    val isLowSpec: Boolean get() = isLowRam || isLowStorage
}

expect fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile

/** iOS reports physical RAM rather than Android's per-process memory class. */
internal fun iosMemoryPerformanceProfile(totalRamMb: Int): DevicePerformanceProfile {
    val lowRam = totalRamMb in 1..2048
    return DevicePerformanceProfile(
        isLowRam = lowRam,
        isLowStorage = false,
        totalRamMb = totalRamMb.takeIf { it > 0 },
        imageMemoryBudgetBytes = if (lowRam) 32L * 1024 * 1024 else null,
        imageRequestParallelismLimit = if (lowRam) 2 else null
    )
}
