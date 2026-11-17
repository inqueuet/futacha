package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.valoser.futacha.shared.desktop.desktopLocalFile
import com.valoser.futacha.shared.media.video.*
import org.bytedeco.javacv.Java2DFrameConverter
import kotlinx.coroutines.*
import okio.Buffer
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream

/** Local video thumbnails only; remote originals continue through the shared media transport. */
internal class DesktopVideoFrameFetcher(private val path: String, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val png = desktopGrabber(path).use { grabber -> Java2DFrameConverter().use { converter ->
            ensureActive()
            val frame = grabber.grabImage() ?: error("動画のサムネイルを読み込めません")
            val image = desktopRaster(frame, converter, desktopRotation(grabber)).bufferedImage()
            ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        } }
        SourceFetchResult(ImageSource(Buffer().write(png), options.fileSystem), "image/png", DataSource.DISK)
    }
    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val value = data.toString()
            if (!(com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath(value) || value.startsWith("file:", true))) return null
            val file = runCatching { desktopLocalFile(value) }.getOrNull() ?: return null
            if (file.extension.lowercase() !in setOf("mp4", "mov", "webm", "m4v")) return null
            return DesktopVideoFrameFetcher(file.absolutePath, options)
        }
    }
}
