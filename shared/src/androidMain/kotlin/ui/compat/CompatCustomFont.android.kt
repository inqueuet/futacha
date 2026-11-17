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
internal actual fun rememberCompatCustomFontFamilyFile(path: String?, revision: Int): FontFamily? {
    return produceState<FontFamily?>(initialValue = null, key1 = path, key2 = revision) {
        value = withContext(Dispatchers.IO) {
            path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { file -> file.isFile && file.length() in 1L..MAX_CUSTOM_FONT_BYTES }
                ?.let { file ->
                    // Loaded here, not when first drawn: a font replaced by one of the other extension removes this
                    // file, and a lazily loaded one fell back to the standard letters for a moment (re-audit L16).
                    runCatching {
                        FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.createFromFile(file)))
                    }.getOrNull()
                }
        }
    }.value
}
