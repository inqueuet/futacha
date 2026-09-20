package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

@Composable
internal actual fun rememberCompatCustomFontFamily(path: String?): FontFamily? = remember(path) {
    path?.let { runCatching { FontFamily(Font(File(it))) }.getOrNull() }
}
