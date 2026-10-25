package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class IosLargeBoardRoundTripTest {
    @Test fun bootstrapKeepsAll201BoardsAcrossModeSwitchingAndReopening(): Unit = runBlocking { check(true) }
    @Test fun upsertKeepsAll201BoardsAcrossModeSwitchingAndReopening(): Unit = runBlocking { check(false) }

    private suspend fun check(bootstrap: Boolean) {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fs)
            store.initialize()
            verifyLargeBoardRoundTrip(store, bootstrap) { IosCompatibilityStore(fs).also { it.initialize() } }
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }
}
