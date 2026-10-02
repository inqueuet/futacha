package com.valoser.futacha.shared.state

/**
 * Salvages entries from a damaged Preferences DataStore file
 * (`PreferenceMap { map<string, Value> preferences = 1; }`).
 *
 * DataStore's own parser rejects the whole file on the first error, which used
 * to reset boards, NG words and the app-lock password together. Here every
 * entry is decoded on its own: an unreadable entry is skipped, and after a
 * damaged region the scan resumes at the next byte range that decodes as a
 * complete entry with a plain ASCII key.
 */
internal sealed interface RecoveredPreference {
    data class BooleanValue(val value: Boolean) : RecoveredPreference
    data class StringValue(val value: String) : RecoveredPreference
    data class IntValue(val value: Int) : RecoveredPreference
    data class LongValue(val value: Long) : RecoveredPreference
    data class FloatValue(val value: Float) : RecoveredPreference
    data class DoubleValue(val value: Double) : RecoveredPreference
    data class StringSetValue(val value: Set<String>) : RecoveredPreference
    class BytesValue(val value: ByteArray) : RecoveredPreference
}

internal class DamagedPreferencesRecovery(
    val values: Map<String, RecoveredPreference>,
    /** True when the damaged bytes show an app-lock password that could not be recovered. */
    val appLockPasswordLost: Boolean
)

internal const val APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY = "app_lock_password_hash"
private const val ENTRY_TAG = 0x0A
private const val MAX_RESYNC_KEY_LENGTH = 256
private val appLockHashPattern = Regex("sha256-v1:[0-9a-f]{1,64}:[0-9a-f]{64}")

internal fun recoverDamagedPreferences(bytes: ByteArray): DamagedPreferencesRecovery {
    val values = LinkedHashMap<String, RecoveredPreference>()
    var position = 0
    while (position < bytes.size) {
        val reader = ProtoReader(bytes, position, bytes.size)
        val tag = reader.varint()
        val advanced = when {
            tag == null -> false
            tag == ENTRY_TAG.toLong() -> reader.lengthDelimited()?.let { (start, end) ->
                decodeEntry(bytes, start, end, requirePlainKey = false)?.let(values::putFirst)
                true
            } ?: false
            // Unknown top-level fields are skipped like protobuf does.
            (tag ushr 3) > 0 -> reader.skip((tag and 7).toInt())
            else -> false
        }
        position = if (advanced) reader.position else resync(bytes, position + 1, values) ?: break
    }
    // A decoded entry is authoritative (an empty value means the lock was turned off).
    if (APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY !in values) salvageAppLockHash(bytes, values)
    val lockValue = (values[APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY] as? RecoveredPreference.StringValue)?.value
    val lockLost = when {
        lockValue == null ->
            containsAscii(bytes, APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY) || containsAscii(bytes, "sha256-v1:")
        lockValue.isBlank() -> false
        else -> sanitizeAppLockPasswordHash(lockValue) == null
    }
    return DamagedPreferencesRecovery(values, appLockPasswordLost = lockLost)
}

private fun MutableMap<String, RecoveredPreference>.putFirst(entry: Pair<String, RecoveredPreference>) {
    if (entry.first !in this) put(entry.first, entry.second)
}

/** Finds the next offset at which a whole entry decodes, recording that entry. */
private fun resync(bytes: ByteArray, from: Int, values: MutableMap<String, RecoveredPreference>): Int? {
    var candidate = from
    while (candidate < bytes.size) {
        if (bytes[candidate].toInt() == ENTRY_TAG) {
            val reader = ProtoReader(bytes, candidate + 1, bytes.size)
            val range = reader.lengthDelimited()
            val entry = range?.let { (start, end) -> decodeEntry(bytes, start, end, requirePlainKey = true) }
            if (entry != null) {
                values.putFirst(entry)
                return reader.position
            }
        }
        candidate++
    }
    return null
}

/** The hash has a fixed, self-checking shape, so it can be found even when its entry is broken. */
private fun salvageAppLockHash(bytes: ByteArray, values: MutableMap<String, RecoveredPreference>) {
    val text = CharArray(bytes.size) { index -> (bytes[index].toInt() and 0xff).toChar() }.concatToString()
    val matches = appLockHashPattern.findAll(text)
        .map { it.value }
        .filter { sanitizeAppLockPasswordHash(it) != null }
        .distinct()
        .toList()
    // Two different candidates cannot be told apart; keep neither.
    if (matches.size == 1) values[APP_LOCK_PASSWORD_HASH_PREFERENCE_KEY] = RecoveredPreference.StringValue(matches.single())
}

private fun containsAscii(bytes: ByteArray, needle: String): Boolean {
    val pattern = needle.encodeToByteArray()
    if (pattern.isEmpty() || pattern.size > bytes.size) return false
    outer@ for (start in 0..bytes.size - pattern.size) {
        for (offset in pattern.indices) if (bytes[start + offset] != pattern[offset]) continue@outer
        return true
    }
    return false
}

