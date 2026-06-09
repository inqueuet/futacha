package com.valoser.futacha.shared.state

import java.security.SecureRandom

private val appLockSecureRandom = SecureRandom()

internal actual fun generateAppLockRandomBytes(size: Int): ByteArray {
    return ByteArray(size).also(appLockSecureRandom::nextBytes)
}
