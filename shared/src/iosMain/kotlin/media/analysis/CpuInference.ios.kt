@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.media.analysis

import cnames.structs.*
import com.valoser.futacha.shared.ort.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.posix.memcpy
import platform.posix.memset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Own public C API objects directly; never take pointers from Objective-C wrapper internals. */
private val ort: CPointer<OrtApi> by lazy {
    val base = requireNotNull(OrtGetApiBase())
    requireNotNull(base.pointed.GetApi!!(ORT_API_VERSION.toUInt()))
}
private fun ortCheck(status: CPointer<OrtStatus>?) {
    if (status == null) return
    try { error(ort.pointed.GetErrorMessage!!(status)?.toKString() ?: "推論に失敗しました") }
    finally { ort.pointed.ReleaseStatus!!(status) }
}
private inline fun <T : CPointed> ortCreate(create: (CPointer<CPointerVar<T>>) -> CPointer<OrtStatus>?): CPointer<T> = memScoped {
    val output = alloc<CPointerVar<T>>(); output.value = null
    ortCheck(create(output.ptr)); requireNotNull(output.value)
}

private data class NativeTensorData(val data: CPointer<FloatVar>, val value: CPointer<OrtValue>, val memory: CPointer<OrtMemoryInfo>)
private class NativeInferenceTensor(override val shape: List<Long>) : InferenceTensor {
    override val size = inferenceTensorSize(shape)
    val resource: InferenceResource<NativeTensorData>
    init {
        val memory = ortCreate<OrtMemoryInfo> { ort.pointed.CreateCpuMemoryInfo!!(OrtArenaAllocator, OrtMemTypeDefault, it) }
        val data = try { nativeHeap.allocArray<FloatVar>(size) } catch (failure: Throwable) { ort.pointed.ReleaseMemoryInfo!!(memory); throw failure }
        try {
            memset(data, 0, size.toULong() * 4u)
            val value = shape.toLongArray().usePinned { dims ->
                ortCreate<OrtValue> { ort.pointed.CreateTensorWithDataAsOrtValue!!(memory, data, size.toULong() * 4u,
                    dims.addressOf(0), shape.size.toULong(), ONNXTensorElementDataType.ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, it) }
            }
            resource = InferenceResource(NativeTensorData(data, value, memory)) {
                ort.pointed.ReleaseValue!!(it.value); nativeHeap.free(it.data); ort.pointed.ReleaseMemoryInfo!!(it.memory)
            }
        } catch (failure: Throwable) { nativeHeap.free(data); ort.pointed.ReleaseMemoryInfo!!(memory); throw failure }
    }
    override fun write(values: FloatArray) {
        require(values.size == size)
        writeSlice(values, 0)
    }
    override fun writeSlice(values: FloatArray, offset: Int) {
        validateSlice(values, offset)
        resource.retain().use { pin ->
            if (values.isNotEmpty()) values.usePinned { memcpy(pin.value.data + offset, it.addressOf(0), values.size.toULong() * 4u) }
        }
    }
    override fun read(): FloatArray = resource.retain().use { pin ->
        FloatArray(size).also { output -> output.usePinned { memcpy(it.addressOf(0), pin.value.data, size.toULong() * 4u) } }
    }
    override fun close() = resource.close()
}
internal actual fun createInferenceTensor(shape: List<Long>): InferenceTensor = NativeInferenceTensor(shape.toList())

private data class NativeSessionData(val env: CPointer<OrtEnv>, val session: CPointer<OrtSession>)
private fun releaseSession(data: NativeSessionData) { ort.pointed.ReleaseSession!!(data.session); ort.pointed.ReleaseEnv!!(data.env) }

internal actual fun openCpuInferenceSession(path: String): CpuInferenceSession = memScoped {
    val env = ortCreate<OrtEnv> { ort.pointed.CreateEnv!!(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING, "futacha-offline-editing".cstr.ptr, it) }
    var session: CPointer<OrtSession>? = null
    try {
        ortCheck(ort.pointed.DisableTelemetryEvents!!(env))
        val options = ortCreate<OrtSessionOptions> { ort.pointed.CreateSessionOptions!!(it) }
        try {
            ortCheck(ort.pointed.SetIntraOpNumThreads!!(options, 2)); ortCheck(ort.pointed.SetInterOpNumThreads!!(options, 1))
            ortCheck(ort.pointed.SetSessionExecutionMode!!(options, ORT_SEQUENTIAL))
            for (key in listOf("session.intra_op.allow_spinning", "session.inter_op.allow_spinning")) {
                ortCheck(ort.pointed.AddSessionConfigEntry!!(options, key.cstr.ptr, "0".cstr.ptr))
            }
            session = ortCreate<OrtSession> { ort.pointed.CreateSession!!(env, path.cstr.ptr, options, it) }
        } finally { ort.pointed.ReleaseSessionOptions!!(options) }
        val data = NativeSessionData(env, requireNotNull(session))
        NativeCpuSession(data, sessionNames(data.session, true), sessionNames(data.session, false))
    } catch (failure: Throwable) { session?.let { ort.pointed.ReleaseSession!!(it) }; ort.pointed.ReleaseEnv!!(env); throw failure }
}

