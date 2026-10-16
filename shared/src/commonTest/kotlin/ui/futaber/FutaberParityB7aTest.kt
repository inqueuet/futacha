package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.COMPAT_WATCH_RULES_KEY
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.encodeValidCompatWatchRules
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.buildCatalogExternalAppUrl
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The B7a parity batch of the ふたばー風 mode (the catalog: そ順・監視, watch words, reply priority, generations, tools). */
class FutaberParityB7aTest {
    private fun item(id: String, title: String?, replies: Int = 0) = CatalogItem(
        id = id, threadUrl = "https://may.2chan.net/b/res/$id.htm", title = title,
        thumbnailUrl = null, fullImageUrl = null, replyCount = replies
    )

    private val may = BoardSummary("may-b", "虹裏may", "二次元", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "チュートリアル", "https://www.example.com/t/futaba.php", "")

    // ---- the switches ----

    @Test fun theNewExtensionsAreOffByDefaultAndReadOnlyAnOn() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extCatalogModes); assertFalse(defaults.extCatalogWatchHighlight); assertFalse(defaults.extReplyPriority)
        assertFalse(defaults.extCatalogHistory); assertFalse(defaults.extCatalogTools)
        assertEquals(0, defaults.replyPriorityThreshold)
        assertEquals(defaults, FutaberDisplaySettings())
        val keys = listOf(
            FutaberSettingKeys.EXT_CATALOG_MODES, FutaberSettingKeys.EXT_CATALOG_WATCH_HIGHLIGHT, FutaberSettingKeys.EXT_REPLY_PRIORITY,
            FutaberSettingKeys.EXT_CATALOG_HISTORY, FutaberSettingKeys.EXT_CATALOG_TOOLS
        )
        assertTrue(keys.all { it.startsWith("compat.futaber.ext.") })
        val on = FutaberDisplaySettings.from(keys.associateWith { "ON" })
        assertTrue(on.extCatalogModes && on.extCatalogWatchHighlight && on.extReplyPriority && on.extCatalogHistory && on.extCatalogTools)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_CATALOG_TOOLS to "on")).extCatalogTools)
    }

    @Test fun theThresholdIsTheSharedValueKeptBetweenZeroAndThirty() {
        assertEquals("compat.catalog.delayFewReplies", FutaberSettingKeys.REPLY_PRIORITY_THRESHOLD)
        assertEquals(12, FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.REPLY_PRIORITY_THRESHOLD to "12")).replyPriorityThreshold)
        assertEquals(30, futaberReplyPriorityThreshold(mapOf(FutaberSettingKeys.REPLY_PRIORITY_THRESHOLD to "99")))
        assertEquals(0, futaberReplyPriorityThreshold(mapOf(FutaberSettingKeys.REPLY_PRIORITY_THRESHOLD to "abc")))
        assertEquals(0, futaberReplyPriorityThreshold(emptyMap()))
    }

    // ---- C04 ----

    @Test fun theSortMenuGetsSoAndWatchOnlyWithTheExtension() {
        assertEquals(futaberCatalogSorts, futaberCatalogSortsFor(false))
        val extended = futaberCatalogSortsFor(true)
        assertEquals(8, extended.size)
        assertEquals(futaberCatalogSorts, extended.take(6))
        assertEquals(listOf("そ順" to CatalogMode.So, "監視" to CatalogMode.WatchWords), extended.drop(6).map { it.label to it.mode })
    }

    @Test fun aStoredSoOrWatchReadsAsTheDefaultWhileTheExtensionIsOff() {
        for (mode in listOf(CatalogMode.So, CatalogMode.WatchWords)) {
            assertEquals(CatalogMode.Catalog, futaberCatalogModeFromPersisted(mode.name))
            assertEquals(mode, futaberCatalogModeFromPersisted(mode.name, extraModes = true))
            val shared = mapOf(FutaberPreferenceKeys.CATALOG_SORT to mode.name)
            assertEquals(CatalogMode.Catalog, futaberCatalogSortFor(shared, "may", perBoard = false))
            assertEquals(mode, futaberCatalogSortFor(shared, "may", perBoard = false, extraModes = true))
            // The per-board record too: off → "通常", not the shared value.
            val perBoard = mapOf(
                FutaberPreferenceKeys.CATALOG_SORT to CatalogMode.New.name,
                FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD to encodeFutaberSortByBoard(mapOf("may" to mode))
            )
            assertEquals(CatalogMode.Catalog, futaberCatalogSortFor(perBoard, "may", perBoard = true))
            assertEquals(mode, futaberCatalogSortFor(perBoard, "may", perBoard = true, extraModes = true))
            assertEquals(CatalogMode.New, futaberCatalogSortFor(perBoard, "img", perBoard = true))
        }
    }

    @Test fun anotherBoardsChoiceKeepsAStoredSoRecord() {
        val stored = encodeFutaberSortByBoard(mapOf("may" to CatalogMode.So))
        val next = decodeFutaberSortByBoard(futaberSortByBoardAfter(stored, "img", CatalogMode.Few, listOf("may", "img")))
        assertEquals(mapOf("may" to CatalogMode.So, "img" to CatalogMode.Few), next)
    }

    @Test fun theWatchWordsAreThePatrolKeywordsAndTheBoardsExtractionRules() {
        val mayKey = assertNotNull(futaberNgScopeKey(may))
        val preferences = mapOf(
            COMPAT_WATCH_RULES_KEY to encodeValidCompatWatchRules(
                listOf(
                    CompatWatchRule("猫"),
                    CompatWatchRule("犬", boardKey = mayKey),
                    CompatWatchRule("鳥", boardKey = "other"),
                    CompatWatchRule("止めた", enabled = false)
                )
            )
        )
        val rules = listOf(
            CompatNgRule("r1", CompatNgKind.CATALOG_EXTRACT, mayKey, "ラーメン", 1L),
            CompatNgRule("r2", CompatNgKind.CATALOG_EXTRACT, "*", "猫", 2L),
            CompatNgRule("r3", CompatNgKind.CATALOG_WORD, mayKey, "隠す", 3L),
            CompatNgRule("r4", CompatNgKind.CATALOG_EXTRACT, "other", "別板", 4L)
        )
        val source = futaberCatalogWatchSource(preferences, rules, may)
        assertEquals(listOf("猫", "犬"), source.patrolWords)
        assertEquals(listOf("r1", "r2"), source.extractRules.map { it.id })
        assertEquals(listOf("猫", "犬", "ラーメン"), source.words)
        // The tutorial board has no key of its own: only what is made for every board.
        val tutorialSource = futaberCatalogWatchSource(preferences, rules, tutorial)
        assertEquals(listOf("猫"), tutorialSource.words)
        assertTrue(futaberCatalogWatchSource(emptyMap(), emptyList(), may).isEmpty)
    }

    @Test fun watchFetchesKeepOnlyMatchingThreadsAndTheOtherSortsAreTheBoardsOwn() = runBlocking {
        val repository = FakeBoardRepository()
        val all = repository.getCatalogPage(tutorial.url, CatalogMode.Catalog).items
        assertTrue(all.isNotEmpty())
        val so = futaberFetchCatalogPage(repository, tutorial.url, CatalogMode.So, listOf("x"))
        assertEquals(repository.getCatalogPage(tutorial.url, CatalogMode.So).items, so.items)
        // No words: nothing (and nothing fetched to filter).
        assertTrue(futaberFetchCatalogPage(repository, tutorial.url, CatalogMode.WatchWords, emptyList()).items.isEmpty())
        val word = all.first { !it.title.isNullOrBlank() }.title!!.take(2)
        val watched = futaberFetchCatalogPage(repository, tutorial.url, CatalogMode.WatchWords, listOf(word)).items
        assertTrue(watched.isNotEmpty())
        assertTrue(watched.all { w -> all.any { it.id == w.id } })
        assertTrue(watched.size <= all.size)
        assertEquals(watched.map { it.id }.distinct(), watched.map { it.id })
    }

    @Test fun theEmptyWatchListSaysWhy() {
        assertNull(futaberWatchModeEmptyMessage(CatalogMode.New, emptyList()))
        assertTrue(futaberWatchModeEmptyMessage(CatalogMode.WatchWords, emptyList())!!.contains("登録されていません"))
        assertTrue(futaberWatchModeEmptyMessage(CatalogMode.WatchWords, listOf("猫"))!!.contains("一致する"))
    }

    // ---- C07 / C08 ----

    @Test fun withoutAProjectionTheListIsUntouched() {
        val items = listOf(item("1", "a", 1), item("2", "b", 9))
        assertSame(items, futaberProjectCatalog(items, null).items)
        assertSame(items, futaberProjectCatalog(items, FutaberCatalogProjection()).items)
        assertTrue(futaberProjectCatalog(items, FutaberCatalogProjection()).badges.isEmpty())
    }

    @Test fun watchedThreadsGetABadgeAndComeFirst() {
        val items = listOf(item("1", "普通のスレ", 5), item("2", "猫のスレ", 1), item("3", "ﾈｺと犬", 3), item("4", "犬と猫", 0))
        val projection = FutaberCatalogProjection(
            highlight = true,
            watch = FutaberCatalogWatchSource(listOf("猫", "犬"), listOf(CompatNgRule("r", CompatNgKind.CATALOG_EXTRACT, "*", "ネコ", 1L)))
        )
        val result = futaberProjectCatalog(items, projection)
        assertEquals(listOf("2", "3", "4", "1"), result.items.map { it.id })
        assertEquals("猫", result.badges["2"])
        assertEquals("犬 +1", result.badges["3"])
        assertEquals("猫 +1", result.badges["4"])
        assertNull(result.badges["1"])
        // The highlight off: no badge and no move, even with words.
        val off = futaberProjectCatalog(items, projection.copy(highlight = false))
        assertEquals(items, off.items)
        assertTrue(off.badges.isEmpty())
    }

    @Test fun theBadgeIsShortAndCountsTheOtherWords() {
        assertEquals("", futaberWatchBadgeLabel(emptyList()))
        assertEquals("とても長い監視ワ +2", futaberWatchBadgeLabel(listOf("とても長い監視ワードです", "b", "c")))
    }

    @Test fun fewRepliesComeLaterOrAreHiddenAndTheThresholdCounts() {
        val items = listOf(item("1", "a", 1), item("2", "b", 10), item("3", "猫", 2), item("4", "d", 5))
        val base = FutaberCatalogProjection(replyPriority = true, threshold = 5)
        assertEquals(listOf("2", "4", "1", "3"), futaberProjectCatalog(items, base).items.map { it.id })
        assertEquals(listOf("2", "4"), futaberProjectCatalog(items, base.copy(showNonPriority = false)).items.map { it.id })
        // The board's switch off, or a threshold of 0: the order is the catalog's.
        assertEquals(items, futaberProjectCatalog(items, base.copy(replyPriorityEnabled = false, showNonPriority = false)).items)
        assertEquals(items, futaberProjectCatalog(items, base.copy(threshold = 0, showNonPriority = false)).items)
        // Not in force (the extension off / the setting not read): nothing is hidden.
        assertEquals(items, futaberProjectCatalog(items, base.copy(replyPriority = false, showNonPriority = false)).items)
        // With the highlight: watched threads first inside the priority group, a watched thread with few replies stays below.
        val both = base.copy(highlight = true, watch = FutaberCatalogWatchSource(listOf("猫", "d")))
        assertEquals(listOf("4", "2", "1", "3"), futaberProjectCatalog(items, both).items.map { it.id })
        assertEquals(setOf("3", "4"), futaberProjectCatalog(items, both).badges.keys)
    }

    // ---- C09 ----

    private fun history(): FutaberCatalogHistory = FutaberCatalogHistory(
        earlier = listOf(
            listOf(item("1", "a"), item("2", "b")),
            listOf(item("1", "a"), item("3", "c"), item("4", "d")),
            listOf(item("5", "e"))
        ),
        dropped = listOf(item("3", "c"), item("4", "d"), item("5", "e"), item("6", "f"))
    )

    @Test fun goingBackOneIsTheOldGoBack() {
        assertEquals(futaberCatalogGoBack(history()), futaberCatalogGoBackTo(history(), 1))
    }

    @Test fun goingBackSeveralSkipsTheListsBetweenAndTheBroughtBackThreadsAreNotGone() {
        val (items, rest) = assertNotNull(futaberCatalogGoBackTo(history(), 2))
        assertEquals(listOf("1", "3", "4"), items.map { it.id })
        assertEquals(listOf(listOf("5")), rest.earlier.map { list -> list.map { it.id } })
        assertEquals(listOf("5", "6"), rest.dropped.map { it.id })
        val (oldest, none) = assertNotNull(futaberCatalogGoBackTo(history(), 3))
        assertEquals(listOf("5"), oldest.map { it.id })
        assertTrue(none.earlier.isEmpty())
        assertEquals(listOf("3", "4", "6"), none.dropped.map { it.id })
        assertNull(futaberCatalogGoBackTo(history(), 4))
        assertNull(futaberCatalogGoBackTo(history(), 0))
        assertNull(futaberCatalogGoBackTo(FutaberCatalogHistory(), 1))
    }

    @Test fun theSheetOffersOneRowPerKeptCatalogOnlyWithTheExtension() {
        val restored = mutableListOf<List<String>>()
        var closed = 0
        fun actions(perGeneration: Boolean, loading: Boolean = false, h: FutaberCatalogHistory = history()) =
            futaberCatalogBackActions(h, loading, perGeneration, { h }, { closed++ }) { items, _ -> restored += items.map { it.id } }
        val single = actions(false)
        assertEquals(listOf("catalog-back" to "更新前のカタログに戻す"), single.map { it.id to it.label })
        assertTrue(single.single().enabled)
        val rows = actions(true)
        assertEquals(
            listOf("更新前のカタログ（1回前 2スレ）", "更新前のカタログ（2回前 3スレ）", "更新前のカタログ（3回前 1スレ）"),
            rows.map { it.label }
        )
        assertEquals(listOf("catalog-back-1", "catalog-back-2", "catalog-back-3"), rows.map { it.id })
        rows[1].onClick()
        assertEquals(listOf(listOf("1", "3", "4")), restored)
        assertEquals(1, closed)
        // Not while a reload runs; with nothing kept, the one disabled row as before.
        assertTrue(actions(true, loading = true).none { it.enabled })
        val empty = actions(true, h = FutaberCatalogHistory())
        assertEquals(listOf("catalog-back"), empty.map { it.id })
        assertFalse(empty.single().enabled)
    }

    // ---- 6 / 7 ----

    @Test fun theBrowserOpensTheCatalogOfTheSortAndWatchOpensThePlainCatalog() {
        assertTrue(buildCatalogExternalAppUrl(may.url, CatalogMode.So).contains("mode=cat"))
        assertTrue(buildCatalogExternalAppUrl(may.url, CatalogMode.So).contains("sort=8"))
        assertEquals(
            buildCatalogExternalAppUrl(may.url, CatalogMode.Catalog),
            buildCatalogExternalAppUrl(may.url, CatalogMode.WatchWords)
        )
    }

    @Test fun theCacheSearchMenuItemIsThereOnlyWhenOffered() {
        assertNull(futaberCacheSearchMenuItem(null, enabled = true) {})
        var opened = 0
        var dismissed = 0
        val entry = assertNotNull(futaberCacheSearchMenuItem({ opened++ }, enabled = true) { dismissed++ })
        assertEquals("cache-search", entry.id)
        assertTrue(entry.enabled)
        entry.onClick()
        assertEquals(1, opened); assertEquals(1, dismissed)
    }

    @Test fun theCacheSearchIsOfferedOnOfficialBoardsOnly() {
        // As in Futacha: the search sends the board's address to the outside server, so a board that is not an
        // official one (the bundled tutorial board) shows the entry disabled and a tap does nothing.
        assertTrue(futaberCacheSearchUsable(may))
        assertFalse(futaberCacheSearchUsable(tutorial))
        assertFalse(futaberCacheSearchUsable(null))
        var opened = 0
        var dismissed = 0
        val entry = assertNotNull(futaberCacheSearchMenuItem({ opened++ }, enabled = futaberCacheSearchUsable(tutorial)) { dismissed++ })
        assertFalse(entry.enabled)
        entry.onClick()
        assertEquals(0, opened); assertEquals(0, dismissed)
    }

    @Test fun theWatchWordHintMentionsBoardsWithTheirOwnWords() {
        assertFalse(futaberWatchWordsHint(0).contains("板ごと"))
        assertTrue(futaberWatchWordsHint(2).contains("2板"))
    }

    /** Re-audit M12: while a new list is matched, the last order and badges stay for the threads still listed. */
    @Test fun theLastProjectionStaysInItsOrderWhileANewListIsMatched() {
        val previous = FutaberProjectedCatalog(
            listOf(item("3", "ねこ"), item("1", "いぬ"), item("2", "とり")),
            mapOf("3" to "ねこ", "2" to "とり")
        )
        // "2" left (an image NG), "4" is new, "1" got a reply.
        val fresh = listOf(item("1", "いぬ", replies = 5), item("3", "ねこ"), item("4", "さかな"))
        val carried = futaberCarryProjection(previous, fresh)
        assertEquals(listOf("3", "1"), carried.items.map { it.id })
        assertEquals(5, carried.items[1].replyCount)
        assertEquals(mapOf("3" to "ねこ"), carried.badges)
    }
}
