package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_CUSTOM_FONT_BYTES = 16L * 1024L * 1024L

@Composable
internal actual fun rememberCompatCustomFontFamily(path: String?): FontFamily? {
    return produceState<FontFamily?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { file -> file.isFile && file.length() in 1L..MAX_CUSTOM_FONT_BYTES }
                ?.let { file ->
                    runCatching { FontFamily(Font(file)) }.getOrNull()
                }
        }
    }.value
}
