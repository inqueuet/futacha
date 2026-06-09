package com.valoser.futacha.shared.state

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
internal actual fun generateAppLockRandomBytes(size: Int): ByteArray {
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
    return ByteArray(size) { Random.nextInt(0, 256).toByte() }
}
