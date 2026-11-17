package com.valoser.futacha.shared.ui.board

/** A bounded, single-line reason suitable for the reference-style media error overlay. */
internal fun formatMediaLoadFailure(throwable: Throwable?): String? {
    val detail = generateSequence(throwable) { it.cause }
        .mapNotNull { cause ->
            cause.message
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
        .distinct()
        .take(2)
        .joinToString(" / ")
        .take(220)
    return detail.takeIf(String::isNotBlank)?.let { "理由: $it" }
}
