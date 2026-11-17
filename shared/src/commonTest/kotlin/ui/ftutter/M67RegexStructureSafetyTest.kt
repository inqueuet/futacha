package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Existing classifier API only; no dangerous ICU matching input is executed by these tests. */
class M67RegexStructureSafetyTest {
    @Test fun symbolAndNegatedNestedRepetitionIsNotDeclaredSafeByTheOldProbes() {
        for (pattern in listOf("([!！?？]+)+$", "(\\W+)+$", "([^a-z]+)+$")) {
            assertTrue(ftutterRegexIsTooSlow(Regex(pattern), pattern, stepLimit = 1.seconds), pattern)
        }
    }

    @Test fun quantifiedLazyUnicodeAndWrappedVariantsAreRejectedBeforeAnyEngineProbe() {
        val patterns = listOf(
            "([!?]{1,3}){2,}$", "([!?]{2,}){8}$", "([!?]+?)+?$", "(?i:([!?]+)+)$",
            "((?:[!?])+)+$", "(?<symbols>[!?]+)+$", "(\\p{P}+)+$", "([^\\p{L}]+)+$",
            "([!]+[!]+)+$", "([!]{2}[!]+)+$", "([^^]+)+$", "([]!?]+)+$", "([\\]!?]+)+$", "([!?]+)+(?=END)", "([!?]+)+(?!.*)",
            "(?x) ([!?]+) { 2, } $", "(?x)([!# comment\n]+)+$", "(?=([!?]+)+$)", "(?>(([!?]+)+END))", "(?:([!?]+)+END)++",
            "(😀+)+$", "(\\uD83D\\uDE00+)+$", "(\\Q😀\\E+)+$", "(\\c!+)+$", "(\\07+)+$",
            "((!|\\?)+)+$", "([!]+|[?]+)+$", "(?:[!]+[!！]+)+$", "(?:\\x21+!+)+$",
            "(?i)(a+A+)+$", "(?:!|!!)+$", "(?:[\\u2E80-\\u2EFF]+[\\u2EC0-\\u2F00]+)+$",
            "(?:\\Q!\\E+\\x21+)+$", "(\\W+|)+$", "(?:|[!?]+)+$",
            "(?:\\ud83d\\udca9+\\uD83D\\uDCA9+)+$", "(?:[!?]{0}|[!?]+)+$"
        )
        for (pattern in patterns) {
            val regex = ftutterRegexOrNull(pattern) ?: continue // Only engine-supported syntax is an existing contract.
            var probes = 0
            val rejected = ftutterRegexIsTooSlow(regex, pattern, input = { _, _ -> probes++; "" })
            assertTrue(rejected, pattern)
            assertEquals(0, probes, "A structurally dangerous pattern reached the engine: $pattern")
        }
    }

    @Test fun fixedLengthsDelimitersAndOrdinaryClassesRemainUsable() {
        val patterns = listOf(
            "[!！？?]+$", "\\W+$", "[^a-z]+$", "([!?]{2})+$", "([!?]+){1}$", "([!?]+)?$",
            "(?:a[!?]+)+$", "(?:[!?]+a)+$", "a+b", "(いぬ|ねこ)+", "\\d{3}", "^(\\s*)$",
            "[\\p{L}\\p{N}]+", "[a-z&&[^b]]+", "[(){}+?$]+", "\\([!?]+\\)\\+", "\\Q([!?]+)+$\\E",
            "(?:😀{2})+$", "(\\uD83D\\uDE00{2})+$", "(\\Q😀\\E{2})+$", "(\\c!{2})+$", "(\\07{2})+$",
            "(?:!|\\?)+$", "(?:(?:!|\\?){2})+$", "(?:a+!|b+\\?)+$",
            "(?:[!?]{1,2}){2}$", "(a?){2}$", "(?:!|!!){2}$", "(?i)(a+B+)+$",
            "(?:[!]+[a-z]+)+$", "(?:[a-f]+[g-l]+)+$", "(?:|!){2}$"
        )
        patterns.forEach { assertAllowed(it) }
    }

