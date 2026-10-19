@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.scene.ComposeScenePointer
import com.valoser.futacha.shared.ui.initializeFutachaPointerDispatch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs on JVM and iOS through Compose's real hit-path dispatch, not callbacks alone. */
class CompatDrawingDispatchTest {
    private val paper = 0xFFF0E0D6.toInt()
    private val ink = 0xFF800000.toInt()

    private inner class DrawingScene {
        val active = mutableStateListOf<Offset>()
        val completed = mutableStateListOf<List<Offset>>()
        private var time = 0L
        private var frame = 0L
        private val cache = CompatDrawingStrokeCache()
        private val scene: ImageComposeScene

        init {
            initializeFutachaPointerDispatch()
            scene = ImageComposeScene(344, 135) {
                Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectCompatDrawingStrokes(
                        onStart = { active.clear(); active.add(it) },
                        onPoint = { if (active.lastOrNull() != it) active.add(it) },
                        onEnd = { completed.add(active.toList()); active.clear() },
                        onCancel = { active.clear() }
                    )
                }) {
                    drawRect(Color(paper))
                    cache.draw(this, completed.map { stroke(it) })
                    if (active.isNotEmpty()) drawCompatDrawingStroke(stroke(active))
                }
            }
            preview()
        }

        fun event(type: PointerEventType, point: Offset, history: List<Offset> = emptyList()) {
            time += 16L
            scene.sendPointerEvent(type, listOf(ComposeScenePointer(
                PointerId(1), point, type != PointerEventType.Release, PointerType.Touch,
                historical = history.mapIndexed { i, p ->
                    // Different real samples may share a rounded millisecond timestamp.
                    HistoricalChange(uptimeMillis = time - 8 + i * 8 / history.size, position = p)
                }
            )), timeMillis = time)
        }

        fun preview(): IntArray {
            Snapshot.sendApplyNotifications()
            frame += 16_000_000L
            val image = scene.render(frame)
            return try {
                image.toComposeImageBitmap().let { b -> IntArray(344 * 135).also { b.readPixels(it) } }
            } finally { image.close() }
        }

        fun png(): IntArray = runBlocking {
            val data = renderCompatDrawingPng(completed.map { stroke(it) }, paper, 344, 135).getOrThrow()
            val image = Image.makeFromEncoded(data.bytes)
            try {
                val b = image.toComposeImageBitmap()
                assertEquals(344, b.width); assertEquals(135, b.height)
                IntArray(344 * 135).also { b.readPixels(it) }
            } finally { image.close() }
        }

        fun close() = scene.close()
        private fun stroke(points: List<Offset>) = CompatDrawingStroke(ink, 4f, points.map { CompatDrawingPoint(it.x, it.y) })
    }

    private fun drawing(block: DrawingScene.() -> Unit) {
        val scene = DrawingScene()
        try { scene.block() } finally { scene.close() }
    }

    @Test fun subpixelExcursionReturningToTheSamePositionReachesTheHandlerInOrder() = drawing {
        val start = Offset(20f, 40f)
        val anchor = Offset(24f, 40f)
        val history = listOf(Offset(24.125f, 40.25f), Offset(24.25f, 39.75f), Offset(24.125f, 40.25f))
        event(PointerEventType.Press, start)
        event(PointerEventType.Move, anchor)
        event(PointerEventType.Move, anchor, history)
        assertEquals(listOf(start, anchor) + history + anchor, active.toList())
        event(PointerEventType.Release, Offset(25f, 41f))
        assertEquals(listOf(start, anchor) + history + listOf(anchor, Offset(25f, 41f)), completed.single())
    }

    @Test fun releaseOnlyMovementAndAllItsHistoryReachTheHandler() = drawing {
        val start = Offset(20f, 40f)
        val end = Offset(100f, 80f)
        val history = listOf(Offset(40f, 70f), Offset(60f, 20f), Offset(80f, 60f))
        event(PointerEventType.Press, start)
        event(PointerEventType.Release, end, history)
        assertEquals(listOf(start) + history + end, completed.single())
        assertTrue(active.isEmpty())
    }

    @Test fun closedSubframeLoopAndReleaseTailRemainVisibleAndInTheActualPng() = drawing {
        val start = Offset(20f, 40f)
        val anchor = Offset(30f, 40f)
        val loop = listOf(Offset(60f, 90f), Offset(100f, 20f), Offset(140f, 85f))
        val tail = listOf(Offset(180f, 30f), Offset(220f, 90f))
        val end = Offset(300f, 110f)
        event(PointerEventType.Press, start)
        event(PointerEventType.Move, anchor)
        event(PointerEventType.Move, anchor, loop)
        // The intermediate loop must already be visible before the finger lifts.
        val beforeUp = preview()
        loop.forEach { assertEquals(ink, beforeUp[it.y.toInt() * 344 + it.x.toInt()], "active bend $it") }
        event(PointerEventType.Release, end, tail)
        assertEquals(listOf(start, anchor) + loop + listOf(anchor) + tail + end, completed.single())
        for (pixels in listOf(preview(), png())) {
            (loop + tail + end).forEach { p ->
                assertEquals(ink, pixels[p.y.toInt() * 344 + p.x.toInt()], "missing bend or endpoint $p")
            }
            assertEquals(paper, pixels[15 * 344 + 300], "Do not invent a straight or predicted continuation")
        }
    }

    @Test fun hundredsOfSamplesInOneFrameAreNotReducedToTheLastPosition() = drawing {
        val anchor = Offset(40f, 50f)
        val samples = (1..512).map { Offset(40f + it % 13 * 0.125f, 50f + it % 11 * 0.125f) }
        event(PointerEventType.Press, Offset(20f, 50f))
        event(PointerEventType.Move, anchor)
        event(PointerEventType.Move, anchor, samples)
        event(PointerEventType.Release, Offset(100f, 70f))
        assertEquals(listOf(Offset(20f, 50f), anchor) + samples + listOf(anchor, Offset(100f, 70f)), completed.single())
    }

    @Test fun eightyFastStrokesDoNotLoseTheirSeparateEndpoints() = drawing {
        repeat(80) { i ->
            val start = Offset(20f + i, 40f)
            val end = start + Offset(0.125f, 0.25f)
            event(PointerEventType.Press, start)
            event(PointerEventType.Release, end)
        }
        assertEquals(80, completed.size)
        completed.forEachIndexed { i, points ->
            assertEquals(listOf(Offset(20f + i, 40f), Offset(20.125f + i, 40.25f)), points)
        }
    }
}
