package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.compat.buildCompatLegacyRestorePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatLegacyRestorePlanTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)

    @Test
    fun legacyRestoreIsOneValidatedPayloadWithoutWorkspace() {
        val backup = CompatLegacyBackupData(
            fileType = "keyword",
            catalogWatchWords = listOf(CompatLegacyScopedWord("https://may.2chan.net/b/", "Watch")),
            catalogNgWords = listOf(
                CompatLegacyScopedWord(null, "ng"),
                CompatLegacyScopedWord("https://unknown.example/x/", "missing-board")
            ),
            threadNgHeaders = listOf(CompatLegacyScopedWord(null, "Header")),
            threadNgWords = listOf(CompatLegacyScopedWord(null, " "), CompatLegacyScopedWord(null, "word")),
            preferences = mapOf("compat.common.commonPrivacy" to "ON"),
            catalogSort = CompatCatalogSort.MANY
        )

        val plan = buildCompatLegacyRestorePlan(
            backups = listOf(backup),
            availableBoards = listOf(board),
            catalogPreferences = listOf(CompatCatalogPreference(board.key).copy(sort = CompatCatalogSort.MANY)),
            existingNgRuleIds = emptySet(),
            nowEpochMillis = 10L
        )

        assertEquals(1, plan.catalogExtractCount)
        assertEquals(1, plan.catalogNgCount)
        assertEquals(2, plan.threadNgCount)
        assertNull(plan.backup.workspace)
        assertTrue(plan.backup.tabs.isEmpty() && plan.backup.boards.isEmpty())
        assertEquals(mapOf("compat.common.commonPrivacy" to "ON"), plan.backup.preferences)
        assertEquals(CompatCatalogSort.MANY, plan.backup.catalogPreferences.single().sort)
        // The encoded payload is what the store imports in one transaction.
        val roundTrip = decodeCompatSettingsBackup(encodeCompatSettingsBackup(plan.backup))
        assertEquals(4, roundTrip.ngRules.size)
        assertNull(roundTrip.workspace)
    }

    @Test
    fun invalidPreferenceFailsBeforeAnythingIsApplied() {
        val backup = CompatLegacyBackupData(fileType = "setting", preferences = mapOf("other.key" to "x"))
        assertFailsWith<IllegalArgumentException> {
            buildCompatLegacyRestorePlan(listOf(backup), listOf(board), emptyList(), emptySet(), 1L)
        }
    }

    @Test
    fun newRulesBeyondTheNgLimitAreSkippedButExistingOnesAreUpdated() {
        val existingId = compatNgRuleId(CompatNgKind.THREAD_IGNORE, "*", "keep")
        val existing = (1 until MAX_COMPAT_NG_RULES).mapTo(mutableSetOf()) { "id-$it" } + existingId
        val backup = CompatLegacyBackupData(
            fileType = "keyword",
            threadNgWords = listOf(CompatLegacyScopedWord(null, "keep"), CompatLegacyScopedWord(null, "new"))
        )
        val plan = buildCompatLegacyRestorePlan(listOf(backup), listOf(board), emptyList(), existing, 1L)
        assertEquals(listOf(existingId), plan.backup.ngRules.map(CompatNgRule::id))
        assertEquals(1, plan.threadNgCount)
    }
}
