package com.valoser.futacha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedHtmlViewerActivityTest {
    @Test
    fun acceptsHtmlMimeOrExtensionAndRejectsUnrelatedDocuments() {
        assertTrue(isSupportedSavedHtmlDocument("text/html", "/document/123"))
        assertTrue(isSupportedSavedHtmlDocument(null, "/saved/123.htm"))
        assertTrue(isSupportedSavedHtmlDocument("application/octet-stream", "/saved/123.HTML"))
        assertFalse(isSupportedSavedHtmlDocument("text/plain", "/saved/readme.txt"))
    }

    @Test
    fun sanitizesFtbucketPreviewControlBeforeWebViewRendering() {
        val source =
            "<a href=\"other/fu7199371.png\">fu7199371.png</a>" +
                "<span onclick=\"previewImg('id','other/fu7199371.png')\">[見る]</span><br>本文"

        assertEquals(
            "<a href=\"other/fu7199371.png\">fu7199371.png</a><br>本文",
            sanitizeSavedHtmlDocument(source)
        )
    }

    @Test
    fun rejectsFilesInsidePrivateAppStorageOnly() {
        val roots = listOf("/data/user/0/com.valoser.futacha", "/data/data/com.valoser.futacha/")
        assertTrue(isInsidePrivateAppStorage("/data/user/0/com.valoser.futacha/files/x.htm", roots))
        assertTrue(isInsidePrivateAppStorage("/data/data/com.valoser.futacha/shared_prefs/a.html", roots))
        assertTrue(isInsidePrivateAppStorage("/data/data/com.valoser.futacha", roots))
        assertFalse(isInsidePrivateAppStorage("/data/data/com.valoser.futacha.other/a.htm", roots))
        assertFalse(
            isInsidePrivateAppStorage(
                "/storage/emulated/0/Android/data/com.valoser.futacha/files/saved/a.htm",
                roots
            )
        )
    }

    // S4-4: only a user's tap on a web link leaves the app; a meta refresh
    // (no gesture) or a redirect does not open the browser on its own.
    @Test
    fun opensWebLinksExternallyOnlyForUserTaps() {
        assertEquals(
            SavedHtmlNavigation.OPEN_EXTERNALLY,
            savedHtmlNavigation("https", isMainFrame = true, hasGesture = true, isRedirect = false, isSameDocumentProvider = false)
        )
        assertEquals(
            SavedHtmlNavigation.OPEN_EXTERNALLY,
            savedHtmlNavigation("http", isMainFrame = true, hasGesture = true, isRedirect = false, isSameDocumentProvider = false)
        )
        assertEquals(
            SavedHtmlNavigation.BLOCK,
            savedHtmlNavigation("https", isMainFrame = true, hasGesture = false, isRedirect = false, isSameDocumentProvider = false)
        )
        assertEquals(
            SavedHtmlNavigation.BLOCK,
            savedHtmlNavigation("https", isMainFrame = true, hasGesture = true, isRedirect = true, isSameDocumentProvider = false)
        )
        assertEquals(
            SavedHtmlNavigation.BLOCK,
            savedHtmlNavigation("https", isMainFrame = false, hasGesture = true, isRedirect = false, isSameDocumentProvider = false)
        )
        assertEquals(
            SavedHtmlNavigation.BLOCK,
            savedHtmlNavigation("intent", isMainFrame = true, hasGesture = true, isRedirect = false, isSameDocumentProvider = false)
        )
    }

    @Test
    fun keepsPagesOfTheSameDocumentProviderInTheViewer() {
        assertEquals(
            SavedHtmlNavigation.LOAD_IN_VIEW,
            savedHtmlNavigation("content", isMainFrame = true, hasGesture = false, isRedirect = false, isSameDocumentProvider = true)
        )
        assertEquals(
            SavedHtmlNavigation.BLOCK,
            savedHtmlNavigation("content", isMainFrame = true, hasGesture = true, isRedirect = false, isSameDocumentProvider = false)
        )
    }
}
