package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Audit D3: a pass waiting for a store that has not loaded does not hold the board lock. */
class SharedBoardsLoadWaitTest {
    private class Unloaded(val inner: CompatibilityStore) : CompatibilityStore by inner {
        val loaded = MutableStateFlow(false)
        override val isLoaded: StateFlow<Boolean> get() = loaded
    }

    @Test
    fun aRestoreGoesOnWhileAPassWaitsForTheLoad() = runBlocking {
        val root = Files.createTempDirectory("futacha-d3").toFile()
        try {
            val inner = DesktopCompatibilityStore(JvmFileSystem(root)).also { it.initialize() }
            val compat = Unloaded(inner)
            val state = AppStateStore(FakePlatformStateStorage())
            val pass = launch(Dispatchers.Default) { reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE) }
            kotlinx.coroutines.delay(200)
            // The pass is still waiting for the load; a restore must not wait for it.
            val restored = withTimeoutOrNull(5_000) { withSharedBoardsRestore(compat, state, keep = { false }) { "done" } }
            assertEquals("done", restored)
            assertNull(withTimeoutOrNull(100) { pass.join() }, "the pass still waits for the load")
            compat.loaded.value = true
            withTimeout(5_000) { pass.join() }
        } finally {
            root.deleteRecursively()
        }
    }
}
