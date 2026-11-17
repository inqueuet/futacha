package com.valoser.futacha.shared.media.analysis

/** Reusable FLOAT CPU buffer. Owned by an analysis session, never the UI or renderer. */
internal interface InferenceTensor : AutoCloseable {
    val shape: List<Long>
    val size: Int
    fun write(values: FloatArray)
    /** Row writes avoid allocating a second full-size RGB float buffer. */
    fun writeSlice(values: FloatArray, offset: Int)
    fun read(): FloatArray
}

internal interface CpuInferenceSession : AutoCloseable {
    val inputNames: Set<String>
    val outputNames: Set<String>
    /** One run per session at a time. Cancellation returns only after native execution ends.
     * [onRunning] reports the inference phase from the worker after all buffers are retained. */
    suspend fun run(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>, onRunning: () -> Unit = {})
}

internal expect fun createInferenceTensor(shape: List<Long>): InferenceTensor
/** Blocking creation: caller must use a worker dispatcher and close an undelivered session. */
internal expect fun openCpuInferenceSession(path: String): CpuInferenceSession

internal fun inferenceTensorSize(shape: List<Long>): Int {
    require(shape.size in 1..8)
    var size = 1L
    for (dimension in shape) {
        require(dimension > 0 && dimension <= MAX_TENSOR_ELEMENTS / size) { "推論テンソルのサイズが不正です" }
        size *= dimension
    }
    return size.toInt()
}
internal fun CpuInferenceSession.validateRun(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>) {
    require(inputs.keys == inputNames) { "モデルの入力名が一致しません" }
    require(outputs.isNotEmpty() && outputs.keys.all { it in outputNames }) { "モデルの出力名が一致しません" }
    require(inputs.values.none { input -> outputs.values.any { it === input } }) { "推論の入出力バッファは分離してください" }
}
private const val MAX_TENSOR_ELEMENTS = 16L * 1024 * 1024

internal fun InferenceTensor.validateSlice(values: FloatArray, offset: Int) {
    require(offset >= 0 && offset <= size && values.size <= size - offset)
}
