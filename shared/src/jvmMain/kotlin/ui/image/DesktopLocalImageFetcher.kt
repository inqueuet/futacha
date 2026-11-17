package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.valoser.futacha.shared.desktop.desktopLocalFile
import com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.buffer

/** A drive letter is a local file, although a generic URI parser treats it as a scheme. */
internal class DesktopLocalImageFetcher(private val path: String, private val options: Options) : Fetcher {
    override suspend fun fetch() = withContext(Dispatchers.IO) {
        SourceFetchResult(ImageSource(options.fileSystem.source(path.toPath()).buffer(), options.fileSystem),
            null, DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val value = data.toString()
            if (!isAbsoluteLocalMediaPath(value) && !value.startsWith("file:/", true)) return null
            return DesktopLocalImageFetcher(desktopLocalFile(value).absolutePath, options)
        }
    }
}
