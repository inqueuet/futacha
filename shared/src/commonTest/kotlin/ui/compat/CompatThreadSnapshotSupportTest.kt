package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import kotlin.test.*

class CompatThreadSnapshotSupportTest {
    private fun post(position: Int, postNo: String, image: String? = null, thumb: String? = null) = CompatPostSnapshot(
        position = position, postNo = postNo, author = "としあき", subject = "無念", mail = "sage",
        timestamp = "26/09/23(水)12:00:00", posterId = "ID:abc", messageHtml = "本文$postNo",
        imageUrl = image, thumbnailUrl = thumb, saidaneLabel = "そうだね×1", isDeleted = position == 1,
        isIsolated = position == 2, referencedCount = position
    )

    private fun snapshot(vararg posts: CompatPostSnapshot) =
        CompatThreadSnapshot(tabKey = "tab", revision = 1L, fetchedAtEpochMillis = 0L, posts = posts.toList())

    @Test fun postsForSaveCopyFieldsAndKeepOnlyRequestedMedia() {
        val source = snapshot(post(0, "100", "https://img/src/1.png", "https://img/thumb/1s.jpg"), post(1, "101"), post(2, "102"))
        val all = compatPostsForSave(source, includeFullImages = true, includeThumbnails = true)
        assertEquals(listOf("100", "101", "102"), all.map { it.id })
        with(all[0]) {
            assertEquals(0, order); assertEquals("としあき", author); assertEquals("無念", subject)
            assertEquals("26/09/23(水)12:00:00", timestamp); assertEquals("ID:abc", posterId); assertEquals("本文100", messageHtml)
            assertEquals("https://img/src/1.png", imageUrl); assertEquals("https://img/thumb/1s.jpg", thumbnailUrl)
            assertEquals("そうだね×1", saidaneLabel); assertEquals("sage", mail)
        }
        assertTrue(all[1].isDeleted); assertTrue(all[2].isIsolated); assertEquals(2, all[2].referencedCount)

        val thumbsOnly = compatPostsForSave(source, includeFullImages = false, includeThumbnails = true)[0]
        assertNull(thumbsOnly.imageUrl); assertEquals("https://img/thumb/1s.jpg", thumbsOnly.thumbnailUrl)
        val textOnly = compatPostsForSave(source, includeFullImages = false, includeThumbnails = false)[0]
        assertNull(textOnly.imageUrl); assertNull(textOnly.thumbnailUrl)
        assertTrue(compatPostsForSave(null, includeFullImages = true, includeThumbnails = true).isEmpty())
    }

    @Test fun snapshotThumbnailUsesOpThumbnailThenImage() {
        assertEquals("t0", compatSnapshotThumbnail(snapshot(post(0, "100", "i0", "t0"), post(1, "101", "i1", "t1")), "100"))
        assertEquals("i0", compatSnapshotThumbnail(snapshot(post(0, "100", "i0")), "100"))
        // A snapshot that starts mid-thread still finds the OP by its number.
        assertEquals("t9", compatSnapshotThumbnail(snapshot(post(3, "103", "i3", "t3"), post(5, "100", "i9", "t9")), "100"))
        assertNull(compatSnapshotThumbnail(snapshot(post(1, "101", "i1", "t1")), "100"))
        assertNull(compatSnapshotThumbnail(snapshot(post(0, "100")), "100"))
        assertNull(compatSnapshotThumbnail(null, "100"))
    }
}
