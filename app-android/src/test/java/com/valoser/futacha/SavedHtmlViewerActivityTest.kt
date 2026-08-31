package com.valoser.futacha

import org.junit.Assert.assertFalse
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
}
