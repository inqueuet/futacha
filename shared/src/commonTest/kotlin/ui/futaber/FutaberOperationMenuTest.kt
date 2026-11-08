package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import com.valoser.futacha.shared.model.Post
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L-B6「操作メニュー」(docs/futaber-evolution-look-2026-10-31.md §1.2 2・3, §1.3, §2.2): the original's eleven slots in its
 * order and every other item after them from one function; the search word's and 「返信が多いレスを表示」's results;
 * the small pictures' size; the two switches of the old behaviour.
 */
class FutaberOperationMenuTest {
    private fun item(id: String, label: String = id, onClick: () -> Unit = {}) = FutaberMenuItem(id, label, Icons.Outlined.Close, onClick = onClick)

    private fun post(id: String, body: String = "本文$id", subject: String? = "無題") = Post(
        id = id, author = "名無し", subject = subject, timestamp = "26/10/16(金)22:37:55", messageHtml = body,
        imageUrl = null, thumbnailUrl = null
    )

    private fun extras(close: () -> Unit = {}, allExtensions: Boolean = true) = futaberOperationMenuExtras(
        close = close,
        liveReadAloudEnabled = true, onLiveReadAloud = {},
        saveEnabled = true, onSave = {},
        onShareUrl = {}, onOpenBrowser = {},
        undoReloadEnabled = false, onUndoReload = {},
        isTabbed = false, tabEnabled = true, onToggleTab = {},
        onWrite = {},
        extract = if (allExtensions) FutaberMenuToggle(false) {} else null,
        autoScroll = if (allExtensions) FutaberMenuToggle(false) {} else null,
        onArchive = if (allExtensions) ({}) else null,
        onCacheSearch = if (allExtensions) ({}) else null,
        ngDetail = if (allExtensions) FutaberNgDetailEntry(true) {} else null,
        scrollItems = if (allExtensions) listOf(item("page-up"), item("page-down"), item("top")) else emptyList(),
        onImageEdit = if (allExtensions) ({}) else null,
        onVideoEdit = if (allExtensions) ({}) else null
    )

    @Test fun theElevenSlotsAreTheOriginalsInItsOrder() {
        assertEquals(
            listOf(
                "copy-url", "small-images", "many-replies", "read-aloud",
                "url-scheme", "favorite", "mht-save", "ng-edit",
                "ng-toggle", "new", "player"
            ),
            FUTABER_MENU_SLOT_IDS
        )
        assertEquals(4, FUTABER_MENU_COLUMNS)
        assertEquals(3, FUTABER_MENU_VISIBLE_ROWS)
        // Slot by slot from their ids, whatever order the screen hands them in; the player is an empty cell until L-B12.
        val slots = FUTABER_MENU_SLOT_IDS.filter { it != FUTABER_MENU_PLAYER_ID }.reversed().map { item(it) }
        val cells = futaberOperationMenuItems(slots, emptyList())
        assertEquals(11, cells.size)
        assertEquals(FUTABER_MENU_SLOT_IDS.dropLast(1), cells.take(10).map { it?.id })
        assertNull(cells[10], "「プレイヤーを表示」 is left empty until L-B12")
    }

    @Test fun everyOtherItemComesAfterTheEleventhSlotFromOneFunction() {
        val slots = FUTABER_MENU_SLOT_IDS.map { item(it) }
        val extra = extras()
        assertEquals(
            listOf(
                // This app's own items, in their old order.
                "live-read-aloud", "save", "share-url", "open-browser", "undo-reload", "tab", "write",
                // The extensions, in the order of the extensions page.
                "extract", "auto-scroll", "archive", "cache-search", "ng-detail", "page-up", "page-down", "top",
                "image-edit", "video-edit"
            ),
            extra.map { it.id }
        )
        val cells = futaberOperationMenuItems(slots, extra)
        assertEquals(FUTABER_MENU_SLOT_IDS, cells.take(11).map { it?.id })
        assertEquals(extra.map { it.id }, cells.drop(11).map { it?.id })
        assertTrue(extra.none { it.id in FUTABER_MENU_SLOT_IDS }, "no extra takes a slot of the original")
        // With every extension off, this app's own seven are still there, right after the slots.
        assertEquals(listOf("live-read-aloud", "save", "share-url", "open-browser", "undo-reload", "tab", "write"),
            extras(allExtensions = false).map { it.id })
        // An item that is not the original's does not get into a slot.
        assertEquals(FUTABER_MENU_SLOT_IDS.dropLast(1), futaberOperationMenuItems(slots.dropLast(1) + item("write"), emptyList()).take(10).map { it?.id })
    }

    @Test fun eachExtraClosesTheMenuBeforeItActs() {
        val order = mutableListOf<String>()
        val extra = futaberOperationMenuExtras(
            close = { order += "close" },
            liveReadAloudEnabled = true, onLiveReadAloud = { order += "live" },
            saveEnabled = true, onSave = { order += "save" },
            onShareUrl = { order += "share" }, onOpenBrowser = { order += "browser" },
            undoReloadEnabled = true, onUndoReload = { order += "undo" },
            isTabbed = true, tabEnabled = true, onToggleTab = { order += "tab" },
            onWrite = { order += "write" },
            extract = FutaberMenuToggle(true) { order += "extract" }
        )
        extra.forEach { it.onClick() }
        assertEquals(
            listOf("close", "live", "close", "save", "close", "share", "close", "browser", "close", "undo", "close", "tab",
                "close", "write", "close", "extract"),
            order
        )
        assertEquals("タブから外す", extra.first { it.id == "tab" }.label)
        assertTrue(extra.first { it.id == "extract" }.active)
    }

