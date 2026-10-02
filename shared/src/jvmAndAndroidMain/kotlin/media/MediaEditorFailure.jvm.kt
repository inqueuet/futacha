package com.valoser.futacha.shared.media

internal actual fun Throwable.isMemoryExhaustion(): Boolean = this is OutOfMemoryError

// UnsatisfiedLinkError on the first load, NoClassDefFoundError/ExceptionInInitializerError afterwards.
internal actual fun Throwable.isNativeLinkFailure(): Boolean = this is LinkageError
