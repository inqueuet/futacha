package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.Dispatchers

private const val MAX_IMAGE_LOADER_PARALLELISM = 3

val LocalFutachaImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("FutachaImageLoader is not provided")
}

@Composable
fun rememberFutachaImageLoader(
    maxParallelism: Int = MAX_IMAGE_LOADER_PARALLELISM
): ImageLoader {
    val platformContext = LocalPlatformContext.current
    val fetcherDispatcher = remember(maxParallelism) {
        Dispatchers.IO.limitedParallelism(maxParallelism.coerceAtLeast(1))
    }
    return remember(platformContext, fetcherDispatcher) {
        ImageLoader.Builder(platformContext)
            .fetcherCoroutineContext(fetcherDispatcher)
            .decoderCoroutineContext(fetcherDispatcher)
            .build()
    }
}
