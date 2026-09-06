package com.valoser.futacha.shared.media

import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutabaMediaSupportTest {
    @Test
    fun linkLabelsAreHiddenInBodiesQuotesAndCachedTextWithoutChangingHtmlTargets() {
        val html = "本文<br><a href=\"https://example.test/[link]\">fu123.png</a>" +
            "<span>[link]</span><br>&gt;fu123.png&#91;link&#93;"
        val expected = "本文<br><a href=\"https://example.test/[link]\">fu123.png</a>" +
            "<span></span><br>&gt;fu123.png"
        assertEquals(expected, normalizeFutabaArchiveApuViewLabelHtml(html))
        assertEquals("本文\nfu123.png\n>fu123.png", messageHtmlToPlainText(html))
        assertEquals("本文\nfu123.png\n>fu123.png", html.toCompatPlainText())
        assertEquals(
            "本文\n>fu123.png\nhttps://example.test/",
            normalizeFutabaArchiveApuViewLabelText("本文\n>fu123.png[link]\nhttps://example.test/")
        )
        assertEquals(
            "本文 link <a href=\"https://example.test/\">通常リンク</a>",
            normalizeFutabaArchiveApuViewLabelHtml(
                "本文 link <a href=\"https://example.test/\">通常リンク</a>"
            )
        )
    }

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
