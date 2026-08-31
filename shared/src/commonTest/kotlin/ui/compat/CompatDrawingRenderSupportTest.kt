package com.valoser.futacha.shared.ui.compat

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompatDrawingRenderSupportTest {
    @Test
    fun drawingSurfaceMatchesTheReferenceApkDimensionsAndBackground() {
        assertEquals(0.9f, COMPAT_DRAWING_WIDTH_FRACTION)
        assertEquals(344f / 135f, COMPAT_DRAWING_ASPECT_RATIO)
        assertEquals(0xFFFFFFEEL, COMPAT_DRAWING_SURROUNDING_COLOR_ARGB)
        assertEquals(0xFFF0E0D6L, COMPAT_DRAWING_CANVAS_COLOR_ARGB)
        assertEquals(344, COMPAT_DRAWING_OUTPUT_WIDTH_PX)
        assertEquals(135, COMPAT_DRAWING_OUTPUT_HEIGHT_PX)
    }

    @Test
    fun drawingBrushesAndTwelveColorPickerMatchBothReferenceApks() {
        assertEquals(CompatDrawingBrush(0xFF800000, 6), CompatDrawingBrush(COMPAT_DRAWING_MAIN_COLOR_ARGB, COMPAT_DRAWING_MAIN_SIZE))
        assertEquals(18f, CompatDrawingBrush(COMPAT_DRAWING_MAIN_COLOR_ARGB, COMPAT_DRAWING_MAIN_SIZE).widthPx)
        assertEquals(CompatDrawingBrush(0xFFF0E0D6, 24), CompatDrawingBrush(COMPAT_DRAWING_SUB_COLOR_ARGB, COMPAT_DRAWING_SUB_SIZE))
        assertEquals(72f, CompatDrawingBrush(COMPAT_DRAWING_SUB_COLOR_ARGB, COMPAT_DRAWING_SUB_SIZE).widthPx)
        assertEquals(
            listOf(
                0xFF000000, 0xFF808080, 0xFF800000, 0xFFF0E0D6,
                0xFFFFFFFF, 0xFFEC3323, 0xFFF8991D, 0xFFF6EB39,
                0xFF64AD3B, 0xFF0791CC, 0xFF7C3692, 0xFFF19EC2
            ),
            compatDrawingReferencePresets
        )
    }

    @Test
    fun drawingStrokesAreScaledIntoTheReference344By135Png() {
        val scaled = scaleCompatDrawingStrokesForReferencePng(
            listOf(
                CompatDrawingStroke(
                    colorArgb = 0xFF800000.toInt(),
                    widthPx = 36f,
                    points = listOf(CompatDrawingPoint(688f, 270f))
                )
            ),
            sourceWidthPx = 688,
            sourceHeightPx = 270
        ).single()

        assertEquals(18f, scaled.widthPx)
        assertEquals(344f, scaled.points.single().x)
        assertEquals(135f, scaled.points.single().y)
        assertEquals("drawing_19700101_000000.png", compatDrawingFileName(0L, TimeZone.UTC))
    }

    @Test
    fun rejectsDimensionsWhosePixelCountExceedsSafeLimit() {
        assertFailsWith<IllegalArgumentException> {
            validateCompatDrawingRender(emptyList(), 100_000, 100_000)
        }
    }

    @Test
    fun rejectsExcessiveStrokeCount() {
        val stroke = CompatDrawingStroke(0, 1f, emptyList())
        assertFailsWith<IllegalArgumentException> {
            validateCompatDrawingRender(
                List(100_001) { stroke },
                100,
                100
            )
        }
    }
}
