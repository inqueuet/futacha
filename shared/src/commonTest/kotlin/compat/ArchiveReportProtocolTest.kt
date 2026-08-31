package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveReportProtocolTest {
    @Test
    fun staleCutoffSaturatesAtLongMinimum() {
        assertEquals(
            Long.MIN_VALUE,
            archiveReportStaleCutoffEpochMillis(Long.MIN_VALUE + 1L)
        )
    }

    @Test
    fun normalizationAcceptsOnlyProductionThreadTargets() {
        assertEquals(
            NormalizedArchiveThread("may/b/00123", "https://may.2chan.net/b/res/00123.htm"),
            normalizeArchiveReportThreadUrl("http://MAY.2chan.net//b/res/00123.htm?foo=1#9")
        )
        assertNotNull(normalizeArchiveReportThreadUrl("https://img.2chan.net/b/res/123.htm"))
        assertNotNull(normalizeArchiveReportThreadUrl("https://may.2chan.net/layout/res/123.htm"))
        assertNull(normalizeArchiveReportThreadUrl("https://dec.2chan.net/55/res/123.htm"))
        assertNull(normalizeArchiveReportThreadUrl("https://may.2chan.net/b/futaba.php?mode=cat"))
        assertNull(normalizeArchiveReportThreadUrl("https://may.2chan.net:443/b/res/123.htm"))
        assertNull(normalizeArchiveReportThreadUrl("https://user@may.2chan.net/b/res/123.htm"))
        assertNull(normalizeArchiveReportThreadUrl("https://may.2chan.net/%62/res/123.htm"))
    }

    @Test
    fun payloadIsSortedDeterministicAndBounded() {
        val requestId = "12345678-test"
        val rows = (25 downTo 1).map {
            NormalizedArchiveThread("may/b/$it", "https://may.2chan.net/b/res/$it.htm")
        }
        val payload = assertNotNull(buildArchiveReportPayload(requestId, rows))
        assertEquals(20, payload.urls.size)
        assertEquals(payload.threadIds.sorted(), payload.threadIds)
        assertTrue(payload.bytes.size <= ARCHIVE_REPORT_MAX_BODY_BYTES)
        assertEquals(payload.sha256, buildArchiveReportPayload(requestId, rows)?.sha256)
        val text = payload.bytes.decodeToString()
        assertTrue(text.startsWith("{\"protocol_version\":1,\"request_id\":\"12345678-test\",\"threads\":["))
        assertFalse(text.contains(" "))
    }

    @Test
    fun retryScheduleUsesSpecifiedBandsAndClampsJitter() {
        assertEquals(48_000L, archiveReportRetryDelayMillis(1, 0.1))
        assertEquals(72_000L, archiveReportRetryDelayMillis(1, 2.0))
        assertEquals(300_000L, archiveReportRetryDelayMillis(2, 1.0))
        assertEquals(21_600_000L, archiveReportRetryDelayMillis(8, 1.0))
        assertEquals(
            ArchiveReportDisposition.Retry(21_600_000L, "network_failure"),
            archiveReportNetworkFailureDisposition(Int.MAX_VALUE, 1.0)
        )
        assertEquals(
            ArchiveReportDisposition.Retry(60_000L, "network_failure"),
            archiveReportNetworkFailureDisposition(-1, 1.0)
        )
    }

    @Test
    fun responsePolicyCoversSuccessSplitRetryAndConfigurationHolds() {
        assertEquals(
            ArchiveReportDisposition.Accepted,
            classifyArchiveReportResponse(202, ArchiveReportResponse(accepted = true), null, 0)
        )
        assertEquals(
            ArchiveReportDisposition.Split,
            classifyArchiveReportResponse(
                400,
                ArchiveReportResponse(accepted = false, reason = "invalid_thread_url"),
                null,
                0
            )
        )
        assertEquals(
            ArchiveReportDisposition.Split,
            classifyArchiveReportResponse(413, ArchiveReportResponse(reason = "body_too_large"), null, 0)
        )
        assertEquals(
            ArchiveReportDisposition.Retry(600_000L, "http_429:rate_limited"),
            classifyArchiveReportResponse(
                429,
                ArchiveReportResponse(reason = "rate_limited"),
                600_000L,
                0
            )
        )
        assertEquals(
            ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, "http_404:unknown"),
            classifyArchiveReportResponse(404, null, null, 0)
        )
        assertTrue(classifyArchiveReportResponse(202, null, null, 0) is ArchiveReportDisposition.Retry)
        assertEquals(
            ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, "invalid_success_response"),
            classifyArchiveReportResponse(202, null, null, 1)
        )
    }

    @Test
    fun untrustedReasonCannotEnterPersistedErrorCode() {
        assertEquals(
            ArchiveReportDisposition.Hold(ARCHIVE_REPORT_CONFIG_HOLD_MILLIS, "http_400:unknown"),
            classifyArchiveReportResponse(
                400,
                ArchiveReportResponse(reason = "https://secret.example/thread"),
                null,
                0
            )
        )
        assertEquals(
            ArchiveReportDisposition.Retry(60_000L, "unknown"),
            archiveReportNetworkFailureDisposition(0, 1.0, "exception: https://secret.example")
        )
    }
}
