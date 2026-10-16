package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.MAX_COMPAT_PREFERENCE_VALUE_CHARS
import com.valoser.futacha.shared.compat.isValidCompatPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2026-10-14の監査（docs/code-audit-2026-10-14.md）A1・A3・A9：タブの上限と起動時の整理、戻るの順、保存値の上限。
 */
class FtutterStoreLimitsTest {
    private fun url(n: Int) = "https://may.2chan.net/27/res/$n.htm"

    // ---- A1：タブ ----

    @Test
    fun theHistoryLimitIsTheOriginalSettingWithItsDefaultAndRange() {
        assertEquals(200, ftutterHistoryLimit(emptyMap()))
        assertEquals(50, ftutterHistoryLimit(mapOf(FtutterKeys.HISTORY_LIMIT to "50")))
        // 原作のバックアップの実数（"50.0"）も読む。範囲外・壊れた値は既定。
        assertEquals(50, ftutterHistoryLimit(mapOf(FtutterKeys.HISTORY_LIMIT to "50.0")))
        assertEquals(0, ftutterHistoryLimit(mapOf(FtutterKeys.HISTORY_LIMIT to "0")))
        assertEquals(200, ftutterHistoryLimit(mapOf(FtutterKeys.HISTORY_LIMIT to "5000")))
        assertEquals(200, ftutterHistoryLimit(mapOf(FtutterKeys.HISTORY_LIMIT to "x")))
    }

    @Test
    fun atStartTabsNotInHistoryAreDroppedButProtectedAndOpenTabsStay() {
        // 原作：起動時に履歴に無いタブを捨てる。保護したタブ・開いていたタブは残す。
        val tabs = listOf(url(1), url(2), url(3), url(4))
        val history = listOf(url(3), url(1))
        assertEquals(
            listOf(url(1), url(2), url(3), url(4)).filter { it != url(4) },
            ftutterLimitTabs(tabs, history, protected = setOf(url(2)), limit = 200, keep = emptySet(), dropMissing = true)
        )
        assertEquals(
            listOf(url(1), url(3), url(4)),
            ftutterLimitTabs(tabs, history, protected = emptySet(), limit = 200, keep = setOf(url(4)), dropMissing = true)
        )
    }

    @Test
    fun theLimitKeepsTheMostRecentlyOpenedUnprotectedTabsInTheirOrder() {
        // 原作：履歴の保存数（保護したものは数えない）を超えた古いスレは外れる。タブの並びは変えない。
        val tabs = listOf(url(1), url(2), url(3), url(4), url(5))
        val history = listOf(url(4), url(2), url(5), url(1), url(3))
        assertEquals(
            listOf(url(2), url(4)),
            ftutterLimitTabs(tabs, history, protected = emptySet(), limit = 2, keep = emptySet(), dropMissing = false)
        )
        // 保護したタブは数えずに残る。
        assertEquals(
            listOf(url(2), url(3), url(4)),
            ftutterLimitTabs(tabs, history, protected = setOf(url(3)), limit = 2, keep = emptySet(), dropMissing = false)
        )
        // 今開いたタブ（まだ履歴の書き込みが届いていない）は、上限0でも残り、最新として数える。
        val opened = listOf(url(9)) + tabs
        assertEquals(
            listOf(url(9), url(4)),
            ftutterLimitTabs(opened, history, protected = emptySet(), limit = 2, keep = setOf(url(9)), dropMissing = false)
        )
        assertEquals(listOf(url(9)), ftutterLimitTabs(opened, history, emptySet(), limit = 0, keep = setOf(url(9)), dropMissing = false))
    }

    @Test
    fun openingThreadsOneAfterAnotherNeverOutgrowsTheStoredValue() {
        // 監査A1：以前は開いたスレがすべてタブに残り、約450件で保存値の上限を超えて黙って保存できなくなった。
        var tabs = emptyList<String>()
        val history = ArrayList<String>()
        (1..2_000).forEach { n ->
            val u = "https://may.2chan.net/b/res/${1_400_000_000L + n}.htm"
            tabs = ftutterLimitTabs(ftutterPushTab(tabs, u), history, emptySet(), limit = 200, keep = setOf(u), dropMissing = false)
            history.add(0, u)
        }
        assertEquals(200, tabs.size)
        assertTrue(isValidCompatPreference(FtutterKeys.TABS, encodeFtutterList(tabs)))
    }

    @Test
    fun tabsBeyondTheStoreLimitLoseTheOldestUnprotectedOnesAndOnlyProtectedOnesCanRefuse() {
        val long = (1..1_000).map { "https://may.2chan.net/b/res/${1_400_000_000L + it}.htm" }
        val history = long.reversed()
        val fitted = assertNotNull(ftutterFitTabs(long, history, protected = setOf(long.first()), keep = setOf(long[1])))
        assertTrue(encodeFtutterList(fitted).length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS)
        // 新しく開いたもの（履歴の先頭＝並びの後ろ）が残り、保護と開いているタブも残る。
        assertTrue(long.last() in fitted && long.first() in fitted && long[1] in fitted)
        assertFalse(long[2] in fitted)
        // 保護したタブだけで上限を超える時は、勝手に消さずに断る（呼ぶ側が知らせる）。
        assertNull(ftutterFitTabs(long, history, protected = long.toSet(), keep = emptySet()))
        // 収まっていれば何もしない。
        assertEquals(long.take(3), ftutterFitTabs(long.take(3), history, emptySet(), emptySet()))
    }

