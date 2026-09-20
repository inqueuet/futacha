package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.*
import com.valoser.futacha.shared.desktop.*
import com.valoser.futacha.shared.util.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopLocalImageTest {
    @Test fun savedImagesWithDriveLettersUnicodeAndReservedCharactersReopenWithoutNetwork() = runBlocking {
        val root = Files.createTempDirectory("futacha-local-image").toFile()
        val environment = DesktopEnvironment(root, File(root, "cache"))
        val fs = createFileSystem(environment)
        val client = HttpClient(MockEngine { error("A local image must not access HTTP") })
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components { addPlatformImageComponents() }.build()
        try {
            val file = File(root, "日本語 空白 #50%+画像.png")
            ImageIO.write(BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB), "png", file)
            val original = file.readBytes()
            for (path in listOf(file.absolutePath, file.invariantSeparatorsPath, file.toURI().toASCIIString())) {
                val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(path).build())
                assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
                assertEquals(24, result.image.width)
                val copied = withMediaSaveSource(client, fs, path) { source ->
                    val buffer = ByteArray(4096)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) { val count = source.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                    output.toByteArray()
                }
                assertContentEquals(original, copied)
            }
            val resolved = fs.resolveAbsolutePath("編集/input.mp4")
            assertFalse('\\' in resolved)
            assertEquals(File(root, "編集/input.mp4").canonicalFile, File(resolved).canonicalFile)
        } finally { loader.shutdown(); client.close(); environment.closeAndAwait(); root.deleteRecursively() }
    }
}
