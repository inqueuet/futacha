package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Uses the old parser, matcher, backup planner, counts and quote APIs for before/after runs. */
class M71PosterIpTest {
    private val may = "https://may.2chan.net/b/"
    private val ipv4 = "192.0.*(isp.example)"
    private val ipv6 = "2001:DB8:abcd::*"
    private fun post(id: String, header: String = "", body: String = "body-$id", posterId: String? = null) =
        Post(id, author = "名前-$id", subject = null, timestamp = "26/10/22(木)10:00:00 $header".trim(),
            posterId = posterId, messageHtml = body, imageUrl = null, thumbnailUrl = null)
    private fun matcher(text: String, board: String = may) =
        FtutterNgMatcher(listOf(FtutterNgRule(text, FtutterNgType.ID.stored)), board)

    @Test fun actualHtmlParserHeaderIpsAreMatchedWithoutPromotingBodyQuotesToIdentity() = runTest {
        val html = """<html><body><div class="thre">
            <span class="cnm">名前-v4</span><span class="cnw">26/10/22(木)10:00:00 IP:$ipv4</span><span class="cno">No.100</span><blockquote>v4本文</blockquote>
            <table border="0"><tr><td class="rtd"><span class="cnm">名前-v6</span><span class="cnw">26/10/22(木)10:00:01 IP:$ipv6</span><span class="cno">No.101</span><blockquote>v6本文</blockquote></td></tr></table>
            <table border="0"><tr><td class="rtd"><span class="cnm">名前-引用</span><span class="cnw">26/10/22(木)10:00:02 ID:owner</span><span class="cno">No.102</span><blockquote>&gt;IP:$ipv4<br>&gt;IP:$ipv6</blockquote></td></tr></table>
            </div></body></html>"""
        val parsed = ThreadHtmlParserCore.parseThread(html, "${may}res/100.htm").posts
        assertEquals(3, parsed.size)
        assertTrue(matcher("IP:$ipv4").hidesPost(parsed[0]))
        assertTrue(matcher("IP:$ipv6").hidesPost(parsed[1]))
        assertFalse(matcher("IP:$ipv4").hidesPost(parsed[2]))
        assertFalse(matcher("IP:$ipv6").hidesPost(parsed[2]))
        assertTrue(matcher("ID:owner").hidesPost(parsed[2]))
        assertEquals("名前-v6", parsed[1].author)
    }

    @Test fun savedAndImportedIpRulesKeepExactLiteralScopeAndActivation() {
        val rules = listOf(
            FtutterNgRule("IP:$ipv4", FtutterNgType.ID.stored, ita = FtutterNgBoard("may", may)),
            FtutterNgRule("IP:$ipv6", FtutterNgType.ID.stored),
            FtutterNgRule("IP:198.51.100.2", FtutterNgType.ID.stored, isActive = false)
        )
        val raw = rules.map { encodeFtutterNgList(listOf(it)).drop(1).dropLast(1) }
        val plan = ftutterBackupPlan(mapOf("NGList" to FtutterPrefValue.Texts(raw)), emptyList(), emptyList(), emptyMap(), 1)
        assertEquals(0, plan.rejectedNg)
        val imported = parseFtutterNgList(plan.preferences[FtutterKeys.NG_LIST])
        assertEquals(rules, parseFtutterNgList(encodeFtutterNgList(imported)))
        val restored = FtutterNgMatcher(imported, "http://may.2chan.net/b/futaba.php")
        assertTrue(restored.hidesPost(post("1", "IP:$ipv4")))
        assertTrue(restored.hidesPost(post("2", "IP:$ipv6")))
        assertFalse(restored.hidesPost(post("3", "IP:198.51.100.2")))
        assertFalse(FtutterNgMatcher(imported, "https://img.2chan.net/b/").hidesPost(post("4", "IP:$ipv4")))
        // A displayed mask is literal text, not a glob; neither prefixes nor IPv6 canonicalisation apply.
        assertFalse(restored.hidesPost(post("5", "IP:192.0.2.1(isp.example)")))
        assertFalse(matcher("IP:192.0").hidesPost(post("6", "IP:$ipv4")))
        assertFalse(restored.hidesPost(post("7", "IP:2001:db8:abcd::*")))
        assertFalse(matcher("IP:2001:db8::1").hidesPost(post("8", "IP:2001:0db8:0:0:0:0:0:1")))
    }

