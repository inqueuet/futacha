package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.reconcileCompatToolbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CompatM42ToolbarRecoveryTest {
    private val custom = listOf(CompatToolbarItem("privacy", 0, true))

    @Test fun catalogAndThreadFailuresBothProduceUsableDefaults() = runBlocking {
        for (failed in listOf(CompatToolbarSurface.CATALOG, CompatToolbarSurface.THREAD)) {
            val result = loadCompatToolbarsWithRecovery(emptyMap()) { surface ->
                if (surface == failed) error("test IO failure") else custom
            }
            assertEquals(CompatToolbarSurface.entries.toSet(), result.items.keys)
            assertEquals(setOf(failed), result.failedSurfaces)
            assertEquals(reconcileCompatToolbar(failed, emptyList()), result.items[failed])
            assertTrue(result.items.getValue(failed).any { it.key == "post" && it.active })
        }
    }

    @Test fun oneSurfaceFailureDoesNotDiscardOtherSuccessfullyLoadedToolbars() = runBlocking {
        val result = loadCompatToolbarsWithRecovery(emptyMap()) {
            if (it == CompatToolbarSurface.VIEWER) error("test IO failure") else custom
        }
        assertEquals(setOf(CompatToolbarSurface.VIEWER), result.failedSurfaces)
        assertSame(custom, result.items[CompatToolbarSurface.CATALOG])
        assertSame(custom, result.items[CompatToolbarSurface.THREAD])
    }

    @Test fun aLaterFailureKeepsTheLastSuccessfullyLoadedConfiguration() = runBlocking {
        val previous = CompatToolbarSurface.entries.associateWith { custom }
        val result = loadCompatToolbarsWithRecovery(previous) { error("test IO failure") }
        assertEquals(previous, result.items)
        assertEquals(CompatToolbarSurface.entries.toSet(), result.failedSurfaces)
    }

    @Test fun retryReplacesFallbacksWithTheSavedConfigurationAndClearsFailures() = runBlocking {
        val failed = loadCompatToolbarsWithRecovery(emptyMap()) { error("test IO failure") }
        val recovered = loadCompatToolbarsWithRecovery(failed.items) { custom }
        assertTrue(recovered.failedSurfaces.isEmpty())
        assertEquals(CompatToolbarSurface.entries.associateWith { custom }, recovered.items)
    }

    @Test fun cancellationPropagatesWithoutBeingPublishedAsADefaultConfiguration() = runBlocking<Unit> {
        val requested = mutableListOf<CompatToolbarSurface>()
        assertFailsWith<CancellationException> {
            loadCompatToolbarsWithRecovery(emptyMap()) {
                requested += it
                throw CancellationException("test cancellation")
            }
        }
        assertEquals(listOf(CompatToolbarSurface.CATALOG), requested)
    }

    @Test fun aSuccessfullyReadEmptyOrInactiveToolbarIsPreserved() = runBlocking {
        val inactive = custom.map { it.copy(active = false) }
        val result = loadCompatToolbarsWithRecovery(emptyMap()) {
            if (it == CompatToolbarSurface.CATALOG) emptyList() else inactive
        }
        assertTrue(result.failedSurfaces.isEmpty())
        assertEquals(emptyList(), result.items[CompatToolbarSurface.CATALOG])
        assertSame(inactive, result.items[CompatToolbarSurface.THREAD])
    }
}
