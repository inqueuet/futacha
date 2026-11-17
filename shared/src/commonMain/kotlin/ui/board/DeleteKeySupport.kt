package com.valoser.futacha.shared.ui.board

private const val MAX_STORED_DELETE_KEY_LENGTH = 8

internal fun sanitizeStoredDeleteKey(value: String): String {
    return value.trim().take(MAX_STORED_DELETE_KEY_LENGTH)
}

internal fun normalizeDeleteKeyForSubmit(value: String): String {
    return value.trim()
}

internal fun hasDeleteKeyForSubmit(value: String): Boolean {
    return normalizeDeleteKeyForSubmit(value).isNotBlank()
}

internal fun resolveDeleteKeyAutofill(
    currentValue: String,
    lastUsedDeleteKey: String
): String {
    return if (currentValue.isBlank()) lastUsedDeleteKey else currentValue
}
