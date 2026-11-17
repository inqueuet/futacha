@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertEquals

class IosTextEncodingLossyTest {
    private val shiftJisHeader = "text/html; charset=Shift_JIS"
    private val utf8Header = "text/html; charset=UTF-8"

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun sjis(text: String): ByteArray = TextEncoding.encodeToShiftJis(text)

    @Test
    fun shiftJisUndefinedSingleBytesAreReplacedInsteadOfEmptyingThePage() {
        for (invalid in listOf(0x80, 0xA0, 0xFD, 0xFE, 0xFF)) {
            val body = sjis("スレ") + bytes(invalid) + sjis("本文")
            assertEquals("スレ�本文", TextEncoding.decodeToString(body, shiftJisHeader), "byte $invalid")
            assertEquals("スレ�本文", TextEncoding.decodeToString(body, "text/html"), "byte $invalid")
        }
    }

    @Test
    fun shiftJisLeadByteWithoutTrailReplacesOnlyTheLeadByte() {
        val body = sjis("あ") + bytes(0x82, 0x20) + sjis("い")
        assertEquals("あ� い", TextEncoding.decodeToString(body, shiftJisHeader))
    }

    @Test
    fun shiftJisMultiByteCharacterCutByRangeReadIsReplaced() {
        val full = sjis("<html>過去ログ")
        val truncated = full.copyOf(full.size - 1)
        assertEquals("<html>過去ロ�", TextEncoding.decodeToString(truncated, shiftJisHeader))
        assertEquals("<html>過去ロ�", TextEncoding.decodeToString(truncated, "text/html"))
        assertEquals("<html>過去ロ�", TextEncoding.decodeToString(truncated, utf8Header))
    }

    @Test
    fun shiftJisUnmappedPairReplacesLeadAndReprocessesTrail() {
        // A structurally valid pair that Apple's CP932 table leaves unassigned.
        val (lead, trail) = unmappedShiftJisPair() ?: return
        val body = sjis("前") + bytes(lead, trail) + sjis("後")
        assertEquals("前�${trail.toChar()}後", TextEncoding.decodeToString(body, shiftJisHeader))
    }

    private fun unmappedShiftJisPair(): Pair<Int, Int>? {
        val encoding = platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding(
            platform.CoreFoundation.kCFStringEncodingDOSJapanese.toUInt()
        )
        for (lead in (0x81..0x9F) + (0xE0..0xFC)) {
            for (trail in 0x40..0x7E) {
                val data = bytes(lead, trail).usePinned { pinned ->
                    platform.Foundation.NSData.create(bytes = pinned.addressOf(0), length = 2u)
                }
                if (platform.Foundation.NSString.create(data = data, encoding = encoding) == null) return lead to trail
            }
        }
        return null
    }

    @Test
    fun shiftJisLossyDecodingKeepsLongTextAroundManyBadBytes() {
        val chunk = sjis("ｱいうえお<br>")
        val body = ArrayList<Byte>()
        repeat(20_000) { index ->
            chunk.forEach(body::add)
            if (index % 97 == 0) body += 0xFF.toByte()
        }
        val decoded = TextEncoding.decodeToString(body.toByteArray(), shiftJisHeader)
        assertEquals(20_000, decoded.split("ｱいうえお<br>").size - 1)
        assertEquals((0 until 20_000).count { it % 97 == 0 }, decoded.count { it == '�' })
    }

    @Test
    fun utf8MultiByteCharacterCutByRangeReadIsReplaced() {
        val full = "<html>過去ログ".encodeToByteArray()
        for (cut in 1..2) {
            val truncated = full.copyOf(full.size - cut)
            assertEquals("<html>過去ロ�", TextEncoding.decodeToString(truncated, utf8Header), "cut $cut")
        }
    }

    @Test
    fun utf8HeaderWithInvalidBytesFallsBackToLossyShiftJisLikeAndroid() {
        val body = "abc".encodeToByteArray() + bytes(0xFF) + "def".encodeToByteArray()
        assertEquals("abc�def", TextEncoding.decodeToString(body, utf8Header))
        assertEquals("abc�def", TextEncoding.decodeToString(body, "text/html"))
    }

    @Test
    fun embeddedNulDoesNotTruncateTheDecodedPage() {
        val body = sjis("前") + bytes(0x00) + sjis("後")
        assertEquals("前\u0000後", TextEncoding.decodeToString(body, shiftJisHeader))
    }
}
