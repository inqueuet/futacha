package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.aimogeUnverifiedDelEnabled
import com.valoser.futacha.shared.compat.aimogeUnverifiedPostingEnabled
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ---------------------------------------------------------------------------------------------------------------
// Messages. The unconfirmed ones start exactly as Futaba's, so the screens tell them apart from the action messages.
// ---------------------------------------------------------------------------------------------------------------

const val AIMOGE_UNCONFIRMED_POST_LEAD = "送信結果を確認できませんでした。スレッドを確認してから再送してください。"
const val AIMOGE_UNVERIFIED_POSTING_MESSAGE = "あいもげの板では、まだ添付・スレ立てを使えません"
const val AIMOGE_UNVERIFIED_DEL_MESSAGE = "あいもげの板では、まだdelを使えません"
const val AIMOGE_SITES_OFF_MESSAGE = "あいもげの板は今は使えません"
const val AIMOGE_CONSENT_NEEDED_MESSAGE = "あいもげの削除キーの説明に同意すると使えます"
const val AIMOGE_APPROVAL_NEEDED_MESSAGE = "あいもげで書き込むには、アプリの承認が要ります"
const val AIMOGE_EMPTY_BODY_MESSAGE = "本文を入力してください"
const val AIMOGE_THREAD_NEEDS_ATTACHMENT_MESSAGE = "スレ立てには画像か動画が要ります"
const val AIMOGE_BODY_TOO_LONG_MESSAGE = "本文は4000文字以内で入力してください"
const val AIMOGE_DELETE_KEY_LENGTH_MESSAGE = "あいもげの削除キーは1〜64文字で入力してください"
const val AIMOGE_SENDING_MESSAGE = "送信中です。終わるまでお待ちください"
const val AIMOGE_LEDGER_FULL_MESSAGE = "前の書き込みの結果を確かめてください"
const val AIMOGE_CHECK_PREVIOUS_SUFFIX = "前の書き込みがスレに出ていないか確かめてください"
const val AIMOGE_CONFIRM_NEW_POST_MESSAGE = "新しい書き込みとして送ります。よろしいですか"
const val AIMOGE_SAME_AS_BEFORE_MESSAGE = "前の書き込みと同じ内容として扱われました（新しくは書き込まれていません）"
const val AIMOGE_SHADOWED_MESSAGE = "書き込みは受け付けられましたが、表示が保留されています。今はほかの人には見えないことがあります"
const val AIMOGE_ATTACHMENT_FAILED_MESSAGE = "添付を送れませんでした。もう一度お試しください"
const val AIMOGE_ATTACHMENT_TYPE_MESSAGE = "この形式のファイルはあいもげに送れません"
const val AIMOGE_DELETE_KEY_MISMATCH_MESSAGE = "削除キーが違います"
const val AIMOGE_POST_NOT_FOUND_MESSAGE = "レスが見つかりません。スレを読み直してください"
const val AIMOGE_DELETE_KEY_CHANGED_MESSAGE = "削除キーが変わったので、前回の内容では送り直せません。スレを確かめてください"
private const val TIMEOUT_KIND = "（時間切れ）"
private const val NETWORK_KIND = "（通信の失敗）"
private const val UNREADABLE_KIND = "（応答を読めませんでした）"

/**
 * The definite failures of a post (the Web client's 17). The request record is dropped on these, and also on
 * `IDEMPOTENCY_CONFLICT` (plan decision); every other error keeps it, so a retry sends the same requestId.
 */
val AIMOGE_DEFINITE_POST_FAILURES: Set<String> = setOf(
    "ATTACHMENT_INVALID", "ATTACHMENT_NOT_UPLOADED", "MODERATION_UNAVAILABLE", "SPAM_REJECTED", "NSFW_REJECTED",
    "POST_RATE_LIMITED", "RATE_LIMITED", "POLL_CREATION_RATE_LIMITED", "NOT_FOUND", "THREAD_ARCHIVED", "THREAD_CLOSED",
    "IMAGE_REPLIES_DISABLED", "THREAD_FULL", "DUPLICATE_POST", "INVALID_DURATION", "THREAD_NG_REJECTED", "INVALID_REQUEST"
)
private const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
private val TOKEN_ERRORS = setOf("TOKEN_EXPIRED", "TOKEN_NOT_FOUND", "TOKEN_INVALID")

// ---------------------------------------------------------------------------------------------------------------
// Requests and results.
// ---------------------------------------------------------------------------------------------------------------

/** Where an attachment came from (`attachmentSources`). */
enum class AimogeAttachmentSource(val wire: String) { FILE("file"), PASTE("paste"), OEKAKI("oekaki") }

/** One attachment as the post screen holds it. The type is read from the bytes, never taken from the screen. */
class AimogeAttachmentInput(val bytes: ByteArray, val fileName: String, val source: AimogeAttachmentSource) {
    override fun toString(): String = "AimogeAttachmentInput(${bytes.size} bytes, $source)"
}

/** The six types the app sends (J5), told from the first bytes; null for anything else (HEIC, MOV, unknown). */
fun aimogeAttachmentMime(bytes: ByteArray): String? {
    fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
    fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
    return when {
        at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
        ascii(0, "\u0089PNG\r\n\u001A\n") -> "image/png"
        ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
        ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
        at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> "video/webm"
        ascii(4, "ftyp") && !ascii(8, "qt  ") && !ascii(8, "heic") && !ascii(8, "heix") && !ascii(8, "mif1") -> "video/mp4"
        else -> null
    }
}

data class AimogeReplyRequest(
    val boardSlug: String,
    /** The thread's UUID. */
    val threadId: String,
    val body: String,
    val sage: Boolean = false,
    val attachment: AimogeAttachmentInput? = null,
    /** The 2nd and 3rd pictures (`attachmentIds`, up to 3 in all). */
    val extraAttachments: List<AimogeAttachmentInput> = emptyList(),
    /** Post UUIDs already on screen: a 201 naming one of them means the server returned an earlier result. */
    val knownPostIds: Set<String> = emptySet()
)

data class AimogeThreadRequest(
    val boardSlug: String,
    val body: String,
    val attachment: AimogeAttachmentInput?,
    /** The 2nd and 3rd pictures (`attachmentIds`, up to 3 in all). */
    val extraAttachments: List<AimogeAttachmentInput> = emptyList(),
    val showId: Boolean = false,
    /** The `duration` field (`H:M`); null = the board's current longest. */
    val duration: String? = null,
    /** Thread UUIDs in the catalog read before sending. */
    val knownThreadIds: Set<String> = emptySet(),
    /** An R18 thread: R-18 pictures allowed (`moderation.image.nsfw = "off"`), and the server tags it R18. */
    val r18: Boolean = false
)

