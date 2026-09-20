@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha.shared.media.analysis

import kotlinx.coroutines.*
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.edit.*
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

/** The same native ORT contract runs against the desktop JNI and iOS C API bindings.
 * Android host tests intentionally exclude it: an Android JNI library cannot run on macOS. */
internal class CpuInferenceContract(private val directory: okio.Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY) {
    private suspend fun withModel(bytes: ByteArray, test: suspend (CpuInferenceSession) -> Unit) {
        val file = directory.resolve("inference-test-${Random.nextLong()}.onnx")
        FileSystem.SYSTEM.write(file) { write(bytes) }
        try { openCpuInferenceSession(file.toString()).use { test(it) } }
        finally { FileSystem.SYSTEM.delete(file) }
    }

    fun nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests() = runBlocking {
        withModel(InferenceFixtures.twice()) { session ->
            assertEquals(setOf("x"), session.inputNames); assertEquals(setOf("y"), session.outputNames)
            createInferenceTensor(listOf(2, 3)).use { input ->
                createInferenceTensor(listOf(2, 3)).use { output ->
                    assertFailsWith<IllegalArgumentException> { input.write(floatArrayOf(1f)) }
                    assertFailsWith<IllegalArgumentException> { input.writeSlice(floatArrayOf(1f), -1) }
                    assertFailsWith<IllegalArgumentException> { input.writeSlice(floatArrayOf(1f), Int.MAX_VALUE) }
                    assertFailsWith<IllegalArgumentException> { input.writeSlice(floatArrayOf(1f, 2f), 5) }
                    input.write(FloatArray(6))
                    input.writeSlice(floatArrayOf(3f, 4f), 2)
                    input.writeSlice(floatArrayOf(7f), 5)
                    input.writeSlice(floatArrayOf(), 6)
                    assertContentEquals(floatArrayOf(0f, 0f, 3f, 4f, 0f, 7f), input.read())
                    assertFailsWith<IllegalArgumentException> { session.run(mapOf("wrong" to input), mapOf("y" to output)) }
                    assertFailsWith<IllegalArgumentException> { session.run(mapOf("x" to input), mapOf("wrong" to output)) }
                    assertFailsWith<IllegalArgumentException> { session.run(mapOf("x" to input), mapOf("y" to input)) }
                    createInferenceTensor(listOf(3, 2)).use { bad ->
                        assertFails { session.run(mapOf("x" to bad), mapOf("y" to output)) }
                        assertFails { session.run(mapOf("x" to input), mapOf("y" to bad)) }
                    }
                    repeat(3) { iteration ->
                        val data = FloatArray(6) { (it - 2f) * (iteration + 1) }
                        input.write(data)
                        session.run(mapOf("x" to input), mapOf("y" to output))
                        assertContentEquals(data.map { it * 2 }.toFloatArray(), output.read())
                        assertContentEquals(data, input.read())
                    }
                }
            }
        }
    }

    fun cancellationInterruptsNativeExecutionAndSameSessionRunsAgain() = runBlocking {
        withTimeout(30_000) {
            withHeavyModel { session, inputs, outputs ->
                val started = CompletableDeferred<Unit>()
                val task = launch { session.run(inputs, outputs) { started.complete(Unit) } }
                started.await(); delay(20)
                assertFalse(task.isCompleted, "Fixture must still be executing when cancellation is requested")
                withTimeout(5_000) { task.cancelAndJoin() }
                assertTrue(task.isCancelled)
                session.run(inputs, outputs)
                assertTrue(outputs.getValue("out").read().all { it == 0f })
            }
        }
    }

    fun closingSessionAndTensorsDuringNativeExecutionDefersTheirRelease() = runBlocking {
        withTimeout(30_000) {
            withHeavyModel { session, inputs, outputs ->
                val started = CompletableDeferred<Unit>()
                val task = launch { session.run(inputs, outputs) { started.complete(Unit) } }
                started.await(); delay(20)
                assertFalse(task.isCompleted)
                // ORT still owns borrowed references until the run returns.
                inputs.values.forEach { it.close(); it.close() }
                outputs.values.forEach { it.close(); it.close() }
                session.close(); session.close()
                withTimeout(5_000) { task.join() }
                assertTrue(task.isCancelled)
                assertFailsWith<IllegalStateException> { inputs.getValue("a").read() }
            }
            // Creation and inference must still work after the previous session was interrupted.
            withModel(InferenceFixtures.twice()) { session ->
                createInferenceTensor(listOf(2, 3)).use { input ->
                    createInferenceTensor(listOf(2, 3)).use { output ->
                        input.write(FloatArray(6) { 7f })
                        session.run(mapOf("x" to input), mapOf("y" to output))
                        assertContentEquals(FloatArray(6) { 14f }, output.read())
                    }
                }
            }
        }
    }

    fun invalidModelDoesNotPoisonSubsequentSessionCreation() = runBlocking {
        var accepted = false
        assertFails { withModel(byteArrayOf(1, 2, 3)) { accepted = true } }
        assertFalse(accepted, "Malformed models must fail while opening the native session")
        withModel(InferenceFixtures.twice()) { assertEquals(setOf("x"), it.inputNames) }
    }

