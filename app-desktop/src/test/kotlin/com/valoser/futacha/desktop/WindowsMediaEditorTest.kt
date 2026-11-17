@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import com.valoser.futacha.shared.desktop.*
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.ui.media.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.test.*

/** Uses the real Windows picker and common editor/save screens with isolated app data. */
class WindowsMediaEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imagePickerBlackMaskSaveAndReopen() = withEditor { root ->
        val input = File(root, "入力 画像 #50%.png")
        val bitmap = BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 100) for (x in 0 until 160) bitmap.setRGB(x, y, 0xffee8030.toInt())
        ImageIO.write(bitmap, "png", input)
        val original = input.readBytes()
        selectFile(input, "device-image-editor-menu")
        awaitEnabled("image-editor-export")
        compose.onNodeWithTag("image-editor-tool-regions").performScrollTo().performClick()
        compose.onNodeWithTag("image-editor-add-region").performScrollTo().performClick()
        compose.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        capture("image-editor")
        compose.onNodeWithTag("image-editor-export").performClick()
        compose.waitUntil(30_000) { File(root, "保存先/edited_images").listFiles().orEmpty().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("編集した画像を保存しました").fetchSemanticsNodes().isNotEmpty() }
        val output = File(root, "保存先/edited_images").listFiles()!!.single()
        val edited = ImageIO.read(output)
        assertEquals(160, edited.width)
        assertEquals(100, edited.height)
        val center = edited.getRGB(80, 50)
        assertTrue(listOf(0, 8, 16).all { (center ushr it and 255) < 20 })
        assertTrue((edited.getRGB(5, 5) ushr 16 and 255) > 200)
        assertContentEquals(original, input.readBytes())
    }

    @Test fun videoPickerBlackMaskExportSaveAndReopen() = withEditor { root ->
        val input = File(root, "入力 動画 #50%.mp4").apply { writeBytes(VideoEditFixtures.bytes("variable")) }
        val original = input.readBytes()
        val before = runBlocking { inspectDeviceVideo(input.absolutePath) }
        selectFile(input, "device-video-editor-menu")
        awaitEnabled("video-editor-add")
        compose.onNodeWithTag("video-editor-add").performScrollTo().performClick()
        compose.onNodeWithTag("video-region-controls").performScrollTo()
        compose.onNodeWithText("黒塗り").performScrollTo().performClick()
        capture("video-editor")
        compose.onNodeWithTag("video-editor-export").performClick()
        awaitEnabled("video-editor-save", 90_000)
        compose.onNodeWithTag("video-editor-save").performScrollTo().performClick()
        compose.waitUntil(30_000) { File(root, "保存先/edited_videos").listFiles().orEmpty().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("編集した動画を保存しました").fetchSemanticsNodes().isNotEmpty() }
        val output = File(root, "保存先/edited_videos").listFiles()!!.single()
        val after = runBlocking { inspectDeviceVideo(output.absolutePath) }
        verifyVideoEditTiming(before, after)
        assertTrue(after.hasAudio)
        assertContentEquals(original, input.readBytes())
    }

    private fun withEditor(block: (File) -> Unit) {
        Assume.assumeTrue(DesktopPlatform.isWindows)
        val root = Files.createTempDirectory("futacha-windows-media").toFile()
        val environment = DesktopEnvironment(root, File(root, "cache"))
        environment.install()
        val fs = createFileSystem(environment)
        val state = createAppStateStore(environment, fs)
        val settings = MediaFeatureSettings(imageEditorEnabled = true, videoEditorEnabled = true)
        val gate = MediaFeatureGate().also { it.update(settings) }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components { addPlatformImageComponents() }.build()
        try {
            runBlocking { state.setManualSaveLocation(SaveLocation.Path(File(root, "保存先").absolutePath)) }
            compose.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalMediaFeatureSettings provides settings,
                    LocalMediaFeatureGate provides gate) {
                    MaterialTheme { Box(Modifier.requiredSize(1100.dp, 800.dp)) {
                        DeviceImageEditingHost(fs, state) { Column { DeviceImageEditorMenuItem {}; DeviceVideoEditorMenuItem {} } }
                    } }
                }
            }
            block(root)
        } finally {
            compose.setContent {}
            compose.waitForIdle()
            loader.shutdown()
            runBlocking { environment.closeAndAwait() }
            root.deleteRecursively()
        }
    }

    private fun awaitEnabled(tag: String, timeout: Long = 20_000) = compose.waitUntil(timeout) {
        val errors = compose.onAllNodesWithTag("video-editor-error").fetchSemanticsNodes()
        check(errors.isEmpty()) { errors.joinToString { it.config.toString() } }
        runCatching { compose.onNodeWithTag(tag).assertIsEnabled(); true }.getOrDefault(false)
    }

    private fun capture(tag: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asSkiaBitmap()
        org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
            image.encodeToData()!!.use { bytes ->
                File("build/desktop-ui/windows-$tag.png").also { it.parentFile.mkdirs() }.writeBytes(bytes.bytes)
            }
        }
    }

    private fun selectFile(input: File, menu: String) {
        fun picker(component: Component): JFileChooser? = if (component is JFileChooser) component
            else (component as? Container)?.components?.firstNotNullOfOrNull(::picker)
        val selection = CompletableFuture.runAsync {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            var selected = false
            while (!selected && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait {
                    Window.getWindows().filter { it.isVisible }.firstNotNullOfOrNull(::picker)?.let {
                        it.selectedFile = input
                        it.approveSelection()
                        selected = true
                    }
                }
                if (!selected) Thread.sleep(30)
            }
            check(selected) { "Windows file picker did not open" }
        }
        compose.onNodeWithTag(menu).performClick()
        selection.get(20, TimeUnit.SECONDS)
    }
}
