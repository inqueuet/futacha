package com.valoser.futacha.shared.network

import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlin.test.*

class ImageRequestPolicyTest {
    @Test fun staticMediaIncludesUploadersAndPreservesSignedQueries() {
        for (url in listOf("https://may.2chan.net/b/thumb/123s.jpg", "https://dec.2chan.net/up2/src/fu123.png", "https://cdn.example.com/files/my%20image.webp?sig=opaque%2Fvalue", "https://media.example.com/clip.webm")) {
            assertTrue(isAllowedImageRequest(HttpMethod.Get, Url(url)), url)
            assertTrue(isAllowedImageRequest(HttpMethod.Head, Url(url)), url)
            assertFalse(isAllowedImageRequest(HttpMethod.Post, Url(url)), url)
        }
    }
    @Test fun rejectStateChangingScriptsIncludingEncodedRedirectTargets() {
        for (path in listOf("/sd.php?b.123", "/del.php", "/sd.php/fake.jpg", "/sd%2ephp/fake.jpg", "/sd%252ephp/fake.jpg", "/script.cgi/image.jpg")) {
            assertFalse(isAllowedImageRequest(HttpMethod.Get, Url("https://may.2chan.net$path")), path)
        }
    }
}
