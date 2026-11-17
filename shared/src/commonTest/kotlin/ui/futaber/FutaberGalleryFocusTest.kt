package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The picture list shows the picture the viewer showed when its "画像一覧" was pressed (FutaberMediaOverlay). */
class FutaberGalleryFocusTest {
    private val pictures = listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg")

    @Test fun thePictureIsFoundByWhatItIsNotByWhereItWas() {
        assertEquals(2, futaberGalleryFocusIndex(pictures, "c.jpg", 0))
        // A refresh behind the viewer added a picture before it: the list goes to the same picture, now one further.
        assertEquals(3, futaberGalleryFocusIndex(listOf("new.jpg") + pictures, "c.jpg", 2))
    }

    @Test fun withoutTheSamePictureTheViewersPlaceIsUsedWhileItIsInTheList() {
        assertEquals(1, futaberGalleryFocusIndex(pictures, "gone.jpg", 1))
        assertEquals(1, futaberGalleryFocusIndex(pictures, null, 1))
        assertNull(futaberGalleryFocusIndex(pictures, "gone.jpg", 9))
        assertNull(futaberGalleryFocusIndex(emptyList(), "a.jpg", 0))
        assertNull(futaberGalleryFocusIndex(pictures, null, null))
    }
}
