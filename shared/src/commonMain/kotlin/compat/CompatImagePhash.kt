package com.valoser.futacha.shared.compat

import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * The perceptual hash used by the reference Android viewer.
 *
 * The APK first scales an image to 32x32, converts it to luminance, computes
 * the 8x8 DCT, drops the DC coefficient when choosing the median, and emits
 * the 64 comparison bits as a 16-character hexadecimal string.  Keeping this
 * part platform independent makes stored NG entries compatible with Android
 * and gives the UI a deterministic testable rule even when decoding is done
 * by a platform image library.
 */
object CompatImagePhash {
    const val SIZE = 32
    const val HASH_SIZE = 8
    const val DEFAULT_THRESHOLD = 8
    const val MIN_THRESHOLD = 0
    const val MAX_THRESHOLD = 16

    /** Explanatory rows shown by sample/1.apk's image-NG threshold dialog. */
    val thresholdGuideRows: List<Pair<String, String>> = listOf(
        "0" to "同一画像",
        "1～5" to "ほぼ同一（リサイズ・JPEG差分）",
        "6～10" to "軽微編集",
        "11～20" to "類似",
        "20以上" to "別画像"
    )

    /** Row-major ARGB pixels. The caller is responsible for scaling to 32x32. */
    fun computeFromArgbPixels(pixels: IntArray): String? {
        if (pixels.size != SIZE * SIZE) return null
        val gray = DoubleArray(SIZE * SIZE)
        for (row in 0 until SIZE) {
            for (column in 0 until SIZE) {
                val argb = pixels[row * SIZE + column]
                val red = (argb ushr 16) and 0xff
                val green = (argb ushr 8) and 0xff
                val blue = argb and 0xff
                // Match ImagePhashUtil.computeScaled: gray[column][row].
                gray[column * SIZE + row] = red * 0.299 + green * 0.587 + blue * 0.114
            }
        }
        return computeFromGrayscale(gray)
    }

    /** 32x32 grayscale matrix in the same transposed layout as the APK. */
    fun computeFromGrayscale(gray: DoubleArray): String? {
        if (gray.size != SIZE * SIZE) return null
        val coefficients = DoubleArray(HASH_SIZE * HASH_SIZE)
        for (u in 0 until HASH_SIZE) {
            for (v in 0 until HASH_SIZE) {
                coefficients[u * HASH_SIZE + v] = dctCoefficient(gray, u, v)
            }
        }
        val medianValues = coefficients.drop(1).sorted()
        val median = medianValues[medianValues.size / 2]
        var hash = 0L
        coefficients.forEach { coefficient ->
            hash = (hash shl 1) or if (coefficient > median) 1L else 0L
        }
        // Java's Long.toHexString (used by the APK) treats the bit pattern as
        // unsigned when the highest bit is set.
        return hash.toULong().toString(16).padStart(16, '0')
    }

    fun hammingDistance(left: String?, right: String?): Int {
        if (left == null || right == null || left.length != right.length) return Int.MAX_VALUE
        var distance = 0
        left.indices.forEach { index ->
            val l = left[index].digitToIntOrNull(16) ?: return Int.MAX_VALUE
            val r = right[index].digitToIntOrNull(16) ?: return Int.MAX_VALUE
            distance += (l xor r).countOneBits()
        }
        return distance
    }

    fun isSimilar(left: String?, right: String?, threshold: Int): Boolean =
        hammingDistance(left, right) <= threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    fun progressLabel(processed: Int, total: Int): String =
        "NG画像判定中... ${processed.coerceIn(0, total.coerceAtLeast(0))}/${total.coerceAtLeast(0)}"

    private fun dctCoefficient(gray: DoubleArray, u: Int, v: Int): Double {
        var sum = 0.0
        for (x in 0 until SIZE) {
            for (y in 0 until SIZE) {
                sum += gray[x * SIZE + y] *
                    cos(((2 * x + 1) * u * PI) / (2.0 * SIZE)) *
                    cos(((2 * y + 1) * v * PI) / (2.0 * SIZE))
            }
        }
        val uScale = if (u == 0) 1.0 / sqrt(2.0) else 1.0
        val vScale = if (v == 0) 1.0 / sqrt(2.0) else 1.0
        return uScale * 0.25 * vScale * sum
    }
}

private const val COMPAT_IMAGE_PHASH_CACHE_PREFIX = "compat.imagePhash."

fun compatImagePhashCachePreferenceKey(url: String): String {
    var hash = 1469598103934665603UL
    url.trim().forEach { char ->
        hash = hash xor char.code.toULong()
        hash *= 1099511628211UL
    }
    return COMPAT_IMAGE_PHASH_CACHE_PREFIX + hash.toString(16).padStart(16, '0')
}

fun isValidCompatImagePhash(value: String?): Boolean =
    value?.length == 16 && value.all { it.digitToIntOrNull(16) != null }
