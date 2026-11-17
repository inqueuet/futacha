package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * A list serializer whose JSON decoding drops only the elements it cannot read
 * (for example an NG rule whose kind was added by a newer build) instead of
 * failing the whole persisted document. Encoding is the ordinary list form.
 */
open class LenientListSerializer<T>(
    private val elementSerializer: KSerializer<T>,
    private val label: String
) : KSerializer<List<T>> {
    private val delegate = ListSerializer(elementSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = jsonDecoder.decodeJsonElement() as? JsonArray
            ?: throw SerializationException("Expected a JSON array for $label")
        var dropped = 0
        val decoded = array.mapNotNull { element ->
            try {
                jsonDecoder.json.decodeFromJsonElement(elementSerializer, element)
            } catch (error: SerializationException) {
                dropped++
                null
            } catch (error: IllegalArgumentException) {
                dropped++
                null
            }
        }
        if (dropped > 0) Logger.w("CompatibilityPersistence", "Dropped $dropped unreadable $label")
        return decoded
    }
}

object LenientCompatNgRuleListSerializer :
    LenientListSerializer<CompatNgRule>(CompatNgRule.serializer(), "NG rules")

internal const val UNREADABLE_COMPATIBILITY_PAYLOAD_PREFIX = "compatibility/unreadable_compatibility_state-"

/**
 * Copies a persisted compatibility payload that could not be decoded next to
 * the database before the store starts from an empty profile, so a later write
 * never destroys the only copy. The file name is derived from the content, so
 * relaunching with the same unreadable payload keeps a single copy. Throws
 * when the copy cannot be written, so the caller does not continue and
 * overwrite the stored payload.
 */
internal suspend fun preserveUnreadableCompatibilityPayload(
    fileSystem: FileSystem,
    payload: String
): String {
    val bytes = payload.encodeToByteArray()
    val path = UNREADABLE_COMPATIBILITY_PAYLOAD_PREFIX +
        "${bytes.size}-${payload.hashCode().toUInt().toString(16)}.json"
    if (fileSystem.exists(path) &&
        fileSystem.getFileSize(path) == bytes.size.toLong() &&
        fileSystem.readString(path).getOrNull() == payload
    ) {
        return path
    }
    fileSystem.writeString(path, payload).getOrThrow()
    return path
}
