package com.valoser.futacha.shared.compat

enum class CompatImageNgSource {
    CATALOG,
    THREAD
}

fun compatImageNgKinds(source: CompatImageNgSource): Set<CompatNgKind> = when (source) {
    CompatImageNgSource.CATALOG -> setOf(
        CompatNgKind.CATALOG_IMAGE,
        CompatNgKind.CATALOG_IMAGE_PHASH
    )
    CompatImageNgSource.THREAD -> setOf(
        CompatNgKind.THREAD_IMAGE,
        CompatNgKind.THREAD_IMAGE_PHASH
    )
}

/**
 * 1.apk keeps one ImageNg table per source (catalog/thread), includes the
 * current board plus all-board rows, and orders registrations newest first.
 * Futacha historically split URL and pHash rows into separate kinds, so both
 * kinds must be surfaced by the single reference-compatible manager.
 */
fun compatImageNgManagementRules(
    rules: List<CompatNgRule>,
    boardKey: String,
    source: CompatImageNgSource,
    legacyThreadKey: String? = null
): List<CompatNgRule> = rules.asSequence()
    .filter { rule ->
        rule.kind in compatImageNgKinds(source) &&
            (
                rule.scopeKey == boardKey ||
                    rule.scopeKey == "*" ||
                    (source == CompatImageNgSource.THREAD && rule.scopeKey == legacyThreadKey)
            )
    }
    .sortedByDescending(CompatNgRule::createdAtEpochMillis)
    .toList()

fun compatImageNgFirstUrl(rule: CompatNgRule): String =
    rule.imageUrl?.takeIf(String::isNotBlank)
        ?: rule.normalizedValue.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }.orEmpty()

fun compatImageNgDisplayTitle(rule: CompatNgRule): String =
    rule.memo.takeIf(String::isNotBlank)
        ?: compatImageNgFirstUrl(rule)
            .substringAfterLast('/')
            .substringBefore('?')
            .takeIf(String::isNotBlank)
        ?: "NG画像"

fun compatImageNgMatchesSearch(rule: CompatNgRule, query: String): Boolean {
    val normalizedQuery = normalizeCompatSearchText(query)
    if (normalizedQuery.isBlank()) return true
    return normalizeCompatSearchText(compatImageNgDisplayTitle(rule)).contains(normalizedQuery) ||
        normalizeCompatSearchText(compatImageNgFirstUrl(rule)).contains(normalizedQuery)
}

fun compatImageNgBoardLabel(rule: CompatNgRule, currentBoardName: String): String =
    if (rule.scopeKey == "*") "全ての板" else currentBoardName.ifBlank { rule.scopeKey }
