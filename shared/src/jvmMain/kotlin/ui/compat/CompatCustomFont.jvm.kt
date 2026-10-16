package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberCompatCustomFontFamilyFile(path: String?, revision: Int): FontFamily? =
    // Read off the main thread, not in composition (re-audit L16: a replaced font was read whole while the screen was
    // being built). The last font stays until the new one is ready.
    produceState<FontFamily?>(initialValue = null, key1 = path, key2 = revision) {
        value = withContext(Dispatchers.IO) {
            path?.let {
                runCatching {
                    // The typeface cache is keyed on the font's identity (for a file font, its path), so a file replaced
                    // at the same path would keep drawing the old font: the font is named by the revision too, and read
                    // as bytes so a file removed later (another extension) does not take it away.
                    FontFamily(Font(identity = "$it#$revision", data = File(it).readBytes()))
                }.getOrNull()
            }
        }
    }.value
