package com.valoser.futacha.shared.network

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MainThreadSafeResponseBodyTest {
    private class Body : ResponseBody() {
        val bytes = Buffer().writeUtf8("response")
        var closes = 0
        override fun contentType(): MediaType = "image/png".toMediaType()
        override fun contentLength(): Long = 8
        override fun source(): BufferedSource = bytes
        override fun close() { closes++ }
    }

    @Test fun mainThreadCompletionSchedulesCloseEvenAfterRequestCancellation() {
        val original = Body()
        val scheduled = mutableListOf<() -> Unit>()
        val body = MainThreadSafeResponseBody(original, { true }, { scheduled += it })
        val job = kotlinx.coroutines.Job()
        job.invokeOnCompletion { body.close() }
        job.cancel()
        assertEquals(0, original.closes)
        assertEquals(1, scheduled.size)
        scheduled.single()()
        assertEquals(1, original.closes)
    }

    @Test fun workerCompletionClosesImmediatelyAndOnlyOnce() {
        val original = Body()
        val body = MainThreadSafeResponseBody(original, { false }, { error("Unexpected dispatch") })
        body.close()
        body.close()
        assertEquals(1, original.closes)
    }

    @Test fun wrapperPreservesStreamingSourceAndResponseMetadata() {
        val original = Body()
        val body = MainThreadSafeResponseBody(original, { false }, { error("Unexpected dispatch") })
        assertSame(original.source(), body.source())
        assertEquals(original.contentType(), body.contentType())
        assertEquals(original.contentLength(), body.contentLength())
        assertEquals("response", body.source().readUtf8())
        body.close()
    }
}
