package com.valoser.futacha.shared.media.analysis

import ai.onnxruntime.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private object CpuOrtEnvironment {
    // No reference to this object is made when the optional editors are disabled.
    val value: OrtEnvironment = OrtEnvironment.getEnvironment("futacha-offline-editing").apply { setTelemetry(false) }
}

internal actual fun createInferenceTensor(shape: List<Long>): InferenceTensor = JvmInferenceTensor(shape.toList())

private data class JvmTensorData(val buffer: FloatBuffer, val tensor: OnnxTensor)
private class JvmInferenceTensor(override val shape: List<Long>) : InferenceTensor {
    override val size = inferenceTensorSize(shape)
    val resource: InferenceResource<JvmTensorData>
    init {
        val buffer = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val tensor = OnnxTensor.createTensor(CpuOrtEnvironment.value, buffer, shape.toLongArray())
        resource = InferenceResource(JvmTensorData(buffer, tensor)) { it.tensor.close() }
    }
    override fun write(values: FloatArray) {
        require(values.size == size)
        writeSlice(values, 0)
    }
    override fun writeSlice(values: FloatArray, offset: Int) {
        validateSlice(values, offset)
        resource.retain().use { it.value.buffer.duplicate().apply { position(offset); put(values) } }
    }
    override fun read(): FloatArray = resource.retain().use { pin ->
        FloatArray(size).also { pin.value.buffer.duplicate().apply { position(0); get(it) } }
    }
    override fun close() = resource.close()
}

internal actual fun openCpuInferenceSession(path: String): CpuInferenceSession {
    val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        options.addConfigEntry("session.intra_op.allow_spinning", "0")
        options.addConfigEntry("session.inter_op.allow_spinning", "0")
        CpuOrtEnvironment.value.createSession(path, options)
    }
    try { return JvmCpuSession(session, session.inputNames.toSet(), session.outputNames.toSet()) }
    catch (failure: Throwable) { session.close(); throw failure }
}

private class JvmCpuSession(
    session: OrtSession,
    override val inputNames: Set<String>,
    override val outputNames: Set<String>
) : CpuInferenceSession {
    private val resource = InferenceResource(session) { it.close() }
    private val mutex = Mutex()
    private val active = MutableStateFlow<InferenceResource<OrtSession.RunOptions>?>(null)

    override suspend fun run(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>, onRunning: () -> Unit) = mutex.withLock {
        validateRun(inputs, outputs)
        withContext(AppDispatchers.io) {
            resource.retain().use { session ->
                val pins = mutableListOf<InferenceLease<JvmTensorData>>()
                try {
                    fun borrow(tensor: InferenceTensor): OnnxTensor =
                        (tensor as JvmInferenceTensor).resource.retain().also { pins += it }.value.tensor
                    val nativeInputs = inputs.mapValues { borrow(it.value) }
                    val nativeOutputs = outputs.mapValues { borrow(it.value) }
                    val options = InferenceResource(OrtSession.RunOptions()) { it.close() }
                    active.value = options
                    try {
                        suspendCancellableCoroutine { continuation ->
                            continuation.invokeOnCancellation { terminate(options) }
                            try {
                                if (resource.isClosed) throw CancellationException("推論セッションは終了しました")
                                if (continuation.isActive) {
                                    options.retain().use { run ->
                                        onRunning()
                                        session.value.run(nativeInputs, emptySet(), nativeOutputs, run.value).use { result ->
                                            check((0 until result.size()).none { result.isResultOwner(it) }) { "推論の出力バッファを再利用できません" }
                                        }
                                    }
                                }
                                if (resource.isClosed) throw CancellationException("推論セッションは終了しました")
                                continuation.resume(Unit)
                            } catch (failure: Throwable) {
                                continuation.resumeWithException(if (resource.isClosed) CancellationException("推論セッションは終了しました", failure) else failure)
                            }
                        }
                    } finally { active.compareAndSet(options, null); options.close() }
                } finally { pins.asReversed().forEach { it.close() } }
            }
        }
    }
    private fun terminate(options: InferenceResource<OrtSession.RunOptions>) {
        options.tryRetain()?.use { it.value.setTerminate(true) }
    }
    override fun close() {
        resource.close()
        active.value?.let(::terminate)
    }
}
