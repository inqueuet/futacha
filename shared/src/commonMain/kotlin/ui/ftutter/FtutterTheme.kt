package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.ui.theme.ApplyFutachaSystemBars

/** 明るさ（原作の `themeMode`）：0 ライト、1 ダーク、2 ダーク（背景真っ黒）。 */
enum class FtutterBrightness(val stored: Int) {
    LIGHT(0), DARK(1), BLACK(2);

    val isDark: Boolean get() = this != LIGHT

    companion object {
        fun fromStored(value: Int?): FtutterBrightness = entries.firstOrNull { it.stored == value } ?: LIGHT
    }
}

/** 原作の「メインカラー」19色。最後の #800000 は「ふたば配色」の選択肢。 */
internal const val FTUTTER_DEFAULT_MAIN_COLOR: Long = 0xFF2196F3L
internal const val FTUTTER_FUTABA_MAIN_COLOR: Long = 0xFF800000L

/** 原作の「色個別設定」。null の項目はテーマから算出する。 */
@Immutable
data class FtutterColorOverrides(
    val background: Long? = null,
    val text: Long? = null,
    val toolBar: Long? = null,
    val onToolBar: Long? = null,
    val onToolBarActive: Long? = null,
    val popUp: Long? = null,
    val highlight: Long? = null,
    val dividerCard: Long? = null,
    val onDividerCard: Long? = null,
    val inTab: Long? = null,
    val gesture: Long? = null,
    val scrollBar: Long? = null,
    val indicator: Long? = null,
    val outline: Long? = null,
    val kidoku: Long? = null,
    val midoku: Long? = null,
    val onMidoku: Long? = null,
    val headerText: Long? = null,
    val resuSuu: Long = 0xFF000000L,
    val onResuSuu: Long = 0xFFFFFFFFL,
    val pickUp: Long = 0xFFFF9800L,
    val onPickUp: Long = 0xFFFFFFFFL,
    val kakikomi: Long = 0xFFFF6E40L,
    val henshin: Long = 0xFF4CAF50L
)

/** 画面の色。原作の Setting_Design の各Providerと同じ規則で、ColorScheme と色個別設定から決める。 */
@Immutable
data class FtutterColors(
    val dark: Boolean,
    val background: Color,
    /** null はテーマ既定の文字色（原作の textColor が未設定の時）。 */
    val text: Color,
    val toolBar: Color,
    val onToolBar: Color,
    val onToolBarActive: Color,
    val popUp: Color,
    val highlight: Color,
    val dividerCard: Color,
    val onDividerCard: Color,
    val inTab: Color,
    val outline: Color,
    val kidoku: Color,
    val midoku: Color,
    val onMidoku: Color,
    val headerText: Color,
    val resuSuu: Color,
    val onResuSuu: Color,
    val pickUp: Color,
    val onPickUp: Color,
    val kakikomi: Color,
    val henshin: Color,
    val gesture: Color,
    val scrollBar: Color,
    val indicator: Color = gesture,
    /** 本文の色（原作の固定値）。 */
    val deleted: Color = Color(0xFFF44336),
    val link: Color = Color(0xFF03A9F4),
    val quote: Color = Color(0xFF789922)
)

/** 生成した19色の配列の役割の並び（FtutterSeedSchemes.kt の先頭の注記と同じ）。 */
private enum class Role {
    PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER,
    SECONDARY, ON_SECONDARY, SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER,
    TERTIARY, ON_TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER,
    ERROR, ON_ERROR, ERROR_CONTAINER, ON_ERROR_CONTAINER,
    SURFACE, ON_SURFACE, SURFACE_VARIANT, ON_SURFACE_VARIANT,
    OUTLINE, OUTLINE_VARIANT, SHADOW, SCRIM,
    INVERSE_SURFACE, INVERSE_ON_SURFACE, INVERSE_PRIMARY, SURFACE_TINT,
    SURFACE_DIM, SURFACE_BRIGHT,
    SURFACE_CONTAINER_LOWEST, SURFACE_CONTAINER_LOW, SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST
}

private fun LongArray.color(role: Role): Color = Color(this[role.ordinal].toInt())

/** メインカラーの配列の位置。候補に無い値（壊れた保存値など）は既定の青。 */
internal fun ftutterSeedIndex(mainColor: Long?): Int {
    val index = FTUTTER_SEED_COLORS.indexOf(mainColor ?: FTUTTER_DEFAULT_MAIN_COLOR)
    return if (index >= 0) index else FTUTTER_SEED_COLORS.indexOf(FTUTTER_DEFAULT_MAIN_COLOR)
}

