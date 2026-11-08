package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.ftutter.FtutterNgBoard
import com.valoser.futacha.shared.ui.ftutter.FtutterNgRule
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterNgList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-B7: the NG ふたったー風 keeps (regex / title / ID) hide posts and threads in ふたばー風 too. */
class FutaberSharedNgTest {
    private fun post(id: String, html: String) = Post(
        id = id, author = null, subject = null, timestamp = "25/10/05(日)12:00:00", posterId = null,
        messageHtml = html, imageUrl = null, thumbnailUrl = null
    )
    private fun item(id: String, title: String) = CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", title, null, null, replyCount = 1)

    private val raw = encodeFtutterNgList(
        listOf(
            FtutterNgRule("荒らし\\d+", 1, isReg = true),
            FtutterNgRule("釣り", 0),
            FtutterNgRule("ピックアップ", 3),
            FtutterNgRule("板だけ", 1, ita = FtutterNgBoard("img", "https://img.2chan.net/b/futaba.php"))
        )
    )

    @Test
    fun aRegularExpressionWordHidesThePostsItMatches() {
        val matcher = futaberSharedNgMatcher(raw, true, "https://may.2chan.net/b/futaba.php")!!
        val posts = listOf(post("1", "普通"), post("2", "これは荒らし12です"), post("3", "板だけの話"))
        val prepared = futaberNgPrepare(emptyList(), emptyList(), emptyList(), "t", "b", matcher)
        assertEquals(setOf("2"), futaberNgEvaluate(posts, prepared))
    }

    @Test
    fun aRuleOfAnotherBoardIsLeftAloneAndThePickupIsNotAnNg() {
        val img = futaberSharedNgMatcher(raw, true, "https://img.2chan.net/b/futaba.php")!!
        assertEquals(setOf("3"), futaberNgEvaluate(listOf(post("3", "板だけの話"), post("4", "ピックアップ")), futaberNgPrepare(emptyList(), emptyList(), emptyList(), "t", "b", img)))
    }

    @Test
    fun theTitleRuleHidesCatalogThreadsAndTheSwitchTurnsItOff() {
        val matcher = futaberSharedNgMatcher(raw, true, "https://may.2chan.net/b/futaba.php")!!
        val shown = futaberApplyCatalogRules(listOf(item("1", "釣りスレ"), item("2", "普通")), emptyList(), null, emptySet(), matcher)
        assertEquals(listOf("2"), shown.map { it.id })
        assertNull(futaberSharedNgMatcher(raw, false, "https://may.2chan.net/b/futaba.php"))
        assertNull(futaberSharedNgMatcher(null, true, "https://may.2chan.net/b/futaba.php"))
        assertTrue(futaberNgEvaluate(listOf(post("1", "荒らし1")), futaberNgPrepare(emptyList(), emptyList(), emptyList(), "t", "b", null)).isEmpty())
    }
}
