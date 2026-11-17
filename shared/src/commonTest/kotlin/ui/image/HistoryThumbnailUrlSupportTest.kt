package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryThumbnailUrlSupportTest {
    @Test
    fun freshThumbnailReplacesCatalogAndDeadRemoteUrls() {
        assertEquals("https://archive.example/new.jpg", updatedHistoryThumbnailUrl("https://may.2chan.net/b/cat/old.jpg", "https://archive.example/new.jpg"))
    }

    @Test
    fun missingThumbnailDoesNotErasePreviousUrl() {
        assertEquals("https://example.com/old.jpg", updatedHistoryThumbnailUrl("https://example.com/old.jpg", ""))
        assertNull(updatedHistoryThumbnailUrl(null, ""))
    }

    @Test
    fun offlineGenerationPathDoesNotReplaceRecoverableRemoteUrl() {
        assertEquals("https://example.com/old.jpg", updatedHistoryThumbnailUrl("https://example.com/old.jpg", "/autosave/generation/thumb.jpg"))
        assertEquals("https://example.com/new.jpg", updatedHistoryThumbnailUrl("/autosave/generation/thumb.jpg", "https://example.com/new.jpg"))
    }
}