/** 原作の ColorScheme.fromSeed(mainColor, brightness) に当たる配色。 */
internal fun ftutterColorScheme(mainColor: Long?, brightness: FtutterBrightness): ColorScheme {
    val roles = (if (brightness.isDark) FTUTTER_DARK_SCHEMES else FTUTTER_LIGHT_SCHEMES)[ftutterSeedIndex(mainColor)]
    val base = if (brightness.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = roles.color(Role.PRIMARY), onPrimary = roles.color(Role.ON_PRIMARY),
        primaryContainer = roles.color(Role.PRIMARY_CONTAINER), onPrimaryContainer = roles.color(Role.ON_PRIMARY_CONTAINER),
        secondary = roles.color(Role.SECONDARY), onSecondary = roles.color(Role.ON_SECONDARY),
        secondaryContainer = roles.color(Role.SECONDARY_CONTAINER), onSecondaryContainer = roles.color(Role.ON_SECONDARY_CONTAINER),
        tertiary = roles.color(Role.TERTIARY), onTertiary = roles.color(Role.ON_TERTIARY),
        tertiaryContainer = roles.color(Role.TERTIARY_CONTAINER), onTertiaryContainer = roles.color(Role.ON_TERTIARY_CONTAINER),
        error = roles.color(Role.ERROR), onError = roles.color(Role.ON_ERROR),
        errorContainer = roles.color(Role.ERROR_CONTAINER), onErrorContainer = roles.color(Role.ON_ERROR_CONTAINER),
        background = roles.color(Role.SURFACE), onBackground = roles.color(Role.ON_SURFACE),
        surface = roles.color(Role.SURFACE), onSurface = roles.color(Role.ON_SURFACE),
        surfaceVariant = roles.color(Role.SURFACE_VARIANT), onSurfaceVariant = roles.color(Role.ON_SURFACE_VARIANT),
        outline = roles.color(Role.OUTLINE), outlineVariant = roles.color(Role.OUTLINE_VARIANT),
        scrim = roles.color(Role.SCRIM),
        inverseSurface = roles.color(Role.INVERSE_SURFACE), inverseOnSurface = roles.color(Role.INVERSE_ON_SURFACE),
        inversePrimary = roles.color(Role.INVERSE_PRIMARY), surfaceTint = roles.color(Role.SURFACE_TINT),
        surfaceDim = roles.color(Role.SURFACE_DIM), surfaceBright = roles.color(Role.SURFACE_BRIGHT),
        surfaceContainerLowest = roles.color(Role.SURFACE_CONTAINER_LOWEST),
        surfaceContainerLow = roles.color(Role.SURFACE_CONTAINER_LOW),
        surfaceContainer = roles.color(Role.SURFACE_CONTAINER),
        surfaceContainerHigh = roles.color(Role.SURFACE_CONTAINER_HIGH),
        surfaceContainerHighest = roles.color(Role.SURFACE_CONTAINER_HIGHEST)
    )
}

/**
 * Flutter の ElevationOverlay.applySurfaceTint と同じ重ね方。原作のツールバー・ポップアップは elevation 2。
 * 不透明度は Flutter の表（1→0.05、3→0.08）を線形に補間した値。
 */
internal fun ftutterSurfaceTint(surface: Color, tint: Color, elevation: Float = 2f): Color {
    val opacity = when {
        elevation <= 0f -> 0f
        elevation <= 1f -> 0.05f * elevation
        elevation <= 3f -> 0.05f + (elevation - 1f) / 2f * 0.03f
        elevation <= 6f -> 0.08f + (elevation - 3f) / 3f * 0.03f
        else -> 0.11f
    }
    return tint.copy(alpha = opacity).compositeOver(surface)
}

private fun Long?.asColor(): Color? = this?.let { Color(it.toInt()) }

