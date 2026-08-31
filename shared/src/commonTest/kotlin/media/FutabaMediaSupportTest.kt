package com.valoser.futacha.shared.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutabaMediaSupportTest {
    @Test
    fun fileExtensionIgnoresCaseQueryFragmentAndDirectoryDots() {
        assertEquals("webm", mediaFileExtension(" https://img.2chan.net/a.b/src/1.WEBM?x=.jpg#png "))
        assertEquals("", mediaFileExtension("https://img.2chan.net/a.b/src/no-extension"))
        assertEquals("", mediaFileExtension(null))
    }

    @Test
    fun everySharedImageAndVideoExtensionHasOneStableClassification() {
        FUTABA_COMPAT_IMAGE_EXTENSIONS.forEach { extension ->
            assertEquals(FutabaMediaKind.IMAGE, classifyFutabaMedia("https://example.test/1.$extension"))
            assertTrue(isFutabaImageExtension(extension.uppercase()))
            assertFalse(isFutabaVideoExtension(extension))
        }
        FUTABA_COMPAT_VIDEO_EXTENSIONS.forEach { extension ->
            assertEquals(FutabaMediaKind.VIDEO, classifyFutabaMedia("https://example.test/1.$extension"))
            assertTrue(isFutabaVideoExtension(extension.uppercase()))
            assertFalse(isFutabaImageExtension(extension))
        }
    }

    @Test
    fun extensionWinsOverMisleadingMimeAndMimeHandlesExtensionlessUrls() {
        assertEquals(
            FutabaMediaKind.VIDEO,
            classifyFutabaMedia("https://example.test/1.webm", "image/jpeg")
        )
        assertEquals(
            FutabaMediaKind.IMAGE,
            classifyFutabaMedia("https://example.test/download", " IMAGE/AVIF ; charset=binary")
        )
        assertEquals(FutabaMediaKind.UNSUPPORTED, classifyFutabaMedia("https://example.test/1.txt"))
    }

    @Test
    fun parserPatternContainsEverySupportedExtensionExactlyAsAnAlternative() {
        val pattern = Regex("^(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN)$", RegexOption.IGNORE_CASE)
        FUTABA_COMPAT_MEDIA_EXTENSIONS.forEach { extension ->
            assertTrue(pattern.matches(extension), "$extension is missing from the parser pattern")
        }
        assertFalse(pattern.matches("txt"))
        assertFalse(pattern.matches("jpg.exe"))
    }
}
