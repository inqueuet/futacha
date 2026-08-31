package com.valoser.futacha.shared.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThreadSavePlatformProtectionTest {
    @Test
    fun secondUserSaveIsRejectedAndTheGateReopensAfterCompletion() = runBlocking {
        coroutineScope {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            // Kotlin/Native runBlocking does not pump a separately dispatched
            // child until the main test thread yields. Start immediately so the
            // gate is definitely held before this test awaits the signal.
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                runExclusiveUserThreadSave {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    "first-complete"
                }
            }

            firstStarted.await()
            val rejection = assertFailsWith<ThreadSaveAlreadyRunningException> {
                runExclusiveUserThreadSave { "should-not-run" }
            }
            assertEquals("別の保存を実行中です", rejection.message)

            releaseFirst.complete(Unit)
            assertEquals("first-complete", first.await())
            assertEquals(
                "third-complete",
                runExclusiveUserThreadSave { "third-complete" }
            )
        }
    }
}
