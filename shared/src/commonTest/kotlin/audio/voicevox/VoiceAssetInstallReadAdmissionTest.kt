package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.AssetDistribution
import com.valoser.futacha.shared.download.AssetDownloader
import kotlinx.coroutines.*
import okio.BufferedSink
import okio.Path.Companion.toPath
import kotlin.test.*

class VoiceAssetInstallReadAdmissionTest {
    @Test fun anInstallWinningAfterPreparationFallsBackBeforeItsTransferCompletes() = runBlocking<Unit> {
        val downloading = CompletableDeferred<Unit>()
        val f = VoiceAssetFailureFixture { object : AssetDownloader {
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) {
                sink.writeUtf8("retained partial"); sink.emit()
                downloading.complete(Unit)
                awaitCancellation()
            }
        } }
        var installation: Job? = null
        val owner = this
        f.beforeVoiceAcquire = {
            // TextSpeaker has already refreshed READY and chosen its real
            // VoicevoxSpeechEngine. Install wins before that engine acquires data.
            installation = owner.launch { f.assets.install() }
            withTimeout(2_000) { downloading.await() }
        }
        try {
            withTimeout(2_000) { f.speaker.speak("取り付け中の本文") }
            assertEquals(listOf("取り付け中の本文"), f.systemSpoken.value)
            assertTrue(installation?.isActive == true, "The utterance must finish while the explicit transfer is still blocked")
            assertTrue(f.assets.state.value.busy)
            assertTrue(f.runtime.notice.value.orEmpty().contains("端末の音声"))
            installation?.cancelAndJoin()
            assertEquals(VoiceAssetStage.PAUSED, f.assets.state.value.stage)
            withTimeout(1_000) { f.assets.delete() } // A refused reader must not leak a lease.
        } finally { installation?.cancelAndJoin(); f.close() }
    }

    @Test fun aMutationRefusesNewReadersWithoutDroppingAnotherConsumersLease() = runBlocking<Unit> {
        val access = VoiceAssetAccess()
        val pack = VoicePack("/silent/0.vvm".toPath(), "/silent/dict".toPath())
        val first = access.lease { pack }
        val second = access.lease { pack }
        val entered = CompletableDeferred<Unit>()
        val mutation = launch(start = CoroutineStart.UNDISPATCHED) {
            access.mutate { entered.complete(Unit); awaitCancellation() }
        }
        var attemptedLoad = false
        try {
            first.close(); yield()
            assertFalse(entered.isCompleted, "The second consumer still owns its mapped pack")
            second.close()
            withTimeout(1_000) { entered.await() }
            val refused = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { access.lease { attemptedLoad = true; pack } }
            }
            try {
                assertTrue(refused.isCompleted, "A reader must not silently queue behind a multi-minute installation")
                assertIs<VoicePackUnavailableException>(refused.await().exceptionOrNull())
                assertFalse(attemptedLoad)
            } finally { refused.cancelAndJoin() }
            assertTrue(mutation.isActive, "Refusing/cancelling a reader must not cancel installation")
            mutation.cancelAndJoin()
            withTimeout(1_000) { access.lease { pack }.close() }
            assertEquals("cleanup", withTimeout(1_000) { access.mutate { "cleanup" } })
        } finally { first.close(); second.close(); mutation.cancelAndJoin() }
    }
}