    fun genitalDetectionsCoverTheExpectedPixelsAfterNativeInference() = runBlocking {
        val data = InferenceFixtures.detector()
        val hash = data.toByteString().sha256().hex()
        val spec = AnalysisModelSpec(AnalysisModel.NUDE_NET, "fixture", "fixture", "https://model.test", data.size.toLong(), hash,
            ModelDistribution("https://model.test/detector.onnx", data.size.toLong(), hash))
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        val permit = gate.permit(MediaFeature.IMAGE_EDITOR)!!
        val path = directory.resolve("detector-pixels-${Random.nextLong()}")
        val store = ModelStore(gate, directory = { path }, downloader = { ModelDownloader { _, _ -> error("Unexpected network") } }, models = listOf(spec))
        try {
            store.import(spec.id, permit) { Buffer().write(data) }
            val original = EditRaster(80, 48, IntArray(80 * 48) { if (it % 80 < 40) 0xffff0000.toInt() else 0xff00ff00.toInt() })
            val candidates = ImageSensitiveAnalyser.detect(original, DetectionSettings(), store, gate, permit) { _, _ -> }
            assertEquals(listOf("男性器候補", "女性器候補"), candidates.map { it.label })
            val female = candidates.last().bounds
            assertEquals(.5f, female.centerX); assertEquals(.5f, female.centerY)
            val document = ImageEditDocument().withDetections(candidates)
            assertTrue(document.needsReview)
            val edited = renderImageEdit(original, document.copy(regions = document.regions.map {
                if (it.label == "女性器候補") it.copy(style = EditStyle.BLACK) else it
            }))
            assertEquals(0xff000000.toInt(), edited.argb[24 * 80 + 40])
            assertEquals(0xffff0000.toInt(), edited.argb[5 * 80 + 5])
            assertEquals(0xff00ff00.toInt(), original.argb[24 * 80 + 40])
        } finally { store.closeAndAwait(); FileSystem.SYSTEM.deleteRecursively(path, mustExist = false) }
    }

    private suspend fun withHeavyModel(test: suspend (CpuInferenceSession, Map<String, InferenceTensor>, Map<String, InferenceTensor>) -> Unit) {
        withModel(InferenceFixtures.cancel()) { session ->
            createInferenceTensor(listOf(1024, 1024)).use { a ->
                createInferenceTensor(listOf(1024, 1024)).use { b ->
                    createInferenceTensor(listOf(1024, 1024)).use { output ->
                        test(session, mapOf("a" to a, "b" to b), mapOf("out" to output))
                    }
                }
            }
        }
    }

    fun nativeContoursCoverOnlyTheBoxInteriorAndRequireReview() = runBlocking {
        val bytes = mapOf(AnalysisModel.MOBILE_SAM_ENCODER to InferenceFixtures.samEncoder(),
            AnalysisModel.MOBILE_SAM_DECODER to InferenceFixtures.samDecoder(), AnalysisModel.NUDE_NET to InferenceFixtures.detector())
        val specs = bytes.map { (id, data) ->
            val hash = data.toByteString().sha256().hex()
            AnalysisModelSpec(id, "fixture", "fixture", "https://model.test", data.size.toLong(), hash,
                ModelDistribution("https://model.test/$id.onnx", data.size.toLong(), hash))
        }
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        val permit = gate.permit(MediaFeature.IMAGE_EDITOR)!!
        val path = directory.resolve("contour-pixels-${Random.nextLong()}")
        val store = ModelStore(gate, { path }, downloader = { ModelDownloader { _, _ -> error("Unexpected network") } }, models = specs)
        try {
            val original = EditRaster(80, 48, IntArray(80 * 48) { 0xffff0000.toInt() })
            val snapshot = ImageEditDocument(regions = listOf(EditRegion(9)))
            assertFailsWith<IllegalStateException> {
                ImageSensitiveAnalyser.analyse(original, DetectionSettings(contours = true), snapshot, store, gate, permit) { _, _ -> }
            }
            assertEquals(listOf(9), snapshot.regions.map { it.id })
            for (spec in specs) store.import(spec.id, permit) { Buffer().write(bytes.getValue(spec.id)) }
            val automatic = ImageSensitiveAnalyser.analyse(original, DetectionSettings(contours = true), snapshot, store, gate, permit) { _, _ -> }
            assertTrue(automatic.needsReview)
            assertEquals(3, automatic.regions.size)
            assertSame(snapshot.regions.single(), automatic.regions.first())
            assertTrue(automatic.regions.drop(1).all { it.contour != null && !it.contour.isEmpty })
            val rectangles = ImageSensitiveAnalyser.analyse(original, DetectionSettings(), snapshot, store, gate, permit) { _, _ -> }
            assertTrue(rectangles.regions.all { it.contour == null })
            val frame = imageAnalysisFrame(original, 1024)
            val regions = listOf(EditRegion(1, style = EditStyle.BLACK), EditRegion(2, EditBounds(0f, 0f, .2f, .2f), EditStyle.BLACK))
            MobileSamSegmenter.open(store, gate, permit).use { segmenter ->
                val contours = segmenter.segment(frame, regions.map { it.bounds.asMosaicBounds() })
                assertTrue(contours.all { it.mask != null && !it.uncertain && it.mask.coverage in .15f.. .35f })
                val document = ImageEditDocument(regions = regions).withContours(regions.map { it.id }, contours)
                assertTrue(document.needsReview); assertFailsWith<IllegalStateException> { document.requireExportReady() }
                val output = renderImageEdit(original, document)
                assertEquals(0xff000000.toInt(), output.argb[24 * 80 + 40])
                assertEquals(0xffff0000.toInt(), output.argb[13 * 80 + 21])
                assertEquals(0xffff0000.toInt(), output.argb[0])
                document.copy(reviewed = true).requireExportReady()
            }
        } finally { store.closeAndAwait(); FileSystem.SYSTEM.deleteRecursively(path, mustExist = false) }
    }
}
