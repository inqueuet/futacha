package com.valoser.futacha.shared.util

actual fun detectDevicePerformanceProfile(platformContext: Any?): DevicePerformanceProfile =
    DevicePerformanceProfile(
        isLowRam = false,
        isLowStorage = false
    )
