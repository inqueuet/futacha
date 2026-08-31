package com.valoser.futacha.shared.util

import android.os.Build

actual fun isAndroid(): Boolean = true

actual fun isLegacyCompatImeBackBehavior(): Boolean =
    Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
