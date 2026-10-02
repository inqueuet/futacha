package com.valoser.futacha.shared.media

// Thrown for allocations Kotlin/Native refuses (e.g. an oversized array); true heap exhaustion aborts.
internal actual fun Throwable.isMemoryExhaustion(): Boolean = this is OutOfMemoryError

// Native code is linked at build time; there is no runtime library load to fail.
internal actual fun Throwable.isNativeLinkFailure(): Boolean = false
