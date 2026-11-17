package com.valoser.futacha.shared.ui.futaber.mht

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberMhtCodecTest {
    private fun file(boundary: String, vararg parts: ByteArray, subject: String = "題名", custom: List<Pair<String, String>> = emptyList()): ByteArray {
        var out = FutaberMhtWriter.header(boundary, subject, "Tue, 06 Oct 2026 11:04:00 +0000", "https://may.2chan.net/27/res/1.htm", custom)
        parts.forEach { out += it }
        return out + FutaberMhtWriter.end(boundary)
    }

    @Test
    fun aWrittenFileIsReadBackWithItsHeadersAndPartsIntact() {
        val b = FutaberMhtWriter.newBoundary(42)
        val image = ByteArray(1000) { (it * 7).toByte() }
        val bytes = file(
            b,
            FutaberMhtWriter.part(b, "text/html", "https://may.2chan.net/27/res/1.htm", "<p>こんにちは</p>".encodeToByteArray(), charset = "utf-8"),
            FutaberMhtWriter.part(b, "image/jpeg", "https://may.2chan.net/27/thumb/1s.jpg", image),
            subject = "うちの子です チョビたん：1歳",
            custom = listOf("Board" to "may", "Thread" to "324989")
        )
        val doc = FutaberMhtReader.parse(bytes)
        assertEquals("うちの子です チョビたん：1歳", doc.subject)
        assertEquals("may", doc.custom("board"))
        assertEquals("324989", doc.custom("thread"))
        assertEquals(2, doc.parts.size)
        assertEquals("<p>こんにちは</p>", doc.htmlPart?.text())
        assertEquals("https://may.2chan.net/27/res/1.htm", doc.htmlPart?.location)
        val img = doc.parts[1]
        assertTrue(img.isImage)
        assertEquals("https://may.2chan.net/27/thumb/1s.jpg", img.location)
        assertContentEquals(image, img.body)
    }

    @Test
    fun aLongSubjectAndOtherNonAsciiHeadersSurviveAsEncodedWords() {
        val subject = "あ".repeat(80) + "😀" + "い".repeat(30)
        assertEquals(subject, decodeMimeWords(encodeMimeWords(subject)))
        assertEquals("plain ascii", encodeMimeWords("plain ascii"))
        // An encoded word split in two, with the space between them, reads as one text.
        assertEquals("日本語", decodeMimeWords("=?utf-8?B?5pel5pys?= =?utf-8?B?6Kqe?="))
        assertEquals("a b", decodeMimeWords("=?utf-8?Q?a_b?="))
    }

    @Test
    fun aFileFromAnotherToolWithQuotedPrintableFoldedHeadersAndBareLineFeedsIsRead() {
        val text = "MIME-Version: 1.0\n" +
            "Subject: Thread\n" +
            "Content-Type: multipart/related;\n" +
            "\tboundary=\"BOUNDARY\"; type=\"text/html\"\n" +
            "\n" +
            "preface that readers ignore\n" +
            "--BOUNDARY\n" +
            "Content-Type: text/html; charset=\"utf-8\"\n" +
            "Content-Transfer-Encoding: quoted-printable\n" +
            "Content-Location: https://example.com/res/9.htm\n" +
            "\n" +
            "<p>caf=C3=A9 =\n" +
            "soft</p>\n" +
            "--BOUNDARY\n" +
            "Content-Type: image/gif\n" +
            "Content-Transfer-Encoding: base64\n" +
            "Content-ID: <img1@host>\n" +
            "Content-Location: https://example.com/src/1.gif\n" +
            "\n" +
            "R0lGODlhAQABAAAAACw=\n" +
            "--BOUNDARY--\n" +
            "epilogue\n"
        val doc = FutaberMhtReader.parse(text.encodeToByteArray())
        assertEquals("Thread", doc.subject)
        assertEquals("<p>café soft</p>", doc.htmlPart?.text())
        val gif = doc.parts[1]
        assertEquals("img1@host", gif.contentId)
        assertEquals("GIF89a", gif.body.copyOfRange(0, 6).decodeToString())
    }

    @Test
    fun nestedMultipartPartsAreFlattenedAndMissingPaddingIsAccepted() {
        val text = "MIME-Version: 1.0\r\n" +
            "Content-Type: multipart/related; boundary=\"OUT\"\r\n\r\n" +
            "--OUT\r\n" +
            "Content-Type: multipart/alternative; boundary=\"IN\"\r\n\r\n" +
            "--IN\r\nContent-Type: text/html\r\n\r\n<b>x</b>\r\n--IN--\r\n" +
            "--OUT\r\n" +
            "Content-Type: image/png\r\nContent-Transfer-Encoding: base64\r\nContent-Location: a.png\r\n\r\nAAEC\r\n" +
            "--OUT--\r\n"
        val doc = FutaberMhtReader.parse(text.encodeToByteArray())
        assertEquals(2, doc.parts.size)
        assertEquals("<b>x</b>", doc.htmlPart?.text())
        assertContentEquals(byteArrayOf(0, 1, 2), doc.parts[1].body)
    }

    @Test
    fun thingsThatAreNotMhtAreRefusedWithAReason() {
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.parse("just some text".encodeToByteArray()) }
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.parse(ByteArray(0)) }
        assertFailsWith<FutaberMhtFormatException> {
            FutaberMhtReader.parse("Content-Type: multipart/related\r\n\r\nbody".encodeToByteArray())
        }
    }

    @Test
    fun headerParametersAreFoundWithAndWithoutQuotes() {
        assertEquals("abc", headerParameter("multipart/related; boundary=\"abc\"; type=\"text/html\"", "boundary"))
        assertEquals("abc", headerParameter("multipart/related; boundary=abc; type=x", "boundary"))
        assertEquals("utf-8", headerParameter("text/html; charset=utf-8", "charset"))
        assertNull(headerParameter("text/html; xcharset=utf-8", "charset"))
    }
}
