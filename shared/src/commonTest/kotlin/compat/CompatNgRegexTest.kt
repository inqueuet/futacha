package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.buildCompatLegacyRestorePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FutabaViewer 18-20's regular-expression NG and watch words, taken into としあき(仮). */
class CompatNgRegexTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)

    private fun rule(kind: CompatNgKind, value: String, regex: Boolean, scope: String = "*") = CompatNgRule(
        id = compatNgRuleId(kind, scope, value, regex),
        kind = kind,
        scopeKey = scope,
        normalizedValue = value,
        createdAtEpochMillis = 1L,
        isRegex = regex
    )

    private fun item(title: String) = CatalogItem("1", "https://may.2chan.net/b/res/1.htm", title, null, null, replyCount = 1)

    private fun post(body: String, author: String? = null) =
        CompatPostSnapshot(position = 0, postNo = "1", author = author, timestamp = "26/11/08", messageHtml = body)

    @Test
    fun aCatalogRegexHidesAndWatchesByTheTitleAsWrittenAndTellsCaseApart() {
        val index = buildCompatCatalogRuleIndex(
            listOf(
                rule(CompatNgKind.CATALOG_IGNORE, "^ABC[0-9]+$", regex = true),
                rule(CompatNgKind.CATALOG_EXTRACT, "株.*ウマ", regex = true)
            )
        )
        assertTrue(index.hides(item("ABC123")))
        // Case and width are told apart for a regular expression (18-20: 「正規表現では上の2つを区別します」).
        assertFalse(index.hides(item("abc123")))
        assertFalse(index.hides(item("ＡＢＣ123")))
        assertTrue(index.extracts(item("株とウマ娘")))
        assertFalse(index.extracts(item("ウマ娘と株")))
        // The regular expression is not also used as a plain word.
        assertTrue(index.hiddenWords.isEmpty())
    }

    @Test
    fun aPlainWordStillIgnoresCaseAndWidth() {
        val index = buildCompatCatalogRuleIndex(listOf(rule(CompatNgKind.CATALOG_IGNORE, "abc", regex = false)))
        assertTrue(index.hides(item("ＡＢＣです")))
    }

    @Test
    fun theWatchWordOfARegexIsThePartItFound() {
        val rules = listOf(rule(CompatNgKind.CATALOG_EXTRACT, "ウマ.", regex = true))
        assertEquals(listOf("ウマ娘"), compatCatalogMatchedWords(item("今日のウマ娘"), emptyList(), rules))
        assertTrue(compatCatalogItemIsExtracted(item("今日のウマ娘"), rules))
    }

    @Test
    fun threadRegexesMatchTheBodyAndTheHeaderAsWritten() {
        val body = buildCompatThreadNgRuleIndex(listOf(rule(CompatNgKind.THREAD_IGNORE, "[0-9]{3}-[0-9]{4}", regex = true)), "tab")
        assertTrue(post("連絡は012-3456へ").matchesCompatThreadNg(body))
        assertFalse(post("連絡は０１２-３４５６へ").matchesCompatThreadNg(body))
        val header = buildCompatThreadNgRuleIndex(listOf(rule(CompatNgKind.THREAD_REFUSE, "^としあき$", regex = true)), "tab")
        assertTrue(post("本文", author = "としあき").matchesCompatThreadNg(header))
        assertFalse(post("本文", author = "としあきさん").matchesCompatThreadNg(header))
        // The cached filter sees the same verdicts and a changed regex list re-judges the posts.
        val posts = listOf(post("012-3456"), post("ok").copy(postNo = "2", position = 1))
        val first = filterCompatThreadPostsCached(posts, ngEnabled = true, index = body)
        assertEquals(listOf("2"), first.posts.map(CompatPostSnapshot::postNo))
        val none = buildCompatThreadNgRuleIndex(emptyList(), "tab")
        assertEquals(listOf("1", "2"), filterCompatThreadPostsCached(posts, true, none, previous = first.cache).posts.map(CompatPostSnapshot::postNo))
    }

    @Test
    fun aPatternThatCannotBeReadOrIsTooLongIsRefusedAndNeverMatches() {
        assertEquals(COMPAT_NG_REGEX_INVALID_MESSAGE, compatNgRegexInputError("(abc"))
        assertEquals("正規表現は100文字までです", compatNgRegexInputError("a".repeat(101)))
        assertNull(compatNgRegexInputError("a".repeat(100)))
        val index = buildCompatCatalogRuleIndex(listOf(rule(CompatNgKind.CATALOG_IGNORE, "(abc", regex = true)))
        assertFalse(index.hides(item("(abc")))
    }

    @Test
    fun aSlowPatternIsNotUsedAndDoesNotFreeze() {
        val index = buildCompatThreadNgRuleIndex(listOf(rule(CompatNgKind.THREAD_IGNORE, "(a|aa)+$", regex = true)), "tab")
        assertFalse(post("a".repeat(40) + "!").matchesCompatThreadNg(index))
    }

    @Test
    fun aRegexAndAPlainWordOfTheSameTextAreTwoRulesShownWithTheLabel() {
        assertNotEquals(
            compatNgRuleId(CompatNgKind.CATALOG_IGNORE, "*", "abc", isRegex = true),
            compatNgRuleId(CompatNgKind.CATALOG_IGNORE, "*", "abc")
        )
        assertEquals("［正規表現］abc", compatNgRuleDisplayWord("abc", isRegex = true))
        assertEquals("abc", compatNgRuleDisplayWord("abc", isRegex = false))
    }

    @Test
    fun the1820BackupMarkIsReadAndTheRegexKeepsItsCase() {
        val decoded = decodeCompatLegacyBackup(
            """{"strFileType":"keyword",
              "arrCatalogIgnoreList":[["allboard","^ABC$","1"],["allboard","Plain"]],
              "arrThreadIgnoreOnlyList":[["[0-9]+","","1"]],
              "arrThreadRefuseList":["Header"]}"""
        )
        assertEquals(listOf(true, false), decoded.catalogNgWords.map(CompatLegacyScopedWord::isRegex))
        assertTrue(decoded.threadNgWords.single().isRegex)
        val plan = buildCompatLegacyRestorePlan(listOf(decoded), listOf(board), emptyList(), emptySet(), 1L)
        val rules = plan.backup.ngRules
        val regexCatalog = rules.single { it.kind == CompatNgKind.CATALOG_IGNORE && it.isRegex }
        assertEquals("^ABC$", regexCatalog.normalizedValue)
        assertEquals("plain", rules.single { it.kind == CompatNgKind.CATALOG_IGNORE && !it.isRegex }.normalizedValue)
        assertEquals("[0-9]+", rules.single { it.kind == CompatNgKind.THREAD_IGNORE }.normalizedValue)
        // The settings backup keeps the mark.
        val roundTrip = decodeCompatSettingsBackup(encodeCompatSettingsBackup(plan.backup))
        assertEquals(2, roundTrip.ngRules.count(CompatNgRule::isRegex))
    }

    @Test
    fun aRegexMarkWithAPatternThatCannotBeReadIsLeftOut() {
        val decoded = decodeCompatLegacyBackup("""{"strFileType":"keyword","arrCatalogIgnoreList":[["allboard","(bad","1"]]}""")
        val plan = buildCompatLegacyRestorePlan(listOf(decoded), listOf(board), emptyList(), emptySet(), 1L)
        assertTrue(plan.backup.ngRules.isEmpty())
    }
}
