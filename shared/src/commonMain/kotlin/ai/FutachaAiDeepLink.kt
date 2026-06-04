package com.valoser.futacha.shared.ai

private val queryPairSeparator = Regex("[&;]")

fun parseFutachaAiDeepLink(raw: String, source: String = "deep-link"): FutachaAiCommand? {
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
    val query = buildString {
        append("action=")
        append(encodeAiQueryValue(action.id))
        parameters.forEach { (key, value) ->
            append('&')
            append(encodeAiQueryValue(key))
            append('=')
            append(encodeAiQueryValue(value))
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
    return queryPairSeparator
        .split(query)
        .mapNotNull { pair ->
            val rawKey = pair.substringBefore('=', missingDelimiterValue = "").trim()
            if (rawKey.isEmpty()) return@mapNotNull null
            val rawValue = pair.substringAfter('=', missingDelimiterValue = "")
            decodeAiQueryValue(rawKey) to decodeAiQueryValue(rawValue)
        }
        .toMap()
}

private fun decodeAiQueryValue(value: String): String {
    val plusNormalized = value.replace('+', ' ')
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < plusNormalized.length) {
        val current = plusNormalized[index]
        if (current == '%' && index + 2 < plusNormalized.length) {
            val hex = plusNormalized.substring(index + 1, index + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                bytes += code.toByte()
                index += 3
                continue
            }
        }
        current.toString().encodeToByteArray().forEach { bytes += it }
        index += 1
    }
    return bytes.toByteArray().decodeToString()
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
