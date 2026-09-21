package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.repo.BoardRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CompatWatcherRepositoryTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private fun match(id: Int = 1, now: Long = 100) = CompatWatchMatch(
        CompatHistoryEntry("https://may.2chan.net/b/res/$id.htm", "https://may.2chan.net/b/res/$id.htm",
            board.key, board.name, "$id", "猫 $id", replyCount = id, contentUpdatedAtEpochMillis = now),
        isNew = false, keyword = "猫"
    )

    private class Fixture(boards: List<CompatBoard> = emptyList()) {
        val preferences = MutableStateFlow<Map<String, String>>(emptyMap())
        val history = MutableStateFlow<List<CompatHistoryEntry>>(emptyList())
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader, arrayOf(CompatibilityStore::class.java)) { _, method, args ->
            when (method.name) {
                "getPreferences" -> preferences
                "getHistory" -> history
                "getBoards" -> MutableStateFlow(boards)
                "getTabs" -> MutableStateFlow(emptyList<CompatTab>())
                "savePreference" -> {
                    requireValidCompatPreference(args[0] as String, args[1] as String)
                    preferences.value += (args[0] as String to args[1] as String)
                    Unit
                }
                "upsertHistory" -> {
                    val entry = args[0] as CompatHistoryEntry
                    history.value = history.value.filterNot { it.canonicalUrl == entry.canonicalUrl } + entry
                    Unit
                }
                else -> error("Unexpected store call: ${method.name}")
            }
        } as CompatibilityStore
    }

    @Test fun rulesMigrateScopeDisableReorderAndPersist() = runBlocking {
        val f = Fixture()
        f.preferences.value = mapOf(COMPAT_WATCH_WORDS_PREFERENCE_KEY to "猫\n犬")
        assertEquals(listOf("猫", "犬"), compatWatchWordsForBoard(f.preferences.value, "any"))
        val rules = listOf(CompatWatchRule("犬", "other"), CompatWatchRule("猫", board.key), CompatWatchRule("無効", enabled = false))
        CompatWatcherRepository(f.store).saveRules(rules)
        assertEquals(rules, compatWatchRules(f.preferences.value))
        assertEquals(listOf("猫"), compatWatchWordsForBoard(f.preferences.value, board.key))
        assertEquals(listOf("犬"), compatWatchWordsForBoard(f.preferences.value, "other"))
        CompatWatcherRepository(f.store).saveRules(emptyList())
        assertFalse(compatWatchEnabled(f.preferences.value))
        assertTrue(compatWatchWordsForBoard(f.preferences.value, "any").isEmpty())
    }

    @Test fun networkPolicyHonorsDisableAndWifi() {
        val p = mapOf(COMPAT_WATCH_WORDS_PREFERENCE_KEY to "猫", COMPAT_WATCH_WIFI_KEY to "ON")
        assertFalse(compatWatchAllowed(p, false))
        assertTrue(compatWatchAllowed(p, true))
        assertFalse(compatWatchAllowed(p + (COMPAT_WATCH_ENABLED_KEY to "OFF"), true))
    }

    @Test fun scopedRulesSurviveKeywordBackupWithoutLeakingIntoGeneralSettings() = runBlocking {
        val f = Fixture()
        val rules = listOf(CompatWatchRule("猫", board.key, false), CompatWatchRule("犬"))
        CompatWatcherRepository(f.store).saveRules(rules)
        val backup = CompatSettingsBackup(exportedAtEpochMillis = 100, preferences = f.preferences.value)
        assertEquals(rules, compatWatchRules(decodeCompatWatchNgBackup(encodeCompatWatchNgBackup(backup)).preferences))
        assertFalse(COMPAT_WATCH_RULES_KEY in backup.settingsOnly().preferences)
        assertTrue(COMPAT_WATCH_RULES_KEY in backup.watchAndNgOnly().preferences)
    }

    @Test fun resultsAreIndependentDeduplicatedAndPreserveFirstDetection() = runBlocking {
        val f = Fixture()
        f.history.value = listOf(match().history)
        val r = CompatWatcherRepository(f.store)
        assertTrue(r.record(match())) // Already-read threads still constitute a new detection.
        assertFalse(r.record(match(now = 200)))
        assertEquals(100L, r.load(200).single().insertedAtEpochMillis)
        assertEquals(200L, r.load(200).single().history.contentUpdatedAtEpochMillis)
        assertEquals("猫", r.load(200).single().keyword)
        r.record(match(2, 201))
        r.delete(match().history.canonicalUrl)
        assertEquals("2", r.load(202).single().history.threadNo)
        r.deleteAll()
        assertTrue(r.load(202).isEmpty())
        assertEquals(listOf(match().history), f.history.value)
    }

    @Test fun expirationAndCapacityAreBounded() = runBlocking {
        val r = CompatWatcherRepository(Fixture().store)
        r.record(match())
        assertEquals(1, r.load(100 + COMPAT_WATCH_RETENTION_MILLIS).size)
        assertTrue(r.load(101 + COMPAT_WATCH_RETENTION_MILLIS).isEmpty())
        repeat(MAX_COMPAT_WATCH_RESULTS + 2) { r.record(match(it, it.toLong())) }
        val loaded = r.load(1000)
        assertEquals(MAX_COMPAT_WATCH_RESULTS, loaded.size)
        assertFalse(loaded.any { it.history.threadNo == "0" || it.history.threadNo == "1" })
    }

    @Test fun concurrentWritersAndStaleProbeDoNotLoseOrResurrectResults() = runBlocking {
        val f = Fixture()
        val r = CompatWatcherRepository(f.store)
        (1..20).map { id -> async { CompatWatcherRepository(f.store).record(match(id)) } }.awaitAll()
        assertEquals(20, r.load(100).size)
        val checked = r.load(100).first { it.history.threadNo == "1" }
        r.record(match(1, 101))
        r.markGone(checked)
        assertTrue(r.load(101).first { it.history.threadNo == "1" }.active)
        val fresh = r.load(101).first { it.history.threadNo == "1" }
        r.markGone(fresh)
        assertFalse(r.load(101).first { it.history.threadNo == "1" }.active)
        r.delete(fresh.history.canonicalUrl)
        r.markGone(fresh)
        assertEquals(19, r.load(101).size)
    }

    @Test fun backgroundUsesScopedRulesAndConfirmsGoneWithoutTreatingNetworkFailureAsDead() = runBlocking {
        val f = Fixture(listOf(board))
        val r = CompatWatcherRepository(f.store)
        r.saveRules(listOf(CompatWatchRule("猫", board.key)))
        r.record(match(2, 10)); r.record(match(3, 10))
        val repository = Proxy.newProxyInstance(BoardRepository::class.java.classLoader, arrayOf(BoardRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getCatalog" -> listOf(CatalogItem("1", match().history.originalUrl, "猫のスレ", null, null, replyCount = 3))
                "probeThreadGone" -> if ((args[0] as String).contains("/2.htm")) true else error("offline")
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as BoardRepository
        val result = refreshCompatTabsInBackground(f.store, repository, 100, checkUpdates = false, checkExistence = false, checkWatchWords = true)
        assertEquals(1, result.newWatchMatches.size)
        assertTrue(f.history.value.isEmpty(), "巡回で閲覧履歴を作らない")
        val entries = r.load(100).associateBy { it.history.threadNo }
        assertTrue(entries.getValue("1").active)
        assertFalse(entries.getValue("2").active)
        assertTrue(entries.getValue("3").active)
        assertTrue(result.failures > 0)
        assertTrue(refreshCompatTabsInBackground(f.store, repository, 101, checkUpdates = false, checkExistence = false, checkWatchWords = true).newWatchMatches.isEmpty())
    }

    @Test fun cancelledRefreshDoesNotSwallowCancellation() = runBlocking<Unit> {
        val f = Fixture(listOf(board))
        CompatWatcherRepository(f.store).saveRules(listOf(CompatWatchRule("猫")))
        val repository = Proxy.newProxyInstance(BoardRepository::class.java.classLoader, arrayOf(BoardRepository::class.java)) { _, _, _ ->
            throw CancellationException("cancel")
        } as BoardRepository
        assertFailsWith<CancellationException> {
            refreshCompatTabsInBackground(f.store, repository, 100, checkUpdates = false, checkExistence = false, checkWatchWords = true)
        }
    }

    @Test fun matchingFoldsAsciiAndVoicedHalfwidthKana() {
        assertEquals(normalizeCompatWatchText("1 ガンダム"), normalizeCompatWatchText("① ｶﾞﾝﾀﾞﾑ"))
        val matches = collectCompatWatchMatches(board,
            listOf(CatalogItem("1", match().history.originalUrl, "ＡＢＣ ガンダム", null, null, replyCount = 0)),
            listOf("abc", "ｶﾞﾝﾀﾞﾑ"), emptyList(), 100)
        assertEquals("abc / ｶﾞﾝﾀﾞﾑ", matches.single().keyword)
    }
}
