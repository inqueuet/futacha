package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ColorScheme
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.ui.compat.CompatibilityPalette
import com.valoser.futacha.shared.ui.theme.FutachaChromeColors

/** Reused controls inherit Futacha's theme; legacy design preferences belong to Toshiaki. */
internal fun futachaSharedPalette(
    colors: ColorScheme,
    chrome: FutachaChromeColors,
    theme: ThemePalette
): CompatibilityPalette {
    val thread = resolveFutabaThreadColors(theme, colors)
    return CompatibilityPalette(
        chrome = chrome.topBar,
        chromeContent = chrome.onBar,
        statusBarChrome = chrome.systemBar,
        background = colors.background,
        divider = colors.outlineVariant,
        text = colors.onBackground,
        headerSubject = thread.accent,
        headerAuthor = thread.author,
        headerEmail = thread.link,
        headerSelfPost = thread.link,
        headerSelfQuote = colors.tertiary,
        headerQuoteAdd = thread.quote,
        headerSubtext = thread.footerText,
        identityTotal = thread.accent,
        saidane = thread.accent,
        saidaneMax = colors.error,
        fileName = thread.link,
        bodyLink = thread.link,
        bodyQuote = thread.quote,
        bodyDropSoon = colors.error,
        bodyIp = thread.link,
        bodyErased = colors.onSurfaceVariant,
        menuSurface = colors.surface,
        dialogSurface = colors.surface,
        uiPrimaryText = colors.onSurface,
        uiSecondaryText = colors.onSurfaceVariant,
        searchResultBackground = colors.secondaryContainer,
        searchTextHighlight = colors.tertiaryContainer,
        accent = thread.link,
        inputCursor = thread.link,
        closedThreadUndoAction = colors.inversePrimary,
        loadingIcon = thread.link,
        loadingProgress = thread.link
        // Keep the explicitly shared new-reply banner's original colors.
    )
}
