package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The second half of the B1 parity batch of the ふたばー風 mode (pure parts). */
class FutaberParityB1bTest {
    private fun post(id: String, image: String? = null) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "本文$id",
        imageUrl = image, thumbnailUrl = image
    )

    // ---- the switches ----

    @Test fun theNewExtensionsAreOffByDefaultAndReadOnlyAnOn() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extSortByBoard); assertFalse(defaults.extCatalogLongPress); assertFalse(defaults.extPostDetailOps)
        assertFalse(defaults.extGalleryColumns); assertFalse(defaults.extImageMenu)
        assertEquals(FutaberVolumeKeyMode.Screen, defaults.volumeKeyMode)
        assertEquals(defaults, FutaberDisplaySettings())
        val keys = listOf(
            FutaberSettingKeys.EXT_SORT_BY_BOARD, FutaberSettingKeys.EXT_CATALOG_LONG_PRESS, FutaberSettingKeys.EXT_POST_DETAIL_OPS,
            FutaberSettingKeys.EXT_GALLERY_COLUMNS, FutaberSettingKeys.EXT_IMAGE_MENU
        )
        assertTrue(keys.all { it.startsWith("compat.futaber.ext.") })
        val on = FutaberDisplaySettings.from(keys.associateWith { "ON" })
        assertTrue(on.extSortByBoard && on.extCatalogLongPress && on.extPostDetailOps && on.extGalleryColumns && on.extImageMenu)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_IMAGE_MENU to "on")).extImageMenu)
    }

    // ---- T15 ----

    @Test fun theVolumeKeyModeReadsItsStoredValueAndFallsBackToAScreen() {
        assertEquals("compat.futaber.ext.volumeKeyMode", FutaberSettingKeys.EXT_VOLUME_KEY_MODE)
        assertEquals(FutaberVolumeKeyMode.Screen, FutaberVolumeKeyMode.fromPersisted(null))
        assertEquals(FutaberVolumeKeyMode.Screen, FutaberVolumeKeyMode.fromPersisted("screen"))
        assertEquals(FutaberVolumeKeyMode.Response, FutaberVolumeKeyMode.fromPersisted("response"))
        assertEquals(FutaberVolumeKeyMode.Thread, FutaberVolumeKeyMode.fromPersisted("thread"))
        assertEquals(FutaberVolumeKeyMode.Screen, FutaberVolumeKeyMode.fromPersisted("THREAD"))
        assertEquals(FutaberVolumeKeyMode.Screen, FutaberVolumeKeyMode.fromPersisted("broken"))
        assertEquals(
            FutaberVolumeKeyMode.Response,
            FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_VOLUME_KEY_MODE to "response")).volumeKeyMode
        )
        assertEquals(listOf("screen", "response", "thread"), FutaberVolumeKeyMode.entries.map { it.persistedValue })
    }

    @Test fun theThreadModeMovesAlongTheTabStripAndDoesNothingOffIt() {
        val a = FutaberTabKey("b", "1"); val b = FutaberTabKey("b", "2"); val c = FutaberTabKey("c", "3")
        val tabs = listOf(a, b, c)
        assertEquals(c, futaberAdjacentTab(tabs, b, forward = true))
        assertEquals(a, futaberAdjacentTab(tabs, b, forward = false))
        // No wrapping at the ends.
        assertNull(futaberAdjacentTab(tabs, c, forward = true))
        assertNull(futaberAdjacentTab(tabs, a, forward = false))
        // A thread that is not a tab, or none open.
        assertNull(futaberAdjacentTab(tabs, FutaberTabKey("b", "9"), forward = true))
        assertNull(futaberAdjacentTab(tabs, null, forward = true))
        assertNull(futaberAdjacentTab(emptyList(), a, forward = true))
    }

    @Test fun aResponseStepGoesToTheNextRowOrBackToTheTopOfTheCurrentOne() {
        assertEquals(4, futaberResponseStepTarget(3, 0, 10, up = false))
        assertEquals(2, futaberResponseStepTarget(3, 0, 10, up = true))
        // Partly scrolled past: up first shows the start of the row.
        assertEquals(3, futaberResponseStepTarget(3, 40, 10, up = true))
        assertEquals(0, futaberResponseStepTarget(0, 0, 10, up = true))
        assertEquals(10, futaberResponseStepTarget(10, 0, 10, up = false))
        assertEquals(0, futaberResponseStepTarget(0, 0, -1, up = false))
    }

    // ---- C05 ----

    @Test fun theSortPerBoardRoundTripsAndFallsBackToTheSharedValue() {
        val stored = encodeFutaberSortByBoard(mapOf("may" to CatalogMode.New, "img" to CatalogMode.Many))
        assertEquals(mapOf("may" to CatalogMode.New, "img" to CatalogMode.Many), decodeFutaberSortByBoard(stored))
        val preferences = mapOf(
            FutaberPreferenceKeys.CATALOG_SORT to CatalogMode.Old.name,
            FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD to stored
        )
        assertEquals(CatalogMode.New, futaberCatalogSortFor(preferences, "may", perBoard = true))
        // A board without a record, the switch off and no board: the one shared value, as before.
        assertEquals(CatalogMode.Old, futaberCatalogSortFor(preferences, "dat", perBoard = true))
        assertEquals(CatalogMode.Old, futaberCatalogSortFor(preferences, "may", perBoard = false))
        assertEquals(CatalogMode.Old, futaberCatalogSortFor(preferences, null, perBoard = true))
        assertEquals(futaberDefaultCatalogMode, futaberCatalogSortFor(emptyMap(), "may", perBoard = true))
        assertEquals("compat.futaber.catalog.sortByBoard", FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD)
    }

    @Test fun aDamagedOrUnknownSortRecordIsIgnored() {
        assertTrue(decodeFutaberSortByBoard(null).isEmpty())
        assertTrue(decodeFutaberSortByBoard("").isEmpty())
        assertTrue(decodeFutaberSortByBoard("{broken").isEmpty())
        assertTrue(decodeFutaberSortByBoard("[\"New\"]").isEmpty())
        assertEquals(
            mapOf("may" to CatalogMode.New),
            decodeFutaberSortByBoard("""{"may":"New","img":"Sideways","dat":3,"":"Old","jun":null}""")
        )
        val preferences = mapOf(
            FutaberPreferenceKeys.CATALOG_SORT to CatalogMode.Few.name,
            FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD to "not json"
        )
        assertEquals(CatalogMode.Few, futaberCatalogSortFor(preferences, "may", perBoard = true))
    }

    @Test fun writingASortKeepsTheOtherBoardsAndDropsTheDeletedOnes() {
        val stored = encodeFutaberSortByBoard(mapOf("may" to CatalogMode.New, "gone" to CatalogMode.Many))
        val next = decodeFutaberSortByBoard(futaberSortByBoardAfter(stored, "img", CatalogMode.Momentum, listOf("may", "img")))
        assertEquals(mapOf("may" to CatalogMode.New, "img" to CatalogMode.Momentum), next)
        assertEquals(
            mapOf("may" to CatalogMode.Few),
            decodeFutaberSortByBoard(futaberSortByBoardAfter(stored, "may", CatalogMode.Few, listOf("may")))
        )
        assertEquals(
            mapOf("may" to CatalogMode.Old),
            decodeFutaberSortByBoard(futaberSortByBoardAfter("{broken", "may", CatalogMode.Old, listOf("may")))
        )
    }

    // ---- C15 ----

    private fun item(full: String?, thumb: String?) = CatalogItem(
        id = "123", threadUrl = "https://may.2chan.net/b/res/123.htm", title = "t", thumbnailUrl = thumb, fullImageUrl = full, replyCount = 1
    )

    @Test fun theCatalogImageNgEntryTakesTheFullImageAndIsOnlyThereWithTheExtension() {
        assertEquals("https://f/1.jpg", futaberCatalogImageUrl(item("https://f/1.jpg", "https://t/1s.jpg")))
        assertEquals("https://t/1s.jpg", futaberCatalogImageUrl(item(" ", "https://t/1s.jpg")))
        assertNull(futaberCatalogImageUrl(item(null, "")))
        var registered: FutaberCatalogImageNgTarget? = null
        assertNull(futaberCatalogImageNgAction(false, item("https://f/1.jpg", null), "key", true) { registered = it })
        val action = assertNotNull(futaberCatalogImageNgAction(true, item("https://f/1.jpg", null), "key", true) { registered = it })
        assertTrue(action.enabled)
        assertEquals("image-ng", action.id)
        action.onClick()
        assertEquals(FutaberCatalogImageNgTarget("key", "https://f/1.jpg", "No.123", threadId = "123"), registered)
        // Disabled for a board the shared store cannot hold, a thread without a picture and without the services.
        assertFalse(futaberCatalogImageNgAction(true, item("https://f/1.jpg", null), null, true) {}!!.enabled)
        assertFalse(futaberCatalogImageNgAction(true, item(null, null), "key", true) {}!!.enabled)
        assertFalse(futaberCatalogImageNgAction(true, item("https://f/1.jpg", null), "key", false) {}!!.enabled)
        // The tutorial board has no key of the shared store.
        assertNull(futaberNgScopeKey(BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")))
    }

    // ---- N06 ----

    @Test fun theNgExtractListsOnlyTheHiddenPostsEvenWithNgSwitchedOff() {
        val posts = listOf(post("1"), post("2"), post("3"), post("4"))
        val hidden = setOf("2", "4")
        val replies = futaberReplyIndex(posts)
        val filter = FutaberViewFilter(extract = FutaberExtract.NgHidden)
        assertEquals(listOf("2", "4"), futaberVisibleRows(posts, filter, replies, hidden = hidden, ngHidden = hidden).map { it.post.id })
        // NG switched off: nothing is dropped as hidden, the extract still lists what NG hides.
        assertEquals(listOf("2", "4"), futaberVisibleRows(posts, filter, replies, hidden = emptySet(), ngHidden = hidden).map { it.post.id })
        // The rows keep their numbers in the whole thread.
        assertEquals(listOf(1, 3), futaberVisibleRows(posts, filter, replies, hidden = hidden, ngHidden = hidden).map { it.ordinal })
        // A search still narrows it.
        assertEquals(
            listOf("4"),
            futaberVisibleRows(posts, filter.copy(query = "本文4"), replies, hidden = hidden, ngHidden = hidden).map { it.post.id }
        )
        // Without the extract, NG hides as before.
        assertEquals(listOf("1", "3"), futaberVisibleRows(posts, FutaberViewFilter(), replies, hidden = hidden, ngHidden = hidden).map { it.post.id })
        assertEquals("NGで隠したレス", FutaberExtract.NgHidden.label)
        assertTrue(FutaberViewFilter(extract = FutaberExtract.NgHidden).isActive)
        assertFalse(futaberMatchesExtract(post("2"), FutaberExtract.NgHidden, emptySet()))
    }

    // ---- A02 ----

    @Test fun onlyAnOwnPostWithAPictureOffersToDeleteThePictureAndItSaysSo() {
        val withImage = post("5", image = "https://may.2chan.net/b/src/5.jpg")
        assertTrue(futaberOffersImageOnlyDelete(true, FutaberRemoteAction.DeleteOwn, withImage))
        assertFalse(futaberOffersImageOnlyDelete(false, FutaberRemoteAction.DeleteOwn, withImage))
        assertFalse(futaberOffersImageOnlyDelete(true, FutaberRemoteAction.Report, withImage))
        assertFalse(futaberOffersImageOnlyDelete(true, FutaberRemoteAction.DeleteOwn, post("6")))
        assertEquals("画像を削除しました", futaberRemoteSuccessMessage(FutaberRemoteAction.DeleteOwn, imageOnly = true))
        assertEquals("レスを削除しました", futaberRemoteSuccessMessage(FutaberRemoteAction.DeleteOwn, imageOnly = false))
        assertEquals("画像を削除できませんでした", futaberRemoteFailurePrefix(FutaberRemoteAction.DeleteOwn, imageOnly = true))
        assertEquals("削除依頼を送信しました", futaberRemoteSuccessMessage(FutaberRemoteAction.Report, imageOnly = true))
        assertEquals(FutaberRemoteAction.Saidane.failurePrefix, futaberRemoteFailurePrefix(FutaberRemoteAction.Saidane, imageOnly = true))
    }

    // ---- M02 ----

    @Test fun theGalleryTakesTheSharedColumnsOnlyWithTheExtension() {
        assertEquals(3, futaberGalleryColumnCount(null, landscape = false))
        assertEquals(3, futaberGalleryColumnCount(null, landscape = true))
        val defaults = futaberSharedGalleryColumns(emptyMap())
        assertEquals(5 to 7, defaults)
        assertEquals(5, futaberGalleryColumnCount(defaults, landscape = false))
        assertEquals(7, futaberGalleryColumnCount(defaults, landscape = true))
        val stored = futaberSharedGalleryColumns(
            mapOf(
                "compat.viewer.galleryGridViewPortraitClmNum" to "4",
                "compat.viewer.galleryGridViewLandscapeClmNum" to "99"
            )
        )
        assertEquals(4 to 16, stored)
        assertEquals(2 to 7, futaberSharedGalleryColumns(mapOf("compat.viewer.galleryGridViewPortraitClmNum" to "1")))
        assertEquals(5 to 7, futaberSharedGalleryColumns(mapOf("compat.viewer.galleryGridViewPortraitClmNum" to "x")))
    }

    // ---- M05 ----

    @Test fun theImageMenuGetsItsEntriesOnlyWithTheExtension() {
        assertTrue(futaberImageMenuExtraActions(false, "u", null, true, {}, {}, {}, {}).isEmpty())
        val copied = mutableListOf<String>()
        var viewer = 0
        var dismissed = 0
        val actions = futaberImageMenuExtraActions(
            true, "https://may.2chan.net/b/src/1.jpg", null, true,
            onCopyUrl = { copied += it }, onOpenExternal = { copied += "ext:$it" }, onOpenViewer = { viewer++ }, onDismiss = { dismissed++ }
        )
        assertEquals(listOf("image-save", "image-share", "image-copy-url", "image-external", "image-viewer"), actions.map { it.id })
        assertEquals(listOf("保存", "共有", "URLをコピー", "外部で開く", "ビューアで表示"), actions.map { it.label })
        // Without the saver (no shared services) saving and sharing are disabled.
        assertFalse(actions[0].enabled); assertFalse(actions[1].enabled)
        actions[2].onClick(); actions[3].onClick(); actions[4].onClick()
        assertEquals(listOf("https://may.2chan.net/b/src/1.jpg", "ext:https://may.2chan.net/b/src/1.jpg"), copied)
        assertEquals(1, viewer)
        assertEquals(3, dismissed)
        val saver = FutaberMediaSaver()
        assertTrue(futaberImageMenuExtraActions(true, "u", saver, true, {}, {}, {}, {}).take(2).all { it.enabled })
        // No picture address: nothing can run; no viewer: the viewer entry is off.
        assertTrue(futaberImageMenuExtraActions(true, null, saver, true, {}, {}, {}, {}).none { it.enabled })
        assertFalse(futaberImageMenuExtraActions(true, "u", saver, false, {}, {}, {}, {}).last().enabled)
        assertEquals("保存中…", futaberMediaSaveStartedNotice(false))
        assertEquals("共有の準備中…", futaberMediaSaveStartedNotice(true))
    }

    /** 監査L21：板ごとのソートは共有の板キーで保存し、旧い板IDの記録も読む。 */
    @Test
    fun theSortPerBoardIsKeptUnderTheSharedKey() {
        val old = encodeFutaberSortByBoard(mapOf("may" to CatalogMode.New))
        // Old record (board id) is still read.
        assertEquals(CatalogMode.New, futaberCatalogSortFor(mapOf(FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD to old), "may", perBoard = true, storedBoardId = "compat_board_x"))
        val next = futaberSortByBoardAfter(old, "may", CatalogMode.Old, listOf("may", "compat_board_x"), storedBoardId = "compat_board_x")
        assertEquals(mapOf("compat_board_x" to CatalogMode.Old), decodeFutaberSortByBoard(next))
        // After a restore the same board has another id but the same shared key: it keeps its sort.
        assertEquals(CatalogMode.Old, futaberCatalogSortFor(mapOf(FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD to next), "board-7", perBoard = true, storedBoardId = "compat_board_x"))
    }

    /** Re-audit M19: what a save tells after its viewer has closed comes as the app's notice instead of being dropped. */
    @Test
    fun aSaveResultAfterTheViewerClosedGoesToTheAppNotice() {
        val saver = FutaberMediaSaver()
        val onScreen = mutableListOf<String>()
        val onApp = mutableListOf<String>()
        saver.screenOnMessage = { onScreen += it }
        saver.afterScreenGone = { onApp += it }
        saver.onMessage("保存中…")
        saver.screenGone = true
        saver.onMessage("保存しました")
        assertEquals(listOf("保存中…"), onScreen)
        assertEquals(listOf("保存しました"), onApp)
    }
}
