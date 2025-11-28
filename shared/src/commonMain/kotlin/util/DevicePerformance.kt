package com.valoser.futacha.shared.util

/**
 * 簡易的なデバイス性能プロファイル。
 * 極端に少ないRAMや空きストレージを検知したら低負荷モードの基準に使う。
 */
data class DevicePerformanceProfile(
    val isLowRam: Boolean,
    val isLowStorage: Boolean,
    val totalRamMb: Int? = null,
    val availableStorageMb: Long? = null
) {
    val isLowSpec: Boolean get() = isLowRam || isLowStorage
}

expect fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile
