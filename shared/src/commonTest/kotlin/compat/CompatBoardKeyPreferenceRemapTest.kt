package com.valoser.futacha.shared.compat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P4-2: board keys stored inside settings follow a corrected board key. */
class CompatBoardKeyPreferenceRemapTest {
    private fun encode(rules: List<CompatWatchRule>) = encodeValidCompatWatchRules(rules)

    @Test
    fun boardScopedRulesMoveAndGlobalOrOtherBoardRulesStay() {
        val encoded = encode(
            listOf(
                CompatWatchRule("猫", boardKey = "old"),
                CompatWatchRule("犬"),
                CompatWatchRule("鳥", boardKey = "other", enabled = false),
                CompatWatchRule("魚", boardKey = "new")
            )
        )
        val moved = assertNotNullRemap(encoded)
        assertEquals(
            listOf(
                CompatWatchRule("猫", boardKey = "new"),
                CompatWatchRule("犬"),
                CompatWatchRule("鳥", boardKey = "other", enabled = false),
                CompatWatchRule("魚", boardKey = "new")
            ),
            compatWatchRules(mapOf(COMPAT_WATCH_RULES_KEY to moved))
        )
        assertEquals(listOf("猫", "犬", "魚"), compatWatchWordsForBoard(mapOf(COMPAT_WATCH_RULES_KEY to moved), "new"))
    }

    @Test
    fun aRuleAlreadyPresentForTheCorrectedBoardIsKeptOnce() {
        val encoded = encode(listOf(CompatWatchRule("猫", boardKey = "old"), CompatWatchRule("猫", boardKey = "new")))
        assertEquals(
            listOf(CompatWatchRule("猫", boardKey = "new")),
            compatWatchRules(mapOf(COMPAT_WATCH_RULES_KEY to assertNotNullRemap(encoded)))
        )
    }

    @Test
    fun nothingToMoveOrUnreadableValuesAreLeftAlone() {
        assertNull(remapCompatWatchRulesBoardKey(encode(listOf(CompatWatchRule("猫"))), "old", "new"))
        assertNull(remapCompatWatchRulesBoardKey("not json", "old", "new"))
        assertNull(remapCompatWatchRulesBoardKey(Json.encodeToString(emptyList<CompatWatchRule>()), "old", "new"))
    }

    @Test
    fun boardDefaultKeysCoverSubjectAndName() {
        assertEquals(
            listOf(compatBoardDefaultSubjectPreferenceKey("b"), compatBoardDefaultNamePreferenceKey("b")),
            compatBoardDefaultPreferenceKeys("b")
        )
    }

    private fun assertNotNullRemap(encoded: String): String =
        kotlin.test.assertNotNull(remapCompatWatchRulesBoardKey(encoded, "old", "new"))
}