    @Test fun smallPicturesAreThreeTenthsOfTheUsualSize() {
        assertEquals(0.21f, futaberAttachmentRatio(true), 0.0001f)
        assertEquals(FUTABER_ATTACHMENT_RATIO, futaberAttachmentRatio(false))
        val usual = futaberAttachmentSize(250, 188, futaberAttachmentRatio(false))
        val small = futaberAttachmentSize(250, 188, futaberAttachmentRatio(true))
        // futaber: 175×132 → 53×40.
        assertEquals(175f, usual.width, 0.01f)
        assertTrue(abs(small.width - 53f) <= 0.5f, "${small.width}")
        // 188×0.21 = 39.5 (futaber rounds its 132×0.3 = 39.6 to 40).
        assertTrue(abs(small.height - 40f) <= 0.6f, "${small.height}")
        assertEquals(usual.width * 0.3f, small.width, 0.01f)
    }

    @Test fun theSearchWordFindsThePostsNgDoesNotHideFoldingWidthAndKana() {
        val posts = listOf(
            post("1", "スレの本文"), post("2", "ＡＢＣを含むレス"), post("3", "abcも"),
            post("4", "ねこ"), post("5", "ネコ<br>二行目"), post("6", "<br>  <br>ｎｅｋｏの後"), post("7", "abc NG")
        )
        assertEquals(listOf("2", "3", "7"), futaberSearchMatches(posts, "abc").map { it.id })
        assertEquals(listOf("2", "3"), futaberSearchMatches(posts, "ABC", hidden = setOf("7")).map { it.id })
        assertEquals(listOf("4", "5"), futaberSearchMatches(posts, "ねこ").map { it.id })
        assertTrue(futaberSearchMatches(posts, "  ").isEmpty())
        assertTrue(futaberSearchMatches(posts, "見つからない語").isEmpty())
        // The same answer with the texts worked out ahead.
        val texts = futaberBuildPostTexts(null, posts)
        assertEquals(listOf("4", "5"), futaberSearchMatches(posts, "ねこ", texts = texts).map { it.id })
        assertEquals("8レスが見つかりました", futaberSearchFoundLabel(8))
        assertEquals("レスが見つかりませんでした", FUTABER_NOTHING_FOUND)
        // Each found post is listed by the first line of its body that has text.
        assertEquals("ネコ", futaberSearchMatchLine(posts[4]))
        assertEquals("ｎｅｋｏの後", futaberSearchMatchLine(posts[5]))
        assertEquals("題名", futaberSearchMatchLine(post("8", "", subject = "題名")))
    }

    @Test fun manyRepliesAreThePostsWithAtLeastTheThresholdsReplies() {
        val posts = (1..8).map { post("$it") }
        val replyIndex = mapOf(
            "1" to posts.subList(1, 4), // 3
            "3" to posts.subList(3, 5), // 2
            "6" to posts.subList(1, 7)  // 6
        )
        assertEquals(listOf("1", "6"), futaberManyRepliesPosts(posts, replyIndex).map { it.id })
        assertEquals(listOf("6"), futaberManyRepliesPosts(posts, replyIndex, hidden = setOf("1")).map { it.id })
        assertEquals(listOf("1", "3", "6"), futaberManyRepliesPosts(posts, replyIndex, threshold = 2).map { it.id })
        assertTrue(futaberManyRepliesPosts(posts, emptyMap()).isEmpty())
    }

    @Test fun theMenusBubblesStandAtAnEdgeAndMayBeEmpty() {
        val search = futaberMenuBubble(FutaberQuoteKind.Search, emptyList())
        assertEquals(FutaberBubbleEdge.Bottom, search.edge)
        assertTrue(search.posts.isEmpty())
        val many = futaberMenuBubble(FutaberQuoteKind.ManyReplies, listOf(post("1")))
        assertEquals(FutaberBubbleEdge.Top, many.edge)
        // Placed by the edge: the search over the bar (its pointer under it), many replies under the title (pointer on top).
        val bottom = futaberBubblePlacement(FutaberAnchor(20f, 1000f, 1000f), 300f, 8f, 992f, 20f)
        assertFalse(bottom.pointerOnTop)
        assertEquals(680f, bottom.top)
        val top = futaberBubblePlacement(FutaberAnchor(20f, 0f, 0f), 300f, 8f, 992f, 20f)
        assertTrue(top.pointerOnTop)
        assertEquals(20f, top.top)
    }

    @Test fun theOldBehavioursAreOffUntilSwitchedOn() {
        val none = FutaberDisplaySettings.from(emptyMap())
        assertFalse(none.legacySearchFilter)
        assertFalse(none.legacyManyRepliesFilter)
        val on = FutaberDisplaySettings.from(mapOf(
            FutaberSettingKeys.LEGACY_SEARCH_FILTER to "ON", FutaberSettingKeys.LEGACY_MANY_REPLIES_FILTER to "ON"
        ))
        assertTrue(on.legacySearchFilter)
        assertTrue(on.legacyManyRepliesFilter)
        val off = FutaberDisplaySettings.from(mapOf(
            FutaberSettingKeys.LEGACY_SEARCH_FILTER to "OFF", FutaberSettingKeys.LEGACY_MANY_REPLIES_FILTER to "OFF"
        ))
        assertFalse(off.legacySearchFilter)
        assertFalse(off.legacyManyRepliesFilter)
        assertEquals("compat.futaber.legacySearchFilter", FutaberSettingKeys.LEGACY_SEARCH_FILTER)
        assertEquals("compat.futaber.legacyManyRepliesFilter", FutaberSettingKeys.LEGACY_MANY_REPLIES_FILTER)
    }
}
