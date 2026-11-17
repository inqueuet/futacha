package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals

/** U-5: duplicated posts must not give the attachment grid duplicate keys. */
class ThreadGalleryItemKeysTest {
    private fun post(id: String, imageUrl: String) = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "",
        messageHtml = "",
        imageUrl = imageUrl,
        thumbnailUrl = imageUrl.replace("/src/", "/thumb/").replace(".png", "s.jpg")
    )

    @Test
    fun duplicatedPostsGetUniqueKeysAndTheFirstKeepsItsKey() {
        val first = post("1", "https://example.com/src/a.png")
        val second = post("2", "https://example.com/src/b.png")
        val items = buildThreadAttachmentGalleryItems(listOf(first, second, first, first))
        assertEquals(4, items.size, "every listed attachment stays visible")

        val keys = buildThreadGalleryItemKeys(items)

        assertEquals(keys.size, keys.toSet().size, "keys must be unique: $keys")
        assertEquals(buildThreadGalleryItemKeys(items.take(2)), keys.take(2))
        assertEquals(listOf("${keys[0]}#1", "${keys[0]}#2"), keys.drop(2))
    }

    @Test
    fun suffixedKeyNeverCollidesWithAnotherItem() {
        val base = post("1", "https://example.com/src/a.png")
        val items = buildThreadAttachmentGalleryItems(listOf(base, base))
        val keys = buildThreadGalleryItemKeys(items + items[0].copy(targetUrl = items[0].targetUrl + "#1"))
        assertEquals(keys.size, keys.toSet().size, "keys must be unique: $keys")
    }
}
