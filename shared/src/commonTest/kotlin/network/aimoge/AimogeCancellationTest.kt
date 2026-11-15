package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Audit 2026-11-11 M4: a cancel while the body is being read must stay a cancel, not turn into
 * 「応答を読めませんでした」 (a runCatching around bodyAsText swallowed it).
 */
class AimogeCancellationTest {
    @Test
    fun aCancelWhileReadingTheBodyStaysACancel() = runTest {
        val started = CompletableDeferred<Unit>()
        val body = ByteChannel()
        val client = HttpClient(MockEngine { started.complete(Unit); respond(body as ByteReadChannel, HttpStatusCode.OK) })
        var caught: Throwable? = null
        withContext(Dispatchers.Default) {
            val job = launch {
                try { AimogeApi(client).boards() } catch (e: Throwable) { caught = e }
            }
            started.await()
            kotlinx.coroutines.delay(100)
            job.cancel()
            job.join()
        }
        assertTrue(caught is CancellationException, "got $caught")
    }
}