    // ---- A3：戻る ----

    @Test
    fun backClosesTheDrawerFirstThenTheCatalogPaneThenLeaves() {
        assertEquals(FtutterBackStep.CLOSE_DRAWER, ftutterBackStep(drawerOpen = true, catalogPaneHandlesBack = true))
        assertEquals(FtutterBackStep.CLOSE_DRAWER, ftutterBackStep(drawerOpen = true, catalogPaneHandlesBack = false))
        assertEquals(FtutterBackStep.CATALOG_PANE, ftutterBackStep(drawerOpen = false, catalogPaneHandlesBack = true))
        assertEquals(FtutterBackStep.LEAVE, ftutterBackStep(drawerOpen = false, catalogPaneHandlesBack = false))
    }

    // ---- A9：NG ----

    @Test
    fun aRealisticNgListFitsInOneStoredValue() {
        // 監査A9：以前は既定値まで書き出して約250件で上限を超え、「登録しました」と出るのに保存されなかった。
        val rules = (1..500).map { FtutterNgRule("荒らしの言葉$it", it % 4) }
        val encoded = encodeFtutterNgListForStore(rules)
        assertTrue(encoded.length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS, "length ${encoded.length}")
        // 読み戻すと同じ（既定値は省いても戻る）。
        assertEquals(rules, parseFtutterNgList(encoded))
        val scoped = FtutterNgRule("x", 1, isReg = true, isActive = false, ita = FtutterNgBoard("ねこ", "https://may.2chan.net/27/"))
        assertEquals(listOf(scoped), parseFtutterNgList(encodeFtutterNgListForStore(listOf(scoped))))
    }

    @Test
    fun anNgRuleThatNoLongerFitsIsRefusedWithAReason() {
        val big = (1..2_000).map { FtutterNgRule("とても長いNGの言葉をたくさん登録した人$it", 1) }
        val fits = ftutterFitNgRules(emptyList(), big)
        assertTrue(fits.skipped > 0)
        assertEquals(big.size, fits.rules.size + fits.skipped)
        assertEquals(big.take(fits.rules.size), fits.rules)
        assertTrue(encodeFtutterNgListForStore(fits.rules).length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS)
        assertNotNull(ftutterNgCapacityError(fits.rules + FtutterNgRule("もう一つ長いNGの言葉".repeat(10), 1)))
        assertNull(ftutterNgCapacityError(listOf(FtutterNgRule("a", 1))))
        // 取り込み：今あるNGは必ず残し、入る分だけ足す。
        val existing = fits.rules.take(10)
        val merged = ftutterFitNgRules(existing, big.drop(10))
        assertEquals(existing, merged.rules.take(10))
    }

    // ---- A9：非表示 ----

    @Test
    fun hidingBeyondTheLimitDropsTheOldestRecordsAndSaysHowMany() {
        val hidden = (1..1_000).map { "https://may.2chan.net/b/res/${1_400_000_000L + it}.htm" }
        val (next, dropped) = ftutterHiddenThreadsWith(hidden, url(1))
        assertTrue(dropped > 0)
        assertEquals(url(1), next.last())
        assertEquals(hidden.drop(dropped), next.dropLast(1))
        assertTrue(encodeFtutterList(next).length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS)
        assertEquals(listOf(url(2), url(1)) to 0, ftutterHiddenThreadsWith(listOf(url(2)), url(1)))
        assertEquals(listOf(url(1)) to 0, ftutterHiddenThreadsWith(listOf(url(1)), url(1)))
    }

    // ---- A9：下書き ----

    @Test
    fun draftsAreBoundedSoEverySaveAndTheRemovalAfterSendingFit() {
        // 監査A9：件数の制限しか無く、長い下書きが数件あると以後の保存も送信後の削除も失敗した。
        var stored: String? = null
        repeat(40) { n ->
            val update = ftutterDraftsUpdate(parseFtutterDrafts(stored), "res:k$n", FtutterDraft(comment = "あ".repeat(3_000) + n))
            assertTrue(update.encoded.length <= FTUTTER_DRAFT_STORE_MAX_CHARS, "length ${update.encoded.length}")
            stored = update.encoded
        }
        val drafts = parseFtutterDrafts(stored)
        assertTrue(drafts.size in 1..FTUTTER_DRAFT_LIMIT)
        // 一番新しいものは必ず残る。
        assertEquals("あ".repeat(3_000) + 39, drafts["res:k39"]?.comment)
        // 送信後の削除：消した結果は必ず上限に収まり、その下書きは残らない。
        val removed = ftutterDraftsUpdate(drafts, "res:k39", FtutterDraft())
        assertTrue(isValidCompatPreference(FtutterKeys.DRAFTS, removed.encoded))
        assertNull(parseFtutterDrafts(removed.encoded)["res:k39"])
        assertEquals(0, removed.dropped)
        assertFalse(removed.truncated)
    }

