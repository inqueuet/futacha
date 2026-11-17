package com.valoser.futacha.shared.ui.ftutter

/** Unlike editing a draft, importing must never evict or shorten an existing one. */
internal data class FtutterDraftImport(
    val encoded: String,
    val added: Int,
    val rejected: Int
) {
    val summary: String get() = "下書き ${added}件" + if (rejected == 0) "" else
        "、下書き ${rejected}件は取り込みません（今の下書きと重複、または保存上限超過）"
    val notice: String? get() = if (rejected == 0) null else
        "下書き ${rejected}件は取り込みませんでした（今の下書きと重複、または保存上限超過）。今の下書きは残しました"
}

/** Existing order and values win; a rejected large candidate does not prevent later small ones. */
internal fun ftutterMergeImportedDrafts(stored: String?, imported: Map<String, FtutterDraft>): FtutterDraftImport {
    val merged = LinkedHashMap(parseFtutterDrafts(stored))
    var added = 0
    var rejected = 0
    imported.forEach { (key, draft) ->
        if (draft.isEmpty) return@forEach
        val existing = merged[key]
        if (existing != null) {
            if (existing != draft) rejected++
            return@forEach
        }
        // Check individual field bounds without accepting the editing path's truncation.
        if (draft.boundedForStore() != draft || merged.size >= FTUTTER_DRAFT_LIMIT) {
            rejected++
            return@forEach
        }
        val candidate = LinkedHashMap(merged).apply { put(key, draft) }
        if (encodeFtutterDraftsForStore(candidate).length > FTUTTER_DRAFT_STORE_MAX_CHARS) rejected++
        else { merged[key] = draft; added++ }
    }
    return FtutterDraftImport(encodeFtutterDraftsForStore(merged), added, rejected)
}
