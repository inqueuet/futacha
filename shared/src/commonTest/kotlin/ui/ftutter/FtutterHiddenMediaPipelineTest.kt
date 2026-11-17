package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.futaber.futaberHiddenWithAi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FtutterHiddenMediaPipelineTest {
    private val url = "https://may.2chan.net/b/"
    private fun post(id: String, body: String = "visible") = Post(id, author = null, subject = null, timestamp = "", messageHtml = body,
        imageUrl = "${url}src/$id.png", thumbnailUrl = null)
    private val posts = listOf(post("100"), post("101", "blocked<br>https://dec.2chan.net/up2/src/fu1234567.png"), post("102"))
    private suspend fun view(
        posts: List<Post> = this.posts, rules: List<FtutterNgRule> = emptyList(), common: List<String> = emptyList(),
        extra: Set<String> = emptySet(), chain: Boolean = false, settings: FtutterThreadSettings = FtutterThreadSettings()
    ) = FtutterThreadWork().view(posts, settings, FtutterThreadFilter(), 0, FtutterNgMatcher(rules, url), common, emptyList(), extra, chain)
    private fun hiddenIds(view: FtutterThreadView) = view.hidden.ngIncludingChained.mapTo(HashSet()) { view.posts[it].id }

    @Test fun modeCommonImageAiAndReportedNgUseTheSameMediaExclusion() = runBlocking<Unit> {
        val modeNg = listOf(FtutterNgRule("blocked", FtutterNgType.WORD.stored))
        val cases = listOf(
            view(rules = modeNg), view(common = listOf("blocked")),
            view(extra = futaberHiddenWithAi(emptySet(), setOf("101"), emptySet())),
            view(extra = futaberHiddenWithAi(emptySet(), emptySet(), setOf("101"))),
            view(extra = setOf("101")) // reported-post NG joins the same extraHiddenIds result
        )
        cases.forEach { result ->
            assertEquals(setOf("101"), hiddenIds(result))
            val media = ftutterMediaPosts(result.posts, false, hiddenPostIds = hiddenIds(result))
            assertEquals(listOf("100", "102"), media.map { it.postNo })
            assertEquals(listOf(0, 2), media.map { it.position })
        }
    }

    @Test fun ngChainFollowsTheThreadDecisionAndDisablingTheRuleRestoresMedia() = runBlocking<Unit> {
        val chained = posts.toMutableList().apply { this[2] = this[2].copy(quoteReferences = listOf(QuoteReference(">No.101", listOf("101")))) }
        val rules = listOf(FtutterNgRule("blocked", FtutterNgType.WORD.stored))
        assertEquals(setOf("101", "102"), hiddenIds(view(chained, rules, chain = true)))
        assertEquals(setOf("101"), hiddenIds(view(chained, rules, chain = false)))
        val cleared = view(chained, rules.map { it.copy(isActive = false) }, chain = true)
        assertTrue(hiddenIds(cleared).isEmpty())
        assertEquals(listOf("100", "101", "101", "102"), ftutterMediaPosts(chained, false, hiddenPostIds = hiddenIds(cleared)).map { it.postNo })
    }

    @Test fun deletionAndDeletionChainStillObeyTheSeparateDeletedImageSetting() = runBlocking<Unit> {
        val deleted = posts.toMutableList().apply {
            this[1] = this[1].copy(isDeleted = true)
            this[2] = this[2].copy(quoteReferences = listOf(QuoteReference(">No.101", listOf("101"))))
        }
        val settings = FtutterThreadSettings(showDeleted = false, deletedChain = true)
        val result = view(deleted, settings = settings)
        assertTrue(result.hidden.isHidden(1)); assertTrue(result.hidden.isHidden(2))
        assertTrue(hiddenIds(result).isEmpty())
        assertEquals(listOf("100", "101", "101", "102"), ftutterMediaPosts(deleted, false, hiddenPostIds = hiddenIds(result)).map { it.postNo })
        val alsoNg = view(deleted, listOf(FtutterNgRule("blocked", FtutterNgType.WORD.stored)), settings = settings)
        assertEquals(setOf("101"), hiddenIds(alsoNg))
        assertEquals(listOf("100", "102"), ftutterMediaPosts(deleted, false, keepPostId = "101", hiddenPostIds = hiddenIds(alsoNg)).map { it.postNo })
    }

    @Test fun filteringAHeldSnapshotRemovesAllMediaOfThePostWithoutRenumbering() {
        val held = ftutterMediaPosts(posts, false)
        assertEquals(listOf("100", "102"), ftutterVisibleMedia(held, setOf("101")).map { it.postNo })
        assertEquals(listOf(0, 2), ftutterVisibleMedia(held, setOf("101")).map { it.position })
        assertTrue(ftutterVisibleMedia(held, setOf("100", "101", "102")).isEmpty())
        assertEquals(held, ftutterVisibleMedia(held, emptySet()))
    }
}
