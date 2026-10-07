package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.board.ThreadTreeNode
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The "拡張機能" of ふたばー風モード: what they list, and that all of them are off until turned on. */
class FutaberExtrasTest {
    private fun post(
        id: String, html: String = "本文", posterId: String? = null, image: String? = null,
        saidane: String? = null, deleted: Boolean = false, quotes: List<String> = emptyList()
    ) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = html,
        imageUrl = image, thumbnailUrl = image, posterId = posterId, saidaneLabel = saidane,
        isDeleted = deleted, quoteReferences = quotes.map { QuoteReference(">>$it", listOf(it)) }
    )

    private val posts = listOf(
        post("1", html = "ttp://example.com/a を見て", posterId = "AAA"),
        post("2", image = "https://may/src/2.jpg", saidane = "そうだねx4", posterId = "BBB"),
        post("3", deleted = true, posterId = "AAA"),
        post("4", saidane = "そうだねx1", posterId = "CCC")
    )

    @Test fun everyExtensionIsOffByDefaultAndAnEmptySettingReadsAsOff() {
        val defaults = FutaberDisplaySettings()
        assertFalse(defaults.extTree); assertFalse(defaults.extExtract); assertFalse(defaults.extIdTap); assertFalse(defaults.extAutoScroll)
        val read = FutaberDisplaySettings.from(emptyMap())
        assertEquals(defaults, read)
        val on = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.EXT_TREE to "ON", FutaberSettingKeys.EXT_EXTRACT to "ON",
                FutaberSettingKeys.EXT_ID_TAP to "ON", FutaberSettingKeys.EXT_AUTO_SCROLL to "ON"
            )
        )
        assertTrue(on.extTree && on.extExtract && on.extIdTap && on.extAutoScroll)
    }

    @Test fun theAutoScrollAmountAndIntervalReadTheSharedValuesAndFallBackWhenDamaged() {
        val shared = FutaberDisplaySettings.from(
            mapOf(FutaberSettingKeys.AUTO_SCROLL_PIXEL to "12px", FutaberSettingKeys.AUTO_SCROLL_SPEED to "80ミリ秒")
        )
        assertEquals(12, shared.autoScrollPixel)
        assertEquals(80, shared.autoScrollSpeedMillis)
        val damaged = FutaberDisplaySettings.from(
            mapOf(FutaberSettingKeys.AUTO_SCROLL_PIXEL to "abc", FutaberSettingKeys.AUTO_SCROLL_SPEED to "99999")
        )
        assertEquals(FUTABER_AUTO_SCROLL_PIXEL_DEFAULT, damaged.autoScrollPixel)
        assertEquals(FUTABER_AUTO_SCROLL_SPEED_DEFAULT, damaged.autoScrollSpeedMillis)
    }

    @Test fun eachExtractKindListsTheRightPosts() {
        fun ids(kind: FutaberExtract, self: Set<String> = emptySet()) = futaberVisibleRows(
            posts, FutaberViewFilter(extract = kind), emptyMap(), selfIds = self
        ).map { it.post.id }
        assertEquals(listOf("2"), ids(FutaberExtract.SelfPosts, setOf("2")))
        assertEquals(emptyList(), ids(FutaberExtract.SelfPosts))
        assertEquals(listOf("2"), ids(FutaberExtract.Saidane))
        assertEquals(listOf("3"), ids(FutaberExtract.Deleted))
        assertEquals(listOf("1"), ids(FutaberExtract.Url))
        assertEquals(listOf("2"), ids(FutaberExtract.Image))
    }

    @Test fun theSamePosterIdListsThatPostersPostsWithTheirOwnNumbers() {
        val rows = futaberVisibleRows(posts, FutaberViewFilter(sameId = "AAA"), emptyMap())
        assertEquals(listOf("1", "3"), rows.map { it.post.id })
        assertEquals(listOf(0, 2), rows.map { it.ordinal })
        assertTrue(FutaberViewFilter(sameId = "AAA").isActive)
        assertTrue(FutaberViewFilter(extract = FutaberExtract.Url).isActive)
        assertFalse(FutaberViewFilter().isActive)
    }

    @Test fun theTreePutsRepliesUnderTheQuotedPostAndKeepsEachPostsNumber() {
        val thread = listOf(post("1"), post("2"), post("3", quotes = listOf("1")), post("4", quotes = listOf("3")))
        val tree = listOf(
            ThreadTreeNode(thread[0], 0), ThreadTreeNode(thread[2], 1), ThreadTreeNode(thread[3], 2), ThreadTreeNode(thread[1], 0)
        )
        val rows = futaberApplyTree(futaberVisibleRows(thread, FutaberViewFilter(), emptyMap()), tree)
        assertEquals(listOf("1", "3", "4", "2"), rows.map { it.post.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
        assertEquals(listOf(0, 2, 3, 1), rows.map { it.ordinal })
        // A filter applied before the tree still lists only the posts that pass it.
        val filtered = futaberApplyTree(futaberVisibleRows(thread, FutaberViewFilter(query = "本文"), emptyMap(), hidden = setOf("3")), tree)
        assertEquals(listOf("1", "4", "2"), filtered.map { it.post.id })
        assertEquals(thread.map { 0 }, futaberApplyTree(futaberVisibleRows(thread, FutaberViewFilter(), emptyMap()), emptyList()).map { it.depth })
    }

    @Test fun scrollingToAPostFindsItsRowEvenWhenTheTreeListsRowsOutOfOrder() {
        val rows = listOf(FutaberRow(0, post("1")), FutaberRow(5, post("6")), FutaberRow(2, post("3")))
        assertEquals(2, futaberRowPositionAtOrAfter(rows, 2))
        assertEquals(1, futaberRowPositionAtOrAfter(rows, 3))
        assertEquals(null, futaberRowPositionAtOrAfter(rows, 9))
    }

    @Test fun aChipCyclesThroughItsChoicesAndWrapsAround() {
        assertEquals(2, futaberNextChoice(listOf(1, 2, 3), 1))
        assertEquals(1, futaberNextChoice(listOf(1, 2, 3), 3))
    }

    @Test fun theOtherExtensionsAreOffByDefaultToo() {
        val defaults = FutaberDisplaySettings()
        assertFalse(defaults.extHistory); assertFalse(defaults.extMailPresets); assertFalse(defaults.extVolumeKeys)
        val on = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.EXT_HISTORY to "ON", FutaberSettingKeys.EXT_MAIL_PRESETS to "ON",
                FutaberSettingKeys.EXT_VOLUME_KEYS to "ON"
            )
        )
        assertTrue(on.extHistory && on.extMailPresets && on.extVolumeKeys)
    }

    @Test fun aPostsPictureAddressIsTheFullOneElseTheThumbnail() {
        assertEquals("https://x/full.jpg", futaberImageUrlOf(post("1", image = "https://x/full.jpg")))
        val thumbOnly = post("2").copy(imageUrl = null, thumbnailUrl = "https://x/t.jpg")
        assertEquals("https://x/t.jpg", futaberImageUrlOf(thumbOnly))
        assertEquals(null, futaberImageUrlOf(post("3")))
        assertEquals(null, futaberImageUrlOf(post("4").copy(imageUrl = " ", thumbnailUrl = "")))
    }

    @Test fun anImageNgRuleReadsAsItsNoteElseTheFileNameAndItsScope() {
        val rule = com.valoser.futacha.shared.compat.CompatNgRule(
            id = "i", kind = com.valoser.futacha.shared.compat.CompatNgKind.THREAD_IMAGE_PHASH, scopeKey = "*",
            normalizedValue = "0123456789abcdef", createdAtEpochMillis = 1L, imageUrl = "https://x/b/src/99.jpg?x=1", memo = "迷惑画像"
        )
        assertEquals("迷惑画像　全板", futaberImageNgRuleLabel(rule))
        assertEquals("99.jpg　この板", futaberImageNgRuleLabel(rule.copy(memo = "", scopeKey = "https://x/b/")))
    }

    private fun entry(id: String, board: String = "t", selfPost: Boolean = false, saved: Boolean = false, fallen: Boolean = false) =
        com.valoser.futacha.shared.model.ThreadHistoryEntry(
            threadId = id, boardId = board, title = "t$id", titleImageUrl = "", boardName = "板", boardUrl = "https://x/$board/res/$id.htm",
            lastVisitedEpochMillis = 1L, replyCount = 1, hasSelfPost = selfPost, hasAutoSave = saved, isAutoRefreshDisabled = fallen
        )

    @Test fun aHistoryRowsMarksNameWhatHappenedToTheThread() {
        assertEquals("", futaberHistoryMarks(entry("1")))
        assertEquals("落ちた・書き込み済み・保存済み", futaberHistoryMarks(entry("1", selfPost = true, saved = true, fallen = true)))
        assertEquals("保存済み", futaberHistoryMarks(entry("1", saved = true)))
    }

    @Test fun closedTabsComeBackOldestFirstAndOnlyWhileTheirThreadAndBoardAreStillThere() {
        val board = com.valoser.futacha.shared.model.BoardSummary("t", "板", "", "https://x/t/", "")
        val history = listOf(entry("1"), entry("2"), entry("3"))
        val closed = listOf(FutaberTabKey("t", "3"), FutaberTabKey("t", "2"), FutaberTabKey("t", "9"), FutaberTabKey("gone", "1"))
        // The latest closed comes first in [closed]; "9" has no history row and "gone" has no board.
        assertEquals(
            listOf(FutaberTabKey("t", "2"), FutaberTabKey("t", "3")),
            futaberRestorableTabs(closed, emptyList(), history, listOf(board))
        )
        // A tab that is open again is not restored twice.
        assertEquals(
            listOf(FutaberTabKey("t", "2")),
            futaberRestorableTabs(closed, listOf(FutaberTabKey("t", "3")), history, listOf(board))
        )
    }

    @Test fun theTabExtensionIsOffByDefaultAndTheVideoViewerIsOnUntilTurnedOff() {
        assertFalse(FutaberDisplaySettings().extTabs)
        assertTrue(FutaberDisplaySettings().extVideo)
        assertTrue(FutaberDisplaySettings.from(emptyMap()).extVideo)
        assertTrue(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_VIDEO to "ON")).extVideo)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_VIDEO to "OFF")).extVideo)
        assertTrue(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_TABS to "ON")).extTabs)
    }

    @Test fun theVideoPanelHidesOnlyWhilePlayingWithHiddenControls() {
        val ready = com.valoser.futacha.shared.ui.board.VideoPlayerState.Ready
        assertFalse(futaberVideoShowsPanel(ready, controlsVisible = false))
        assertTrue(futaberVideoShowsPanel(ready, controlsVisible = true))
        for (state in listOf(
            com.valoser.futacha.shared.ui.board.VideoPlayerState.Idle,
            com.valoser.futacha.shared.ui.board.VideoPlayerState.Buffering,
            com.valoser.futacha.shared.ui.board.VideoPlayerState.Error
        )) assertTrue(futaberVideoShowsPanel(state, controlsVisible = false))
    }

    @Test fun aFoundArchiveThreadReadsAsItsPostCountAndStatus() {
        val item = com.valoser.futacha.shared.network.ArchiveSearchItem(
            threadId = "123", server = "may", board = "b", title = "題", htmlUrl = "https://may.inqueuet.com/b/res/123.htm",
            replyCount = 150, status = "available"
        )
        assertEquals("150レス　available", futaberArchiveItemLine(item))
        assertEquals("80レス", futaberArchiveItemLine(item.copy(replyCount = 80, status = " ")))
        assertEquals("0レス", futaberArchiveItemLine(item.copy(replyCount = 0, status = null)))
        assertTrue(futaberArchiveNotice().isNotEmpty())
    }

    @Test fun theIconsAreRoundedGlyphsAndThisModesOwnIconsAreKept() {
        val outlined = androidx.compose.material.icons.Icons.Outlined.Close
        val rounded = futaberIconFor(outlined)
        assertTrue(rounded !== outlined)
        assertEquals("Rounded.Close", rounded.name)
        // A glyph the table does not know (this mode's own icons) is drawn as it is.
        assertTrue(futaberIconFor(FutaberTwoPaneIcon) === FutaberTwoPaneIcon)
        // The star shows its state by fill: a line star until the thread is starred, a solid one after.
        assertEquals("Rounded.StarOutline", futaberIconFor(androidx.compose.material.icons.Icons.Outlined.StarOutline).name)
        assertEquals("Rounded.Star", futaberIconFor(androidx.compose.material.icons.Icons.Outlined.Star).name)
    }
}
