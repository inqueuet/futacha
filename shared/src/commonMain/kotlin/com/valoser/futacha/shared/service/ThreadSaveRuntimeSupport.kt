package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SavedThreadMetadata

internal fun <K, V> createThreadSaveLruCache(maxSize: Int): MutableMap<K, V> {
    return ThreadSaveLruCache(maxSize)
}

private class ThreadSaveLruCache<K, V>(private val maxSize: Int) : MutableMap<K, V> {
    private val backing = LinkedHashMap<K, V>()

    override val size: Int
        get() = backing.size

    override fun isEmpty(): Boolean = backing.isEmpty()

    override fun containsKey(key: K): Boolean = backing.containsKey(key)

    override fun containsValue(value: V): Boolean = backing.containsValue(value)

    override fun get(key: K): V? {
        val value = backing.remove(key) ?: return null
        backing[key] = value
        return value
    }

    override fun put(key: K, value: V): V? {
        val previous = backing.remove(key)
        backing[key] = value
        trimToSize()
        return previous
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun remove(key: K): V? = backing.remove(key)

    override fun clear() {
        backing.clear()
    }

    override val keys: MutableSet<K>
        get() = backing.keys

    override val values: MutableCollection<V>
        get() = backing.values

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = backing.entries

    private fun trimToSize() {
        while (backing.size > maxSize) {
            val iterator = backing.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}

internal fun buildThreadSaveMetadataPayloadWithStableSize(
    metadata: SavedThreadMetadata,
    baseTotalSize: Long,
    encodeMetadata: (SavedThreadMetadata) -> String
): Pair<String, Long> {
    var estimatedSize = 0L
    var payload = encodeMetadata(metadata.copy(totalSize = baseTotalSize))
    repeat(8) {
        val size = measureThreadSaveUtf8ByteLength(payload)
        if (size == estimatedSize) {
            return payload to size
        }
        estimatedSize = size
        payload = encodeMetadata(metadata.copy(totalSize = baseTotalSize + size))
    }
    val finalSize = measureThreadSaveUtf8ByteLength(payload)
    return payload to finalSize
}

internal fun measureThreadSaveUtf8ByteLength(value: String): Long {
    var total = 0L
    var index = 0
    while (index < value.length) {
        val code = value[index].code
        val nextCode = value.getOrNull(index + 1)?.code
        val hasSurrogatePair =
            code in 0xD800..0xDBFF &&
                nextCode != null &&
                nextCode in 0xDC00..0xDFFF
        total += when {
            code <= 0x7F -> 1L
            code <= 0x7FF -> 2L
            hasSurrogatePair -> {
                index += 1
                4L
            }
            else -> 3L
        }
        index += 1
    }
    return total
}

internal fun buildThreadSaveMediaDownloadKey(
    url: String,
    requestType: ThreadSaveMediaRequestType
): String {
    return "${requestType.name}|${url.trim()}"
}

internal fun resolveThreadSaveFileName(
    url: String,
    extension: String,
    postId: String,
    timestampMillis: Long
): String {
    val candidate = url
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() }
    return candidate ?: "${postId}_${timestampMillis}.$extension"
}

internal fun enforceThreadSaveBudget(
    totalSizeBytes: Long,
    startedAtMillis: Long,
    nowMillis: Long,
    maxTotalSizeBytes: Long,
    maxSaveDurationMs: Long
) {
    val elapsed = nowMillis - startedAtMillis
    if (totalSizeBytes > maxTotalSizeBytes) {
        throw IllegalStateException(
            "Save aborted: total size exceeds limit (${totalSizeBytes / (1024 * 1024)}MB > ${maxTotalSizeBytes / (1024 * 1024)}MB)"
        )
    }
    if (elapsed > maxSaveDurationMs) {
        throw IllegalStateException(
            "Save aborted: exceeded time limit (${elapsed / 1000}s > ${maxSaveDurationMs / 1000}s)"
        )
    }
}
