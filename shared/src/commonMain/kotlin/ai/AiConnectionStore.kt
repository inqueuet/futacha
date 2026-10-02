package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

enum class AiProvider { DEVICE, OPENAI, BOTH }
internal const val MAX_OPENAI_KEYS = 16
internal const val MAX_AI_CREDENTIAL_BYTES = 65_536

data class AiApiKeyInfo(val id: String, val name: String, val enabled: Boolean, val maskedKey: String)

/** Not a data class: toString must never expose credentials. */
@Serializable
internal class StoredOpenAiKey(val id: String, val name: String, val key: String, val enabled: Boolean = true)


data class AiConnectionState(
    val loaded: Boolean = false,
    val summaryProvider: AiProvider = AiProvider.DEVICE,
    val moderationProvider: AiProvider = AiProvider.DEVICE,
    val summaryModel: String = DEFAULT_OPENAI_SUMMARY_MODEL,
    val hasApiKey: Boolean = false,
    val apiKeys: List<AiApiKeyInfo> = emptyList(),
    val revision: Long = 0,
    val storageError: String? = null,
    val supportsSecureStorage: Boolean = true,
    val moderationThreshold: Float = 0.7f,
    val moderationAutoHide: Boolean = true,
    val moderationCategories: Set<String> = DEFAULT_OPENAI_MODERATION_CATEGORIES
)

const val DEFAULT_OPENAI_SUMMARY_MODEL = "gpt-6-luna"
val OPENAI_SUMMARY_MODELS = listOf("gpt-6-luna", "gpt-6-sol", "gpt-6-astra", "gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini")
const val OPENAI_MODERATION_MODEL = "omni-moderation-latest"

/** Kept out of normal preferences, backups, analytics and saveable Compose state. */
@Serializable
internal class StoredAiConnection(
    val summaryProvider: AiProvider = AiProvider.DEVICE,
    val moderationProvider: AiProvider = AiProvider.DEVICE,
    val summaryModel: String = DEFAULT_OPENAI_SUMMARY_MODEL,
    val apiKey: String = "",
    val moderationThreshold: Float = 0.7f,
    val moderationAutoHide: Boolean = true,
    val moderationCategories: Set<String> = DEFAULT_OPENAI_MODERATION_CATEGORIES,
    val apiKeys: List<StoredOpenAiKey> = emptyList()
)

/**
 * Tolerates settings written by another build: unknown fields are ignored and
 * an unknown provider falls back to its default, so a downgrade or upgrade
 * never makes the saved API keys unreadable.
 */
internal val AI_CONNECTION_JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

internal interface AiConnectionStorage {
    val supported: Boolean get() = true
    fun read(): String?
    fun write(value: String)
    fun readUsage(): String? = null
    fun writeUsage(value: String) = Unit
    fun readCache(): String? = null
    fun writeCache(value: String) = Unit
}

