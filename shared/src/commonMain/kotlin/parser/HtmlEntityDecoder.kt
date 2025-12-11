package com.valoser.futacha.shared.parser

internal object HtmlEntityDecoder {
    private val namedEntityMap = mapOf(
        // 基本的なHTML
        "lt" to "<",
        "gt" to ">",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        // 追加: 引用符
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "sbquo" to "‚",
        "bdquo" to "„",
        "prime" to "′",
        "Prime" to "″",
        // 追加: 記号
        "hellip" to "…",
        "bull" to "•",
        "middot" to "·",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "sect" to "§",
        "para" to "¶",
        "dagger" to "†",
        "Dagger" to "‡",
        "permil" to "‰",
        "euro" to "€",
        // 追加: 矢印
        "larr" to "←",
        "rarr" to "→",
        "uarr" to "↑",
        "darr" to "↓",
        "harr" to "↔",
        "crarr" to "↵",
        "lArr" to "⇐",
        "rArr" to "⇒",
        "uArr" to "⇑",
        "dArr" to "⇓",
        "hArr" to "⇔",
        // 追加: 数学記号
        "times" to "×",
        "divide" to "÷",
        "plusmn" to "±",
        "minus" to "−",
        "lowast" to "∗",
        "radic" to "√",
        "prop" to "∝",
        "infin" to "∞",
        "ang" to "∠",
        "and" to "∧",
        "or" to "∨",
        "cap" to "∩",
        "cup" to "∪",
        "int" to "∫",
        "there4" to "∴",
        "sim" to "∼",
        "cong" to "≅",
        "asymp" to "≈",
        "ne" to "≠",
        "equiv" to "≡",
        "le" to "≤",
        "ge" to "≥",
        "sub" to "⊂",
        "sup" to "⊃",
        "nsub" to "⊄",
        "sube" to "⊆",
        "supe" to "⊇",
        "oplus" to "⊕",
        "otimes" to "⊗",
        "perp" to "⊥",
        "sdot" to "⋅",
        // 追加: ギリシャ文字 (一部)
        "alpha" to "α",
        "beta" to "β",
        "gamma" to "γ",
        "delta" to "δ",
        "epsilon" to "ε",
        "zeta" to "ζ",
        "eta" to "η",
        "theta" to "θ",
        "iota" to "ι",
        "kappa" to "κ",
        "lambda" to "λ",
        "mu" to "μ",
        "nu" to "ν",
        "xi" to "ξ",
        "omicron" to "ο",
        "pi" to "π",
        "rho" to "ρ",
        "sigmaf" to "ς",
        "sigma" to "σ",
        "tau" to "τ",
        "upsilon" to "υ",
        "phi" to "φ",
        "chi" to "χ",
        "psi" to "ψ",
        "omega" to "ω",
        // その他
        "deg" to "°",
        "iexcl" to "¡",
        "cent" to "¢",
        "pound" to "£",
        "curren" to "¤",
        "yen" to "¥",
        "brvbar" to "¦",
        "uml" to "¨",
        "ordf" to "ª",
        "laquo" to "«",
        "not" to "¬",
        "shy" to "\u00AD",
        "macr" to "¯",
        "sup2" to "²",
        "sup3" to "³",
        "acute" to "´",
        "micro" to "µ",
        "cedil" to "¸",
        "sup1" to "¹",
        "ordm" to "º",
        "raquo" to "»",
        "frac14" to "¼",
        "frac12" to "½",
        "frac34" to "¾",
        "iquest" to "¿"
    )

    private val namedEntityRegex = Regex("&([a-zA-Z]+);")
    private val numericEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")

    fun decode(value: String): String {
        var result = value
        result = namedEntityRegex.replace(result) { match ->
            namedEntityMap[match.groupValues[1].lowercase()] ?: match.value
        }
        result = hexEntityRegex.replace(result) { match ->
            decodeCodePoint(match, radix = 16)
        }
        result = numericEntityRegex.replace(result) { match ->
            decodeCodePoint(match, radix = 10)
        }
        return result
    }

    private fun decodeCodePoint(match: MatchResult, radix: Int): String {
        val digits = match.groupValues.getOrNull(1) ?: return match.value
        val codePoint = runCatching { digits.toInt(radix) }.getOrNull() ?: return match.value
        if (!isAllowedCodePoint(codePoint)) return match.value
        return codePointToString(codePoint)
    }

    private fun isAllowedCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x20..0x10FFFF && codePoint !in 0xD800..0xDFFF
    }

    private fun codePointToString(codePoint: Int): String {
        return if (codePoint <= 0xFFFF) {
            codePoint.toChar().toString()
        } else {
            val cpPrime = codePoint - 0x10000
            val highSurrogate = ((cpPrime shr 10) + 0xD800).toChar()
            val lowSurrogate = ((cpPrime and 0x3FF) + 0xDC00).toChar()
            buildString {
                append(highSurrogate)
                append(lowSurrogate)
            }
        }
    }
}
