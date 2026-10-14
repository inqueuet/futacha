package com.valoser.futacha.shared.ui.image

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import com.valoser.futacha.shared.media.source.bindOriginalMediaSource
import okio.Path.Companion.toPath

class ImageClipboardActionTest {
    @Test fun originalCacheCopiesFullImageWithoutAnotherDownload() = runBlocking {
        val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(1, 1, 0xff12ab34.toInt())
        val output = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "png", output)
        val bytes = output.toByteArray()
        val directory = java.nio.file.Files.createTempDirectory("clipboard-original").toFile()
        var downloads = 0
        val store = com.valoser.futacha.shared.media.source.OriginalMediaStore("clipboard-test", {
            coil3.disk.DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, com.valoser.futacha.shared.media.source.OriginalMediaDownloader { request, sink ->
            downloads++; sink.write(bytes)
            com.valoser.futacha.shared.media.source.OriginalMediaInfo("image/png", bytes.size.toLong(), resolvedUrl = request.url)
        })
        val client = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine) {
            engine { addHandler { error("The clipboard must reuse original bytes") } }
        }
        try {
            client.bindOriginalMediaSource(store)
            val url = "https://example.test/original.png"
            store.acquire(com.valoser.futacha.shared.media.source.OriginalMediaRequest(url)).close()
            repeat(2) {
                copyImageUrlToClipboard(client, url) { original ->
                    val pasted = imageClipboardTransferable(imageClipboardBitmap(original)).getTransferData(DataFlavor.imageFlavor) as BufferedImage
                    assertEquals(3, pasted.width)
                    assertEquals(2, pasted.height)
                    assertEquals(0xff12ab34.toInt(), pasted.getRGB(1, 1))
                }
            }
            assertEquals(1, downloads)
        } finally {
            client.close(); store.closeAndAwait(); directory.deleteRecursively()
        }
    }
    @Test fun clipboardContainsImagePixelsRatherThanAFileListOrUrl() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(1, 1, 0xff12ab34.toInt())
        val transfer = imageClipboardTransferable(image)
        assertTrue(transfer.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertFalse(transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        val pasted = transfer.getTransferData(DataFlavor.imageFlavor) as BufferedImage
        assertEquals(0xff12ab34.toInt(), pasted.getRGB(1, 1))
        assertFailsWith<UnsupportedFlavorException> { transfer.getTransferData(DataFlavor.stringFlavor) }
    }
}