    @Test
    fun anOverlongDraftIsCutAndTheCutIsReported() {
        val long = "い".repeat(FTUTTER_DRAFT_MAX_COMMENT_CHARS + 500)
        val update = ftutterDraftsUpdate(emptyMap(), "res:a", FtutterDraft(title = "題", comment = long))
        assertTrue(update.truncated)
        assertEquals(long.take(FTUTTER_DRAFT_MAX_COMMENT_CHARS), parseFtutterDrafts(update.encoded)["res:a"]?.comment)
        // 改行・引用符の多い本文（書き出すと長くなる）でも収まる。
        val escaped = ftutterDraftsUpdate(emptyMap(), "res:b", FtutterDraft(comment = "\u0001".repeat(FTUTTER_DRAFT_MAX_COMMENT_CHARS)))
        assertTrue(escaped.encoded.length <= FTUTTER_DRAFT_STORE_MAX_CHARS)
        assertNotNull(parseFtutterDrafts(escaped.encoded)["res:b"])
    }

    // ---- A9：.ftt の取り込み ----

    @Test
    fun anImportThatWouldOverflowKeepsWhatFitsAndListsWhatWasLeftOut() {
        val existingNg = listOf(FtutterNgRule("今のNG", 1))
        val importedNg = (1..2_000).map { FtutterNgRule("取り込んだとても長いNGの言葉$it", 1) }
        val hidden = (1..1_000).map { "https://may.2chan.net/b/res/${1_400_000_000L + it}.htm" }
        val plan = mapOf(
            FtutterKeys.NG_LIST to encodeFtutterNgList(existingNg + importedNg),
            FtutterKeys.HIDDEN_THREADS to encodeFtutterList(hidden),
            FtutterKeys.TABS to encodeFtutterList(hidden),
            FtutterKeys.MAIN_COLOR to "3"
        )
        val fit = ftutterFitPreferencesForStore(plan)
        fit.preferences.forEach { (k, v) -> assertTrue(isValidCompatPreference(k, v), "$k ${v.length}") }
        val ng = parseFtutterNgList(fit.preferences[FtutterKeys.NG_LIST])
        assertEquals(existingNg, ng.take(1))
        assertTrue(ng.size > 1)
        assertEquals("3", fit.preferences[FtutterKeys.MAIN_COLOR])
        assertTrue(fit.leftOut.any { it.startsWith("NG") }, fit.leftOut.toString())
        assertTrue(fit.leftOut.any { it.startsWith("非表示") }, fit.leftOut.toString())
        assertTrue(fit.leftOut.any { it.startsWith("タブ") }, fit.leftOut.toString())
        // 収まる取り込みは何も変えない。
        val small = mapOf(FtutterKeys.MAIN_COLOR to "3", FtutterKeys.NG_LIST to encodeFtutterNgList(existingNg))
        assertEquals(emptyList(), ftutterFitPreferencesForStore(small).leftOut)
        assertEquals(existingNg, parseFtutterNgList(ftutterFitPreferencesForStore(small).preferences[FtutterKeys.NG_LIST]))
    }

    /** 再監査M8：タブは「取り込んだタブ＋今のタブ」の順。入らない時は取り込んだタブから捨て、今のタブは残す。 */
    @Test
    fun anOverflowingTabImportDropsImportedTabsAndKeepsTheCurrentOnes() {
        val current = (1..5).map { "https://may.2chan.net/b/res/${1_300_000_000L + it}.htm" }
        val imported = (1..2_000).map { "https://may.2chan.net/b/res/${1_400_000_000L + it}.htm" }
        val fit = ftutterFitPreferencesForStore(mapOf(FtutterKeys.TABS to encodeFtutterList(imported + current)), current.toSet())
        val tabs = decodeFtutterList(fit.preferences[FtutterKeys.TABS]).orEmpty()
        assertTrue(isValidCompatPreference(FtutterKeys.TABS, fit.preferences.getValue(FtutterKeys.TABS)))
        assertEquals(current, tabs.takeLast(5))
        assertEquals(imported.take(tabs.size - 5), tabs.dropLast(5))
        assertTrue(fit.leftOut.any { it.startsWith("タブ") }, fit.leftOut.toString())
    }

    /** Re-audit L22: marks pushed out by an import are told. */
    @Test
    fun anImportThatPushesOutMarksSaysSo() {
        assertEquals("取り込みました", ftutterImportNotice(emptyList(), 0))
        val notice = ftutterImportNotice(listOf("タブ 3件"), 5)
        assertTrue(notice.contains("タブ 3件") && notice.contains("5件外しました"), notice)
    }
}
