@file:OptIn(ExperimentalUnsignedTypes::class)

package com.valoser.futacha.shared.network.aimoge

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.coroutineContext

/** Shown when the server asks for a proof the app cannot make (an unknown algorithm, or far too much work). */
const val AIMOGE_ALTCHA_CHANGED_MESSAGE = "書き込みの確かめの形が変わりました。アプリの更新をお待ちください"

/** The largest `maxnumber` solved (100 times what was seen, so the device is never busy for long). */
const val AIMOGE_ALTCHA_MAX_NUMBER = 10_000_000L

data class AimogeAltchaChallenge(
    val algorithm: String,
    val challenge: String,
    val maxNumber: Long,
    val salt: String,
    val signature: String
)

/** Reads `GET /altcha`; null when a field is missing. `maxnumber` may be a long or a decimal like `1e5`. */
internal fun parseAimogeAltcha(obj: JsonObject): AimogeAltchaChallenge? {
    fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
    val max = str("maxnumber")?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.takeIf { d -> d.isFinite() && d >= 0 }?.toLong() }
    return AimogeAltchaChallenge(
        algorithm = str("algorithm") ?: return null,
        challenge = str("challenge")?.lowercase() ?: return null,
        maxNumber = max ?: return null,
        salt = str("salt") ?: return null,
        signature = str("signature") ?: return null
    )
}

/** Whether [challenge] is a kind the app solves (SHA-256/384/512 within [AIMOGE_ALTCHA_MAX_NUMBER]). */
fun isAimogeAltchaSolvable(challenge: AimogeAltchaChallenge): Boolean =
    aimogeAltchaHasher(challenge.algorithm) != null && challenge.maxNumber in 0..AIMOGE_ALTCHA_MAX_NUMBER

private fun aimogeAltchaHasher(algorithm: String): ((ByteArray) -> String)? = when (algorithm.uppercase()) {
    "SHA-256" -> { b -> b.toByteString().sha256().hex() }
    "SHA-384" -> { b -> sha384(b).toByteString().hex() }
    "SHA-512" -> { b -> b.toByteString().sha512().hex() }
    else -> null
}

/**
 * Finds the number whose hash of `salt + number` is the challenge, the Web client's way, and returns the answer to put
 * in the post body (base64 of the JSON); null when not found or not solvable. Cancellable.
 */
suspend fun solveAimogeAltcha(challenge: AimogeAltchaChallenge): String? {
    val hash = aimogeAltchaHasher(challenge.algorithm) ?: return null
    if (challenge.maxNumber !in 0..AIMOGE_ALTCHA_MAX_NUMBER) return null
    val salt = challenge.salt.encodeToByteArray()
    for (n in 0..challenge.maxNumber) {
        if (n % 2_000L == 0L) coroutineContext.ensureActive()
        if (hash(salt + n.toString().encodeToByteArray()) == challenge.challenge) {
            return aimogeAltchaPayload(challenge, n)
        }
    }
    return null
}

internal fun aimogeAltchaPayload(challenge: AimogeAltchaChallenge, number: Long): String = buildJsonObject {
    put("algorithm", challenge.algorithm)
    put("challenge", challenge.challenge)
    put("number", number)
    put("salt", challenge.salt)
    put("signature", challenge.signature)
}.toString().encodeUtf8().base64()

