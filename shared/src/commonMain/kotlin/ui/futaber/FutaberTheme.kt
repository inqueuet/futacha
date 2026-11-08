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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.theme.ApplyFutachaSystemBars

/**
 * User choice for the ふたばー palette: the original's four themes under 「システムテーマ」, in its order, then this app's
 * "follow the device" (C13). The saved values are the ones written before (light, olddark, dark, futaba, system).
 */
enum class FutaberThemeMode(val persistedValue: String, val label: String) {
    /** The original's "デフォルト": white page and bars, red board title. */
    Light("light", "デフォルト"),
    /** The original's "ダークテーマ (旧)": a near-black page. */
    OldDark("olddark", "ダークテーマ (旧)"),
    /** The original's "ダークテーマ": dark grey page and bars, cyan title. */
    Dark("dark", "ダークテーマ"),
    /** The original's "ふたば": cream page, dark-red top bar, the colours of the board itself. */
    Futaba("futaba", "ふたば"),
    /** This app's own entry, last: デフォルト or ダークテーマ as the device is set. */
    System("system", "端末の設定に合わせる");

    companion object {
        /** The original's four, as listed under 「システムテーマ」. */
        val original: List<FutaberThemeMode> = listOf(Light, OldDark, Dark, Futaba)

        fun fromPersistedValue(value: String?): FutaberThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: System
    }
}

/**
 * Colour roles of the ふたばー風 mode, read from futaber 5.1.2 (the element lists and pixels of the device captures,
 * docs/futaber-evolution-look-2026-10-31.md §3.1 and appendix B). A text role whose surveyed value is under 4.5:1 on the
 * ground it is drawn on keeps its hue and is made darker (lighter on the dark themes); `FutaberThemeContractTest` checks
 * every role on every ground it appears on. These are separate from the ふたちゃ, としあき and ふたったー palettes.
 *
 * Roles the screens of this batch do not draw yet (the read row, the sheets, the HUD, the settings table, the panel's
 * category colours) are here already, so the later batches do not have to touch the theme again.
 */
