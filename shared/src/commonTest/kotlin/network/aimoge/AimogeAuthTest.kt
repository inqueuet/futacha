package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemorySecrets : AimogeSecretStorage {
    var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
}

/** A store whose read or write can fail, like a locked Keychain or a broken file. */
private class FlakySecrets : AimogeSecretStorage {
    var value: String? = null
    var readFails = false
    var writeFails = false
    override fun read(): String? { if (readFails) throw IllegalStateException("locked"); return value }
    override fun write(value: String) { if (writeFails) throw IllegalStateException("disk"); this.value = value }
}

class AimogeAuthTest {
    private var now = 1_800_000_000_000L
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var startStatus = HttpStatusCode.Created
    private var errorCode = "RATE"
    private var exchangeStatus = HttpStatusCode.Created
    private var revokeStatus = HttpStatusCode.NoContent
    private var revokeThrows = false
    private val credentialValue = "C".repeat(52)

    private fun client() = HttpClient(MockEngine { request ->
        requests += request
        bodies += ((request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            ?: (request.body as? io.ktor.http.content.TextContent)?.text).orEmpty()
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when (request.url.encodedPath) {
            "/app-authorizations" -> if (startStatus == HttpStatusCode.Created) respond(
                """{"id":"0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f","expiresAt":"2027-01-15T08:10:00Z","authorizationUrl":"https://nijiurachan.net/b/aimg/app-auth?requestId=r1"}""",
                startStatus, json
            ) else respond("""{"error":{"code":"$errorCode","message":"x"}}""", startStatus, json)
            "/app-credentials" -> if (exchangeStatus == HttpStatusCode.Created) respond(
                """{"credential":"$credentialValue","credentialId":"0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e01","expiresAt":"2027-02-05T08:00:00Z"}""",
                exchangeStatus, json
            ) else respond("""{"error":{"code":"AUTHORIZATION_CONSUMED"}}""", exchangeStatus, json)
            "/app-credentials/current" -> if (revokeThrows) throw RuntimeException("socket to api.nijiurachan.net failed") else respond("", revokeStatus)
            else -> respond("", HttpStatusCode.NotFound)
        }
    })

    private fun auth(storage: MemorySecrets? = MemorySecrets(), persist: Boolean = true) =
        AimogeAuth(storage, AimogeAuthApi(client()), persistPending = persist, nowMillis = { now })

    private fun stateOf(url: String) = io.ktor.http.Url(url).parameters["state"]
    private fun returnUrl(state: String, code: String = "A".repeat(43)) = "$AIMOGE_REDIRECT_URI?state=$state&code=$code"

    private suspend fun beginAndState(auth: AimogeAuth, storage: MemorySecrets?): String {
        val open = auth.begin("aimg")
        assertIs<AimogeBeginResult.Open>(open)
        val startBody = bodies.last()
        return Regex("\"state\":\"([^\"]+)\"").find(startBody)!!.groupValues[1]
    }

    @Test fun pkceIsS256AndTheStartBodyHasOnlyTheSpecFields() = runTest {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", aimogeCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val v = newAimogeCodeVerifier()
        assertTrue(Regex("^[A-Za-z0-9_-]{43,128}$").matches(v))
        assertTrue(Regex("^[A-Za-z0-9_-]{32,128}$").matches(newAimogeState()))
        val storage = MemorySecrets()
        val a = auth(storage)
        beginAndState(a, storage)
        val body = bodies.last()
        assertFalse("clientId" in body, "the spec body is additionalProperties:false")
        assertTrue("\"installationId\"" in body)
        assertTrue("\"redirectUri\":\"com.valoser.futacha://auth/callback\"" in body)
        assertTrue("\"codeChallengeMethod\":\"S256\"" in body)
        assertEquals("aimg", requests.last().headers["X-Board-Key"])
        assertNull(requests.last().headers["x-aimg-token"])
        assertNull(requests.last().headers["x-aimg-delete-key"])
        assertTrue(a.hasPersistedPending())
    }

    @Test fun aReturnIsExchangedOnceAndASecondDeliveryIsIgnored() = runTest {
        val storage = MemorySecrets()
        val a = auth(storage)
        val state = beginAndState(a, storage)
        assertEquals(AimogeReturnResult.Approved, a.handleReturn(returnUrl(state)))
        assertEquals(AimogeReturnResult.Ignored, a.handleReturn(returnUrl(state)))
        assertEquals(1, requests.count { it.url.encodedPath == "/app-credentials" })
        assertFalse(a.hasPersistedPending())
        assertTrue(a.status.value.approved)
        assertEquals(credentialValue, a.credentialForWrite())
    }

    @Test fun aMismatchedOrStrangeReturnIsIgnoredSilentlyAndKeepsWaiting() = runTest {
        val storage = MemorySecrets()
        val a = auth(storage)
        beginAndState(a, storage)
        assertEquals(AimogeReturnResult.Ignored, a.handleReturn(returnUrl("Z".repeat(43))))
        assertEquals(AimogeReturnResult.Ignored, a.handleReturn("https://nijiurachan.net/b/aimg/app-auth?requestId=r1"))
        assertEquals(AimogeReturnResult.Ignored, a.handleReturn("com.valoser.futacha://auth/callback?state=short&code=x"))
        assertTrue(a.hasPersistedPending())
        assertEquals(0, requests.count { it.url.encodedPath == "/app-credentials" })
    }

    @Test fun anExpiredWaitIsDroppedWithoutAnExchange() = runTest {
        val storage = MemorySecrets()
        val a = auth(storage)
        val state = beginAndState(a, storage)
        now += AIMOGE_PENDING_LIFETIME_MILLIS + 1
        assertEquals(AimogeReturnResult.Failed(AIMOGE_PENDING_EXPIRED_MESSAGE), a.handleReturn(returnUrl(state)))
        assertEquals(0, requests.count { it.url.encodedPath == "/app-credentials" })
        assertFalse(a.hasPersistedPending())
    }

    @Test fun aRejectedExchangeIsNotResentAndAsksForANewApproval() = runTest {
        for (status in listOf(401, 409, 429, 503)) {
            requests.clear()
            exchangeStatus = HttpStatusCode.fromValue(status)
            val storage = MemorySecrets()
            val a = auth(storage)
            val state = beginAndState(a, storage)
            assertEquals(AimogeReturnResult.Failed(AIMOGE_RETRY_APPROVAL_MESSAGE), a.handleReturn(returnUrl(state)))
            assertEquals(AimogeReturnResult.Ignored, a.handleReturn(returnUrl(state)))
            assertEquals(1, requests.count { it.url.encodedPath == "/app-credentials" }, "status $status")
            assertFalse(a.hasPersistedPending())
            assertFalse(a.status.value.approved)
        }
    }

    @Test fun aRefusedStartOpensNoBrowserAndLeavesNoWait() = runTest {
        startStatus = HttpStatusCode.TooManyRequests
        val storage = MemorySecrets()
        val a = auth(storage)
        assertEquals(AimogeBeginResult.Failed(AIMOGE_START_RATE_LIMITED_MESSAGE), a.begin("aimg"))
        assertFalse(a.hasPersistedPending())
        startStatus = HttpStatusCode.Conflict
        assertEquals(AimogeBeginResult.Failed(AIMOGE_START_REFUSED_MESSAGE), a.begin("aimg"))
        startStatus = HttpStatusCode.Unauthorized
        assertEquals(AimogeBeginResult.Failed(AIMOGE_START_REFUSED_MESSAGE), a.begin("aimg"))
        assertFalse(a.hasPersistedPending())
    }

    @Test fun anApprovalPageOutsideNijiurachanIsNotOpened() = runTest {
        assertTrue(isAimogeApprovalPage("https://nijiurachan.net/b/aimg/app-auth?requestId=r1", "aimg"))
        assertFalse(isAimogeApprovalPage("https://evil.example/b/aimg/app-auth", "aimg"))
        assertFalse(isAimogeApprovalPage("http://nijiurachan.net/b/aimg/app-auth", "aimg"))
        // Opened as returned (guide): only https on nijiurachan.net is checked.
        assertTrue(isAimogeApprovalPage("https://nijiurachan.net/app-auth/r1", "aimg"))
        assertFalse(isAimogeApprovalPage("https://user@nijiurachan.net/b/aimg/app-auth", "aimg"))
        assertFalse(isAimogeApprovalPage("https://nijiurachan.net.evil.example/b/aimg/app-auth", "aimg"))
    }

    @Test fun aDeniedOrCancelledReturnIsToldAndDropsTheWait() = runTest {
        for ((error, message) in listOf("access_denied" to AIMOGE_APPROVAL_DENIED_MESSAGE, "cancelled" to AIMOGE_APPROVAL_CANCELLED_MESSAGE)) {
            val storage = MemorySecrets()
            val a = auth(storage)
            val state = beginAndState(a, storage)
            assertEquals(AimogeReturnResult.Ignored, a.handleReturn("$AIMOGE_REDIRECT_URI?error=$error&state=${"Z".repeat(43)}"))
            assertTrue(a.hasPersistedPending())
            assertEquals(AimogeReturnResult.Failed(message), a.handleReturn("$AIMOGE_REDIRECT_URI?error=$error&state=$state"))
            assertFalse(a.hasPersistedPending())
        }
        assertEquals(0, requests.count { it.url.encodedPath == "/app-credentials" })
    }

    @Test fun aDisabledApprovalIsToldApartAndNotRetried() = runTest {
        startStatus = HttpStatusCode.ServiceUnavailable
        errorCode = "APP_AUTH_DISABLED"
        val a = auth(MemorySecrets())
        assertEquals(AimogeBeginResult.Failed(AIMOGE_AUTH_DISABLED_MESSAGE), a.begin("aimg"))
        assertEquals(1, requests.count { it.url.encodedPath == "/app-authorizations" })
        assertFalse(a.hasPersistedPending())
    }

    @Test fun revokingAlwaysRemovesTheLocalCredential() = runTest {
        for (mode in listOf("ok", "429", "throws")) {
            requests.clear()
            revokeStatus = if (mode == "429") HttpStatusCode.TooManyRequests else HttpStatusCode.NoContent
            revokeThrows = mode == "throws"
            exchangeStatus = HttpStatusCode.Created
            val storage = MemorySecrets()
            val a = auth(storage)
            a.handleReturn(returnUrl(beginAndState(a, storage)))
            val result = a.revoke()
            if (mode == "ok") assertEquals(AimogeRevokeResult.Revoked, result)
            else assertEquals(AimogeRevokeResult.ServerUnconfirmed(AIMOGE_REVOKE_UNCONFIRMED_MESSAGE), result)
            assertNull(a.credentialForWrite())
            assertFalse(a.status.value.approved)
            assertFalse(credentialValue in storage.value.orEmpty())
            val revoke = requests.last { it.url.encodedPath == "/app-credentials/current" }
            assertEquals(credentialValue, revoke.headers["x-aimg-token"])
            assertEquals("aimg", revoke.headers["X-Board-Key"])
        }
    }

    @Test fun iosKeepsTheWaitInMemoryOnly() = runTest {
        val storage = MemorySecrets()
        val a = auth(storage, persist = false)
        val state = beginAndState(a, storage)
        assertFalse(a.hasPersistedPending())
        assertTrue(a.hasMemoryPending())
        assertFalse(state in storage.value.orEmpty())
        a.cancelPending()
        assertEquals(AimogeReturnResult.Ignored, a.handleReturn(returnUrl(state)))
    }

    @Test fun theStatusSaysSoonTheDayBefore() = runTest {
        val storage = MemorySecrets()
        val a = auth(storage)
        a.handleReturn(returnUrl(beginAndState(a, storage)))
        val expires = a.status.value.expiresAtMillis!!
        assertFalse(a.status.value.expiresSoon)
        now = expires - AIMOGE_EXPIRES_SOON_MILLIS + 1
        a.refresh()
        assertTrue(a.status.value.expiresSoon)
        now = expires + 1
        a.refresh()
        assertFalse(a.status.value.approved)
        assertNull(a.credentialForWrite())
    }

    @Test fun aHeldDraftComesBackOnceAndIsDroppedAfterADay() = runTest {
        val storage = MemorySecrets()
        val a = auth(storage)
        val draft = AimogeHeldDraft(boardUrl = "https://nijiurachan.net/b/aimg/", threadId = "t1", body = "本文", email = "sage", attachmentPaths = listOf("/tmp/a.png"))
        a.holdDraft(draft)
        assertNull(a.takeHeldDraft("https://nijiurachan.net/b/aimg/", "t2"))
        assertEquals("本文", a.takeHeldDraft("https://nijiurachan.net/b/aimg/", "t1")?.body)
        assertNull(a.takeHeldDraft("https://nijiurachan.net/b/aimg/", "t1"))
        a.holdDraft(draft)
        now += AIMOGE_HELD_DRAFT_LIFETIME_MILLIS + 1
        a.discardExpired()
        assertNull(a.takeHeldDraft("https://nijiurachan.net/b/aimg/", "t1"))
        assertFalse("本文" in storage.value.orEmpty())
    }

    @Test fun aChangedDeleteKeyNeedsTheConsentAgain() = runTest {
        val a = auth()
        a.setDeleteKey("abcdefgh")
        a.setDeleteKeyConsent(true)
        assertTrue(a.status.value.deleteKeyConsented)
        a.setDeleteKey("abcdefgh")
        assertTrue(a.status.value.deleteKeyConsented)
        a.setDeleteKey("12345678")
        assertFalse(a.status.value.deleteKeyConsented)
    }

    @Test fun noSecretReachesAnExceptionMessageOrToString() = runTest {
        revokeThrows = true
        val e = runCatching { AimogeAuthApi(client()).revoke("aimg", credentialValue) }.exceptionOrNull()
        assertIs<AimogeAuthException>(e)
        assertNull(e.cause)
        val text = e.toString() + e.message
        assertFalse(credentialValue in text)
        assertFalse("nijiurachan" in text)
        exchangeStatus = HttpStatusCode.Conflict
        val x = runCatching { AimogeAuthApi(client()).exchange("aimg", "A".repeat(43), "V".repeat(64)) }.exceptionOrNull()!!
        assertFalse("A".repeat(43) in x.toString() + x.message)
        assertFalse("V".repeat(64) in x.toString() + x.message)
        assertFalse(credentialValue in AimogeCredential(credentialValue, "id", 0).toString())
        assertFalse("requestId" in AimogeBeginResult.Open("https://nijiurachan.net/b/aimg/app-auth?requestId=r1").toString())
        assertFalse("A".repeat(43) in parseAimogeAuthReturn(returnUrl("S".repeat(43)))!!.toString())
    }

    @Test fun withoutASecretStoreNothingStarts() = runTest {
        val a = auth(storage = null)
        assertEquals(AimogeBeginResult.Failed(AIMOGE_UNSUPPORTED_MESSAGE), a.begin("aimg"))
        assertEquals(0, requests.size)
        assertFalse(a.status.value.supported)
    }

    @Test fun anUnreadableStoreIsToldApartAndNeverOverwritten() = runTest {
        val storage = FlakySecrets()
        storage.value = """{"deleteKey":"keep-me","deleteKeyConsentAtMillis":1}"""
        storage.readFails = true
        val a = AimogeAuth(storage, AimogeAuthApi(client()), persistPending = true, nowMillis = { now })
        a.refresh() // the first read runs off the caller's thread; wait for it
        assertTrue(a.status.value.unreadable, "an unreadable store is not 'not approved'")
        assertFalse(a.setDeleteKey("other"), "nothing is written over what could not be read")
        assertIs<AimogeBeginResult.Failed>(a.begin("aimg"))
        assertEquals("""{"deleteKey":"keep-me","deleteKeyConsentAtMillis":1}""", storage.value)
        storage.readFails = false
        a.refresh()
        assertFalse(a.status.value.unreadable)
        assertTrue(a.status.value.hasDeleteKey)
    }

    @Test fun aFailedSaveIsAFailureNotAThrow() = runTest {
        val storage = FlakySecrets()
        val a = AimogeAuth(storage, AimogeAuthApi(client()), persistPending = true, nowMillis = { now })
        val open = a.begin("aimg")
        assertIs<AimogeBeginResult.Open>(open)
        val state = Regex("\"state\":\"([^\"]+)\"").find(bodies.last())!!.groupValues[1]
        storage.writeFails = true
        val result = a.handleReturn(returnUrl(state))
        assertEquals(AimogeReturnResult.Failed(AIMOGE_SAVE_FAILED_MESSAGE), result)
        assertTrue(requests.none { it.url.encodedPath == "/app-credentials" }, "the code is not sent while the waiting state stays")
        assertFalse(a.setDeleteKey("k"))
        assertFalse(a.setDeleteKeyConsent(true))
    }

    @Test fun aValueOverTheKeychainReadLimitIsRefused() = runTest {
        val storage = FlakySecrets()
        val a = AimogeAuth(storage, AimogeAuthApi(client()), persistPending = true, nowMillis = { now })
        assertTrue(a.setDeleteKey("k"))
        assertFalse(a.holdDraft(AimogeHeldDraft(boardUrl = "https://nijiurachan.net/b/ai2/", body = "あ".repeat(30_000))))
        assertTrue(storage.value!!.encodeToByteArray().size <= AIMOGE_SECRETS_MAX_BYTES)
    }
}
