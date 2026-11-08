package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.compatBackupExportPreferences
import com.valoser.futacha.shared.compat.compatBackupRestorePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The writing screen's "ペーストボード" keeps texts and pictures of this device (FutaberPasteboard.kt). */
class FutaberPasteboardTest {
    @Test fun keptTextsAreNewestFirstAndATextKeptAgainMovesUpInsteadOfDoubling() {
        val one = futaberPasteboardWithText(emptyList(), "定型１", "a")
        val two = futaberPasteboardWithText(one, "定型２", "b")
        assertEquals(listOf("定型２", "定型１"), two.map { it.text })
        val again = futaberPasteboardWithText(two, "定型１", "c")
        assertEquals(listOf("a", "b"), again.map { it.id })
        assertEquals(two, futaberPasteboardWithText(two, "  \n", "d"))
        assertEquals(FUTABER_PASTEBOARD_MAX_TEXT_CHARS, futaberPasteboardWithText(emptyList(), "あ".repeat(5_000), "e").single().text.length)
    }

    @Test fun theListSurvivesItsStoredFormAndDropsRowsItCannotUse() {
        val items = futaberPasteboardWithImage(
            futaberPasteboardWithText(emptyList(), "テンプレ", "t1"),
            futaberPasteboardImageLocator("i1", "res.png"), "res.png", "i1"
        )
        val (encoded, pushedOut) = encodeFutaberPasteboard(items)
        assertTrue(pushedOut.isEmpty())
        assertEquals(items, decodeFutaberPasteboard(encoded))
        // A picture outside this mode's folder (an edited value, another mode's draft file) is never read through the list.
        val foreign = """[{"id":"x","imageLocator":"private/compat_post_attachments/abc-def/a.png"},{"id":"y","text":"ok"}]"""
        assertEquals(listOf("y"), decodeFutaberPasteboard(foreign).map { it.id })
        assertTrue(decodeFutaberPasteboard("not json").isEmpty())
        assertTrue(decodeFutaberPasteboard(null).isEmpty())
    }

    @Test fun theOldestRowsGoWhenTheListWouldNotFitTheStoredValue() {
        var items = emptyList<FutaberPasteItem>()
        repeat(FUTABER_PASTEBOARD_MAX_ITEMS + 5) { index -> items = futaberPasteboardWithText(items, "長い定型文$index" + "あ".repeat(900), "id$index") }
        val (encoded, pushedOut) = encodeFutaberPasteboard(items)
        val kept = decodeFutaberPasteboard(encoded)
        assertTrue(encoded.length <= 18_000)
        assertEquals("id${FUTABER_PASTEBOARD_MAX_ITEMS + 4}", kept.first().id)
        assertEquals(items.size, kept.size + pushedOut.size)
        assertTrue(pushedOut.all { out -> kept.none { it.id == out.id } })
    }

    @Test fun picturesLiveInTheModesOwnFolderOnly() {
        val locator = futaberPasteboardImageLocator("lq3-1a", "レス画像.JPG")
        assertEquals("$FUTABER_PASTEBOARD_DIRECTORY/lq3-1a.jpg", locator)
        assertTrue(futaberOwnsPasteboardFile(locator))
        assertFalse(futaberOwnsPasteboardFile("$FUTABER_PASTEBOARD_DIRECTORY/../secret"))
        assertFalse(futaberOwnsPasteboardFile("$FUTABER_PASTEBOARD_DIRECTORY/a/b.png"))
        assertFalse(futaberOwnsPasteboardFile("private/compat_post_attachments/x/y.png"))
        assertEquals("$FUTABER_PASTEBOARD_DIRECTORY/item", futaberPasteboardImageLocator("../", "noext"))
    }

    @Test fun aKeptTextGoesOnItsOwnLineAfterWhatIsWritten() {
        assertEquals("定型", futaberInsertPaste("", "定型"))
        assertEquals("本文\n定型", futaberInsertPaste("本文", "定型"))
        assertEquals("本文\n定型", futaberInsertPaste("本文\n", "定型"))
    }

    @Test fun theListIsNeitherWrittenToNorRestoredFromABackup() {
        val preferences = mapOf(FutaberPreferenceKeys.PASTEBOARD to "[]", FutaberPreferenceKeys.THEME to "dark")
        assertEquals(setOf(FutaberPreferenceKeys.THEME), compatBackupExportPreferences(preferences).keys)
        assertEquals(setOf(FutaberPreferenceKeys.THEME), compatBackupRestorePreferences(preferences).keys)
    }
}
