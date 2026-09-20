package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.features.Features
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.*

internal object TrackingRuntime {
    @Volatile private var ready = false
    @Synchronized fun load() {
        if (!ready) {
            check(OpenCVLoader.initLocal()) { "追尾ライブラリを読み込めません。" }
            Core.setNumThreads(2)
            ready = true
        }
    }
}

/** Sparse pyramidal LK with backward validation and a robust similarity transform.
 * A failure freezes the last box until an explicit manual/detector anchor reseeds the tracker.
 * Corners are transformed cumulatively; a rotated AABB is never fed back as a new shape.
 */
internal actual class OpticalFlowTracker actual constructor() : AutoCloseable {
    init { TrackingRuntime.load() }
    private var closed = false
    private val before = Mat()
    private val after = Mat()
    private var corners = emptyArray<Point>()
    private var points = MatOfPoint2f()
    private var bounds = MosaicBounds.DEFAULT
    private var width = 0; private var height = 0
    private var valid = false


    actual fun seed(frame: AnalysisFrame, region: MosaicBounds) {
        check(!closed); validateTrackingFrame(frame)
        require(region == region.constrained() && listOf(region.centerX, region.centerY, region.width, region.height).all(Float::isFinite))
        width = frame.width; height = frame.height; bounds = region
        before.create(height, width, CvType.CV_8UC1); before.put(0, 0, frame.gray)
        corners = arrayOf(
            Point((region.centerX - region.width / 2) * width.toDouble(), (region.centerY - region.height / 2) * height.toDouble()),
            Point((region.centerX + region.width / 2) * width.toDouble(), (region.centerY - region.height / 2) * height.toDouble()),
            Point((region.centerX + region.width / 2) * width.toDouble(), (region.centerY + region.height / 2) * height.toDouble()),
            Point((region.centerX - region.width / 2) * width.toDouble(), (region.centerY + region.height / 2) * height.toDouble())
        )
        refill(before)
        valid = points.total() >= 8
    }

    actual fun step(frame: AnalysisFrame, sceneCut: Boolean): TrackingStep {
        check(!closed); validateTrackingFrame(frame)
        if (!valid || sceneCut || frame.width != width || frame.height != height) { valid = false; return TrackingStep(bounds, true) }
        after.create(height, width, CvType.CV_8UC1); after.put(0, 0, frame.gray)
        val next = MatOfPoint2f(); val back = MatOfPoint2f()
        val status = MatOfByte(); val backStatus = MatOfByte(); val error = MatOfFloat(); val backError = MatOfFloat()
        val from = MatOfPoint2f(); val to = MatOfPoint2f(); val inliers = Mat()
        var affine: Mat? = null
        try {
            Video.calcOpticalFlowPyrLK(before, after, points, next, status, error, Size(21.0, 21.0), 3)
            Video.calcOpticalFlowPyrLK(after, before, next, back, backStatus, backError, Size(21.0, 21.0), 3)
            val p = points.toArray(); val q = next.toArray(); val r = back.toArray()
            val ok = status.toArray(); val bok = backStatus.toArray(); val errors = error.toArray()
            val good = p.indices.filter { i -> ok[i].toInt() != 0 && bok[i].toInt() != 0 && errors[i] < 35 &&
                q[i].x.isFinite() && q[i].y.isFinite() && q[i].x in 0.0..width.toDouble() && q[i].y in 0.0..height.toDouble() && hypot(p[i].x - r[i].x, p[i].y - r[i].y) <= 1.5 }
            if (good.size < 8 || good.size < p.size * 0.5) return lost()
            from.fromList(good.map { p[it] }); to.fromList(good.map { q[it] })
            affine = Geometry.estimateAffinePartial2D(from, to, inliers, Geometry.RANSAC, 2.0, 500, 0.99, 5)
            if (affine.empty() || Core.countNonZero(inliers) < maxOf(8, good.size * 3 / 5)) return lost()
            val m = DoubleArray(6); affine.get(0, 0, m)
            if (m.any { !it.isFinite() }) return lost()
            val scale = hypot(m[0], m[3])
            if (scale !in 0.8..1.25 || abs(atan2(m[3], m[0])) > PI / 5) return lost()
            val transformed = corners.map { Point(m[0] * it.x + m[1] * it.y + m[2], m[3] * it.x + m[4] * it.y + m[5]) }.toTypedArray()
            val left = transformed.minOf { it.x }; val right = transformed.maxOf { it.x }
            val top = transformed.minOf { it.y }; val bottom = transformed.maxOf { it.y }
            if (right <= 0 || bottom <= 0 || left >= width || top >= height) return lost()
            val newBounds = MosaicBounds(((left + right) / 2 / width).toFloat(), ((top + bottom) / 2 / height).toFloat(), ((right - left) / width).toFloat(), ((bottom - top) / height).toFloat()).constrained()
            if (hypot((newBounds.centerX - bounds.centerX).toDouble(), (newBounds.centerY - bounds.centerY).toDouble()) > 0.3) return lost()
            bounds = newBounds; corners = transformed
            after.copyTo(before)
            // Re-detect features inside the transported polygon, avoiding long-lived background points.
            refill(before)
            valid = points.total() >= 8
            return TrackingStep(bounds, !valid)
        } finally {
            listOf(next, back, status, backStatus, error, backError, from, to, inliers).forEach(Mat::release)
            affine?.release()
        }
    }

    private fun refill(image: Mat) {
        val mask = Mat.zeros(height, width, CvType.CV_8UC1)
        val polygon = MatOfPoint(*corners)
        val found = MatOfPoint()
        try {
            Imgproc.fillConvexPoly(mask, polygon, Scalar(255.0))
            Features.goodFeaturesToTrack(image, found, 96, 0.01, 4.0, mask)
            points.fromArray(*found.toArray())
        } finally { mask.release(); polygon.release(); found.release() }
    }
    private fun lost(): TrackingStep { valid = false; return TrackingStep(bounds, true) }
    actual override fun close() { if (!closed) { closed = true; before.release(); after.release(); points.release() } }
}
