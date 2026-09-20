package com.valoser.futacha.shared.util

actual fun isAndroid(): Boolean = false

actual fun isLegacyCompatImeBackBehavior(): Boolean = false

actual fun isDesktop(): Boolean = com.valoser.futacha.shared.desktop.DesktopEnvironment.current != null
