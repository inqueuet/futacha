package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared across the 4 modes: the colour of a dice result (ふたばの`diceNdM=`の後・あいもげのダイス). */
internal const val DICE_COLOR_KEY = "compat.thread.diceColor"

internal data class DiceColorOption(val value: String, val label: String, val color: Color?)

/** The first is the default (red, as Futaba's own page shows it). 「本文と同じ」 colours nothing (bold only). */
internal val DICE_COLOR_OPTIONS = listOf(
    DiceColorOption("red", "赤", Color(0xFFE00000)),
    DiceColorOption("blue", "青", Color(0xFF1565C0)),
    DiceColorOption("green", "緑", Color(0xFF2E7D32)),
    DiceColorOption("orange", "オレンジ", Color(0xFFE65100)),
    DiceColorOption("purple", "紫", Color(0xFF7B1FA2)),
    DiceColorOption("none", "本文と同じ", null)
)

internal const val DICE_COLOR_NOTE = "4つのモードで共通の設定です。ダイスの結果の文字の色です。"

internal fun diceColorOption(preferences: Map<String, String>): DiceColorOption =
    DICE_COLOR_OPTIONS.firstOrNull { it.value == preferences[DICE_COLOR_KEY] } ?: DICE_COLOR_OPTIONS.first()

/** Read by every body renderer; each mode's root keeps it in step with [DICE_COLOR_KEY]. */
object DiceColorState {
    var color: Color? by mutableStateOf(DICE_COLOR_OPTIONS.first().color)
}

internal fun applyDiceColorPreference(preferences: Map<String, String>) {
    val color = diceColorOption(preferences).color
    if (DiceColorState.color != color) DiceColorState.color = color
}

/** The setting row: one chip per colour, the chosen one selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiceColorSetting(
    preferences: Map<String, String>,
    textColor: Color = Color.Unspecified,
    onSelect: (String) -> Unit
) {
    val chosen = diceColorOption(preferences)
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("dice-color-setting")) {
        Text("ダイスの結果の色", color = textColor)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DICE_COLOR_OPTIONS.forEach { option ->
                FilterChip(
                    selected = option == chosen,
                    onClick = { onSelect(option.value) },
                    modifier = Modifier.testTag("dice-color-${option.value}"),
                    label = { Text(option.label, color = option.color ?: textColor, fontWeight = FontWeight.Bold) }
                )
            }
        }
        Text(DICE_COLOR_NOTE, color = textColor)
    }
}
