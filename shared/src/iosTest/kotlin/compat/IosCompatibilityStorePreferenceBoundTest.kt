package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosCompatibilityStorePreferenceBoundTest {
    @Test
    fun loadKeepsRealSettingsAddedAfterManyTransientMarkers() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            fileSystem.createDirectory("compatibility").getOrThrow()
            val transientCount = IOS_COMPAT_MAX_PREFERENCES + 2_000
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
            val database = IosCompatibilityDatabase(fileSystem)
            try {
                database.writePayload(payload, 1L)
            } finally {
                database.close()
            }

            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            val loaded = store.preferences.first()
            assertEquals("18", loaded["compat.thread.fontSize"])
            assertEquals("5", loaded["compat.catalog.columns"])
            assertEquals("ON", loaded["compat.watch.enabled"])
            assertTrue(loaded.size <= IOS_COMPAT_MAX_PREFERENCES)
            // The newest markers survive; the oldest are pruned first.
            assertTrue("compat.ownpost.compat_tab_${transientCount - 1}.1" in loaded)
            assertTrue("compat.ownpost.compat_tab_0.1" !in loaded)

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            reopened.initialize()
            val durable = reopened.preferences.first()
            assertEquals("5", durable["compat.catalog.columns"])
            assertEquals("ON", durable["compat.watch.enabled"])
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun runtimeSavesPruneTransientMarkersInsteadOfGrowingWithoutBound() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.savePreference("compat.thread.fontSize", "18")
            store.savePreferences(
                (0 until IOS_COMPAT_MAX_PREFERENCES + 10).associate { index ->
                    "compat.catalog.lastFetchThreadCount.board$index.CATALOG" to "10"
                }
            )
            store.savePreference("compat.catalog.columns", "5")
            val current = store.preferences.first()
            assertTrue(current.size <= IOS_COMPAT_MAX_PREFERENCES)
            assertEquals("18", current["compat.thread.fontSize"])
            assertEquals("5", current["compat.catalog.columns"])
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun realSettingsAreNeverPrunedEvenAboveTheSoftCap() {
        val many = (0 until IOS_COMPAT_MAX_PREFERENCES + 5).associate { index -> "compat.real.$index" to "v" }
        assertEquals(many, boundCompatPreferences(many))
        assertTrue(isTransientCompatPreferenceKey("compat.ownpost.compat_tab_1.2"))
        assertTrue(!isTransientCompatPreferenceKey("compat.thread.fontSize"))
    }
}
