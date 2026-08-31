package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Loads the font selected in the compatibility settings.
 *
 * The old Android application applies the selected font to every compatibility
 * screen, including the drawer and viewer.  The file itself is platform
 * specific, so the actual loader lives beside the platform UI code.
 */
@Composable
internal expect fun rememberCompatCustomFontFamily(path: String?): FontFamily?
