package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals

/** seq53: search, NG words and watch words match the whole title with one normalization; only the shown title is cut. */
class CompatCatalogWholeTitleMatchTest {
    private val longTitle = "今日の晩ごはんの話をしようぜみんな何を食べたのかなあ俺はカレーうどん"
    private fun item(id: String, title: String) =
        CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", title, null, null, replyCount = 10)

    private fun request(items: List<CatalogItem>, query: String = "", watchWords: List<String> = emptyList(),
        rules: List<CompatNgRule> = emptyList()) = CompatCatalogProjectionRequest(
        items = items, resolvedTitles = emptyMap(), searchQuery = query,
        preference = CompatCatalogPreference(boardKey = "board", replyPriorityEnabled = false),
        priorityThreshold = 0, catalogNgEnabled = true, watchWords = watchWords,
        catalogImagePhashRules = emptyList(), catalogImagePhashes = emptyMap(), catalogImageNgPhashThreshold = 0,
        catalogRuleIndex = buildCompatCatalogRuleIndex(rules), catalogSourceTitleLength = 20)

    @Test
    fun aWordAfterTheCutIsFoundAndTheShownTitleIsStillCut() {
        val found = buildCompatCatalogProjection(request(listOf(item("1", longTitle), item("2", "別のスレ")), query = "カレーうどん"))
        assertEquals(listOf("1"), found.map { it.id })
        assertEquals(longTitle.take(20), found.single().title)
    }

    @Test
    fun anNgWordAfterTheCutHidesTheThread() {
        val rule = CompatNgRule("w", CompatNgKind.CATALOG_WORD, "board", "カレーうどん", 1L)
        val shown = buildCompatCatalogProjection(request(listOf(item("1", longTitle), item("2", "別のスレ")), rules = listOf(rule)))
        assertEquals(listOf("2"), shown.map { it.id })
    }

    @Test
    fun aWatchWordAfterTheCutPutsTheThreadFirst() {
        val shown = buildCompatCatalogProjection(request(listOf(item("2", "別のスレ"), item("1", longTitle)), watchWords = listOf("ｶﾚｰ")))
        assertEquals(listOf("1", "2"), shown.map { it.id })
    }

    @Test
    fun halfWidthKanaFullWidthAndCaseMatchTheSameInEveryMatcher() {
        val titles = listOf("ｶﾚｰうどん食べたい", "カレーうどん", "かれーＵＤＯＮ")
        val items = titles.mapIndexed { index, title -> item("$index", title) }
        assertEquals(3, buildCompatCatalogProjection(request(items, query = "カレー")).size)
        assertEquals(setOf("0", "1"), buildCompatCatalogProjection(request(items, query = "カレーうどん")).map { it.id }.toSet())
        assertEquals("かれーudon", normalizeCompatCatalogMatchText("カレーＵＤＯＮ"))
        assertEquals(
            com.valoser.futacha.shared.model.normalizeCatalogSearchText("ｶﾞﾝﾀﾞﾑ"),
            com.valoser.futacha.shared.model.normalizeCatalogSearchText("ガンダム")
        )
        assertEquals(normalizeCompatCatalogMatchText("ｶﾞﾝﾀﾞﾑ"), normalizeCompatCatalogMatchText("ガンダム"))
        val board = CompatBoard("board", "may", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
        val matches = collectCompatWatchMatches(board, items, listOf("カレーうどん"), emptyList(), 0L)
        assertEquals(2, matches.size)
    }
}
