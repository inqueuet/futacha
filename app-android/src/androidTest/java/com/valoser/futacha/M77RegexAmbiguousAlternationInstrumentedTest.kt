@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import com.valoser.futacha.shared.ui.ftutter.ftutterRegexIsTooSlow
import com.valoser.futacha.shared.ui.ftutter.ftutterRegexOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * M77 on Android's ICU engine: atom overlap is decided by the engine itself (ICU `\s` includes U+3000),
 * and dangerous whole expressions are rejected without ever being matched natively.
 */
class M77RegexAmbiguousAlternationInstrumentedTest {
    @Test fun icuRejectsOverlappingAlternativesBeforeMatching() {
        listOf("(!|!)+$", "([^a-z]|\\W)+$", "(\\W|！)+$", "(.*！){20}", "(\\s|　)+$", "(?:！|[！？])+$").forEach { pattern ->
            val regex = ftutterRegexOrNull(pattern) ?: return@forEach
            var probes = 0
            assertTrue(pattern, ftutterRegexIsTooSlow(regex, pattern, input = { _, _ -> probes++; "" }))
            assertEquals("A dangerous pattern reached ICU: $pattern", 0, probes)
        }
    }

    @Test fun icuKeepsOrdinaryAlternativesUsable() {
        listOf("(?:!|\\?)+$", "(いぬ|ねこ)+", "(ｗ|w){3,}", "(草|ｗ)+$", "[!！？?]+$", "(ab|cd)+$").forEach { pattern ->
            val regex = ftutterRegexOrNull(pattern) ?: return@forEach
            assertFalse(pattern, ftutterRegexIsTooSlow(regex, pattern, stepLimit = 1.seconds))
        }
    }
}
