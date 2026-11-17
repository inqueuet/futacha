package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.ui.compat.CompatNgRuleManagementDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 参考アプリ 18-20: the NG word dialogs offer a 「正規表現」 check, refuse a pattern that cannot be read and label the rule. */
class CompatNgRegexInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val added = mutableListOf<Triple<String, Boolean, Boolean>>()

    private fun show(rules: List<CompatNgRule> = emptyList()) {
        rule.setContent {
            MaterialTheme {
                CompatNgRuleManagementDialog(
                    title = "ＮＧワード",
                    rules = rules,
                    onDelete = {},
                    onDeleteAll = {},
                    addScopeLabel = "全板に適用",
                    referenceKind = CompatNgKind.CATALOG_IGNORE,
                    onAdd = { value, global, regex -> added += Triple(value, global, regex) },
                    onEdit = { _, _, _, _, _ -> },
                    offerRegex = true,
                    onDismiss = {}
                )
            }
        }
        rule.waitForIdle()
    }

    @Test fun aPatternThatCannotBeReadIsRefusedAndAGoodOneIsAddedAsARegex() {
        show()
        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithText("・正規表現では上の2つを区別します", substring = true).assertExists()
        rule.onNodeWithTag("compat-catalog-ignore-add-regex").performClick()
        // A regular expression may be longer than the ten letters of a plain catalog word.
        rule.onNodeWithTag("compat-catalog-ignore-add-word").performTextInput("(abc")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("正規表現として解釈できません").assertExists()
        assertEquals(emptyList<Triple<String, Boolean, Boolean>>(), added)
        rule.onNodeWithTag("compat-catalog-ignore-add-word").performTextClearance()
        rule.onNodeWithTag("compat-catalog-ignore-add-word").performTextInput("^ABC[0-9]+スレです$")
        rule.onNodeWithText("追加する").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Triple("^ABC[0-9]+スレです$", false, true)), added)
    }

    @Test fun withoutTheCheckTheWordIsPlainAndLimitedAsBefore() {
        show()
        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithTag("compat-catalog-ignore-add-word").performTextInput("abcdefghijklmn")
        rule.onNodeWithText("追加する").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Triple("abcdefghij", false, false)), added)
    }

    @Test fun aRegexRuleIsListedWithTheLabel() {
        val regexRule = CompatNgRule(
            id = compatNgRuleId(CompatNgKind.CATALOG_IGNORE, "*", "^ABC$", true),
            kind = CompatNgKind.CATALOG_IGNORE,
            scopeKey = "*",
            normalizedValue = "^ABC$",
            createdAtEpochMillis = 1L,
            isRegex = true
        )
        show(listOf(regexRule))
        rule.onNodeWithText("［正規表現］^ABC$").assertExists()
    }
}
