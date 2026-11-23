package com.valoser.futacha.shared.ui.board

/** The outcome of "題名からNGワードを登録" (may 1449174385). */
internal sealed interface FutachaTitleNgResult {
    data class Added(val words: List<String>) : FutachaTitleNgResult
    data class Rejected(val reason: String) : FutachaTitleNgResult
}

/** Adds the trimmed [word] to the catalog NG words, refusing an empty or an already registered one. */
internal fun futachaAddCatalogNgWord(existing: List<String>, word: String): FutachaTitleNgResult {
    val trimmed = word.trim()
    if (trimmed.isEmpty()) return FutachaTitleNgResult.Rejected("NGワードが空です。題名から隠したい語句を残してください。")
    if (existing.any { it == trimmed }) return FutachaTitleNgResult.Rejected("「$trimmed」はすでに登録されています。")
    return FutachaTitleNgResult.Added(existing + trimmed)
}
