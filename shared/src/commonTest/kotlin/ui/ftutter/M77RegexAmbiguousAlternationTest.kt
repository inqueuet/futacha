package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * M77: alternatives that can match the same text, and failures inside a counted repetition, backtrack
 * like the nested variable repetitions M67 rejects. These must not reach a native (uninterruptible) engine.
 */
class M77RegexAmbiguousAlternationTest {
    @Test fun overlappingAlternativesAndCountedFailuresAreRejectedBeforeAnyEngineProbe() {
        val patterns = listOf(
            "(!|!)+$", "([^a-z]|\\W)+$", "(\\W|！)+$", "(.*！){20}", "(?:a|b|a)+$", "(?:[ab]|a|c)+$",
            "(?:！|[！？])+$", "(?:\\d|[0-9])+$", "(\\s|\\t)+$", "(?:[!?]|\\W)+(?=END)", "(?:(?:!|!))+$",
            "(.+！){5}", "(?i)(?:a|A)+$", "(?:！！|！[！])+$"
        )
        for (pattern in patterns) {
            val regex = ftutterRegexOrNull(pattern) ?: continue
            var probes = 0
            val rejected = ftutterRegexIsTooSlow(regex, pattern, input = { _, _ -> probes++; "" })
            assertTrue(rejected, pattern)
            assertEquals(0, probes, "A structurally dangerous pattern reached the engine: $pattern")
        }
    }

    @Test fun disjointAlternativesSmallCountsAndDelimitedRunsRemainUsable() {
        listOf(
            "(?:!|\\?)+$", "(いぬ|ねこ)+", "(ｗ|w){3,}", "(草|ｗ)+$", "(?:!|!!){2}$", "(!|!){3}$",
            "(ab|cd)+$", "(\\d+-){2}", "(?:a|b|c)+$", "(?:[ab]|c)+$", "(.*！)", "([!?]+){1}$",
            "(?:草|w|ｗ)+", "^(?:ID|IP):[^ ]+$"
        ).forEach { pattern ->
            val regex = ftutterRegexOrNull(pattern) ?: return@forEach
            assertFalse(ftutterRegexIsTooSlow(regex, pattern, stepLimit = 1.seconds), pattern)
        }
    }

    @Test fun theTimedProbeAlsoTriesTheSymbolsWrittenInThePattern() {
        // Structurally harmless, so it reaches the timed probe; that probe must use `！` itself.
        val pattern = "！+$"
        val probes = mutableListOf<String>()
        assertFalse(ftutterRegexIsTooSlow(Regex(pattern), pattern, input = { probe, _ -> probes += probe; probe }))
        assertTrue(probes.any { it.startsWith("！！") }, "the probe must include the pattern's own symbols")
    }
}
