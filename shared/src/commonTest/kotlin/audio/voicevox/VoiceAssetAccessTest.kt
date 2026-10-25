package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import kotlin.test.*

class VoiceAssetAccessTest {
    private val pack = VoicePack("/fixture/0.vvm".toPath(), "/fixture/dict".toPath())

    @Test fun metadataAndAnotherSpeakerCanReadWhileAReaderProtectsThePack() = runBlocking {
        val access = VoiceAssetAccess()
        val first = access.lease { pack }
        val second = withTimeout(1_000) { access.lease { pack } }
        try {
            assertEquals(pack, withTimeout(1_000) { access.inspect { pack } })
            var deleted = false
            val deletion = launch(start = CoroutineStart.UNDISPATCHED) { access.mutate { deleted = true } }
            assertFalse(deletion.isCompleted)
            first.close(); first.close()
            yield()
            assertFalse(deleted, "a second speaker still owns mapped data")
            second.close()
            withTimeout(1_000) { deletion.join() }
            assertTrue(deleted)
        } finally { first.close(); second.close() }
    }

    @Test fun cancellingAWaitingWriterDoesNotBlockFutureReadersOrDeletion() = runBlocking {
        val access = VoiceAssetAccess()
        val reader = access.lease { pack }
        try {
            val writer = launch(start = CoroutineStart.UNDISPATCHED) { access.mutate { fail("must not delete") } }
            writer.cancelAndJoin()
            withTimeout(1_000) { access.lease { pack }.close() }
        } finally { reader.close() }
        assertEquals("deleted", withTimeout(1_000) { access.mutate { "deleted" } })
    }

    @Test fun aStatusCheckSkipsWhileDataChangesButMetadataReadsWaitForIt() = runBlocking {
        val access = VoiceAssetAccess()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val mutation = launch(start = CoroutineStart.UNDISPATCHED) { access.mutate { entered.complete(Unit); release.await() } }
        try {
            withTimeout(1_000) { entered.await() }
            assertNull(withTimeout(1_000) { access.inspectUnlessMutating { "status" } }, "A refresh must not wait minutes")
            val metadata = async(start = CoroutineStart.UNDISPATCHED) { access.inspect { "after" } }
            yield()
            assertFalse(metadata.isCompleted, "Metadata must not be read while files are replaced")
            release.complete(Unit)
            assertEquals("after", withTimeout(1_000) { metadata.await() })
            assertEquals("status", access.inspectUnlessMutating { "status" })
        } finally { release.complete(Unit); mutation.cancelAndJoin() }
    }

    @Test fun mutationsStillRunOneAtATime() = runBlocking {
        val access = VoiceAssetAccess()
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { access.mutate { release.await() } }
        var secondRan = false
        val second = launch(start = CoroutineStart.UNDISPATCHED) { access.mutate { secondRan = true } }
        try {
            yield()
            assertFalse(secondRan, "A deletion must not run inside an installation")
            release.complete(Unit)
            withTimeout(1_000) { second.join() }
            assertTrue(secondRan)
        } finally { release.complete(Unit); first.cancelAndJoin(); second.cancelAndJoin() }
    }

    @Test fun failedVerificationDoesNotLeakAReadLease() = runBlocking {
        val access = VoiceAssetAccess()
        assertFailsWith<IllegalStateException> { access.lease { error("corrupt") } }
        assertEquals("deleted", withTimeout(1_000) { access.mutate { "deleted" } })
    }
}
