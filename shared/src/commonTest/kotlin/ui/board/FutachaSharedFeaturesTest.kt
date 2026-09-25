package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import kotlin.test.*

class FutachaSharedFeaturesTest {
    private val context = FutachaThreadProjection("tab-a", "board-a")
    private fun post(id: String, body: String = "本文", image: String? = null, saidane: String? = null, replies: Int = 0) =
        Post(id, id.toInt(), null, null, "", messageHtml = body, imageUrl = image,
            thumbnailUrl = image?.replace("/src/", "/thumb/"), saidaneLabel = saidane, referencedCount = replies)
    private fun page(vararg posts: Post) = ThreadPage("1", "板", null, null, posts.toList())
    private fun rule(kind: CompatNgKind, scope: String, value: String) =
        CompatNgRule(compatNgRuleId(kind, scope, value), kind, scope, value, 1L)

    @Test fun noSharedFilteringRetainsTheExistingPageAndPostList() {
        val original = page(post("1"), post("2"))
        val result = projectFutachaThread(original, original, context, emptyList(), emptyMap())
        assertSame(original, result)
        assertSame(original.posts, result.posts)
    }

    @Test fun sharedNgScopesApplyInFutachaAndCanBeExtractedWithoutDestroyingTheOriginalPage() {
        val original = page(post("1", "通常"), post("2", "秘密"), post("3", "別スレだけ"), post("4", image = "https://a/src/4.jpg"))
        val rules = listOf(rule(CompatNgKind.THREAD_IGNORE, "tab-a", "秘密"),
            rule(CompatNgKind.THREAD_IGNORE, "tab-b", "別スレだけ"),
            rule(CompatNgKind.THREAD_IMAGE, "board-a", "https://a/src/4.jpg"))
        assertEquals(listOf("1", "3"), projectFutachaThread(original, original, context, rules, emptyMap()).posts.map { it.id })
        assertEquals(listOf("2", "4"), projectFutachaThread(original, original, context.copy(extraction = CompatExtractionKind.NG), rules, emptyMap()).posts.map { it.id })
        assertEquals(4, original.posts.size)
        assertEquals(original.posts, projectFutachaThread(original, original, context, rules,
            mapOf(compatPreferenceStorageKey("thread", "threadNg") to "OFF")).posts)
    }

    @Test fun sharedThresholdChangesExtractionImmediatelyAndPhashMatchesStayHidden() {
        val original = page(post("1", saidane = "そうだねx2", replies = 1), post("2", saidane = "そうだねx5", replies = 4), post("3", saidane = "そうだねx9", replies = 7))
        val preferences = mapOf(compatPreferenceStorageKey("thread", "threadExtractSoudaneNum") to "4",
            compatPreferenceStorageKey("thread", "threadExtractQuoteNum") to "3")
        assertEquals(listOf("2"), projectFutachaThread(original, original, context.copy(extraction = CompatExtractionKind.MANY_SAIDANE),
            emptyList(), preferences, phashHidden = setOf("3")).posts.map { it.id })
        assertEquals(listOf("2", "3"), projectFutachaThread(original, original, context.copy(extraction = CompatExtractionKind.MANY_REPLIES),
            emptyList(), preferences).posts.map { it.id })
        assertEquals(listOf("1", "3"), projectFutachaThread(original, original, context.copy(extraction = CompatExtractionKind.NG),
            emptyList(), preferences, phashHidden = setOf("3"), modernNgHidden = setOf("1")).posts.map { it.id })
    }

    @Test fun formatSelectionKeepsTextAndOriginalMediaWhileSelectingOnlyRequestedDownloads() {
        val original = page(post("1", image = "https://a/src/image.jpg"), post("2", image = "https://a/src/video.webm"))
        assertTrue(original.postsForFutachaSave(FutachaPageSaveMode.HTML).all { it.imageUrl == null && it.thumbnailUrl == null })
        assertTrue(original.postsForFutachaSave(FutachaPageSaveMode.THUMBNAILS).all { it.imageUrl == null && it.thumbnailUrl != null })
        assertEquals(original.posts, original.postsForFutachaSave(FutachaPageSaveMode.ALL))
        assertEquals(original.posts.map { it.messageHtml }, original.postsForFutachaSave(FutachaPageSaveMode.HTML).map { it.messageHtml })
    }

    @Test fun boardRenamePreservesIdentityUrlPinAndOrderAndRejectsBlankNames() {
        val boards = listOf(BoardSummary("one", "旧名", "test", "https://may.2chan.net/b/", "説明"),
            BoardSummary("two", "そのまま", "test", "https://img.2chan.net/b/", "説明"))
        val renamed = renameBoardSummary(boards, "one", " 新しい名前 ")
        assertEquals(boards[0].copy(name = "新しい名前"), renamed[0])
        assertEquals(boards[1], renamed[1])
        assertFailsWith<IllegalArgumentException> { renameBoardSummary(boards, "one", "   ") }
    }

    @Test fun backgroundCommonPoliciesEnableBothModesOnlyWhenRequested() {
        assertFalse(sharedFeatureRefreshEnabled(emptyMap()))
        assertTrue(sharedFeatureRefreshEnabled(mapOf("compat.background.backgroundThreadUpdateCheck" to "wifi")))
        assertTrue(sharedFeatureRefreshEnabled(mapOf("compat.background.backgroundThreadExistCheck" to "usually")))
        assertFalse(sharedFeatureRefreshEnabled(mapOf("compat.background.backgroundThreadExistCheck" to "none")))
    }

    @Test fun sharedTabFindsRenamedModernBoardByCanonicalUrl() {
        val url = "https://may.2chan.net/b/"
        val threadUrl = "${url}res/123.htm"
        val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, compatBoardKey(url),
            "以前の板名", "123", "共有タブ", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
        val modernBoard = BoardSummary("modern-board-id", "改名した板", "", url, "")
        val selection = assertNotNull(com.valoser.futacha.shared.ui.resolveHistoryEntrySelection(
            tab.toFutachaHistoryEntry(), listOf(modernBoard)))
        assertEquals(modernBoard.id, selection.boardId)
        assertEquals(threadUrl, selection.threadUrl)
        assertEquals("123", selection.threadId)
    }

    @Test fun projectionSettingsIgnoreUnrelatedPreferences() {
        val base = mapOf("compat.thread.threadExtractSoudaneNum" to "5")
        val withCacheCheck = base + mapOf(
            "compat.cache.checkTime" to "123",
            "compat.background.watcherCheckTime" to "456"
        )
        assertEquals(FutachaThreadProjectionSettings.from(base), FutachaThreadProjectionSettings.from(withCacheCheck))
        assertEquals(5, FutachaThreadProjectionSettings.from(base).saidaneThreshold)
        assertEquals(false, FutachaThreadProjectionSettings.from(base + ("compat.thread.threadNg" to "OFF")).ngEnabled)
    }
}