/**
 * 「メール」欄に r18（R18・R-18、大文字小文字は問わない）と書くと R18 のスレを立てる（ふたちゃスレ 01a1d21b、2026-11-13）。
 * あいもげにはメール欄が無いので、ふたばと同じ画面のまま選べるようにする。
 */
fun aimogeMailRequestsR18(mail: String): Boolean =
    mail.split(Regex("[\\s　]+")).any { it.replace("-", "").equals("r18", ignoreCase = true) }

/**
 * The `moderation` of an R18 thread. Leaving it out keeps the server's default that forbids everything, so only the
 * sexual checks are turned off; the others stay strict.
 */
internal fun aimogeR18Moderation(): JsonObject = buildJsonObject {
    put("text", buildJsonObject { put("sexual", "off"); put("exclusion", "strict"); put("abuse", "strict") })
    put("image", buildJsonObject { put("nsfw", "off") })
}

data class AimogeWriteResult(val threadId: String, val postId: String, val seq: Long, val status: String) {
    /** The post was taken but is not shown to others yet. */
    val shadowed: Boolean get() = status == "shadowed"
}

data class AimogeDeleteResult(val postId: String, val threadDeleted: Boolean)

sealed interface AimogeWriteOutcome {
    /** Sent and taken. [message] is what to show (also for an earlier result or a held post), or null. */
    data class Posted(val result: AimogeWriteResult, val sameAsBefore: Boolean) : AimogeWriteOutcome {
        val message: String? get() = when {
            sameAsBefore -> AIMOGE_SAME_AS_BEFORE_MESSAGE
            result.shadowed -> AIMOGE_SHADOWED_MESSAGE
            else -> null
        }
    }
    /** Nothing was sent. */
    data class Refused(val message: String) : AimogeWriteOutcome
    /** Nothing was sent: ask the delete-key question at this moment, then send again. */
    data object NeedsConsent : AimogeWriteOutcome
    /** Nothing was sent: the app must be approved first (the draft is kept). */
    data object NeedsApproval : AimogeWriteOutcome
    /** Nothing was sent: show [message] and send again with `confirmed = true` only when the person agrees. */
    data class ConfirmFirst(val message: String) : AimogeWriteOutcome
    /** The server refused. [kept]: a retry sends the same requestId. */
    data class Failed(
        val message: String,
        val code: String?,
        val status: Int?,
        val kept: Boolean,
        val needsApproval: Boolean = false,
        val retryAfterSeconds: Long? = null
    ) : AimogeWriteOutcome
    /** It may or may not have been taken. Never resent by itself; the person's retry sends the same requestId. */
    data class Unconfirmed(val message: String) : AimogeWriteOutcome
}

enum class AimogeReaction(val wire: String, val label: String) { SOUDANE("up", "そうだね"), DEL("del", "del") }

sealed interface AimogeActionOutcome {
    data class Done(val threadDeleted: Boolean = false) : AimogeActionOutcome
    data class Refused(val message: String) : AimogeActionOutcome
    data object NeedsConsent : AimogeActionOutcome
    data object NeedsApproval : AimogeActionOutcome
    data class Failed(val message: String, val code: String?, val status: Int?) : AimogeActionOutcome
    /** Contains [com.valoser.futacha.shared.network.HTTP_BOARD_API_ACTION_RESULT_UNCONFIRMED]'s text. */
    data class Unconfirmed(val message: String) : AimogeActionOutcome
}

/** A poll as the viewer sees it (`PollState`). [counts] is empty while the results are hidden. */
data class AimogePollState(
    val isClosed: Boolean,
    val selectedOptionId: String?,
    val totalVotes: Int?,
    val counts: Map<String, Int>
)

