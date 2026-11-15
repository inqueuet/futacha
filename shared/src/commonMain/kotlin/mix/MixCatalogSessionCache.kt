package com.valoser.futacha.shared.mix

data class MixCacheKey(val mixId: String, val order: MixOrderKind, val membersHash: Int, val namespace: String = "default")

/** プロセスが続く間だけ持つ一覧の入れ物。新しく使った順に maxEntries 個まで。 */
class MixCatalogSessionCache(private val maxEntries: Int = 4) {
    private val results = LinkedHashMap<MixCacheKey, MixFetchResult>()
    private val finished = HashMap<String, Long>()

    fun get(key: MixCacheKey): MixFetchResult? {
        val v = results.remove(key) ?: return null
        results[key] = v
        return v
    }

    fun put(key: MixCacheKey, result: MixFetchResult) {
        results.remove(key)
        results[key] = result
        while (results.size > maxEntries) results.remove(results.keys.first())
    }

    fun lastFinishedAt(mixId: String): Long? = finished[mixId]
    fun markFinished(mixId: String, at: Long) { finished[mixId] = at }

    fun remove(mixId: String) {
        results.keys.removeAll { it.mixId == mixId }
        finished.remove(mixId)
    }
}
