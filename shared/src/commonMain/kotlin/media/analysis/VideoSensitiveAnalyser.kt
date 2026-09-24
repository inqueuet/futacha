package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import kotlinx.coroutines.*

/** Uses the editor's single owned source. Results are committed only after the entire operation. */
internal object VideoSensitiveAnalyser {
    suspend fun detect(source: VideoEditSource, info: VideoEditInfo, document: MosaicDocument,
        startUs: Long, endUs: Long, settings: DetectionSettings, store: ModelStore,
        gate: MediaFeatureGate, permit: MediaFeaturePermit, progress: (String, Float?) -> Unit
    ): MosaicDocument = withContext(Dispatchers.Default) {
        settings.validated()
        val context = currentCoroutineContext()
        fun checkActive() { context.ensureActive(); source.checkActive(); checkVideoAnalysisPermit(gate, permit) }
        checkActive()
        val request = VideoAnalysisRequest(info.frames, startUs, endUs, if (settings.tiles) 960 else 640, true)
        val total = request.endIndex - request.firstIndex
        require(total in 1..30_000) { "検出は1回30,000コマまでです。区間を短くしてください" }
        require(document.regions.size < MosaicDocument.MAX_REGIONS) { "範囲が16か所あります。不要な範囲を削除してください" }
        if (settings.contours) {
            require(total <= 600) { "輪郭の自動抽出は1回600コマまでです。解析区間を短くしてください" }
            for (model in listOf(AnalysisModel.MOBILE_SAM_ENCODER, AnalysisModel.MOBILE_SAM_DECODER)) {
                // Early, cheap check; MobileSamSegmenter.open verifies the content before running.
                check(store.present(model, permit)) { "モデル画面で輪郭用の2モデルを導入してください" }
            }
        }
        val detectors = mutableListOf<SensitiveDetector>()
        val tracks = mutableListOf<DetectionTrack>()
        val issues = VideoAnalysisIssues()
        val existingSamples = document.regions.sumOf { it.trajectory?.size?.toLong() ?: 0L }
        val detected = try {
            for (model in settings.models) {
                checkActive()
                progress("検出モデルを読み込んでいます", null)
                detectors += SensitiveDetector.open(model, store, gate, permit)
            }
            var previous: AnalysisFrame? = null
            var count = 0
            source.analysisFrames(info, startUs, endUs, request.maximumEdge) { frame ->
                checkActive()
                val end = info.frames.endAfter(frame.timeUs)
                val cut = sceneChanged(previous, frame)
                if (cut) {
                    issues.add(frame.timeUs, end, "場面の切り替わりを確認してください")
                    tracks.forEach { it.active = false }
                }
                // Search every actual frame, including one-frame exposures.
                val found = mutableListOf<Detection>()
                for (detector in detectors) found += detector.detect(frame, settings.threshold, settings.faces, settings.tiles)
                val detections = suppressDetections(found, check = ::checkActive)
                val unmatched = detections.toMutableList()
                for (track in tracks.filter { it.active }) {
                    checkActive()
                    val predicted = track.tracker.step(frame, cut)
                    val match = unmatched.filter { it.label == track.label && intersectionOverUnion(it.bounds, predicted.bounds) >= .15f }
                        .maxByOrNull { intersectionOverUnion(it.bounds, predicted.bounds) }
                    if (match != null) {
                        unmatched.remove(match)
                        val bounds = expand(match.bounds, settings.margin)
                        track.tracker.seed(frame, bounds)
                        track.lastDetectedUs = frame.timeUs
                        track.append(frame.timeUs, bounds, match.score < .3f, end)
                        if (match.score < .3f) issues.add(frame.timeUs, end, "弱い検出候補を確認してください")
                    } else {
                        track.append(frame.timeUs, predicted.bounds, true, end)
                        issues.add(frame.timeUs, end, "検出が途切れました。枠と隠し漏れを確認してください")
                        if (frame.timeUs - track.lastDetectedUs >= 500_000) track.active = false
                    }
                }
                for (detection in unmatched) {
                    check(tracks.size + document.regions.size < MosaicDocument.MAX_REGIONS) {
                        "候補が16か所を超えました。結果は反映していません。短い区間に分けてください"
                    }
                    val track = DetectionTrack(detection.label, frame.timeUs)
                    tracks += track
                    val bounds = expand(detection.bounds, settings.margin)
                    track.tracker.seed(frame, bounds)
                    track.append(frame.timeUs, bounds, detection.score < .3f, end)
                    if (detection.score < .3f) issues.add(frame.timeUs, end, "弱い検出候補を確認してください")
                }
                if (detections.isEmpty()) issues.add(frame.timeUs, end, "候補なし。隠す部分がないか映像を確認してください")
                check(existingSamples + tracks.sumOf { it.samples.size.toLong() } <= MosaicTrajectory.MAX_SAMPLES) {
                    "追尾データの上限です。短い区間に分けて解析してください"
                }
                previous = frame; count++
                if (count == 1 || count % 2 == 0 || count == total) {
                    progress("性器の候補を検出しています（$count / $total コマ）", count.toFloat() / total)
                }
            }
            checkActive()
            val ids = document.regions.map { it.id }.toMutableSet()
            var nextId = 1
            val regions = tracks.map { track ->
                var id: String
                do { id = "detected-${nextId++}" } while (!ids.add(id))
                val trajectory = track.samples.build()
                MosaicRegion(id, startUs = trajectory.firstTimeUs, endUs = track.endUs,
                    keyframes = listOf(MosaicKeyframe(trajectory.firstTimeUs, trajectory.boundsAtIndex(0))),
                    trajectory = trajectory, label = track.label)
            }
            document.copy(regions = document.regions + regions,
                review = mergeVideoReview(document.review, startUs, endUs, total, "性器の候補検出（${regions.size}か所）", issues.items))
        } finally {
            tracks.forEach { it.tracker.close() }
            detectors.asReversed().forEach { it.close() }
        }
        val added = detected.regions.map { it.id }.toSet() - document.regions.map { it.id }.toSet()
        val result = if (settings.contours && added.isNotEmpty()) contours(source, info, detected, added, startUs, endUs,
            store, gate, permit, progress) else detected
        checkActive()
        result
    }

