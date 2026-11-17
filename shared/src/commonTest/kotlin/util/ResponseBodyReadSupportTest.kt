package com.valoser.futacha.shared.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ktor 3.6.1 reports a cut body as CancellationException("Receive failed"); the app must see an IOException. */
class ResponseBodyReadSupportTest {
    @Test fun aCancellationWrappingATransportFailureBecomesAnIoException(): Unit = runBlocking {
        val transport = IOException("connection lost")
        val error = assertFailsWith<IOException> {
            readResponseBody<Unit> { throw CancellationException("Receive failed", transport) }
        }
        // The transport exception itself, as Ktor 3.6.0 delivered it: retry checks that
        // look at the type or stop at a CancellationException in the chain keep working.
        assertSame(transport, error)
        assertTrue(error.suppressedExceptions.any { it is CancellationException })
    }

    @Test fun aPureCancellationStillPropagates(): Unit = runBlocking {
        val cancelled = CancellationException("caller cancelled")
        val error = assertFailsWith<CancellationException> { readResponseBody<Unit> { throw cancelled } }
        assertSame(cancelled, error)
    }

    @Test fun cancellingTheReaderIsNotTurnedIntoAFailure(): Unit = runBlocking {
        val reader = async(start = CoroutineStart.UNDISPATCHED) {
            readResponseBody<Unit> {
                try { awaitCancellation() } catch (e: CancellationException) {
                    throw CancellationException("Receive failed", IOException("closed by the cancel"))
                }
            }
        }
        yield()
        reader.cancel()
        assertFailsWith<CancellationException> { reader.await() }
    }

    @Test fun aTimeoutBehindTheCancellationKeepsItsKind(): Unit = runBlocking {
        val timeout = io.ktor.client.network.sockets.SocketTimeoutException("read timed out")
        val error = assertFailsWith<io.ktor.client.network.sockets.SocketTimeoutException> {
            readResponseBody<Unit> { throw CancellationException("Receive failed", timeout) }
        }
        assertSame(timeout, error)
    }

    @Test fun aSelfReferencingOrDeepCancellationChainEnds() {
        val self = object : CancellationException("loop") { override val cause: Throwable get() = this }
        kotlin.test.assertNull(transportCauseOf(self))
        var deep: Throwable = CancellationException("0")
        repeat(40) { deep = CancellationException("x", deep) }
        kotlin.test.assertNull(transportCauseOf(deep))
    }

    @Test fun aCutBodyReadThroughKtorFailsAsAnIoException(): Unit = runBlocking {
        val client = HttpClient(MockEngine {
            respond(ByteChannel().apply { cancel(IOException("response connection lost")) }, HttpStatusCode.OK)
        })
        try {
            assertFailsWith<IOException> {
                client.prepareGet("https://example.com/").execute { response -> readResponseBody { response.bodyAsText() } }
            }
        } finally { client.close() }
    }
}
