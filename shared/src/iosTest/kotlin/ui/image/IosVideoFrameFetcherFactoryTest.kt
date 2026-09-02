package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.Options
import coil3.toUri
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IosVideoFrameFetcherFactoryTest {
    @Test
    fun coilUriMappingStillRoutesSupportedVideosToTheThumbnailFetcher() {
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val options = Options(context = PlatformContext.INSTANCE)
        try {
            assertNotNull(
                IosVideoFrameFetcher.Factory().create(
                    data = "https://example.test/video.webm?cache=1".toUri(),
                    options = options,
                    imageLoader = imageLoader
                )
            )
            assertNotNull(
                IosVideoFrameFetcher.Factory().create(
                    data = "file:///tmp/video.mp4".toUri(),
                    options = options,
                    imageLoader = imageLoader
                )
            )
            assertNull(
                IosVideoFrameFetcher.Factory().create(
                    data = "https://example.test/image.jpg".toUri(),
                    options = options,
                    imageLoader = imageLoader
                )
            )
        } finally {
            imageLoader.shutdown()
        }
    }
}