/** 原作の各Providerの算出規則（第6次調査§6、settings.md §3）。 */
internal fun ftutterColors(
    mainColor: Long?,
    brightness: FtutterBrightness,
    overrides: FtutterColorOverrides = FtutterColorOverrides()
): FtutterColors {
    val scheme = ftutterColorScheme(mainColor, brightness)
    val futaba = (mainColor ?: FTUTTER_DEFAULT_MAIN_COLOR) == FTUTTER_FUTABA_MAIN_COLOR && brightness == FtutterBrightness.LIGHT
    val maroon = Color(0xFF800000)
    val tinted = ftutterSurfaceTint(scheme.surface, scheme.surfaceTint)
    val background = overrides.background.asColor() ?: when {
        futaba -> Color(0xFFF0E0D6)
        brightness == FtutterBrightness.BLACK -> Color.Black
        else -> scheme.surface
    }
    val toolBar = overrides.toolBar.asColor() ?: if (futaba) Color(0xFFFFFFEE) else tinted
    return FtutterColors(
        dark = brightness.isDark,
        background = background,
        text = overrides.text.asColor() ?: if (futaba) maroon else scheme.onSurface,
        toolBar = toolBar,
        onToolBar = overrides.onToolBar.asColor() ?: if (futaba) maroon else scheme.onSurface,
        onToolBarActive = overrides.onToolBarActive.asColor() ?: scheme.primary,
        popUp = overrides.popUp.asColor() ?: tinted,
        highlight = overrides.highlight.asColor() ?: scheme.primary,
        dividerCard = overrides.dividerCard.asColor() ?: if (futaba) Color(0xFFEEAA88) else scheme.secondaryContainer,
        onDividerCard = overrides.onDividerCard.asColor() ?: if (futaba) maroon else scheme.onSecondaryContainer,
        inTab = overrides.inTab.asColor() ?: if (futaba) Color(0xFFFFFFEE) else scheme.surfaceContainerHighest,
        outline = overrides.outline.asColor() ?: scheme.outlineVariant,
        kidoku = overrides.kidoku.asColor() ?: scheme.primary,
        midoku = overrides.midoku.asColor() ?: scheme.primary,
        onMidoku = overrides.onMidoku.asColor() ?: scheme.onPrimary,
        headerText = overrides.headerText.asColor() ?: scheme.outline,
        resuSuu = Color(overrides.resuSuu.toInt()),
        onResuSuu = Color(overrides.onResuSuu.toInt()),
        pickUp = Color(overrides.pickUp.toInt()),
        onPickUp = Color(overrides.onPickUp.toInt()),
        kakikomi = Color(overrides.kakikomi.toInt()),
        henshin = Color(overrides.henshin.toInt()),
        gesture = overrides.gesture.asColor() ?: scheme.primary,
        scrollBar = overrides.scrollBar.asColor() ?: scheme.primary.copy(alpha = 0.7f),
        indicator = overrides.indicator.asColor() ?: scheme.primary
    )
}

internal val LocalFtutterFont = staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

internal val LocalFtutterColors = staticCompositionLocalOf { ftutterColors(null, FtutterBrightness.LIGHT) }

/**
 * ふたったー風モードの見た目。原作どおり ColorScheme.fromSeed の配色で、ツールバーとシステムバーは同じ色にする。
 * 原作はOSの明暗に従わず、設定の「明るさ」だけで決まる。
 */
/** The scheme as the settings name it, for a bug report (L35). */
internal fun ftutterBugReportThemeName(mainColor: Long?, brightness: FtutterBrightness): String {
    val color = mainColor?.let { "メインカラー #" + (it and 0xFFFFFF).toString(16).uppercase().padStart(6, '0') } ?: "メインカラー 標準"
    val light = when (brightness) {
        FtutterBrightness.LIGHT -> "ライトモード"
        FtutterBrightness.DARK -> "ダークモード"
        FtutterBrightness.BLACK -> "ダークモード（背景真っ黒）"
    }
    return "$color・$light"
}

@Composable
internal fun FtutterTheme(
    mainColor: Long?,
    brightness: FtutterBrightness,
    overrides: FtutterColorOverrides = FtutterColorOverrides(),
    disableStretch: Boolean = true,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    preferences: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit
) {
    val scheme = remember(mainColor, brightness) { ftutterColorScheme(mainColor, brightness) }
    val colors = remember(mainColor, brightness, overrides) { ftutterColors(mainColor, brightness, overrides) }
    // Material's theme locals also invalidate their descendants when these instances change.
    // Saving an unrelated preference must retain both theme instances.
    val materialScheme = remember(scheme, colors.background) { scheme.copy(background = colors.background) }
    val typography = remember(fontFamily) { com.valoser.futacha.shared.ui.compat.compatibilityTypography(fontFamily) }
    val barColor = colors.toolBar
    ApplyFutachaSystemBars(systemBarColor = barColor, useDarkIcons = barColor.luminance() > 0.5f)
    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalFtutterColors provides colors,
        LocalFtutterUseCustomTab provides preferences.ftutterBool(FtutterKeys.USE_CUSTOM_TAB, true),
        com.valoser.futacha.shared.ui.board.LocalBugReportThemeName provides ftutterBugReportThemeName(mainColor, brightness),
        androidx.compose.foundation.LocalOverscrollFactory provides if (disableStretch) null else androidx.compose.foundation.LocalOverscrollFactory.current
    ) {
        MaterialTheme(colorScheme = materialScheme, typography = typography) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                content()
                // Edge-to-edge hosts leave the system bars transparent; paint them with the bar colour.
                Box(Modifier.fillMaxSize()) {
                    Spacer(
                        Modifier.align(Alignment.TopStart).fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars).background(barColor)
                    )
                    Spacer(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(barColor)
                    )
                }
            }
        }
    }
}
