package com.valoser.futacha.shared.compat

/** Nijilog compares NFKC-normalized, case-folded catalog titles (not whole thread bodies). */
internal expect fun normalizeCompatWatchText(raw: String): String