@Immutable
data class FutaberColors(
    val isDark: Boolean,
    // ---- Grounds ----
    /** The catalog, the panel and the lists. */
    val background: Color,
    /** The thread's page (ふたば tints it differently from the catalog). */
    val threadBackground: Color,
    /** The bottom bar and the tab strip. */
    val bar: Color,
    /** The hairline over the bottom bar and the tab strip. */
    val barSeparator: Color,
    /** The catalog's navigation bar, the status area and the thread's title row. */
    val navBar: Color,
    /** The icons on [navBar]. */
    val onNavBar: Color,
    /** The board address on the catalog's navigation bar and the thread title under the status area. */
    val navTitle: Color,
    /** The hairline under the catalog's navigation bar. */
    val navSeparator: Color,
    /** The hairline under the thread's title row. */
    val statusSeparator: Color,
    /** A catalog row of a thread in the history. */
    val readRow: Color,
    /** The row being opened, and the text on it. */
    val selection: Color,
    val onSelection: Color,
    /** The rules between catalog rows and between posts. */
    val rowSeparator: Color,
    val postSeparator: Color,
    /** The ground between the cells of the catalog grid. */
    val gridGap: Color,
    /** Hairlines and borders of this app's own controls (dialogs, chips) where futaber has nothing to copy. */
    val separator: Color,
    /** A neutral fill: behind pictures that are still loading, fields and this app's own chips. */
    val fill: Color,
    // ---- Text ----
    /** A catalog row's title. */
    val catalogTitle: Color,
    /** A post's text. */
    val body: Color,
    /** Board address, reply counts, the thread title, a deleted post, many そうだね; this app's notices are on it. */
    val accent: Color,
    val onAccent: Color,
    /** A post's number in the thread (0, 1, ...). */
    val ordinal: Color,
    /** A poster's name. */
    val name: Color,
    /** A quoted line. */
    val quote: Color,
    /** Date, No., the tab strip's count and secondary text. */
    val meta: Color,
    /** A file name, the reply count, a link. */
    val link: Color,
    /** The "[sage]" of a post's mail field in the header (futaber draws it in a brighter blue than the links). */
    val mail: Color,
    /** そうだね: five or more, and fewer. */
    val saidaneHigh: Color,
    val saidaneLow: Color,
    /** The text of a deleted post. */
    val deleted: Color,
    /** "…頃消えます" at the end of the thread, on [threadFooter]. */
    val expiry: Color,
    /** The ground under the last post of a thread (futaber's table footer: "…頃消えます", later "引っ張って..."). */
    val threadFooter: Color,
    // ---- Controls ----
    /** The system blue of the sheets' items, "完了" and the settings' links: on white, and on a sheet's group. */
    val iosBlue: Color,
    val iosBlueOnSheet: Color,
    /** Text on a filled [iosBlue] control (send). */
    val onIosBlue: Color,
    /** "タブをすべて削除" and the like: on white, and on a sheet's group. */
    val destructive: Color,
    val destructiveOnSheet: Color,
    /** The line icons. */
    val icon: Color,
    /** A line icon that cannot be pressed: the same shape, lighter. */
    val iconDisabled: Color,
    /** The track of a switch that is on (not text). */
    val switchOn: Color,
    // ---- Over the screens ----
    /** The veil under the operation menu. */
    val scrim: Color,
    /** The lighter veil under the action sheets and the alerts (futaber: white behind them reads #CCCCCC, 20%). */
    val sheetScrim: Color,
    val sheetGroup: Color,
    val sheetTitle: Color,
    val sheetSeparator: Color,
    val sheetCancel: Color,
    /** A sheet's row while the finger is on it (the items keep 4.5:1 on it). */
    val sheetPressed: Color,
    /** The thin frame round a quote bubble's white page (futaber: #F4F4F4, 4pt). Not text. */
    val bubbleFrame: Color,
    /** The chosen option of a popover (futaber's light blue, darker to keep 4.5:1 on the popover's [fill]). */
    val popoverSelected: Color,
    /** The small black menu with an arrow (the camera's 最近の画像／ファイル／…), and its text. */
    val callout: Color,
    val onCallout: Color,
    val menuCard: Color,
    val menuSearchField: Color,
    val menuSearchPlaceholder: Color,
    val menuCircleBorder: Color,
    /** The box of "Loading..." and "新着 N", and its text. */
    val hud: Color,
    val onHud: Color,
    // ---- The settings table ----
    val settingsPage: Color,
    val settingsRow: Color,
    val settingsHeader: Color,
    val settingsSeparator: Color,
    /** The "›" at a row's end (not text). */
    val chevron: Color,
    // ---- The panel's categories (the fill of the chosen icon, not text) ----
    val categorySaved: Color,
    val categoryNotifications: Color,
    val categoryFavorites: Color,
    val categoryHistory: Color,
    val categoryTabs: Color,
    // ---- The board list (always dark, the original's menu wallpaper) ----
    val drawerBackground: Color,
    val drawerText: Color,
    val drawerMeta: Color,
    val drawerAccent: Color,
    val drawerShape: Color
)

object FutaberTokens {
    /**
     * The original's "デフォルト": white page and bars, red board address and counts, blue numbers, green quotes and
     * names, thin grey rules. Values under 4.5:1 are darkened in their own hue (red #FF3B30 → #D60B00, blue #0085B8 →
     * #007DAD, grey #969696 → #757575, link #4083A9 → #3C7B9F, yellow #E6AE00 → #936F00, system blue #007AFF → #0070EB).
     */
    val light = FutaberColors(
        isDark = false,
        background = Color(0xFFFFFFFF),
        threadBackground = Color(0xFFFFFFFF),
        bar = Color(0xFFFFFFFF),
        barSeparator = Color(0xFFE5E5E5),
        navBar = Color(0xFFFFFFFF),
        onNavBar = Color(0xFF1B1B1B),
        navTitle = Color(0xFFD60B00),
        navSeparator = Color(0xFFA4A4A4),
        statusSeparator = Color(0xFFB3B3B3),
        readRow = Color(0xFFEEEEEE),
        selection = Color(0xFF2F70E1),
        onSelection = Color(0xFFFFFFFF),
        rowSeparator = Color(0xFFEAEAEA),
        postSeparator = Color(0xFFD2D2DC),
        gridGap = Color(0xFFF0F0F0),
        separator = Color(0xFFC8C8C8),
        fill = Color(0xFFF2F2F2),
        catalogTitle = Color(0xFF000000),
        body = Color(0xFF333333),
        accent = Color(0xFFD60B00),
        onAccent = Color(0xFFFFFFFF),
        ordinal = Color(0xFF007DAD),
        name = Color(0xFF327800),
        quote = Color(0xFF327800),
        meta = Color(0xFF757575),
        link = Color(0xFF3C7B9F),
        // futaber's "[sage]" blue as read from the capture (4.64:1 on white).
        mail = Color(0xFF2F70E1),
        saidaneHigh = Color(0xFFD60B00),
        saidaneLow = Color(0xFF936F00),
        deleted = Color(0xFFD60B00),
        // futaber's #969696 on its grey footer (#F2F2F2) is 2.6:1; the same grey made darker to 4.7:1 there.
        expiry = Color(0xFF6B6B6B),
        threadFooter = Color(0xFFF2F2F2),
        iosBlue = Color(0xFF0070EB),
        // #0062CE (4.61:1 on the group) was too light for the pressed row; the same blue darker keeps 4.5:1 on both.
        iosBlueOnSheet = Color(0xFF005CC2),
        onIosBlue = Color(0xFFFFFFFF),
        destructive = Color(0xFFD60B00),
        destructiveOnSheet = Color(0xFFC00A00),
        icon = Color(0xFF1B1B1B),
        iconDisabled = Color(0xFFA0A0A0),
        switchOn = Color(0xFF34C759),
        scrim = Color(0x66000000),
        sheetScrim = Color(0x33000000),
        sheetGroup = Color(0xFFE4E6E6),
        sheetTitle = Color(0xFF656665),
        sheetSeparator = Color(0xFFC8C8C8),
        sheetCancel = Color(0xFFFFFFFF),
        sheetPressed = Color(0xFFDCDEDE),
        bubbleFrame = Color(0xFFF4F4F4),
        // futaber's #29B1BF (2.4:1 on #F5F5F5) darker in its hue.
        popoverSelected = Color(0xFF00737E),
        callout = Color(0xFF333333),
        onCallout = Color(0xFFFFFFFF),
        menuCard = Color(0xFFFFFFFF),
        menuSearchField = Color(0xFFF2F2F2),
        menuSearchPlaceholder = Color(0xFF616161),
        menuCircleBorder = Color(0xFFD9D9D9),
        hud = Color(0xFF757574),
        onHud = Color(0xFFFFFFFF),
        settingsPage = Color(0xFFF2F2F2),
        settingsRow = Color(0xFFFFFFFF),
        settingsHeader = Color(0xFF6D6D72),
        settingsSeparator = Color(0xFFC8C8C8),
        chevron = Color(0xFFC5C5C7),
        // Provisional: the saved box's green is read from the capture; the other four are the system colours until
        // the device capture (L-B0) gives them.
        categorySaved = Color(0xFF4CD964),
        categoryNotifications = Color(0xFFFF9500),
        categoryFavorites = Color(0xFFFFCC00),
        categoryHistory = Color(0xFF007AFF),
        categoryTabs = Color(0xFFFF3B30),
        drawerBackground = Color(0xFF1D1F21),
        drawerText = Color(0xFFFFFFFF),
        drawerMeta = Color(0xFFC4CACE),
        drawerAccent = Color(0xFF409CFF),
        drawerShape = Color(0xFF272B2F)
    )

    /**
     * The original's "ダークテーマ" (read from docs/references/futaber-2026-10-05/device/catalog-dark.png and
     * tab-switch-dark.png): dark grey page and bars, cyan title and counts, red-pink numbers. The read row, the rules
     * and the box of "新着 N" are read from the same pictures. Provisional until the device capture (L-B0): the
     * selection, the sheets, the operation menu, the settings table, the panel's categories, the name, the link and
     * the few-そうだね colour (the standard dark values of iOS, kept at 4.5:1).
     */
    val dark = FutaberColors(
        isDark = true,
        background = Color(0xFF1C2123),
        threadBackground = Color(0xFF1C2123),
        bar = Color(0xFF1D1F21),
        barSeparator = Color(0xFF343537),
        navBar = Color(0xFF1D1F21),
        onNavBar = Color(0xFFDADEDF),
        navTitle = Color(0xFF00C4CF),
        navSeparator = Color(0xFF373C3E),
        statusSeparator = Color(0xFF3E4244),
        readRow = Color(0xFF263941),
        selection = Color(0xFF2F70E1),
        onSelection = Color(0xFFFFFFFF),
        rowSeparator = Color(0xFF343537),
        postSeparator = Color(0xFF2C2E2F),
        gridGap = Color(0xFF14191C),
        separator = Color(0xFF38383A),
        fill = Color(0xFF2C2C2E),
        catalogTitle = Color(0xFFFFFFFF),
        body = Color(0xFFFFFFFF),
        accent = Color(0xFF00C4CF),
        onAccent = Color(0xFF002A2D),
        ordinal = Color(0xFFFE3F58),
        name = Color(0xFF529A1F),
        quote = Color(0xFF529A1F),
        meta = Color(0xFF898989),
        link = Color(0xFF5A9BC4),
        mail = Color(0xFF409CFF),
        saidaneHigh = Color(0xFF00C4CF),
        saidaneLow = Color(0xFFC99700),
        deleted = Color(0xFF33CCCC),
        expiry = Color(0xFF969696),
        threadFooter = Color(0xFF14191C),
        iosBlue = Color(0xFF409CFF),
        iosBlueOnSheet = Color(0xFF5AABFF),
        onIosBlue = Color(0xFF000000),
        destructive = Color(0xFFFF6961),
        destructiveOnSheet = Color(0xFFFF7B74),
        icon = Color(0xFFDADEDF),
        iconDisabled = Color(0xFF5C6063),
        switchOn = Color(0xFF30D158),
        scrim = Color(0x66000000),
        sheetScrim = Color(0x66000000),
        sheetGroup = Color(0xFF2C2C2E),
        sheetTitle = Color(0xFF98989D),
        sheetSeparator = Color(0xFF48484A),
        sheetCancel = Color(0xFF2C2C2E),
        sheetPressed = Color(0xFF38383A),
        bubbleFrame = Color(0xFF2A3033),
        popoverSelected = Color(0xFF5AC8D8),
        callout = Color(0xFF3A3A3C),
        onCallout = Color(0xFFFFFFFF),
        menuCard = Color(0xFF1D1F21),
        menuSearchField = Color(0xFF303234),
        menuSearchPlaceholder = Color(0xFFABABAB),
        menuCircleBorder = Color(0xFF48484A),
        hud = Color(0xFF4A4D54),
        onHud = Color(0xFFE0E0E1),
        settingsPage = Color(0xFF14191C),
        settingsRow = Color(0xFF1D1F21),
        settingsHeader = Color(0xFF8E8E93),
        settingsSeparator = Color(0xFF38383A),
        chevron = Color(0xFF5A5A5E),
        categorySaved = Color(0xFF30D158),
        categoryNotifications = Color(0xFFFF9F0A),
        categoryFavorites = Color(0xFFFFD60A),
        categoryHistory = Color(0xFF0A84FF),
        categoryTabs = Color(0xFFFF453A),
        drawerBackground = Color(0xFF1D1F21),
        drawerText = Color(0xFFFFFFFF),
        drawerMeta = Color(0xFFC4CACE),
        drawerAccent = Color(0xFF409CFF),
        drawerShape = Color(0xFF272B2F)
    )

    /**
     * "ダークテーマ (旧)", provisional: no capture of the original is left (L-B0 takes one). The values are those set while
     * the 2026-10-05 captures existed (before the 2026-10-06 redesign): the dark theme on a near-black page with a black
     * thread, orange numbers and lighter grey and green text.
     */
    val oldDark = dark.copy(
        background = Color(0xFF0D0D0D),
        threadBackground = Color(0xFF000000),
        threadFooter = Color(0xFF141414),
        gridGap = Color(0xFF191919),
        ordinal = Color(0xFFFF7A47),
        meta = Color(0xFFA0A0A0),
        quote = Color(0xFF7CB342),
        name = Color(0xFF7CB342)
    )

    /**
     * "ふたば", provisional the same way: the board's own colours as set from the 2026-10-05 captures (cream catalog,
     * pink-beige thread, maroon text and top bar), kept at 4.5:1 on the beige thread (the quote is darkened to #2D6B00, the few-そうだね yellow to #765800). Everything the
     * redesign added is gone; the bars stay white.
     */
    val futaba = light.copy(
        background = Color(0xFFFFFFEE),
        threadBackground = Color(0xFFF0E0D6),
        navBar = Color(0xFF800000),
        onNavBar = Color(0xFFFFFFFF),
        navTitle = Color(0xFFFFFFFF),
        readRow = Color(0xFFF0E0D6),
        rowSeparator = Color(0xFFE6D6C8),
        postSeparator = Color(0xFFD2C0B6),
        gridGap = Color(0xFFF0E0D6),
        separator = Color(0xFFD2C0B6),
        catalogTitle = Color(0xFF800000),
        body = Color(0xFF800000),
        accent = Color(0xFFCC0000),
        ordinal = Color(0xFFB71C1C),
        name = Color(0xFF2D6B00),
        quote = Color(0xFF2D6B00),
        meta = Color(0xFF6B4F4F),
        link = Color(0xFF0000EE),
        mail = Color(0xFF0000EE),
        saidaneHigh = Color(0xFFCC0000),
        saidaneLow = Color(0xFF765800),
        deleted = Color(0xFFCC0000),
        expiry = Color(0xFF6B4F4F),
        threadFooter = Color(0xFFFFFFEE),
        bubbleFrame = Color(0xFFF0E0D6)
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

/**
 * The status bar's colour asked for by a full-screen window over the mode while it is up (the picture list and the
 * viewer on iPhone, [futaberMediaDarkensStatusBar]); null leaves it to the screens. The glyphs follow it.
 */
internal class FutaberStatusBarOverride {
    var color: Color? by androidx.compose.runtime.mutableStateOf(null)
    /** L-B9: the band under the screen (navigation bar, home-indicator strip) while a table page is up; null = the bar. */
    var bottomColor: Color? by androidx.compose.runtime.mutableStateOf(null)
}

internal val LocalFutaberStatusBarOverride = staticCompositionLocalOf<FutaberStatusBarOverride?> { null }

/**
 * The custom font of the theme, for the text fields whose own [androidx.compose.ui.text.TextStyle] does not take the
 * typography's font. Null (the default) is the same as not naming a font.
 */
val LocalFutaberCustomFont = staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

/**
 * The text styles of the mode (V28): no letter spacing anywhere, and a line height given for every style (relative to
 * the size of the text, so a text that sets its own size keeps the proportion). The Material defaults this replaces
 * spaced letters by up to 0.5sp and gave a 12sp text the 24sp line of the 16sp body. The custom font (拡張機能
 * 「カスタムフォント」) is carried into every style; null keeps the platform's font.
 */
internal fun futaberTypography(fontFamily: androidx.compose.ui.text.font.FontFamily?): Typography {
    val base = Typography()
    fun TextStyle.futaber(lineHeight: Float): TextStyle = copy(
        fontFamily = fontFamily ?: this.fontFamily,
        letterSpacing = 0.sp,
        lineHeight = lineHeight.em,
        lineHeightStyle = FUTABER_LINE_HEIGHT_STYLE
    )
    return Typography(
        displayLarge = base.displayLarge.futaber(FUTABER_HEADING_LINE_HEIGHT),
        displayMedium = base.displayMedium.futaber(FUTABER_HEADING_LINE_HEIGHT),
        displaySmall = base.displaySmall.futaber(FUTABER_HEADING_LINE_HEIGHT),
        headlineLarge = base.headlineLarge.futaber(FUTABER_HEADING_LINE_HEIGHT),
        headlineMedium = base.headlineMedium.futaber(FUTABER_HEADING_LINE_HEIGHT),
        headlineSmall = base.headlineSmall.futaber(FUTABER_HEADING_LINE_HEIGHT),
        titleLarge = base.titleLarge.futaber(FUTABER_LABEL_LINE_HEIGHT),
        titleMedium = base.titleMedium.futaber(FUTABER_LABEL_LINE_HEIGHT),
        titleSmall = base.titleSmall.futaber(FUTABER_LABEL_LINE_HEIGHT),
        // The body styles are what a Text without a style uses (a post's lines among them): 14 on about 20.
        bodyLarge = base.bodyLarge.futaber(FUTABER_BODY_LINE_HEIGHT),
        bodyMedium = base.bodyMedium.futaber(FUTABER_BODY_LINE_HEIGHT),
        bodySmall = base.bodySmall.futaber(FUTABER_LABEL_LINE_HEIGHT),
        labelLarge = base.labelLarge.futaber(FUTABER_LABEL_LINE_HEIGHT),
        labelMedium = base.labelMedium.futaber(FUTABER_LABEL_LINE_HEIGHT),
        labelSmall = base.labelSmall.futaber(FUTABER_LABEL_LINE_HEIGHT)
    )
}

/** A body line: 14 on about 20 (futaber's post text, 20〜21pt for 14pt). */
internal const val FUTABER_BODY_LINE_HEIGHT = 1.45f

/** Labels, headers and one-line rows: 12 on about 15 (the post header, the settings rows). */
internal const val FUTABER_LABEL_LINE_HEIGHT = 1.25f

/** Large headings. */
internal const val FUTABER_HEADING_LINE_HEIGHT = 1.2f

private val FUTABER_LINE_HEIGHT_STYLE = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

@Composable
fun FutaberTheme(
    mode: FutaberThemeMode,
    /**
     * True while the screen under the status area is the catalog or the thread, whose navigation bar (the catalog's
     * bar, the thread's title row) has its own colour; false for the screens that show the plain bar (settings, writing).
     */
    statusFollowsNavBar: Boolean = true,
    /** The shared custom font (拡張機能「カスタムフォント」); null keeps the platform's font. */
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colors = remember(mode, systemDark) { FutaberTokens.resolve(mode, systemDark) }
    val scheme = remember(colors) { futaberColorScheme(colors) }
    val typography = remember(fontFamily) { futaberTypography(fontFamily) }
    // The catalog and the thread show the navigation bar's colour up to the screen edge; the other screens the plain bar.
    val screenStatusColor = if (statusFollowsNavBar) colors.navBar else colors.bar
    val statusOverride = remember { FutaberStatusBarOverride() }
    val statusColor = statusOverride.color ?: screenStatusColor
    // The glyphs of each system bar follow the colour under them (ふたば: white glyphs on the red top, dark ones on the
    // white bottom bar). The shared function sets both bars from the status colour; the navigation bar is then set
    // from the bottom bar's colour.
    ApplyFutachaSystemBars(systemBarColor = statusColor, useDarkIcons = statusColor.luminance() > 0.5f)
    // L-B9: a table page (the settings) gives the band its grey page instead of the white bar.
    val bottomColor = statusOverride.bottomColor ?: colors.bar
    ApplyFutaberNavigationBar(bottomColor, darkIcons = bottomColor.luminance() > 0.5f, statusColor = statusColor)
    // iPhone: the home-indicator strip belongs to the host view; it takes the bottom bar's colour.
    ApplyFutaberHostBackground(bottomColor)
    CompositionLocalProvider(LocalFutaberColors provides colors, LocalFutaberCustomFont provides fontFamily,
        LocalFutaberStatusBarOverride provides statusOverride,
        com.valoser.futacha.shared.ui.board.LocalBugReportThemeName provides mode.label) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
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
                            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(bottomColor)
                    )
                }
            }
        }
    }
}

