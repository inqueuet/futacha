package com.valoser.futacha.shared.compat

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCompatibilityMapping

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
internal actual fun normalizeCompatWatchText(raw: String): String =
    NSString.create(string = raw).precomposedStringWithCompatibilityMapping.trim().uppercase()
