package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Uses the old TextSpeaker/VoiceAssetStore APIs and the real verification/fallback route, silently. */
class VoiceAssetCorruptionReadinessTest {
    @Test fun aRealFailedPackAcquisitionStaysFailedWhenSettingsRefreshesMetadata() = runBlocking<Unit> {
        val f = VoiceAssetFailureFixture()
        try {
            f.assets.refresh()
            assertTrue(f.assets.state.value.ready, "The fixture must reproduce the size-only initial readiness")
            assertEquals(0, f.sourceOpens.value, "Ordinary metadata refresh must not hash 58MB on startup")
            f.speaker.speak("破損時の本文")
            assertEquals(listOf("破損時の本文"), f.systemSpoken.value)
            assertEquals(VoiceAssetStage.FAILED, f.assets.state.value.stage)
            assertTrue(f.assets.state.value.message.orEmpty().contains("ダウンロード"))
            assertNotNull(f.runtime.notice.value)
            val readsAfterFailure = f.sourceOpens.value
            assertTrue(readsAfterFailure > 0, "The actual engine must reach pack verification")
            f.assets.refresh()
            assertFalse(f.assets.state.value.ready, "A known corrupt pack must not become prepared merely because its size matches")
            assertEquals(VoiceAssetStage.FAILED, f.assets.state.value.stage)
            f.speaker.speak("次の本文")
            assertEquals(listOf("破損時の本文", "次の本文"), f.systemSpoken.value)
            assertEquals(readsAfterFailure, f.sourceOpens.value, "Known failure must avoid repeated full verification until explicit repair")
        } finally { f.close() }
    }

    @Test fun recreatingTheSettingsStoreDoesNotHideTheDetectedCorruptionOrRehashAtStartup() = runBlocking<Unit> {
        val f = VoiceAssetFailureFixture()
        try {
            assertFailsWith<VoicePackUnavailableException> { f.assets.acquireVerifiedPack() }
            val readsAfterFailure = f.sourceOpens.value
            val recreated = VoiceAssetStore({ f.root }, { Long.MAX_VALUE }, f.fileSystem, downloader = { error("No repair without user action") })
            recreated.refresh()
            assertEquals(VoiceAssetStage.FAILED, recreated.state.value.stage)
            assertFalse(recreated.state.value.ready)
            assertTrue(recreated.state.value.message.orEmpty().contains("ダウンロード"))
            assertEquals(readsAfterFailure, f.sourceOpens.value)
        } finally { f.close() }
    }
}
