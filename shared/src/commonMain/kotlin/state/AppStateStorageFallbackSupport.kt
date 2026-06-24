package com.valoser.futacha.shared.state

internal fun shouldEmitStorageReadFallback(hasReadableSnapshot: Boolean): Boolean =
    hasReadableSnapshot
