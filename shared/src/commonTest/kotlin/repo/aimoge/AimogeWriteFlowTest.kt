package com.valoser.futacha.shared.repo.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.aimoge.AIMOGE_POLL_LINK_PREFIX
import com.valoser.futacha.shared.network.aimoge.AimogeAuth
import com.valoser.futacha.shared.network.aimoge.AimogeAuthApi
import com.valoser.futacha.shared.network.aimoge.AimogeAuthRegistry
import com.valoser.futacha.shared.network.aimoge.AimogeNotices
import com.valoser.futacha.shared.network.aimoge.AimogePollLinks
import com.valoser.futacha.shared.network.aimoge.AimogePromptRequest
import com.valoser.futacha.shared.network.aimoge.AimogePrompts
import com.valoser.futacha.shared.network.aimoge.AimogeReplyRequest
import com.valoser.futacha.shared.network.aimoge.AimogeWriteOutcome
import com.valoser.futacha.shared.network.aimoge.AimogeRequestLedger
import com.valoser.futacha.shared.network.aimoge.AimogeSecretStorage
import com.valoser.futacha.shared.network.aimoge.AimogeWriteApi
import com.valoser.futacha.shared.network.aimoge.AimogeWriteRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WBOARD = "https://nijiurachan.net/b/ai2/"
private const val WTHREAD = "01a1ab6e-57e8-755b-a01c-66792ff13aef"
private const val OP_UUID = "01a1ab6e-57e8-755a-8c24-61da88f0bd4d"
private const val TOKEN = "tok-secret-1"
private const val KEY = "key-secret-1"

private class Memory(var text: String? = null) : AimogeSecretStorage {
    override fun read(): String? = text
    override fun write(value: String) { text = value }
}

