package com.valoser.futacha.shared.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AppStateHistoryMetricsSupportTest {
    @Test
    fun historyJsonByteSize_countsUtf8Bytes() {
        assertEquals(3L, historyJsonByteSize("abc"))
        assertEquals(6L, historyJsonByteSize("履歴"))
    }

    @Test
    fun shouldLogAppStateHistoryMetrics_usesEntryAndByteThresholds() {
        assertFalse(shouldLogAppStateHistoryMetrics(entryCount = 99, jsonByteSize = 511_999L))
        assertTrue(shouldLogAppStateHistoryMetrics(entryCount = 100, jsonByteSize = 1L))
        assertTrue(shouldLogAppStateHistoryMetrics(entryCount = 1, jsonByteSize = 512_000L))
    }
}
