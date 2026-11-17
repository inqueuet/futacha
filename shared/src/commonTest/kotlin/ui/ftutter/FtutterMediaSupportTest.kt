package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtutterMediaSupportTest {
    private fun post(id: String, image: Boolean = true, deleted: Boolean = false) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "本文",
        imageUrl = if (image) "https://may.2chan.net/b/src/$id.jpg" else null,
        thumbnailUrl = if (image) "https://may.2chan.net/b/thumb/${id}s.jpg" else null,
        isDeleted = deleted
    )

    @Test
    fun theDefaultsFollowTheOriginal() {
        val s = parseFtutterMediaSettings(emptyMap())
        assertTrue(s.connectAll)
        assertTrue(s.excludeDeleted)
        assertEquals(4, s.galleryColumns)
        assertTrue(s.showStrip)
        assertEquals(80, s.stripSize)
        assertEquals(1, parseFtutterMediaSettings(mapOf(FtutterKeys.GALLERY_COLUMNS to "1")).galleryColumns)
        assertEquals(4, parseFtutterMediaSettings(mapOf(FtutterKeys.GALLERY_COLUMNS to "0")).galleryColumns)
    }

    @Test
    fun theListKeepsPositionsAndDropsDeletedPostsButNotTheOpenedOne() {
        val posts = listOf(post("1"), post("2", image = false), post("3", deleted = true), post("4"))
        val all = ftutterMediaPosts(posts, excludeDeleted = true)
        assertEquals(listOf(0, 3), all.map { it.position })
        val opened = ftutterMediaPosts(posts, excludeDeleted = true, keepPostId = "3")
        assertEquals(listOf(0, 2, 3), opened.map { it.position })
        val kept = ftutterMediaPosts(posts, excludeDeleted = false)
        assertEquals(listOf(0, 2, 3), kept.map { it.position })
        // 「スレ内の画像を全て繋げて表示する」がOFFなら、開いたレスの画像だけ。
        assertEquals(listOf(3), ftutterMediaPosts(posts, excludeDeleted = true, keepPostId = "4", onlyPostId = "4").map { it.position })
    }

    @Test
    fun theViewerClosesOnALongOrFastVerticalSwipe() {
        assertTrue(ftutterViewerShouldClose(dy = 900f, dx = 10f, heightPx = 2000f, millis = 600))
        assertFalse(ftutterViewerShouldClose(dy = 700f, dx = 10f, heightPx = 2000f, millis = 600))
        assertTrue(ftutterViewerShouldClose(dy = -400f, dx = 0f, heightPx = 2000f, millis = 120))
        assertFalse(ftutterViewerShouldClose(dy = 900f, dx = 800f, heightPx = 2000f, millis = 600))
    }

    @Test
    fun theSaveTextsAndTheDestinationNameAreShort() {
        assertEquals("3個のファイルを保存しますか？", ftutterSelectSaveQuestion(3))
        assertEquals("既定の保存先", ftutterDestinationLabel(null))
        assertEquals("pics", ftutterDestinationLabel(SaveLocation.Path("/sdcard/Download/pics/")))
    }
}
