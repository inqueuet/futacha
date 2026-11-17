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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * aimg No.39: the size of the text outside posts and the catalog (the settings screens). Shared by the 4 modes; the
 * posts and the catalog keep their own sizes. Absent is 標準 (the screens as before).
 */
internal const val SETTINGS_FONT_SCALE_KEY = "compat.design.settingsFontScale"

internal data class SettingsFontScaleOption(val value: String, val label: String, val scale: Float)

internal val SETTINGS_FONT_SCALE_OPTIONS = listOf(
    SettingsFontScaleOption("90", "小", 0.9f),
    SettingsFontScaleOption("100", "標準", 1f),
    SettingsFontScaleOption("115", "大", 1.15f),
    SettingsFontScaleOption("130", "特大", 1.3f)
)

internal const val SETTINGS_FONT_SCALE_NOTE = "4つのモードで共通の設定です。設定画面の文字の大きさです。レス・カタログの文字は、それぞれの設定で変えます。"

internal fun settingsFontScaleOption(preferences: Map<String, String>): SettingsFontScaleOption =
    SETTINGS_FONT_SCALE_OPTIONS.firstOrNull { it.value == preferences[SETTINGS_FONT_SCALE_KEY] }
        ?: SETTINGS_FONT_SCALE_OPTIONS.first { it.scale == 1f }

/** [content] with its text scaled by the chosen size (on top of the system's own font scale). */
@Composable
internal fun SettingsFontScale(preferences: Map<String, String>, content: @Composable () -> Unit) {
    val scale = settingsFontScaleOption(preferences).scale
    if (scale == 1f) { content(); return }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsFontScaleSetting(
    preferences: Map<String, String>,
    textColor: Color = Color.Unspecified,
    onSelect: (String) -> Unit
) {
    val chosen = settingsFontScaleOption(preferences)
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("settings-font-scale-setting")) {
        Text("設定画面の文字の大きさ", color = textColor)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SETTINGS_FONT_SCALE_OPTIONS.forEach { option ->
                FilterChip(
                    selected = option == chosen,
                    onClick = { onSelect(option.value) },
                    modifier = Modifier.testTag("settings-font-scale-${option.value}"),
                    label = { Text(option.label, color = textColor) }
                )
            }
        }
        Text(SETTINGS_FONT_SCALE_NOTE, color = textColor)
    }
}