// SHA-384 (FIPS 180-4): SHA-512 with other first values, cut to 48 bytes. okio has no SHA-384.
private val K512 = ulongArrayOf(
    0x428a2f98d728ae22uL, 0x7137449123ef65cduL, 0xb5c0fbcfec4d3b2fuL, 0xe9b5dba58189dbbcuL,
    0x3956c25bf348b538uL, 0x59f111f1b605d019uL, 0x923f82a4af194f9buL, 0xab1c5ed5da6d8118uL,
    0xd807aa98a3030242uL, 0x12835b0145706fbeuL, 0x243185be4ee4b28cuL, 0x550c7dc3d5ffb4e2uL,
    0x72be5d74f27b896fuL, 0x80deb1fe3b1696b1uL, 0x9bdc06a725c71235uL, 0xc19bf174cf692694uL,
    0xe49b69c19ef14ad2uL, 0xefbe4786384f25e3uL, 0x0fc19dc68b8cd5b5uL, 0x240ca1cc77ac9c65uL,
    0x2de92c6f592b0275uL, 0x4a7484aa6ea6e483uL, 0x5cb0a9dcbd41fbd4uL, 0x76f988da831153b5uL,
    0x983e5152ee66dfabuL, 0xa831c66d2db43210uL, 0xb00327c898fb213fuL, 0xbf597fc7beef0ee4uL,
    0xc6e00bf33da88fc2uL, 0xd5a79147930aa725uL, 0x06ca6351e003826fuL, 0x142929670a0e6e70uL,
    0x27b70a8546d22ffcuL, 0x2e1b21385c26c926uL, 0x4d2c6dfc5ac42aeduL, 0x53380d139d95b3dfuL,
    0x650a73548baf63deuL, 0x766a0abb3c77b2a8uL, 0x81c2c92e47edaee6uL, 0x92722c851482353buL,
    0xa2bfe8a14cf10364uL, 0xa81a664bbc423001uL, 0xc24b8b70d0f89791uL, 0xc76c51a30654be30uL,
    0xd192e819d6ef5218uL, 0xd69906245565a910uL, 0xf40e35855771202auL, 0x106aa07032bbd1b8uL,
    0x19a4c116b8d2d0c8uL, 0x1e376c085141ab53uL, 0x2748774cdf8eeb99uL, 0x34b0bcb5e19b48a8uL,
    0x391c0cb3c5c95a63uL, 0x4ed8aa4ae3418acbuL, 0x5b9cca4f7763e373uL, 0x682e6ff3d6b2b8a3uL,
    0x748f82ee5defb2fcuL, 0x78a5636f43172f60uL, 0x84c87814a1f0ab72uL, 0x8cc702081a6439ecuL,
    0x90befffa23631e28uL, 0xa4506cebde82bde9uL, 0xbef9a3f7b2c67915uL, 0xc67178f2e372532buL,
    0xca273eceea26619cuL, 0xd186b8c721c0c207uL, 0xeada7dd6cde0eb1euL, 0xf57d4f7fee6ed178uL,
    0x06f067aa72176fbauL, 0x0a637dc5a2c898a6uL, 0x113f9804bef90daeuL, 0x1b710b35131c471buL,
    0x28db77f523047d84uL, 0x32caab7b40c72493uL, 0x3c9ebe0a15c9bebcuL, 0x431d67c49c100d4cuL,
    0x4cc5d4becb3e42b6uL, 0x597f299cfc657e2auL, 0x5fcb6fab3ad6faecuL, 0x6c44198c4a475817uL
).map { it.toLong() }.toLongArray()

private val SHA384_INIT = ulongArrayOf(
    0xcbbb9d5dc1059ed8uL, 0x629a292a367cd507uL, 0x9159015a3070dd17uL, 0x152fecd8f70e5939uL,
    0x67332667ffc00b31uL, 0x8eb44a8768581511uL, 0xdb0c2e0d64f98fa7uL, 0x47b5481dbefa4fa4uL
).map { it.toLong() }.toLongArray()

internal fun sha384(input: ByteArray): ByteArray {
    val h = SHA384_INIT.copyOf()
    val bitLength = input.size.toLong() * 8
    val padded = run {
        var len = input.size + 1
        while (len % 128 != 112) len++
        val out = ByteArray(len + 16)
        input.copyInto(out)
        out[input.size] = 0x80.toByte()
        for (i in 0 until 8) out[out.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        out
    }
    val w = LongArray(80)
    for (block in padded.indices step 128) {
        for (t in 0 until 16) {
            var v = 0L
            for (j in 0 until 8) v = (v shl 8) or (padded[block + t * 8 + j].toLong() and 0xFF)
            w[t] = v
        }
        for (t in 16 until 80) {
            val s0 = w[t - 15].rotateRight(1) xor w[t - 15].rotateRight(8) xor (w[t - 15] ushr 7)
            val s1 = w[t - 2].rotateRight(19) xor w[t - 2].rotateRight(61) xor (w[t - 2] ushr 6)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 80) {
            val s1 = e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K512[t] + w[t]
            val s0 = a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }
    val out = ByteArray(48)
    for (i in 0 until 6) for (j in 0 until 8) out[i * 8 + j] = (h[i] ushr (56 - 8 * j)).toByte()
    return out
}
