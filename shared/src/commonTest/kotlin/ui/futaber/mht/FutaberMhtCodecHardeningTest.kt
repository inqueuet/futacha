package com.valoser.futacha.shared.ui.futaber.mht

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What keeps a big file from being held whole, and a hostile file from holding the reader. */
@OptIn(ExperimentalEncodingApi::class)
class FutaberMhtCodecHardeningTest {
    private fun body(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    private fun oldLayout(bytes: ByteArray): String {
        val encoded = Base64.Default.encode(bytes)
        return buildString {
            var index = 0
            while (index < encoded.length) {
                val end = minOf(index + 76, encoded.length)
                append(encoded, index, end).append("\r\n")
                index = end
            }
            append("\r\n")
        }
    }

    @Test
    fun aPartIsWrittenAsBase64LinesOf76CharactersWhateverItsSize() {
        val boundary = FutaberMhtWriter.newBoundary(1)
        for (size in listOf(0, 1, 2, 3, 56, 57, 58, 3647, 3648, 3649, 10_000)) {
            val bytes = body(size)
            val whole = FutaberMhtWriter.part(boundary, "image/jpeg", "https://may.2chan.net/27/thumb/1s.jpg", bytes)
            val head = FutaberMhtWriter.partHead(boundary, "image/jpeg", "https://may.2chan.net/27/thumb/1s.jpg").decodeToString()
            assertEquals(head + oldLayout(bytes), whole.decodeToString(), "size $size")
            assertEquals(oldLayout(bytes).length.toLong(), FutaberMhtWriter.bodySize(size), "size $size")
        }
    }

    @Test
    fun theBodyIsWrittenInBatchesToASinkWithTheSameBytesAndTheCountedSize() = runBlocking {
        val bytes = body(20_000)
        val sink = CollectingSink()
        FutaberMhtWriter.writeBody(sink, bytes)
        val boundary = FutaberMhtWriter.newBoundary(1)
        val whole = FutaberMhtWriter.part(boundary, "image/png", null, bytes)
        val headSize = FutaberMhtWriter.partHead(boundary, "image/png", null).size
        assertContentEquals(whole.copyOfRange(headSize, whole.size), sink.bytes)
        assertEquals(FutaberMhtWriter.bodySize(bytes.size), sink.bytes.size.toLong())
    }

    @Test
    fun base64OverARangeIsDecodedLikeTheStandardDecoderWithoutACopyOfTheText() {
        for (size in 0..45) {
            val bytes = body(size)
            val text = Base64.Default.encode(bytes).chunked(7).joinToString("\r\n ")
            val raw = ("junk" + text + "junk").encodeToByteArray()
            assertContentEquals(bytes, FutaberMhtReader.decodeBase64Range(raw, 4, 4 + text.length), "size $size")
            // Some writers leave the padding off.
            val unpadded = Base64.Default.encode(bytes).trimEnd('=').encodeToByteArray()
            assertContentEquals(bytes, FutaberMhtReader.decodeBase64Range(unpadded, 0, unpadded.size), "unpadded $size")
        }
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.decodeBase64Range("AB!D".encodeToByteArray(), 0, 4) }
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.decodeBase64Range("A".encodeToByteArray(), 0, 1) }
    }

    @Test
    fun quotedPrintableOverARangeKeepsTheSoftBreaksAndHexBytes() {
        val raw = "xx=41=3D b=\r\nc=\nd=ZZ=4".encodeToByteArray()
        // "A=" then " b" "c" "d" and the two unreadable escapes left as they are.
        assertEquals("A= bcd=ZZ=4", FutaberMhtReader.decodeQuotedPrintableRange(raw, 2, raw.size).decodeToString())
        assertEquals("", FutaberMhtReader.decodeQuotedPrintableRange(raw, 3, 3).decodeToString())
    }

    @Test
    fun aPictureIsDecodedOnlyWhenAskedForAndABrokenOneIsOnlyLeftOut() {
        val boundary = "BB"
        val text = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"$boundary\"\r\n\r\n" +
            "--$boundary\r\nContent-Type: text/html\r\n\r\n<p>page</p>\r\n" +
            "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Transfer-Encoding: base64\r\nContent-Location: https://may.2chan.net/27/thumb/1s.jpg\r\n\r\n!!!not base64!!!\r\n" +
            "--$boundary\r\nContent-Type: image/png\r\nContent-Transfer-Encoding: base64\r\nContent-Location: https://may.2chan.net/27/thumb/2s.png\r\n\r\nAAEC\r\n" +
            "--$boundary--\r\n"
        val doc = FutaberMhtReader.parse(text.encodeToByteArray())
        assertEquals(3, doc.parts.size)
        assertEquals("<p>page</p>", doc.htmlPart?.text())
        val broken = doc.parts[1]
        assertNull(broken.bodyOrNull())
        assertFailsWith<FutaberMhtFormatException> { broken.body }
        assertContentEquals(byteArrayOf(0, 1, 2), doc.parts[2].bodyOrNull())
        assertTrue(doc.isComplete)
    }

    @Test
    fun aPageThatCannotBeDecodedRefusesTheFile() {
        val text = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"BB\"\r\n\r\n" +
            "--BB\r\nContent-Type: text/html\r\nContent-Transfer-Encoding: base64\r\n\r\n!!!\r\n--BB--\r\n"
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.parse(text.encodeToByteArray()) }
    }

    @Test
    fun anInnerBoundaryThatBeginsWithTheOuterOneIsNotTakenForTheOuterDelimiter() {
        val text = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"ab\"\r\n\r\n" +
            "--ab\r\nContent-Type: multipart/alternative; boundary=\"abc\"\r\n\r\n" +
            "--abc\r\nContent-Type: text/html\r\n\r\n<b>inner</b>\r\n--abc--\r\n" +
            "--ab\r\nContent-Type: image/png\r\nContent-Transfer-Encoding: base64\r\nContent-Location: a.png\r\n\r\nAAEC\r\n" +
            "--ab--\r\n"
        val doc = FutaberMhtReader.parse(text.encodeToByteArray())
        assertEquals(2, doc.parts.size)
        assertEquals("<b>inner</b>", doc.htmlPart?.text())
        assertContentEquals(byteArrayOf(0, 1, 2), doc.parts[1].body)
        assertTrue(doc.isComplete)
    }

    @Test
    fun anInnerBoundaryThatStartsWithTheOuterOneAndTwoDashesDoesNotCloseTheOuterMessage() {
        val text = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"ab\"\r\n\r\n" +
            "--ab\r\nContent-Type: multipart/alternative; boundary=\"ab--cd\"\r\n\r\n" +
            "--ab--cd\r\nContent-Type: text/html\r\n\r\n<b>inner</b>\r\n--ab--cd--\r\n" +
            "--ab\r\nContent-Type: image/png\r\nContent-Transfer-Encoding: base64\r\nContent-Location: a.png\r\n\r\nAAEC\r\n" +
            "--ab--\r\n"
        val doc = FutaberMhtReader.parse(text.encodeToByteArray())
        assertEquals(2, doc.parts.size)
        assertEquals("<b>inner</b>", doc.htmlPart?.text())
    }

    @Test
    fun aDelimiterMayBePaddedWithSpacesButNotFollowedByOtherText() {
        val text = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"bb\"\r\n\r\n" +
            "--bb  \r\nContent-Type: text/html\r\n\r\n<p>x</p>\r\n--bb--  \r\n"
        val doc = FutaberMhtReader.parse(text.encodeToByteArray())
        assertEquals("<p>x</p>", doc.htmlPart?.text())
        assertTrue(doc.isComplete)
    }

    @Test
    fun aFileWithoutItsClosingDelimiterIsReadAsCutShortWithoutItsLastPartialPart() {
        val boundary = FutaberMhtWriter.newBoundary(3)
        val bytes = FutaberMhtWriter.header(boundary, "題", "Tue, 06 Oct 2026 11:04:00 +0000", "https://may.2chan.net/27/res/1.htm", emptyList()) +
            FutaberMhtWriter.part(boundary, "text/html", null, "<p>page</p>".encodeToByteArray(), "utf-8") +
            FutaberMhtWriter.part(boundary, "image/jpeg", "https://may.2chan.net/27/thumb/1s.jpg", body(2000)).let { it.copyOf(it.size - 300) }
        val doc = FutaberMhtReader.parse(bytes)
        assertFalse(doc.isComplete)
        // The page is kept; the picture that was cut is left out.
        assertEquals(1, doc.parts.size)
        assertEquals("<p>page</p>", doc.htmlPart?.text())
    }

    @Test
    fun endlessFoldedHeaderLinesAreRefusedInsteadOfBeingJoinedForever() {
        val folded = buildString {
            append("MIME-Version: 1.0\r\nSubject: x\r\n")
            repeat(100_000) { append(" yyyy\r\n") }
            append("Content-Type: multipart/related; boundary=\"b\"\r\n\r\n--b--\r\n")
        }
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.parse(folded.encodeToByteArray()) }
    }

    @Test
    fun aLongButReasonableHeaderStillReads() {
        val subject = "あ".repeat(300)
        val bytes = FutaberMhtWriter.header("b", subject, "Tue, 06 Oct 2026 11:04:00 +0000", "https://may.2chan.net/27/res/1.htm", listOf("Thumb-Data" to "A".repeat(56_000))) +
            FutaberMhtWriter.part("b", "text/html", null, "<p>x</p>".encodeToByteArray(), "utf-8") + FutaberMhtWriter.end("b")
        val doc = FutaberMhtReader.parse(bytes)
        assertEquals(subject, doc.subject)
        assertEquals(56_000, doc.custom("thumb-data")?.length)
        assertNotNull(FutaberMhtReader.readHeaderBlock(bytes.copyOf(100_000)))
    }

    @Test
    fun moreThanThePartLimitIsRefusedWithAReason() {
        val text = buildString {
            append("MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"b\"\r\n\r\n")
            append("--b\r\nContent-Type: text/html\r\n\r\n<p>x</p>\r\n")
            repeat(5_001) { append("--b\r\nContent-Type: image/png\r\nContent-Location: ").append(it).append("\r\n\r\nAA\r\n") }
            append("--b--\r\n")
        }
        val failure = assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.parse(text.encodeToByteArray()) }
        assertTrue(failure.message!!.contains("多すぎ"))
    }

    @Test
    fun partsNestedTooDeepAreRefused() {
        fun nested(level: Int): String =
            if (level == 0) "Content-Type: text/html\r\n\r\n<p>x</p>"
            else "Content-Type: multipart/related; boundary=\"b$level\"\r\n\r\n--b$level\r\n${nested(level - 1)}\r\n--b$level--"
        val text = "MIME-Version: 1.0\r\n" + nested(9) + "\r\n"
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtReader.parse(text.encodeToByteArray()) }
    }

    @Test
    fun theReadLimitIsTheOneTheFileSystemReadsWholeFilesUnder() {
        // A file that was saved must be one that can be opened: the writer's limit and the reader's are this one number.
        assertEquals(com.valoser.futacha.shared.util.MAX_FILE_SYSTEM_FILE_SIZE, FUTABER_MHT_MAX_READ_BYTES)
    }

    @Test
    fun theDateHeaderIsReadAsTheTimeTheFileWasSaved() {
        assertEquals(1_791_284_640_000L, parseRfc1123Millis("Tue, 06 Oct 2026 11:04:00 +0000"))
        assertEquals(1_791_284_640_000L, parseRfc1123Millis("Tue, 06 Oct 2026 20:04:00 +0900"))
        assertEquals(1_791_284_640_000L, parseRfc1123Millis("6 Oct 2026 11:04 GMT"))
        assertEquals(0L, parseRfc1123Millis("Thu, 01 Jan 1970 00:00:00 +0000"))
        assertNull(parseRfc1123Millis("yesterday"))
        assertNull(parseRfc1123Millis(null))
        assertNull(parseRfc1123Millis("Tue, 32 Oct 2026 11:04:00 +0000"))
    }
}
