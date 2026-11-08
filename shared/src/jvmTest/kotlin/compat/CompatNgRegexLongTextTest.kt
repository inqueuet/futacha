package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.ftutter.ftutterIsSlowNgPattern
import com.valoser.futacha.shared.ui.ftutter.ftutterRegexIsSafe
import com.valoser.futacha.shared.ui.ftutter.ftutterRegexIsTooSlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The 2026-11-08 audit's D1/D2: Android cannot stop a running match, and the short probe (256 characters)
 * let a pattern like `a*a*b` through — 15 ms at 256 characters but seconds at 2,000. A long post is now
 * matched only after the pattern was probed up to [COMPAT_NG_REGEX_MAX_TEXT_CHARS], and only its beginning.
 */
class CompatNgRegexLongTextTest {
    @Test fun aPatternFastOnShortTextsButCubicOnLongOnesIsRefusedBeforeALongPost() {
        val pattern = "k*k*m"
        val regex = Regex(pattern)
        assertTrue(ftutterRegexIsSafe(pattern, regex), "The short probe still lets it through")
        // Like Android: the plain string, so nothing can stop the probe from the inside.
        assertTrue(
            ftutterRegexIsTooSlow(
                regex, pattern,
                input = { probe, _ -> probe },
                lengths = COMPAT_NG_REGEX_LONG_PROBE_LENGTHS,
                stepLimitFor = ::compatLongProbeStepLimit
            ),
            "The long probe catches it"
        )
        val word = compatNgRegexWordOrNull(pattern)!!
        val started = TimeSource.Monotonic.markNow()
        assertFalse(word.matches("k".repeat(3_000) + "x"))
        assertTrue(started.elapsedNow() < 5.seconds, "Refused instead of matched: ${started.elapsedNow()}")
        assertTrue(ftutterIsSlowNgPattern(pattern), "Shown as not used in the NG list")
    }

    @Test fun anOrdinaryQuadraticPatternStaysUsableOnLongPosts() {
        val word = compatNgRegexWordOrNull("q+r")!!
        assertTrue(compatRegexIsSafeForLongText(word.pattern, word.regex))
        assertTrue(word.matches("q".repeat(3_000) + "r"))
        assertFalse(ftutterIsSlowNgPattern("q+r"))
    }

    @Test fun onlyTheBeginningOfAVeryLongPostIsMatched() {
        val word = compatNgRegexWordOrNull("合言葉[0-9]")!!
        assertTrue(word.matches("あ".repeat(100) + "合言葉1"))
        assertFalse(word.matches("あ".repeat(COMPAT_NG_REGEX_MAX_TEXT_CHARS) + "合言葉1"))
    }

    @Test fun aPatternIsCompiledOnceAndFindNamesThePartItFound() {
        assertSame(compatNgRegexWordOrNull("ウマ娘|競馬"), compatNgRegexWordOrNull("ウマ娘|競馬"))
        assertNull(compatNgRegexWordOrNull("(壊れた"))
        assertEquals("競馬", compatNgRegexWordOrNull("ウマ娘|競馬")!!.find("今日の競馬"))
        assertNull(compatNgRegexWordOrNull("ウマ娘|競馬")!!.find("今日の天気"))
    }

    @Test fun theLongProbeAllowsQuadraticGrowthUpToOneMatchsLimit() {
        assertEquals(listOf(512, 1024, 2048, 4096), COMPAT_NG_REGEX_LONG_PROBE_LENGTHS)
        assertEquals(100, compatLongProbeStepLimit(512).inWholeMilliseconds)
        assertEquals(200, compatLongProbeStepLimit(4096).inWholeMilliseconds)
    }
}
