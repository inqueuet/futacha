package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatHeaderExtractionKind
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.board.FutachaPostItemSelectionMode
import com.valoser.futacha.shared.ui.board.futachaGoogleSearchUrl
import com.valoser.futacha.shared.ui.board.futachaNgRuleAddScope
import com.valoser.futacha.shared.ui.board.futachaNgRuleEditScope
import com.valoser.futacha.shared.ui.board.futachaSelectedItemsText
import com.valoser.futacha.shared.ui.compat.compatMediaIdentity
import com.valoser.futacha.shared.ui.compat.compatViewerMediaPosts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The B7b parity batch of the ふたばー風 mode (detailed NG, post detail operations, related / same ID posts, selection save, read-aloud panel). */
class FutaberParityB7bTest {
    private val may = BoardSummary("may-b", "虹裏may", "二次元", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "チュートリアル", "https://www.example.com/t/futaba.php", "")

    private fun post(
        id: String,
        html: String,
        posterId: String? = null,
        timestamp: String = "26/10/12(日)12:00:00",
        quotes: List<QuoteReference> = emptyList(),
        image: String? = null
    ) = Post(
        id = id, author = "としあき", subject = null, timestamp = timestamp, posterId = posterId, messageHtml = html,
        imageUrl = image, thumbnailUrl = image?.replace("/src/", "/thumb/"), quoteReferences = quotes
    )

    // ---- the switches ----

