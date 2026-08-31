package com.valoser.futacha.shared.state

import java.security.SecureRandom

private val appLockSecureRandom = SecureRandom()

internal actual fun generateAppLockRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Secure random byte count must be positive" }
    return ByteArray(size).also(appLockSecureRandom::nextBytes)
}
