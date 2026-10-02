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

internal const val MAX_COMPAT_WATCH_RULES = 500
internal const val MAX_COMPAT_WATCH_RULE_WORD_CHARS = 100

fun compatWatchRules(preferences: Map<String, String>): List<CompatWatchRule> {
    val encoded = preferences[COMPAT_WATCH_RULES_KEY]
    return if (encoded == null) {
        legacyCompatWatchRules(parseCompatWatchWords(preferences[COMPAT_WATCH_WORDS_PREFERENCE_KEY]))
    } else runCatching { watcherJson.decodeFromString<List<CompatWatchRule>>(encoded) }.getOrDefault(emptyList())
}

/**
 * The one set of limits for stored rules: at most [MAX_COMPAT_WATCH_RULES]
 * rules of 1..[MAX_COMPAT_WATCH_RULE_WORD_CHARS] characters whose compact JSON
 * fits one preference value. Saving, keyword-backup restore and the legacy
 * word-list conversion all use it, so whatever was saved or exported restores.
 * Returns the value to store.
 */
fun encodeValidCompatWatchRules(rules: List<CompatWatchRule>): String {
    require(rules.size <= MAX_COMPAT_WATCH_RULES) { "キーワードは${MAX_COMPAT_WATCH_RULES}件までです" }
    require(rules.all { it.word.isNotBlank() && it.word.length <= MAX_COMPAT_WATCH_RULE_WORD_CHARS }) {
        "キーワードは1〜${MAX_COMPAT_WATCH_RULE_WORD_CHARS}文字で入力してください"
    }
    val encoded = watcherJson.encodeToString(rules)
    require(isValidCompatPreference(COMPAT_WATCH_RULES_KEY, encoded)) {
        "キーワードの合計が長すぎます。キーワードを減らしてください"
    }
    return encoded
}

/**
 * Words from the pre-rules list (an older version or a words-only keyword
 * backup, which allow far more text) become the leading rules that can still
 * be saved, so editing, reordering or deleting one never fails on the rest.
 */
private fun legacyCompatWatchRules(words: List<String>): List<CompatWatchRule> {
    val rules = mutableListOf<CompatWatchRule>()
    var encodedChars = 2 // []
    for (word in words) {
        if (rules.size >= MAX_COMPAT_WATCH_RULES) break
        if (word.isBlank() || word.length > MAX_COMPAT_WATCH_RULE_WORD_CHARS) continue
        val rule = CompatWatchRule(word)
        val ruleChars = watcherJson.encodeToString(rule).length + if (rules.isEmpty()) 0 else 1
        if (encodedChars + ruleChars > MAX_COMPAT_PREFERENCE_VALUE_CHARS) break
        encodedChars += ruleChars
        rules += rule
    }
    return rules
}

/**
 * Board-scoped rules after a board's key was corrected from [fromBoardKey] to
 * [toBoardKey] (G-16/P4-2); null when nothing refers to the old key, the value
 * cannot be read, or the moved rules would no longer fit one stored value.
 * A rule already present for the corrected board is kept once.
 */
