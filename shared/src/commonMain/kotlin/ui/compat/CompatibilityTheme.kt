package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily

@Immutable
internal data class CompatibilityPalette(
    val chrome: Color,
    val background: Color,
    val divider: Color,
    val chromeContent: Color = Color.White,
    val text: Color = Color.Black,
    /** Target getResHeaderSubjectHtmlColorByThemeKey(). */
    val headerSubject: Color = Color(0xFFCC1105),
    /** Target getResHeaderNameHtmlColorByThemeKey(). */
    val headerAuthor: Color = Color(0xFF117743),
    /** Target getResHeaderEmailHtmlColorByThemeKey(). */
    val headerEmail: Color = Color(0xFF005CE6),
    /** Target getResHeaderSelfPostHtmlColorByThemeKey(). */
    val headerSelfPost: Color = Color(0xFF005CE6),
    /** Target getResHeaderSelfQuoteHtmlColorByThemeKey(). */
    val headerSelfQuote: Color = Color(0xFFF48FB1),
    /** Target getResHeaderQuoteAddHtmlColorByThemeKey(). */
    val headerQuoteAdd: Color = Color(0xFF117743),
    /** Target getResHeaderSubTextHtmlColorByThemeKey(). */
    val headerSubtext: Color = Color(0xFF333333),
    /** Target getResHeaderIdTotalHtmlColorByThemeKey()/getResHeaderIpTotalHtmlColorByThemeKey(). */
    val identityTotal: Color = Color(0xFFCC1105),
    /** Target getResHeaderSoudaneHtmlColorByThemeKey(). */
    val saidane: Color = Color(0xFFE25E00),
    /** Target getResHeaderSoudaneMaxHtmlColorByThemeKey(). */
    val saidaneMax: Color = Color.Red,
    /** Target getResHeaderFileNameHtmlColorByThemeKey(). */
    val fileName: Color = Color(0xFF005CE6),
    /** ReplyClickableSpan's initial link colour in old.apk and 1.apk. */
    val bodyLink: Color = Color(0xFF009688),
    /** ReplyClickableSpan quote colour. */
    val bodyQuote: Color = Color(0xFF789922),
    /** ReplyClickableSpan imminent-deletion colour. */
    val bodyDropSoon: Color = Color(0xFFCC1105),
    /** ReplyClickableSpan IP-display colour. */
    val bodyIp: Color = Color(0xFF0000F0),
    /** ReplyClickableSpan erased-content colour. */
    val bodyErased: Color = Color(0xFFCCCCCC),
    /** Black PopupMenu/ListView surface from BlackPopupMenuListView. */
    val menuSurface: Color = Color(0xFFFAFAFA),
    /** Material dark AlertDialog surface used by the black reference theme. */
    val dialogSurface: Color = Color.White,
    val uiPrimaryText: Color = Color(0xFF4C4C4C),
    val uiSecondaryText: Color = Color(0xFF757575),
    /** Target getBackgroundColorHighlightByThemeKey(). */
    val searchResultBackground: Color = Color(0xFFE0F2F1),
    /** Target getBackgroundColorSearchHighlightByThemeKey(). */
    val searchTextHighlight: Color = Color(0xFFFFA500),
    /** Target's subdued new-reply marker, shared with its update notification. */
    val newReplyBackground: Color = Color(0xFF91CAC3),
    val newReplyContent: Color = Color.White,
    val statusBarChrome: Color? = null,
    val accent: Color = Color(0xFF26A69A),
    /** Text cursor/selection accent. Black is white in sample/1.apk. */
    val inputCursor: Color = accent,
    /** 1.apk's per-theme closedThreadUndoAction attribute. */
    val closedThreadUndoAction: Color = Color(0xFFFFF176),
    /** Dominant colour of cmn_loading_ico_* in old.apk and 1.apk. */
    val loadingIcon: Color = Color(0xFF00A499),
    /** Dominant colour of cmn_loading_prg_* in old.apk and 1.apk. */
    val loadingProgress: Color = Color(0xFF00A499)
)

internal fun compatibilityLoadingUsesIcon(style: String?): Boolean =
    style.equals("icon", ignoreCase = true) || style == "アイコン"

internal fun compatibilityLoadingColor(palette: CompatibilityPalette, style: String?): Color =
    if (compatibilityLoadingUsesIcon(style)) palette.loadingIcon else palette.loadingProgress

internal fun compatibilitySaidaneColor(
    palette: CompatibilityPalette,
    label: String?,
    threshold: Int
): Color {
    val count = label?.let { Regex("[0-9]+").find(it)?.value?.toIntOrNull() }
    return if (count != null && count >= threshold.coerceAtLeast(1)) palette.saidaneMax else palette.saidane
}

/** The old viewer uses a slightly darker strip behind status-bar icons. */
internal fun CompatibilityPalette.statusBarColor(): Color = statusBarChrome ?: Color(
        red = (chrome.red * 0.88f).coerceIn(0f, 1f),
        green = (chrome.green * 0.88f).coerceIn(0f, 1f),
        blue = (chrome.blue * 0.88f).coerceIn(0f, 1f),
        alpha = chrome.alpha
    )

internal fun compatibilityUsesDarkStatusBarIcons(palette: CompatibilityPalette): Boolean =
    palette.statusBarColor().luminance() > 0.5f

/** Keep every legacy popup on the selected theme's surface. */
internal fun compatibilityPopupSurface(palette: CompatibilityPalette): Color =
    palette.menuSurface

internal fun compatibilityPopupContent(palette: CompatibilityPalette): Color =
    palette.uiPrimaryText

internal fun compatibilitySettingsCategoryColor(palette: CompatibilityPalette): Color = palette.accent

@Composable
internal fun compatibilityMenuItemColors(): MenuItemColors {
    val content = compatibilityPopupContent(LocalCompatibilityPalette.current)
    return MenuDefaults.itemColors(
        textColor = content,
        leadingIconColor = content,
        trailingIconColor = content,
        disabledTextColor = content.copy(alpha = 0.38f),
        disabledLeadingIconColor = content.copy(alpha = 0.38f),
        disabledTrailingIconColor = content.copy(alpha = 0.38f)
    )
}

internal val LocalCompatibilityPalette = staticCompositionLocalOf {
    CompatibilityPalette(
        chrome = Color(0xFF009688),
        // sample/apk uses a neutral light-gray content surface.
        background = Color(0xFFE6E6E6),
        divider = Color(0xFFC7C7C7),
        text = Color(0xFF333333),
        statusBarChrome = Color(0xFF00867B)
    )
}

@Composable
internal fun CompatibilityProfileTheme(
    theme: String?,
    textColor: String? = null,
    navigationBarBackground: Boolean = false,
    customFontFamily: FontFamily? = null,
    content: @Composable () -> Unit
) {
    val palette = compatibilityPaletteFor(theme, textColor)
    // The legacy light themes use white PopupWindow/Dialog surfaces, while
    // the reference black theme keeps every surface dark.  Material3's
    // default lightColorScheme would otherwise make only the body dark and
    // leave overflow menus white with low-contrast text.
    val isBlack = palette.background == Color.Black
    val compatSurface = palette.dialogSurface
    val compatSurfaceVariant = if (isBlack) palette.menuSurface else Color(0xFFF4F4F4)
    val readableUiContent = compatibilityPopupContent(palette)
    CompositionLocalProvider(LocalCompatibilityPalette provides palette) {
        val navigationBarColor = if (navigationBarBackground) palette.background else Color.Black
        ApplyCompatSystemBars(
            statusBarColor = palette.statusBarColor(),
            navigationBarColor = navigationBarColor,
            useDarkStatusBarIcons = compatibilityUsesDarkStatusBarIcons(palette),
            useDarkNavigationBarIcons = navigationBarColor.luminance() > 0.5f
        )
        // The compatibility UI uses the old APK's light dialog/sheet surfaces as
        // well as its palette. Without an explicit scheme, the host app's dynamic
        // Material3 colors leak into compat mode (notably purple dialogs/buttons).
        MaterialTheme(
            colorScheme = lightColorScheme(
                // Menus in the reference APK are black-on-white. Screens that
                // need teal/white chrome set their colors explicitly.
                primary = compatibilityPopupContent(palette),
                onPrimary = palette.chromeContent,
                primaryContainer = palette.chrome,
                onPrimaryContainer = palette.chromeContent,
                background = palette.background,
                // The optional legacy text-colour preference applies to
                // thread/catalog content, not settings, dialogs or menus.
                // UI chrome must retain contrast even for white-on-light or
                // black-on-black user combinations.
                onBackground = readableUiContent,
                surface = compatSurface,
                onSurface = readableUiContent,
                surfaceVariant = compatSurfaceVariant,
                onSurfaceVariant = readableUiContent,
                surfaceDim = if (isBlack) palette.menuSurface else Color(0xFFE0E0E0),
                surfaceBright = compatSurface,
                surfaceContainerLowest = compatSurface,
                surfaceContainerLow = compatSurface,
                surfaceContainer = compatSurface,
                surfaceContainerHigh = compatSurface,
                surfaceContainerHighest = compatSurface,
                outline = palette.divider,
                outlineVariant = palette.divider,
                error = Color(0xFFB00020),
                onError = Color.White
            ),
            typography = compatibilityTypography(customFontFamily),
            content = content
        )
    }
}