private fun sessionNames(session: CPointer<OrtSession>, inputs: Boolean): Set<String> = memScoped {
    val count = alloc<ULongVar>()
    ortCheck(if (inputs) ort.pointed.SessionGetInputCount!!(session, count.ptr) else ort.pointed.SessionGetOutputCount!!(session, count.ptr))
    require(count.value in 1uL..32uL)
    val allocator = ortCreate<OrtAllocator> { ort.pointed.GetAllocatorWithDefaultOptions!!(it) }
    (0uL until count.value).map { index ->
        val name = alloc<CPointerVar<ByteVar>>(); name.value = null
        ortCheck(if (inputs) ort.pointed.SessionGetInputName!!(session, index, allocator, name.ptr)
            else ort.pointed.SessionGetOutputName!!(session, index, allocator, name.ptr))
        try { requireNotNull(name.value).toKString() } finally { allocator.pointed.Free!!(allocator, name.value) }
    }.toSet().also { require(it.size.toULong() == count.value) }
}

private class NativeCpuSession(
    data: NativeSessionData,
    override val inputNames: Set<String>,
    override val outputNames: Set<String>
) : CpuInferenceSession {
    private val resource = InferenceResource(data, ::releaseSession)
    private val mutex = Mutex()
    private val active = MutableStateFlow<InferenceResource<CPointer<OrtRunOptions>>?>(null)

    override suspend fun run(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>, onRunning: () -> Unit) = mutex.withLock {
        validateRun(inputs, outputs)
        withContext(AppDispatchers.io) {
            resource.retain().use { session ->
                val pins = mutableListOf<InferenceLease<NativeTensorData>>()
                try {
                    fun borrow(tensor: InferenceTensor): CPointer<OrtValue> =
                        (tensor as NativeInferenceTensor).resource.retain().also { pins += it }.value.value
                    val inValues = inputs.values.map(::borrow)
                    val outValues = outputs.values.map(::borrow)
                    val options = InferenceResource(ortCreate<OrtRunOptions> { ort.pointed.CreateRunOptions!!(it) }) { ort.pointed.ReleaseRunOptions!!(it) }
                    active.value = options
                    try {
                        suspendCancellableCoroutine { continuation ->
                            continuation.invokeOnCancellation { terminate(options) }
                            try {
                                if (resource.isClosed) throw CancellationException("推論セッションは終了しました")
                                if (continuation.isActive) options.retain().use { run ->
                                    memScoped {
                                        val inNames = allocArray<CPointerVar<ByteVar>>(inputs.size)
                                        val outNames = allocArray<CPointerVar<ByteVar>>(outputs.size)
                                        val inPointers = allocArray<CPointerVar<OrtValue>>(inputs.size)
                                        val outPointers = allocArray<CPointerVar<OrtValue>>(outputs.size)
                                        inputs.keys.forEachIndexed { i, name -> inNames[i] = name.cstr.ptr; inPointers[i] = inValues[i] }
                                        outputs.keys.forEachIndexed { i, name -> outNames[i] = name.cstr.ptr; outPointers[i] = outValues[i] }
                                        try {
                                            onRunning()
                                            ortCheck(ort.pointed.Run!!(session.value.session, run.value, inNames, inPointers, inputs.size.toULong(),
                                                outNames, outputs.size.toULong(), outPointers))
                                            check(outValues.indices.all { outPointers[it] == outValues[it] }) { "推論の出力バッファを再利用できません" }
                                        } finally {
                                            outValues.indices.forEach { if (outPointers[it] != outValues[it]) outPointers[it]?.let { value -> ort.pointed.ReleaseValue!!(value) } }
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
    private fun terminate(options: InferenceResource<CPointer<OrtRunOptions>>) {
        options.tryRetain()?.use { pin -> ort.pointed.RunOptionsSetTerminate!!(pin.value)?.let { ort.pointed.ReleaseStatus!!(it) } }
    }
    override fun close() {
        resource.close()
        active.value?.let(::terminate)
    }
}