/** The Material roles the shared dialogs and controls read, filled from the mode's own roles. */
private fun futaberColorScheme(colors: FutaberColors): ColorScheme = if (colors.isDark) {
    darkColorScheme(
        primary = colors.iosBlue, onPrimary = colors.onIosBlue,
        secondary = colors.ordinal, onSecondary = colors.onAccent,
        tertiary = colors.quote, onTertiary = colors.onAccent,
        background = colors.background, onBackground = colors.body,
        surface = colors.bar, onSurface = colors.body,
        surfaceVariant = colors.fill, onSurfaceVariant = colors.meta,
        surfaceContainer = colors.bar, surfaceContainerHigh = colors.bar, surfaceContainerHighest = colors.fill,
        outline = colors.separator, error = colors.destructive
    )
} else {
    lightColorScheme(
        primary = colors.iosBlue, onPrimary = colors.onIosBlue,
        secondary = colors.ordinal, onSecondary = Color.White,
        tertiary = colors.quote, onTertiary = Color.White,
        background = colors.background, onBackground = colors.body,
        surface = colors.bar, onSurface = colors.body,
        surfaceVariant = colors.fill, onSurfaceVariant = colors.meta,
        surfaceContainer = colors.bar, surfaceContainerHigh = colors.bar, surfaceContainerHighest = colors.fill,
        outline = colors.separator, error = colors.destructive
    )
}
