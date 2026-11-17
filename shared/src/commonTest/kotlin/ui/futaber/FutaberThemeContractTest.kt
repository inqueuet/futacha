package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * L-B2 (V01・V02・C13): the colours of ふたばー風 are futaber's, every text role reads at 4.5:1 or more on each ground it
 * is drawn on (docs/futaber-evolution-look-2026-10-31.md §3.1), and the theme list is the original's.
 */
class FutaberThemeContractTest {
    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= 4.5f, "$label: contrast $ratio < 4.5")
    }

    private val themes = listOf(
        "light" to FutaberTokens.light, "dark" to FutaberTokens.dark,
        "oldDark" to FutaberTokens.oldDark, "futaba" to FutaberTokens.futaba
    )

    /** Each text role with each ground it appears on (a role is only checked where it is drawn). */
    private fun pairs(c: FutaberColors): List<Triple<String, Color, Color>> = buildList {
        fun on(ground: String, bg: Color, vararg roles: Pair<String, Color>) =
            roles.forEach { (name, fg) -> add(Triple("$name on $ground", fg, bg)) }
        // The catalog: a row's title and count, also on a read row; the board address on the navigation bar.
        on("background", c.background, "catalogTitle" to c.catalogTitle, "accent" to c.accent, "body" to c.body,
            "meta" to c.meta, "link" to c.link, "iosBlue" to c.iosBlue, "destructive" to c.destructive)
        on("readRow", c.readRow, "catalogTitle" to c.catalogTitle, "accent" to c.accent)
        on("selection", c.selection, "onSelection" to c.onSelection)
        on("navBar", c.navBar, "navTitle" to c.navTitle, "onNavBar" to c.onNavBar)
        // The thread: the header (number, name, date, IP, reply count, そうだね), the text, quotes, deleted posts.
        on("threadBackground", c.threadBackground, "body" to c.body, "accent" to c.accent, "ordinal" to c.ordinal,
            "name" to c.name, "mail" to c.mail, "quote" to c.quote, "meta" to c.meta, "link" to c.link,
            "saidaneHigh" to c.saidaneHigh, "saidaneLow" to c.saidaneLow, "deleted" to c.deleted, "expiry" to c.expiry)
        // L-B3: a post in a quote bubble is drawn on the page's ground, with the same header and text roles.
        on("background (bubble)", c.background, "ordinal" to c.ordinal, "name" to c.name, "mail" to c.mail,
            "quote" to c.quote, "saidaneHigh" to c.saidaneHigh, "saidaneLow" to c.saidaneLow, "deleted" to c.deleted)
        // L-B3 (F22): "…頃消えます" on the grey footer after the last post.
        on("threadFooter", c.threadFooter, "expiry" to c.expiry)
        // The bars: icons, the tab strip's title and count.
        on("bar", c.bar, "icon" to c.icon, "catalogTitle" to c.catalogTitle, "meta" to c.meta, "link" to c.link,
            "body" to c.body, "accent" to c.accent)
        // This app's notices and filled controls.
        on("accent", c.accent, "onAccent" to c.onAccent)
        on("iosBlue", c.iosBlue, "onIosBlue" to c.onIosBlue)
        on("fill", c.fill, "body" to c.body, "accent" to c.accent)
        // The sheets: the heading, the items, the destructive item, the cancel.
        on("sheetGroup", c.sheetGroup, "sheetTitle" to c.sheetTitle, "iosBlueOnSheet" to c.iosBlueOnSheet,
            "destructiveOnSheet" to c.destructiveOnSheet)
        on("sheetCancel", c.sheetCancel, "iosBlueOnSheet" to c.iosBlueOnSheet)
        // L-B4: a sheet's row (and キャンセル, and an alert's button) while the finger is on it.
        on("sheetPressed", c.sheetPressed, "iosBlueOnSheet" to c.iosBlueOnSheet, "destructiveOnSheet" to c.destructiveOnSheet)
        // L-B4: the alert and the popover on the light grey box: the title, the message, the buttons, the chosen option.
        on("fill (alert, popover)", c.fill, "iosBlueOnSheet" to c.iosBlueOnSheet, "destructiveOnSheet" to c.destructiveOnSheet,
            "sheetTitle" to c.sheetTitle, "popoverSelected" to c.popoverSelected)
        // L-B4: the black callout menu.
        on("callout", c.callout, "onCallout" to c.onCallout)
        // L-B4: the alert's text field (white) with its placeholder and its text.
        on("background (alert field)", c.background, "sheetTitle" to c.sheetTitle, "body" to c.body)
        // The operation menu: the labels and the search field.
        on("menuCard", c.menuCard, "body" to c.body, "meta" to c.meta, "icon" to c.icon, "iosBlue" to c.iosBlue)
        on("menuSearchField", c.menuSearchField, "menuSearchPlaceholder" to c.menuSearchPlaceholder, "body" to c.body)
        // Loading... and 新着 N.
        on("hud", c.hud, "onHud" to c.onHud)
        // The settings table: the section headings on the page, the rows and their values, the links.
        // L-B9: the notes, the empty list's text and 「すべて削除」 (the sheet's darker blue: the light one is 4.15 on the grey) are on the grey page.
        on("settingsPage", c.settingsPage, "settingsHeader" to c.settingsHeader, "iosBlueOnSheet" to c.iosBlueOnSheet)
        on("settingsRow", c.settingsRow, "body" to c.body, "meta" to c.meta, "iosBlue" to c.iosBlue, "accent" to c.accent)
        // L-B10: the manage panel's heading words (「編集」「選択」「完了」「すべて削除」) on the white bar, an empty list's
        // message and the extra rows' notes on the grey page under the rows.
        on("bar (panel heading)", c.bar, "iosBlue" to c.iosBlue, "body" to c.body)
        on("settingsPage (panel)", c.settingsPage, "body" to c.body, "settingsHeader" to c.settingsHeader, "iosBlueOnSheet" to c.iosBlueOnSheet)
        // L-B10 (V30): the search bar's black 「キャンセル」 and the field's text and 「検索ワード」.
        on("bar (search)", c.bar, "catalogTitle" to c.catalogTitle)
        // The board list, always dark, with its facets behind the text.
        listOf("drawerBackground" to c.drawerBackground, "drawerShape" to c.drawerShape).forEach { (ground, bg) ->
            on(ground, bg, "drawerText" to c.drawerText, "drawerMeta" to c.drawerMeta, "drawerAccent" to c.drawerAccent)
        }
    }

    @Test
    fun everyTextRoleIsReadableOnEachGroundItIsDrawnOn() {
        themes.forEach { (name, c) ->
            pairs(c).forEach { (label, fg, bg) -> assertReadable("$name $label", fg, bg) }
        }
    }

    @Test
    fun theDefaultThemeIsTheOriginalsWhiteAndRed() {
        val c = FutaberTokens.light
        // White page and bars (the redesign's warm white #FBFAF7 is gone).
        listOf(c.background, c.threadBackground, c.bar, c.navBar, c.menuCard, c.settingsRow).forEach {
            assertEquals(Color.White, it)
        }
        // futaber's red #FF3B30 darkened in its own hue to 4.5:1 (#D60B00): the address, the counts, the title.
        assertEquals(Color(0xFFD60B00), c.accent)
        assertEquals(Color(0xFFD60B00), c.navTitle)
        assertEquals(Color(0xFF000000), c.catalogTitle)
        assertEquals(Color(0xFF333333), c.body)
        assertEquals(Color(0xFF327800), c.quote)
        assertEquals(Color(0xFFEEEEEE), c.readRow)
        assertEquals(Color(0xFFE5E5E5), c.barSeparator)
        // The original's dark theme: dark grey ground, cyan title.
        assertEquals(Color(0xFF1C2123), FutaberTokens.dark.background)
        assertEquals(Color(0xFF1D1F21), FutaberTokens.dark.bar)
        assertEquals(Color(0xFF00C4CF), FutaberTokens.dark.navTitle)
        assertEquals(Color(0xFF263941), FutaberTokens.dark.readRow)
    }

    @Test
    fun everyPosterColourIsReadableAndStablePerId() {
        themes.forEach { (name, c) ->
            futaberPosterColors(c.isDark).forEachIndexed { index, color ->
                assertReadable("$name poster colour #$index", color, c.threadBackground)
                assertReadable("$name poster colour #$index in a bubble", color, c.background)
            }
        }
        assertEquals(futaberPosterColor("abcd1234", false), futaberPosterColor("abcd1234", false))
        assertEquals(futaberPosterColor("abcd1234", true), futaberPosterColor("abcd1234", true))
    }

    @Test
    fun themeModesResolveToTheirOwnPalette() {
        assertEquals(FutaberTokens.light, FutaberTokens.resolve(FutaberThemeMode.System, systemDark = false))
        assertEquals(FutaberTokens.dark, FutaberTokens.resolve(FutaberThemeMode.System, systemDark = true))
        listOf(false, true).forEach { systemDark ->
            assertEquals(FutaberTokens.light, FutaberTokens.resolve(FutaberThemeMode.Light, systemDark))
            assertEquals(FutaberTokens.dark, FutaberTokens.resolve(FutaberThemeMode.Dark, systemDark))
            assertEquals(FutaberTokens.oldDark, FutaberTokens.resolve(FutaberThemeMode.OldDark, systemDark))
            assertEquals(FutaberTokens.futaba, FutaberTokens.resolve(FutaberThemeMode.Futaba, systemDark))
        }
        // Nothing saved is "follow the device", as before (L-B0 decides whether the default changes).
        assertEquals(FutaberThemeMode.System, FutaberThemeMode.fromPersistedValue(null))
        assertEquals(FutaberThemeMode.System, FutaberThemeMode.fromPersistedValue("unknown"))
        FutaberThemeMode.entries.forEach {
            assertEquals(it, FutaberThemeMode.fromPersistedValue(it.persistedValue))
        }
    }

    /** C13: futaber's four names in futaber's order under 「システムテーマ」, then this app's own; the saved values stay. */
    @Test
    fun theThemeListIsTheOriginalsFourThenFollowTheDevice() {
        assertEquals(
            listOf("デフォルト", "ダークテーマ (旧)", "ダークテーマ", "ふたば"),
            FutaberThemeMode.original.map { it.label }
        )
        assertEquals(FutaberThemeMode.original + FutaberThemeMode.System, FutaberThemeMode.entries.toList())
        assertEquals("端末の設定に合わせる", FutaberThemeMode.System.label)
        assertEquals(
            listOf("light", "olddark", "dark", "futaba", "system"),
            FutaberThemeMode.entries.map { it.persistedValue }
        )
    }

    @Test
    fun theStatusAreaGlyphsFollowTheGroundUnderThem() {
        // Dark glyphs on the white bars of デフォルト, light ones on the dark bars and ふたば's red top.
        assertTrue(FutaberTokens.light.navBar.luminance() > 0.5f)
        assertTrue(FutaberTokens.light.bar.luminance() > 0.5f)
        assertTrue(FutaberTokens.dark.navBar.luminance() < 0.5f)
        assertTrue(FutaberTokens.futaba.navBar.luminance() < 0.5f)
        assertTrue(FutaberTokens.futaba.bar.luminance() > 0.5f)
    }
}
