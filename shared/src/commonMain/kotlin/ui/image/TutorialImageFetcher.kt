package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import futacha.shared.generated.resources.Res
import okio.Buffer
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Example-board media are packaged samples, never network requests. */
private val tutorialImageUrl = Regex("^https?://(?:www\\.)?example\\.com/(?:b/(?:src|thumb)|t/cat)/[0-9]+s?\\.(?:png|jpg|webp)$",
        RegexOption.IGNORE_CASE)

internal fun isTutorialImageUrl(url: String): Boolean = tutorialImageUrl.matches(url)

internal class TutorialImageFetcherFactory : Fetcher.Factory<Uri> {
    @OptIn(ExperimentalResourceApi::class)
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        if (!isTutorialImageUrl(data.toString())) return null
        return object : Fetcher {
            override suspend fun fetch() = SourceFetchResult(
                source = ImageSource(Buffer().write(Res.readBytes("files/tutorial_sample.png")), options.fileSystem),
                mimeType = "image/png",
                dataSource = DataSource.DISK
            )
        }
    }
}
