package com.valoser.futacha.shared.compat

const val COMPAT_THREAD_CACHE_PREFERENCE_KEY = "compat.storage.スレッドキャッシュ上限"
const val COMPAT_IMAGE_CACHE_PREFERENCE_KEY = "compat.storage.commonImageCache"
const val COMPAT_CATALOG_IMAGE_CACHE_PREFERENCE_KEY = "compat.storage.commonCatalogImageCache"
const val COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY = "compat.storage.dummyImageCacheLocation"
const val COMPAT_CATALOG_IMAGE_CACHE_LOCATION_PREFERENCE_KEY =
    "compat.storage.dummyCatalogImageCacheLocation"
const val COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY = "compat.network.networkImageParallel"
const val DEFAULT_COMPAT_THREAD_CACHE_MEBIBYTES = 32L
// sample/1.apk changed the ordinary image cache default to 512 MiB while
// keeping catalog thumbnails in their own 128 MiB cache.
const val DEFAULT_COMPAT_IMAGE_CACHE_MEBIBYTES = 512L
const val DEFAULT_COMPAT_CATALOG_IMAGE_CACHE_MEBIBYTES = 128L
const val DEFAULT_COMPAT_IMAGE_PARALLELISM = 6
const val MAX_COMPAT_THREAD_SNAPSHOT_POSTS = 2050

// Coil's DiskCache requires a finite positive limit. "Unlimited" means that the
// user did not choose an ordinary quota, not that one app may consume an entire
// filesystem. Keep a generous emergency ceiling so corrupt/missing eviction
// metadata cannot grow toward the previous 2 TiB sentinel on a phone.
const val PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES = 4L * 1024L * 1024L * 1024L

private const val BYTES_PER_MEBIBYTE = 1024L * 1024L

/** Returns null for the explicit unlimited choice. Unknown/corrupt values use 32 MiB. */
fun parseCompatThreadCacheQuotaBytes(value: String?): Long? {
    val normalized = value?.trim().orEmpty()
    if (normalized in setOf("131072", "無制限")) return null
    val mebibytes = compatCacheMebibytes(normalized) ?: DEFAULT_COMPAT_THREAD_CACHE_MEBIBYTES
    return mebibytes * BYTES_PER_MEBIBYTE
}

/** Returns the finite byte limit used by Coil for the APK's image-cache choices. */
fun parseCompatImageCacheQuotaBytes(value: String?): Long {
    val normalized = value?.trim().orEmpty()
    if (normalized in setOf("131072", "無制限")) return PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES
    val mebibytes = compatCacheMebibytes(normalized) ?: DEFAULT_COMPAT_IMAGE_CACHE_MEBIBYTES
    return mebibytes * BYTES_PER_MEBIBYTE
}

fun parseCompatCatalogImageCacheQuotaBytes(value: String?): Long {
    val normalized = value?.trim().orEmpty()
    if (normalized in setOf("131072", "無制限")) return PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES
    val mebibytes = compatCacheMebibytes(normalized) ?: DEFAULT_COMPAT_CATALOG_IMAGE_CACHE_MEBIBYTES
    return mebibytes * BYTES_PER_MEBIBYTE
}

private fun compatCacheMebibytes(value: String): Long? = when (value) {
    "32", "32MB" -> 32L
    "64", "64MB" -> 64L
    "128", "128MB" -> 128L
    "256", "256MB" -> 256L
    "512", "512MB" -> 512L
    "1024", "1GB" -> 1024L
    "2048", "2GB" -> 2048L
    else -> null
}

/** Values exposed by sample/1.apk: 1,2,3,4,5,6(default),8. */
fun parseCompatImageParallelism(value: String?): Int =
    value?.trim()?.toIntOrNull()?.takeIf { it in setOf(1, 2, 3, 4, 5, 6, 8) }
        ?: DEFAULT_COMPAT_IMAGE_PARALLELISM

fun formatCompatCacheUsage(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val whole = safeBytes / BYTES_PER_MEBIBYTE
    val hundredths = ((safeBytes % BYTES_PER_MEBIBYTE) * 100L + BYTES_PER_MEBIBYTE / 2L) /
        BYTES_PER_MEBIBYTE
    val normalizedWhole = whole + hundredths / 100L
    val normalizedFraction = (hundredths % 100L).toString().padStart(2, '0')
    return "$normalizedWhole.${normalizedFraction}MB"
}

data class CompatImageCacheUsage(
    val imageBytes: Long,
    val catalogBytes: Long
)

/**
 * sample/1.apk deliberately reports the ordinary and catalog caches
 * separately so a thumbnail-only leak is visible to the user.
 */
fun formatCompatImageCacheUsage(usage: CompatImageCacheUsage): String =
    "画像 ${formatCompatCacheUsage(usage.imageBytes)} / " +
        "カタログ ${formatCompatCacheUsage(usage.catalogBytes)}"
