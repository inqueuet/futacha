package com.valoser.futacha.shared.ui.compat

/*
 * Two switches of としあき(仮)'s control page (feature-parity batches 4 and 7), appended to its "ふたちゃ拡張" group
 * only where the shared AppStateStore exists (both act on AppState data) and never in ふたちゃ's own presentation of
 * these pages. Both are off by default; the keys are "compat.control.*", which load before "compat.ownpost.*".
 */

internal const val COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY = "controlCommonNgApply"
internal const val COMPAT_COMMON_NG_APPLY_STORAGE_KEY = "compat.control.$COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY"

/**
 * ふたちゃ's "下書きを端末に保存" (feature-parity batch 4, approved: a switch, off by default). Only ふたちゃ's own
 * presentation of the control page shows it: としあき(仮) always keeps its drafts, so the row would mean nothing there.
 */
internal const val FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY = "controlFutachaDraftPersist"
internal const val FUTACHA_DRAFT_PERSIST_STORAGE_KEY = "compat.control.$FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY"

/** Review 2026-11-11 (としあき(仮)): optional links between closing a tab and deleting its history, off by default. */
internal const val COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY = "controlTabCloseDeletesHistory"
internal const val COMPAT_TAB_CLOSE_DELETES_HISTORY_STORAGE_KEY = "compat.control.$COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY"
internal const val COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY = "controlHistoryDeleteClosesTab"
internal const val COMPAT_HISTORY_DELETE_CLOSES_TAB_STORAGE_KEY = "compat.control.$COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY"

internal fun compatTabCloseDeletesHistory(preferences: Map<String, String>): Boolean =
    preferences[COMPAT_TAB_CLOSE_DELETES_HISTORY_STORAGE_KEY] == "ON"

internal fun compatHistoryDeleteClosesTab(preferences: Map<String, String>): Boolean =
    preferences[COMPAT_HISTORY_DELETE_CLOSES_TAB_STORAGE_KEY] == "ON"

/** The history rows of the closed tabs' threads. */
internal fun compatHistoryRowsForClosedTabs(
    closedTabUrls: Set<String>,
    history: List<com.valoser.futacha.shared.compat.CompatHistoryEntry>
): List<com.valoser.futacha.shared.compat.CompatHistoryEntry> = history.filter { it.canonicalUrl in closedTabUrls }

/** The open tabs of the deleted history row's thread. */
internal fun compatTabKeysForDeletedHistory(
    canonicalUrl: String,
    tabs: List<com.valoser.futacha.shared.compat.CompatTab>
): Set<String> = tabs.filter { it.canonicalUrl == canonicalUrl }.map { it.key }.toSet()

internal val COMPAT_PARITY_CONTROL_BOOLEAN_KEYS = setOf(
    COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY,
    COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY,
    COMPAT_HISTORY_TOOLS_PREFERENCE_KEY,
    COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY,
    FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY,
    FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY,
    FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY
)

internal const val FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY = "controlFutachaSwipeToCatalog"

internal fun futachaSwipeToCatalogEntry(): CompatSettingEntry =
    CompatSettingEntry("スレの右スワイプでカタログへ戻る", "ON", preferenceKey = FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY)

internal const val FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY = "controlFutachaFlatMediaSave"

internal fun futachaFlatMediaSaveEntry(): CompatSettingEntry =
    CompatSettingEntry("画像をスレごとのフォルダに分けずに保存", "ON", preferenceKey = FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY)

internal fun futachaDraftPersistEntry(): CompatSettingEntry =
    CompatSettingEntry("下書きを端末に保存", "OFF", preferenceKey = FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY)

internal fun compatParityControlEntries(): List<CompatSettingEntry> = listOf(
    CompatSettingEntry("履歴の絞り込み・並べ替え・印", "OFF", preferenceKey = COMPAT_HISTORY_TOOLS_PREFERENCE_KEY),
    CompatSettingEntry("タブを閉じたら履歴も消す", "OFF", preferenceKey = COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY),
    CompatSettingEntry("履歴を消したらタブも閉じる", "OFF", preferenceKey = COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY),
    CompatSettingEntry("共通NGをこのモードでも適用", "ON", preferenceKey = COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY)
)

internal fun compatParityControlSummary(preferenceKey: String): String? = when (preferenceKey) {
    COMPAT_HISTORY_TOOLS_PREFERENCE_KEY ->
        "履歴の長押しに絞り込み・並べ替え、エクスポート／インポート、一括更新を足し、行に印（落ちた・書き込み済み・保存済み）を出します"
    COMPAT_TAB_CLOSE_DELETES_HISTORY_PREFERENCE_KEY ->
        "スレッドのタブを閉じると、そのスレッドの履歴も削除します（他のモードの履歴からも消えます）。タブを元に戻しても履歴は戻りません"
    COMPAT_HISTORY_DELETE_CLOSES_TAB_PREFERENCE_KEY ->
        "履歴を1件削除すると、そのスレッドの開いているタブも閉じます（他のモードのタブも閉じます）"
    COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY ->
        "ふたちゃ・ふたばー風で登録したNGワードでもレスやスレを隠します。OFFにすると、このモードでは使いません"
    FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY ->
        "返信・スレ立ての本文と添付を端末に残し、スレッドやカタログを離れても戻します。送信に成功すると消えます。" +
            "同じスレッド・板の下書きは、としあき(仮)の書き込み画面にも出ます"
    FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY ->
        "スレの中央から右へスワイプするとカタログへ戻ります。左端から右へは今までどおり履歴を開きます"
    FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY ->
        "画像・動画を1枚ずつ保存する時、スレッドごとのフォルダを作らず保存先に直接置きます（他のモードと同じ）。OFFにすると従来どおりスレッドのフォルダに分けます"
    else -> null
}

/**
 * [compatSettingsGroups] with the two switches at the end of the control page's "ふたちゃ拡張" group when [includeAppStateEntries]
 * (としあき(仮)), or with ふたちゃ's draft switch there in the modern presentation.
 */
internal fun compatSettingsGroups(
    path: String,
    modernPresentation: Boolean,
    includeAppStateEntries: Boolean
): List<Pair<String, List<CompatSettingEntry>>> {
    val groups = compatSettingsGroups(path, modernPresentation)
    if (path == "control" && modernPresentation) {
        // ふたちゃ's presentation: its own draft switch at the end of the "ふたちゃ拡張" group.
        return groups.map { (group, entries) ->
            if (group == "ふたちゃ拡張") group to (entries + futachaDraftPersistEntry() + futachaFlatMediaSaveEntry() + futachaSwipeToCatalogEntry()) else group to entries
        }
    }
    if (path != "control" || !includeAppStateEntries || modernPresentation) return groups
    return groups.map { (group, entries) ->
        if (group == "ふたちゃ拡張") group to (entries + compatParityControlEntries()) else group to entries
    }
}

internal fun futachaDraftPersistEnabled(preferences: Map<String, String>): Boolean =
    preferences[FUTACHA_DRAFT_PERSIST_STORAGE_KEY] == "ON"

/** D-B7 (J4): on until switched off, so the NG entered in any mode shows in the others (an explicit OFF is kept). */
internal fun compatCommonNgApplyEnabled(preferences: Map<String, String>): Boolean =
    preferences[COMPAT_COMMON_NG_APPLY_STORAGE_KEY] != "OFF"
