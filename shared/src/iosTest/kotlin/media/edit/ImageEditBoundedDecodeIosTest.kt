package com.valoser.futacha.shared.media.edit

import coil3.ImageLoader
import coil3.PlatformContext
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** B-8: an image larger than the editor's edge is decoded by ImageIO at the reduced size. */
class ImageEditBoundedDecodeIosTest {
    @Test fun largeImageOpensAtTheEditorEdge() = runBlocking {
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            ImageEditSession.open(ImageEditInput(ImageData(binaryPng(6_000, 4_000), "large.png")), gate, loader, PlatformContext.INSTANCE).use { session ->
                val raster = session.original
                assertEquals(IMAGE_EDIT_MAX_EDGE, raster.width)
                assertTrue(raster.height in 1364..1366, "height ${raster.height}")
                val row = raster.height / 2 * raster.width
                assertTrue((raster.argb[row + 100] and 0xffffff) < 0x101010)
                assertTrue((raster.argb[row + raster.width - 100] and 0xffffff) > 0xefefef)
                assertTrue(session.export(ImageEditDocument()).bytes.isNotEmpty())
            }
        } finally { loader.shutdown() }
    }

    /** 1-bit grayscale, black left half and white right half, in stored (uncompressed) deflate blocks. */
    private fun binaryPng(width: Int, height: Int): ByteArray {
        val rowBytes = (width + 7) / 8
        val raw = ByteArray((1 + rowBytes) * height)
        for (y in 0 until height) for (i in 1 + rowBytes / 2..rowBytes) raw[y * (1 + rowBytes) + i] = 0xff.toByte()
        val blocks = (raw.size + 65_534) / 65_535
        val zlib = ByteArray(2 + blocks * 5 + raw.size + 4)
        zlib[0] = 0x78; zlib[1] = 0x01
        var offset = 0; var at = 2
        while (offset < raw.size) {
            val count = minOf(65_535, raw.size - offset)
            zlib[at++] = if (offset + count == raw.size) 1 else 0
            zlib[at++] = count.toByte(); zlib[at++] = (count ushr 8).toByte()
            zlib[at++] = count.inv().toByte(); zlib[at++] = (count.inv() ushr 8).toByte()
            raw.copyInto(zlib, at, offset, offset + count)
            at += count; offset += count
        }
        var a = 1L; var b = 0L
        for (value in raw) { a = (a + (value.toInt() and 255)) % 65_521; b = (b + a) % 65_521 }
        int(((b shl 16) or a).toInt()).copyInto(zlib, at)
        fun chunk(type: String, data: ByteArray): ByteArray {
            val body = type.encodeToByteArray() + data
            return int(data.size) + body + int(crc32(body))
        }
        val header = int(width) + int(height) + byteArrayOf(1, 0, 0, 0, 0)
        return byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) + chunk("IHDR", header) +
            chunk("IDAT", zlib) + chunk("IEND", ByteArray(0))
    }

    private fun int(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun crc32(bytes: ByteArray): Int {
        var crc = 0xffffffffL.toInt()
        for (value in bytes) {
            crc = crc xor (value.toInt() and 255)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xedb88320.toInt() else crc ushr 1 }
        }
        return crc.inv()
    }
}
