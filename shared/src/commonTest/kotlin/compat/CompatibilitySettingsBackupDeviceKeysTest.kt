package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatibilitySettingsBackupDeviceKeysTest {
    private val deviceKeys = listOf(
        "compat.storage.dummyDownloadDir",
        "compat.storage.dummyDrawingDir",
        "compat.storage.dummyImageCacheLocation",
        "compat.storage.dummyCatalogImageCacheLocation"
    )

    @Test
    fun exportLeavesOutDeviceSpecificFoldersAndUnsentFutaberDrafts() {
        val exported = compatBackupExportPreferences(
            deviceKeys.associateWith { "content://tree/device" } + mapOf(
                "compat.futaber.drafts" to "[unsent]",
                "compat.futaber.post.name" to "name",
                "compat.thread.threadFontSize" to "18"
            )
        )
        assertEquals(
            mapOf(
                "compat.futaber.post.name" to "name",
                "compat.thread.threadFontSize" to "18"
            ),
            exported
        )
    }

    @Test
    fun restoreNeverAppliesDeviceFoldersEvenFromAnOlderBackupThatHasThem() {
        val older = CompatSettingsBackup(
            exportedAtEpochMillis = 1L,
            preferences = deviceKeys.associateWith { "/other/device/path" } + mapOf(
                "compat.futaber.drafts" to "[stale]",
                "compat.common.commonPostDeleteKey" to "pass",
                "compat.thread.threadFontSize" to "18"
            )
        )
        val restored = decodeCompatSettingsBackup(encodeCompatSettingsBackup(older))
        assertTrue(deviceKeys.none { it in restored.preferences })
        assertTrue("compat.futaber.drafts" !in restored.preferences)
        // Unchanged behaviour: a delete key in an older backup is still restored.
        assertEquals("pass", restored.preferences["compat.common.commonPostDeleteKey"])
        assertEquals("18", restored.preferences["compat.thread.threadFontSize"])
    }
}
