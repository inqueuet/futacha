package com.valoser.futacha.shared.state

private const val APP_LOCK_PASSWORD_HASH_PREFIX = "sha256-v1"
private const val APP_LOCK_SALT_BYTE_COUNT = 16

internal const val APP_LOCK_PASSWORD_MIN_LENGTH = 4

internal fun sanitizeAppLockPasswordHash(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.takeIf { it.startsWith("$APP_LOCK_PASSWORD_HASH_PREFIX:") }
}

internal fun buildAppLockPasswordHash(
    password: String,
    salt: String = generateAppLockSalt()
): String {
    val digest = sha256Hex("$salt:$password")
    return "$APP_LOCK_PASSWORD_HASH_PREFIX:$salt:$digest"
}

internal fun verifyAppLockPassword(
    password: String,
    storedHash: String?
): Boolean {
    val sanitized = sanitizeAppLockPasswordHash(storedHash) ?: return false
    val parts = sanitized.split(':')
    if (parts.size != 3 || parts[0] != APP_LOCK_PASSWORD_HASH_PREFIX) return false
    val salt = parts[1]
    val expectedDigest = parts[2]
    if (salt.isBlank() || expectedDigest.length != 64) return false
    val actualDigest = sha256Hex("$salt:$password")
    return constantTimeEquals(actualDigest, expectedDigest)
}

internal fun isValidAppLockPassword(password: String): Boolean {
    return password.length >= APP_LOCK_PASSWORD_MIN_LENGTH
}

private fun generateAppLockSalt(): String {
    return generateAppLockRandomBytes(APP_LOCK_SALT_BYTE_COUNT).toHexLowercase()
}

private fun constantTimeEquals(left: String, right: String): Boolean {
    if (left.length != right.length) return false
    var diff = 0
    left.indices.forEach { index ->
        diff = diff or (left[index].code xor right[index].code)
    }
    return diff == 0
}

private fun sha256Hex(input: String): String {
    return sha256(input.encodeToByteArray()).toHexLowercase()
}

private fun ByteArray.toHexLowercase(): String {
    val chars = CharArray(size * 2)
    val digits = "0123456789abcdef"
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        chars[index * 2] = digits[value ushr 4]
        chars[index * 2 + 1] = digits[value and 0x0f]
    }
    return chars.concatToString()
}

private fun sha256(input: ByteArray): ByteArray {
    val paddedLength = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedLength)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    val bitLength = input.size.toLong() * 8L
    for (index in 0 until 8) {
        padded[paddedLength - 1 - index] = ((bitLength ushr (index * 8)) and 0xff).toByte()
    }

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19
    val words = IntArray(64)

    for (chunkOffset in padded.indices step 64) {
        for (index in 0 until 16) {
            val offset = chunkOffset + index * 4
            words[index] = ((padded[offset].toInt() and 0xff) shl 24) or
                ((padded[offset + 1].toInt() and 0xff) shl 16) or
                ((padded[offset + 2].toInt() and 0xff) shl 8) or
                (padded[offset + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val s0 = rotateRight(words[index - 15], 7) xor
                rotateRight(words[index - 15], 18) xor
                (words[index - 15] ushr 3)
            val s1 = rotateRight(words[index - 2], 17) xor
                rotateRight(words[index - 2], 19) xor
                (words[index - 2] ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (index in 0 until 64) {
            val sum1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choice + SHA256_K[index] + words[index]
            val sum0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
    }

    return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).toBigEndianBytes()
}

private fun rotateRight(value: Int, bits: Int): Int {
    return (value ushr bits) or (value shl (32 - bits))
}

private fun IntArray.toBigEndianBytes(): ByteArray {
    val output = ByteArray(size * 4)
    forEachIndexed { index, value ->
        val offset = index * 4
        output[offset] = (value ushr 24).toByte()
        output[offset + 1] = (value ushr 16).toByte()
        output[offset + 2] = (value ushr 8).toByte()
        output[offset + 3] = value.toByte()
    }
    return output
}

private val SHA256_K = intArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
    0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
    0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
    0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
    0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
    0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
    0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
    0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
)
