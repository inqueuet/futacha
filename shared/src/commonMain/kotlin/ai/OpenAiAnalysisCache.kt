package com.valoser.futacha.shared.ai

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.ByteString.Companion.encodeUtf8

internal fun aiDigest(value: String): String = value.encodeUtf8().sha256().hex()

@Serializable
internal data class AiCachedResult(val createdAt: Long, val value: JsonElement)

/** All access is serialized by AiConnectionStore.analysisMutex. No source text or keys. */
internal class OpenAiAnalysisCache(private val storage: AiConnectionStorage) {
    private var loaded = false
    private var dirty = false
    private val entries = linkedMapOf<String, AiCachedResult>()
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            storage.readCache()?.takeIf { it.length <= 4_000_000 }?.let {
                entries.putAll(Json.decodeFromString<Map<String, AiCachedResult>>(it))
            }
        } catch (_: Exception) { /* Cache loss is recoverable; credentials are separate. */ }
    }
    fun get(key: String): JsonElement? {
        ensureLoaded()
        val entry = entries[key] ?: return null
        if (Clock.System.now().toEpochMilliseconds() - entry.createdAt !in 0..86_400_000L) {
            entries.remove(key)
            dirty = true
            return null
        }
        return entry.value
    }
    fun put(key: String, value: JsonElement) {
        // Load first: a flush must never replace unread disk entries with this session's subset.
        ensureLoaded()
        entries.remove(key)
        entries[key] = AiCachedResult(Clock.System.now().toEpochMilliseconds(), value)
        while (entries.size > 4096) entries.remove(entries.keys.first())
        dirty = true
    }
    /** Writes only after a change; an unread or unchanged cache is never rewritten. */
    fun flush() {
        if (!loaded || !dirty) return
        try {
            var text = Json.encodeToString(entries.toMap())
            while (text.length > 3_000_000 && entries.isNotEmpty()) {
                entries.remove(entries.keys.first())
                text = Json.encodeToString(entries.toMap())
            }
            storage.writeCache(text)
            dirty = false
        } catch (_: Exception) { /* Keep the successful in-memory result if disk is full. */ }
    }
}
