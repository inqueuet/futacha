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
}
