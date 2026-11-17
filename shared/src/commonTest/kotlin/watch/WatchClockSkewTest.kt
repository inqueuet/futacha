package com.valoser.futacha.shared.watch

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A watch clock minutes off the phone's must not reject or age out the phone's data for good. */
class WatchClockSkewTest {
    private val phoneNow = 1_800_000_000_000L
    private val tenMinutes = 10 * 60_000L

    @Test
    fun snapshotFromAPhoneTenMinutesAheadIsAcceptedWhenTheWatchClockIsNotCalibrated() {
        val watchNow = phoneNow - tenMinutes
        // Strict mode (a trusted clock) keeps rejecting a snapshot dated far in the future.
        assertFalse(shouldAcceptWatchSnapshot(null, phoneNow, watchNow))
        assertTrue(shouldAcceptWatchSnapshot(null, phoneNow, watchNow, tolerateIncomingClockSkew = true))
        // Ordering still applies against the stored snapshot (here also from the phone's clock).
        assertTrue(shouldAcceptWatchSnapshot(phoneNow, phoneNow + 1_000L, watchNow, tolerateIncomingClockSkew = true))
        assertTrue(shouldAcceptWatchSnapshot(phoneNow, phoneNow, watchNow, tolerateIncomingClockSkew = true))
    }

    @Test
    fun uncalibratedWatchStillRecoversFromAPoisonedStoredSnapshot() {
        val watchNow = phoneNow
        val poisoned = Long.MAX_VALUE
        assertTrue(shouldAcceptWatchSnapshot(poisoned, phoneNow, watchNow, tolerateIncomingClockSkew = true))
        // An older snapshot is refused while the stored one is plausible.
        assertFalse(shouldAcceptWatchSnapshot(phoneNow, phoneNow - 1L, watchNow, tolerateIncomingClockSkew = true))
        assertFalse(shouldAcceptWatchSnapshot(null, 0L, watchNow, tolerateIncomingClockSkew = true))
    }

    @Test
    fun clockOffsetIsEstimatedFromTheRoundTrip() {
        val watchSent = phoneNow - tenMinutes
        // 2 s round trip: the phone read its clock after 1 s.
        val offset = estimateWatchClockOffsetMillis(
            watchSentAtMillis = watchSent,
            watchReceivedAtMillis = watchSent + 2_000L,
            phoneNowMillis = phoneNow
        )
        assertEquals(tenMinutes - 1_000L, offset)

        // Watch ahead of the phone gives a negative offset.
        assertEquals(
            -tenMinutes,
            estimateWatchClockOffsetMillis(
                watchSentAtMillis = phoneNow + tenMinutes,
                watchReceivedAtMillis = phoneNow + tenMinutes,
                phoneNowMillis = phoneNow
            )
        )
    }

    @Test
    fun slowOrUnechoedRoundTripsAreNotTrusted() {
        val watchSent = phoneNow
        // A request that sat in a queue for minutes would teach a wildly wrong offset.
        assertNull(estimateWatchClockOffsetMillis(watchSent, watchSent + WATCH_CLOCK_MAX_ROUND_TRIP_MILLIS + 1L, phoneNow))
        assertNull(estimateWatchClockOffsetMillis(watchSent, watchSent - 1L, phoneNow))
        assertNull(estimateWatchClockOffsetMillis(0L, watchSent, phoneNow))
        assertNull(estimateWatchClockOffsetMillis(watchSent, watchSent, 0L))
        assertEquals(0L, estimateWatchClockOffsetMillis(watchSent, watchSent, phoneNow))
    }

