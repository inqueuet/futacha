package com.valoser.futacha.shared.ui.image

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.Options
import coil3.size.Size
import coil3.size.pxOrElse
import com.valoser.futacha.shared.util.iosMemoryPerformanceProfile

private val sharedIosLowMemoryImageGate = LowMemoryImageRequestGate()
private object WebKitImageProcessingResource
private val IosPressureGate = Extras.Key<AdaptiveImageRequestGate?>(null)
private val IosImageAdmission = Extras.Key<IosImageProcessingAdmission?>(null)

internal actual fun platformImageMemoryPressureInterceptor(gate: AdaptiveImageRequestGate): Interceptor =
    Interceptor { chain ->
        chain.withRequest(chain.request.newBuilder().apply { extras[IosPressureGate] = gate }.build()).proceed()
    }

private class IosImageProcessingAdmission(
    val workload: ImageRequestWorkload,
    val lowRam: Boolean,
    val gate: LowMemoryImageRequestGate,
    val pressureGate: AdaptiveImageRequestGate?
)

/**
 * Coil 3.6 checks its memory cache and completes Fetcher.fetch before invoking
 * Decoder.decode. Carry the budget to that boundary instead of gating this chain.
 */
internal class IosLowMemoryImageInterceptor(
    private val isLowRam: () -> Boolean = { iosMemoryPerformanceProfile().isLowRam },
    private val gate: LowMemoryImageRequestGate = sharedIosLowMemoryImageGate
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val admission = IosImageProcessingAdmission(
            workload = classifyImageRequestWorkload(
                widthPx = chain.size.width.pxOrElse { 0 }.takeIf { it > 0 },
                heightPx = chain.size.height.pxOrElse { 0 }.takeIf { it > 0 },
                isOriginalSize = chain.size == Size.ORIGINAL,
                data = chain.request.data.toString()
            ),
            lowRam = isLowRam(),
            gate = gate,
            pressureGate = chain.request.getExtra(IosPressureGate)
        )
        val request = chain.request.newBuilder().apply {
            extras[IosImageAdmission] = admission
            // Coil prepends a per-request factory ahead of registered factories.
            // In particular, boundedOriginalDecoding must use the same budget.
            chain.request.decoderFactory?.let { factory ->
                if (factory !is IosAdmittedDecoderFactory) decoderFactory(IosAdmittedDecoderFactory(factory))
            }
        }.build()
        return chain.withRequest(request).proceed()
    }
}

internal class IosAdmittedDecoderFactory(private val delegate: Decoder.Factory) : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val decoder = delegate.create(result, options, imageLoader) ?: return null
        return Decoder { options.withIosImageProcessingPermit { decoder.decode() } }
    }
}

internal suspend fun <T> Options.withIosImageProcessingPermit(
    exclusiveWebKit: Boolean = false,
    block: suspend () -> T
): T {
    val admission = getExtra(IosImageAdmission)
    if (admission == null && !exclusiveWebKit) return block()
    // WebKit has one reusable view even on healthy/high-RAM devices. Admit that
    // resource atomically too: owning its separate queue while waiting for a
    // heavy permit would let an already queued prefetch block visible work.
    return (admission?.gate ?: sharedIosLowMemoryImageGate).withPermit(
        workload = admission?.workload ?: ImageRequestWorkload.VIDEO,
        prefetch = isImagePrefetchRequest() || videoThumbnailRequestPriority() == VideoThumbnailRequestPriority.PREFETCH,
        lowRam = admission?.lowRam ?: iosMemoryPerformanceProfile().isLowRam,
        pressureGate = admission?.pressureGate,
        exclusiveResource = if (exclusiveWebKit) WebKitImageProcessingResource else null,
        block = block
    )
}
