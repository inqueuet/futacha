package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.THREAD_RELOAD_ON_OPEN_KEY
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Re-audit M30: an opened MHT file is always read from the file. Another copy on the device (a saved copy, the shared
 * snapshot) is never shown in its place, and with "開いた時に再読込" off the file's page still comes.
 */
class FutachaMhtLoadTest {
    @Test
    fun anOpenedMhtIsReadFromTheFileEvenWithTheReloadOff() = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-mht-load").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.savePreference(compatPreferenceStorageKey("catalog", THREAD_RELOAD_ON_OPEN_KEY), "OFF")
            val page = ThreadPage("123", null, null, null, emptyList())
            val callbacks = buildThreadLoadRunnerCallbacks(
                repository = FutachaStaticThreadRepository(FakeBoardRepository(), page),
                httpClient = null,
                fileSystem = null,
                threadId = "123",
                threadTitle = "title",
                boardUrl = "https://may.2chan.net/b/",
                archiveSearchJson = Json,
                offlineLookupContext = OfflineThreadLookupContext(listOf("b"), setOf("b")),
                offlineSources = emptyList(),
                currentThreadUrlOverride = { null },
                compatibilityStore = store
            )
            assertTrue(callbacks.reloadOnOpenEnabled())
            assertNull(callbacks.loadLocalStalePage())
            assertNull(callbacks.loadOfflineFallback())
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }
}