    @Test
    fun learnedOffsetExpiresAndIsIgnoredWhenTheWatchClockMovedBack() {
        val watchNow = 1_700_000_000_000L
        assertEquals(60_000L, resolveWatchClockOffsetMillis(60_000L, watchNow - 1_000L, watchNow))
        assertEquals(
            60_000L,
            resolveWatchClockOffsetMillis(60_000L, watchNow - WATCH_CLOCK_OFFSET_MAX_AGE_MILLIS, watchNow)
        )
        assertNull(resolveWatchClockOffsetMillis(60_000L, watchNow - WATCH_CLOCK_OFFSET_MAX_AGE_MILLIS - 1L, watchNow))
        assertNull(resolveWatchClockOffsetMillis(60_000L, watchNow + 1L, watchNow))
        assertNull(resolveWatchClockOffsetMillis(null, watchNow, watchNow))
        assertNull(resolveWatchClockOffsetMillis(60_000L, 0L, watchNow))
    }

    @Test
    fun snapshotFreshnessToleratesSmallSkewAndNamesALargeOne() {
        val now = phoneNow
        assertEquals(WatchSnapshotFreshness.Fresh, classifyWatchSnapshotFreshness(now - 1_000L, now))
        // The watch clock a few seconds behind: it used to read "同期古い".
        assertEquals(WatchSnapshotFreshness.Fresh, classifyWatchSnapshotFreshness(now + 5_000L, now))
        assertEquals(
            WatchSnapshotFreshness.Fresh,
            classifyWatchSnapshotFreshness(now + WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS, now)
        )
        assertEquals(
            WatchSnapshotFreshness.ClockSkew,
            classifyWatchSnapshotFreshness(now + WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS + 1L, now)
        )
        assertEquals(
            WatchSnapshotFreshness.Fresh,
            classifyWatchSnapshotFreshness(now - WATCH_SNAPSHOT_STALE_AGE_MILLIS, now)
        )
        assertEquals(
            WatchSnapshotFreshness.Stale,
            classifyWatchSnapshotFreshness(now - WATCH_SNAPSHOT_STALE_AGE_MILLIS - 1L, now)
        )
        assertEquals(WatchSnapshotFreshness.Stale, classifyWatchSnapshotFreshness(0L, now))
    }

    @Test
    fun readAloudStatusFreshnessToleratesSmallSkew() {
        val now = phoneNow
        assertTrue(isWatchReadAloudStatusFreshOnWatch(now - 1_000L, now))
        assertTrue(isWatchReadAloudStatusFreshOnWatch(now + 10_000L, now))
        assertFalse(isWatchReadAloudStatusFreshOnWatch(now + WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS + 1L, now))
        assertTrue(isWatchReadAloudStatusFreshOnWatch(now - WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS, now))
        assertFalse(isWatchReadAloudStatusFreshOnWatch(now - WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS - 1L, now))
        assertFalse(isWatchReadAloudStatusFreshOnWatch(0L, now))
    }

    @Test
    fun clockRequestPayloadRoundTripsAndOlderBuildsSendNothing() {
        assertEquals(phoneNow, decodeWatchClockRequestPayload(encodeWatchClockRequestPayload(phoneNow)))
        assertEquals(0L, decodeWatchClockRequestPayload(ByteArray(0)))
        assertEquals(0L, decodeWatchClockRequestPayload("abc".encodeToByteArray()))
        assertEquals(0L, decodeWatchClockRequestPayload("-5".encodeToByteArray()))
        assertEquals(0L, decodeWatchClockRequestPayload(ByteArray(64) { '1'.code.toByte() }))
        assertContentEquals("1800000000000".encodeToByteArray(), encodeWatchClockRequestPayload(phoneNow))
    }

    @Test
    fun phoneStatusDecodesWithoutTheOptionalFields() {
        val json = Json { ignoreUnknownKeys = true }
        val minimal = json.decodeFromString(WatchPhoneStatus.serializer(), """{"phoneNowMillis":5}""")
        assertEquals(5L, minimal.phoneNowMillis)
        assertEquals(0L, minimal.watchSentAtMillis)
        assertTrue(minimal.isSupported)
        val full = json.decodeFromString(
            WatchPhoneStatus.serializer(),
            """{"phoneNowMillis":5,"watchSentAtMillis":4,"isSupported":false,"future":1}"""
        )
        assertFalse(full.isSupported)
        assertEquals(4L, full.watchSentAtMillis)
    }
}
