package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import kotlinx.coroutines.*
import kotlin.test.*

class SensitiveDetectorTest {
    private val enabled = MediaFeatureSettings(imageEditorEnabled = true, videoEditorEnabled = true)
    private val frame = AnalysisFrame(0, 64, 32, ByteArray(64 * 32 * 3) { 127 })
    private class Tensor(override val shape: List<Long>) : InferenceTensor {
        override val size = inferenceTensorSize(shape)
        var data = FloatArray(size)
        var closed = 0
        override fun write(values: FloatArray) { check(closed == 0); data = values.copyOf() }
        override fun writeSlice(values: FloatArray, offset: Int) { check(closed == 0); validateSlice(values, offset); values.copyInto(data, offset) }
        override fun read(): FloatArray { check(closed == 0); return data.copyOf() }
        override fun close() { closed++ }
    }
    private class Runtime(val execute: suspend (Int) -> Unit) : CpuInferenceSession {
        override val inputNames = setOf("images")
        override val outputNames = setOf("output0")
        var calls = 0
        var closed = 0
        override suspend fun run(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>, onRunning: () -> Unit) {
            onRunning(); calls++; execute(calls)
            val out = FloatArray(22 * 2100)
            out[0] = 160f; out[2100] = 80f; out[4200] = 80f; out[6300] = 40f
            out[8 * 2100] = .8f
            outputs.getValue("output0").write(out)
        }
        override fun close() { closed++ }
    }
    private inner class Fixture(feature: MediaFeature = MediaFeature.IMAGE_EDITOR, execute: suspend (Int) -> Unit = {}) : AutoCloseable {
        val gate = MediaFeatureGate().apply { update(enabled) }
        val input = Tensor(listOf(1, 3, 320, 320)); val output = Tensor(listOf(1, 22, 2100))
        val runtime = Runtime(execute)
        val detector = SensitiveDetector(DetectorModel.REAL, gate, gate.permit(feature)!!, runtime, input, output)
        override fun close() = detector.close()
    }

    @Test fun onePassAndTiledPassesReuseSessionAndMapResultsToFrame() = runBlocking {
        Fixture().use { f ->
            val single = f.detector.detect(frame, .15f, false, false).single()
            assertEquals(.5f, single.bounds.centerX); assertEquals(.5f, single.bounds.centerY)
            assertEquals(.25f, single.bounds.width); assertEquals(.25f, single.bounds.height)
            assertEquals(1, f.runtime.calls)
            f.detector.detect(frame, .15f, false, true)
            assertEquals(6, f.runtime.calls)
            f.detector.close(); f.detector.close()
            assertEquals(1, f.runtime.closed); assertEquals(1, f.input.closed); assertEquals(1, f.output.closed)
        }
    }

    @Test fun ownFeatureOffInterruptsInferenceAndOldSessionCannotRunAfterReenable() = runBlocking {
        val started = CompletableDeferred<Unit>(); val stopped = CompletableDeferred<Unit>()
        Fixture(execute = { started.complete(Unit); try { awaitCancellation() } finally { stopped.complete(Unit) } }).use { f ->
            val task = async { f.detector.detect(frame, .15f, false, false) }
            withTimeout(5_000) { started.await() }
            f.gate.update(enabled.copy(imageEditorEnabled = false))
            withTimeout(5_000) { task.join(); stopped.await() }
            assertTrue(task.isCancelled)
            f.gate.update(enabled)
            assertFailsWith<CancellationException> { f.detector.detect(frame, .15f, false, false) }
            assertEquals(1, f.runtime.calls)
        }
    }

    @Test fun otherFeatureOffLeavesInferenceRunningAndManualCloseCancelsIt() = runBlocking {
        val started = CompletableDeferred<Unit>()
        Fixture(MediaFeature.VIDEO_EDITOR) { started.complete(Unit); awaitCancellation() }.use { f ->
            val task = async { f.detector.detect(frame, .15f, false, false) }
            withTimeout(5_000) { started.await() }
            f.gate.update(enabled.copy(imageEditorEnabled = false))
            yield(); assertTrue(task.isActive)
            f.detector.close()
            withTimeout(5_000) { task.join() }
            assertTrue(task.isCancelled)
        }
    }
}
