@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.ui.board

import kotlinx.cinterop.*
import kotlin.native.Platform
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

internal actual fun logVideoPlaybackDiagnostic(event: String, detail: String) {
    if (!Platform.isDebugBinary) return
    // Console only: do not persist signed URLs or media errors in the production diagnostic file.
    // Kotlin/Native C varargs marshal String as char*, not an Objective-C object.
    // Supply a single escaped format string so URLs containing '%' are safe too.
    NSLog(("[FutachaVideo] $event " + detail.take(4096).replace('\n', ' ').replace('\r', ' ')).replace("%", "%%"))
}

internal fun logVideoPlaybackEnvironment(url: String, engine: String) {
    if (!Platform.isDebugBinary) return
    val model = memScoped { val info = alloc<utsname>(); if (uname(info.ptr) == 0) info.machine.toKString() else UIDevice.currentDevice.model }
    logVideoPlaybackDiagnostic("environment", "device=$model iOS=${UIDevice.currentDevice.systemVersion} " +
        "app=${NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString")} " +
        "build=${NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion")} engine=$engine " +
        "url=$url extension=${extractVideoUrlExtension(url)}")
}
