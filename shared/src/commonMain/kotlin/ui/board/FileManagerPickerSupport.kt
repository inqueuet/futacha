package com.valoser.futacha.shared.ui.board

internal const val FILE_MANAGER_PICKER_CACHE_TTL_MILLIS = 5 * 60 * 1_000L
internal const val FILE_MANAGER_ICON_CACHE_MAX_ENTRIES = 64

internal data class FileManagerPickerCacheState<T>(
    val value: T,
    val loadedAtMillis: Long
)

internal fun isFileManagerPickerCacheFresh(
    loadedAtMillis: Long,
    nowMillis: Long,
    ttlMillis: Long = FILE_MANAGER_PICKER_CACHE_TTL_MILLIS
): Boolean {
    return nowMillis - loadedAtMillis in 0..ttlMillis.coerceAtLeast(0L)
}

internal fun <T> fileManagerPickerCachedValueOrNull(
    cacheState: FileManagerPickerCacheState<T>?,
    nowMillis: Long,
    ttlMillis: Long = FILE_MANAGER_PICKER_CACHE_TTL_MILLIS
): T? {
    return cacheState
        ?.takeIf { isFileManagerPickerCacheFresh(it.loadedAtMillis, nowMillis, ttlMillis) }
        ?.value
}

internal fun normalizeFileManagerPackageName(packageName: String?): String? {
    return packageName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

internal fun <T> distinctFileManagerPackages(
    items: Iterable<T>,
    packageNameOf: (T) -> String?
): List<T> {
    val seenPackages = linkedSetOf<String>()
    val distinctItems = mutableListOf<T>()
    items.forEach { item ->
        val packageName = normalizeFileManagerPackageName(packageNameOf(item)) ?: return@forEach
        if (seenPackages.add(packageName)) {
            distinctItems += item
        }
    }
    return distinctItems
}

internal fun trimFileManagerIconCacheKeys(
    keysInAccessOrder: List<String>,
    maxEntries: Int = FILE_MANAGER_ICON_CACHE_MAX_ENTRIES
): List<String> {
    val overflowCount = keysInAccessOrder.size - maxEntries.coerceAtLeast(0)
    return if (overflowCount <= 0) emptyList() else keysInAccessOrder.take(overflowCount)
}
