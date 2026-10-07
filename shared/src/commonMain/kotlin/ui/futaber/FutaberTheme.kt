package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.ui.theme.ApplyFutachaSystemBars

/** User choice for the ふたばー palette. */
enum class FutaberThemeMode(val persistedValue: String, val label: String) {
    System("system", "端末の設定に合わせる"),
    Light("light", "明るい"),
    Dark("dark", "暗い"),
    /** The original's "ダークテーマ（旧）": a near-black page. */
    OldDark("olddark", "ダークテーマ（旧）"),
    /** The original's "ふたば": cream page, dark-red top bar, the colours of the board itself. */
    Futaba("futaba", "ふたば");

    companion object {
        fun fromPersistedValue(value: String?): FutaberThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: System
    }
}

/**
 * Colour roles of the ふたばー mode. The roles (white page, red board title, green quote,
 * blue ordinal) follow the surveyed app; the values are darkened where the surveyed
 * values fell below 4.5:1 so every text role stays readable (see
 * `FutaberThemeContractTest`). These are separate from the ふたちゃ and としあき palettes.
 */
@Immutable
data class FutaberColors(
    val isDark: Boolean,
    /** Page, list rows and bars. */
    val background: Color,
    /** The thread's page (the original's ふたば theme tints it differently from the catalog). */
    val threadBackground: Color,
    val bar: Color,
    /** The bar at the top of the catalog and the thread, the colour of the status area behind it, and what is drawn on it. */
    val topBar: Color,
    val onTopBar: Color,
    val topBarTitle: Color,
    /** Gap colour between catalog cells. */
    val catalogGap: Color,
    val body: Color,
    /** Board address, thread title, reply counts. */
    val accent: Color,
    /** Post ordinal (0, 1, ...). */
    val ordinal: Color,
    /** Date, post number and secondary text. */
    val meta: Color,
    val quote: Color,
    /** Deleted posts and things that went wrong: a red kept apart from the accent, which is the app's one brand colour. */
    val danger: Color,
    /** The そうだね count at the right end of a post header. */
    val saidane: Color,
    val link: Color,
    val icon: Color,
    val separator: Color,
    /** Filled controls (send, selected category) and the text on them. */
    val action: Color,
    val onAction: Color,
    val drawerBackground: Color,
    val drawerText: Color,
    val drawerMeta: Color,
    val drawerAccent: Color,
    val drawerShape: Color
)

object FutaberTokens {
    /**
     * The default look: a warm off-white page, white bars, thin warm-grey rules, and one teal accent.
     * Text roles keep 4.5:1 on the page (`FutaberThemeContractTest`).
     */
    val light = FutaberColors(
        isDark = false,
        background = Color(0xFFFBFAF7),
        threadBackground = Color(0xFFFBFAF7),
        bar = Color(0xFFFFFFFF),
        topBar = Color(0xFFFFFFFF),
        onTopBar = Color(0xFF2B3036),
        topBarTitle = Color(0xFF0E7C73),
        catalogGap = Color(0xFFF0EDE7),
        body = Color(0xFF20252B),
        accent = Color(0xFF0E7C73),
        ordinal = Color(0xFF0E7C73),
        meta = Color(0xFF666B72),
        quote = Color(0xFF2A7236),
        danger = Color(0xFFB3261E),
        saidane = Color(0xFF9A5B00),
        link = Color(0xFF1F5FA6),
        icon = Color(0xFF2B3036),
        separator = Color(0xFFE4E0D9),
        action = Color(0xFF0E7C73),
        onAction = Color(0xFFFFFFFF),
        drawerBackground = Color(0xFF1D1F21),
        drawerText = Color(0xFFFFFFFF),
        drawerMeta = Color(0xFFC4CACE),
        drawerAccent = Color(0xFF5FD0C4),
        drawerShape = Color(0xFF272B2F)
    )

    /** The default dark look: a deep neutral grey (not pure black), softened white text, the same teal made lighter. */
    val dark = FutaberColors(
        isDark = true,
        background = Color(0xFF15171A),
        threadBackground = Color(0xFF15171A),
        bar = Color(0xFF1C1F23),
        topBar = Color(0xFF1C1F23),
        onTopBar = Color(0xFFD6DADF),
        topBarTitle = Color(0xFF5FD0C4),
        catalogGap = Color(0xFF23272C),
        body = Color(0xFFE6E8EA),
        accent = Color(0xFF5FD0C4),
        ordinal = Color(0xFF5FD0C4),
        meta = Color(0xFF9AA1A9),
        quote = Color(0xFF7BCB8B),
        danger = Color(0xFFFF8A80),
        saidane = Color(0xFFF2B44D),
        link = Color(0xFF8CB8E8),
        icon = Color(0xFFD6DADF),
        separator = Color(0xFF2E3338),
        action = Color(0xFF5FD0C4),
        onAction = Color(0xFF00201D),
        drawerBackground = Color(0xFF1D1F21),
        drawerText = Color(0xFFFFFFFF),
        drawerMeta = Color(0xFFC4CACE),
        drawerAccent = Color(0xFF5FD0C4),
        drawerShape = Color(0xFF272B2F)
    )

