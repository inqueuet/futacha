package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.isValidCompatPreference
import com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val mixStoreMutex = Mutex()
private val mixJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
private data class MixFile(val version: Int = MIX_FORMAT_VERSION, val mixes: List<MixDefinition> = emptyList())

sealed interface MixLoadResult {
    data class Ok(val mixes: List<MixDefinition>) : MixLoadResult
    data class ReadOnly(val mixes: List<MixDefinition>, val reason: String) : MixLoadResult
}

sealed interface MixWriteResult {
    data object Ok : MixWriteResult
    data class Refused(val message: String) : MixWriteResult
}

internal const val MIX_READ_ONLY_MESSAGE = "保存の形が新しいため、ここでは変えられません"

private fun cleanName(raw: String): String = raw.filter { !it.isISOControl() }.trim()

private fun normalizeOnRead(def: MixDefinition): MixDefinition =
    def.copy(members = def.members.map { storeCanonicalBoardUrlOf(it) ?: it })

/** 純関数。壊れた値・新しい版は読める分だけ読んで ReadOnly。読むだけで保存し直さない。 */
fun decodeMixDefinitions(raw: String?): MixLoadResult {
    if (raw.isNullOrBlank()) return MixLoadResult.Ok(emptyList())
    if (!isValidCompatPreference(MIX_DEFINITIONS_KEY, raw))
        return MixLoadResult.ReadOnly(emptyList(), "保存データが大きすぎます")
    val file = try {
        mixJson.decodeFromString(MixFile.serializer(), raw)
    } catch (_: Throwable) {
        return MixLoadResult.ReadOnly(emptyList(), "壊れています")
    }
    val mixes = file.mixes.map(::normalizeOnRead)
    val duplicateIds = mixes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    val invalid = mixes.firstNotNullOfOrNull(::validateMixDefinition)
    val reason = when {
        file.version > MIX_FORMAT_VERSION -> MIX_READ_ONLY_MESSAGE
        file.version < 1 -> "保存データの版が不正です"
        mixes.size > MAX_MIX_COUNT -> "まとめカタログは${MAX_MIX_COUNT}個までです"
        duplicateIds.isNotEmpty() -> "まとめカタログのIDが重複しています"
        invalid != null -> invalid
        else -> null
    }
    if (reason == null) return MixLoadResult.Ok(mixes)
    // Preserve the original preference. Only unambiguous, bounded definitions can be displayed or fetched.
    val readable = mixes.filter { it.id !in duplicateIds && validateMixDefinition(it) == null }.take(MAX_MIX_COUNT)
    return MixLoadResult.ReadOnly(readable, reason)
}

class MixStore(private val store: CompatibilityStore) {
    suspend fun load(): MixLoadResult = try {
        decodeMixDefinitions(store.loadPreference(MIX_DEFINITIONS_KEY))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        MixLoadResult.ReadOnly(emptyList(), "保存データを読み込めませんでした")
    }

    suspend fun create(name: String, members: List<String>, order: MixOrderKind, nowMillis: Long): MixWriteResult =
        mutate { list ->
            if (list.size >= MAX_MIX_COUNT) return@mutate "まとめカタログは${MAX_MIX_COUNT}個までです"
            val id = "__mix__" + newMixIdSuffix(nowMillis, list)
            validateMixDefinition(MixDefinition(id, cleanName(name), members, order, nowMillis))?.let { return@mutate it }
            list + MixDefinition(id, cleanName(name), members, order, nowMillis)
        }

    suspend fun update(id: String, transform: (MixDefinition) -> MixDefinition): MixWriteResult = mutate { list ->
        val old = list.firstOrNull { it.id == id } ?: return@mutate "まとめカタログが見つかりません"
        val new = transform(old).let { it.copy(id = old.id, name = cleanName(it.name)) }
        validateMixDefinition(new)?.let { return@mutate it }
        list.map { if (it.id == id) new else it }
    }

    suspend fun move(id: String, toIndex: Int): MixWriteResult = mutate { list ->
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@mutate "まとめカタログが見つかりません"
        val mutable = list.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(toIndex.coerceIn(0, mutable.size), item)
        mutable
    }

    suspend fun delete(id: String): MixWriteResult = mutate { list ->
        if (list.none { it.id == id }) return@mutate "まとめカタログが見つかりません"
        list.filterNot { it.id == id }
    }

    /** lambda は Refused の文（String）か新しい一覧（List）を返す。 */
    private suspend fun mutate(block: (List<MixDefinition>) -> Any): MixWriteResult = mixStoreMutex.withLock {
        val loaded = load()
        if (loaded is MixLoadResult.ReadOnly) return MixWriteResult.Refused("まとめカタログを変更できません：${loaded.reason}")
        val current = (loaded as MixLoadResult.Ok).mixes
        @Suppress("UNCHECKED_CAST")
        val result = block(current)
        if (result is String) return MixWriteResult.Refused(result)
        @Suppress("UNCHECKED_CAST")
        val json = mixJson.encodeToString(MixFile.serializer(), MixFile(MIX_FORMAT_VERSION, result as List<MixDefinition>))
        if (!isValidCompatPreference(MIX_DEFINITIONS_KEY, json)) return MixWriteResult.Refused("まとめカタログが多すぎて保存できません")
        try {
            store.savePreference(MIX_DEFINITIONS_KEY, json)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return MixWriteResult.Refused("まとめカタログを保存できませんでした")
        }
        MixWriteResult.Ok
    }

}

/** Board membership can be unavailable or unknown; only structural limits make a definition invalid. */
private fun validateMixDefinition(def: MixDefinition): String? = when {
    def.id.isBlank() || def.id.any(Char::isISOControl) -> "まとめカタログのIDが不正です"
    def.name.isBlank() || def.name.any(Char::isISOControl) -> "まとめカタログの名前が不正です"
    def.name.length > MAX_MIX_NAME_CHARS -> "名前は${MAX_MIX_NAME_CHARS}字までです"
    def.members.isEmpty() -> "板を1つ以上選んでください"
    def.members.size > MAX_MIX_MEMBERS -> "板は${MAX_MIX_MEMBERS}個までです"
    else -> null
}

private fun newMixIdSuffix(nowMillis: Long, existing: List<MixDefinition>): String {
    var seed = nowMillis * 2654435761L + existing.size
    while (true) {
        val s = seed.toULong().toString(16).padStart(16, '0').takeLast(16)
        if (existing.none { it.id.endsWith(s) }) return s
        seed += 1
    }
}
