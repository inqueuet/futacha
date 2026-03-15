package com.valoser.futacha.shared.state

internal actual fun createPlatformStateStorage(platformContext: Any?): PlatformStateStorage {
    return JvmPlatformStateStorage()
}

private class JvmPlatformStateStorage : BaseInMemoryPlatformStateStorage()
