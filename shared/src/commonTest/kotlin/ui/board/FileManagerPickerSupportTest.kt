package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileManagerPickerSupportTest {
    @Test
    fun cacheFreshnessRequiresNowWithinTtl() {
        assertTrue(isFileManagerPickerCacheFresh(loadedAtMillis = 1_000, nowMillis = 1_500, ttlMillis = 1_000))
        assertTrue(isFileManagerPickerCacheFresh(loadedAtMillis = 1_000, nowMillis = 2_000, ttlMillis = 1_000))
        assertFalse(isFileManagerPickerCacheFresh(loadedAtMillis = 1_000, nowMillis = 2_001, ttlMillis = 1_000))
        assertFalse(isFileManagerPickerCacheFresh(loadedAtMillis = 1_000, nowMillis = 999, ttlMillis = 1_000))
    }

    @Test
    fun cachedValueReturnsOnlyFreshEntries() {
        val cache = FileManagerPickerCacheState(value = listOf("a"), loadedAtMillis = 1_000)

        assertEquals(listOf("a"), fileManagerPickerCachedValueOrNull(cache, nowMillis = 1_500, ttlMillis = 1_000))
        assertNull(fileManagerPickerCachedValueOrNull(cache, nowMillis = 2_001, ttlMillis = 1_000))
    }

    @Test
    fun distinctFileManagerPackagesKeepsFirstNonBlankPackage() {
        val values = listOf(" files ", "files", "", "docs", "docs", "media")

        assertEquals(
            listOf(" files ", "docs", "media"),
            distinctFileManagerPackages(values) { it }
        )
    }

    @Test
    fun trimFileManagerIconCacheKeysReturnsOldestOverflowKeys() {
        assertEquals(
            listOf("a", "b"),
            trimFileManagerIconCacheKeys(
                keysInAccessOrder = listOf("a", "b", "c", "d"),
                maxEntries = 2
            )
        )
        assertEquals(
            emptyList(),
            trimFileManagerIconCacheKeys(
                keysInAccessOrder = listOf("a", "b"),
                maxEntries = 2
            )
        )
    }
}
