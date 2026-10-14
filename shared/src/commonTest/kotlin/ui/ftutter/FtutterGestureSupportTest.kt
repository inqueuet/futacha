package com.valoser.futacha.shared.ui.ftutter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterGestureSupportTest {
    private fun track(vararg moves: Pair<Float, Float>, steps: Int = 8): String {
        val tracker = FtutterGestureTracker()
        moves.forEach { (dx, dy) -> repeat(steps) { tracker.update(dx / steps, dy / steps) } }
        return tracker.code
    }

    @Test
    fun thereAreTwentySixTrajectoriesWithTheOriginalDefaults() {
        assertEquals(26, FTUTTER_GESTURE_DEFAULTS.size)
        assertTrue(FTUTTER_GESTURE_DEFAULTS.keys.all { it.first() == '1' || it.first() == '2' })
        assertEquals(8, FTUTTER_GESTURE_DEFAULTS.values.count { it != FtutterGestureAction.NONE })
        assertEquals(FtutterGestureAction.OPEN_DRAWER, FTUTTER_GESTURE_DEFAULTS["10"])
        assertEquals(FtutterGestureAction.REFRESH, FTUTTER_GESTURE_DEFAULTS["201"])
        assertEquals("←↑→", ftutterGestureArrows("201"))
        assertEquals(19, FtutterGestureAction.entries.size)
    }

    @Test
    fun theFirstSegmentIsTheHorizontalSignOfTheFirstMove() {
        // 少し右下でも、横の符号が正なら「→」（原作：1区間目は必ず横）。
        assertEquals("1", track(3f to 10f, steps = 1))
        assertEquals("2", track(-5f to 0f))
    }

    @Test
    fun laterSegmentsNeedMoreThanTwentyDpOnOneAxis() {
        assertEquals("1", track(30f to 0f, 0f to -19f))
        assertEquals("10", track(30f to 0f, 0f to -21f))
        // 同じ向きは足さない。逆向きは別の区間。
        assertEquals("12", track(30f to 0f, 30f to 0f, -25f to 0f))
        assertEquals("201", track(-30f to 0f, 0f to -25f, 25f to 0f))
        assertEquals("2010", track(-30f to 0f, 0f to -25f, 25f to 0f, 0f to -25f))
    }

    @Test
    fun fourSegmentsShowNothingAndDoNothing() {
        val map = FTUTTER_GESTURE_DEFAULTS
        assertEquals("更新", ftutterGestureLabel("201", map))
        assertEquals("なし", ftutterGestureLabel("12", map))
        assertNull(ftutterGestureLabel("2010", map))
        assertNull(ftutterGestureActionFor("2010", map))
        assertNull(ftutterGestureActionFor("12", map))
        assertEquals(FtutterGestureAction.REFRESH, ftutterGestureActionFor("201", map))
    }

    @Test
    fun aStartSteeperThanThirtyDegreesIsLeftToTheScroll() {
        // 縦スクロールの受理（slop）とパン（slopの2倍）の競合：sinθ > 1/2 で縦が先に勝つ。
        fun accepts(angleDeg: Double): Boolean? {
            val r = angleDeg * PI / 180
            var verdict: Boolean? = null
            var d = 0.0
            while (verdict == null) {
                d += 0.5
                verdict = ftutterGestureAccepts((d * cos(r)).toFloat(), (d * sin(r)).toFloat(), scrollSlop = 8f)
            }
            return verdict
        }
        assertEquals(true, accepts(25.0))
        assertEquals(false, accepts(35.0))
        assertNull(ftutterGestureAccepts(10f, 0f, 8f))
        assertEquals(true, ftutterGestureAccepts(17f, 0f, 8f))
    }

    @Test
    fun theStoredMappingIsReadWithDefaultsForTheRest() {
        val map = parseFtutterGestureMap(mapOf(FtutterKeys.gesture("12") to "11", FtutterKeys.gesture("1") to "99"))
        assertEquals(FtutterGestureAction.REFRESH, map["12"])
        assertEquals(FtutterGestureAction.PREVIOUS, map["1"])
        val s = parseFtutterGestureSettings(emptyMap())
        assertTrue(s.enabled)
        assertFalse(s.backByPreviousInCatalog)
        assertTrue(s.backByPreviousInThread)
    }

    @Test
    fun previousAndNextMoveBetweenTabsAndFavoriteBoards() {
        val tabs = listOf("a", "b", "c")
        assertEquals("a", ftutterAdjacentTab(tabs, "b", forward = false))
        assertEquals("c", ftutterAdjacentTab(tabs, "b", forward = true))
        assertNull(ftutterAdjacentTab(tabs, "c", forward = true))
        val favorites = listOf("x", "y")
        assertEquals(FtutterBoardStep.To("y"), ftutterBoardStep(favorites, "x", forward = true, backByPrevious = false))
        assertEquals(FtutterBoardStep.Stay, ftutterBoardStep(favorites, "x", forward = false, backByPrevious = false))
        assertEquals(FtutterBoardStep.Back, ftutterBoardStep(favorites, "x", forward = false, backByPrevious = true))
        assertEquals(FtutterBoardStep.Back, ftutterBoardStep(favorites, "z", forward = false, backByPrevious = true))
        assertEquals(FtutterBoardStep.Stay, ftutterBoardStep(favorites, "z", forward = true, backByPrevious = true))
    }

    @Test
    fun twoPaneNeedsTheSwitchAndAWidthStrictlyAboveTheThreshold() {
        val s = parseFtutterTabletSettings(mapOf(FtutterKeys.USE_TABLET to "true", FtutterKeys.TABLET_MIN_WIDTH to "600", FtutterKeys.DIVIDE_PERCENTAGE to "0"))
        assertFalse(ftutterIsTwoPane(s, 600f))
        assertTrue(ftutterIsTwoPane(s, 601f))
        assertEquals(20, s.leftPercent) // 原作は0で表示が崩れた
        assertFalse(ftutterIsTwoPane(parseFtutterTabletSettings(emptyMap()), 2000f))
    }
}
