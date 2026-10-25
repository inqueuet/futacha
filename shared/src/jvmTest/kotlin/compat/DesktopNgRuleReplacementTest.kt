package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopNgRuleReplacementTest {
    @Test fun replacementAndRejectedEditsSurviveReopening() = runBlocking {
        val root = Files.createTempDirectory("ng-replacement").toFile()
        var store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val expected = verifyCompatNgReplacementPersistence(store)
            store.close()
            store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            assertEquals(expected, store.ngRules.first())
        } finally { store.close(); root.deleteRecursively() }
    }
}
