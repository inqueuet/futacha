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
}
