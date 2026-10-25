package com.valoser.futacha.shared.audio.voicevox

import kotlin.test.*

class IosVoiceAssetCapacityTest {
    @Test fun importantUsageCapacityIncludesSpaceTheSystemCanReclaim() {
        var fallbackCalls = 0
        assertEquals(900L, iosVoiceAssetAvailableBytes({ 900L }, { fallbackCalls++; 4L }))
        assertEquals(0, fallbackCalls)
    }
    @Test fun anImportantUsageValueOfZeroIsARealFullVolumeRatherThanAnUnknownReading() {
        assertEquals(0L, iosVoiceAssetAvailableBytes({ 0L }, { 999L }))
    }
    @Test fun aMissingOrFailedImportantCapacityQueryUsesTheLegacyReading() {
        assertEquals(123L, iosVoiceAssetAvailableBytes({ null }, { 123L }))
        assertEquals(123L, iosVoiceAssetAvailableBytes({ error("resource value unavailable") }, { 123L }))
    }
    @Test fun negativeReadingsAreUnknownAndDoNotInventOrRejectCapacity() {
        assertEquals(123L, iosVoiceAssetAvailableBytes({ -1L }, { 123L }))
        assertNull(iosVoiceAssetAvailableBytes({ -1L }, { -1L }))
        assertNull(iosVoiceAssetAvailableBytes({ null }, { null }))
    }
}