    @Test fun theNewExtensionsAreOffByDefaultAndReadOnlyAnOn() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extNgDetail); assertFalse(defaults.extGallerySelectSave); assertFalse(defaults.extReadAloudPanel)
        assertFalse(defaults.extPostDetailOps)
        val on = FutaberDisplaySettings.from(listOf(
            FutaberSettingKeys.EXT_NG_DETAIL, FutaberSettingKeys.EXT_GALLERY_SELECT_SAVE, FutaberSettingKeys.EXT_READ_ALOUD_PANEL
        ).associateWith { "ON" })
        assertTrue(on.extNgDetail && on.extGallerySelectSave && on.extReadAloudPanel)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_NG_DETAIL to "on")).extNgDetail)
        assertEquals(3, setOf(FutaberSettingKeys.EXT_NG_DETAIL, FutaberSettingKeys.EXT_GALLERY_SELECT_SAVE, FutaberSettingKeys.EXT_READ_ALOUD_PANEL).size)
    }

    // ---- N02 ----

    @Test fun theDetailedNgOpensOnlyForABoardTheSharedStoreCanKeep() {
        val catalog = assertNotNull(futaberNgDetailRequest(may, null))
        assertEquals(futaberNgScopeKey(may), catalog.boardKey)
        assertNull(catalog.threadTabKey)
        val thread = assertNotNull(futaberNgDetailRequest(may, "123"))
        assertEquals(futaberCompatTabKey(may, "123"), thread.threadTabKey)
        assertNull(futaberNgDetailRequest(tutorial, "1"))
        assertNull(futaberNgDetailRequest(tutorial, null))
    }

    @Test fun theEntryIsHiddenWhileOffAndDisabledWithoutABoardKey() {
        var opened: FutaberNgDetailRequest? = null
        assertNull(futaberNgDetailEntry(false, true, may, "1") { opened = it })
        assertNull(futaberNgDetailEntry(true, true, null, "1") { opened = it })
        assertFalse(assertNotNull(futaberNgDetailEntry(true, true, tutorial, "1") { opened = it }).enabled)
        assertFalse(assertNotNull(futaberNgDetailEntry(true, false, may, "1") { opened = it }).enabled)
        val entry = assertNotNull(futaberNgDetailEntry(true, true, may, "9") { opened = it })
        assertTrue(entry.enabled)
        entry.open()
        assertEquals(futaberCompatTabKey(may, "9"), opened?.threadTabKey)
        val item = assertNotNull(futaberNgDetailMenuItem(entry) {})
        assertEquals(FUTABER_NG_DETAIL_LABEL, item.label)
        assertEquals("ng-detail", assertNotNull(futaberNgDetailCatalogAction(entry)).id)
        assertNull(futaberNgDetailMenuItem(null) {})
        assertNull(futaberNgDetailCatalogAction(null))
    }

    @Test fun aThreadTextRuleOfThisModeIsNeverMadeForOneThread() {
        // ふたばー風 (textGlobalOnly): a new text rule is for every thread whatever the dialog says.
        assertEquals("*", futachaNgRuleAddScope(global = false, image = false, boardKey = "b", tabKey = "t", textGlobalOnly = true))
        assertEquals("*", futachaNgRuleAddScope(global = true, image = false, boardKey = "b", tabKey = "t", textGlobalOnly = true))
        // An edit keeps a rule another mode made for this thread, or makes it global; it never moves it to the thread.
        assertEquals("t", futachaNgRuleEditScope("t", global = false, image = false, boardKey = "b", tabKey = "t", textGlobalOnly = true))
        assertEquals("*", futachaNgRuleEditScope("*", global = true, image = false, boardKey = "b", tabKey = "t", textGlobalOnly = true))
        // Image rules keep "この板のみ" (a board scope is valid for them).
        assertEquals("b", futachaNgRuleAddScope(global = false, image = true, boardKey = "b", tabKey = "t", textGlobalOnly = false))
        // The usual dialog (ふたちゃ) is unchanged: thread, board for the catalog, "*" when global.
        assertEquals("t", futachaNgRuleAddScope(global = false, image = false, boardKey = "b", tabKey = "t", textGlobalOnly = false))
        assertEquals("b", futachaNgRuleAddScope(global = false, image = false, boardKey = "b", tabKey = null, textGlobalOnly = false))
        assertEquals("*", futachaNgRuleAddScope(global = true, image = false, boardKey = "b", tabKey = null, textGlobalOnly = false))
        assertEquals("t", futachaNgRuleEditScope("*", global = false, image = false, boardKey = "b", tabKey = "t", textGlobalOnly = false))
    }

    @Test fun theRuleScreensTopBarIsReadableWithItsWhiteTextInEveryTheme() {
        listOf(FutaberTokens.light, FutaberTokens.dark, FutaberTokens.oldDark, FutaberTokens.futaba).forEach { colors ->
            val chrome = futaberNgDetailPalette(colors).chrome
            assertTrue(futaberContrastWithWhite(chrome) >= 4.5f, "$chrome")
        }
    }

    // ---- A05 ----

    @Test fun theCopyPickerOffersNumberIdAndBodyLinesAndJoinsThemByLine() {
        val target = post("123", "一行目<br>二行目", posterId = "ID:abcd1234")
        val copy = futaberPostItemSelection(target, 1, FutachaPostItemSelectionMode.COPY)
        val values = copy.candidates.map { it.value }
        assertEquals("No.123", values.first())
        assertTrue(values.any { it.contains("abcd1234") }, "$values")
        assertTrue("一行目" in values && "二行目" in values, "$values")
        val chosen = copy.copy(selected = setOf(0, values.indexOf("二行目")))
        assertEquals("No.123\n二行目", futachaSelectedItemsText(chosen))
    }

    @Test fun theSearchPickerOffersTheBodysWordsAndJoinsThemByASpace() {
        val search = futaberPostItemSelection(post("5", "ねこ 犬<br>Kotlin"), 0, FutachaPostItemSelectionMode.SEARCH)
        assertTrue(search.candidates.isNotEmpty())
        assertTrue(search.candidates.all { it.label.isEmpty() })
        val text = futachaSelectedItemsText(search.copy(selected = search.candidates.indices.toSet()))
        assertTrue(text.contains("Kotlin"), text)
        assertTrue(futachaGoogleSearchUrl(text).orEmpty().startsWith("https://www.google.com/search?q="))
    }

    @Test fun theQuickQuoteQuotesEveryLineOrTheNumberOfAPostWithoutText() {
        assertEquals(">一行目\n>二行目", futaberQuickQuote(post("1", "一行目<br>二行目"), 0))
        assertEquals(">No.7", futaberQuickQuote(post("7", ""), 3))
    }

    // ---- T09 ----

    private val thread = listOf(
        post("1", "スレ本文", posterId = "ID:aaa"),
        post("2", "&gt;スレ本文<br>返信", posterId = "ID:bbb", quotes = listOf(QuoteReference(">スレ本文", listOf("1")))),
        post("3", "&gt;返信<br>さらに", posterId = "ID:aaa", quotes = listOf(QuoteReference(">返信", listOf("2")))),
        post("4", "ひとりごと", timestamp = "26/10/12(日)12:01:00 IP:1.2.3.*"),
        post("5", "もうひとつ", timestamp = "26/10/12(日)12:02:00 IP:1.2.3.*")
    )

    @Test fun relatedPostsAreTheSourcesAndRepliesAroundThePost() {
        assertEquals(listOf("1", "2", "3"), futaberRelatedPosts(thread[1], thread).map { it.id })
        assertTrue(futaberHasRelatedPosts(thread[1], thread))
        assertFalse(futaberHasRelatedPosts(thread[3], thread))
    }

    @Test fun samePosterPostsAreFoundByIdAndByIp() {
        assertEquals(setOf(CompatHeaderExtractionKind.ID), futaberPosterIdentityKinds(thread[0], 0))
        assertEquals(listOf("1", "3"), futaberSameIdentityPosts(thread, thread[0], CompatHeaderExtractionKind.ID).map { it.id })
        assertEquals(setOf(CompatHeaderExtractionKind.IP), futaberPosterIdentityKinds(thread[3], 3))
        assertEquals(listOf("4", "5"), futaberSameIdentityPosts(thread, thread[3], CompatHeaderExtractionKind.IP).map { it.id })
        assertTrue(futaberSameIdentityPosts(thread, post("x", "外"), CompatHeaderExtractionKind.ID).isEmpty())
        assertEquals(FutaberQuoteKind.SameIp, futaberIdentityQuoteKind(CompatHeaderExtractionKind.IP))
        assertEquals(FutaberQuoteKind.SameId, futaberIdentityQuoteKind(CompatHeaderExtractionKind.ID))
    }

    @Test fun theSheetRowsAreAddedOnlyWithTheExtensionAndOnlyWhenTheyShowSomething() {
        val ops = FutaberPostDetailOps(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        val none = futaberPostDetailActions(false, ops, thread[1], thread, {}, {}, { _, _, _ -> })
        assertTrue(none.isEmpty())
        val ids = futaberPostDetailActions(true, ops, thread[1], thread, {}, {}, { _, _, _ -> }).map { it.id }
        assertEquals(listOf("select-copy", "select-search", "quick-quote", "related", "same-id"), ids)
        val lone = futaberPostDetailActions(true, ops, thread[3], thread, {}, {}, { _, _, _ -> }).map { it.id }
        assertEquals(listOf("select-copy", "select-search", "quick-quote", "same-ip"), lone)
    }

    @Test fun theSheetRowsOpenThePickerTheReplyAndTheBubble() {
        val ops = FutaberPostDetailOps(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        var closed = 0
        var quote: String? = null
        var bubble: Pair<FutaberQuoteKind, List<String>>? = null
        val actions = futaberPostDetailActions(true, ops, thread[1], thread, { closed++ }, { quote = it }, { kind, posts, _ ->
            bubble = kind to posts.map { it.id }
        }).associateBy { it.id }
        actions.getValue("select-copy").onClick()
        assertEquals(FutachaPostItemSelectionMode.COPY, ops.selection?.mode)
        actions.getValue("quick-quote").onClick()
        // Every line, quoted ones too (the same text as ふたちゃ and としあき(仮)).
        assertEquals(">>スレ本文\n>返信", quote)
        actions.getValue("related").onClick()
        assertEquals(FutaberQuoteKind.Related to listOf("1", "2", "3"), bubble)
        assertEquals(3, closed)
    }

    // ---- M03 ----

    @Test fun theSharedListGetsTheSnapshotWithoutWhatNgHidesAndOpensTheSameViewerPage() {
        val page = ThreadPage(
            threadId = "1", boardTitle = "may", expiresAtLabel = null, deletedNotice = null,
            posts = listOf(
                post("1", "a", image = "https://may.2chan.net/b/src/1.jpg"),
                post("2", "b", image = "https://may.2chan.net/b/src/2.jpg"),
                post("3", "c", image = "https://may.2chan.net/b/src/3.jpg")
            )
        )
        val snapshot: CompatThreadSnapshot = page.toCompatThreadSnapshot("tab", 0)
        val hidden = setOf("2")
        val shared = futaberGallerySnapshot(snapshot, hidden)
        assertEquals(listOf("1", "3"), shared.posts.map { it.postNo })
        assertEquals(snapshot, futaberGallerySnapshot(snapshot, emptySet()))
        // The viewer's list: the same snapshot and the same hidden posts.
        val viewer = compatViewerMediaPosts(posts = snapshot.posts, hiddenPostNos = hidden)
        val sharedList = compatViewerMediaPosts(posts = shared.posts)
        assertEquals(viewer.map(::compatMediaIdentity), sharedList.map(::compatMediaIdentity))
        sharedList.forEachIndexed { index, item ->
            assertEquals(index, futaberViewerIndexFor(viewer, compatMediaIdentity(item), index))
        }
        // An identity the viewer lacks falls back to the list's index, kept inside the viewer's pages.
        assertEquals(1, futaberViewerIndexFor(viewer, "missing", 1))
        assertEquals(1, futaberViewerIndexFor(viewer, null, 9))
        assertEquals(0, futaberViewerIndexFor(emptyList(), null, 5))
    }

    // ---- R04 ----

    @Test fun thePanelShowsThePositionAndTurnsTheSliderIntoAPost() {
        assertEquals("3 / 10", futaberReadAloudPositionLabel(2, 10))
        assertEquals("- / 10", futaberReadAloudPositionLabel(-1, 10))
        assertEquals("- / 0", futaberReadAloudPositionLabel(0, 0))
        assertEquals(4, futaberReadAloudSliderIndex(3.6f, 10))
        assertEquals(9, futaberReadAloudSliderIndex(20f, 10))
        assertEquals(0, futaberReadAloudSliderIndex(-1f, 10))
        assertEquals(0, futaberReadAloudSliderIndex(5f, 1))
    }
}
