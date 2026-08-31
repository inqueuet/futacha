package com.valoser.futacha.shared.state

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun generateAppLockRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Secure random byte count must be positive" }
    val bytes = ByteArray(size)
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(
            kSecRandomDefault,
            bytes.size.toULong(),
            pinned.addressOf(0)
        )
    }
    if (status == errSecSuccess) {
        return bytes
    }
    error("SecRandomCopyBytes failed with status=$status")
}
