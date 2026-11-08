package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtutterThreadLookTest {
    private fun post(html: String, subject: String? = null) = Post(
        id = "1", author = null, subject = subject, timestamp = "", messageHtml = html,
        imageUrl = null, thumbnailUrl = null
    )

    @Test fun theStoredTitleWinsThenTheJoinedBodyThenTheSubject() {
        assertEquals("うちの子です", ftutterThreadTitle(" うちの子です ", post("別")))
        assertEquals("一行目二行目&", ftutterThreadTitle(null, post("一行目<br>二行目<br>&amp;")))
        assertEquals("題", ftutterThreadTitle("", post("", subject = "題")))
        assertEquals("無題", ftutterThreadTitle(null, null))
    }

    @Test fun theSubtitleCountsRepliesWithoutTheFirstPost() {
        assertEquals("ねこ (19)", ftutterThreadSubtitle("ねこ", 20))
        assertEquals("ねこ (0)", ftutterThreadSubtitle("ねこ", 0))
    }

    @Test fun theLegacyLookIsOffUntilChosen() {
        assertFalse(ftutterLegacyThreadLook(emptyMap()))
        assertTrue(ftutterLegacyThreadLook(mapOf(FTUTTER_LEGACY_THREAD_LOOK_KEY to "true")))
    }

    @Test fun onlyAnchoredUrlsBecomeLinksUnlessLegacy() {
        val anchors = ftutterAnchorTargets("""見て <a href="https://example.com/a?x=1&amp;y=2" target="_blank">https://example.com/a</a>""")
        assertTrue(ftutterKeepsLink("https://example.com/a?x=1&y=2", anchors, legacy = false))
        assertFalse(ftutterKeepsLink("https://other.example/b", anchors, legacy = false))
        assertTrue(ftutterKeepsLink("https://other.example/b", anchors, legacy = true))
    }

    @Test fun redFontLinesAreFound() {
        assertEquals(setOf(1), ftutterRedLines("""普通<br><font color="#ff0000">管理人より注意</font><br>普通"""))
        assertEquals(emptySet(), ftutterRedLines("""<font color="#789922">&gt;引用</font>"""))
    }

    @Test fun gifWebmAndMp4AreMoving() {
        assertTrue(ftutterIsMovingImage("https://may.2chan.net/b/src/1.webm"))
        assertTrue(ftutterIsMovingImage("https://x/1.GIF?t=1"))
        assertFalse(ftutterIsMovingImage("https://x/1.jpg"))
        assertFalse(ftutterIsMovingImage(null))
    }

    @Test fun onlyTheNewCountGetsTheCapsule() {
        assertTrue(ftutterIsNewCountNotice("新着 2 件"))
        assertFalse(ftutterIsNewCountNotice("保存しました"))
    }

    @Test fun theSoudaneCountAfterAVoteIsTheAnswerOrOneMore() {
        assertEquals(3, ftutterSoudaneAfterVote("3", "そうだねx2"))
        assertEquals(3, ftutterSoudaneAfterVote("", "そうだねx2"))
        assertEquals(1, ftutterSoudaneAfterVote("", null))
    }

    @Test fun delIsSentWithoutAskingUnlessTheNgExtensionOrTheLegacySwitch() {
        assertFalse(ftutterDelAsks(emptyMap(), offerNg = false))
        assertTrue(ftutterDelAsks(emptyMap(), offerNg = true))
        assertTrue(ftutterDelAsks(mapOf(FTUTTER_LEGACY_DEL_CONFIRM_KEY to "true"), offerNg = false))
    }

    @Test fun addingOneNgRuleSaysWhatWasAdded() {
        val a = FtutterNgRule("abc", 0)
        assertEquals("abc を追加しました", ftutterNgAddedNotice(emptyList(), listOf(a)))
        assertEquals(null, ftutterNgAddedNotice(listOf(a), emptyList()))
    }

    @Test
    fun onlyWhollyVisibleRowsCountAsReadLikeTheOriginal() {
        // 行0〜2は最後まで見え、行3は下で少しだけ見えている（原作のNo.8とNo.9の形）。
        val items = listOf(Triple(0, 0, 300), Triple(1, 300, 300), Triple(2, 600, 300), Triple(3, 900, 300))
        assertEquals(listOf(0, 1, 2), ftutterLastSeenRow(items, 1000, legacy = false))
        assertEquals(listOf(0, 1, 2, 3), ftutterLastSeenRow(items, 1000, legacy = true))
        // 1つの行が画面より大きい時は、見えているその行を使う。
        assertEquals(listOf(5), ftutterLastSeenRow(listOf(Triple(5, -200, 3000)), 1000, legacy = false))
        assertEquals(emptyList(), ftutterLastSeenRow(emptyList(), 1000, legacy = false))
    }

    @Test
    fun aFailedRefreshBlanksThePageLikeTheOriginalUnlessTheLegacySwitchIsOn() {
        assertEquals(true, ftutterBlanksOnFailure(emptyMap(), null))
        assertEquals(true, ftutterBlanksOnFailure(emptyMap(), 500))
        // 落ちは従来どおり（本文を残す、1度も読めていなければ「スレッドは落ちています」）。
        assertEquals(false, ftutterBlanksOnFailure(emptyMap(), 404))
        assertEquals(false, ftutterBlanksOnFailure(emptyMap(), 410))
        assertEquals(false, ftutterBlanksOnFailure(mapOf(FTUTTER_LEGACY_KEEP_ON_FAILURE_KEY to "true"), null))
    }
}
