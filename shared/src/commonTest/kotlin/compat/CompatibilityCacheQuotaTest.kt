package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.image.parseCompatCacheLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatibilityCacheQuotaTest {
    @Test
    fun parsesEveryPersistedCapacityDomain() {
        assertEquals(32L * 1024 * 1024, parseCompatThreadCacheQuotaBytes("32MB"))
        assertEquals(32L * 1024 * 1024, parseCompatThreadCacheQuotaBytes("32"))
        assertEquals(512L * 1024 * 1024, parseCompatThreadCacheQuotaBytes("512MB"))
        assertEquals(1024L * 1024 * 1024, parseCompatThreadCacheQuotaBytes("1GB"))
        assertEquals(1024L * 1024 * 1024, parseCompatThreadCacheQuotaBytes("1024"))
        assertEquals(2048L * 1024 * 1024, parseCompatThreadCacheQuotaBytes("2GB"))
        assertNull(parseCompatThreadCacheQuotaBytes("無制限"))
        assertNull(parseCompatThreadCacheQuotaBytes("131072"))
    }

    @Test
    fun missingOrCorruptValuesFallBackTo32MiB() {
        val expected = 32L * 1024 * 1024
        assertEquals(expected, parseCompatThreadCacheQuotaBytes(null))
        assertEquals(expected, parseCompatThreadCacheQuotaBytes("show"))
        assertEquals(expected, parseCompatThreadCacheQuotaBytes("-1MB"))
        assertEquals(expected, parseCompatThreadCacheQuotaBytes("4MB"))
    }

    @Test
    fun referenceApkUsesSeparateImageCachesAndSixParallelRequestsByDefault() {
        assertEquals(512L * 1024L * 1024L, parseCompatImageCacheQuotaBytes(null))
        assertEquals(32L * 1024L * 1024L, parseCompatImageCacheQuotaBytes("32"))
        assertEquals(128L * 1024L * 1024L, parseCompatCatalogImageCacheQuotaBytes(null))
        assertEquals(256L * 1024L * 1024L, parseCompatCatalogImageCacheQuotaBytes("256MB"))
        assertEquals(256L * 1024L * 1024L, parseCompatCatalogImageCacheQuotaBytes("256"))
        assertEquals(
            PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES,
            parseCompatImageCacheQuotaBytes("131072")
        )
        assertEquals(6, parseCompatImageParallelism(null))
        assertEquals(8, parseCompatImageParallelism("8"))
        assertEquals(6, parseCompatImageParallelism("7"))
    }

    @Test
    fun parsesFinalApkCacheLocationValuesAndEarlierDisplayLabels() {
        assertEquals(
            com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation.INTERNAL,
            parseCompatCacheLocation("internal")
        )
        assertEquals(
            com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation.DEVICE,
            parseCompatCacheLocation("device")
        )
        assertEquals(
            com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation.EXTERNAL_SD,
            parseCompatCacheLocation("sdcard")
        )
        assertEquals(
            com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation.EXTERNAL_SD,
            parseCompatCacheLocation("外部SDカード")
        )
    }

    @Test
    fun formatsLogicalCacheUsageWithTwoDecimalPlaces() {
        assertEquals("0.00MB", formatCompatCacheUsage(0L))
        assertEquals("1.50MB", formatCompatCacheUsage(1024L * 1024L + 512L * 1024L))
        assertEquals("2.00MB", formatCompatCacheUsage(2L * 1024L * 1024L - 1L))
        assertEquals("0.00MB", formatCompatCacheUsage(-1L))
    }

    @Test
    fun reportsOrdinaryAndCatalogImageCacheUsageSeparatelyLikeTheReferenceApk() {
        assertEquals(
            "画像 1.50MB / カタログ 2.00MB",
            formatCompatImageCacheUsage(
                CompatImageCacheUsage(
                    imageBytes = 1024L * 1024L + 512L * 1024L,
                    catalogBytes = 2L * 1024L * 1024L
                )
            )
        )
    }
}
