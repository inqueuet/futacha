package com.valoser.futacha.shared.network.aimoge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.toLocalDateTime

/** The device's secret store for あいもげ (Android Keystore file, iOS Keychain). One JSON value. */
interface AimogeSecretStorage {
    fun read(): String?
    fun write(value: String)
}

@Serializable
internal data class AimogeStoredCredential(
    val credential: String,
    val credentialId: String,
    val expiresAtMillis: Long,
    val boardSlug: String
)

@Serializable
internal data class AimogePendingAuthorization(
    val state: String,
    val codeVerifier: String,
    val boardSlug: String,
    val installationId: String,
    val createdAtMillis: Long
)

/** A message held while the person approves (J1 / AM-B8). Attachments stay as paths of device-local copies. */
@Serializable
data class AimogeHeldDraft(
    val boardUrl: String,
    val threadId: String? = null,
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val body: String = "",
    val attachmentPaths: List<String> = emptyList(),
    val savedAtMillis: Long = 0L
) {
    override fun toString(): String = "AimogeHeldDraft(board=$boardUrl, thread=$threadId)"
}

@Serializable
internal data class AimogeSecrets(
    val installationId: String? = null,
    val credential: AimogeStoredCredential? = null,
    val pending: AimogePendingAuthorization? = null,
    val deleteKey: String? = null,
    val deleteKeyConsentAtMillis: Long? = null,
    val heldDraft: AimogeHeldDraft? = null
)

/** What the settings page shows. */
data class AimogeAuthStatus(
    val supported: Boolean,
    val approved: Boolean = false,
    val expiresAtMillis: Long? = null,
    val expiresSoon: Boolean = false,
    val hasDeleteKey: Boolean = false,
    val deleteKeyConsented: Boolean = false,
    /** The secret store could not be read (a locked Keychain, a broken file): nothing is overwritten. */
    val unreadable: Boolean = false
)

sealed interface AimogeBeginResult {
    /** Open this page in the browser (outside the app). */
    data class Open(val authorizationUrl: String) : AimogeBeginResult {
        override fun toString(): String = "Open(…)"
    }
    data class Failed(val message: String) : AimogeBeginResult
}

sealed interface AimogeReturnResult {
    data object Approved : AimogeReturnResult
    /** Not ours, already handled, or no waiting approval: nothing is shown. */
    data object Ignored : AimogeReturnResult
    data class Failed(val message: String) : AimogeReturnResult
}

sealed interface AimogeRevokeResult {
    data object Revoked : AimogeRevokeResult
    data class ServerUnconfirmed(val message: String) : AimogeRevokeResult
}

const val AIMOGE_PENDING_LIFETIME_MILLIS = 10 * 60 * 1000L
const val AIMOGE_HELD_DRAFT_LIFETIME_MILLIS = 24 * 60 * 60 * 1000L
const val AIMOGE_EXPIRES_SOON_MILLIS = 24 * 60 * 60 * 1000L
const val AIMOGE_START_RATE_LIMITED_MESSAGE = "しばらく待ってから承認してください"
const val AIMOGE_START_REFUSED_MESSAGE = "承認を始められませんでした。しばらくしてからやり直してください"
const val AIMOGE_START_OFFLINE_MESSAGE = "あいもげに接続できませんでした。しばらくしてからやり直してください"
const val AIMOGE_RETRY_APPROVAL_MESSAGE = "承認をやり直してください"
const val AIMOGE_AUTH_DISABLED_MESSAGE = "あいもげの承認は今止まっています。しばらくしてからやり直してください"
const val AIMOGE_APPROVAL_DENIED_MESSAGE = "承認されませんでした"
const val AIMOGE_APPROVAL_CANCELLED_MESSAGE = "承認をやめました"
const val AIMOGE_PENDING_EXPIRED_MESSAGE = "承認の期限が切れました。承認をやり直してください"
const val AIMOGE_REVOKE_UNCONFIRMED_MESSAGE = "サーバーでの取り消しを確かめられませんでした（資格は最長21日で切れます）"
const val AIMOGE_APPROVAL_REQUIRED_MESSAGE = "書き込むには、あいもげの承認が要ります"
const val AIMOGE_UNSUPPORTED_MESSAGE = "この端末では、あいもげの承認を保存できません"
const val AIMOGE_SAVE_FAILED_MESSAGE = "承認情報を保存できませんでした"
const val AIMOGE_UNREADABLE_MESSAGE = "承認情報を今は読めません（端末のロックを解いてから開き直してください）"
/** The iOS Keychain read keeps at most this many bytes; a larger value could not be read back. */
const val AIMOGE_SECRETS_MAX_BYTES = 65_536

