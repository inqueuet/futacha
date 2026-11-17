package com.valoser.futacha

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.util.readImageDataFromUri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real content-provider I/O for both posting modes, independent of picker vendor. */
class AttachmentPickerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageAndVideoReadersHonorEachModesLimit() = runBlocking {
        for (extension in listOf("jpg", "mp4", "webm")) {
            withAttachment("camera.$extension", 8_192_001) { uri ->
                assertNull(readImageDataFromUri(context, uri))
                val result = readImageDataFromUri(context, uri, 32_000_000)
                assertNotNull(result)
                assertEquals("camera.$extension", result!!.fileName)
                assertEquals(8_192_001, result.bytes.size)
                assertEquals(7.toByte(), result.bytes.last())
            }
        }
    }

    @Test fun missingProviderSizeStillEnforcesLimitForImagesAndVideos() = runBlocking {
        for (name in listOf("camera.jpg", "video.mp4")) {
            withAttachment(name, 5) { uri ->
                val unknown = uri.buildUpon().appendQueryParameter("size", "-1").build()
                assertNull(readImageDataFromUri(context, unknown, 4))
                assertEquals(5, readImageDataFromUri(context, unknown, 5)!!.bytes.size)
            }
        }
    }

    @Test fun incorrectProviderSizesAreNotReturnedAsCorruptAttachments() = runBlocking {
        withAttachment("video.mp4", 5) { uri ->
            for (size in listOf("3", "7")) {
                assertNull(readImageDataFromUri(context, uri.buildUpon().appendQueryParameter("size", size).build(), 10))
            }
        }
    }

    @Test fun emptyAndUnavailableAttachmentsFailWithoutCreatingAPreview() = runBlocking {
        withAttachment("empty.jpg", 0) { assertNull(readImageDataFromUri(context, it)) }
        assertNull(readImageDataFromUri(context, Uri.parse("content://com.valoser.futacha.test.attachments/missing.mp4")))
    }

    @Test fun providerWithoutDisplayNameKeepsVideoMimeExtension() = runBlocking {
        withAttachment("video.mp4", 5) { uri ->
            val nameless = uri.buildUpon().appendQueryParameter("nameless", "true").build()
            assertEquals("attachment.mp4", readImageDataFromUri(context, nameless)!!.fileName)
        }
    }

    private suspend fun withAttachment(name: String, size: Int, block: suspend (Uri) -> Unit) {
        val uri = Uri.parse("content://com.valoser.futacha.test.attachments/$name")
        context.contentResolver.openOutputStream(uri)!!.use { it.write(ByteArray(size) { 7 }) }
        try { block(uri) }
        finally { context.contentResolver.delete(uri, null, null) }
    }
}
