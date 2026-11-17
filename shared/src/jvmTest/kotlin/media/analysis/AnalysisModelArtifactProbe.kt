package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath

/** Explicit developer probe; external model weights are not fixtures bundled with the app/tests.
 * Run with tools/verify-analysis-models.init.gradle.kts after obtaining the pinned distributions. */
object AnalysisModelArtifactProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        require(args.size in 2..4)
        val network = "--network" in args.drop(2)
        val inference = "--inference" in args.drop(2)
        val inputs = args[0].toPath()
        val output = args[1].toPath()
        val names = mapOf(AnalysisModel.NUDE_NET to "nudenet.whl", AnalysisModel.ANIME_CENSOR to "anime.onnx",
            AnalysisModel.MOBILE_SAM_ENCODER to "mobile_sam_image_encoder.onnx", AnalysisModel.MOBILE_SAM_DECODER to "sam_mask_decoder_multi.onnx")
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }
        val permit = requireNotNull(gate.permit(MediaFeature.IMAGE_EDITOR))
        var acquisitions = 0
        val store = ModelStore(gate, { output }, downloader = {
            if (network) object : ModelDownloader, AutoCloseable {
                val transport = KtorModelDownloader()
                override suspend fun download(distribution: ModelDistribution, sink: okio.BufferedSink) {
                    acquisitions++; transport.download(distribution, sink)
                }
                override fun close() = transport.close()
            } else ModelDownloader { distribution, sink ->
                val spec = AnalysisModels.all.single { it.distribution == distribution }
                acquisitions++
                FileSystem.SYSTEM.source(inputs.resolve(names.getValue(spec.id))).use { source ->
                    val buffer = Buffer()
                    while (true) {
                        val n = source.read(buffer, 64 * 1024)
                        if (n == -1L) break
                        sink.write(buffer, n)
                    }
                }
            }
        })
        try {
            for (spec in AnalysisModels.all) {
                val file = store.download(spec.id, permit)
                check(file == store.verified(spec.id, permit))
                check(FileSystem.SYSTEM.metadata(file.path).size == spec.bytes)
                println("VERIFIED ${spec.id} ${spec.bytes} ${spec.sha256}")
            }
            println("PINNED_ARTIFACTS_OK models=4 acquisitions=$acquisitions network=$network")
            if (inference) {
                val frame = AnalysisFrame(0, 96, 64, ByteArray(96 * 64 * 3) { (it % 256).toByte() })
                for (model in DetectorModel.entries) {
                    SensitiveDetector.open(model, store, gate, permit).use { detector ->
                        for (tiles in listOf(false, true)) {
                            val result = detector.detect(frame, .15f, faces = true, tiles = tiles)
                            check(result.all { it.score.isFinite() && it.bounds == it.bounds.constrained() })
                            println("DETECTOR_OK model=$model tiles=$tiles candidates=${result.size}")
                        }
                    }
                }
                probeContourModels(store, permit)
                MobileSamSegmenter.open(store, gate, permit).use { segmenter ->
                    val boxes = listOf(com.valoser.futacha.shared.media.video.model.MosaicBounds(.5f, .5f, .5f, .5f),
                        com.valoser.futacha.shared.media.video.model.MosaicBounds(.3f, .3f, .2f, .2f))
                    val result = segmenter.segment(frame, boxes)
                    check(result.size == boxes.size && result.all { it.mask == null || it.mask.coverage in .02f.. .95f })
                    println("CONTOUR_PIPELINE_OK regions=${result.size} contours=${result.count { it.mask != null }} uncertain=${result.count { it.uncertain }}")
                }
            }
        } finally { store.closeAndAwait() }
    }

    private suspend fun probeContourModels(store: ModelStore, permit: MediaFeaturePermit) {
        val encoder = requireNotNull(store.verified(AnalysisModel.MOBILE_SAM_ENCODER, permit))
        val decoder = requireNotNull(store.verified(AnalysisModel.MOBILE_SAM_DECODER, permit))
        val tensors = mutableListOf<InferenceTensor>()
        fun tensor(vararg shape: Long) = createInferenceTensor(shape.toList()).also { tensors += it }
        try {
            val image = tensor(256, 1024, 3)
            val embeddings = tensor(1, 256, 64, 64)
            openCpuInferenceSession(encoder.path.toString()).use { runtime ->
                runtime.run(mapOf("input_image" to image), mapOf("image_embeddings" to embeddings))
                check(embeddings.read().all { it.isFinite() })
            }
            val points = tensor(1, 2, 2).apply { write(floatArrayOf(100f, 50f, 500f, 200f)) }
            val labels = tensor(1, 2).apply { write(floatArrayOf(2f, 3f)) }
            val mask = tensor(1, 1, 256, 256)
            val hasMask = tensor(1)
            val originalSize = tensor(2).apply { write(floatArrayOf(256f, 1024f)) }
            val lowResolution = tensor(1, 4, 256, 256)
            val scores = tensor(1, 4)
            val masks = tensor(1, 4, 256, 1024)
            openCpuInferenceSession(decoder.path.toString()).use { runtime ->
                runtime.run(mapOf("image_embeddings" to embeddings, "point_coords" to points, "point_labels" to labels,
                    "mask_input" to mask, "has_mask_input" to hasMask, "orig_im_size" to originalSize),
                    mapOf("low_res_masks" to lowResolution, "iou_predictions" to scores, "masks" to masks))
                check(listOf(lowResolution, scores, masks).all { output -> output.read().all { it.isFinite() } })
            }
            println("CONTOUR_RUNTIME_OK models=2 embeddingsReused=true finiteOutputs=true")
        } finally { tensors.asReversed().forEach { it.close() } }
    }
}
