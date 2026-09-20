package com.valoser.futacha.shared.media.edit

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.*
import coil3.size.Precision
import coil3.size.Scale
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.PreservedImageMetadata
import com.valoser.futacha.shared.media.prompt.preserveEditedImageMetadata
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.random.Random

/** A device image selected explicitly by the user, never a board URL. */
internal data class ImageEditInput(val image: ImageData)

internal class ImageEditSession private constructor(
    val original: EditRaster,
    private val metadata: PreservedImageMetadata,
    private val gate: MediaFeatureGate,
    private val permit: MediaFeaturePermit
) : AutoCloseable {
    private val closed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasPartialMetadata: Boolean get() = metadata.partial
    fun checkActive() { check(!closed.value && gate.isCurrent(permit)) { "画像編集は無効になりました" } }
    suspend fun export(document: ImageEditDocument): ImageData = withContext(Dispatchers.Default) {
        checkActive()
        document.validated().requireExportReady()
        val rendered = renderImageEdit(original, document)
        checkActive()
        val bytes = preserveEditedImageMetadata(encodeImageEditJpeg(rendered), metadata, rendered.width, rendered.height)
        currentCoroutineContext().ensureActive(); checkActive()
        check(bytes.size <= 16 * 1024 * 1024) { "編集結果が大きすぎます" }
        ImageData(bytes, "edited-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(0, Int.MAX_VALUE).toString(36)}.jpg")
    }
    override fun close() { closed.value = true }

    companion object {
        suspend fun open(
            input: ImageEditInput,
            gate: MediaFeatureGate,
            loader: ImageLoader,
            context: PlatformContext
        ): ImageEditSession {
            val permit = requireNotNull(gate.permit(MediaFeature.IMAGE_EDITOR)) { "画像編集は無効です" }
            validateEditableImage(input.image.bytes)
            check(gate.isCurrent(permit)) { "画像編集は無効になりました" }
            val request = configureImageEditDecode(ImageRequest.Builder(context))
                // Downsample large inputs without enlarging small device images.
                .data(input.image.bytes).size(IMAGE_EDIT_MAX_EDGE).scale(Scale.FIT).precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.DISABLED).build()
            val result = loader.execute(request)
            if (result is ErrorResult) throw result.throwable
            val image = (result as SuccessResult).image
            require(image is BitmapImage) { "静止画像を選んでください" }
            val (raster, metadata) = withContext(Dispatchers.Default) {
                imageEditPixels(image) to PreservedImageMetadata.scan(input.image.bytes)
            }
            currentCoroutineContext().ensureActive()
            check(gate.isCurrent(permit)) { "画像編集は無効になりました" }
            return ImageEditSession(raster, metadata, gate, permit)
        }
    }
}
