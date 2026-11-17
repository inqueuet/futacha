package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only transient markers are pruned at the preference cap, never real settings, as on iOS (L84). */
class DesktopCompatibilityStorePreferenceBoundTest {
    private val cap = DESKTOP_COMPAT_MAX_PREFERENCES

    @Test
    fun loadKeepsRealSettingsAddedAfterManyTransientMarkers(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-preference-bound-load").toFile()
        val fs = JvmFileSystem(root)
        val transientCount = cap + 2_000
        val payload = buildJsonObject {
            put("version", 1)
            put("partitionedCaches", true)
            put("preferences", buildJsonObject {
                put("compat.thread.fontSize", JsonPrimitive("18"))
                repeat(transientCount) { index ->
                    put("compat.ownpost.compat_tab_$index.1", JsonPrimitive("1"))
                }
                put("compat.catalog.columns", JsonPrimitive("5"))
                put("compat.watch.enabled", JsonPrimitive("ON"))
            })
        }.toString()
        val database = DesktopCompatibilityDatabase(fs)
        try { database.writePayload(payload, 1L) } finally { database.close() }

        var store = DesktopCompatibilityStore(fs, nowMillis = { 1_000L })
        try {
            store.initialize()
            val loaded = store.preferences.first()
            assertEquals("18", loaded["compat.thread.fontSize"])
            assertEquals("5", loaded["compat.catalog.columns"])
            assertEquals("ON", loaded["compat.watch.enabled"])
            assertTrue(loaded.size <= cap)
            // The newest markers survive; the oldest are pruned first.
            assertTrue("compat.ownpost.compat_tab_${transientCount - 1}.1" in loaded)
            assertTrue("compat.ownpost.compat_tab_0.1" !in loaded)
            store.close()

            store = DesktopCompatibilityStore(fs, nowMillis = { 2_000L })
            store.initialize()
            val durable = store.preferences.first()
            assertEquals("5", durable["compat.catalog.columns"])
            assertEquals("ON", durable["compat.watch.enabled"])
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun runtimeSavesPruneTransientMarkersInsteadOfGrowingWithoutBound(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-preference-bound-runtime").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root), nowMillis = { 1_000L })
        try {
            store.initialize()
            store.savePreference("compat.thread.fontSize", "18")
            store.savePreferences(
                (0 until cap + 10).associate { index ->
                    "compat.catalog.lastFetchThreadCount.board$index.CATALOG" to "10"
                }
            )
            store.savePreference("compat.catalog.columns", "5")
            val current = store.preferences.first()
            assertTrue(current.size <= cap)
            assertEquals("18", current["compat.thread.fontSize"])
            assertEquals("5", current["compat.catalog.columns"])
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun realSettingsAreNeverPrunedEvenAboveTheSoftCap() {
        val many = (0 until cap + 5).associate { index -> "compat.real.$index" to "v" }
        assertEquals(many, boundCompatPreferences(many))
        assertTrue(isTransientCompatPreferenceKey("compat.ownpost.compat_tab_1.2"))
        assertTrue(!isTransientCompatPreferenceKey("compat.thread.fontSize"))
    }
}