/** The repository's writes with MockEngine only: nothing is ever sent to the real site. */
class AimogeWriteFlowTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var beforePost: suspend () -> Unit = {}
    private var postStatus = HttpStatusCode.Created
    private var postBody = """{"threadId":"$WTHREAD","postId":"$OP_UUID","seq":0,"status":"public"}"""
    private var threadJson = AimogeFixtures.THREAD
    private var stateJson = """{"newPosts":[],"postStates":[]}"""
    private var voteStatus = HttpStatusCode.OK
    private var policyJson: String? = null
    private var limitsJson: String? = null
    private var presigns = 0

    private val altcha = run {
        val hex = ("salt7".encodeUtf8().sha256().hex())
        """{"algorithm":"SHA-256","challenge":"$hex","maxnumber":50,"salt":"salt","signature":"sig"}"""
    }

    private fun client() = HttpClient(MockEngine { request ->
        requests += request
        bodies += ((request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()).orEmpty()
        val path = request.url.encodedPath
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            path == "/client-policy" -> policyJson?.let { respond(it, HttpStatusCode.OK, json) } ?: respond("{}", HttpStatusCode.NotFound, json)
            path.startsWith("/thread-limits/") -> limitsJson?.let { respond(it, HttpStatusCode.OK, json) } ?: respond("{}", HttpStatusCode.NotFound, json)
            path == "/attachments/presign" -> respond("""{"attachmentId":"11111111-1111-4111-8111-11111111111${presigns++}","uploadUrl":null}""", HttpStatusCode.OK, json)
            path.endsWith("/close") -> respond("{}", HttpStatusCode.OK, json)
            path == "/threads/ai2" && request.method == HttpMethod.Post -> respond(postBody, postStatus, json)
            path == "/altcha" -> respond(altcha, HttpStatusCode.OK, json)
            path.startsWith("/threads/") && path.endsWith("/state") -> respond(stateJson, HttpStatusCode.OK, json)
            path.startsWith("/thread/") -> respond(threadJson, HttpStatusCode.OK, json)
            path.endsWith("/posts") && request.method == HttpMethod.Post -> {
                beforePost()
                respond(postBody, postStatus, json)
            }
            path.endsWith("/poll/vote") -> respond(
                """{"isClosed":false,"selectedOptionId":"o1","results":{"totalVotes":1,"options":[{"optionId":"o1","voteCount":1}]}}""",
                voteStatus, json
            )
            path.contains("/reactions/") -> respond("{}", HttpStatusCode.OK, json)
            path.endsWith("/report") -> respond("{}", HttpStatusCode.Created, json)
            else -> respond("{}", HttpStatusCode.NotFound, json)
        }
    })

    private fun secrets(consented: Boolean, key: String? = KEY) = buildString {
        append("""{"credential":{"credential":"$TOKEN","credentialId":"c1","expiresAtMillis":${Long.MAX_VALUE},"boardSlug":"ai2"}""")
        if (key != null) append(""","deleteKey":"$key"""")
        if (consented) append(""","deleteKeyConsentAtMillis":1""")
        append("}")
    }

    private var ids = 0
    private fun repo(storage: Memory, ledgerStorage: AimogeSecretStorage = Memory()): AimogeBoardRepository {
        val http = client()
        AimogeAuthRegistry.auth = AimogeAuth(storage, AimogeAuthApi(http), persistPending = true)
        val r = AimogeBoardRepository(http)
        r.writerOverride = AimogeWriteRepository(
            AimogeWriteApi(http), { AimogeAuthRegistry.auth }, AimogeRequestLedger(ledgerStorage),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            newRequestId = { "00000000-0000-4000-8000-00000000000${ids++}" }
        )
        return r
    }

    @BeforeTest fun on() { AimogeAvailability.sitesEnabled = true; AimogeAvailability.unverifiedPostingEnabled = true; AimogeAvailability.unverifiedDelEnabled = true }
    @AfterTest fun off() {
        AimogeAvailability.sitesEnabled = false; AimogeAvailability.unverifiedPostingEnabled = false; AimogeAvailability.unverifiedDelEnabled = false
        AimogeAuthRegistry.auth = null; AimogeNotices.clear(); AimogePollLinks.handler = null
    }

    private fun sent(path: String) = requests.indices.filter { requests[it].url.encodedPath.endsWith(path) }

    @Test
    fun aSuccessfulPostStaysSuccessfulWhenTheDeviceLocksBeforeLedgerCleanup() = runTest {
        var durable: String? = null
        val ledgerStore = object : AimogeSecretStorage {
            override fun read(): String? = durable
            override fun write(value: String) {
                check(sent("/posts").isEmpty()) { "Keychain locked after POST" }
                durable = value
            }
        }
        val r = repo(Memory(secrets(consented = true)), ledgerStore)
        val number = r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true)
        assertEquals("100493594", number)
        assertEquals(1, sent("/posts").size)
        assertTrue(durable!!.contains("requestId"), "the original durable id stays available for idempotent recovery")
    }

    @Test
    fun cancellingTheScreenDoesNotReleaseTheSendLock() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        beforePost = { entered.complete(Unit); finish.await() }
        val writer = repo(Memory(secrets(consented = true))).writerOverride!!
        val request = AimogeReplyRequest("ai2", WTHREAD, "本文")
        val waitingScreen = async { writer.reply(request) }
        try {
            entered.await()
            waitingScreen.cancelAndJoin()
            assertEquals(1, writer.heldKeyLockCount())
            assertTrue(writer.reply(request) is AimogeWriteOutcome.Refused)
            assertEquals(1, sent("/posts").size)
        } finally {
            finish.complete(Unit)
            withContext(Dispatchers.Default) {
                kotlinx.coroutines.withTimeout(5_000) {
                    while (writer.heldKeyLockCount() != 0) kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    @Test
    fun aRecordedReplyCanBeResentWithTheSameId() = runTest {
        val writer = repo(Memory(secrets(consented = true))).writerOverride!!
        postBody = "{}"
        assertTrue(writer.reply(AimogeReplyRequest("ai2", WTHREAD, "本文")) is AimogeWriteOutcome.Unconfirmed)
        postBody = """{"threadId":"$WTHREAD","postId":"$OP_UUID","seq":0}"""
        assertTrue(writer.resendRecorded("ai2", WTHREAD) is AimogeWriteOutcome.Posted)
        val ids = sent("/posts").map { Json.parseToJsonElement(bodies[it]).jsonObject["requestId"] }
        assertEquals(2, ids.size)
        assertEquals(ids.first(), ids.last())
    }

    @Test
    fun anUnreadableLedgerPreventsAllWritesToTheServer() = runTest {
        val r = repo(Memory(secrets(consented = true)), object : AimogeSecretStorage {
            override fun read(): String? = error("Keychain locked")
            override fun write(value: String) = error("must not overwrite")
        })
        assertFailsWith<NetworkException> { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true) }
        assertTrue(sent("/posts").isEmpty())
        assertTrue(sent("/altcha").isEmpty())
    }

    @Test
    fun aReplyCarriesTheTokenTheBoardKeyAndNoNameOrScreenKey() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        val number = withContext(Dispatchers.Default) {
            r.replyToThread(WBOARD, WTHREAD, "名無し", "sage", "題", "  本文  ", "futaba-key", null, null, textOnly = true)
        }
        assertEquals("100493594", number, "the new post's number is read back")
        val i = sent("/posts").single()
        val req = requests[i]
        assertEquals(TOKEN, req.headers["x-aimg-token"])
        assertEquals("ai2", req.headers["X-Board-Key"])
        assertNull(req.headers["x-aimg-delete-key"], "a post sends the key in the body only")
        val body = Json.parseToJsonElement(bodies[i]).jsonObject
        assertEquals("本文", (body["body"] as JsonPrimitive).content)
        assertEquals(KEY, (body["deleteKey"] as JsonPrimitive).content, "the あいもげ key, not the screen's")
        assertEquals("true", body["sage"].toString())
        assertFalse("name" in body || "subject" in body || "email" in body)
        assertTrue(sent("/altcha").single() < i, "a fresh Altcha right before the post")
        assertEquals(0, r.writerOverride!!.heldKeyLockCount(), "a finished write leaves no lock in the table (audit L2)")
    }

    @Test
    fun withoutConsentAndNoHostNothingIsSent() = runTest {
        val r = repo(Memory(secrets(consented = false)))
        val e = assertFailsWith<NetworkException> {
            withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true) }
        }
        assertEquals(AIMOGE_CONSENT_DECLINED_MESSAGE, e.message)
        assertTrue(sent("/posts").isEmpty() && sent("/altcha").isEmpty())
    }

    @Test
    fun consentIsAskedAtTheTapAndAKeyIsMadeWhenNoneIsStored() = runTest {
        val storage = Memory(secrets(consented = false, key = null))
        val r = repo(storage)
        AimogePrompts.attachHost()
        try {
            val answerer = CoroutineScope(Dispatchers.Default).async {
                val (request, answer) = AimogePrompts.current.filterNotNull().first()
                assertEquals(AimogePromptRequest.DeleteKeyConsent, request)
                answer.complete(true)
            }
            withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true) }
            answerer.await()
        } finally {
            AimogePrompts.detachHost()
        }
        val body = Json.parseToJsonElement(bodies[sent("/posts").single()]).jsonObject
        val key = (body["deleteKey"] as JsonPrimitive).content
        assertTrue(key.length in 8..64)
        assertTrue(storage.text!!.contains("deleteKeyConsentAtMillis"))
    }

    @Test
    fun anUnknownResultIsNeverResentAndTheRetrySendsTheSameRequestId() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        postStatus = HttpStatusCode.InternalServerError
        postBody = """{"message":"oops"}"""
        assertFailsWith<AimogeUnconfirmedWriteException> {
            withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true) }
        }
        assertEquals(1, sent("/posts").size, "sent once, never again by itself")
        postStatus = HttpStatusCode.Created
        postBody = """{"threadId":"$WTHREAD","postId":"$OP_UUID","seq":0,"status":"shadowed"}"""
        withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true) }
        val (a, b) = sent("/posts").map { Json.parseToJsonElement(bodies[it]).jsonObject["requestId"].toString() }
        assertEquals(a, b, "the person's retry sends the same requestId")
        assertTrue(AimogeNotices.message.value!!.isNotBlank(), "a held (shadowed) post is told")
    }

    @Test
    fun soudaneAndDelSendTheKeyInItsHeaderOnTheRightPost() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        withContext(Dispatchers.Default) {
            r.voteSaidane(WBOARD, WTHREAD, "100493594")
            r.requestDeletion(WBOARD, WTHREAD, "100493594", "110")
        }
        val reactions = requests.filter { it.url.encodedPath.contains("/reactions/") }
        assertEquals(listOf("/posts/$OP_UUID/reactions/up", "/posts/$OP_UUID/reactions/del"), reactions.map { it.url.encodedPath })
        assertTrue(reactions.all { it.headers["x-aimg-delete-key"] == KEY && it.headers["x-aimg-token"] == TOKEN })
    }

    @Test
    fun withTheSiteOffEveryWriteIsRefusedWithoutARequest() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        AimogeAvailability.sitesEnabled = false
        assertFailsWith<NetworkException> { withContext(Dispatchers.Default) { r.voteSaidane(WBOARD, WTHREAD, "100493594") } }
        assertTrue(requests.none { it.method != HttpMethod.Get })
    }

    @Test
    fun aPollShowsItsVotesAndAnOptionTapAnswersIt() = runTest {
        val root = Json.parseToJsonElement(AimogeFixtures.THREAD).jsonObject
        val posts = (root["posts"] as JsonArray).toMutableList()
        val op = posts[0].jsonObject
        posts[0] = JsonObject(op + ("poll" to Json.parseToJsonElement("""{"options":[{"id":"o1","label":"はい"},{"id":"o2","label":"いいえ"}]}""")))
        threadJson = JsonObject(root + ("posts" to JsonArray(posts))).toString()
        val seq = (op["seq"] as JsonPrimitive).content
        stateJson = """{"newPosts":[],"postStates":[{"seq":$seq,"poll":{"isClosed":false,"selectedOptionId":null,"results":null}}]}"""
        val r = repo(Memory(secrets(consented = true)))
        val page = withContext(Dispatchers.Default) { r.getThread(WBOARD, WTHREAD) }
        val html = page.posts.first().messageHtml
        // Like the Web version: an open poll not answered yet shows no results, only the options to press.
        assertTrue("件 /" !in html && "（選択肢を押すと回答できます）" in html, html)
        val link = "$AIMOGE_POLL_LINK_PREFIX$OP_UUID/o1"
        assertTrue(link in html)
        assertEquals(OP_UUID to "o1", AimogePollLinks.parse(link))
        AimogePrompts.attachHost()
        try {
            for (confirm in listOf(false, true)) {
                val vote = CoroutineScope(Dispatchers.Default).async { AimogePollLinks.handler!!(OP_UUID, "o1") }
                val (prompt, answer) = AimogePrompts.current.filterNotNull().first()
                assertEquals(AimogePromptRequest.Confirm("「はい」に投票しますか？"), prompt)
                assertTrue(requests.none { it.url.encodedPath.endsWith("/poll/vote") })
                answer.complete(confirm)
                val result = vote.await()
                if (confirm) assertEquals(AIMOGE_POLL_VOTED_MESSAGE, result)
            }
        } finally {
            AimogePrompts.detachHost()
        }
        val vote = requests.single { it.url.encodedPath.endsWith("/poll/vote") }
        assertEquals(KEY, vote.headers["x-aimg-delete-key"])
        assertNull(AimogePollLinks.parse("https://nijiurachan.net/b/ai2/"))
        // The public /state still has no answer; the vote's own answer shows the results (thread 01a1cd5d #72).
        val after = withContext(Dispatchers.Default) { r.getThread(WBOARD, WTHREAD) }.posts.first().messageHtml
        assertTrue("現在の結果" in after && "はい（あなたの回答）　1件 / 100%" in after && "合計 1件" in after, after)
        assertTrue("futacha-poll" !in after, after)
    }

    @Test
    fun closingAShortLivedRepositoryKeepsTheAppsPollLink() = runTest {
        val app = repo(Memory(secrets(consented = true)))
        val picker = repo(Memory(secrets(consented = true)))
        picker.close()
        assertTrue(AimogePollLinks.handler != null, "the board picker closing must not take the link from the app")
        app.close()
        assertNull(AimogePollLinks.handler)
    }

    @Test
    fun theDelButtonCanReportThePostWithAReason() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        withContext(Dispatchers.Default) { r.getThread(WBOARD, WTHREAD) }
        AimogePrompts.attachHost()
        try {
            val answerer = CoroutineScope(Dispatchers.Default).async {
                AimogePrompts.delChoice.filterNotNull().first().complete(
                    com.valoser.futacha.shared.network.aimoge.AimogeDelChoice.Report(com.valoser.futacha.shared.network.aimoge.AimogeReportReason.ILLEGAL, " 詳細 ")
                )
            }
            withContext(Dispatchers.Default) { r.requestDeletion(WBOARD, WTHREAD, "100493594", "110") }
            answerer.await()
        } finally {
            AimogePrompts.detachHost()
        }
        assertTrue(requests.none { it.url.encodedPath.contains("/reactions/") }, "a report sends no del")
        val i = sent("/report").single()
        assertEquals("/threads/$WTHREAD/report", requests[i].url.encodedPath)
        assertEquals(KEY, requests[i].headers["x-aimg-delete-key"])
        val body = Json.parseToJsonElement(bodies[i]).jsonObject
        assertEquals("illegal", (body["reason"] as JsonPrimitive).content)
        assertEquals("詳細", (body["detail"] as JsonPrimitive).content)
        assertEquals(body["fromSeq"], body["toSeq"])
    }

    private val policy = """{"boardKey":"ai2","allowedMimeTypes":["image/png","image/jpeg"],"maxFileSizeBytes":100,"maxBodyLength":10,"maxDeleteKeyLength":64,"threadsPerHour":3,"threadCreationCooldownSeconds":60,"replyCreationCooldownSeconds":30,"threadReplyCreationCooldownSeconds":0}"""
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(8)

    @Test
    fun thePolicyRefusesBeforeSendingAndTellsTheLimits() = runTest {
        policyJson = policy
        val r = repo(Memory(secrets(consented = true)))
        suspend fun refused(body: String, file: ByteArray?) = assertFailsWith<NetworkException> {
            withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", body, "", file, file?.let { "a" }, file == null) }
        }.message!!
        assertTrue("10字まで" in refused("12345678901", null))
        val gif = "GIF89a".encodeToByteArray() + ByteArray(8)
        val type = refused("本文", gif)
        assertTrue("JPG・PNG" in type, type)
        assertTrue("100B" in refused("本文", png + ByteArray(200)))
        assertTrue(sent("/posts").isEmpty() && sent("/altcha").isEmpty(), "nothing is sent")
        withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", null, null, true) }
        assertTrue("秒待って" in refused("本文2", null), "the reply cooldown is kept")
        assertEquals(1, sent("/posts").size)
    }

    @Test
    fun twoMorePicturesGoUpInOrderAndThePostNamesAllThree() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(8)
        withContext(Dispatchers.Default + AimogeExtraPictures(listOf(jpg to "b.jpg", png to "c.png"))) {
            r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", png, "a.png", false)
        }
        assertEquals(3, sent("/attachments/presign").size, "each picture is uploaded once")
        val names = sent("/attachments/presign").map { (Json.parseToJsonElement(bodies[it]).jsonObject["fileName"] as JsonPrimitive).content }
        assertEquals(listOf("a.png", "b.jpg", "c.png"), names, "in the order picked")
        val body = Json.parseToJsonElement(bodies[sent("/posts").single()]).jsonObject
        assertFalse("attachmentId" in body)
        assertEquals((0..2).map { "11111111-1111-4111-8111-11111111111$it" }, (body["attachmentIds"] as JsonArray).map { (it as JsonPrimitive).content })
        assertEquals(3, (body["attachmentSources"] as JsonArray).size)
    }

    @Test
    fun picturesHeldByAPostScreenGoWithTheirFirstAndAreForgottenAfterThePost() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        AimogePendingExtras.set(WBOARD, listOf(png.copyOf() + 1 to "b.png"))
        try {
            withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", png, "a.png", false) }
            val body = Json.parseToJsonElement(bodies[sent("/posts").single()]).jsonObject
            assertEquals(2, (body["attachmentIds"] as JsonArray).size)
            assertTrue(AimogePendingExtras.of(WBOARD).isEmpty(), "forgotten once posted")
        } finally {
            AimogePendingExtras.clearAll()
        }
    }

    @Test
    fun onePictureStillSendsTheSingleIdAndAFourthIsRefusedBeforeSending() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        withContext(Dispatchers.Default) { r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文", "", png, "a.png", false) }
        val body = Json.parseToJsonElement(bodies[sent("/posts").single()]).jsonObject
        assertTrue("attachmentId" in body && "attachmentIds" !in body)
        val before = requests.size
        val e = assertFailsWith<NetworkException> {
            withContext(Dispatchers.Default + AimogeExtraPictures(List(3) { png to "x.png" })) {
                r.replyToThread(WBOARD, WTHREAD, "", "", "", "本文2", "", png, "a.png", false)
            }
        }
        assertTrue("3個まで" in e.message.orEmpty(), e.message)
        assertTrue(requests.drop(before).none { it.url.encodedPath == "/attachments/presign" || it.url.encodedPath.endsWith("/posts") })
    }

    @Test
    fun aNewThreadAsksItsLifetimeFromTheThreadLimits() = runTest {
        policyJson = policy
        limitsJson = """{"activeThreads":3,"inputMode":"hours_minutes","durationHours":6,"minimumMinutes":30,"timeZone":"Asia/Tokyo","nextBoundaryAt":null,"allowR18ThreadCreation":false}"""
        postBody = """{"threadId":"$WTHREAD","postId":"$OP_UUID","seq":0,"status":"public"}"""
        val r = repo(Memory(secrets(consented = true)))
        AimogePrompts.attachHost()
        try {
            val answerer = CoroutineScope(Dispatchers.Default).async {
                val (request, answer) = AimogePrompts.choice.filterNotNull().first()
                assertEquals(listOf("いちばん長く（6時間）", "3時間", "1時間", "30分"), request.options)
                assertTrue("PNG" in request.text, "the board's file rules are shown")
                answer.complete(2)
            }
            val id = withContext(Dispatchers.Default) { r.createThread(WBOARD, "", "", "", "本文", "", png, "a.png", false, false) }
            answerer.await()
            assertEquals(WTHREAD, id)
        } finally {
            AimogePrompts.detachHost()
        }
        val body = Json.parseToJsonElement(bodies[requests.indexOfLast { it.url.encodedPath == "/threads/ai2" }]).jsonObject
        assertEquals("1:0", (body["duration"] as JsonPrimitive).content)
        assertEquals("1日2時間", com.valoser.futacha.shared.network.aimoge.aimogeDurationLabel(26 * 60))
        assertEquals("1:2:0", com.valoser.futacha.shared.network.aimoge.aimogeDurationWire(26 * 60, true))
    }

    @Test
    fun r18InTheMailFieldStartsAnR18ThreadOnlyWhereTheBoardTakesOne() = runTest {
        assertTrue(com.valoser.futacha.shared.network.aimoge.aimogeMailRequestsR18("r18"))
        assertTrue(com.valoser.futacha.shared.network.aimoge.aimogeMailRequestsR18("R-18"))
        assertTrue(com.valoser.futacha.shared.network.aimoge.aimogeMailRequestsR18("sage　R18"))
        assertTrue(!com.valoser.futacha.shared.network.aimoge.aimogeMailRequestsR18("r180"))
        assertTrue(!com.valoser.futacha.shared.network.aimoge.aimogeMailRequestsR18(""))
        policyJson = policy
        val limits = """{"activeThreads":3,"inputMode":"hours_minutes","durationHours":6,"minimumMinutes":30,"timeZone":"Asia/Tokyo","nextBoundaryAt":null,"allowR18ThreadCreation":%s}"""
        postBody = """{"threadId":"$WTHREAD","postId":"$OP_UUID","seq":0,"status":"public"}"""
        val r = repo(Memory(secrets(consented = true)))
        // A board that takes no R18 thread: refused before anything is posted.
        limitsJson = limits.replace("%s", "false")
        val refused = assertFailsWith<NetworkException> {
            withContext(Dispatchers.Default) { r.createThread(WBOARD, "", "r18", "", "本文", "", png, "a.png", false, false) }
        }
        assertEquals(com.valoser.futacha.shared.repo.aimoge.AIMOGE_R18_NOT_ALLOWED_MESSAGE, refused.message)
        assertTrue(requests.none { it.url.encodedPath == "/threads/ai2" && it.method == HttpMethod.Post })
        // A board that does: the thread is sent with R-18 pictures allowed.
        limitsJson = limits.replace("%s", "true")
        AimogePrompts.attachHost()
        try {
            val answerer = CoroutineScope(Dispatchers.Default).async {
                val (_, answer) = AimogePrompts.choice.filterNotNull().first()
                answer.complete(0)
            }
            withContext(Dispatchers.Default) { r.createThread(WBOARD, "", "r18", "", "本文", "", png, "a.png", false, false) }
            answerer.await()
        } finally {
            AimogePrompts.detachHost()
        }
        val body = Json.parseToJsonElement(bodies[requests.indexOfLast { it.url.encodedPath == "/threads/ai2" }]).jsonObject
        val moderation = body["moderation"]!!.jsonObject
        assertEquals("off", (moderation["image"]!!.jsonObject["nsfw"] as JsonPrimitive).content)
        assertEquals("off", (moderation["text"]!!.jsonObject["sexual"] as JsonPrimitive).content)
        // Without r18 nothing about moderation is sent (the server's default).
        limitsJson = limits.replace("%s", "true")
        AimogePrompts.attachHost()
        try {
            val answerer = CoroutineScope(Dispatchers.Default).async {
                val (_, answer) = AimogePrompts.choice.filterNotNull().first()
                answer.complete(0)
            }
            withContext(Dispatchers.Default) { repo(Memory(secrets(consented = true))).createThread(WBOARD, "", "", "", "本文2", "", png, "a.png", false, false) }
            answerer.await()
        } finally {
            AimogePrompts.detachHost()
        }
        val plain = Json.parseToJsonElement(bodies[requests.indexOfLast { it.url.encodedPath == "/threads/ai2" }]).jsonObject
        assertTrue("moderation" !in plain)
    }

    @Test
    fun deletingTheFirstPostOffersClosingTheThreadWithAConfirmation() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        withContext(Dispatchers.Default) { r.getThread(WBOARD, WTHREAD) }
        AimogePrompts.attachHost()
        try {
            val answerer = CoroutineScope(Dispatchers.Default).async {
                AimogePrompts.choice.filterNotNull().first().second.complete(0)
                val (request, answer) = AimogePrompts.current.filterNotNull().first()
                assertTrue(request is AimogePromptRequest.Confirm)
                answer.complete(true)
            }
            withContext(Dispatchers.Default) { r.deleteByUser(WBOARD, WTHREAD, "100493594", "", false) }
            answerer.await()
        } finally {
            AimogePrompts.detachHost()
        }
        val close = requests.single { it.url.encodedPath.endsWith("/close") }
        assertEquals("/threads/$WTHREAD/close", close.url.encodedPath)
        assertTrue(requests.none { it.method == HttpMethod.Delete }, "closing deletes nothing")
        assertEquals(AIMOGE_CLOSED_MESSAGE, AimogeNotices.message.value)
    }

    @Test
    fun closingIsNeverSentWithoutTheConfirmation() = runTest {
        val r = repo(Memory(secrets(consented = true)))
        withContext(Dispatchers.Default) { r.getThread(WBOARD, WTHREAD) }
        assertFailsWith<NetworkException> { withContext(Dispatchers.Default) { r.closeOwnThread(WBOARD, WTHREAD) } }
        assertTrue(requests.none { it.url.encodedPath.endsWith("/close") })
    }
}