fun remapCompatWatchRulesBoardKey(encoded: String, fromBoardKey: String, toBoardKey: String): String? {
    val rules = runCatching { watcherJson.decodeFromString<List<CompatWatchRule>>(encoded) }.getOrNull()
        ?: return null
    if (rules.none { it.boardKey == fromBoardKey }) return null
    val moved = rules.map { rule -> if (rule.boardKey == fromBoardKey) rule.copy(boardKey = toBoardKey) else rule }
        .distinct()
    return runCatching { encodeValidCompatWatchRules(moved) }.getOrNull()
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
        store.savePreference(COMPAT_WATCH_RULES_KEY, encodeValidCompatWatchRules(rules))
    }

    private suspend fun slots(): Map<String, CompatWatchResult> = decodeSlots(store.preferences.first())

    private fun decodeSlots(preferences: Map<String, String>): Map<String, CompatWatchResult> = preferences
        .filterKeys { it.startsWith(RESULT_PREFIX) }
        .mapNotNull { (key, value) -> runCatching {
            key to watcherJson.decodeFromString<CompatWatchResult>(value)
        }.getOrNull() }.toMap()

    /**
     * Live results after dropping expired ones. Expired rows, and blank rows left
     * by older versions, are added to [removals] (null = delete the row) so the
     * caller writes everything in one change.
     */
    private suspend fun pruneInto(now: Long, removals: MutableMap<String, String?>): Map<String, CompatWatchResult> {
        val preferences = store.preferences.first()
        preferences.filter { (key, value) -> key.startsWith(RESULT_PREFIX) && value.isBlank() }
            .keys.forEach { removals[it] = null }
        val all = decodeSlots(preferences)
        val expired = all.filterValues { now - it.insertedAtEpochMillis > COMPAT_WATCH_RETENTION_MILLIS }
        expired.keys.forEach { removals[it] = null }
        return all - expired.keys
    }

    suspend fun load(now: Long): List<CompatWatchResult> = watcherMutex.withLock {
        val removals = mutableMapOf<String, String?>()
        val live = pruneInto(now, removals)
        if (removals.isNotEmpty()) store.savePreferences(removals)
        live.values.sortedByDescending { it.history.contentUpdatedAtEpochMillis }
    }

    /** Returns true for a newly detected URL, independently of whether it has been read. */
    suspend fun record(match: CompatWatchMatch): Boolean =
        match.history.canonicalUrl in recordAll(listOf(match))

    /**
     * Records every match of one catalog with a single preference write, applying
     * the same rules as recording them one by one. Returns the newly detected URLs.
     */
    suspend fun recordAll(matches: List<CompatWatchMatch>): Set<String> = watcherMutex.withLock {
        if (matches.isEmpty()) return@withLock emptySet()
        val writes = mutableMapOf<String, String?>()
        val live = pruneInto(matches.minOf { it.history.contentUpdatedAtEpochMillis }, writes).toMutableMap()
        val newUrls = linkedSetOf<String>()
        matches.forEach { match ->
            val now = match.history.contentUpdatedAtEpochMillis
            val previous = live.entries.firstOrNull { it.value.history.canonicalUrl == match.history.canonicalUrl }
            if (previous != null && previous.value.history.contentUpdatedAtEpochMillis > now) return@forEach
            val slot = previous?.key ?: (0 until MAX_COMPAT_WATCH_RESULTS)
                .map { "$RESULT_PREFIX$it" }.firstOrNull { it !in live }
                ?: live.minBy { it.value.insertedAtEpochMillis }.key
            val result = CompatWatchResult(
                history = match.history.copy(title = match.history.title.take(1000), scrollAnchor = ScrollAnchor()),
                keyword = match.keyword.take(1000),
                insertedAtEpochMillis = previous?.value?.insertedAtEpochMillis ?: now,
                checkedAtEpochMillis = now
            )
            live[slot] = result
            writes[slot] = watcherJson.encodeToString(result)
            if (previous == null) newUrls += match.history.canonicalUrl
        }
        if (writes.isNotEmpty()) store.savePreferences(writes)
        newUrls
    }

    suspend fun delete(url: String) = watcherMutex.withLock {
        val keys = slots().filterValues { it.history.canonicalUrl == url }.keys
        if (keys.isNotEmpty()) store.savePreferences(keys.associateWith { null })
    }

    suspend fun deleteAll() = watcherMutex.withLock {
        val keys = slots().keys
        if (keys.isNotEmpty()) store.savePreferences(keys.associateWith { null })
    }

    suspend fun markGone(checked: CompatWatchResult) = markChecked(checked, true, checked.history.contentUpdatedAtEpochMillis)

    suspend fun markChecked(checked: CompatWatchResult, gone: Boolean, now: Long) =
        markCheckedAll(mapOf(checked to gone), now)

    /**
     * Stores several probe outcomes in one write. Each applies only while the
     * stored result still equals the checked one, so deleted results are not
     * resurrected and a later catalog refresh is not overwritten.
     */
    suspend fun markCheckedAll(outcomes: Map<CompatWatchResult, Boolean>, now: Long) = watcherMutex.withLock {
        if (outcomes.isEmpty()) return@withLock
        val writes = slots().entries.mapNotNull { (slot, current) ->
            val gone = outcomes[current] ?: return@mapNotNull null
            slot to watcherJson.encodeToString(current.copy(active = !gone, checkedAtEpochMillis = now))
        }.toMap()
        if (writes.isNotEmpty()) store.savePreferences(writes)
    }
}
