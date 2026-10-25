package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class IosNgRuleReplacementTest {
    @Test fun replacementAndRejectedEditsSurviveReopening(): Unit = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fs)
            store.initialize()
            val expected = verifyCompatNgReplacementPersistence(store)
            val reopened = IosCompatibilityStore(fs)
            reopened.initialize()
            assertEquals(expected, reopened.ngRules.first())
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }
}