class AiConnectionStore internal constructor(
    private val storage: AiConnectionStorage,
    internal val requestQueue: OpenAiRequestQueue = OpenAiRequestQueue()
) {
    private val mutex = Mutex()
    private val rotationMutex = Mutex()
    private var stored = StoredAiConnection()
    private val mutableState = MutableStateFlow(AiConnectionState(supportsSecureStorage = storage.supported))
    val state = mutableState.asStateFlow()
    internal val analysisMutex = Mutex()
    internal val cache = OpenAiAnalysisCache(storage)
    internal val usage = OpenAiUsageTracker(storage, requestQueue.nowMillis)

    suspend fun load() = withContext(AppDispatchers.io) {
        usage.load()
        mutex.withLock {
            if (state.value.loaded && state.value.storageError == null) return@withLock
            try {
                val saved = storage.read()?.let { AI_CONNECTION_JSON.decodeFromString(StoredAiConnection.serializer(), it) } ?: StoredAiConnection()
                // External summary remains implemented but is not a selectable product feature.
                // Existing/experimental settings must never restore cloud summary execution.
                val migrated = saved.apiKeys.ifEmpty {
                    saved.apiKey.takeIf { it.isNotBlank() }?.let { listOf(StoredOpenAiKey("legacy", "OpenAI 1", it)) }.orEmpty()
                }
                require(migrated.size <= MAX_OPENAI_KEYS && migrated.map { it.id }.distinct().size == migrated.size)
                stored = StoredAiConnection(AiProvider.DEVICE, saved.moderationProvider, saved.summaryModel,
                    moderationThreshold = saved.moderationThreshold, moderationAutoHide = saved.moderationAutoHide,
                    moderationCategories = knownModerationCategories(saved.moderationCategories), apiKeys = migrated)
                if (saved.apiKey.isNotEmpty()) {
                    persist(stored)
                    usage.migrateLegacyKey(migrated.first().id)
                }
                publish()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val previous = state.value
                mutableState.value = AiConnectionState(loaded = true, storageError = AI_CONNECTION_LOAD_ERROR,
                    supportsSecureStorage = storage.supported,
                    revision = previous.revision + if (previous.storageError == AI_CONNECTION_LOAD_ERROR) 0 else 1)
            }
        }
    }

    /** Explicit recovery from an unreadable store: replaces it with defaults (no keys). */
    suspend fun resetUnreadableSettings() = withContext(AppDispatchers.io) {
        mutex.withLock {
            check(state.value.storageError != null) { "AI接続設定は読み込み済みです。" }
            check(storage.supported) { "この環境ではAPIキーの安全な保存に対応していません。" }
            val next = StoredAiConnection()
            persist(next)
            stored = next
            usage.retainKeys(emptySet(), resetOrder = true)
            publish()
        }
    }

    /** null keeps the saved key; empty explicitly deletes it. */
    suspend fun save(summaryProvider: AiProvider, moderationProvider: AiProvider, model: String, replacementKey: String? = null,
        moderationThreshold: Float = state.value.moderationThreshold,
        moderationAutoHide: Boolean = state.value.moderationAutoHide,
        moderationCategories: Set<String> = state.value.moderationCategories) = withContext(AppDispatchers.io) {
        mutex.withLock {
            check(storage.supported) { "この環境ではAPIキーの安全な保存に対応していません。" }
            // After a failed load `stored` is empty: saving now would erase every saved key.
            check(state.value.loaded && state.value.storageError == null) { "AI接続設定を読み込めないため保存できません。再読み込みするか、設定を初期化してください。" }
            require(summaryProvider == AiProvider.DEVICE) { "スレ要約は端末内AIのみ利用できます。" }
            require(model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) { "モデルIDを確認してください。" }
            require(moderationThreshold.isFinite() && moderationThreshold in 0.05f..1f) { "閾値は0.05〜1.00で設定してください。" }
            require(moderationCategories.isNotEmpty() && moderationCategories.all { it in OPENAI_MODERATION_CATEGORIES }) { "判定カテゴリを1つ以上選択してください。" }
            val keys = when {
                replacementKey == null -> stored.apiKeys
                replacementKey.isBlank() -> emptyList()
                else -> {
                    val key = validatedKey(replacementKey)
                    val old = stored.apiKeys.firstOrNull()
                    val id = if (old?.key == key) old.id else newKeyId()
                    listOf(StoredOpenAiKey(id, old?.name ?: "OpenAI 1", key)) + stored.apiKeys.drop(1)
                }
            }
            require(keys.map { it.key }.distinct().size == keys.size) { "同じAPIキーは登録済みです。" }
            require(moderationProvider == AiProvider.DEVICE || keys.any { it.enabled }) { "有効なOpenAIのAPIキーを登録してください。" }
            val next = StoredAiConnection(summaryProvider, moderationProvider, model,
                moderationThreshold = moderationThreshold, moderationAutoHide = moderationAutoHide,
                moderationCategories = moderationCategories.toSet(), apiKeys = keys)
            persist(next)
            stored = next
            usage.retainKeys(keys.map { it.id }.toSet())
            publish()
        }
    }

    internal suspend fun apiKey(revision: Long): String = mutex.withLock {
        check(state.value.revision == revision && stored.apiKeys.any { it.enabled }) {
            "AIの接続設定が変更されました。"
        }
        stored.apiKeys.first { it.enabled }.key
    }

    private fun newKeyId() = "api-" + Random.nextLong().toULong().toString(16)
    private fun validatedKey(value: String): String = value.trim().also { key ->
        require(key.isNotEmpty() && key.length <= 1024 && key.none { it.isWhitespace() || it.code < 32 }) { "APIキーの形式を確認してください。" }
    }
    private fun persist(next: StoredAiConnection) {
        try {
            val encoded = AI_CONNECTION_JSON.encodeToString(StoredAiConnection.serializer(), next)
            check(encoded.encodeToByteArray().size <= MAX_AI_CREDENTIAL_BYTES)
            storage.write(encoded)
        } catch (_: Exception) { throw IllegalStateException("AI接続設定を安全に保存できませんでした。") }
    }
    private suspend fun changeKeys(resetOrder: Boolean = false, change: (List<StoredOpenAiKey>) -> List<StoredOpenAiKey>) = withContext(AppDispatchers.io) {
        mutex.withLock {
            check(state.value.loaded && state.value.storageError == null && storage.supported) { "AI接続設定を読み込めません。" }
            val keys = change(stored.apiKeys)
            val next = StoredAiConnection(AiProvider.DEVICE,
                if (keys.any { it.enabled }) stored.moderationProvider else AiProvider.DEVICE, stored.summaryModel,
                moderationThreshold = stored.moderationThreshold, moderationAutoHide = stored.moderationAutoHide,
                moderationCategories = stored.moderationCategories, apiKeys = keys)
            persist(next)
            stored = next
            usage.retainKeys(keys.map { it.id }.toSet(), resetOrder)
            publish()
        }
    }
    suspend fun saveApiKey(id: String?, name: String, replacementKey: String? = null) = changeKeys { keys ->
        val label = name.trim()
        require(label.isNotEmpty() && label.length <= 60 && label.none { it.code < 32 }) { "名前は1〜60文字で入力してください。" }
        val old = id?.let { target -> keys.firstOrNull { it.id == target } ?: error("このAPIは削除されています。") }
        val key = replacementKey?.takeIf { it.isNotBlank() }?.let(::validatedKey) ?: old?.key ?: error("APIキーを入力してください。")
        require(keys.none { it.id != id && it.key == key }) { "同じAPIキーは登録済みです。" }
        require(old != null || keys.size < MAX_OPENAI_KEYS) { "登録できるAPIは最大${MAX_OPENAI_KEYS}件です。" }
        val entry = StoredOpenAiKey(if (old?.key == key) old.id else newKeyId(), label, key, old?.enabled ?: true)
        if (old == null) keys + entry else keys.map { if (it.id == old.id) entry else it }
    }
    suspend fun setApiKeyEnabled(id: String, enabled: Boolean) = changeKeys { keys ->
        require(keys.any { it.id == id }) { "このAPIは削除されています。" }
        keys.map { if (it.id == id) StoredOpenAiKey(it.id, it.name, it.key, enabled) else it }
    }
    suspend fun deleteApiKey(id: String) = changeKeys { keys -> keys.filterNot { it.id == id } }
    suspend fun moveApiKey(id: String, direction: Int) = changeKeys(resetOrder = true) { keys ->
        val index = keys.indexOfFirst { it.id == id }
        require(index >= 0 && direction in listOf(-1, 1))
        val destination = (index + direction).coerceIn(0, keys.lastIndex)
        keys.toMutableList().apply { add(destination, removeAt(index)) }
    }
    private val mutableKeyRetries = MutableStateFlow(0)
    /**
     * Counts explicit key retries. A retry does not change [AiConnectionState.revision] (services
     * and their caches are kept), so moderation paused by the failed key follows this instead.
     */
    val keyRetries: StateFlow<Int> = mutableKeyRetries.asStateFlow()
    suspend fun retryApiKey(id: String) {
        usage.clearKeyBlock(id)
        mutableKeyRetries.update { it + 1 }
    }

    /** Keep using the selected key; move forward only when it cannot serve the request. */
    internal suspend fun <T> executeRequest(revision: Long, tokens: Int, moderation: Boolean,
        request: suspend (id: String, key: String) -> T): T = rotationMutex.withLock {
        val deadline = requestQueue.nowMillis() + 120_000L
        val attempted = mutableSetOf<String>()
        var retryCount = 0
        var lastFailure: OpenAiFailure? = null
        while (true) {
            val keys = mutex.withLock {
                if (!isCurrent(revision)) throw kotlinx.coroutines.CancellationException("AI connection changed")
                stored.apiKeys.filter { it.enabled }
            }
            if (keys.isEmpty()) throw OpenAiFailure("有効なAPIがありません。API管理で登録・有効化してください。")
            usage.observeClock()
            val active = usage.state.value.activeKeyId
            val index = keys.indexOfFirst { it.id == active }.coerceAtLeast(0)
            val ordered = keys.drop(index) + keys.take(index)
            val now = requestQueue.nowMillis()
            val candidates = ordered.filter { it.id !in attempted && usage.state.value.keys[it.id]?.blockedReason.isNullOrEmpty() }
            val selected = candidates.firstOrNull { (usage.state.value.keys[it.id]?.requiredWait(now, tokens) ?: 0) == 0L }
            if (selected == null) {
                val wait = candidates.minOfOrNull { usage.state.value.keys[it.id]?.requiredWait(now, tokens) ?: 0 }
                if (wait != null && wait > 0 && now + wait <= deadline) {
                    requestQueue.waitFor(wait)
                    continue
                }
                throw lastFailure ?: OpenAiFailure("利用できるAPIがありません。API管理で待ち時間・認証・利用枠を確認してください。")
            }
            try {
                return@withLock requestQueue.execute(tokens, if (moderation) usage else null,
                    localLimitsOnly = true, retryRateLimits = false) {
                    if (!isCurrent(revision)) throw kotlinx.coroutines.CancellationException("AI connection changed")
                    usage.keyAttempt(selected.id, tokens)
                    request(selected.id, selected.key)
                }
            } catch (failure: OpenAiFailure) {
                lastFailure = failure
                if (failure.status !in setOf(401, 403, 429)) throw failure
                attempted += selected.id
                // Once every usable key has answered 429, wait for the earliest per-key deadline
                // (as with a single key) instead of failing immediately.
                if (failure.retryableRateLimit && requestQueue.nowMillis() < deadline &&
                    keys.all { it.id in attempted || !usage.state.value.keys[it.id]?.blockedReason.isNullOrEmpty() } &&
                    retryCount++ < 3) attempted.clear()
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    internal fun isCurrent(revision: Long) = state.value.revision == revision

    /**
     * Records a server response for a credential that is still registered, even if
     * unrelated settings changed during the request; a deleted/replaced key is ignored.
     */
    internal suspend fun recordResponse(credentialId: String, moderation: Boolean, status: Int,
        headers: io.ktor.http.Headers, permanentQuota: Boolean) = mutex.withLock {
        if (stored.apiKeys.none { it.id == credentialId }) return@withLock
        if (moderation) usage.response(status, headers, permanentQuota)
        usage.keyResponse(credentialId, status, headers, permanentQuota)
    }

    /**
     * The revision changes only when the effective connection changes. Renaming, reordering or
     * swapping keys is read live by [executeRequest] and must not recreate services or cancel work.
     */
    private fun publish() {
        val previous = state.value
        val next = AiConnectionState(loaded = true, summaryProvider = stored.summaryProvider,
            moderationProvider = stored.moderationProvider, summaryModel = stored.summaryModel,
            hasApiKey = stored.apiKeys.any { it.enabled },
            apiKeys = stored.apiKeys.map { AiApiKeyInfo(it.id, it.name, it.enabled, "••••" + it.key.takeLast(4)) }, revision = previous.revision, supportsSecureStorage = storage.supported,
            moderationThreshold = stored.moderationThreshold, moderationAutoHide = stored.moderationAutoHide,
            moderationCategories = stored.moderationCategories)
        val changed = !previous.loaded || previous.copy(apiKeys = emptyList()) != next.copy(apiKeys = emptyList()) ||
            previous.apiKeys.filter { it.enabled }.map { it.id }.toSet() !=
                next.apiKeys.filter { it.enabled }.map { it.id }.toSet()
        mutableState.value = if (changed) next.copy(revision = previous.revision + 1) else next
    }
}

/**
 * Categories written by another build may be unknown here; [AiConnectionStore.save] accepts only
 * known ones, so unknown ones are dropped on load (the defaults if none is left).
 */
internal fun knownModerationCategories(saved: Set<String>): Set<String> =
    saved.filterTo(linkedSetOf()) { it in OPENAI_MODERATION_CATEGORIES }.ifEmpty { DEFAULT_OPENAI_MODERATION_CATEGORIES }

internal const val AI_CONNECTION_LOAD_ERROR = "AI接続設定を読み込めません。再読み込みしても解消しない場合は、設定を初期化してキーを再登録してください。"

expect fun getAiConnectionStore(platformContext: Any? = null): AiConnectionStore
