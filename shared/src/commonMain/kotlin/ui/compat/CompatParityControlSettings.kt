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

internal val COMPAT_PARITY_CONTROL_BOOLEAN_KEYS = setOf(
    COMPAT_HISTORY_TOOLS_PREFERENCE_KEY,
    COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY,
    FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY
)

internal fun futachaDraftPersistEntry(): CompatSettingEntry =
    CompatSettingEntry("下書きを端末に保存", "OFF", preferenceKey = FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY)

internal fun compatParityControlEntries(): List<CompatSettingEntry> = listOf(
    CompatSettingEntry("履歴の絞り込み・並べ替え・印", "OFF", preferenceKey = COMPAT_HISTORY_TOOLS_PREFERENCE_KEY),
    CompatSettingEntry("共通NGをこのモードでも適用", "OFF", preferenceKey = COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY)
)

internal fun compatParityControlSummary(preferenceKey: String): String? = when (preferenceKey) {
    COMPAT_HISTORY_TOOLS_PREFERENCE_KEY ->
        "履歴の長押しに絞り込み・並べ替え、エクスポート／インポート、一括更新を足し、行に印（落ちた・書き込み済み・保存済み）を出します"
    COMPAT_COMMON_NG_APPLY_PREFERENCE_KEY ->
        "ONにすると、ふたちゃ・ふたばー風で登録したNGワードでもレスやスレが隠れます"
    FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY ->
        "返信・スレ立ての本文と添付を端末に残し、スレッドやカタログを離れても戻します。送信に成功すると消えます。" +
            "同じスレッド・板の下書きは、としあき(仮)の書き込み画面にも出ます"
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
            if (group == "ふたちゃ拡張") group to (entries + futachaDraftPersistEntry()) else group to entries
        }
    }
    if (path != "control" || !includeAppStateEntries || modernPresentation) return groups
    return groups.map { (group, entries) ->
        if (group == "ふたちゃ拡張") group to (entries + compatParityControlEntries()) else group to entries
    }
}

internal fun futachaDraftPersistEnabled(preferences: Map<String, String>): Boolean =
    preferences[FUTACHA_DRAFT_PERSIST_STORAGE_KEY] == "ON"

internal fun compatCommonNgApplyEnabled(preferences: Map<String, String>): Boolean =
    preferences[COMPAT_COMMON_NG_APPLY_STORAGE_KEY] == "ON"
