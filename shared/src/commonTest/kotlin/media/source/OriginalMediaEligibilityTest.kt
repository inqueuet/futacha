package com.valoser.futacha.shared.media.source

import kotlin.test.*

class OriginalMediaEligibilityTest {
    @Test fun onlyPublicBoardOriginalsJoinTheSharedMediaStore() {
        for (suffix in listOf("mp4", "WEBM", "mov", "m4v")) {
            assertTrue(isSharedOriginalVideoUrl("https://may.2chan.net/b/src/1.$suffix?token=a%2Bb#compat-reload=7"))
            assertFalse(isSharedOriginalImageUrl("https://may.2chan.net/b/src/1.$suffix"))
        }
        for (url in listOf("https://may.2chan.net/b/thumb/1.mp4", "https://2chan.net.example/b/src/1.mp4",
            "file:///src/1.mp4", "content://photos/src/1.mp4", "https://may.2chan.net/b/src/1.php?file=.mp4")) {
            assertFalse(isSharedOriginalMediaUrl(url), url)
        }
        assertTrue(isSharedOriginalImageUrl("https://may.2chan.net/b/src/1.png"))
        assertFalse(isSharedOriginalVideoUrl("https://may.2chan.net/b/src/1.png"))
    }
    @Test fun aimogeAttachmentOriginalsJoinButThumbnailsAndOtherHostsDoNot() {
        assertTrue(isSharedOriginalImageUrl("https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp"))
        assertTrue(isSharedOriginalVideoUrl("https://storage.nijiurachan.net/aimg/attachments/2026/11/a.mp4"))
        for (url in listOf("https://storage.nijiurachan.net/aimg/thumbnails/2026/11/a.webp",
            "http://storage.nijiurachan.net/aimg/attachments/a.png", "https://storage.nijiurachan.net.evil.com/attachments/a.png",
            "https://nijiurachan.net/aimg/attachments/a.png", "https://storage.nijiurachan.net:8443/attachments/a.png")) {
            assertFalse(isSharedOriginalMediaUrl(url), url)
        }
    }
    @Test fun playbackReloadChangesRevisionWithoutChangingSignedQuery() {
        val url = "https://may.2chan.net/b/src/1.mp4?token=a%2Bb&x=1&x=2"
        val request = originalVideoRequest("$url#compat-reload=123")
        assertEquals(url, request.url)
        assertEquals(123L, request.reloadToken)
        assertEquals(originalVideoRequest(url).cacheKey(), request.cacheKey())
        assertEquals(0L, originalVideoRequest("$url#other-fragment").reloadToken)
        assertEquals(0L, originalVideoRequest("$url#compat-reload=oops").reloadToken)
    }
}
