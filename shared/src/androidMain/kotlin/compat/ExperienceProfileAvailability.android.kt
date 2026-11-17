package com.valoser.futacha.shared.compat

/** FutachaApplication sets the real value from FLAG_DEBUGGABLE before any profile is read. */
actual fun platformDefaultPreviewModesEnabled(): Boolean = false

/** FutachaApplication turns it on before any profile is read. */
actual fun platformDefaultMobileModesEnabled(): Boolean = false