/** The secret store could not be read or written. Its value is never in the message. */
class AimogeSecretStoreException(message: String) : Exception(message)

private val secretsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/**
 * The app-lifetime holder of the あいもげ approval. Nothing here sends a post: after an approval the person
 * presses send again (AM-B9/AM-B10).
 *
 * @param persistPending Android keeps the waiting approval in the secret store so that a return after the process
 * was stopped can still be exchanged. iOS keeps it in memory only (the return is delivered to the live session).
 */
class AimogeAuth(
    private val storage: AimogeSecretStorage?,
    private val api: AimogeAuthApi,
    private val persistPending: Boolean,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) {
    private val mutex = Mutex()
    private var memoryPending: AimogePendingAuthorization? = null
    private val mutableStatus = MutableStateFlow(AimogeAuthStatus(supported = storage != null))
    val status: StateFlow<AimogeAuthStatus> = mutableStatus.asStateFlow()

    init {
        runCatching { refreshStatus(load()) }
    }

    /** Set while the last read failed; a write then would erase what could not be read, so it is refused. */
    private var unreadable = false

    private fun load(): AimogeSecrets {
        val raw = try {
            storage?.read()
        } catch (e: Exception) {
            unreadable = true
            mutableStatus.value = mutableStatus.value.copy(unreadable = true, approved = false)
            return AimogeSecrets()
        }
        unreadable = false
        val text = raw?.takeIf { it.isNotBlank() } ?: return AimogeSecrets()
        return runCatching { secretsJson.decodeFromString(AimogeSecrets.serializer(), text) }.getOrDefault(AimogeSecrets())
    }

    /** Throws [AimogeSecretStoreException] when the store is unreadable, the value too large, or the write fails. */
    private fun save(secrets: AimogeSecrets) {
        val store = storage ?: return
        if (unreadable) throw AimogeSecretStoreException(AIMOGE_SAVE_FAILED_MESSAGE)
        val text = secretsJson.encodeToString(AimogeSecrets.serializer(), secrets)
        if (text.encodeToByteArray().size > AIMOGE_SECRETS_MAX_BYTES) throw AimogeSecretStoreException(AIMOGE_SAVE_FAILED_MESSAGE)
        try {
            store.write(text)
        } catch (e: Exception) {
            throw AimogeSecretStoreException(AIMOGE_SAVE_FAILED_MESSAGE)
        }
        refreshStatus(secrets)
    }

    private fun trySave(secrets: AimogeSecrets): Boolean = try { save(secrets); true } catch (_: AimogeSecretStoreException) { false }

    private fun refreshStatus(secrets: AimogeSecrets) {
        if (unreadable) return
        val now = nowMillis()
        val credential = secrets.credential?.takeIf { it.expiresAtMillis > now }
        mutableStatus.value = AimogeAuthStatus(
            supported = storage != null,
            approved = credential != null,
            expiresAtMillis = credential?.expiresAtMillis,
            expiresSoon = credential != null && credential.expiresAtMillis - now <= AIMOGE_EXPIRES_SOON_MILLIS,
            hasDeleteKey = !secrets.deleteKey.isNullOrEmpty(),
            deleteKeyConsented = secrets.deleteKeyConsentAtMillis != null
        )
    }

    /** Recomputes the expiry state (the settings page calls this when it opens). */
    suspend fun refresh() = mutex.withLock { refreshStatus(load()) }

    /** Starts an approval for [boardSlug]. On success the pending state is written before the browser opens. */
    suspend fun begin(boardSlug: String): AimogeBeginResult = mutex.withLock {
        if (storage == null) return@withLock AimogeBeginResult.Failed(AIMOGE_UNSUPPORTED_MESSAGE)
        var secrets = load()
        val installationId = secrets.installationId ?: newAimogeInstallationId().also {
            secrets = secrets.copy(installationId = it)
            if (!trySave(secrets)) return@withLock AimogeBeginResult.Failed(AIMOGE_SAVE_FAILED_MESSAGE)
        }
        val state = newAimogeState()
        val verifier = newAimogeCodeVerifier()
        val started = try {
            api.start(boardSlug, installationId, state, aimogeCodeChallenge(verifier))
        } catch (e: AimogeAuthException) {
            return@withLock AimogeBeginResult.Failed(
                when {
                    e.status == 503 && e.code == "APP_AUTH_DISABLED" -> AIMOGE_AUTH_DISABLED_MESSAGE
                    else -> when (e.status) {
                    429 -> AIMOGE_START_RATE_LIMITED_MESSAGE
                    null -> AIMOGE_START_OFFLINE_MESSAGE
                    else -> AIMOGE_START_REFUSED_MESSAGE
                    }
                }
            )
        }
        val pending = AimogePendingAuthorization(state, verifier, boardSlug, installationId, nowMillis())
        if (persistPending) {
            if (!trySave(secrets.copy(pending = pending))) return@withLock AimogeBeginResult.Failed(AIMOGE_SAVE_FAILED_MESSAGE)
        } else memoryPending = pending
        AimogeBeginResult.Open(started.authorizationUrl)
    }

    /** Forgets a waiting approval (the iOS session ended without a return, or the person cancelled). */
    suspend fun cancelPending() = mutex.withLock {
        memoryPending = null
        if (persistPending) {
            val secrets = load()
            if (secrets.pending != null) trySave(secrets.copy(pending = null))
        }
    }

    /**
     * Handles a return to [AIMOGE_REDIRECT_URI]. The waiting state is removed before the code is exchanged, so a
     * second delivery of the same address (a recreated Activity, a repeated tap) is ignored and the code is sent
     * once. Every exchange failure asks for a new approval; the code is never sent again.
     */
    suspend fun handleReturn(url: String): AimogeReturnResult = mutex.withLock {
        val refusal = parseAimogeAuthErrorReturn(url)
        if (refusal != null) {
            // access_denied / cancelled: the waiting approval is dropped and the person is told once.
            val secrets = load()
            val pending = (if (persistPending) secrets.pending else memoryPending) ?: return@withLock AimogeReturnResult.Ignored
            if (refusal.state != null && refusal.state != pending.state) return@withLock AimogeReturnResult.Ignored
            memoryPending = null
            if (persistPending) trySave(secrets.copy(pending = null))
            return@withLock AimogeReturnResult.Failed(
                if (refusal.cancelled) AIMOGE_APPROVAL_CANCELLED_MESSAGE else AIMOGE_APPROVAL_DENIED_MESSAGE
            )
        }
        val received = parseAimogeAuthReturn(url) ?: return@withLock AimogeReturnResult.Ignored
        var secrets = load()
        val pending = (if (persistPending) secrets.pending else memoryPending) ?: return@withLock AimogeReturnResult.Ignored
        if (pending.state != received.state) return@withLock AimogeReturnResult.Ignored
        memoryPending = null
        if (persistPending) {
            secrets = secrets.copy(pending = null)
            // The code is never exchanged unless the waiting state is gone (a second delivery must not send it again).
            if (!trySave(secrets)) return@withLock AimogeReturnResult.Failed(AIMOGE_SAVE_FAILED_MESSAGE)
        }
        if (nowMillis() - pending.createdAtMillis > AIMOGE_PENDING_LIFETIME_MILLIS) {
            return@withLock AimogeReturnResult.Failed(AIMOGE_PENDING_EXPIRED_MESSAGE)
        }
        val credential = try {
            api.exchange(pending.boardSlug, received.code, pending.codeVerifier)
        } catch (e: AimogeAuthException) {
            return@withLock AimogeReturnResult.Failed(
                if (e.status == 503 && e.code == "APP_AUTH_DISABLED") AIMOGE_AUTH_DISABLED_MESSAGE else AIMOGE_RETRY_APPROVAL_MESSAGE
            )
        }
        if (!trySave(load().copy(credential = AimogeStoredCredential(credential.credential, credential.credentialId, credential.expiresAtMillis, pending.boardSlug)))) {
            return@withLock AimogeReturnResult.Failed(AIMOGE_SAVE_FAILED_MESSAGE)
        }
        AimogeReturnResult.Approved
    }

    /** Revokes on the server; the local credential is removed whatever the server answers. */
    suspend fun revoke(): AimogeRevokeResult = mutex.withLock {
        val secrets = load()
        val credential = secrets.credential ?: return@withLock AimogeRevokeResult.Revoked
        val removedHere = trySave(secrets.copy(credential = null))
        if (!removedHere) return@withLock AimogeRevokeResult.ServerUnconfirmed(AIMOGE_SAVE_FAILED_MESSAGE)
        try {
            api.revoke(credential.boardSlug, credential.credential)
            AimogeRevokeResult.Revoked
        } catch (_: AimogeAuthException) {
            AimogeRevokeResult.ServerUnconfirmed(AIMOGE_REVOKE_UNCONFIRMED_MESSAGE)
        }
    }

    /** The credential to send with a write, checked on the device only (no request). Null when absent or expired. */
    suspend fun credentialForWrite(): String? = mutex.withLock {
        load().credential?.takeIf { it.expiresAtMillis > nowMillis() }?.credential
    }

    /** The credential and its id for a write (the record keeps the id to notice a new approval). Null when absent or expired. */
    suspend fun writeIdentity(): AimogeWriteIdentity? = mutex.withLock {
        load().credential?.takeIf { it.expiresAtMillis > nowMillis() }?.let { AimogeWriteIdentity(it.credential, it.credentialId) }
    }

    /** The delete key, only once the person agreed to send it (J7); null otherwise. */
    suspend fun consentedDeleteKey(): String? = mutex.withLock {
        val secrets = load()
        secrets.deleteKey?.takeIf { it.isNotEmpty() && secrets.deleteKeyConsentAtMillis != null }
    }

    /** Whether a write on [boardUrl] would need an approval first (the banner above the post screen). */
    fun needsApproval(): Boolean = status.value.supported && !status.value.approved

    suspend fun deleteKey(): String? = mutex.withLock { load().deleteKey }

    /** False when the key could not be saved (the store is unreadable or failed). */
    suspend fun setDeleteKey(value: String?): Boolean = mutex.withLock {
        val secrets = load()
        val next = value?.takeIf { it.isNotEmpty() }
        // A changed key must be agreed to again (J7): the server ties the key to the account.
        val consent = if (next == secrets.deleteKey) secrets.deleteKeyConsentAtMillis else null
        trySave(secrets.copy(deleteKey = next, deleteKeyConsentAtMillis = consent))
    }

    suspend fun setDeleteKeyConsent(agreed: Boolean): Boolean = mutex.withLock {
        val secrets = load()
        trySave(secrets.copy(deleteKeyConsentAtMillis = if (agreed && secrets.deleteKey != null) nowMillis() else null))
    }

    suspend fun holdDraft(draft: AimogeHeldDraft): Boolean = mutex.withLock {
        trySave(load().copy(heldDraft = draft.copy(savedAtMillis = nowMillis())))
    }

    /** The held draft for [boardUrl] and [threadId], or null (also once 24 hours have passed; then it is removed). */
    suspend fun takeHeldDraft(boardUrl: String, threadId: String?): AimogeHeldDraft? = mutex.withLock {
        val secrets = load()
        val held = secrets.heldDraft ?: return@withLock null
        if (nowMillis() - held.savedAtMillis > AIMOGE_HELD_DRAFT_LIFETIME_MILLIS) {
            trySave(secrets.copy(heldDraft = null))
            return@withLock null
        }
        if (held.boardUrl != boardUrl || held.threadId != threadId) return@withLock null
        trySave(secrets.copy(heldDraft = null))
        held
    }

    /** Drops an expired held draft and an expired waiting approval (called at launch). */
    suspend fun discardExpired() = mutex.withLock {
        val secrets = load()
        val now = nowMillis()
        val draft = secrets.heldDraft?.takeIf { now - it.savedAtMillis <= AIMOGE_HELD_DRAFT_LIFETIME_MILLIS }
        val pending = secrets.pending?.takeIf { now - it.createdAtMillis <= AIMOGE_PENDING_LIFETIME_MILLIS }
        if (draft != secrets.heldDraft || pending != secrets.pending) trySave(secrets.copy(heldDraft = draft, pending = pending))
    }

    internal suspend fun hasPersistedPending(): Boolean = mutex.withLock { load().pending != null }
    internal fun hasMemoryPending(): Boolean = memoryPending != null
}

/** The app's one [AimogeAuth], set by each host at launch (null on Desktop, where あいもげ is off). */
object AimogeAuthRegistry {
    var auth: AimogeAuth? = null
}

/** Formats the expiry as 「M/d HH:mm」 in the device's zone. */
fun aimogeExpiryText(expiresAtMillis: Long, zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault()): String {
    val t = kotlin.time.Instant.fromEpochMilliseconds(expiresAtMillis).toLocalDateTime(zone)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.month.ordinal + 1}/${t.day} ${two(t.hour)}:${two(t.minute)}"
}

/** What a write sends as `x-aimg-token`, and the id kept in the request record. Never printed. */
class AimogeWriteIdentity(val credential: String, val credentialId: String) {
    override fun toString(): String = "AimogeWriteIdentity(…)"
}
