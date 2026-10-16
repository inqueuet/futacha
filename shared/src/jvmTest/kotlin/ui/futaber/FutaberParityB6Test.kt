package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.ui.compat.CompatCustomFontRevision
import com.valoser.futacha.shared.ui.compat.compatibilityTypography
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/** ふたばー風 B6：メディアのみ保存とカスタムフォントの拡張（どちらも既定OFF）、共通フォントの書き込み。 */
class FutaberParityB6Test {
    @Test
    fun bothExtensionsAreOffByDefaultAndReadTheirOwnKeys() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extMediaOnlySave)
        assertFalse(defaults.extCustomFont)
        val on = FutaberDisplaySettings.from(mapOf(
            "compat.futaber.ext.mediaOnlySave" to "ON", "compat.futaber.ext.customFont" to "ON"
        ))
        assertTrue(on.extMediaOnlySave)
        assertTrue(on.extCustomFont)
        assertEquals("compat.futaber.ext.mediaOnlySave", FutaberSettingKeys.EXT_MEDIA_ONLY_SAVE)
        assertEquals("compat.futaber.ext.customFont", FutaberSettingKeys.EXT_CUSTOM_FONT)
    }

    @Test
    fun withoutAFontTheTypographyIsTheSameObjectAndWithOneEveryRoleTakesIt() {
        val base = Typography()
        assertSame(base, compatibilityTypography(null, base))
        val font = FontFamily.Serif
        val themed = compatibilityTypography(font, base)
        assertEquals(font, themed.bodyLarge.fontFamily)
        assertEquals(font, themed.labelSmall.fontFamily)
        assertEquals(base.bodyLarge.fontSize, themed.bodyLarge.fontSize)
        assertEquals(base.bodyLarge.lineHeight, themed.bodyLarge.lineHeight)
    }

    @Test
    fun theSharedFontNameIsReadFromTheSharedKey() {
        assertEquals("compat.design.dummyCustomFont", FUTABER_CUSTOM_FONT_PREFERENCE_KEY)
        assertNull(futaberCustomFontName(emptyMap()))
        assertNull(futaberCustomFontName(mapOf(FUTABER_CUSTOM_FONT_PREFERENCE_KEY to "デフォルト")))
        assertEquals("a.ttf", futaberCustomFontName(mapOf(FUTABER_CUSTOM_FONT_PREFERENCE_KEY to "a.ttf")))
    }

    @Test
    fun aPickedFontReplacesTheSharedFileAndResetRemovesIt() = runBlocking {
        val directory = Files.createTempDirectory("futaber-font").toFile()
        val fileSystem = JvmFileSystem(directory)
        val store = DesktopCompatibilityStore(fileSystem)
        try {
            store.initialize()
            assertTrue(futaberSaveCustomFont(store, fileSystem, ImageData(byteArrayOf(1, 2), "One.OTF")).isSuccess)
            assertTrue(File(directory, "private/compat_font/font.otf").isFile)
            assertTrue(futaberSaveCustomFont(store, fileSystem, ImageData(byteArrayOf(3), "two.ttf")).isSuccess)
            // Only the last choice is kept, so the start of the app cannot pick a stale file.
            assertEquals(listOf("font.ttf"), File(directory, "private/compat_font").list()?.toList())
            assertEquals("two.ttf", store.loadPreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY))
            assertTrue(futaberSaveCustomFont(store, fileSystem, ImageData(byteArrayOf(4), "notes.txt")).isFailure)
            assertEquals("two.ttf", store.loadPreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY))
            assertTrue(futaberResetCustomFont(store, fileSystem).isSuccess)
            assertFalse(File(directory, "private/compat_font").exists())
            assertEquals("デフォルト", store.loadPreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY))
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun aFailedCopyKeepsTheFontInUse() = runBlocking {
        val directory = Files.createTempDirectory("futaber-font-fail").toFile()
        val fileSystem = JvmFileSystem(directory)
        val store = DesktopCompatibilityStore(fileSystem)
        try {
            store.initialize()
            assertTrue(futaberSaveCustomFont(store, fileSystem, ImageData(byteArrayOf(1, 2), "old.otf")).isSuccess)
            // The copy of the next font fails (a full disk, say): the old file was deleted before it was written.
            val failingWrite = object : FileSystem by fileSystem {
                override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> =
                    if (path.startsWith("private/compat_font/")) Result.failure(java.io.IOException("disk full"))
                    else fileSystem.writeBytes(path, bytes)
            }
            assertTrue(futaberSaveCustomFont(store, failingWrite, ImageData(byteArrayOf(9), "new.ttf")).isFailure)
            assertTrue(futaberSaveCustomFont(store, failingWrite, ImageData(byteArrayOf(9), "new.otf")).isFailure)
            assertEquals(listOf("font.otf"), File(directory, "private/compat_font").list()?.toList())
            assertEquals(listOf<Byte>(1, 2), File(directory, "private/compat_font/font.otf").readBytes().toList())
            assertEquals("old.otf", store.loadPreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY))
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun replacingTheFontWithOneOfTheSameExtensionTellsTheLoadersToReadItAgain() = runBlocking {
        val directory = Files.createTempDirectory("futaber-font-revision").toFile()
        val fileSystem = JvmFileSystem(directory)
        val store = DesktopCompatibilityStore(fileSystem)
        try {
            store.initialize()
            assertTrue(futaberSaveCustomFont(store, fileSystem, ImageData(byteArrayOf(1), "a.ttf")).isSuccess)
            val first = CompatCustomFontRevision.value.value
            // The same path ("font.ttf") holds another font now; a loader keyed on the path alone kept the old one.
            assertTrue(futaberSaveCustomFont(store, fileSystem, ImageData(byteArrayOf(2, 3), "b.ttf")).isSuccess)
            assertNotEquals(first, CompatCustomFontRevision.value.value)
            assertEquals(listOf<Byte>(2, 3), File(directory, "private/compat_font/font.ttf").readBytes().toList())
            val beforeReset = CompatCustomFontRevision.value.value
            assertTrue(futaberResetCustomFont(store, fileSystem).isSuccess)
            assertNotEquals(beforeReset, CompatCustomFontRevision.value.value)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }
}
