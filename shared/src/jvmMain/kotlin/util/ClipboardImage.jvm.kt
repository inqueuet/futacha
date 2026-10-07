package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberClipboardImageReader(): suspend () -> ImageData = remember {
    {
        withContext(Dispatchers.IO) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            check(clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) { "コピーされた画像がありません" }
            val image = clipboard.getData(DataFlavor.imageFlavor) as Image
            val width = image.getWidth(null)
            val height = image.getHeight(null)
            require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000) { "画像が大きすぎます" }
            val bitmap = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            bitmap.createGraphics().let { graphics ->
                try { graphics.drawImage(image, 0, 0, null) } finally { graphics.dispose() }
            }
            val output = ByteArrayOutputStream()
            check(ImageIO.write(bitmap, "png", output)) { "画像を読み込めませんでした" }
            clipboardImageData(output.toByteArray())
        }
    }
}
