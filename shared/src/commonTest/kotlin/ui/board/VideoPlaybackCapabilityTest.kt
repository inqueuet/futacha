package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.media.source.OriginalMediaInfo
import kotlinx.coroutines.*
import kotlin.test.*

class VideoPlaybackCapabilityTest {
    private val webm = "https://may.2chan.net/b/src/clip.WEBM?token=.mp4#fragment"
    private fun oldIos(url: String, info: OriginalMediaInfo?) = iosVideoPlaybackCapabilityError(url, false, info)

    @Test fun explicitWebmIsRejectedBeforeAnyResourceFactoryAcrossTheIosBoundary(): Unit = runBlocking {
        for ((major, minor) in listOf(15L to 1L, 16L to 7L, 17L to 3L, 17L to 4L, 26L to 0L)) {
            var calls = 0
            val allowed = supportsIosWebmPath(major, minor)
            val capability = { url: String, info: OriginalMediaInfo? -> iosVideoPlaybackCapabilityError(url, allowed, info) }
            if (!allowed) {
                val rejected = assertFailsWith<UnsupportedVideoPlayback> {
                    prepareVideoPlaybackResource(webm, capability = capability) { calls++; "prepared" }
                }
                assertEquals("webm_os_version", rejected.error.code)
                assertEquals("WebMの再生にはiOS 17.4以降が必要です", rejected.error.message)
                assertEquals(0, calls)
            } else {
                assertEquals("prepared", prepareVideoPlaybackResource(webm, capability = capability) { calls++; "prepared" })
                assertEquals(1, calls)
            }
        }
    }

    @Test fun urlCaseQueryAndFileSourcesUseThePathExtensionWithoutBlockingOtherNativeUrls() {
        listOf(webm, "file:///saved/clip.WeBm", "//may.2chan.net/b/src/a.webm?name=a.mp4").forEach {
            assertEquals("webm_os_version", oldIos(it, null)?.code)
        }
        listOf("https://may.2chan.net/b/src/a.MP4?next=a.webm", "file:///saved/a.mov", "https://example.test/a.m4v",
            "https://example.test/no-extension?format=webm", "invalid-or-unknown").forEach { assertNull(oldIos(it, null)) }
    }

    @Test fun resolvedMimeOrUrlIsCheckedBeforePublishingAPlayerOrStartingTheNextFactory(): Unit = runBlocking {
        val original = "https://may.2chan.net/b/src/redirect.mp4"
        for (info in listOf(
            OriginalMediaInfo("VIDEO/WEBM; codecs=vp9", 42, resolvedUrl = original),
            OriginalMediaInfo("application/octet-stream", 42, resolvedUrl = "https://cdn.test/clip.WebM?x=1")
        )) {
            var calls = 0
            assertFailsWith<UnsupportedVideoPlayback> {
                prepareVideoPlaybackResource(original, info, ::oldIos) { calls++ }
            }
            assertEquals(0, calls)
        }
        assertNull(oldIos(original, OriginalMediaInfo("video/mp4", 42, resolvedUrl = original)))
    }

    @Test fun supportedFactoriesKeepTheirValueAndCancellationIsNotConvertedIntoAPlaybackError(): Unit = runBlocking {
        val token = Any()
        assertSame(token, prepareVideoPlaybackResource("file:///saved/a.mp4", capability = ::oldIos) { token })
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val task = launch {
            prepareVideoPlaybackResource("file:///saved/a.mp4", capability = ::oldIos) {
                try { started.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) }
            }
        }
        started.await()
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
        assertTrue(stopped.isCompleted)
        assertFailsWith<CancellationException> {
            prepareVideoPlaybackResource("file:///saved/a.mp4", capability = ::oldIos) { throw CancellationException("closed") }
        }
    }

    @Test fun rejectionDoesNotInventAnAlternativeSource() {
        assertEquals(listOf(webm), videoPlaybackSources(webm, emptyList()))
        assertEquals(listOf(webm, "https://cdn.test/explicit.mp4"), videoPlaybackSources(webm,
            listOf("https://cdn.test/explicit.mp4", "https://cdn.test/another.webm")))
    }
}
