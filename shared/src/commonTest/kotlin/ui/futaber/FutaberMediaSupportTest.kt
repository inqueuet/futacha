package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.compat.compatViewerMediaPosts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberMediaSupportTest {
    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= 4.5f, "$label: contrast $ratio < 4.5")
    }

    private fun post(id: String, image: String?) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "",
        imageUrl = image, thumbnailUrl = image
    )

    @Test
    fun sharedScreenTextRolesStayReadableInBothThemes() {
        listOf("light" to FutaberTokens.light, "dark" to FutaberTokens.dark).forEach { (name, c) ->
            val p = futaberMediaPalette(c)
            // Menus, dialogs and settings rows of the shared screens.
            listOf(p.menuSurface, p.dialogSurface).forEach { surface ->
                assertReadable("$name primary text on surface", p.uiPrimaryText, surface)
                assertReadable("$name secondary text on surface", p.uiSecondaryText, surface)
            }
            assertReadable("$name text on page", p.text, p.background)
            assertReadable("$name link on page", p.bodyLink, p.background)
            assertReadable("$name quote on page", p.bodyQuote, p.background)
            assertReadable("$name subject on page", p.headerSubject, p.background)
            assertReadable("$name chrome content on chrome", p.chromeContent, p.chrome)
            assertReadable("$name accent on page", p.accent, p.background)
            assertReadable("$name text on search hit", p.uiPrimaryText, p.searchResultBackground)
        }
    }

    @Test
    fun paletteFollowsTheFutaberTheme() {
        val light = futaberMediaPalette(FutaberTokens.light)
        val dark = futaberMediaPalette(FutaberTokens.dark)
        assertEquals(FutaberTokens.light.background, light.background)
        assertEquals(FutaberTokens.dark.background, dark.background)
        assertEquals(FutaberTokens.dark.bar, dark.chrome)
        assertEquals(FutaberTokens.dark.bar, dark.statusBarChrome)
    }

    @Test
    fun theViewerTabIsBuiltFromTheThreadAndNeverClaimsToBeStored() {
        val board = BoardSummary(id = "may-b", name = "may/b", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
        val ref = FutaberThreadRef(board.id, "1234", "題名", "https://example.com/t.jpg", 7)
        val tab = futaberMediaTab(board, ref, snapshotRevision = 99L)
        assertEquals("1234", tab.threadNo)
        assertEquals("題名", tab.title)
        assertEquals("may/b", tab.boardName)
        assertEquals(futaberHistoryThreadUrl(board, "1234"), tab.canonicalUrl)
        assertEquals(tab.canonicalUrl, tab.originalUrl)
        assertEquals(99L, tab.snapshotRevision)
        assertEquals(0L, tab.insertedAtEpochMillis)
        assertEquals(0, tab.unreadCount)
        assertEquals(futaberMediaTab(board, ref, 1L).key, tab.key)
        assertNull(futaberMediaTab(board, ref.copy(thumbnailUrl = ""), 0L).thumbnailUrl)
    }

    @Test
    fun onlyPostsWithAFileCountForTheGalleryEntry() {
        assertEquals(0, futaberMediaPostCount(emptyList()))
        assertEquals(0, futaberMediaPostCount(listOf(post("1", null), post("2", ""))))
        assertEquals(2, futaberMediaPostCount(listOf(post("1", "a.jpg"), post("2", null), post("3", "b.png"))))
    }

    @Test
    fun postsHiddenByNgLeaveTheGalleryAndTheViewer() {
        val posts = listOf(
            post("1", "https://may.2chan.net/b/src/1.jpg").copy(messageHtml = "普通の話"),
            post("2", "https://may.2chan.net/b/src/2.jpg").copy(messageHtml = "禁止ワードを含む"),
            post("3", "https://may.2chan.net/b/src/3.jpg").copy(messageHtml = "また普通")
        )
        val hidden = futaberNgHiddenIds(posts, listOf("禁止ワード"), emptyList(), emptyList(), "tab", "board")
        val snapshot = ThreadPage("1", null, null, null, posts).toCompatThreadSnapshot("tab", 0L).posts
        // The numbers NG hides in the thread are the ones the gallery is told to drop (the same ids on both sides).
        assertEquals(listOf("1", "3"), compatViewerMediaPosts(snapshot, hiddenPostNos = hidden).map { it.postNo })
        assertEquals(listOf("1", "2", "3"), compatViewerMediaPosts(snapshot).map { it.postNo })
    }
}
