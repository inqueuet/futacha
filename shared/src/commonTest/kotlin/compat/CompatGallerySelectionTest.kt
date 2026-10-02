package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatGallerySelectionTest {
    @Test
    fun allSelectedComparesUniqueMediaKeysSoDuplicatesCanFlipToDeselect() {
        // Two posts list the same media, as a cache/archive merge can produce.
        val posts = listOf(post(1, "a.jpg"), post(2, "b.png"), post(3, "a.jpg"))
        val keys = posts.mapTo(mutableSetOf(), ::compatMediaIdentity)
        assertEquals(2, keys.size)
        // 全選択 selects every distinct key; the label must then flip to 全解除.
        assertTrue(compatGalleryAllSelected(keys.toSet(), keys))
        assertFalse(compatGalleryAllSelected(setOf("a.jpg"), keys))
        assertTrue(compatGalleryAllSelected(emptySet(), emptySet()))
    }

    private fun post(position: Int, mediaKey: String) = CompatPostSnapshot(
        position = position,
        postNo = position.toString(),
        timestamp = "",
        messageHtml = "",
        mediaKey = mediaKey
    )
}
