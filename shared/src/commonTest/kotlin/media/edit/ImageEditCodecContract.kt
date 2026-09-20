package com.valoser.futacha.shared.media.edit

import coil3.ImageLoader
import coil3.PlatformContext
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.GenerationImageFixtures
import com.valoser.futacha.shared.media.prompt.ImageGenerationMetadataReader
import com.valoser.futacha.shared.media.prompt.PreservedImageMetadata
import com.valoser.futacha.shared.util.ImageData
import kotlin.test.*

/** Called on native Skia/JVM and iOS; Android uses its real Bitmap instrumentation. */
internal object ImageEditCodecContract {
    private fun pattern() = EditRaster(80, 48, IntArray(80 * 48) { i ->
        val right = i % 80 >= 40; val bottom = i / 80 >= 24
        when { !right && !bottom -> 0xffff0000.toInt(); right && !bottom -> 0xff00ff00.toInt()
            right && bottom -> 0xff0000ff.toInt(); else -> 0xffffff00.toInt() }
    })
    suspend fun nativePixelsPreviewAndJpegExport(context: PlatformContext) {
        val loader = ImageLoader.Builder(context).build()
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        try {
            val source = pattern(); val input = ImageData(encodeImageEditJpeg(source), "phone.jpg")
            val originalBytes = input.bytes.copyOf()
            ImageEditSession.open(ImageEditInput(input), gate, loader, context).use { session ->
                assertEquals(80, session.original.width); assertEquals(48, session.original.height)
                val doc = ImageEditDocument(regions = listOf(EditRegion(1, style = EditStyle.BLACK)))
                assertFailsWith<IllegalStateException> { session.export(doc.copy(analysed = true)) }
                assertTrue(session.export(doc.copy(analysed = true, reviewed = true)).bytes.isNotEmpty())
                val rendered = renderImageEdit(session.original, doc)
                val bitmap = imageEditBitmap(rendered); val pixels = IntArray(rendered.argb.size)
                bitmap.readPixels(pixels)
                assertContentEquals(rendered.argb, pixels, "Preview must show the pixels that are exported")
                val result = session.export(doc)
                assertEquals(255, result.bytes[0].toInt() and 255); assertEquals(216, result.bytes[1].toInt() and 255)
                ImageEditSession.open(ImageEditInput(result), gate, loader, context).use { reloaded ->
                    val center = reloaded.original.argb[24 * 80 + 40]
                    assertTrue((center and 0xffffff) < 0x101010, "Black cover must survive JPEG encoding")
                    val corner = reloaded.original.argb[5 * 80 + 5]
                    assertTrue((corner ushr 16 and 255) > 220 && (corner and 255) < 25)
                }
                assertContentEquals(originalBytes, input.bytes)
            }
        } finally { loader.shutdown() }
    }
    suspend fun exifRotationAndMirroringAreAppliedBeforeEditing(context: PlatformContext) {
        val loader = ImageLoader.Builder(context).build()
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        val jpeg = encodeImageEditJpeg(pattern())
        // Corner identifiers in clockwise order, as required by each EXIF orientation.
        val expected = listOf(listOf(0,1,2,3), listOf(1,0,3,2), listOf(2,3,0,1), listOf(3,2,1,0),
            listOf(0,3,2,1), listOf(3,0,1,2), listOf(2,1,0,3), listOf(1,2,3,0))
        try {
            for (orientation in 1..8) {
                val header = byteArrayOf(69,120,105,102,0,0, 73,73,42,0,8,0,0,0, 1,0,
                    18,1,3,0,1,0,0,0, orientation.toByte(),0,0,0, 0,0,0,0)
                val bytes = jpeg.copyOfRange(0, 2) + byteArrayOf(-1,-31,0,(header.size+2).toByte()) + header + jpeg.copyOfRange(2,jpeg.size)
                ImageEditSession.open(ImageEditInput(ImageData(bytes,"phone.jpg")), gate, loader, context).use { session ->
                    val r = session.original
                    assertEquals(if (orientation <= 4) 80 else 48, r.width)
                    assertEquals(if (orientation <= 4) 48 else 80, r.height)
                    val points = listOf(5 to 5, r.width-6 to 5, r.width-6 to r.height-6, 5 to r.height-6)
                    val actual = points.map { (x,y) ->
                        val c = r.argb[y*r.width+x]; val red=c ushr 16 and 255; val green=c ushr 8 and 255
                        when { red>180 && green>180 -> 3; red>180 -> 0; green>180 -> 1; else -> 2 }
                    }
                    assertEquals(expected[orientation-1], actual, "EXIF $orientation")
                    val saved = session.export(ImageEditDocument())
                    ImageEditSession.open(ImageEditInput(saved), gate, loader, context).use { reopened ->
                        assertEquals(r.width, reopened.original.width)
                        assertEquals(r.height, reopened.original.height)
                        val savedCorners = points.map { (x,y) ->
                            val c = reopened.original.argb[y*r.width+x]; val red=c ushr 16 and 255; val green=c ushr 8 and 255
                            when { red>180 && green>180 -> 3; red>180 -> 0; green>180 -> 1; else -> 2 }
                        }
                        assertEquals(actual, savedCorners, "Saved EXIF must not rotate the image again")
                    }
                }
            }
        } finally { loader.shutdown() }
    }
    suspend fun featureGatesAreIndependentAndOldSessionsCannotExport(context: PlatformContext) {
        val loader=ImageLoader.Builder(context).build(); val gate=MediaFeatureGate()
        val input=ImageEditInput(ImageData(GenerationImageFixtures.jpeg,"phone.jpg"))
        try {
            assertFailsWith<IllegalArgumentException> { ImageEditSession.open(input,gate,loader,context) }
            gate.update(MediaFeatureSettings(promptDisplayEnabled=true))
            assertFailsWith<IllegalArgumentException> { ImageEditSession.open(input,gate,loader,context) }
            gate.update(MediaFeatureSettings(imageEditorEnabled=true))
            ImageEditSession.open(input,gate,loader,context).use { session ->
                gate.update(MediaFeatureSettings(imageEditorEnabled=true, promptDisplayEnabled=true, videoEditorEnabled=true))
                assertTrue(session.export(ImageEditDocument()).bytes.isNotEmpty())
                gate.update(MediaFeatureSettings.Disabled)
                assertFailsWith<IllegalStateException> { session.export(ImageEditDocument()) }
                gate.update(MediaFeatureSettings(imageEditorEnabled=true))
                assertFailsWith<IllegalStateException> { session.export(ImageEditDocument()) }
            }
        } finally { loader.shutdown() }
    }
    suspend fun staticWebpFromDeviceIsSupported(context: PlatformContext) {
        val loader=ImageLoader.Builder(context).build(); val gate=MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled=true)) }
        try {
            ImageEditSession.open(ImageEditInput(ImageData(GenerationImageFixtures.webp,"phone.webp")),gate,loader,context).use { session ->
                assertEquals(8, session.original.width)
                assertTrue(session.export(ImageEditDocument()).fileName.endsWith(".jpg"))
            }
        } finally { loader.shutdown() }
    }

    suspend fun promptOffPreservesTagsAndEditedPixelsWithoutCache(context: PlatformContext) {
        val loader = ImageLoader.Builder(context).build()
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        try {
            for (bytes in listOf(GenerationImageFixtures.jpeg, GenerationImageFixtures.webp, GenerationImageFixtures.jpegXmp)) {
                val original = bytes.copyOf()
                val raw = PreservedImageMetadata.scan(bytes)
                ImageEditSession.open(ImageEditInput(ImageData(bytes, "phone-image")), gate, loader, context).use { session ->
                    val document = ImageEditDocument(regions = listOf(EditRegion(1, bounds = EditBounds(0f, 0f, 1f, 1f), style = EditStyle.BLACK)))
                    val output = session.export(document)
                    assertEquals(raw, PreservedImageMetadata.scan(output.bytes))
                    val generation = ImageGenerationMetadataReader().read(output.bytes.size.toLong()) { offset, count ->
                        output.bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
                    }
                    assertTrue(generation.candidates.all { it.positive == GenerationImageFixtures.positive })
                    assertTrue(generation.candidates.isNotEmpty())
                    ImageEditSession.open(ImageEditInput(output), gate, loader, context).use { reloaded ->
                        assertEquals(8, reloaded.original.width); assertEquals(8, reloaded.original.height)
                        assertTrue(reloaded.original.argb.all { (it and 0xffffff) < 0x101010 })
                    }
                }
                assertContentEquals(original, bytes)
            }
        } finally { loader.shutdown() }
    }
}