private fun decodeEntry(
    bytes: ByteArray,
    start: Int,
    end: Int,
    requirePlainKey: Boolean
): Pair<String, RecoveredPreference>? {
    val reader = ProtoReader(bytes, start, end)
    var key: String? = null
    var value: RecoveredPreference? = null
    while (!reader.atEnd) {
        val tag = reader.varint() ?: return null
        when (tag) {
            0x0AL -> {
                val (keyStart, keyEnd) = reader.lengthDelimited() ?: return null
                key = decodeUtf8(bytes, keyStart, keyEnd) ?: return null
            }
            0x12L -> {
                val (valueStart, valueEnd) = reader.lengthDelimited() ?: return null
                value = decodeValue(bytes, valueStart, valueEnd) ?: return null
            }
            else -> if ((tag ushr 3) == 0L || !reader.skip((tag and 7).toInt())) return null
        }
    }
    val decodedKey = key?.takeIf { it.isNotEmpty() } ?: return null
    if (requirePlainKey && (decodedKey.length > MAX_RESYNC_KEY_LENGTH || decodedKey.any { it.code !in 0x21..0x7e })) {
        return null
    }
    return decodedKey to (value ?: return null)
}

private fun decodeValue(bytes: ByteArray, start: Int, end: Int): RecoveredPreference? {
    val reader = ProtoReader(bytes, start, end)
    var value: RecoveredPreference? = null
    while (!reader.atEnd) {
        val tag = reader.varint() ?: return null
        value = when (tag) {
            0x08L -> when (reader.varint()) {
                0L -> RecoveredPreference.BooleanValue(false)
                1L -> RecoveredPreference.BooleanValue(true)
                else -> return null
            }
            0x15L -> RecoveredPreference.FloatValue(Float.fromBits(reader.fixed32() ?: return null))
            0x18L -> RecoveredPreference.IntValue((reader.varint() ?: return null).toInt())
            0x20L -> RecoveredPreference.LongValue(reader.varint() ?: return null)
            0x2AL -> {
                val (valueStart, valueEnd) = reader.lengthDelimited() ?: return null
                RecoveredPreference.StringValue(decodeUtf8(bytes, valueStart, valueEnd) ?: return null)
            }
            0x32L -> {
                val (setStart, setEnd) = reader.lengthDelimited() ?: return null
                RecoveredPreference.StringSetValue(decodeStringSet(bytes, setStart, setEnd) ?: return null)
            }
            0x39L -> RecoveredPreference.DoubleValue(Double.fromBits(reader.fixed64() ?: return null))
            0x42L -> {
                val (valueStart, valueEnd) = reader.lengthDelimited() ?: return null
                RecoveredPreference.BytesValue(bytes.copyOfRange(valueStart, valueEnd))
            }
            else -> {
                if ((tag ushr 3) == 0L || !reader.skip((tag and 7).toInt())) return null
                value
            }
        }
    }
    return value
}

private fun decodeStringSet(bytes: ByteArray, start: Int, end: Int): Set<String>? {
    val reader = ProtoReader(bytes, start, end)
    val strings = LinkedHashSet<String>()
    while (!reader.atEnd) {
        val tag = reader.varint() ?: return null
        if (tag == 0x0AL) {
            val (itemStart, itemEnd) = reader.lengthDelimited() ?: return null
            strings += decodeUtf8(bytes, itemStart, itemEnd) ?: return null
        } else if ((tag ushr 3) == 0L || !reader.skip((tag and 7).toInt())) {
            return null
        }
    }
    return strings
}

private fun decodeUtf8(bytes: ByteArray, start: Int, end: Int): String? =
    runCatching { bytes.decodeToString(start, end, throwOnInvalidSequence = true) }.getOrNull()

private class ProtoReader(private val bytes: ByteArray, var position: Int, private val end: Int) {
    val atEnd: Boolean get() = position >= end

    fun varint(): Long? {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (position >= end) return null
            val byte = bytes[position++].toInt() and 0xff
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        return null
    }

    fun lengthDelimited(): Pair<Int, Int>? {
        val length = varint() ?: return null
        if (length < 0 || length > (end - position).toLong()) return null
        val start = position
        position += length.toInt()
        return start to position
    }

    fun fixed32(): Int? {
        if (end - position < 4) return null
        var value = 0
        for (index in 0 until 4) value = value or ((bytes[position + index].toInt() and 0xff) shl (8 * index))
        position += 4
        return value
    }

    fun fixed64(): Long? {
        if (end - position < 8) return null
        var value = 0L
        for (index in 0 until 8) value = value or ((bytes[position + index].toLong() and 0xff) shl (8 * index))
        position += 8
        return value
    }

    fun skip(wireType: Int): Boolean = when (wireType) {
        0 -> varint() != null
        1 -> fixed64() != null
        2 -> lengthDelimited() != null
        5 -> fixed32() != null
        else -> false
    }
}