    @Test fun literalCaseFoldOverlapPreservesScopedFlagsAndUnicodeEngineSemantics() {
        // Structural inspection may compile single atoms, but never executes these
        // ambiguous whole expressions. Case folding must survive literal quoting.
        listOf("(?i)(a+A+)+$", "(?i:(A+a+)+)$", "(?i)(\\Qa\\E+A+)+$",
            "(?i)((?:a)+A+)+$", "(?i)([a]+A+)+$"
        ).forEach { pattern -> assertTrue(ftutterRegexHasAmbiguousNestedRepetition(pattern), pattern) }
        listOf("(a+A+)+$", "(?i)(a+B+)+$", "(?i)((?-i:a+A+))+$",
            "(?i:a+)(?-i:A+)$", "(?i)((?>a+)A+)+$", "(?i)(a++A+)+$",
            "(?:é+β+)+$", "(?:😀+！+)+$"
        ).forEach { pattern -> assertFalse(ftutterRegexHasAmbiguousNestedRepetition(pattern), pattern) }

        // JVM inline i folds ASCII by default, while Native folds Unicode too.
        // Compare only one unquoted atom on one code point to retain that contract.
        for ((lower, upper) in listOf("é" to "É", "β" to "Β", "ж" to "Ж")) {
            val folds = Regex("(?i:$lower)").matches(upper)
            val pattern = "(?i)($lower+$upper+)+$"
            assertEquals(folds, ftutterRegexHasAmbiguousNestedRepetition(pattern), pattern)
            assertFalse(ftutterRegexHasAmbiguousNestedRepetition("($lower+$upper+)+$"), pattern)
        }
    }

    @Test fun possessiveAtomicAndHarmlessLookaroundDoNotLoseTheirBacktrackingBarriers() {
        listOf("([!?]++)+$", "([!?]+)++$", "(?>[!?]+)+$", "(?>([!?]+)+)$", "([!?]+)+",
            "^([!?]+)+", "(?=![?]+$)![?]+$", "(?<=ID:)[^ ]+", "(?!blocked)[!]+$",
            "(?x: a [!?]+ )+$", "(?x)\\Q([!?]+)+$\\E # quoted text\n",
            "(?:(?:[!?]+)+$){0}literal"
        ).forEach { assertAllowed(it) }
    }

    @Test fun registrationAndBackupUseTheSameStructuralRejection() {
        val unsafe = listOf("([!?]+)+$", "(\\W+)+$", "([^a-z]+)+$")
            .map { FtutterNgRule(it, FtutterNgType.TITLE.stored, isReg = true) }
        unsafe.forEach { assertEquals(FTUTTER_NG_REGEX_TOO_SLOW, ftutterNgValidationError(it, emptyList()), it.text) }
        val normal = FtutterNgRule("[!?]+$", FtutterNgType.TITLE.stored, isReg = true)
        val raw = (unsafe + normal).map { encodeFtutterNgList(listOf(it)).drop(1).dropLast(1) }
        val plan = ftutterBackupPlan(mapOf("NGList" to FtutterPrefValue.Texts(raw)), emptyList(), emptyList(), emptyMap(), 1)
        assertEquals(unsafe.size, plan.rejectedNg)
        assertEquals(listOf(normal), parseFtutterNgList(plan.preferences[FtutterKeys.NG_LIST]))
    }

    @Test fun savedTitleWordAndPickupRulesAreSkippedBeforeMatchingEvenShortText() {
        val pattern = "([^a-z]+)+$"
        val rules = listOf(FtutterNgType.TITLE, FtutterNgType.WORD, FtutterNgType.PICKUP)
            .map { FtutterNgRule(pattern, it.stored, isReg = true) }
        val restored = parseFtutterNgList(encodeFtutterNgList(rules))
        val matcher = FtutterNgMatcher(restored, null)
        assertFalse(matcher.hidesTitle("!!!"))
        assertFalse(matcher.hidesPost(Post("1", 0, "fixture", null, "now", messageHtml = "!!!", imageUrl = null, thumbnailUrl = null)))
        assertNull(matcher.pickupWord("!!!"))
        assertEquals(rules, matcher.slowRules)
        assertTrue(ftutterIsSlowNgPattern(pattern))
        rules.forEach { assertNotNull(ftutterNgRuleProblem(it)) }
        // A new matcher must inherit the cached rejection, rather than starting native work again.
        assertFalse(FtutterNgMatcher(restored, null).hidesTitle("!!!"))
    }

    private fun assertAllowed(pattern: String) {
        val regex = ftutterRegexOrNull(pattern) ?: return
        assertFalse(ftutterRegexIsTooSlow(regex, pattern, stepLimit = 1.seconds), pattern)
    }
}
