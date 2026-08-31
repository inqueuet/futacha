package compat

import com.valoser.futacha.shared.ui.compat.compatSelectorCloseTransform
import com.valoser.futacha.shared.ui.compat.compatSelectorShadowAlphaAdd
import com.valoser.futacha.shared.ui.compat.compatPagerGestureAxis
import com.valoser.futacha.shared.ui.compat.compatPagerShouldDeferToDrawer
import com.valoser.futacha.shared.ui.compat.compatDrawerSwipeShouldOpen
import com.valoser.futacha.shared.ui.compat.compatDrawerScrimAlpha
import com.valoser.futacha.shared.ui.compat.CompatPagerGestureAxis
import com.valoser.futacha.shared.ui.compat.CompatPullGestureAxis
import com.valoser.futacha.shared.ui.compat.COMPAT_PAGER_DIRECTION_RATIO
import com.valoser.futacha.shared.ui.compat.COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP
import com.valoser.futacha.shared.ui.compat.COMPAT_DRAWER_SWIPE_TRIGGER_DP
import com.valoser.futacha.shared.ui.compat.compatPullGestureAxis
import com.valoser.futacha.shared.ui.compat.compatSelectorPreviewOffset
import com.valoser.futacha.shared.ui.compat.CompatSelectorWindowPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.valoser.futacha.shared.ui.compat.isCompatSelectorCloseDrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityTabSelectorMotionTest {
    @Test
    fun dragPopupUsesWindowOriginInsteadOfSelectorAnchor() {
        assertEquals(
            0 to 0,
            CompatSelectorWindowPositionProvider.calculatePosition(
                anchorBounds = IntRect(0, 1_600, 1_080, 1_640),
                windowSize = IntSize(1_080, 1_920),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(1_080, 1_920)
            ).let { it.x to it.y }
        )
    }

    @Test
    fun dragPreviewUsesRootCoordinatesAndStaysInsideTheWindow() {
        assertEquals(
            100 to 300,
            compatSelectorPreviewOffset(100f, 300f, 1080, 1920, 160, 100).let { it.x to it.y }
        )
        assertEquals(
            920 to 1820,
            compatSelectorPreviewOffset(2_000f, 3_000f, 1080, 1920, 160, 100).let { it.x to it.y }
        )
        assertEquals(
            0 to 0,
            compatSelectorPreviewOffset(-20f, -50f, 1080, 1920, 160, 100).let { it.x to it.y }
        )
    }

    @Test
    fun dropRegionUsesItemTopAndExcludesTheNinetyPercentBoundary() {
        assertTrue(isCompatSelectorCloseDrop(500f, 899f, 1_000f, 1_080f))
        assertFalse(isCompatSelectorCloseDrop(500f, 900f, 1_000f, 1_080f))
        assertFalse(isCompatSelectorCloseDrop(-1f, 800f, 1_000f, 1_080f))
        assertFalse(isCompatSelectorCloseDrop(1_081f, 800f, 1_000f, 1_080f))
    }

    @Test
    fun shadowAndCloseAnimationMatchTargetEndpoints() {
        assertEquals(0f, compatSelectorShadowAlphaAdd(1_000f, 1_000f), 0.0001f)
        assertEquals(0.5f, compatSelectorShadowAlphaAdd(950f, 1_000f), 0.0001f)
        assertEquals(1f, compatSelectorShadowAlphaAdd(900f, 1_000f), 0.0001f)
        assertEquals(1.1f, compatSelectorCloseTransform(0f).scale, 0.0001f)
        assertEquals(0.1f, compatSelectorCloseTransform(1f).scale, 0.0001f)
        assertEquals(-270f, compatSelectorCloseTransform(1f).rotationDegrees, 0.0001f)
    }

    @Test
    fun pagerRequiresARealHorizontalLeadBeforeCapturingTheGesture() {
        assertEquals(
            CompatPagerGestureAxis.UNDECIDED,
            compatPagerGestureAxis(10f, 3f, touchSlopPx = 16f)
        )
        assertEquals(
            CompatPagerGestureAxis.REJECTED,
            compatPagerGestureAxis(18f, 20f, touchSlopPx = 16f)
        )
        assertEquals(
            CompatPagerGestureAxis.HORIZONTAL,
            compatPagerGestureAxis(32f, 20f, touchSlopPx = 16f)
        )
        assertTrue(32f >= 20f * COMPAT_PAGER_DIRECTION_RATIO)
        assertEquals(
            CompatPagerGestureAxis.REJECTED,
            compatPagerGestureAxis(20f, 17f, touchSlopPx = 16f)
        )
    }

    @Test
    fun pullRefreshOnlyAcceptsClearlyVerticalGestures() {
        assertEquals(
            CompatPullGestureAxis.UNDECIDED,
            compatPullGestureAxis(8f, 8f, touchSlopPx = 16f)
        )
        assertEquals(
            CompatPullGestureAxis.VERTICAL,
            compatPullGestureAxis(10f, 20f, touchSlopPx = 16f)
        )
        assertEquals(
            CompatPullGestureAxis.REJECTED,
            compatPullGestureAxis(20f, 16f, touchSlopPx = 16f)
        )
        assertEquals(
            CompatPullGestureAxis.REJECTED,
            compatPullGestureAxis(20f, 20f, touchSlopPx = 16f)
        )
    }

    @Test
    fun pagerLeavesTheLeftEdgeGestureToTheDrawer() {
        val edge = COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP.toFloat()
        assertTrue(compatPagerShouldDeferToDrawer(downX = 0f, drawerEdgeWidthPx = edge))
        assertTrue(compatPagerShouldDeferToDrawer(downX = edge, drawerEdgeWidthPx = edge))
        assertFalse(compatPagerShouldDeferToDrawer(downX = edge + 0.1f, drawerEdgeWidthPx = edge))
        assertFalse(compatPagerShouldDeferToDrawer(downX = -1f, drawerEdgeWidthPx = edge))
    }

    @Test
    fun drawerOpensAfterShortEdgeTravelButNotFromTheSecondColumn() {
        val edge = COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP.toFloat()
        val trigger = COMPAT_DRAWER_SWIPE_TRIGGER_DP.toFloat()
        assertTrue(
            compatDrawerSwipeShouldOpen(
                startX = 1f,
                totalDx = trigger,
                totalDy = 0f,
                edgeWidthPx = edge,
                triggerPx = trigger
            )
        )
        assertFalse(
            compatDrawerSwipeShouldOpen(
                startX = 145f,
                totalDx = trigger,
                totalDy = 0f,
                edgeWidthPx = edge,
                triggerPx = trigger
            )
        )
        assertFalse(
            compatDrawerSwipeShouldOpen(
                startX = 1f,
                totalDx = trigger - 1f,
                totalDy = 0f,
                edgeWidthPx = edge,
                triggerPx = trigger
            )
        )
    }

    @Test
    fun drawerScrimHasTheSameAlphaDuringPreviewAndAfterSettling() {
        assertEquals(0f, compatDrawerScrimAlpha(0f), 0.0001f)
        assertEquals(0.16f, compatDrawerScrimAlpha(0.5f), 0.0001f)
        assertEquals(0.32f, compatDrawerScrimAlpha(1f), 0.0001f)
        assertEquals(0.32f, compatDrawerScrimAlpha(2f), 0.0001f)
    }
}
