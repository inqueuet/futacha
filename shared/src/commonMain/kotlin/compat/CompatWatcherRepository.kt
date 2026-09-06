package com.valoser.futacha.shared.compat

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val COMPAT_WATCH_RULES_KEY = "compat.watcher.rules"
const val COMPAT_WATCH_ENABLED_KEY = "compat.watcher.enabled"
const val COMPAT_WATCH_WIFI_KEY = "compat.watcher.wifiOnly"
const val COMPAT_WATCH_EXTERNAL_KEY = "compat.watcher.external"
const val COMPAT_WATCH_NOTIFY_KEY = "compat.watcher.notify"
private const val RESULT_PREFIX = "compat.watcher.result."
internal const val MAX_COMPAT_WATCH_RESULTS = 500
internal const val COMPAT_WATCH_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
private val watcherJson = Json { ignoreUnknownKeys = true }
private val watcherMutex = Mutex()

@Serializable
data class CompatWatchRule(val word: String, val boardKey: String? = null, val enabled: Boolean = true)

@Serializable
data class CompatWatchResult(
    val history: CompatHistoryEntry,
    val keyword: String,
    val insertedAtEpochMillis: Long,
    val active: Boolean = true,
    val checkedAtEpochMillis: Long = 0
)

fun compatWatchRules(preferences: Map<String, String>): List<CompatWatchRule> {
    val encoded = preferences[COMPAT_WATCH_RULES_KEY]
    return if (encoded == null) {
        parseCompatWatchWords(preferences[COMPAT_WATCH_WORDS_PREFERENCE_KEY]).map { CompatWatchRule(it) }
    } else runCatching { watcherJson.decodeFromString<List<CompatWatchRule>>(encoded) }.getOrDefault(emptyList())
}

fun compatWatchWordsForBoard(preferences: Map<String, String>, boardKey: String): List<String> =
    compatWatchRules(preferences).filter { it.enabled && (it.boardKey == null || it.boardKey == boardKey) }
        .map { it.word }.filter { it.isNotBlank() }.distinct()

fun compatWatchEnabled(preferences: Map<String, String>): Boolean =
    preferences[COMPAT_WATCH_ENABLED_KEY] != "OFF" && compatWatchRules(preferences).any { it.enabled && it.word.isNotBlank() }

fun compatWatchAllowed(preferences: Map<String, String>, wifi: Boolean): Boolean =
    compatWatchEnabled(preferences) && (preferences[COMPAT_WATCH_WIFI_KEY] != "ON" || wifi)

/** Fixed slots bound preference growth. Results never alias or delete browsing history. */
class CompatWatcherRepository(private val store: CompatibilityStore) {
    suspend fun saveRules(rules: List<CompatWatchRule>) {
        require(rules.size <= 500) { "キーワードは500件までです" }
        require(rules.all { it.word.isNotBlank() && it.word.length <= 100 }) { "キーワードは1〜100文字で入力してください" }
        val encoded = watcherJson.encodeToString(rules)
        requireValidCompatPreference(COMPAT_WATCH_RULES_KEY, encoded)
        store.savePreference(COMPAT_WATCH_RULES_KEY, encoded)
    }

    private suspend fun slots(): Map<String, CompatWatchResult> = store.preferences.first()
        .filterKeys { it.startsWith(RESULT_PREFIX) }
        .mapNotNull { (key, value) -> runCatching {
            key to watcherJson.decodeFromString<CompatWatchResult>(value)
        }.getOrNull() }.toMap()

    private suspend fun prune(now: Long): Map<String, CompatWatchResult> {
        val all = slots()
        val expired = all.filterValues { now - it.insertedAtEpochMillis > COMPAT_WATCH_RETENTION_MILLIS }
        expired.keys.forEach { store.savePreference(it, "") }
        return all - expired.keys
    }

    suspend fun load(now: Long): List<CompatWatchResult> = watcherMutex.withLock {
        prune(now).values.sortedByDescending { it.history.contentUpdatedAtEpochMillis }
    }

    /** Returns true for a newly detected URL, independently of whether it has been read. */
    suspend fun record(match: CompatWatchMatch): Boolean = watcherMutex.withLock {
        val now = match.history.contentUpdatedAtEpochMillis
        val all = prune(now)
        val previous = all.entries.firstOrNull { it.value.history.canonicalUrl == match.history.canonicalUrl }
        if (previous != null && previous.value.history.contentUpdatedAtEpochMillis > now) return@withLock false
        val slot = previous?.key ?: (0 until MAX_COMPAT_WATCH_RESULTS)
            .map { "$RESULT_PREFIX$it" }.firstOrNull { it !in all }
            ?: all.minBy { it.value.insertedAtEpochMillis }.key
        val result = CompatWatchResult(
            history = match.history.copy(title = match.history.title.take(1000), scrollAnchor = ScrollAnchor()),
            keyword = match.keyword.take(1000),
            insertedAtEpochMillis = previous?.value?.insertedAtEpochMillis ?: now,
            checkedAtEpochMillis = now
        )
        store.savePreference(slot, watcherJson.encodeToString(result))
        previous == null
    }

    suspend fun delete(url: String) = watcherMutex.withLock {
        slots().filterValues { it.history.canonicalUrl == url }.keys.forEach { store.savePreference(it, "") }
    }

    suspend fun deleteAll() = watcherMutex.withLock {
        slots().keys.forEach { store.savePreference(it, "") }
    }

    suspend fun markGone(checked: CompatWatchResult) = markChecked(checked, true, checked.history.contentUpdatedAtEpochMillis)

    suspend fun markChecked(checked: CompatWatchResult, gone: Boolean, now: Long) = watcherMutex.withLock {
        // Do not resurrect deleted results or overwrite a later successful catalog refresh.
        slots().entries.firstOrNull { it.value == checked }?.let { (slot, current) ->
            store.savePreference(slot, watcherJson.encodeToString(current.copy(active = !gone, checkedAtEpochMillis = now)))
        }
    }
}
