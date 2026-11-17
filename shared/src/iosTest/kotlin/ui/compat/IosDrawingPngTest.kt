package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals

class IosDrawingPngTest {
    private val ink = 0xFF800000.toInt()
    private val paper = 0xFFF0E0D6.toInt()

    private fun pixels(points: List<CompatDrawingPoint>): IntArray = runBlocking {
        val png = renderCompatDrawingPng(listOf(CompatDrawingStroke(ink, 12f, points)), paper, 120, 80).getOrThrow()
        val image = Image.makeFromEncoded(png.bytes)
        try {
            val bitmap = image.toComposeImageBitmap()
            assertEquals(120, bitmap.width)
            assertEquals(80, bitmap.height)
            IntArray(120 * 80).also { bitmap.readPixels(it) }
        } finally { image.close() }
    }

    @Test fun contactDotSavesAsACircleWithTheSelectedDiameter() {
        val pixels = pixels(listOf(CompatDrawingPoint(40f, 40f)))
        for ((x, y) in listOf(40 to 40, 35 to 40, 44 to 40, 40 to 35, 40 to 44)) {
            assertEquals(ink, pixels[y * 120 + x], "dot at $x,$y")
        }
        assertEquals(paper, pixels[40 * 120 + 33])
        assertEquals(paper, pixels[33 * 120 + 40])
        assertEquals(paper, pixels[0])
    }

    @Test fun shortStrokeSavesBothRoundCapsLikeThePreview() {
        val pixels = pixels(listOf(CompatDrawingPoint(40f, 40f), CompatDrawingPoint(42f, 40f)))
        for (x in listOf(35, 40, 42, 46)) assertEquals(ink, pixels[40 * 120 + x], "cap at $x")
        assertEquals(paper, pixels[40 * 120 + 33])
        assertEquals(paper, pixels[40 * 120 + 49])
    }
}
