package com.valoser.futacha.shared.compat

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
actual fun platformDefaultPreviewModesEnabled(): Boolean = Platform.isDebugBinary

actual fun platformDefaultMobileModesEnabled(): Boolean = true
