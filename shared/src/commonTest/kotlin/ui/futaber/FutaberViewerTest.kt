package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.compat.compatViewerDoubleTapTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L-B11「ビューア・画像一覧」 (look doc §4.2 S11・G26・G27・G37・V18・V19・M48・M34・C04・C27〜C29・F15): the pure parts. */
class FutaberViewerTest {
    @Test fun theGalleryTitleIsTheCountOfPhotos() {
        assertEquals("2 photos", futaberGalleryTitle(2))
        assertEquals("9 photos", futaberGalleryTitle(9))
        assertEquals("1 photo", futaberGalleryTitle(1))
        assertEquals("0 photos", futaberGalleryTitle(0))
    }

    @Test fun theCaptionsBodyIsOneLineOfTheTextLinesJoinedBySpaces() {
        assertEquals("一行目 二行目 三行目", futaberViewerBodyLine("一行目<br>二行目<br><br>三行目"))
        assertEquals("", futaberViewerBodyLine(""))
    }

    @Test fun theStorageControlReadsItsStoredValueAndDefaultsToNone() {
        assertEquals(FutaberStorageButton.None, FutaberStorageButton.from(null))
        assertEquals(FutaberStorageButton.None, FutaberStorageButton.from("garbage"))
        assertEquals(FutaberStorageButton.Save, FutaberStorageButton.from("save"))
        assertEquals(FutaberStorageButton.Action, FutaberStorageButton.from("action"))
        assertEquals(FutaberStorageButton.LongPress, FutaberStorageButton.from("longpress"))
        assertEquals(listOf("割り当てなし", "画像保存ボタン", "アクションボタン", "長押し"), FutaberStorageButton.entries.map { it.label })
    }

    @Test fun theNewSettingsStartAsTheViewerWasAndReadTheirKeys() {
        val none = FutaberDisplaySettings.from(emptyMap())
        assertEquals(FutaberStorageButton.None, none.viewerStorageButton)
        assertFalse(none.viewerPortraitLock)
        assertFalse(none.viewerAutoplay)
        val set = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.VIEWER_STORAGE_BUTTON to "longpress",
                FutaberSettingKeys.VIEWER_PORTRAIT_LOCK to "ON",
                FutaberSettingKeys.VIEWER_AUTOPLAY to "ON"
            )
        )
        assertEquals(FutaberStorageButton.LongPress, set.viewerStorageButton)
        assertTrue(set.viewerPortraitLock)
        assertTrue(set.viewerAutoplay)
    }

    @Test fun aSharedFileKeepsTheNameInItsAddress() {
        assertEquals("1792153182260.jpg", futaberViewerFileName("https://may.2chan.net/b/src/1792153182260.jpg"))
        assertEquals("a.webm", futaberViewerFileName("https://x.example/dir/a.webm?x=1#t"))
        assertNull(futaberViewerFileName("https://x.example/dir/"))
        assertNull(futaberViewerFileName("https://x.example/noextension"))
    }

    @Test fun aLongPressIsOnlyWhereTheFingerStayed() {
        assertTrue(futaberLongPressAllowed(0f, 8f))
        assertTrue(futaberLongPressAllowed(8f, 8f))
        assertFalse(futaberLongPressAllowed(40f, 8f))
    }

    @Test fun aDoubleTapZoomsInOnTheTapAndOutAgainWhenZoomed() {
        val (scale, x, y) = compatViewerDoubleTapTransform(1f, 100f, 100f, 400f, 800f)
        assertEquals(2f, scale)
        assertTrue(x != 0f || y != 0f, "the zoom is centred on the tap, not on the middle")
        val (back, bx, by) = compatViewerDoubleTapTransform(2f, 100f, 100f, 400f, 800f)
        assertEquals(1f, back)
        assertEquals(0f, bx)
        assertEquals(0f, by)
    }

    @Test fun thePostComesBackFromTheViewerAsABubbleOverTheThread() {
        val post = Post(id = "5", order = 5, author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        val layer = futaberMenuBubble(FutaberQuoteKind.FromViewer, listOf(post))
        assertEquals(FutaberQuoteKind.FromViewer, layer.kind)
        assertEquals(listOf(post), layer.posts)
        assertNotNull(layer.edge)
    }
}