/** Apply the selected legacy font to every Material text role used by compat UI. */
private fun compatibilityTypography(fontFamily: FontFamily?) : androidx.compose.material3.Typography {
    val base = androidx.compose.material3.Typography()
    if (fontFamily == null) return base
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily)
    )
}

internal fun compatibilityPaletteFor(theme: String?, textColor: String? = null): CompatibilityPalette {
    val base = when (theme?.lowercase()) {
        "mono", "モノクロ" -> CompatibilityPalette(
            Color(0xFF222222), Color(0xFFE6E6E6), Color(0xFFAAAAAA),
            text = Color(0xFF333333),
            searchResultBackground = Color(0xFFDDDDDD),
            statusBarChrome = Color(0xFF1E1E1E),
            accent = Color(0xFFAAAAAA),
            closedThreadUndoAction = Color(0xFFDDDDDD),
            loadingIcon = Color(0xFF2D2D2D),
            loadingProgress = Color(0xFF5D5D5D)
        )
        "futaba", "ふたば" -> CompatibilityPalette(
            Color(0xFF542D24), Color(0xFFFFFFEE), Color(0xFFF0E0D6),
            text = Color(0xFF800000),
            uiPrimaryText = Color(0xFF55332D),
            uiSecondaryText = Color(0xFF800000),
            searchResultBackground = Color(0xFFEEC0A3),
            statusBarChrome = Color(0xFF542D24),
            accent = Color(0xFF117743),
            closedThreadUndoAction = Color(0xFFFFCC00),
            loadingIcon = Color(0xFF673C2F),
            loadingProgress = Color(0xFF673C2F)
        )
        "blue", "ブルー" -> CompatibilityPalette(
            Color(0xFF03A9F4), Color(0xFFFBFBFB), Color(0xFFB7C9DE),
            text = Color(0xFF333333),
            searchResultBackground = Color(0xFFD4E7F0),
            statusBarChrome = Color(0xFF0288D1),
            accent = Color(0xFF39B7F0),
            loadingIcon = Color(0xFF00B8F6),
            loadingProgress = Color(0xFF00B8F6)
        )
        "pink", "ピンク" -> CompatibilityPalette(
            Color(0xFFE91E63), Color(0xFFFAFAFA), Color(0xFFE4B6C6),
            text = Color(0xFF333333),
            searchResultBackground = Color(0xFFF2E0E9),
            statusBarChrome = Color(0xFFC81955),
            accent = Color(0xFFFB4180),
            loadingIcon = Color(0xFFEF3A76),
            loadingProgress = Color(0xFFEF3A76)
        )
        "black", "ブラック" -> CompatibilityPalette(
            // The reference black theme is an OLED-black surface. Keeping
            // the body at #303134 leaves a visible light halo around catalog
            // thumbnails and makes the drawer/menu surfaces look like a
            // different theme.
            Color.Black, Color.Black, Color(0xFF333333),
            text = Color(0xFFBEBEBE),
            headerSubject = Color(0xFFFC1105),
            headerAuthor = Color(0xFF007551),
            headerEmail = Color(0xFF008CE6),
            headerSelfPost = Color(0xFF008CE6),
            headerSelfQuote = Color(0xFFF48FB1),
            headerQuoteAdd = Color(0xFF11A743),
            headerSubtext = Color(0xFFBEBEBE),
            identityTotal = Color(0xFFFC1105),
            saidane = Color(0xFFF99A00),
            fileName = Color(0xFF008CE6),
            menuSurface = Color(0xFF1E1E1E),
            dialogSurface = Color(0xFF424242),
            uiPrimaryText = Color(0xFFDDDDDD),
            uiSecondaryText = Color(0xFFAAAAAA),
            searchResultBackground = Color(0xFF3C3030),
            searchTextHighlight = Color(0xFF005AA1),
            statusBarChrome = Color(0xFF1E1E1E),
            accent = Color.White,
            loadingIcon = Color(0xFF2D2D2D),
            loadingProgress = Color.White
        )
        else -> CompatibilityPalette(
            Color(0xFF009688), Color(0xFFE6E6E6), Color(0xFFC7C7C7),
            text = Color(0xFF333333),
            statusBarChrome = Color(0xFF00867B)
        )
    }
    val resolvedText = when (textColor?.trim()) {
        "白" -> Color.White
        "薄い灰" -> Color(0xFFBDBDBD)
        "濃い灰" -> Color(0xFF555555)
        "黒" -> Color.Black
        else -> base.text
    }
    return base.copy(text = resolvedText)
}
