package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.compat.CompatViewerReturnPosition
import kotlin.test.*

/** URL admission uses the product gallery/viewer resolver, including old synthetic identities. */
class FutaberViewerReturnPositionTest {
    private fun media(url: String, key: String) = CompatPostSnapshot(0, "same-post", timestamp = "", messageHtml = "",
        imageUrl = url, mediaKey = key)

    @Test fun theOriginalUrlFindsTheSameInlineImageAfterItsSyntheticIndexChanges() {
        val selected = "https://dec.2chan.net/up2/src/fu7099124.jpg"
        val position = CompatViewerReturnPosition().apply { update(1, selected) }
        val reordered = listOf(
            media("https://may.2chan.net/b/src/primary.jpg", "same-post"),
            media("https://dec.2chan.net/up2/src/fu7099122.jpg", "same-post::apu::0"),
            media("https://dec.2chan.net/up2/src/fu7099123.jpg", "same-post::apu::1"),
            media(selected, "same-post::apu::2")
        )
        assertEquals(3, futaberViewerIndexFor(reordered, position.mediaIdentity, position.index!!))
    }

    @Test fun queryVariantsKeepTheirFullMediaUrlInsteadOfFallingBackToThePostNumber() {
        val first = "https://example.com/image.jpg?variant=one"
        val selected = "https://example.com/image.jpg?variant=two"
        val posts = listOf(media(first, "same-post"), media(selected, "same-post::apu::0"))
        assertEquals(1, futaberViewerIndexFor(posts, selected, 0))
        assertEquals(0, futaberViewerIndexFor(posts, first, 1))
    }

    /** L147: a picture posted twice returns to the copy that was open, not the first one. */
    @Test fun aRepeatedPictureReturnsToTheCopyThatWasOpen() {
        val repeated = "https://dec.2chan.net/up2/src/fu7099124.jpg"
        val posts = listOf(media(repeated, "a"), media("https://dec.2chan.net/up2/src/fu7099125.jpg", "b"), media(repeated, "c"))
        assertEquals(2, futaberViewerIndexFor(posts, repeated, 2))
        assertEquals(0, futaberViewerIndexFor(posts, repeated, 0))
        assertEquals(0, futaberViewerIndexFor(posts, repeated, 1), "A page now showing another picture still finds it by URL")
    }

    @Test fun galleryIdentitiesMissingMediaAndEmptyListsKeepTheExistingFallbackContract() {
        val posts = listOf(media("https://example.com/a.jpg", "same-post"), media("https://example.com/b.jpg", "same-post::apu::0"))
        assertEquals(1, futaberViewerIndexFor(posts, "same-post::apu::0", 0))
        assertEquals(0, futaberViewerIndexFor(posts, "missing", -3))
        assertEquals(1, futaberViewerIndexFor(posts, null, 8))
        assertEquals(0, futaberViewerIndexFor(emptyList(), "missing", 8))
    }
}
