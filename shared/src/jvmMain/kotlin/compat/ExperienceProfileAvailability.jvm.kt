package com.valoser.futacha.shared.compat

/** Desktop does not offer preview modes or the mobile-only modes. */
actual fun platformDefaultPreviewModesEnabled(): Boolean = false

actual fun platformDefaultMobileModesEnabled(): Boolean = false
