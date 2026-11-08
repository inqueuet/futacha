package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.shared.SharedKeys
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first

/**
 * D-B6: the reply drafts of this mode, in the shared store's terms, so the other modes' write screens can show them.
 * Only replies of official boards (the shared store has no place for the others); a draft with no text and no file is
 * not shared. The attachment locator is this mode's own private file, so it is not passed on.
 */
internal fun futaberSharedDraftsPlan(drafts: List<FutaberDraft>, boards: List<BoardSummary>): Map<String, CompatReplyDraft> {
    val result = LinkedHashMap<String, CompatReplyDraft>()
    drafts.forEach { draft ->
        if (draft.isEmpty || draft.comment.isBlank() && draft.subject.isBlank()) return@forEach
        val parts = draft.key.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != "r") return@forEach
        val board = SharedKeys.boardOfFutaberStoredId(parts[1], boards)?.takeIf { futaberNgScopeKey(it) != null } ?: return@forEach
        val tabKey = futaberCompatTabKey(board, parts[2])
        val current = result[tabKey]
        if (current != null && current.updatedAtEpochMillis >= draft.updatedAt) return@forEach
        result[tabKey] = CompatReplyDraft(tabKey = tabKey, subject = draft.subject, comment = draft.comment, updatedAtEpochMillis = draft.updatedAt)
    }
    return result
}

/**
 * Writes [plan] for the threads whose shared tab exists (a draft hangs on its tab) and takes off the drafts this mode
 * shared before ([previous]) that are no longer there. A draft the other modes wrote in the meantime is left alone: it is
 * replaced only while it is older than this one. Returns the tab keys now shared.
 */
internal suspend fun futaberApplySharedDrafts(
    store: CompatibilityStore,
    plan: Map<String, CompatReplyDraft>,
    previous: Set<String>
): Set<String> {
    val tabKeys = store.tabs.first().mapTo(HashSet()) { it.key }
    val shared = LinkedHashSet<String>()
    plan.forEach { (tabKey, draft) ->
        if (tabKey !in tabKeys) return@forEach
        val existing = store.loadDraft(tabKey)
        val same = existing != null && existing.comment == draft.comment && existing.subject == draft.subject
        if (existing == null || existing.updatedAtEpochMillis <= draft.updatedAtEpochMillis && !same) store.saveDraft(draft)
        // Only a draft that is ours is remembered, so a newer one another mode wrote is never taken off later.
        if (same || existing == null || existing.updatedAtEpochMillis <= draft.updatedAtEpochMillis) shared += tabKey
    }
    (previous - plan.keys).forEach { key -> if (key in tabKeys) store.deleteDraft(key) }
    return shared
}

/**
 * D-B6 (J5): the text to start a reply from when another mode left a draft for the thread and this mode has none of its
 * own (and nothing typed yet). Null when there is nothing to take over. A draft this mode itself shared has the same
 * text as its own, so it never gets here.
 */
internal fun futaberSharedDraftToAdopt(
    own: FutaberDraft?,
    shared: CompatReplyDraft?,
    currentSubject: String,
    currentComment: String
): FutaberPostStart? {
    if (shared == null || own?.isEmpty == false) return null
    if (currentSubject.isNotBlank() || currentComment.isNotBlank()) return null
    if (shared.comment.isBlank() && shared.subject.isBlank()) return null
    return FutaberPostStart(shared.subject, shared.comment)
}
