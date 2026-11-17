package com.valoser.futacha.shared.compat

/**
 * Search over the rules shown in the NG management dialog.
 *
 * The straightforward filter re-normalizes four fields of every rule for every typed
 * character (5000 rules: 20000 normalizations per key stroke on the main thread). The
 * normalized fields depend only on the rules, so they are derived once, on first use,
 * and each query only runs `contains` over them. The result is exactly that of
 * [filterCompatNgRulesBySearch].
 */
class CompatNgRuleSearchIndex(
    private val rules: List<CompatNgRule>,
    private val isImageReference: Boolean,
    private val isThreadWordReference: Boolean
) {
    private val haystacks: List<List<String>> by lazy(LazyThreadSafetyMode.NONE) {
        rules.map { rule ->
            if (isImageReference) {
                listOf(
                    normalizeCompatSearchText(compatImageNgDisplayTitle(rule)),
                    normalizeCompatSearchText(compatImageNgFirstUrl(rule))
                )
            } else {
                listOf(
                    normalizeCompatSearchText(rule.normalizedValue),
                    normalizeCompatSearchText(rule.memo),
                    normalizeCompatSearchText(
                        if (isThreadWordReference) compatThreadReferenceDisplayValue(rule)
                        else rule.normalizedValue
                    ),
                    normalizeCompatSearchText(rule.imageUrl.orEmpty())
                )
            }
        }
    }

    fun filter(searchQuery: String): List<CompatNgRule> {
        val query = normalizeCompatSearchText(searchQuery)
        if (query.isBlank()) return rules
        return rules.filterIndexed { index, _ -> haystacks[index].any { it.contains(query) } }
    }
}

/** The un-indexed reference implementation of [CompatNgRuleSearchIndex.filter]. */
internal fun filterCompatNgRulesBySearch(
    rules: List<CompatNgRule>,
    searchQuery: String,
    isImageReference: Boolean,
    isThreadWordReference: Boolean
): List<CompatNgRule> {
    val query = normalizeCompatSearchText(searchQuery)
    return if (isImageReference) {
        rules.filter { rule -> compatImageNgMatchesSearch(rule, searchQuery) }
    } else if (query.isBlank()) rules else rules.filter { rule ->
        normalizeCompatSearchText(rule.normalizedValue).contains(query) ||
            normalizeCompatSearchText(rule.memo).contains(query) ||
            normalizeCompatSearchText(
                if (isThreadWordReference) compatThreadReferenceDisplayValue(rule)
                else rule.normalizedValue
            ).contains(query) ||
            normalizeCompatSearchText(rule.imageUrl.orEmpty()).contains(query)
    }
}
