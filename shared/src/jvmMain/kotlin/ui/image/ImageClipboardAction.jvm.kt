package com.valoser.futacha.shared.ui.image

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat

internal fun imageClipboardTransferable(image: BufferedImage): Transferable = object : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}

internal fun imageClipboardBitmap(bytes: ByteArray): BufferedImage {
    // Skia also handles WebP/GIF; a static clipboard image is the first decoded frame.
    return org.jetbrains.skia.Image.makeFromEncoded(bytes).use { image ->
        require(image.width > 0 && image.height > 0 && image.width.toLong() * image.height <= 32_000_000L) { "画像が大きすぎます" }
        image.encodeToData(EncodedImageFormat.PNG)?.use { encoded -> ImageIO.read(encoded.bytes.inputStream()) }
            ?: error("画像をコピーできませんでした")
    }
}

internal actual suspend fun copyImageBytesToClipboard(bytes: ByteArray) = withContext(Dispatchers.IO) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(imageClipboardTransferable(imageClipboardBitmap(bytes)), null)
}
