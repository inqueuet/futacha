package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatImageNgDisplayTitle
import com.valoser.futacha.shared.compat.compatThreadReferenceDisplayValue
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry

/**
 * The thread rules ([futaberNgPrepare] applies them to every thread they are scoped to) that no other list of the
 * settings shows: post number, poster id, words (body and header, body only, header only) and the address of a
 * picture. They were made in ふたちゃ or としあき(仮) (or imported from the old app) and hid posts here without a way to
 * see or take them back. The look-alike picture rules ([CompatNgKind.THREAD_IMAGE_PHASH]) have the "NG画像" list.
 */
internal val FUTABER_THREAD_DETAIL_NG_KINDS: Set<CompatNgKind> = setOf(
    CompatNgKind.THREAD_POST_NO,
    CompatNgKind.THREAD_POSTER_ID,
    CompatNgKind.THREAD_WORD,
    CompatNgKind.THREAD_IGNORE,
    CompatNgKind.THREAD_REFUSE,
    CompatNgKind.THREAD_IMAGE
)

/** The rules of [FUTABER_THREAD_DETAIL_NG_KINDS], the newest first. */
internal fun futaberThreadDetailNgRules(rules: List<CompatNgRule>): List<CompatNgRule> =
    rules.filter { it.kind in FUTABER_THREAD_DETAIL_NG_KINDS }.sortedByDescending { it.createdAtEpochMillis }

/** What the rule hides by, in the words of the lists of the other modes. */
internal fun futaberThreadDetailNgKindLabel(kind: CompatNgKind): String = when (kind) {
    CompatNgKind.THREAD_POST_NO -> "レス番号"
    CompatNgKind.THREAD_POSTER_ID -> "ID"
    CompatNgKind.THREAD_WORD -> "語句（本文・ヘッダー）"
    CompatNgKind.THREAD_IGNORE -> "語句（本文）"
    CompatNgKind.THREAD_REFUSE -> "語句（ヘッダー）"
    CompatNgKind.THREAD_IMAGE -> "画像のアドレス"
    else -> kind.name
}

/** What a rule's scope key stands for: a thread (its title) or a board (its name), keyed as the shared rules are. */
internal fun futaberThreadNgScopeTitles(
    history: List<ThreadHistoryEntry>,
    boards: List<BoardSummary>,
    /** Only these keys are needed; the history is not looked at further once all are found (L110). Null: every key. */
    wanted: Set<String>? = null
): Map<String, String> {
    val titles = HashMap<String, String>()
    val boardsById = boards.associateBy { it.id }
    boards.forEach { board -> futaberNgScopeKey(board)?.let { if (it !in titles) titles[it] = board.name } }
    fun allFound() = wanted != null && wanted.all { it in titles }
    if (allFound()) return titles
    // Each board's canonical address is worked out once, not once per history row.
    val threadUrlPrefix = HashMap<String, String?>()
    for (entry in history) {
        val board = boardsById[entry.boardId] ?: continue
        val prefix = threadUrlPrefix.getOrPut(board.id) { canonicalizeBoardUrl(board.url)?.let { "${it}res/" } }
        val threadUrl = prefix?.let { "$it${entry.threadId}.htm" } ?: futaberHistoryThreadUrl(board, entry.threadId)
        // Rows that key to the same thread carry the same title; the first one is used.
        val threadKey = com.valoser.futacha.shared.compat.compatTabKey(threadUrl)
        if (threadKey !in titles) {
            titles[threadKey] = entry.title.ifBlank { "(無題)" }
            if (wanted != null && threadKey in wanted && allFound()) break
        }
    }
    return titles
}

/** Where a rule applies: every thread, the thread or board [scopeTitles] knows by its key, or "スレ別" for a thread it does not know. */
internal fun futaberThreadDetailNgScopeLabel(rule: CompatNgRule, scopeTitles: Map<String, String>): String = when {
    rule.scopeKey == "*" -> "全スレッド"
    else -> (scopeTitles[rule.scopeKey] ?: com.valoser.futacha.shared.compat.canonicalizeThreadUrl(rule.scopeKey)?.let {
        scopeTitles[com.valoser.futacha.shared.compat.compatTabKey(it.canonicalUrl)] ?: it.canonicalUrl
    })?.takeIf { it.isNotBlank() } ?: "スレ別"
}

/**
 * One line of the list: the kind, the value, where it applies and the note. The note is left out when it is what the
 * value already shows (a word rule keeps the word as typed in its note).
 */
internal fun futaberThreadDetailNgRuleLabel(rule: CompatNgRule, scopeTitles: Map<String, String>): String {
    val value = if (rule.kind == CompatNgKind.THREAD_IMAGE) compatImageNgDisplayTitle(rule).trim()
    else compatThreadReferenceDisplayValue(rule).trim()
    val memo = rule.memo.trim().takeIf { it.isNotEmpty() && it != value }
    return listOfNotNull(
        futaberThreadDetailNgKindLabel(rule.kind),
        value.takeIf { it.isNotEmpty() },
        futaberThreadDetailNgScopeLabel(rule, scopeTitles),
        memo
    ).joinToString("　")
}