fun parseAimogePollState(element: JsonElement?): AimogePollState? {
    val obj = element as? JsonObject ?: return null
    val results = obj["results"] as? JsonObject
    val counts = (results?.get("options") as? JsonArray)?.mapNotNull { o ->
        val item = o as? JsonObject ?: return@mapNotNull null
        val id = (item["optionId"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val n = (item["voteCount"] as? JsonPrimitive)?.longOrNull?.toInt() ?: return@mapNotNull null
        id to n
    }?.toMap().orEmpty()
    return AimogePollState(
        isClosed = (obj["isClosed"] as? JsonPrimitive)?.booleanOrNull == true,
        selectedOptionId = (obj["selectedOptionId"] as? JsonPrimitive)?.contentOrNull,
        totalVotes = (results?.get("totalVotes") as? JsonPrimitive)?.longOrNull?.toInt(),
        counts = counts
    )
}

/** Why a range is reported (`ReportBody.reason`). */
enum class AimogeReportReason(val wire: String, val label: String) {
    SPAM("spam", "スパム・荒らし"), ILLEGAL("illegal", "違法な内容"), NSFW("nsfw_violation", "R18の設定違反"), OTHER("other", "その他")
}

sealed interface AimogePollOutcome {
    data class Voted(val state: AimogePollState?) : AimogePollOutcome
    /** Already answered (another option) or closed: [state] is the current poll when the server sent it. */
    data class Rejected(val message: String, val state: AimogePollState?) : AimogePollOutcome
    data class Other(val outcome: AimogeActionOutcome) : AimogePollOutcome
}

const val AIMOGE_REPORT_DETAIL_MAX = 200
const val AIMOGE_REPORT_DUPLICATE_MESSAGE = "この範囲はもう通報しています"
const val AIMOGE_POLL_CLOSED_MESSAGE = "このアンケートは終わっています"
const val AIMOGE_POLL_ALREADY_MESSAGE = "このアンケートにはもう答えています"

// ---------------------------------------------------------------------------------------------------------------
// The write requests. Every request has X-Api-Version and X-Board-Key; the token goes only where the spec asks for it.
// ---------------------------------------------------------------------------------------------------------------

/** An answer of a write request; [body] is null when it could not be read. */
internal class AimogeWriteAnswer(val status: Int, val body: String?, val retryAfterSeconds: Long?) {
    val json: JsonObject? get() = body?.let { runCatching { writeJson.parseToJsonElement(it).jsonObject }.getOrNull() }
}

/** Thrown when a request got no answer. [timedOut] decides the kind word of the message; the transport text is dropped. */
internal class AimogeNoAnswerException(val timedOut: Boolean) : Exception(if (timedOut) "timeout" else "network")

private val writeJson = Json { ignoreUnknownKeys = true; isLenient = true }

class AimogeWriteApi(private val client: HttpClient) {
    internal suspend fun altcha(boardSlug: String, token: String): AimogeWriteAnswer =
        send { client.get("$AIMOGE_API_BASE/altcha") { common(boardSlug); header("x-aimg-token", token) } }

    internal suspend fun presign(boardSlug: String, token: String, body: JsonObject): AimogeWriteAnswer =
        send { client.post("$AIMOGE_API_BASE/attachments/presign") { common(boardSlug); header("x-aimg-token", token); json(body) } }

    /** The signed address carries its own permission: no token, no board key, no API version. */
    internal suspend fun upload(uploadUrl: String, mime: String, bytes: ByteArray): AimogeWriteAnswer =
        send { client.put(uploadUrl) { header(HttpHeaders.ContentType, mime); setBody(bytes) } }

    internal suspend fun reply(boardSlug: String, threadId: String, token: String, body: JsonObject): AimogeWriteAnswer =
        send {
            client.post("$AIMOGE_API_BASE/threads/${threadId.encodeURLPathPart()}/posts") {
                common(boardSlug); header("x-aimg-token", token); json(body)
            }
        }

    internal suspend fun createThread(boardSlug: String, token: String, body: JsonObject): AimogeWriteAnswer =
        send {
            client.post("$AIMOGE_API_BASE/threads/${boardSlug.encodeURLPathPart()}") {
                common(boardSlug); header("x-aimg-token", token); json(body)
            }
        }

    internal suspend fun react(boardSlug: String, postId: String, type: String, token: String, deleteKey: String): AimogeWriteAnswer =
        send {
            client.put("$AIMOGE_API_BASE/posts/${postId.encodeURLPathPart()}/reactions/${type.encodeURLPathPart()}") {
                common(boardSlug); header("x-aimg-token", token); header("x-aimg-delete-key", aimogeDeleteKeyHeader(deleteKey))
            }
        }

    internal suspend fun deleteOwn(boardSlug: String, postId: String, token: String, deleteKey: String): AimogeWriteAnswer =
        send {
            client.delete("$AIMOGE_API_BASE/posts/${postId.encodeURLPathPart()}") {
                common(boardSlug); header("x-aimg-token", token); json(buildJsonObject { put("deleteKey", deleteKey) })
            }
        }

    internal suspend fun pollVote(boardSlug: String, postId: String, optionId: String, token: String, deleteKey: String): AimogeWriteAnswer =
        send {
            client.put("$AIMOGE_API_BASE/posts/${postId.encodeURLPathPart()}/poll/vote") {
                common(boardSlug); header("x-aimg-token", token); header("x-aimg-delete-key", aimogeDeleteKeyHeader(deleteKey))
                json(buildJsonObject { put("optionId", optionId) })
            }
        }

    internal suspend fun report(boardSlug: String, threadId: String, body: JsonObject, token: String, deleteKey: String): AimogeWriteAnswer =
        send {
            client.post("$AIMOGE_API_BASE/threads/${threadId.encodeURLPathPart()}/report") {
                common(boardSlug); header("x-aimg-token", token); header("x-aimg-delete-key", aimogeDeleteKeyHeader(deleteKey)); json(body)
            }
        }

    internal suspend fun closeThread(boardSlug: String, threadId: String, token: String, deleteKey: String): AimogeWriteAnswer =
        send {
            client.post("$AIMOGE_API_BASE/threads/${threadId.encodeURLPathPart()}/close") {
                common(boardSlug); header("x-aimg-token", token); json(buildJsonObject { put("deleteKey", deleteKey) })
            }
        }

    private fun HttpRequestBuilder.common(boardSlug: String) {
        header("X-Api-Version", AIMOGE_API_VERSION)
        header(HttpHeaders.Accept, "application/json")
        header("X-Board-Key", boardSlug)
    }

    private fun HttpRequestBuilder.json(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

    private suspend fun send(request: suspend () -> HttpResponse): AimogeWriteAnswer {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ktor's text holds the address (a thread UUID or a signed upload address): only the kind is kept.
            throw AimogeNoAnswerException(timedOut = isTimeout(e))
        }
        val text = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return AimogeWriteAnswer(response.status.value, text, parseRetryAfterSeconds(response.headers[HttpHeaders.RetryAfter]))
    }
}

private fun isTimeout(e: Throwable): Boolean {
    var t: Throwable? = e
    repeat(4) {
        val name = t?.let { it::class.simpleName }.orEmpty()
        if (name.contains("Timeout", ignoreCase = true)) return true
        t = t?.cause ?: return false
    }
    return false
}

/** `x-aimg-delete-key` is UTF-8 percent-encoded (research ch.21). */
fun aimogeDeleteKeyHeader(deleteKey: String): String = deleteKey.encodeURLParameter()

// ---------------------------------------------------------------------------------------------------------------
// The request record (J2): kept in the device's secret store, never in compat.* settings or backups.
// ---------------------------------------------------------------------------------------------------------------

@Serializable
internal data class AimogeLedgerEntry(
    /** HMAC-SHA256 (device secret) of every sent field, so the record never shows the body or the delete key. */
    val key: String,
    val requestId: String,
    /** `reply:<thread uuid>` or `thread:<slug>`. */
    val target: String,
    val savedAtMillis: Long,
    val attachmentId: String? = null,
    /** The ids of the 2nd and 3rd pictures, in order, as each finished uploading. */
    val extraAttachmentIds: List<String> = emptyList(),
    val credentialId: String? = null,
    /** A post went out and no answer came back. */
    val unconfirmed: Boolean = false,
    /** What was sent (without the delete key, the requestId and the Altcha), for a resend after the app was closed. */
    val sent: String? = null
)

@Serializable
internal data class AimogeLedgerState(
    val secret: String? = null,
    val entries: List<AimogeLedgerEntry> = emptyList(),
    /** Targets whose next send must be confirmed first (an earlier post may have been taken). */
    val confirmTargets: List<String> = emptyList()
)

private val ledgerJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/**
 * The requestId record. 200 entries and 24 hours. A known-result entry is dropped first; an unconfirmed one is only
 * dropped by age. One mutex for every read-change-write.
 */
class AimogeRequestLedger(
    private val storage: AimogeSecretStorage?,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) {
    private val mutex = AimogeStoreLock()
    /** Used when the device has no secret store (Desktop, tests): in memory for the process only. */
    private var memory: AimogeLedgerState? = null

    private fun load(): AimogeLedgerState {
        val store = storage ?: return memory ?: AimogeLedgerState()
        val text = runCatching { store.read() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return AimogeLedgerState()
        return runCatching { ledgerJson.decodeFromString(AimogeLedgerState.serializer(), text) }.getOrDefault(AimogeLedgerState())
    }

    private fun save(state: AimogeLedgerState) {
        val store = storage ?: run { memory = state; return }
        store.write(ledgerJson.encodeToString(AimogeLedgerState.serializer(), state))
    }

    private fun pruned(state: AimogeLedgerState): AimogeLedgerState {
        val now = nowMillis()
        return state.copy(entries = state.entries.filter { now - it.savedAtMillis in 0..AIMOGE_LEDGER_LIFETIME_MILLIS })
    }

    @OptIn(ExperimentalUuidApi::class)
    internal suspend fun keyOf(canonical: String): String = mutex.withLock {
        var state = load()
        val secret = state.secret ?: run {
            val fresh = (Uuid.random().toByteArray() + Uuid.random().toByteArray()).toByteString().base64()
            state = state.copy(secret = fresh)
            save(state)
            fresh
        }
        val secretBytes = okio.ByteString.Companion.run { secret.decodeBase64() } ?: secret.encodeUtf8()
        canonical.encodeUtf8().hmacSha256(secretBytes).hex()
    }

    internal suspend fun find(key: String): AimogeLedgerEntry? = mutex.withLock { pruned(load()).entries.firstOrNull { it.key == key } }

    internal suspend fun unconfirmedFor(target: String): AimogeLedgerEntry? = mutex.withLock {
        pruned(load()).entries.lastOrNull { it.target == target && it.unconfirmed }
    }

    /** Puts [entry] (replacing the same key). False when 200 unconfirmed entries leave no room. */
    internal suspend fun put(entry: AimogeLedgerEntry): Boolean = mutex.withLock {
        val state = pruned(load())
        val others = state.entries.filter { it.key != entry.key }
        var kept = others
        while (kept.size >= AIMOGE_LEDGER_MAX_ENTRIES) {
            val oldestKnown = kept.filter { !it.unconfirmed }.minByOrNull { it.savedAtMillis } ?: return@withLock false
            kept = kept - oldestKnown
        }
        save(state.copy(entries = kept + entry))
        true
    }

    internal suspend fun remove(key: String) = mutex.withLock {
        val state = pruned(load())
        save(state.copy(entries = state.entries.filter { it.key != key }))
    }

    internal suspend fun needsConfirm(target: String): Boolean = mutex.withLock { target in load().confirmTargets }

    internal suspend fun setConfirm(target: String, on: Boolean) = mutex.withLock {
        val state = load()
        val next = if (on) (state.confirmTargets - target + target).takeLast(50) else state.confirmTargets - target
        if (next != state.confirmTargets) save(state.copy(confirmTargets = next))
    }

    internal suspend fun size(): Int = mutex.withLock { pruned(load()).entries.size }
}

const val AIMOGE_LEDGER_MAX_ENTRIES = 200
const val AIMOGE_LEDGER_LIFETIME_MILLIS = 24 * 60 * 60 * 1000L

/** The host sets the store of the request record at launch (Android `aimoge-requests.enc`, iOS its own Keychain service). */
object AimogeWriteRegistry {
    var ledgerStorage: AimogeSecretStorage? = null
    /** The app-lifetime scope writes run on, so closing a screen never stops a send half way. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var shared: AimogeRequestLedger? = null
    private var sharedStorage: AimogeSecretStorage? = null

    /** One record for the whole app (two screens must find the same requestId). */
    fun ledger(): AimogeRequestLedger {
        val storage = ledgerStorage
        shared?.takeIf { sharedStorage === storage }?.let { return it }
        return AimogeRequestLedger(storage).also { shared = it; sharedStorage = storage }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The write repository.
// ---------------------------------------------------------------------------------------------------------------

/**
 * Every あいもげ write goes through here. Nothing is ever sent again by itself: a retry by the person sends the same
 * requestId. Attachments and new threads (and del) are refused without a request unless the debug-only values allow
 * them (J3). Writes run on [appScope] to the end, even when the screen goes away.
 */
class AimogeWriteRepository(
    private val api: AimogeWriteApi,
    private val auth: () -> AimogeAuth?,
    private val ledger: AimogeRequestLedger,
    private val appScope: CoroutineScope = AimogeWriteRegistry.appScope,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val newRequestId: () -> String = ::randomRequestId
) {
    private val keyLocks = mutableMapOf<String, Mutex>()
    private val keyLocksGuard = Mutex()

    suspend fun reply(request: AimogeReplyRequest, confirmed: Boolean = false): AimogeWriteOutcome =
        write(
            boardSlug = request.boardSlug,
            target = "reply:${request.threadId.lowercase()}",
            body = request.body,
            attachment = request.attachment,
            extras = request.extraAttachments,
            unverified = request.attachment != null,
            confirmed = confirmed,
            extraFields = { if (request.sage) put("sage", true) },
            known = request.knownPostIds,
            knownIsThread = false,
            send = { token, json -> api.reply(request.boardSlug, request.threadId, token, json) }
        )

    suspend fun createThread(request: AimogeThreadRequest, confirmed: Boolean = false): AimogeWriteOutcome {
        if (request.attachment == null && aimogeUnverifiedPostingEnabled()) return AimogeWriteOutcome.Refused(AIMOGE_THREAD_NEEDS_ATTACHMENT_MESSAGE)
        return write(
            boardSlug = request.boardSlug,
            target = "thread:${request.boardSlug.lowercase()}",
            body = request.body,
            attachment = request.attachment,
            extras = request.extraAttachments,
            unverified = true,
            confirmed = confirmed,
            extraFields = {
                // chessEnabled is never sent as true (plan ch.5).
                put("chessEnabled", false)
                if (request.showId) put("showId", true)
                request.duration?.let { put("duration", it) }
                if (request.r18) put("moderation", aimogeR18Moderation())
            },
            known = request.knownThreadIds,
            knownIsThread = true,
            send = { token, json -> api.createThread(request.boardSlug, token, json) }
        )
    }

    /** Whether [threadId] (or, with null, a new thread on [boardSlug]) has a post whose result is not known. */
    suspend fun hasUnconfirmed(boardSlug: String, threadId: String?): Boolean =
        ledger.unconfirmedFor(targetOf(boardSlug, threadId)) != null

    /**
     * Sends the recorded post again with its own requestId and attachment id (after the app was closed). The delete key
     * is read again and must still give the recorded key; nothing is sent when it changed.
     */
    suspend fun resendRecorded(boardSlug: String, threadId: String?, confirmed: Boolean = false): AimogeWriteOutcome {
        gate(unverified = threadId == null)?.let { return it }
        val target = targetOf(boardSlug, threadId)
        val entry = ledger.unconfirmedFor(target) ?: return AimogeWriteOutcome.Refused(AIMOGE_LEDGER_FULL_MESSAGE)
        val sent = entry.sent?.let { runCatching { writeJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: return AimogeWriteOutcome.Refused(AIMOGE_DELETE_KEY_CHANGED_MESSAGE)
        val authNow = auth() ?: return AimogeWriteOutcome.NeedsApproval
        val deleteKey = authNow.consentedDeleteKey() ?: return AimogeWriteOutcome.NeedsConsent
        val fields = JsonObject(sent + ("deleteKey" to JsonPrimitive(deleteKey)))
        val attachmentMeta = sent["_attachment"]
        val bare = JsonObject(fields - "_attachment" - EXTRA_ATTACHMENTS_FIELD)
        val canonical = canonicalOf(bare, attachmentMeta) + extrasCanonical(sent[EXTRA_ATTACHMENTS_FIELD] as? JsonArray)
        if (ledger.keyOf(canonical) != entry.key) return AimogeWriteOutcome.Refused(AIMOGE_DELETE_KEY_CHANGED_MESSAGE)
        val identity = authNow.writeIdentity() ?: return AimogeWriteOutcome.NeedsApproval
        if (entry.credentialId != null && entry.credentialId != identity.credentialId && !confirmed) {
            return AimogeWriteOutcome.ConfirmFirst(AIMOGE_UNCONFIRMED_POST_LEAD + AIMOGE_CONFIRM_NEW_POST_MESSAGE)
        }
        return locked(entry.key) {
            appScope.async {
                post(entry, identity, bare, boardSlug, emptySet(), threadId == null, recordSent = fields) { token, json ->
                    if (threadId == null) api.createThread(boardSlug, token, json) else api.reply(boardSlug, threadId, token, json)
                }
            }.await()
        }
    }

    /** そうだね or del on the post with UUID [postId]. del is refused without a request unless the debug-only value allows it. */
    suspend fun react(boardSlug: String, postId: String, reaction: AimogeReaction): AimogeActionOutcome {
        if (!AimogeAvailability.sitesEnabled) return AimogeActionOutcome.Refused(AIMOGE_SITES_OFF_MESSAGE)
        if (reaction == AimogeReaction.DEL && !aimogeUnverifiedDelEnabled()) return AimogeActionOutcome.Refused(AIMOGE_UNVERIFIED_DEL_MESSAGE)
        val authNow = auth() ?: return AimogeActionOutcome.NeedsApproval
        val deleteKey = authNow.consentedDeleteKey() ?: return AimogeActionOutcome.NeedsConsent
        val identity = authNow.writeIdentity() ?: return AimogeActionOutcome.NeedsApproval
        return appScope.async {
            val answer = try {
                api.react(boardSlug, postId, reaction.wire, identity.credential, deleteKey)
            } catch (e: AimogeNoAnswerException) {
                return@async AimogeActionOutcome.Unconfirmed(actionUnconfirmed(reaction.label, e.timedOut))
            }
            when {
                answer.status in 200..299 -> AimogeActionOutcome.Done()
                else -> actionFailure(answer)
            }
        }.await()
    }

    /** Deletes the person's own post with [deleteKey] (or, with null, the stored あいもげ key). No consent is needed. */
    suspend fun deleteOwn(boardSlug: String, postId: String, deleteKey: String? = null): AimogeActionOutcome {
        if (!AimogeAvailability.sitesEnabled) return AimogeActionOutcome.Refused(AIMOGE_SITES_OFF_MESSAGE)
        val authNow = auth() ?: return AimogeActionOutcome.NeedsApproval
        val key = deleteKey?.takeIf { it.isNotEmpty() } ?: authNow.deleteKey() ?: return AimogeActionOutcome.Refused(AIMOGE_DELETE_KEY_LENGTH_MESSAGE)
        if (key.length !in 1..64) return AimogeActionOutcome.Refused(AIMOGE_DELETE_KEY_LENGTH_MESSAGE)
        val identity = authNow.writeIdentity() ?: return AimogeActionOutcome.NeedsApproval
        return appScope.async {
            val answer = try {
                api.deleteOwn(boardSlug, postId, identity.credential, key)
            } catch (e: AimogeNoAnswerException) {
                return@async AimogeActionOutcome.Unconfirmed(actionUnconfirmed("削除", e.timedOut))
            }
            when {
                answer.status in 200..299 -> AimogeActionOutcome.Done(
                    threadDeleted = (answer.json?.get("threadDeleted") as? JsonPrimitive)?.booleanOrNull == true
                )
                answer.status == 403 -> AimogeActionOutcome.Failed(AIMOGE_DELETE_KEY_MISMATCH_MESSAGE, parseAimogeErrorBody(answer.body).code, 403)
                else -> actionFailure(answer)
            }
        }.await()
    }

    /** Answers the poll on [postId]. The delete key is required by the spec, so consent is asked at the tap. */
    suspend fun votePoll(boardSlug: String, postId: String, optionId: String): AimogePollOutcome {
        val pre = when (val checked = preAction()) {
            null -> return AimogePollOutcome.Other(AimogeActionOutcome.NeedsApproval)
            is PreAction.Refused -> return AimogePollOutcome.Other(checked.outcome)
            is PreAction.Ready -> checked
        }
        return appScope.async {
            val answer = try {
                api.pollVote(boardSlug, postId, optionId, pre.identity.credential, pre.deleteKey)
            } catch (e: AimogeNoAnswerException) {
                return@async AimogePollOutcome.Other(AimogeActionOutcome.Unconfirmed(actionUnconfirmed("アンケートの回答", e.timedOut)))
            }
            val json = answer.json
            when {
                answer.status in 200..299 -> AimogePollOutcome.Voted(parseAimogePollState(json))
                answer.status == 409 -> {
                    val state = parseAimogePollState(json?.get("poll"))
                    AimogePollOutcome.Rejected(if (state?.isClosed == true) AIMOGE_POLL_CLOSED_MESSAGE else AIMOGE_POLL_ALREADY_MESSAGE, state)
                }
                else -> AimogePollOutcome.Other(actionFailure(answer))
            }
        }.await()
    }

    /** Reports the posts [fromSeq]..[toSeq] of [threadId]. A duplicate (409) is told, never sent again. */
    suspend fun report(
        boardSlug: String, threadId: String, fromSeq: Long, toSeq: Long, reason: AimogeReportReason, detail: String = ""
    ): AimogeActionOutcome {
        if (fromSeq < 0 || toSeq < fromSeq) return AimogeActionOutcome.Refused(AIMOGE_POST_NOT_FOUND_MESSAGE)
        val pre = when (val checked = preAction()) {
            null -> return AimogeActionOutcome.NeedsApproval
            is PreAction.Refused -> return checked.outcome
            is PreAction.Ready -> checked
        }
        val body = buildJsonObject {
            put("fromSeq", fromSeq); put("toSeq", toSeq); put("reason", reason.wire)
            detail.trim().take(AIMOGE_REPORT_DETAIL_MAX).takeIf { it.isNotEmpty() }?.let { put("detail", it) }
        }
        return appScope.async {
            val answer = try {
                api.report(boardSlug, threadId, body, pre.identity.credential, pre.deleteKey)
            } catch (e: AimogeNoAnswerException) {
                return@async AimogeActionOutcome.Unconfirmed(actionUnconfirmed("通報", e.timedOut))
            }
            when {
                answer.status in 200..299 -> AimogeActionOutcome.Done()
                answer.status == 409 -> AimogeActionOutcome.Failed(AIMOGE_REPORT_DUPLICATE_MESSAGE, parseAimogeErrorBody(answer.body).code, 409)
                else -> actionFailure(answer)
            }
        }.await()
    }

    /** Closes the person's own thread with the stored あいもげ key (or [deleteKey]). */
    suspend fun closeThread(boardSlug: String, threadId: String, deleteKey: String? = null): AimogeActionOutcome {
        if (!AimogeAvailability.sitesEnabled) return AimogeActionOutcome.Refused(AIMOGE_SITES_OFF_MESSAGE)
        val authNow = auth() ?: return AimogeActionOutcome.NeedsApproval
        val key = deleteKey?.takeIf { it.isNotEmpty() } ?: authNow.deleteKey() ?: return AimogeActionOutcome.Refused(AIMOGE_DELETE_KEY_LENGTH_MESSAGE)
        val identity = authNow.writeIdentity() ?: return AimogeActionOutcome.NeedsApproval
        return appScope.async {
            val answer = try {
                api.closeThread(boardSlug, threadId, identity.credential, key)
            } catch (e: AimogeNoAnswerException) {
                return@async AimogeActionOutcome.Unconfirmed(actionUnconfirmed("スレを閉じる操作", e.timedOut))
            }
            when {
                answer.status in 200..299 -> AimogeActionOutcome.Done()
                answer.status == 403 -> AimogeActionOutcome.Failed(AIMOGE_DELETE_KEY_MISMATCH_MESSAGE, parseAimogeErrorBody(answer.body).code, 403)
                else -> actionFailure(answer)
            }
        }.await()
    }

    /** Ready carries what a write needs, so no caller has to assert it is there (audit 2026-11-12 L4). */
    private sealed interface PreAction {
        class Ready(val identity: AimogeWriteIdentity, val deleteKey: String) : PreAction
        class Refused(val outcome: AimogeActionOutcome) : PreAction
    }

    private suspend fun preAction(): PreAction? {
        if (!AimogeAvailability.sitesEnabled) return PreAction.Refused(AimogeActionOutcome.Refused(AIMOGE_SITES_OFF_MESSAGE))
        val authNow = auth() ?: return null
        val key = authNow.consentedDeleteKey() ?: return PreAction.Refused(AimogeActionOutcome.NeedsConsent)
        val identity = authNow.writeIdentity() ?: return null
        return PreAction.Ready(identity, key)
    }

    // ---- the post itself ----

    private fun gate(unverified: Boolean): AimogeWriteOutcome? = when {
        !AimogeAvailability.sitesEnabled -> AimogeWriteOutcome.Refused(AIMOGE_SITES_OFF_MESSAGE)
        unverified && !aimogeUnverifiedPostingEnabled() -> AimogeWriteOutcome.Refused(AIMOGE_UNVERIFIED_POSTING_MESSAGE)
        else -> null
    }

    private suspend fun write(
        boardSlug: String,
        target: String,
        body: String,
        attachment: AimogeAttachmentInput?,
        extras: List<AimogeAttachmentInput>,
        unverified: Boolean,
        confirmed: Boolean,
        extraFields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
        known: Set<String>,
        knownIsThread: Boolean,
        send: suspend (String, JsonObject) -> AimogeWriteAnswer
    ): AimogeWriteOutcome {
        gate(unverified)?.let { return it }
        val trimmed = body.trim()
        if (trimmed.isEmpty() && attachment == null) return AimogeWriteOutcome.Refused(AIMOGE_EMPTY_BODY_MESSAGE)
        if (trimmed.length > 4000) return AimogeWriteOutcome.Refused(AIMOGE_BODY_TOO_LONG_MESSAGE)
        // The picture and its type travel together, so the upload never asserts the type is there (audit 2026-11-12 L4).
        val typed = attachment?.let { it to (aimogeAttachmentMime(it.bytes) ?: return AimogeWriteOutcome.Refused(AIMOGE_ATTACHMENT_TYPE_MESSAGE)) }
        if (extras.isNotEmpty() && attachment == null) return AimogeWriteOutcome.Refused(AIMOGE_ATTACHMENT_TYPE_MESSAGE)
        if (1 + extras.size > AIMOGE_MAX_ATTACHMENTS) return AimogeWriteOutcome.Refused(AIMOGE_TOO_MANY_ATTACHMENTS_MESSAGE)
        val typedExtras = extras.map { it to (aimogeAttachmentMime(it.bytes) ?: return AimogeWriteOutcome.Refused(AIMOGE_ATTACHMENT_TYPE_MESSAGE)) }
        val authNow = auth() ?: return AimogeWriteOutcome.NeedsApproval
        val deleteKey = authNow.consentedDeleteKey() ?: return AimogeWriteOutcome.NeedsConsent
        if (deleteKey.length !in 1..64) return AimogeWriteOutcome.Refused(AIMOGE_DELETE_KEY_LENGTH_MESSAGE)
        val identity = authNow.writeIdentity() ?: return AimogeWriteOutcome.NeedsApproval

        val fields = buildJsonObject {
            put("body", trimmed)
            put("deleteKey", deleteKey)
            extraFields()
        }
        val prepared = typed?.let { (it, mime) -> it to describeAttachment(it, mime) }
        val preparedExtras = typedExtras.map { (it, mime) -> it to describeAttachment(it, mime) }
        val extrasMeta = preparedExtras.takeIf { it.isNotEmpty() }?.let { list -> JsonArray(list.map { it.second }) }
        val key = ledger.keyOf(canonicalOf(fields, prepared?.second) + extrasCanonical(extrasMeta) + "|" + target)

        if (ledger.needsConfirm(target) && !confirmed) return AimogeWriteOutcome.ConfirmFirst(AIMOGE_CONFIRM_NEW_POST_MESSAGE)
        return locked(key) {
            appScope.async {
                val previous = ledger.find(key)
                if (previous != null && previous.unconfirmed && previous.credentialId != null &&
                    previous.credentialId != identity.credentialId && !confirmed
                ) {
                    return@async AimogeWriteOutcome.ConfirmFirst(AIMOGE_UNCONFIRMED_POST_LEAD + AIMOGE_CONFIRM_NEW_POST_MESSAGE)
                }
                if (confirmed) ledger.setConfirm(target, false)
                var entry = previous ?: AimogeLedgerEntry(key = key, requestId = newRequestId(), target = target, savedAtMillis = nowMillis())
                entry = entry.copy(credentialId = identity.credentialId)
                // Written before anything is sent, so a crash keeps the requestId.
                if (!ledger.put(entry)) return@async AimogeWriteOutcome.Refused(AIMOGE_LEDGER_FULL_MESSAGE)

                if (prepared != null && entry.attachmentId == null) {
                    when (val up = upload(boardSlug, identity.credential, prepared.first, prepared.second)) {
                        is UploadResult.Done -> {
                            entry = entry.copy(attachmentId = up.attachmentId)
                            ledger.put(entry)
                        }
                        is UploadResult.Failed -> return@async up.outcome
                    }
                }
                // The 2nd and 3rd go up in order; an id already kept (a retry) is not uploaded again.
                for ((index, extra) in preparedExtras.withIndex()) {
                    if (index < entry.extraAttachmentIds.size) continue
                    when (val up = upload(boardSlug, identity.credential, extra.first, extra.second)) {
                        is UploadResult.Done -> {
                            entry = entry.copy(extraAttachmentIds = entry.extraAttachmentIds + up.attachmentId)
                            ledger.put(entry)
                        }
                        is UploadResult.Failed -> return@async up.outcome
                    }
                }
                var sentFields = prepared?.let { JsonObject(fields + ("_attachment" to it.second)) } ?: fields
                if (extrasMeta != null) sentFields = JsonObject(sentFields + (EXTRA_ATTACHMENTS_FIELD to extrasMeta))
                post(entry, identity, fields, boardSlug, known, knownIsThread, recordSent = sentFields, send = send)
            }.await()
        }
    }

    private sealed interface UploadResult {
        data class Done(val attachmentId: String) : UploadResult
        data class Failed(val outcome: AimogeWriteOutcome) : UploadResult
    }

    private suspend fun upload(boardSlug: String, token: String, attachment: AimogeAttachmentInput, meta: JsonObject): UploadResult {
        // presign may be tried once more by itself: no post has gone out yet.
        val answer = retryOnce { api.presign(boardSlug, token, meta) }
            ?: return UploadResult.Failed(AimogeWriteOutcome.Failed(AIMOGE_ATTACHMENT_FAILED_MESSAGE, null, null, kept = true))
        if (answer.status !in 200..299) return UploadResult.Failed(failureKept(answer))
        val obj = answer.json ?: return UploadResult.Failed(AimogeWriteOutcome.Failed(AIMOGE_ATTACHMENT_FAILED_MESSAGE, null, answer.status, kept = true))
        val id = (obj["attachmentId"] as? JsonPrimitive)?.contentOrNull
            ?: return UploadResult.Failed(AimogeWriteOutcome.Failed(AIMOGE_ATTACHMENT_FAILED_MESSAGE, null, answer.status, kept = true))
        val uploadUrl = (obj["uploadUrl"] as? JsonPrimitive)?.contentOrNull
        // null: the server already has the same bytes (reused). Nothing is put.
        if (uploadUrl != null) {
            val url = runCatching { Url(uploadUrl) }.getOrNull()
            if (url == null || url.protocol.name != "https") {
                return UploadResult.Failed(AimogeWriteOutcome.Failed(AIMOGE_ATTACHMENT_FAILED_MESSAGE, null, answer.status, kept = true))
            }
            val put = try {
                api.upload(uploadUrl, (meta["mime"] as JsonPrimitive).content, attachment.bytes)
            } catch (_: AimogeNoAnswerException) {
                return UploadResult.Failed(AimogeWriteOutcome.Failed(AIMOGE_ATTACHMENT_FAILED_MESSAGE, null, null, kept = true))
            }
            if (put.status !in 200..299) {
                return UploadResult.Failed(AimogeWriteOutcome.Failed(AIMOGE_ATTACHMENT_FAILED_MESSAGE, null, put.status, kept = true))
            }
        }
        return UploadResult.Done(id)
    }

    private suspend fun post(
        entry: AimogeLedgerEntry,
        identity: AimogeWriteIdentity,
        fields: JsonObject,
        boardSlug: String,
        known: Set<String>,
        knownIsThread: Boolean,
        recordSent: JsonObject? = null,
        send: suspend (String, JsonObject) -> AimogeWriteAnswer
    ): AimogeWriteOutcome {
        // A fresh Altcha for every send, taken after the attachment is up, right before the post.
        val altchaAnswer = retryOnce { api.altcha(boardSlug, identity.credential) }
            ?: return AimogeWriteOutcome.Failed("あいもげに接続できませんでした", null, null, kept = true)
        if (altchaAnswer.status !in 200..299) return failureKept(altchaAnswer)
        val challenge = altchaAnswer.json?.let(::parseAimogeAltcha)
            ?: return AimogeWriteOutcome.Failed(AIMOGE_ALTCHA_CHANGED_MESSAGE, null, altchaAnswer.status, kept = true)
        if (!isAimogeAltchaSolvable(challenge)) return AimogeWriteOutcome.Refused(AIMOGE_ALTCHA_CHANGED_MESSAGE)
        val solution = withContext(Dispatchers.Default) { solveAimogeAltcha(challenge) }
            ?: return AimogeWriteOutcome.Refused(AIMOGE_ALTCHA_CHANGED_MESSAGE)

        val attachmentMeta = recordSent?.get("_attachment") as? JsonObject
        val body = buildJsonObject {
            put("requestId", entry.requestId)
            fields.forEach { (k, v) -> put(k, v) }
            put("altcha", solution)
            entry.attachmentId?.let { id ->
                val extraMeta = recordSent?.get(EXTRA_ATTACHMENTS_FIELD) as? JsonArray
                val sources = listOf(attachmentMeta?.get("source") ?: JsonPrimitive("file")) +
                    entry.extraAttachmentIds.indices.map { i -> (extraMeta?.getOrNull(i) as? JsonObject)?.get("source") ?: JsonPrimitive("file") }
                if (entry.extraAttachmentIds.isEmpty()) put("attachmentId", id)
                else put("attachmentIds", JsonArray((listOf(id) + entry.extraAttachmentIds).map { JsonPrimitive(it) }))
                put("attachmentSources", JsonArray(sources))
            }
        }
        val answer = try {
            send(identity.credential, body)
        } catch (e: AimogeNoAnswerException) {
            markUnconfirmed(entry, recordSent ?: fields)
            return AimogeWriteOutcome.Unconfirmed(AIMOGE_UNCONFIRMED_POST_LEAD + if (e.timedOut) TIMEOUT_KIND else NETWORK_KIND)
        }
        if (answer.status in 200..299) {
            val obj = answer.json
            val result = obj?.let {
                val threadId = (it["threadId"] as? JsonPrimitive)?.contentOrNull
                val postId = (it["postId"] as? JsonPrimitive)?.contentOrNull
                val seq = (it["seq"] as? JsonPrimitive)?.longOrNull
                val status = (it["status"] as? JsonPrimitive)?.contentOrNull ?: "public"
                if (threadId != null && postId != null && seq != null) AimogeWriteResult(threadId, postId, seq, status) else null
            }
            if (result == null) {
                markUnconfirmed(entry, recordSent ?: fields)
                return AimogeWriteOutcome.Unconfirmed(AIMOGE_UNCONFIRMED_POST_LEAD + UNREADABLE_KIND)
            }
            ledger.remove(entry.key)
            val same = if (knownIsThread) result.threadId.lowercase() in known.map { it.lowercase() }
            else result.postId.lowercase() in known.map { it.lowercase() }
            return AimogeWriteOutcome.Posted(result, sameAsBefore = same)
        }
        val error = parseAimogeErrorBody(answer.body)
        val code = error.code
        val text = aimogeErrorText(code, answer.status, error.message) ?: aimogeStatusLead(answer.status)
        return when {
            code in AIMOGE_DEFINITE_POST_FAILURES || code == IDEMPOTENCY_CONFLICT -> {
                ledger.remove(entry.key)
                val mayHaveBeenTaken = entry.unconfirmed || code == IDEMPOTENCY_CONFLICT
                if (mayHaveBeenTaken) ledger.setConfirm(entry.target, true)
                AimogeWriteOutcome.Failed(
                    message = if (mayHaveBeenTaken) "$text。$AIMOGE_CHECK_PREVIOUS_SUFFIX" else text,
                    code = code, status = answer.status, kept = false, retryAfterSeconds = answer.retryAfterSeconds
                )
            }
            code in TOKEN_ERRORS && answer.status in setOf(401, 422) ->
                AimogeWriteOutcome.Failed(AIMOGE_APPROVAL_NEEDED_MESSAGE, code, answer.status, kept = true, needsApproval = true)
            // Another 409 (the same requestId still being processed), and a 5xx without one of the 17: not known.
            answer.status == 409 || (answer.status in 500..599) -> {
                markUnconfirmed(entry, recordSent ?: fields)
                AimogeWriteOutcome.Unconfirmed(AIMOGE_UNCONFIRMED_POST_LEAD + "（${answer.status}）")
            }
            else -> AimogeWriteOutcome.Failed(text, code, answer.status, kept = true, retryAfterSeconds = answer.retryAfterSeconds)
        }
    }

    private suspend fun markUnconfirmed(entry: AimogeLedgerEntry, sent: JsonObject) {
        // The delete key is never kept: a resend reads it again (J7).
        val kept = JsonObject(sent - "deleteKey")
        ledger.put(entry.copy(unconfirmed = true, sent = kept.toString(), savedAtMillis = nowMillis()))
    }

    private fun failureKept(answer: AimogeWriteAnswer): AimogeWriteOutcome {
        val error = parseAimogeErrorBody(answer.body)
        if (error.code in TOKEN_ERRORS && answer.status in setOf(401, 422)) {
            return AimogeWriteOutcome.Failed(AIMOGE_APPROVAL_NEEDED_MESSAGE, error.code, answer.status, kept = true, needsApproval = true)
        }
        return AimogeWriteOutcome.Failed(
            aimogeErrorText(error.code, answer.status, error.message) ?: aimogeStatusLead(answer.status),
            error.code, answer.status, kept = true, retryAfterSeconds = answer.retryAfterSeconds
        )
    }

    private fun actionFailure(answer: AimogeWriteAnswer): AimogeActionOutcome {
        val error = parseAimogeErrorBody(answer.body)
        if (error.code in TOKEN_ERRORS && answer.status in setOf(401, 422)) return AimogeActionOutcome.NeedsApproval
        if (answer.status == 404) return AimogeActionOutcome.Failed(AIMOGE_POST_NOT_FOUND_MESSAGE, error.code, 404)
        return AimogeActionOutcome.Failed(
            aimogeErrorText(error.code, answer.status, error.message) ?: aimogeStatusLead(answer.status), error.code, answer.status
        )
    }

    private fun actionUnconfirmed(label: String, timedOut: Boolean): String =
        "$label$AIMOGE_ACTION_RESULT_UNCONFIRMED${if (timedOut) TIMEOUT_KIND else NETWORK_KIND}"

    private suspend fun retryOnce(request: suspend () -> AimogeWriteAnswer): AimogeWriteAnswer? {
        repeat(2) { attempt ->
            try {
                return request()
            } catch (_: AimogeNoAnswerException) {
                if (attempt == 1) return null
            }
        }
        return null
    }

    private suspend fun <T> locked(key: String, block: suspend () -> T): T where T : Any {
        // The lock is taken and released under the guard, and dropped from the table once free (no growth).
        val lock = keyLocksGuard.withLock {
            val lock = keyLocks.getOrPut(key) { Mutex() }
            if (!lock.tryLock()) null else lock
        }
        @Suppress("UNCHECKED_CAST")
        if (lock == null) return AimogeWriteOutcome.Refused(AIMOGE_SENDING_MESSAGE) as T
        try {
            return block()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                keyLocksGuard.withLock {
                    lock.unlock()
                    if (keyLocks[key] === lock) keyLocks.remove(key)
                }
            }
        }
    }

    internal suspend fun heldKeyLockCount(): Int = keyLocksGuard.withLock { keyLocks.size }
}

/** The same words as Futaba's action messages (`HTTP_BOARD_API_ACTION_RESULT_UNCONFIRMED`), so screens tell them apart. */
const val AIMOGE_ACTION_RESULT_UNCONFIRMED = "の結果を確認できませんでした"

private fun targetOf(boardSlug: String, threadId: String?): String =
    if (threadId == null) "thread:${boardSlug.lowercase()}" else "reply:${threadId.lowercase()}"

/** Up to 3 pictures in one post (`attachmentIds`, OpenAPI 2026-11-11; the capability `ordered-multi-attachments-v1`). */
const val AIMOGE_MAX_ATTACHMENTS = 3
const val AIMOGE_TOO_MANY_ATTACHMENTS_MESSAGE = "あいもげに添付できるのは3個までです"
internal const val EXTRA_ATTACHMENTS_FIELD = "_extraAttachments"

private suspend fun describeAttachment(it: AimogeAttachmentInput, mime: String): JsonObject = buildJsonObject {
    // Hashed off the main thread: a picture of several MB froze the tap for a moment (audit 2026-11-12 L1).
    put("sha256", kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { it.bytes.toByteString().sha256().hex() })
    put("mime", mime)
    put("fileName", it.fileName)
    put("sizeBytes", it.bytes.size.toLong())
    put("source", it.source.wire)
}

/** Empty with no extra pictures, so a one-picture post keeps the key it always had. */
internal fun extrasCanonical(extras: JsonArray?): String =
    if (extras.isNullOrEmpty()) "" else "|+" + JsonArray(extras.map { e ->
        JsonObject((e as JsonObject).entries.sortedBy { it.key }.associate { it.key to it.value })
    }).toString()

/** Every sent field (sorted) and the attachment's description: the source of the record's key. */
internal fun canonicalOf(fields: JsonObject, attachmentMeta: JsonElement?): String {
    val sorted = JsonObject(fields.entries.sortedBy { it.key }.associate { it.key to it.value })
    return sorted.toString() + "|" + (attachmentMeta?.let { JsonObject((it as JsonObject).entries.sortedBy { e -> e.key }.associate { e -> e.key to e.value }).toString() } ?: "")
}

@OptIn(ExperimentalUuidApi::class)
private fun randomRequestId(): String = Uuid.random().toString()
