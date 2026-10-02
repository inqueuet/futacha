package com.valoser.futacha.shared.compat

import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatWatchRuleLimitsTest {
    private class Store {
        val preferences = MutableStateFlow<Map<String, String>>(emptyMap())
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader, arrayOf(CompatibilityStore::class.java)) { _, method, args ->
            when (method.name) {
                "getPreferences" -> preferences
                "savePreference" -> {
                    // Same limit as every platform store.
                    requireValidCompatPreference(args[0] as String, args[1] as String)
                    preferences.value += (args[0] as String to args[1] as String)
                    Unit
                }
                else -> error("Unexpected store call: ${method.name}")
            }
        } as CompatibilityStore
    }

    /** Rules whose stored (compact) JSON nearly fills one preference value. */
    private fun nearlyFullRules(): List<CompatWatchRule> {
        val rules = mutableListOf<CompatWatchRule>()
        var index = 0
        while (rules.size < MAX_COMPAT_WATCH_RULES) {
            val word = "監視ワード${index.toString().padStart(4, '0')}テスト用の長めの文字"
            val next = rules + CompatWatchRule(word, if (index % 2 == 0) "may-b" else null, index % 3 != 0)
            if (encodedLength(next) > MAX_COMPAT_PREFERENCE_VALUE_CHARS) return rules
            rules.clear(); rules += next; index++
        }
        error("expected the size limit before the count limit")
    }

    private fun encodedLength(rules: List<CompatWatchRule>) =
        Json.encodeToString(ListSerializer(CompatWatchRule.serializer()), rules).length

    @Test
    fun keywordBackupOfTheAppsOwnRulesAlwaysRestores() = runBlocking {
        val store = Store()
        val rules = nearlyFullRules()
        CompatWatcherRepository(store.store).saveRules(rules)
        val verbose = Json { encodeDefaults = true; explicitNulls = true }
            .encodeToString(ListSerializer(CompatWatchRule.serializer()), rules)
        // The backup's verbose form is over the limit; restore used to re-encode it that way and fail.
        assertTrue(verbose.length > MAX_COMPAT_PREFERENCE_VALUE_CHARS)

        val exported = encodeCompatWatchNgBackup(CompatSettingsBackup(exportedAtEpochMillis = 1, preferences = store.preferences.value))
        val restored = decodeCompatWatchNgBackup(exported)

        assertEquals(store.preferences.value[COMPAT_WATCH_RULES_KEY], restored.preferences[COMPAT_WATCH_RULES_KEY])
        assertEquals(rules, compatWatchRules(restored.preferences))
        val target = Store()
        CompatWatcherRepository(target.store).saveRules(compatWatchRules(restored.preferences))
        assertEquals(rules, compatWatchRules(target.preferences.value))
    }

    @Test
    fun rulesThatCannotBeStoredAreRejectedWithAMessageBeforeWriting() = runBlocking {
        val store = Store()
        val tooLong = (0 until 400).map { CompatWatchRule("w".repeat(60) + it) }
        val failure = assertFailsWith<IllegalArgumentException> { CompatWatcherRepository(store.store).saveRules(tooLong) }
        assertTrue(failure.message!!.contains("キーワード"))
        assertFalse(COMPAT_WATCH_RULES_KEY in store.preferences.value)
        assertFailsWith<IllegalArgumentException> {
            CompatWatcherRepository(store.store).saveRules((0..MAX_COMPAT_WATCH_RULES).map { CompatWatchRule("$it") })
        }
        Unit
    }

    @Test
    fun largeLegacyWordListStaysEditable() = runBlocking {
        // A words-only keyword backup accepts far more than the rules can store.
        val words = (0 until 3_000).map { "w$it" } + listOf("x".repeat(150), "猫")
        val legacy = decodeCompatWatchNgBackup(
            """{"schemaVersion":1,"exportedAtEpochMillis":1,"watchWords":[${words.joinToString(",") { "\"$it\"" }}]}"""
        )
        val rules = compatWatchRules(legacy.preferences)
        assertEquals(MAX_COMPAT_WATCH_RULES, rules.size)
        assertEquals("w0", rules.first().word)
        assertTrue(rules.all { it.word.length <= MAX_COMPAT_WATCH_RULE_WORD_CHARS })

        val store = Store()
        store.preferences.value = legacy.preferences
        // Deleting, reordering and adding (within the count) all save.
        CompatWatcherRepository(store.store).saveRules(rules.drop(1))
        assertEquals(rules.drop(1), compatWatchRules(store.preferences.value))
        CompatWatcherRepository(store.store).saveRules(rules.drop(1).reversed() + CompatWatchRule("犬", "may-b"))
        assertEquals("犬", compatWatchRules(store.preferences.value).last().word)
    }

    @Test
    fun legacyConversionStopsAtTheStoredSizeLimit() {
        val words = (0 until 400).map { "longwatchword-".repeat(5) + it }
        val rules = compatWatchRules(mapOf(COMPAT_WATCH_WORDS_PREFERENCE_KEY to words.joinToString("\n")))
        assertTrue(rules.size < words.size)
        assertEquals(words.take(rules.size), rules.map { it.word })
        assertTrue(encodedLength(rules) <= MAX_COMPAT_PREFERENCE_VALUE_CHARS)
        encodeValidCompatWatchRules(rules)
    }
}
