package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatibilitySettingsBackupTest {
    @Test
    fun settingsAndWordBackupsArePhysicallySeparate() {
        val backup = CompatSettingsBackup(
            exportedAtEpochMillis = 123L,
            boards = listOf(
                CompatBoard("may-b", "may", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
            ),
            preferences = mapOf(
                COMPAT_WATCH_WORDS_PREFERENCE_KEY to "foo\nbar",
                "compat.catalog.catalogThreadSize" to "300"
            ),
            ngRules = listOf(
                CompatNgRule(
                    id = "rule",
                    kind = CompatNgKind.CATALOG_WORD,
                    scopeKey = "may-b",
                    normalizedValue = "baz",
                    createdAtEpochMillis = 1L
                )
            )
        )

        val settings = decodeCompatSettingsBackup(encodeCompatSettingsBackup(backup.settingsOnly()))
        val words = decodeCompatSettingsBackup(encodeCompatSettingsBackup(backup.watchAndNgOnly()))

        assertEquals(mapOf("compat.catalog.catalogThreadSize" to "300"), settings.preferences)
        assertTrue(settings.ngRules.isEmpty())
        assertEquals(mapOf(COMPAT_WATCH_WORDS_PREFERENCE_KEY to "foo\nbar"), words.preferences)
        assertEquals(listOf("baz"), words.ngRules.map(CompatNgRule::normalizedValue))
        assertTrue(words.boards.isEmpty())
        assertTrue(words.tabs.isEmpty())
        assertTrue(words.history.isEmpty())

        val editablePayload = encodeCompatWatchNgBackup(backup)
        assertTrue("\"watchWords\"" in editablePayload)
        assertTrue("\"boards\"" !in editablePayload)
        assertTrue("\"workspace\"" !in editablePayload)
        val editable = decodeCompatWatchNgBackup(editablePayload)
        assertEquals("foo\nbar", editable.preferences[COMPAT_WATCH_WORDS_PREFERENCE_KEY])
        assertEquals(listOf("baz"), editable.ngRules.map(CompatNgRule::normalizedValue))
    }

    @Test
    fun filteringAnOldCombinedFileCannotLeakGeneralSettingsIntoWordRestore() {
        val oldCombined = CompatSettingsBackup(
            exportedAtEpochMillis = 456L,
            preferences = mapOf(
                COMPAT_WATCH_WORDS_PREFERENCE_KEY to "watch",
                "compat.design.designTheme" to "ブラック"
            )
        )

        val filtered = decodeCompatSettingsBackup(
            encodeCompatSettingsBackup(
                decodeCompatSettingsBackup(encodeCompatSettingsBackup(oldCombined)).watchAndNgOnly()
            )
        )

        assertEquals(setOf(COMPAT_WATCH_WORDS_PREFERENCE_KEY), filtered.preferences.keys)
    }
}
