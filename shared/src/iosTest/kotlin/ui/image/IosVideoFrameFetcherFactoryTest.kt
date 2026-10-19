package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.Options
import coil3.toUri
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import coil3.decode.DataSource
import platform.Foundation.NSURL
import com.valoser.futacha.shared.util.IOS_WEBM_PREVIEW_UNAVAILABLE

class IosVideoFrameFetcherFactoryTest {
    @Test
    fun unsupportedWebmFailsBeforeStartingTheTenSecondWebKitAttempt(): Unit = runBlocking {
        withTimeout(5_000) {
            val fetcher = IosVideoFrameFetcher(
                NSURL.fileURLWithPath("/nonexistent/ios15-unsupported-webm.webm"),
                Options(context = PlatformContext.INSTANCE), DataSource.DISK,
                webmPlaybackAvailable = { false }
            )
            val error = assertFailsWith<IllegalStateException> { fetcher.fetch() }
            assertEquals(IOS_WEBM_PREVIEW_UNAVAILABLE, error.message)
        }
    }

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
