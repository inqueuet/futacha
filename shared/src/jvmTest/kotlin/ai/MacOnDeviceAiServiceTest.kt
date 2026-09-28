package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class MacOnDeviceAiServiceTest {
    private fun post(id: String) = Post(id = id, author = null, subject = null, timestamp = "", messageHtml = "これは投稿の本文です。", imageUrl = null, thumbnailUrl = null)
    private fun response(vararg fields: Pair<String, String>) = buildJsonObject { fields.forEach { (k,v) -> put(k,v) } }

    @Test fun missingMacLibraryLeavesExternalModerationPossible() = runBlocking {
        val local = MacOnDeviceAiService { _, _ -> throw UnsatisfiedLinkError("not installed") }
        assertFalse(local.getAvailability().isAvailable)
        assertTrue(local.summarizeThread(ThreadSummaryInput("t", null, listOf(post("1")))).isFailure)
    }

    @Test fun summaryAndModerationPollParseAndCleanUpEachRequest() = runBlocking {
        val operations = mutableListOf<String>()
        var current = ""
        val service = MacOnDeviceAiService { op, _ ->
            operations += op
            when (op) {
                "summary", "moderation" -> { current = op; response("status" to "pending") }
                "poll" -> response("status" to "done", "text" to if (current == "summary") "話題の見出し\n- 本文の要点" else "2\tHIDE\t明確な攻撃\n999\tHIDE\t存在しない投稿")
                else -> response("status" to "cancelled")
            }
        }
        val posts = listOf(post("1"), post("2"))
        assertEquals("話題の見出し", service.summarizeThread(ThreadSummaryInput("t", null, posts)).getOrThrow().headline)
        assertEquals(listOf("2"), service.classifyPosts(PostModerationInput("t", posts)).getOrThrow().map { it.postId })
        assertEquals(2, operations.count { it == "cancel" })
    }

    @Test fun cancelledInferenceCancelsTheNativeRequestAndCannotReturnAResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val operations = CopyOnWriteArrayList<String>()
        val service = MacOnDeviceAiService { op, _ ->
            operations += op
            if (op == "summary") started.complete(Unit)
            response("status" to if (op == "cancel") "cancelled" else "pending")
        }
        val job = async { service.summarizeThread(ThreadSummaryInput("t", null, listOf(post("1")))) }
        started.await()
        job.cancelAndJoin()
        assertTrue("cancel" in operations)
        assertTrue(job.isCancelled)
    }

    @Test fun blankSummaryIsFailureRatherThanAnExtractiveSuccess() = runBlocking {
        val service = MacOnDeviceAiService { _, _ -> response("status" to "done", "text" to "") }
        assertTrue(service.summarizeThread(ThreadSummaryInput("t", null, listOf(post("1")))).isFailure)
    }
}
