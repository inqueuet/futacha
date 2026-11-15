package com.valoser.futacha.shared.repo.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val BOARD = "https://nijiurachan.net/b/ai2/"
private const val THREAD = "01a1ab6e-57e8-755b-a01c-66792ff13aef"
private val START = Instant.parse("2026-11-11T00:00:00Z").toEpochMilliseconds()

/** The real shape (2026-11-11): an unreadable post is only {"seq":N,"status":"unavailable"}, without an id. */
private fun newPost(seq: Long, body: String, status: String = "public") = if (status == "unavailable") """{"seq":$seq,"status":"unavailable"}""" else """{"id":"01a1ab6e-0000-7000-8000-${seq.toString().padStart(12, '0')}",
 "seq":$seq,"postSequenceNo":${200000000 + seq},"status":"$status","body":${if (status == "unavailable") "null" else "\"$body\""},
 "createdAt":"2026-11-10T10:00:00.000Z","sodaneCount":0,"sage":false}"""

private fun state(after: Long, vararg extra: String, states: String = "[]", archivedAt: String? = null) =
    """{"archivedAt":${archivedAt?.let { "\"$it\"" } ?: "null"},"newPosts":[${extra.joinToString(",")}],"postStates":$states}"""

class AimogeDeltaTest {
    private var now = START
    private val requests = mutableListOf<HttpRequestData>()
    private var stateBody: (Url) -> String = { state(11) }
    private var paused = false

    private fun repo(): AimogeBoardRepository {
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                path.startsWith("/threads/") -> respond(stateBody(request.url), HttpStatusCode.OK)
                path.startsWith("/thread/") -> respond(AimogeFixtures.THREAD, HttpStatusCode.OK)
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
        return AimogeBoardRepository(HttpClient(engine), nowMillis = { now }, pausedNow = { paused })
    }

    private fun fulls() = requests.count { it.url.encodedPath.startsWith("/thread/") }
    private fun deltas() = requests.count { it.url.encodedPath.startsWith("/threads/") }

    @Test
    fun automaticReadsEveryTwelveSecondsSendEveryOtherOne() = runTest {
        val r = repo()
        r.getThread(BOARD, THREAD)
        repeat(10) {
            now += 12_000
            withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        }
        assertEquals(1, fulls())
        assertEquals(5, deltas())
        assertEquals("11", requests.last().url.parameters["after"])
    }

    @Test
    fun openingAlwaysReadsEverything() = runTest {
        val r = repo()
        r.getThread(BOARD, THREAD)
        now += 20_000
        r.getThread(BOARD, THREAD)
        assertEquals(2, fulls())
        assertEquals(0, deltas())
    }

    @Test
    fun newPostsRemovalsAndSoudaneAreApplied() = runTest {
        val r = repo()
        val first = r.getThread(BOARD, THREAD)
        stateBody = {
            state(
                11, newPost(12, "新しいレス"), newPost(13, "", status = "unavailable"),
                states = """[{"seq":1,"status":"unavailable"},{"seq":2,"reactions":{"up":7,"super":0}}]"""
            )
        }
        now += 20_000
        val page = withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        assertEquals(first.posts.size + 1, page.posts.size, "a removed new post has no row")
        assertEquals("新しいレス", page.posts.last().messageHtml)
        assertTrue(page.posts[1].isDeleted)
        assertEquals("そうだねx7", page.posts[2].saidaneLabel)
        assertEquals(12L, page.maxSeq)
    }

    @Test
    fun anUnavailableRowWithoutAnIdMarksAnOldPostAndKeepsTheDeltaGoing() = runTest {
        val r = repo()
        val first = r.getThread(BOARD, THREAD)
        stateBody = { state(11, """{"seq":3,"status":"unavailable"}""", newPost(12, "次"), """{"seq":13,"status":"unavailable"}""") }
        now += 20_000
        val page = withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        assertEquals(1, fulls(), "the delta is read, not thrown away")
        assertEquals(first.posts.size + 1, page.posts.size)
        assertTrue(page.posts.first { it.displayNumber == 3 }.isDeleted)
        assertEquals("ai2", requests.last().headers["X-Board-Key"])
        assertEquals("2026-11-11", requests.last().headers["X-Api-Version"])
    }

    @Test
    fun aGapInTheSeqsReadsEverythingAgain() = runTest {
        val r = repo()
        r.getThread(BOARD, THREAD)
        stateBody = { state(11, newPost(15, "飛んだ")) }
        now += 20_000
        withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        assertEquals(2, fulls())
    }

    @Test
    fun anOldCopyIsReadAgainInFull() = runTest {
        val r = repo()
        r.getThread(BOARD, THREAD)
        now += AimogeBoardRepository.FULL_READ_AGE_MILLIS
        withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        assertEquals(2, fulls())
        assertEquals(0, deltas())
    }

