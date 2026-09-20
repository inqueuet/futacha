package com.valoser.futacha.shared.media.source

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

class OriginalMediaStreamingIntegrationTest {
    @Test fun declaredVideoStreamsThroughSessionAndSettingsCycleWithOneGet(): Unit = runBlocking { verify(true) }
    @Test fun chunkedVideoStreamsBeforeEofAndLatePromptEnableUsesTheSameBytes(): Unit = runBlocking { verify(false) }

    private suspend fun verify(declared: Boolean): Unit = coroutineScope {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("original-http-stream-${Random.nextLong()}")
        val body = ByteChannel(autoFlush = true)
        val calls = MutableStateFlow(0)
        val parses = MutableStateFlow(0)
        val request = OriginalMediaRequest("https://may.2chan.net/b/src/video.mp4?token=a%2Bb")
        val client = HttpClient(MockEngine {
            calls.update { it + 1 }
            assertEquals(request.url, it.url.toString())
            assertNull(it.headers[HttpHeaders.Range])
            respond(body, HttpStatusCode.OK, headers {
                append(HttpHeaders.ContentType, "video/mp4")
                if (declared) append(HttpHeaders.ContentLength, "12")
            })
        })
        val session = createOriginalMediaSession(client)
        session.configure(OriginalMediaCacheConfiguration(directory, 1024 * 1024))
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) }
        val source = PromptMediaSource(session, gate) {
            parses.update { it + 1 }
            assertEquals("prefixsuffix", it.readAt(0, 12).decodeToString())
            GenerationMetadata(listOf(GenerationCandidate("test", "prompt", raw = "prompt", isAi = true)), MetadataCoverage.MP4_METADATA)
        }
        try {
            withTimeout(10_000) {
                source.acquireForPlayback(request).use { player ->
                    assertEquals(if (declared) 12L else -1L, player.info().sizeBytes)
                    body.writeFully("prefix".encodeToByteArray()); body.flush()
                    assertEquals("prefix", player.readAt(0, 100).decodeToString())
                    assertEquals(0, parses.value)
                    val revision = source.changes.value
                    gate.update(MediaFeatureSettings.Disabled)
                    source.changes.first { it > revision }
                    val tail = async(start = CoroutineStart.UNDISPATCHED) { player.readAt(6, 100) }
                    val saving = async(start = CoroutineStart.UNDISPATCHED) { source.acquireForExport(request) }
                    assertFalse(tail.isCompleted); assertFalse(saving.isCompleted)
                    if (declared) gate.update(MediaFeatureSettings(promptDisplayEnabled = true))
                    body.writeFully("suffix".encodeToByteArray()); body.flushAndClose()
                    assertEquals("suffix", tail.await().decodeToString())
                    player.complete().use { original -> saving.await().use { saved ->
                        assertEquals(original.identity, saved.identity)
                        assertEquals(original.file, saved.file)
                    } }
                    if (!declared) {
                        assertEquals(0, parses.value, "Saving and playback must not parse while OFF")
                        gate.update(MediaFeatureSettings(promptDisplayEnabled = true))
                        source.inspectCached(request.url)
                    }
                    source.changes.first { source.metadata(request.url) != null }
                    assertEquals(1, parses.value)
                    assertEquals(1, calls.value)
                    assertEquals(12, player.info(true).sizeBytes)
                    assertTrue(player.readAt(12, 1).isEmpty())
                }
            }
        } finally {
            body.cancel(null); source.close()
            withTimeout(5_000) { session.closeAndAwait() }
            client.close(); FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }
}
