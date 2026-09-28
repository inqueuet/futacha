package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class AiProvider { DEVICE, OPENAI }

data class AiConnectionState(
    val loaded: Boolean = false,
    val summaryProvider: AiProvider = AiProvider.DEVICE,
    val moderationProvider: AiProvider = AiProvider.DEVICE,
    val summaryModel: String = DEFAULT_OPENAI_SUMMARY_MODEL,
    val hasApiKey: Boolean = false,
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
    val moderationCategories: Set<String> = DEFAULT_OPENAI_MODERATION_CATEGORIES
)

internal interface AiConnectionStorage {
    val supported: Boolean get() = true
    fun read(): String?
    fun write(value: String)
    fun readCache(): String? = null
    fun writeCache(value: String) = Unit
}

class AiConnectionStore internal constructor(private val storage: AiConnectionStorage) {
    private val mutex = Mutex()
    private var stored = StoredAiConnection()
    private val mutableState = MutableStateFlow(AiConnectionState(supportsSecureStorage = storage.supported))
    val state = mutableState.asStateFlow()
    internal val analysisMutex = Mutex()
    internal val cache = OpenAiAnalysisCache(storage)

    suspend fun load() = withContext(AppDispatchers.io) {
        mutex.withLock {
            if (state.value.loaded && state.value.storageError == null) return@withLock
            try {
                val saved = storage.read()?.let { Json.decodeFromString<StoredAiConnection>(it) } ?: StoredAiConnection()
                // External summary remains implemented but is not a selectable product feature.
                // Existing/experimental settings must never restore cloud summary execution.
                stored = StoredAiConnection(AiProvider.DEVICE, saved.moderationProvider, saved.summaryModel,
                    saved.apiKey, saved.moderationThreshold, saved.moderationAutoHide, saved.moderationCategories)
                publish()
            } catch (_: Exception) {
                mutableState.value = AiConnectionState(loaded = true, storageError = "AI接続設定を読み込めません。キーを再登録してください。", supportsSecureStorage = storage.supported)
            }
        }
    }

    /** null keeps the saved key; empty explicitly deletes it. */
    suspend fun save(summaryProvider: AiProvider, moderationProvider: AiProvider, model: String, replacementKey: String? = null,
        moderationThreshold: Float = state.value.moderationThreshold,
        moderationAutoHide: Boolean = state.value.moderationAutoHide,
        moderationCategories: Set<String> = state.value.moderationCategories) = withContext(AppDispatchers.io) {
        mutex.withLock {
            check(storage.supported) { "この環境ではAPIキーの安全な保存に対応していません。" }
            require(summaryProvider == AiProvider.DEVICE) { "スレ要約は端末内AIのみ利用できます。" }
            require(model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) { "モデルIDを確認してください。" }
            require(moderationThreshold.isFinite() && moderationThreshold in 0.05f..1f) { "閾値は0.05〜1.00で設定してください。" }
            require(moderationCategories.isNotEmpty() && moderationCategories.all { it in OPENAI_MODERATION_CATEGORIES }) { "判定カテゴリを1つ以上選択してください。" }
            val key = replacementKey?.trim() ?: stored.apiKey
            require(key.length <= 1024 && key.none { it.isWhitespace() || it.code < 32 }) { "APIキーの形式を確認してください。" }
            require((summaryProvider != AiProvider.OPENAI && moderationProvider != AiProvider.OPENAI) || key.isNotEmpty()) { "OpenAIのAPIキーを登録してください。" }
            val next = StoredAiConnection(summaryProvider, moderationProvider, model, key, moderationThreshold, moderationAutoHide, moderationCategories.toSet())
            try { storage.write(Json.encodeToString(StoredAiConnection.serializer(), next)) }
            catch (_: Exception) { throw IllegalStateException("AI接続設定を安全に保存できませんでした。") }
            stored = next
            publish()
        }
    }

    internal suspend fun apiKey(revision: Long): String = mutex.withLock {
        check(state.value.revision == revision && stored.apiKey.isNotEmpty()) {
            "AIの接続設定が変更されました。"
        }
        stored.apiKey
    }

    internal fun isCurrent(revision: Long) = state.value.revision == revision

    private fun publish() {
        mutableState.value = AiConnectionState(loaded = true, summaryProvider = stored.summaryProvider,
            moderationProvider = stored.moderationProvider, summaryModel = stored.summaryModel,
            hasApiKey = stored.apiKey.isNotEmpty(), revision = state.value.revision + 1, supportsSecureStorage = storage.supported,
            moderationThreshold = stored.moderationThreshold, moderationAutoHide = stored.moderationAutoHide,
            moderationCategories = stored.moderationCategories)
    }
}

expect fun getAiConnectionStore(platformContext: Any? = null): AiConnectionStore
