package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.*
import kotlin.test.*

class CompatMediaReturnStateTest {
    private val gallery = CompatHost.Gallery("tab", index = 12, postNo = "13")
    private val viewer = CompatHost.Viewer("tab", 2, CompatViewerCaller.GALLERY, postNo = "3")

    @Test fun nestedSettingsHelpAndLicensesKeepTheOriginalGalleryVisit() {
        val route = CompatHost.License(CompatHost.Help(CompatHost.Settings("viewer", gallery)))
        assertSame(gallery, compatMediaReturnOrigin(route))
        assertSame(gallery, compatMediaReturnOrigin(gallery))
    }
    @Test fun toolbarEditingAndHelpKeepTheOriginalViewerVisit() {
        assertSame(viewer, compatMediaReturnOrigin(CompatHost.ToolbarEditor(CompatToolbarSurface.VIEWER, viewer)))
        assertSame(viewer, compatMediaReturnOrigin(CompatHost.Help(viewer)))
        assertSame(viewer, compatMediaReturnOrigin(CompatHost.Settings(origin = viewer)))
    }
    @Test fun leavingTheMediaVisitHasNoReusableReturnOrigin() {
        assertNull(compatMediaReturnOrigin(CompatHost.Main))
        assertNull(compatMediaReturnOrigin(CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG)))
        assertNull(compatMediaReturnOrigin(CompatHost.Settings(origin = CompatHost.Main)))
        val fresh = viewer.copy(index = 9, postNo = "10")
        assertEquals(fresh, compatMediaReturnOrigin(fresh))
        assertNotEquals(compatMediaReturnOrigin(viewer), compatMediaReturnOrigin(fresh))
    }
    @Test fun currentMediaIdentitySurvivesItsIndexMovingWhileSettingsAreOpen() {
        val position = CompatViewerReturnPosition().apply { update(1, "b") }
        val posts = listOf("new", "a", "b").mapIndexed { index, id ->
            CompatPostSnapshot(index, id, timestamp = "", messageHtml = "", imageUrl = "https://example.com/$id.jpg")
        }
        assertEquals(2, compatViewerInitialPage(posts, position.mediaIdentity, position.index ?: 0))
        assertEquals(1, position.index)
    }
    @Test fun aFreshVisitHasNoCurrentPositionAndReturningToTheFirstPageIsRecorded() {
        val position = CompatViewerReturnPosition()
        assertNull(position.index); assertNull(position.mediaIdentity)
        position.update(4, "five"); position.update(0, "one")
        assertEquals(0, position.index); assertEquals("one", position.mediaIdentity)
        assertNull(CompatViewerReturnPosition().index)
    }

    @Test fun urlMediaIdentityDistinguishesSeveralImagesFromTheSamePostAfterAnInsertion() {
        val first = "https://example.com/first.jpg"
        val selected = "https://example.com/second.jpg?variant=original"
        val position = CompatViewerReturnPosition().apply { update(1, selected) }
        val reordered = listOf("https://example.com/inserted.jpg", first, selected).mapIndexed { index, url ->
            CompatPostSnapshot(index, "same-post", timestamp = "", messageHtml = "", imageUrl = url, mediaKey = url)
        }
        assertEquals(selected, compatMediaIdentity(reordered[2]))
        assertEquals(2, compatViewerInitialPage(reordered, position.mediaIdentity, position.index ?: 0))
    }

    // reference app 18-20: the thread toolbar opens the gallery near the post being read.
    @Test fun theThreadToolbarOpensTheGalleryAtTheFirstMediaOfTheReadPostOrALaterOne() {
        val posts = listOf("100", "105", "105", "120").mapIndexed { index, no ->
            CompatPostSnapshot(index, no, timestamp = "", messageHtml = "", imageUrl = "https://example.com/$index.jpg",
                mediaKey = if (index == 2) "inline" else null)
        }
        assertEquals(0, compatGalleryIndexAtOrAfterPost(posts, "90"))
        assertEquals(1, compatGalleryIndexAtOrAfterPost(posts, "101"))
        assertEquals(1, compatGalleryIndexAtOrAfterPost(posts, "105"))
        assertEquals(3, compatGalleryIndexAtOrAfterPost(posts, "130"))
        assertNull(compatGalleryIndexAtOrAfterPost(posts, null))
        assertNull(compatGalleryIndexAtOrAfterPost(emptyList(), "100"))
    }

    // reference app 18-20: Back from the viewer returns to the image it last showed.
    @Test fun backFromAGalleryViewerReturnsAtTheLastShownImageCentredWhenItMoved() {
        val moved = compatViewerGalleryReturn(viewer, currentIndex = 5, currentIdentity = "6")
        assertEquals(CompatHost.Gallery("tab", index = 5, postNo = "6", centerOnOpen = true), moved)
        val same = compatViewerGalleryReturn(viewer, currentIndex = 2, currentIdentity = "3")
        assertEquals(false, same?.centerOnOpen)
        assertNull(compatViewerGalleryReturn(viewer.copy(caller = CompatViewerCaller.THREAD), 5, "6"))
        assertNull(compatViewerGalleryReturn(viewer.copy(directMediaUrl = "https://example.com/a.jpg"), 5, "6"))
        assertNull(compatViewerGalleryReturn(viewer, null, null))
    }
}
