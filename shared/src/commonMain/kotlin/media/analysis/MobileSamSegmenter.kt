package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.model.MosaicBounds
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Box-prompted segmentation. One encoder run serves every selected box in a frame. */
internal class MobileSamSegmenter private constructor(
    private val gate: MediaFeatureGate, private val permit: MediaFeaturePermit,
    private val encoder: CpuInferenceSession, private val decoder: CpuInferenceSession,
    private val inputs: Map<String, InferenceTensor>, private val outputs: Map<String, InferenceTensor>,
    private val tensorFactory: (List<Long>) -> InferenceTensor
) : AutoCloseable {
    private val closed = MutableStateFlow(false)
    private val active = MutableStateFlow<Job?>(null)
    private val mutex = Mutex()
    private fun checkAllowed() {
        if (closed.value || !gate.isCurrent(permit)) throw CancellationException("編集機能は終了しました")
    }

    suspend fun segment(frame: AnalysisFrame, bounds: List<MosaicBounds>,
        progress: (String, Float?) -> Unit = { _, _ -> }): List<ContourResult> = mutex.withLock {
        require(bounds.size in 1..16)
        bounds.forEach { it.validateContourBounds() }
        checkAllowed()
        coroutineScope {
            val operation = currentCoroutineContext().job
            active.value = operation
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.permits(permit.feature).first { !gate.isCurrent(permit) }
                operation.cancel("編集機能は無効になりました")
            }
            try {
                withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    fun check() { context.ensureActive(); checkAllowed() }
                    check()
                    val (width, height) = mobileSamDimensions(frame)
                    progress("輪郭用に画像を解析しています", null)
                    tensorFactory(listOf(height.toLong(), width.toLong(), 3L)).use { image ->
                        prepareMobileSamInput(frame, width, height, image, ::check)
                        inputs.getValue("orig_im_size").write(floatArrayOf(height.toFloat(), width.toFloat()))
                        encoder.run(mapOf("input_image" to image), mapOf("image_embeddings" to inputs.getValue("image_embeddings")))
                    }
                    check()
                    bounds.mapIndexed { index, box ->
                        check()
                        progress("輪郭を抽出しています（${index + 1}/${bounds.size}）", index.toFloat() / bounds.size)
                        val left = box.centerX - box.width / 2; val top = box.centerY - box.height / 2
                        inputs.getValue("point_coords").write(floatArrayOf(left * width, top * height,
                            (left + box.width) * width, (top + box.height) * height))
                        decoder.run(inputs, outputs)
                        check()
                        selectMobileSamMask(box, width, height, outputs.getValue("iou_predictions").read(),
                            outputs.getValue("low_res_masks").read(), ::check)
                    }.also { check() }
                }
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive(); checkAllowed(); throw failure
            } finally { watcher.cancel(); active.compareAndSet(operation, null) }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.value?.cancel()
        encoder.close(); decoder.close()
        (inputs.values + outputs.values).forEach { it.close() }
    }

    companion object {
        suspend fun open(store: ModelStore, gate: MediaFeatureGate, permit: MediaFeaturePermit): MobileSamSegmenter {
            var undelivered: MobileSamSegmenter? = null
            try {
                return withContext(AppDispatchers.io) {
                    require(permit.feature == MediaFeature.IMAGE_EDITOR || permit.feature == MediaFeature.VIDEO_EDITOR)
                    if (!gate.isCurrent(permit)) throw CancellationException("編集機能は無効になりました")
                    val encoderFile = checkNotNull(store.verified(AnalysisModel.MOBILE_SAM_ENCODER, permit)) { "モデル画面で輪郭用の2モデルを導入してください" }
                    val decoderFile = checkNotNull(store.verified(AnalysisModel.MOBILE_SAM_DECODER, permit)) { "モデル画面で輪郭用の2モデルを導入してください" }
                    currentCoroutineContext().ensureActive()
                    if (!gate.isCurrent(permit)) throw CancellationException("編集機能は無効になりました")
                    val encoder = openCpuInferenceSession(encoderFile.path.toString())
                    val decoder = try { openCpuInferenceSession(decoderFile.path.toString()) }
                    catch (failure: Throwable) { encoder.close(); throw failure }
                    create(gate, permit, encoder, decoder).also { undelivered = it }
                }.also { currentCoroutineContext().ensureActive(); it.checkAllowed(); undelivered = null }
            } finally { undelivered?.close() }
        }

        internal fun create(gate: MediaFeatureGate, permit: MediaFeaturePermit, encoder: CpuInferenceSession,
            decoder: CpuInferenceSession, factory: (List<Long>) -> InferenceTensor = ::createInferenceTensor): MobileSamSegmenter {
            val allocated = mutableListOf<InferenceTensor>()
            fun tensor(vararg shape: Long) = factory(shape.toList()).also { allocated += it }
            try {
                require(permit.feature == MediaFeature.IMAGE_EDITOR || permit.feature == MediaFeature.VIDEO_EDITOR)
                check(encoder.inputNames == setOf("input_image") && "image_embeddings" in encoder.outputNames)
                check(decoder.inputNames == setOf("image_embeddings", "point_coords", "point_labels", "mask_input", "has_mask_input", "orig_im_size"))
                check(decoder.outputNames.containsAll(listOf("low_res_masks", "iou_predictions")))
                val inputs = mapOf("image_embeddings" to tensor(1, 256, 64, 64), "point_coords" to tensor(1, 2, 2),
                    "point_labels" to tensor(1, 2).apply { write(floatArrayOf(2f, 3f)) },
                    "mask_input" to tensor(1, 1, 256, 256).apply { write(FloatArray(256 * 256)) },
                    "has_mask_input" to tensor(1).apply { write(floatArrayOf(0f)) }, "orig_im_size" to tensor(2))
                val outputs = mapOf("low_res_masks" to tensor(1, 4, 256, 256), "iou_predictions" to tensor(1, 4))
                return MobileSamSegmenter(gate, permit, encoder, decoder, inputs, outputs, factory)
            } catch (failure: Throwable) {
                allocated.asReversed().forEach { it.close() }; decoder.close(); encoder.close(); throw failure
            }
        }
    }
}
