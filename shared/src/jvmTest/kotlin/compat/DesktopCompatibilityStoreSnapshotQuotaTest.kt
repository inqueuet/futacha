package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The snapshot quota is checked from the sizes of the rows as written, as on iOS (L84). */
class DesktopCompatibilityStoreSnapshotQuotaTest {
    private var clock = 1_000L

    @Test
    fun savingASnapshotMeasuresOnlyTheNewOneAgainstTheQuota(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-snapshot-quota").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
        try {
            store.initialize()
            repeat(20) { index -> assertTrue(store.saveThreadSnapshot(snapshot(index))) }

            val before = store.snapshotSizeEncodings
            assertTrue(store.saveThreadSnapshot(snapshot(20)))
            // Only the new snapshot is serialized to be measured; the 20 unchanged ones use their rows.
            assertEquals(1L, store.snapshotSizeEncodings - before)

            // The usage those sizes add up to is the one measured from the rows after reopening.
            val usage = store.threadSnapshotCacheUsageBytes()
            assertTrue(usage > 0L)
            store.close()
            store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
            store.initialize()
            assertEquals(usage, store.threadSnapshotCacheUsageBytes())
        } finally { store.close(); root.deleteRecursively() }
    }

    private fun snapshot(index: Int) = CompatThreadSnapshot(
        tabKey = compatTabKey("https://may.2chan.net/b/res/${3_000 + index}.htm"),
        revision = 1L,
        fetchedAtEpochMillis = clock,
        posts = listOf(CompatPostSnapshot(position = 0, postNo = "$index", timestamp = "", messageHtml = "x".repeat(500)))
    )
}
