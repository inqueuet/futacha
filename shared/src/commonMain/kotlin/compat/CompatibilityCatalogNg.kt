package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem

/**
 * Catalog NG matching kept in one place so imported keyword.cfg rules and
 * rules created from the current UI have exactly the same behavior.
 *
 * The old APK had three independent catalog lists:
 * CatalogExtract (highlight), CatalogIgnore (hide by word), and
 * CatalogRefuse (hide one exact thread). The current app used to collapse the
 * latter two into CATALOG_WORD/CATALOG_THREAD, which made old data appear in
 * the settings but not behave the same way.
 */
fun compatCatalogRulesForBoard(
    rules: List<CompatNgRule>,
    boardKey: String
): List<CompatNgRule> = rules.filter { it.scopeKey == boardKey || it.scopeKey == "*" }

/**
 * The first compatibility implementation used CATALOG_THREAD/CATALOG_WORD,
 * while both reference APKs persist the same concepts as
 * CatalogRefuse/CatalogIgnore.  Keep both aliases visible in the reference
 * management screens so rules created by an older Futacha build never become
 * effective-but-impossible-to-delete entries.
 */
fun compatCatalogManagementKinds(kind: CompatNgKind): Set<CompatNgKind> = when (kind) {
    CompatNgKind.CATALOG_REFUSE -> setOf(CompatNgKind.CATALOG_REFUSE, CompatNgKind.CATALOG_THREAD)
    CompatNgKind.CATALOG_IGNORE -> setOf(CompatNgKind.CATALOG_IGNORE, CompatNgKind.CATALOG_WORD)
    else -> setOf(kind)
}

fun compatCatalogManagementRules(
    rules: List<CompatNgRule>,
    boardKey: String,
    kind: CompatNgKind
): List<CompatNgRule> {
    val acceptedKinds = compatCatalogManagementKinds(kind)
    val applicable = rules.filter {
        it.kind in acceptedKinds && (it.scopeKey == boardKey || it.scopeKey == "*")
    }
    return when (kind) {
        // CatalogRefuseDao orders newest registrations first.
        CompatNgKind.CATALOG_REFUSE -> applicable.sortedByDescending(CompatNgRule::createdAtEpochMillis)
        // CatalogExtract/CatalogIgnore are ordered by their displayed word.
        CompatNgKind.CATALOG_EXTRACT,
        CompatNgKind.CATALOG_IGNORE -> applicable.sortedBy {
            normalizeCompatSearchText(compatCatalogManagementDisplayValue(it))
        }
        else -> applicable
    }
}

/** User-facing value stored separately from the normalized matching value. */
fun compatCatalogManagementDisplayValue(rule: CompatNgRule): String = when (rule.kind) {
    CompatNgKind.CATALOG_EXTRACT,
    CompatNgKind.CATALOG_IGNORE,
    CompatNgKind.CATALOG_WORD -> rule.memo.takeIf(String::isNotBlank) ?: rule.normalizedValue
    else -> rule.normalizedValue
}

/** CatalogRefuse rows are exactly the four-character title and URL on two lines. */
fun compatCatalogRefuseDisplayText(rule: CompatNgRule): String = buildString {
    rule.memo.takeIf(String::isNotBlank)?.let {
        append(it)
        append('\n')
    }
    append(rule.normalizedValue)
}

fun hasCompatCatalogManagementDuplicate(
    rules: List<CompatNgRule>,
    value: String,
    excludingRuleId: String? = null
): Boolean {
    val normalized = normalizeCompatSearchText(value)
    return normalized.isNotBlank() && rules.any {
        it.id != excludingRuleId && normalizeCompatSearchText(it.normalizedValue) == normalized
    }
}

