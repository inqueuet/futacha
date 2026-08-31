package com.valoser.futacha.shared.compat

private const val COMPAT_DEFAULT_TEXT_MIN_SAMPLE_SIZE = 20

data class CompatBoardDefaultText(
    val defaultSubject: String = "無題",
    val defaultName: String = "名無し"
)

fun compatBoardDefaultSubjectPreferenceKey(boardKey: String): String =
    "compat.board_default.$boardKey.subject"

fun compatBoardDefaultNamePreferenceKey(boardKey: String): String =
    "compat.board_default.$boardKey.name"

fun normalizeCompatBoardDefaultText(raw: String?): String =
    raw.orEmpty().replace(Regex("<[^>]*>"), "").trim { it.isWhitespace() || it == '　' }

private fun compatMajorityOrNull(values: List<String>): String? {
    if (values.size < COMPAT_DEFAULT_TEXT_MIN_SAMPLE_SIZE) return null
    val winner = values.asSequence()
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()
        .maxByOrNull(Map.Entry<String, Int>::value)
        ?: return null
    return winner.key.takeIf { winner.value * 2 > values.size }
}

/** Mirrors sample/1.apk BoardDefaultTextLearning (20 samples, strict majority). */
fun learnCompatBoardDefaultText(
    current: CompatBoardDefaultText,
    posts: List<CompatPostSnapshot>
): CompatBoardDefaultText = CompatBoardDefaultText(
    defaultSubject = compatMajorityOrNull(posts.map { normalizeCompatBoardDefaultText(it.subject) })
        ?: current.defaultSubject,
    defaultName = compatMajorityOrNull(posts.map { normalizeCompatBoardDefaultText(it.author) })
        ?: current.defaultName
)

fun shouldHideCompatDefaultSubject(value: String?, learned: CompatBoardDefaultText): Boolean {
    val normalized = normalizeCompatBoardDefaultText(value)
    return normalized.isNotEmpty() && normalized in setOf(
        normalizeCompatBoardDefaultText(learned.defaultSubject),
        "無念",
        "無題"
    )
}

fun shouldHideCompatDefaultName(value: String?, learned: CompatBoardDefaultText): Boolean {
    val normalized = normalizeCompatBoardDefaultText(value)
    return normalized.isNotEmpty() && normalized in setOf(
        normalizeCompatBoardDefaultText(learned.defaultName),
        "としあき",
        "名無しさん",
        "名無し"
    )
}
