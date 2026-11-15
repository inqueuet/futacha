package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Test

/** The watch snapshot is built from boards, history and watch words only; まとめカタログ definitions are never an input. */
class WatchSnapshotExcludesMixTest {
    @Test
    fun snapshotInputsCarryOnlyBoardsHistoryAndWatchWords() {
        val inputs = Class.forName("com.valoser.futacha.WatchSyncManager\$WatchSnapshotInputs")
        assertEquals(setOf("boards", "history", "watchWords"), inputs.declaredFields.map { it.name }.toSet())
    }

    @Test
    fun theWatchSyncSourceNeverTouchesMixDefinitions() {
        val src = java.io.File("src/main/java/com/valoser/futacha/WatchSyncManager.kt").readText()
        assertEquals(false, src.contains("compat.mix", ignoreCase = true) || src.contains("MixStore") || src.contains("MIX_DEFINITIONS"))
    }
}
