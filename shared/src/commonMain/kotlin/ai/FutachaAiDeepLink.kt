package com.valoser.futacha.shared.ai

private const val FUTACHA_AI_DEEP_LINK_MAX_CHARS = 64 * 1024
private const val FUTACHA_AI_QUERY_MAX_PAIRS = 64

fun parseFutachaAiDeepLink(raw: String, source: String = "deep-link"): FutachaAiCommand? {
    if (raw.length > FUTACHA_AI_DEEP_LINK_MAX_CHARS) return null
    val value = raw.trim()
    if (value.isEmpty()) return null

    val withoutFragment = value.substringBefore('#')
    val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
    val pathPart = withoutFragment.substringBefore('?')
    if (!pathPart.hasValidFutachaAiScheme()) return null
    val pathSegments = pathPart
        .replace("://", "/")
        .split('/')
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }

    val params = parseFutachaAiQuery(query).toMutableMap()
    val actionId = params.removeAiActionValue()
        ?: pathSegments.lastOrNull()?.takeIf { segment ->
            segment != "futacha" && segment != "ai"
        }?.let(::decodeAiQueryValue)
    val action = FutachaAiAction.fromId(actionId) ?: return null
    return FutachaAiCommand(
        action = action,
        parameters = sanitizeFutachaAiCommandParameters(params),
        source = source
    )
}

fun buildFutachaAiDeepLink(
    action: FutachaAiAction,
    parameters: Map<String, String> = emptyMap()
): String {
    val safeParameters = sanitizeFutachaAiCommandParameters(parameters)
    val query = buildString {
        append("action=")
        append(encodeAiQueryValue(action.id))
        for ((key, value) in safeParameters) {
            val encodedPair = "&${encodeAiQueryValue(key)}=${encodeAiQueryValue(value)}"
            if (length + encodedPair.length > FUTACHA_AI_DEEP_LINK_MAX_CHARS - "futacha://ai?".length) {
                break
            }
            append(encodedPair)
        }
    }
    return "futacha://ai?$query"
}

private fun String.hasValidFutachaAiScheme(): Boolean {
    if (!contains("://")) return true
    val scheme = substringBefore("://").trim().lowercase()
    val host = substringAfter("://")
        .substringBefore('/')
        .trim()
        .lowercase()
    return scheme == "futacha" && host == "ai"
}

private fun parseFutachaAiQuery(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query
        .split('&', ';', limit = FUTACHA_AI_QUERY_MAX_PAIRS + 1)
        .asSequence()
        .take(FUTACHA_AI_QUERY_MAX_PAIRS)
        .mapNotNull { pair ->
            val rawKey = pair.substringBefore('=', missingDelimiterValue = "").trim()
            if (rawKey.isEmpty()) return@mapNotNull null
            val rawValue = pair.substringAfter('=', missingDelimiterValue = "")
            decodeAiQueryValue(rawKey) to decodeAiQueryValue(rawValue)
        }
        .toMap()
}

internal fun decodeAiQueryValue(value: String): String {
    // Scan UTF-8 bytes directly. This avoids one boxed Byte object per input
    // byte and keeps the output allocation no larger than the encoded input.
    val input = value.encodeToByteArray()
    val output = ByteArray(input.size)
    var readIndex = 0
    var writeIndex = 0
    while (readIndex < input.size) {
        val current = input[readIndex].toInt() and 0xFF
        when {
            current == '+'.code -> {
                output[writeIndex++] = ' '.code.toByte()
                readIndex += 1
            }
            current == '%'.code && readIndex + 2 < input.size -> {
                val high = input[readIndex + 1].hexDigitValue()
                val low = input[readIndex + 2].hexDigitValue()
                if (high >= 0 && low >= 0) {
                    output[writeIndex++] = ((high shl 4) or low).toByte()
                    readIndex += 3
                } else {
                    output[writeIndex++] = input[readIndex++]
                }
            }
            else -> output[writeIndex++] = input[readIndex++]
        }
    }
    return output.copyOf(writeIndex).decodeToString()
}

private fun Byte.hexDigitValue(): Int = when (val value = toInt() and 0xFF) {
    in '0'.code..'9'.code -> value - '0'.code
    in 'a'.code..'f'.code -> value - 'a'.code + 10
    in 'A'.code..'F'.code -> value - 'A'.code + 10
    else -> -1
}

private fun MutableMap<String, String>.removeAiActionValue(): String? {
    val key = keys.firstOrNull { it.normalizedAiQueryKey() == "action" }
        ?: keys.firstOrNull { it.normalizedAiQueryKey() == "command" }
        ?: return null
    return remove(key)
}

private fun String.normalizedAiQueryKey(): String {
    return trim()
        .lowercase()
        .filter { it != '_' && it != '-' }
}

private fun encodeAiQueryValue(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    return buildString {
        value.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            when {
                char in unreserved -> append(char)
                char == ' ' -> append('+')
                else -> append('%').append(code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
