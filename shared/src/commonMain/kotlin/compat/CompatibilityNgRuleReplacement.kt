package com.valoser.futacha.shared.compat

/** Replacement keeps the number of rules constant, including at the registration limit. */
internal fun planCompatNgRuleReplacement(
    current: List<CompatNgRule>, previousRuleId: String, rule: CompatNgRule,
    boardKeys: Set<String>, tabKeys: Set<String>
): List<CompatNgRule>? {
    if (!isValidCompatNgRule(rule) || !isCompatNgScopeValid(rule.kind, rule.scopeKey, boardKeys, tabKeys)) return null
    val index = current.indexOfFirst { it.id == previousRuleId }
    if (index < 0 || current.any { it.id == rule.id && it.id != previousRuleId }) return null
    return current.toMutableList().apply { this[index] = rule }
}
