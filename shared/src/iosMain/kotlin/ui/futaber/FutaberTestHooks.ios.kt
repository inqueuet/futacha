package com.valoser.futacha.shared.ui.futaber

import platform.Foundation.NSUserDefaults
import kotlin.experimental.ExperimentalNativeApi

/**
 * The iOS UI test's launch argument `-futaber.uitest.threadDelayMillis N` (L-B7): the bundled tutorial board answers its
 * thread N ms late, so the catalog's "Loading..." can be seen. Read only by a debug binary; a release build ignores it.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun futaberTestThreadDelayMillis(): Long =
    if (!Platform.isDebugBinary) 0L
    else NSUserDefaults.standardUserDefaults.integerForKey("futaber.uitest.threadDelayMillis").coerceIn(0L, 10_000L)
