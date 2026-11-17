package com.valoser.futacha.shared.media.video

import kotlinx.coroutines.runBlocking
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.*

class DesktopVideoThumbnailBoundsTest {
    @Test fun thumbnailsDownsampleLargeVideosWhileEditingKeepsItsOwnLimit() = runBlocking {
        val file = Files.createTempFile("thumbnail-4096", ".mp4").toFile()
        try {
            Java2DFrameConverter().use { converter ->
                FFmpegFrameRecorder(file, 4096, 64).use { recorder ->
                    recorder.format = "mp4"
                    recorder.videoCodec = AV_CODEC_ID_MPEG4
                    recorder.frameRate = 1.0
                    recorder.start()
                    recorder.record(converter.convert(BufferedImage(4096, 64, BufferedImage.TYPE_3BYTE_BGR)))
                    recorder.stop()
                }
                assertFailsWith<IllegalArgumentException> { desktopGrabber(file.absolutePath) }
                desktopGrabber(file.absolutePath, thumbnailEdge = 128).use { grabber ->
                    val frame = assertNotNull(grabber.grabImage())
                    val raster = desktopRaster(frame, converter, desktopRotation(grabber))
                    assertTrue(raster.width in 1..128 && raster.height in 1..128)
                }
            }
        } finally { file.delete() }
    }
}
