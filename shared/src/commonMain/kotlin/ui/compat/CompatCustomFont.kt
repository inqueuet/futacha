package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where every mode keeps the shared custom font (compat.design.dummyCustomFont names it). */
internal const val COMPAT_CUSTOM_FONT_DIRECTORY = "private/compat_font"

/** The files a mode looks for, in this order. Only one of them is kept. */
internal val COMPAT_CUSTOM_FONT_CANDIDATES =
    listOf("$COMPAT_CUSTOM_FONT_DIRECTORY/font.ttf", "$COMPAT_CUSTOM_FONT_DIRECTORY/font.otf")

/**
 * Counts the replacements of the shared font file in this process. A font replaced by one with the same extension
 * keeps the same path, so a loader keyed on the path alone kept drawing the old font until the app restarted.
 */
internal object CompatCustomFontRevision {
    private val state = MutableStateFlow(0)
    val value: StateFlow<Int> = state.asStateFlow()
    fun bump() = state.update { it + 1 }
}

/**
 * Makes [bytes] the shared font. The new file is written first (writeBytes replaces a file atomically on every
 * platform), so a failed write keeps the font that was there; only then is the file of the other extension removed,
 * so the start of the app cannot pick a stale one.
 */
internal suspend fun replaceCompatCustomFontFile(fileSystem: FileSystem, extension: String, bytes: ByteArray) {
    val target = "$COMPAT_CUSTOM_FONT_DIRECTORY/font.$extension"
    require(target in COMPAT_CUSTOM_FONT_CANDIDATES) { "フォントファイルではありません" }
    fileSystem.createDirectory(COMPAT_CUSTOM_FONT_DIRECTORY).getOrThrow()
    val existedBefore = fileSystem.exists(target)
    fileSystem.writeBytes(target, bytes).getOrThrow()
    try {
        COMPAT_CUSTOM_FONT_CANDIDATES.filter { it != target }.forEach { other ->
            if (fileSystem.exists(other)) fileSystem.delete(other).getOrThrow()
        }
    } catch (failure: Throwable) {
        // Two candidates would make the start of the app read the older one; take the new file back out instead,
        // which leaves the previous font (and its name in the preference) as they were.
        if (!existedBefore) fileSystem.delete(target)
        CompatCustomFontRevision.bump()
        throw failure
    }
    CompatCustomFontRevision.bump()
}

/** Removes the shared font (every mode goes back to its own letters). */
internal suspend fun removeCompatCustomFontFiles(fileSystem: FileSystem) {
    try {
        fileSystem.deleteRecursively(COMPAT_CUSTOM_FONT_DIRECTORY).getOrThrow()
    } finally {
        CompatCustomFontRevision.bump()
    }
}

/**
 * Loads the font selected in the compatibility settings.
 *
 * The old Android application applies the selected font to every compatibility
 * screen, including the drawer and viewer.  The file itself is platform
 * specific, so the actual loader lives beside the platform UI code. It is
 * loaded again whenever the shared file is replaced ([CompatCustomFontRevision]).
 */
@Composable
internal fun rememberCompatCustomFontFamily(path: String?): FontFamily? {
    val revision by CompatCustomFontRevision.value.collectAsState()
    return rememberCompatCustomFontFamilyFile(path, revision)
}

/** The platform loader of [path]; [revision] changes when the file at the same path was replaced. */
@Composable
internal expect fun rememberCompatCustomFontFamilyFile(path: String?, revision: Int): FontFamily?
