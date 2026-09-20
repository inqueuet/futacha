package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A detector owns one runtime and reusable buffers; the host owns the model store separately. */
internal class SensitiveDetector internal constructor(
    private val model: DetectorModel,
    private val gate: MediaFeatureGate,
    private val permit: MediaFeaturePermit,
    private val runtime: CpuInferenceSession,
    private val input: InferenceTensor,
    private val output: InferenceTensor
) : AutoCloseable {
    private val closed = MutableStateFlow(false)
    private val active = MutableStateFlow<Job?>(null)
    private val mutex = Mutex()
    private val pixels = FloatArray(model.edge * model.edge * 3)

    private fun checkAllowed() {
        require(permit.feature == MediaFeature.IMAGE_EDITOR || permit.feature == MediaFeature.VIDEO_EDITOR)
        if (closed.value || !gate.isCurrent(permit)) throw CancellationException("編集機能は終了しました")
    }

    suspend fun detect(frame: AnalysisFrame, threshold: Float, faces: Boolean, tiles: Boolean): List<Detection> = mutex.withLock {
        require(threshold.isFinite() && threshold in .05f.. .5f)
        checkAllowed()
        coroutineScope {
            val operation = currentCoroutineContext().job
            active.value = operation
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.permits(permit.feature).first { !gate.isCurrent(permit) }
                operation.cancel("編集機能は無効になりました")
            }
            try {
                checkAllowed()
                withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val windows = mutableListOf(DetectionWindow(0, 0, frame.width, frame.height))
                    if (tiles) {
                        val w = (frame.width * .65).toInt().coerceAtLeast(1)
                        val h = (frame.height * .65).toInt().coerceAtLeast(1)
                        for (y in intArrayOf(0, frame.height - h)) for (x in intArrayOf(0, frame.width - w)) {
                            windows += DetectionWindow(x, y, w, h)
                        }
                    }
                    val found = mutableListOf<Detection>()
                    for (window in windows.distinct()) {
                        context.ensureActive(); checkAllowed()
                        val transform = DetectionTransform(model, window, frame.width, frame.height)
                        prepareDetectionInput(frame, transform, pixels) { context.ensureActive(); checkAllowed() }
                        input.write(pixels)
                        runtime.run(mapOf("images" to input), mapOf("output0" to output))
                        context.ensureActive(); checkAllowed()
                        found += decodeDetections(output.read(), model, transform, threshold, faces) { context.ensureActive(); checkAllowed() }
                    }
                    suppressDetections(found, check = { context.ensureActive(); checkAllowed() })
                        .also { context.ensureActive(); checkAllowed() }
                }
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive(); checkAllowed(); throw failure
            } finally { watcher.cancel(); active.compareAndSet(operation, null) }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.value?.cancel()
        // CPU runtime pins in-flight tensors; destruction is deferred until its run exits.
        runtime.close(); output.close(); input.close()
    }

    companion object {
        /** Missing models stay missing. Only the explicit model-install UI may download them. */
        suspend fun open(model: DetectorModel, store: ModelStore, gate: MediaFeatureGate, permit: MediaFeaturePermit): SensitiveDetector {
            var undelivered: SensitiveDetector? = null
            try {
                return withContext(AppDispatchers.io) {
                    require(permit.feature == MediaFeature.IMAGE_EDITOR || permit.feature == MediaFeature.VIDEO_EDITOR)
                    if (!gate.isCurrent(permit)) throw CancellationException("編集機能は無効になりました")
                    val verified = checkNotNull(store.verified(model.artifact, permit)) { "検出モデルを先に導入してください" }
                    currentCoroutineContext().ensureActive()
                    if (!gate.isCurrent(permit)) throw CancellationException("編集機能は無効になりました")
                    val runtime = openCpuInferenceSession(verified.path.toString())
                    var input: InferenceTensor? = null
                    var output: InferenceTensor? = null
                    try {
                        check(runtime.inputNames == setOf("images") && runtime.outputNames == setOf("output0")) { "検出モデルの入出力が一致しません" }
                        input = createInferenceTensor(listOf(1, 3, model.edge.toLong(), model.edge.toLong()))
                        output = createInferenceTensor(listOf(1, (4 + model.classes).toLong(), model.candidates.toLong()))
                        SensitiveDetector(model, gate, permit, runtime, input, output).also { undelivered = it }
                    } catch (failure: Throwable) {
                        output?.close(); input?.close(); runtime.close(); throw failure
                    }
                }.also {
                    currentCoroutineContext().ensureActive()
                    it.checkAllowed()
                    undelivered = null
                }
            } finally { undelivered?.close() }
        }
    }
}
