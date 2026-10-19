package com.valoser.futacha.shared.ui.image

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Size
import coil3.size.pxOrElse
import com.valoser.futacha.shared.util.iosMemoryPerformanceProfile

internal object IosLowMemoryImageInterceptor : Interceptor {
    private val gate = LowMemoryImageRequestGate()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (!iosMemoryPerformanceProfile().isLowRam) return chain.proceed()
        val workload = classifyImageRequestWorkload(
            widthPx = chain.size.width.pxOrElse { 0 }.takeIf { it > 0 },
            heightPx = chain.size.height.pxOrElse { 0 }.takeIf { it > 0 },
            isOriginalSize = chain.size == Size.ORIGINAL,
            data = chain.request.data.toString()
        )
        return gate.withPermit(workload) { chain.proceed() }
    }
}