    @Test fun legacyPosterIdAndHeaderIdsStillWorkWhileNamesAndBodyAreNotIps() {
        assertTrue(matcher("IP:$ipv6").hidesPost(post("1", posterId = "IP:$ipv6")))
        assertTrue(matcher("ID:abc").hidesPost(post("2", posterId = "ID:abc")))
        assertFalse(matcher("ID:abc").hidesPost(post("3", posterId = "ID:abcd")))
        assertTrue(matcher("ID:cached").hidesPost(post("4", "ID:cached")))
        val quoted = post("5", body = "&gt;IP:$ipv6").copy(author = "IP:$ipv6", subject = "IP:$ipv6")
        assertFalse(matcher("IP:$ipv6").hidesPost(quoted))
        val both = post("6", "IP:$ipv6 ID:visible", posterId = "ID:visible")
        assertTrue(matcher("ID:visible").hidesPost(both))
        assertTrue(matcher("IP:$ipv6").hidesPost(both))
    }

    @Test fun ipHeaderProgressIncludesOnlyTheSameLiteralIdentity() {
        val posts = listOf(post("1", "IP:$ipv6"), post("2", "IP:$ipv6"), post("3", "IP:2001:db8:abcd::*"),
            post("4", body = "&gt;IP:$ipv6"), post("5", posterId = "ID:$ipv6"))
        val counts = ftutterIdCounts(posts)
        assertEquals(ipv6, counts["1"]?.id)
        assertEquals(1, counts["1"]?.nth)
        assertEquals(2, counts["1"]?.total)
        assertEquals(2, counts["2"]?.nth)
        assertEquals(1, counts["3"]?.total)
        assertNull(counts["4"])
        assertEquals(1, counts["5"]?.total)
    }

    @Test fun ipQuotePopupTargetsAreKindAwarePriorHeadersWithoutCreatingDirectReplies() {
        val posts = listOf(
            post("1", "IP:$ipv6"), post("2", posterId = "ID:$ipv6"),
            post("3", body = "IP:$ipv6"), post("4", "IP:$ipv6"),
            post("5", body = "&gt;IP:$ipv6").copy(quoteReferences = listOf(QuoteReference(
                ">IP:$ipv6", listOf("1"), isDirectReply = false
            ))), post("6", "IP:$ipv6")
        )
        assertEquals(listOf(0, 3), ftutterQuoteLineTargets(posts, 4, ">IP:$ipv6"))
        assertEquals(listOf(0, 3), ftutterQuoteLineTargets(posts, 4, "＞IP:2001:DB8"))
        assertTrue(ftutterQuoteLineTargets(posts, 4, ">IP:").isEmpty())
        assertEquals(listOf(1), ftutterQuoteLineTargets(posts, 4, ">ID:2001:DB8"))
        assertTrue(ftutterReplyIndex(posts).isEmpty())
        assertTrue(ftutterQuoteTargets(posts, 4).isEmpty())
        assertEquals(listOf(1), ftutterQuoteLineTargets(posts, 4, ">ID:$ipv6"))
    }

    @Test fun ordinaryAndNestedQuoteLinksKeepTheirExistingReplyTargets() {
        val posts = listOf(post("1", "IP:$ipv4"), post("2").copy(quoteReferences = listOf(
            QuoteReference(">No.1", listOf("1"))
        )), post("3").copy(quoteReferences = listOf(
            QuoteReference(">>No.1", listOf("1"), isDirectReply = false, replyTargetPostIds = listOf("2"))
        )))
        assertEquals(listOf(0), ftutterQuoteLineTargets(posts, 2, ">>No.1"))
        assertEquals(mapOf("1" to listOf(1), "2" to listOf(2)), ftutterReplyIndex(posts))
        assertEquals(listOf(1), ftutterQuoteTargets(posts, 2))
    }
}