/** Pre-normalized lookup used by large compatibility catalogs. */
data class CompatCatalogRuleIndex(
    val hiddenThreadValues: Set<String>,
    val hiddenWords: List<String>,
    val hiddenImageValues: Set<String>,
    val extractWords: List<String>
) {
    fun hides(item: CatalogItem): Boolean {
        val id = item.id.normalizeCompatCatalogValue()
        val threadUrl = item.threadUrl.normalizeCompatCatalogValue()
        if (id in hiddenThreadValues || threadUrl in hiddenThreadValues) return true
        val title = item.title.orEmpty().normalizeCompatCatalogValue()
        if (hiddenWords.any(title::contains)) return true
        return listOfNotNull(item.fullImageUrl, item.thumbnailUrl)
            .any { it.normalizeCompatCatalogValue() in hiddenImageValues }
    }

    fun extracts(item: CatalogItem): Boolean {
        val title = item.title.orEmpty().normalizeCompatCatalogValue()
        return extractWords.any(title::contains)
    }
}

fun buildCompatCatalogRuleIndex(rules: List<CompatNgRule>): CompatCatalogRuleIndex {
    fun values(vararg kinds: CompatNgKind): List<String> {
        val accepted = kinds.toSet()
        return rules.asSequence()
            .filter { it.kind in accepted }
            .map { it.normalizedValue.normalizeCompatCatalogValue() }
            .filter(String::isNotBlank)
            .distinct()
            .toList()
    }
    return CompatCatalogRuleIndex(
        hiddenThreadValues = values(CompatNgKind.CATALOG_THREAD, CompatNgKind.CATALOG_REFUSE).toSet(),
        hiddenWords = values(CompatNgKind.CATALOG_WORD, CompatNgKind.CATALOG_IGNORE),
        hiddenImageValues = values(CompatNgKind.CATALOG_IMAGE).toSet(),
        extractWords = values(CompatNgKind.CATALOG_EXTRACT)
    )
}

fun compatCatalogItemMatchesRule(item: CatalogItem, rule: CompatNgRule): Boolean {
    val value = normalizeCompatSearchText(rule.normalizedValue)
    if (value.isBlank()) return false
    return when (rule.kind) {
        CompatNgKind.CATALOG_THREAD,
        CompatNgKind.CATALOG_REFUSE -> listOf(item.id, item.threadUrl).any {
            it.normalizeCompatCatalogValue() == value
        }

        CompatNgKind.CATALOG_WORD,
        CompatNgKind.CATALOG_IGNORE,
        CompatNgKind.CATALOG_EXTRACT -> item.title.orEmpty()
            .normalizeCompatCatalogValue()
            .contains(value)

        CompatNgKind.CATALOG_IMAGE -> listOfNotNull(item.fullImageUrl, item.thumbnailUrl).any {
            it.normalizeCompatCatalogValue() == value
        }

        // pHash is resolved asynchronously by the UI and is intentionally not
        // treated as a string rule here.
        CompatNgKind.CATALOG_IMAGE_PHASH -> false
        else -> false
    }
}

fun compatCatalogItemIsExtracted(
    item: CatalogItem,
    rules: List<CompatNgRule>
): Boolean = rules.any {
    it.kind == CompatNgKind.CATALOG_EXTRACT && compatCatalogItemMatchesRule(item, it)
}

/** Returns the original words that actually matched this catalog item. */
fun compatCatalogMatchedWords(
    item: CatalogItem,
    watchWords: List<String>,
    rules: List<CompatNgRule>
): List<String> = buildList {
    val title = item.title.orEmpty().normalizeCompatCatalogValue()
    watchWords.map(String::trim).filter(String::isNotBlank).forEach { word ->
        if (title.contains(word.normalizeCompatCatalogValue())) add(word)
    }
    rules.asSequence()
        .filter { it.kind == CompatNgKind.CATALOG_EXTRACT }
        .map { it.normalizedValue.trim() }
        .filter(String::isNotBlank)
        .filter { title.contains(it.normalizeCompatCatalogValue()) }
        .forEach(::add)
}.distinctBy(String::normalizeCompatCatalogValue)

private fun String.normalizeCompatCatalogValue(): String = normalizeCompatSearchText(this)