    suspend fun contours(source: VideoEditSource, info: VideoEditInfo, document: MosaicDocument, ids: Set<String>,
        startUs: Long, endUs: Long, store: ModelStore, gate: MediaFeatureGate, permit: MediaFeaturePermit,
        progress: (String, Float?) -> Unit
    ): MosaicDocument = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        fun checkActive() { context.ensureActive(); source.checkActive(); checkVideoAnalysisPermit(gate, permit) }
        checkActive()
        require(ids.isNotEmpty()) { "輪郭を抽出する範囲を選択してください" }
        val request = VideoAnalysisRequest(info.frames, startUs, endUs, 1024, true)
        val count = request.endIndex - request.firstIndex
        require(count in 1..600) { "輪郭の自動抽出は1回600コマまでです。解析区間を短くしてください" }
        val targets = document.regions.filter { it.id in ids }
        require(targets.size == ids.size)
        val addedCount = targets.sumOf { region ->
            (request.firstIndex until request.endIndex).count { region.activeAt(info.frames.timeAt(it)) }
        }
        require(addedCount > 0) { "選択した枠の適用区間と解析区間が重なっていません" }
        val retained = document.regions.sumOf { region ->
            region.masks.count { region.id !in ids || it.timeUs < startUs || it.timeUs >= endUs }
        }
        require(retained + addedCount + targets.size <= MosaicMask.MAX_SAMPLES) { "輪郭データの上限です。区間を短くしてください" }
        val masks = targets.associate { it.id to mutableListOf<MosaicMaskKeyframe>() }
        val issues = VideoAnalysisIssues()
        MobileSamSegmenter.open(store, gate, permit).use { segmenter ->
            var processed = 0
            source.analysisFrames(info, startUs, endUs, maximumEdge = 1024) { frame ->
                checkActive()
                val active = targets.filter { it.activeAt(frame.timeUs) }
                if (active.isNotEmpty()) {
                    val results = segmenter.segment(frame, active.map { it.boundsAt(frame.timeUs) })
                    active.zip(results).forEach { (region, result) ->
                        masks.getValue(region.id) += MosaicMaskKeyframe(frame.timeUs, result.mask)
                        if (result.uncertain) issues.add(frame.timeUs, info.frames.endAfter(frame.timeUs),
                            if (result.mask == null) "輪郭を特定できないため元の枠で隠しています" else "輪郭の境界を確認してください")
                    }
                }
                processed++
                progress("輪郭を抽出しています（$processed / $count コマ）", processed.toFloat() / count)
            }
        }
        checkActive()
        document.copy(regions = document.regions.map { region ->
            val added = masks[region.id]
            if (added.isNullOrEmpty()) region else {
                // A short extraction must not overwrite the mask after its half-open interval.
                val restore = if (endUs < region.endUs && region.masks.none { it.timeUs == endUs })
                    listOf(MosaicMaskKeyframe(endUs, region.maskAt(endUs))) else emptyList()
                region.copy(masks = (region.masks.filter { it.timeUs < startUs || it.timeUs >= endUs } + added + restore).sortedBy { it.timeUs })
            }
        }, review = mergeVideoReview(document.review, startUs, endUs, count, "輪郭の自動抽出", issues.items))
    }

    private class DetectionTrack(val label: String, var lastDetectedUs: Long) {
        val tracker = OpticalFlowTracker()
        val samples = MosaicTrajectory.Builder()
        var endUs = 0L
        var active = true
        fun append(time: Long, bounds: MosaicBounds, uncertain: Boolean, end: Long) {
            samples.add(time, bounds, uncertain); endUs = end
        }
    }
    private fun expand(bounds: MosaicBounds, margin: Float) = bounds.copy(
        width = bounds.width * (1 + margin * 2), height = bounds.height * (1 + margin * 2)).constrained()
}

private fun checkVideoAnalysisPermit(gate: MediaFeatureGate, permit: MediaFeaturePermit) {
    require(permit.feature == MediaFeature.VIDEO_EDITOR)
    if (!gate.isCurrent(permit)) throw CancellationException("動画編集は無効になりました")
}
