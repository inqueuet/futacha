package com.valoser.futacha.shared.compat

internal actual fun normalizeCompatWatchText(raw: String): String =
    java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).trim().uppercase(java.util.Locale.ROOT)
