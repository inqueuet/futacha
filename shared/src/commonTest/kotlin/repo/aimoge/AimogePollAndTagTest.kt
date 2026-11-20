package com.valoser.futacha.shared.repo.aimoge

import com.valoser.futacha.shared.network.aimoge.AimogePollDto
import com.valoser.futacha.shared.network.aimoge.AimogePollOptionDto
import com.valoser.futacha.shared.network.aimoge.AimogePostDto
import com.valoser.futacha.shared.network.aimoge.AimogeTagDto
import com.valoser.futacha.shared.network.aimoge.AimogeThreadDto
import com.valoser.futacha.shared.network.aimoge.toCatalogItem
import com.valoser.futacha.shared.network.aimoge.toPost
import com.valoser.futacha.shared.network.aimoge.aimogePollHtml
import com.valoser.futacha.shared.network.aimoge.aimogePollOptionLinks
import com.valoser.futacha.shared.network.aimoge.AimogePollState
import com.valoser.futacha.shared.network.aimoge.AimogePollStateDto
import com.valoser.futacha.shared.network.aimoge.aimogeMergePollState
import com.valoser.futacha.shared.network.aimoge.toDto
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AimogePollAndTagTest {
    @Test fun aPollShowsItsOptionsUnderTheBodyWithNoVotes() {
        val post = AimogePostDto(id = "u", body = "どれ？",
            poll = AimogePollDto(options = listOf(AimogePollOptionDto("a", "犬<猫"), AimogePollOptionDto("b", "鳥"))))
        val html = post.toPost(0).messageHtml
        val text = messageHtmlToPlainText(html)
        assertEquals("どれ？\n\nアンケート\n\n・犬<猫\n\n・鳥\n（選択肢を押すと回答できます）", text)
        assertEquals(listOf("犬<猫", "鳥"), aimogePollOptionLinks(html, text).map { (range, _) -> text.substring(range) })
        assertEquals("x", messageHtmlToPlainText(AimogePostDto(id = "v", body = "x").toPost(0).messageHtml))
    }

    /** Thread 01a1cd5d #72: a poll said nothing about being pressable, and a closed one nothing about being closed. */
    @Test fun anOpenPollSaysItsOptionsArePressedToVoteAndAPastOneIsClosed() {
        val options = listOf(AimogePollOptionDto("a", "犬"), AimogePollOptionDto("b", "鳥"))
        val now = kotlin.time.Instant.parse("2026-11-12T00:00:00Z")
        val open = aimogePollHtml(AimogePollDto(closesAt = "2026-11-13T00:00:00Z", options = options), postId = "p", now = now)
        assertTrue("（選択肢を押すと回答できます）" in open, open)
        assertTrue("残り1日 · 締切" in open, open)
        assertTrue("#futacha-poll/p/a" in open, open)
        val past = aimogePollHtml(AimogePollDto(closesAt = "2026-11-11T00:00:00Z", options = options), postId = "p", now = now)
        assertTrue("回答受付は終了しました" in past, past)
        assertTrue("futacha-poll" !in past, past)
    }

    /** As the Web version: once answered here, the current results with counts, percent and 「あなたの回答」. */
    @Test fun anAnsweredPollShowsTheResultsLikeTheWebVersion() {
        val options = listOf(AimogePollOptionDto("a", "犬"), AimogePollOptionDto("b", "鳥"))
        val now = kotlin.time.Instant.parse("2026-11-12T00:00:00Z")
        val mine = AimogePollState(isClosed = false, selectedOptionId = "b", totalVotes = 3, counts = mapOf("a" to 1, "b" to 2)).toDto()
        // The public /state of an open poll has neither the answer nor the results.
        val dto = aimogeMergePollState(AimogePollStateDto(), mine)
        val html = aimogePollHtml(AimogePollDto(closesAt = "2026-11-12T00:30:00Z", options = options), postId = "p", state = dto, now = now)
        val text = messageHtmlToPlainText(html)
        assertTrue("残り30分" in text, text)
        assertTrue("現在の結果" in text, text)
        assertTrue("犬　1件 / 33%" in text, text)
        assertTrue("鳥（あなたの回答）　2件 / 67%" in text, text)
        assertTrue("合計 3件" in text, text)
        assertTrue("futacha-poll" !in html, html)
    }

    @Test fun onlyTheR18TagMarksTheTitle() {
        val op = AimogePostDto(id = "u", body = "題名")
        assertEquals("[R18] 題名", AimogeThreadDto(id = "t", opPost = op, tags = listOf(AimogeTagDto("R18", "op"))).toCatalogItem("x").title)
        assertEquals("題名", AimogeThreadDto(id = "t", opPost = op, tags = listOf(AimogeTagDto("ゲーム", "ai"))).toCatalogItem("x").title)
        assertTrue(AimogeThreadDto(id = "t", opPost = op).toCatalogItem("x").title == "題名")
    }
}