    @Test
    fun aPauseHoldsAutomaticReadsButNotTheUsers() = runTest {
        val r = repo()
        r.getThread(BOARD, THREAD)
        paused = true
        now += 60_000
        withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        assertEquals(1, requests.size)
        r.getThread(BOARD, THREAD)
        assertEquals(2, requests.size)
        paused = false
        now += 60_000
        withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }
        assertEquals(3, requests.size)
    }

    @Test
    fun archivedAtInTheStateMarksTheThreadFallen() = runTest {
        val r = repo()
        r.getThread(BOARD, THREAD)
        stateBody = { state(11, archivedAt = "2026-11-10T23:00:00.000Z") }
        now += 20_000
        assertTrue(withAimogeAutomaticFetch { r.getThread(BOARD, THREAD) }.archived)
    }
}

class AimogeProbeTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(summary: (List<String>) -> String, threadStatus: HttpStatusCode = HttpStatusCode.NotFound): AimogeBoardRepository {
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                path.endsWith("/summary") -> respond(summary(request.url.parameters["ids"].orEmpty().split(",")), HttpStatusCode.OK)
                path.startsWith("/thread/") -> respond("""{"success":false,"message":"Not Found"}""", threadStatus)
                path.contains("snapshot.json") -> respond(AimogeFixtures.SNAPSHOT, HttpStatusCode.OK)
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
        return AimogeBoardRepository(HttpClient(engine), nowMillis = { START })
    }

    private fun url(uuid: String) = "https://nijiurachan.net/b/ai2/thread/$uuid"
    private fun uuid(n: Int) = "01a1ab6e-0000-7000-8000-${n.toString().padStart(12, '0')}"
    private fun summaryOf(ids: List<String>, expiresAt: String) =
        """{"threads":[${ids.joinToString(",") { """{"id":"$it","replyCount":1,"expiresAt":"$expiresAt","isPermanent":false}""" }}]}"""

    @Test
    fun aliveAndFallenThreadsAreTold() = runTest {
        val alive = repo({ summaryOf(it, "2026-11-12T00:00:00.000Z") })
        assertTrue(alive.probeThreadExists(url(THREAD)))
        assertFalse(alive.probeThreadGone(url(THREAD)))
        val fallen = repo({ summaryOf(it, "2026-11-10T00:00:00.000Z") })
        assertTrue(fallen.probeThreadGone(url(THREAD)))
        val missing = repo({ """{"threads":[]}""" })
        assertTrue(missing.probeThreadGone(url(THREAD)), "not in summary and /thread is 404")
        assertTrue(requests.none { it.method.value == "HEAD" })
    }

    @Test
    fun aFailureAnswersNeither() = runTest {
        val broken = repo({ "<html>" })
        assertFalse(broken.probeThreadGone(url(THREAD)))
        assertFalse(broken.probeThreadExists(url(THREAD)))
        val missingButServerFails = repo({ """{"threads":[]}""" }, threadStatus = HttpStatusCode.ServiceUnavailable)
        assertFalse(missingButServerFails.probeThreadGone(url(THREAD)))
        assertFalse(missingButServerFails.probeThreadExists(url(THREAD)))
    }

    @Test
    fun aCancelledLeaderDoesNotLeaveTheOthersWaiting() = runTest {
        val r = repo({ summaryOf(it, "2026-11-12T00:00:00.000Z") })
        val leader = launch { r.probeThreadExists(url(uuid(1))) }
        kotlinx.coroutines.yield()
        val joiner = async { r.probeThreadExists(url(uuid(2))) }
        kotlinx.coroutines.yield()
        leader.cancel()
        val answer = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeoutOrNull(5_000) { joiner.await() }
        }
        assertTrue(answer != null, "the joiner must get an answer after the leader is cancelled")
        // A later check of the same board works again.
        assertTrue(r.probeThreadExists(url(uuid(3))))
    }

    @Test
    fun twentyFiveChecksMakeTwoSummaryCalls() = runTest {
        val r = repo({ summaryOf(it, "2026-11-12T00:00:00.000Z") })
        val answers = coroutineScope { (1..25).map { n -> async { r.probeThreadExists(url(uuid(n))) } }.awaitAll() }
        assertTrue(answers.all { it })
        assertEquals(2, requests.count { it.url.encodedPath.endsWith("/summary") })
    }

    @Test
    fun picturesAndTitlesNeedNoNetwork() = runTest {
        val r = repo({ "{}" })
        assertNull(r.fetchOpImageUrl(BOARD, THREAD))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theArchivedCopyComesFromTheSnapshot() = runTest {
        val r = repo({ "{}" })
        val content = r.getArchivedThreadContent(BOARD, "01a19b5d-aba5-7514-8fdb-dab25b35eb3b")!!
        assertTrue(content.page.archived)
        assertTrue(content.page.posts.isNotEmpty())
        assertEquals("/archive/ai2/threads/01a19b5d-aba5-7514-8fdb-dab25b35eb3b/snapshot.json", requests.single().url.encodedPath)
    }
}