    /**
     * "ダークテーマ（旧）": the near-black variant for OLED screens. It follows the same rules as the default dark
     * (one teal accent, softened white text, thin quiet rules) on a true-black thread page.
     */
    val oldDark = dark.copy(
        background = Color(0xFF0E0F10),
        threadBackground = Color(0xFF000000),
        bar = Color(0xFF141516),
        topBar = Color(0xFF141516),
        catalogGap = Color(0xFF1A1C1E),
        meta = Color(0xFF8E9399),
        separator = Color(0xFF26292D)
    )

    /**
     * "ふたば": the board's own colours (cream page, warm-beige thread, dark red) in the same calm style:
     * softer reds, a muted blue link, thin warm rules, and a top bar in the board's dark red.
     */
    val futaba = FutaberColors(
        isDark = false,
        background = Color(0xFFFFFAEB),
        threadBackground = Color(0xFFF6EADF),
        bar = Color(0xFFFFFDF6),
        topBar = Color(0xFF7B1E1E),
        onTopBar = Color(0xFFFFFFFF),
        topBarTitle = Color(0xFFFFFFFF),
        catalogGap = Color(0xFFF0E2D5),
        body = Color(0xFF5E1A1A),
        accent = Color(0xFFA32F2F),
        ordinal = Color(0xFFA32F2F),
        meta = Color(0xFF6E5550),
        quote = Color(0xFF3D6F1E),
        danger = Color(0xFFB3261E),
        saidane = Color(0xFF8A5200),
        link = Color(0xFF2B5C9E),
        icon = Color(0xFF3A2A28),
        separator = Color(0xFFE6D6C8),
        action = Color(0xFF7B1E1E),
        onAction = Color(0xFFFFFFFF),
        drawerBackground = Color(0xFF1D1F21),
        drawerText = Color(0xFFFFFFFF),
        drawerMeta = Color(0xFFC4CACE),
        drawerAccent = Color(0xFF5FD0C4),
        drawerShape = Color(0xFF272B2F)
    )

    fun resolve(mode: FutaberThemeMode, systemDark: Boolean): FutaberColors = when (mode) {
        FutaberThemeMode.System -> if (systemDark) dark else light
        FutaberThemeMode.Light -> light
        FutaberThemeMode.Dark -> dark
        FutaberThemeMode.OldDark -> oldDark
        FutaberThemeMode.Futaba -> futaba
    }
}

val LocalFutaberColors = staticCompositionLocalOf { FutaberTokens.light }

@Composable
fun FutaberTheme(
    mode: FutaberThemeMode,
    /**
     * True while the screen under the status area is the catalog or the thread, whose top bar has its own
     * colour; false for the screens that show the plain bar (settings, writing).
     */
    statusFollowsTopBar: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colors = remember(mode, systemDark) { FutaberTokens.resolve(mode, systemDark) }
    val scheme = remember(colors) {
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.accent, onPrimary = colors.onAction,
                secondary = colors.ordinal, onSecondary = colors.onAction,
                tertiary = colors.quote, onTertiary = colors.onAction,
                background = colors.background, onBackground = colors.body,
                surface = colors.bar, onSurface = colors.body,
                surfaceVariant = colors.catalogGap, onSurfaceVariant = colors.meta,
                surfaceContainer = colors.bar, surfaceContainerHigh = colors.bar, surfaceContainerHighest = colors.catalogGap,
                outline = colors.separator
            )
        } else {
            lightColorScheme(
                primary = colors.accent, onPrimary = Color.White,
                secondary = colors.ordinal, onSecondary = Color.White,
                tertiary = colors.quote, onTertiary = Color.White,
                background = colors.background, onBackground = colors.body,
                surface = colors.bar, onSurface = colors.body,
                surfaceVariant = colors.catalogGap, onSurfaceVariant = colors.meta,
                surfaceContainer = colors.bar, surfaceContainerHigh = colors.bar, surfaceContainerHighest = colors.catalogGap,
                outline = colors.separator
            )
        }
    }
    // The catalog and the thread show the top bar's colour up to the screen edge; the other screens show the plain bar.
    val statusColor = if (statusFollowsTopBar) colors.topBar else colors.bar
    ApplyFutachaSystemBars(systemBarColor = statusColor, useDarkIcons = statusColor.luminance() > 0.5f)
    ApplyFutaberHostBackground(statusColor)
    CompositionLocalProvider(LocalFutaberColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                content()
                // Edge-to-edge hosts leave the system bars transparent; paint them with the bar colour.
                Box(Modifier.fillMaxSize()) {
                    Spacer(
                        Modifier.align(Alignment.TopStart).fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars).background(statusColor)
                    )
                    Spacer(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(colors.bar)
                    )
                }
            }
        }
    }
}
