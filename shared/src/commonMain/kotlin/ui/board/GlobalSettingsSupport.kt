package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries
import kotlin.math.round

private const val GLOBAL_SETTINGS_HISTORY_WARNING_THRESHOLD = 50

internal data class PreferredFileManagerSummaryState(
    val currentSettingText: String,
    val isConfigured: Boolean
)

internal data class SaveDirectoryPickerState(
    val descriptionText: String,
    val warningText: String,
    val isPickerButtonEnabled: Boolean,
    val showManualInputFallbackButton: Boolean
)

internal data class GlobalSettingsBehaviorText(
    val backgroundRefreshDescription: String
)

internal data class GlobalSettingsSaveText(
    val sectionTitle: String,
    val sectionDescription: String,
    val preferredAppTitle: String,
    val preferredAppDescription: String,
    val preferredAppButtonLabel: String,
    val pickerSelectionLabel: String,
    val manualSaveDescription: String,
    val manualSaveInputLabel: String,
    val resetManualSaveButtonLabel: String,
    val updateManualSaveButtonLabel: String,
    val pickerButtonLabel: String,
    val pickerFallbackButtonLabel: String,
    val defaultWarningTitle: String?
)

internal data class GlobalSettingsEntrySelectionState(
    val shouldOpenCookieManager: Boolean,
    val externalUrl: String?,
    val shouldCloseScreen: Boolean
)

internal fun resolvePreferredFileManagerSummaryState(
    preferredFileManagerLabel: String?
): PreferredFileManagerSummaryState {
    val normalizedLabel = preferredFileManagerLabel?.trim().orEmpty()
    return if (normalizedLabel.isBlank()) {
        PreferredFileManagerSummaryState(
            currentSettingText = "未設定(システムのデフォルト)",
            isConfigured = false
        )
    } else {
        PreferredFileManagerSummaryState(
            currentSettingText = "現在の設定: $normalizedLabel",
            isConfigured = true
        )
    }
}

internal fun buildBackgroundRefreshDescription(isAndroidPlatform: Boolean): String {
    return if (isAndroidPlatform) {
        "最短15分間隔の定期更新を要求します。実際の実行時刻は端末状態や OS により前後します。"
    } else {
        "OS が許可したタイミングでバックグラウンド更新します。実際の実行時刻は端末状態や OS により前後します。"
    }
}

internal fun buildPreferredFileManagerDescription(isAndroidPlatform: Boolean): String {
    return if (isAndroidPlatform) {
        "ディレクトリ選択で使うファイラーアプリを指定できます。設定すると次回からそのアプリを直接起動します。"
    } else {
        "Files に表示される保存先アプリを優先できます。設定すると次回から同じ保存先を選びやすくなります。"
    }
}

internal fun resolveGlobalSettingsBehaviorText(
    isAndroidPlatform: Boolean
): GlobalSettingsBehaviorText {
    return GlobalSettingsBehaviorText(
        backgroundRefreshDescription = buildBackgroundRefreshDescription(isAndroidPlatform)
    )
}

internal fun resolveGlobalSettingsSaveText(
    isAndroidPlatform: Boolean
): GlobalSettingsSaveText {
    return if (isAndroidPlatform) {
        GlobalSettingsSaveText(
            sectionTitle = "保存とファイラー",
            sectionDescription = "保存先とディレクトリ選択まわりをまとめました。",
            preferredAppTitle = "優先ファイラー",
            preferredAppDescription = buildPreferredFileManagerDescription(true),
            preferredAppButtonLabel = "ファイラーを選択",
            pickerSelectionLabel = "ファイラーで選ぶ",
            manualSaveDescription = "手入力ではプラットフォーム既定の保存領域を使います。「Documents」や「Download」は端末ごとの既定フォルダ系統に解決されます。絶対パス指定もできます。",
            manualSaveInputLabel = "フォルダ名またはパス",
            resetManualSaveButtonLabel = "デフォルトに戻す",
            updateManualSaveButtonLabel = "保存先を更新",
            pickerButtonLabel = "フォルダを選択",
            pickerFallbackButtonLabel = "手入力に戻す",
            defaultWarningTitle = "Android では保存先の変更が必要です"
        )
    } else {
        GlobalSettingsSaveText(
            sectionTitle = "保存先",
            sectionDescription = "保存先と Files 連携まわりをまとめました。",
            preferredAppTitle = "優先保存先アプリ",
            preferredAppDescription = buildPreferredFileManagerDescription(false),
            preferredAppButtonLabel = "保存先アプリを選択",
            pickerSelectionLabel = "フォルダを選ぶ",
            manualSaveDescription = "手入力ではプラットフォーム既定の保存領域を使います。Files で扱いやすい場所を指定するか、必要に応じて絶対パスを入力できます。",
            manualSaveInputLabel = "フォルダ名またはパス",
            resetManualSaveButtonLabel = "デフォルトに戻す",
            updateManualSaveButtonLabel = "保存先を更新",
            pickerButtonLabel = "フォルダを選択",
            pickerFallbackButtonLabel = "手入力に戻す",
            defaultWarningTitle = null
        )
    }
}

internal fun resolveSaveDirectoryPickerState(
    isAndroidPlatform: Boolean,
    hasPickerLauncher: Boolean
): SaveDirectoryPickerState {
    return SaveDirectoryPickerState(
        descriptionText = if (isAndroidPlatform) {
            "AndroidではSAFで選ぶ方法を推奨します。手入力も利用できます。"
        } else {
            "選択したフォルダを保存先に使います。取得できない場合は手入力に切り替えてください。"
        },
        warningText = if (isAndroidPlatform) {
            "※ SAF のフォルダー選択 (OPEN_DOCUMENT_TREE) に非対応のファイラーでは選択できません。その場合は標準ファイラーを使うか手入力を選んでください。"
        } else {
            "※ 保存先アプリによってはフォルダ指定に制限があります。その場合は Files か手入力を利用してください。"
        },
        isPickerButtonEnabled = hasPickerLauncher,
        showManualInputFallbackButton = !isAndroidPlatform
    )
}

internal fun shouldShowCookieSettingsEntry(
    hasCookieManager: Boolean
): Boolean = hasCookieManager

internal fun resolveGlobalSettingsEntrySelection(
    action: GlobalSettingsAction
): GlobalSettingsEntrySelectionState {
    return when (action) {
        GlobalSettingsAction.Cookies -> GlobalSettingsEntrySelectionState(
            shouldOpenCookieManager = true,
            externalUrl = null,
            shouldCloseScreen = true
        )
        GlobalSettingsAction.Email,
        GlobalSettingsAction.X,
        GlobalSettingsAction.Developer,
        GlobalSettingsAction.PrivacyPolicy -> GlobalSettingsEntrySelectionState(
            shouldOpenCookieManager = false,
            externalUrl = resolveGlobalSettingsActionTarget(action),
            shouldCloseScreen = true
        )
    }
}

internal data class GlobalSettingsStorageSummaryState(
    val historyText: String,
    val autoSavedText: String,
    val isHistoryWarning: Boolean,
    val warningText: String?
)

internal fun formatGlobalSettingsSizeMb(bytes: Long?): String {
    if (bytes == null) return "不明"
    val mbTimesTen = (bytes / (1024.0 * 1024.0)) * 10
    val rounded = round(mbTimesTen) / 10.0
    return "${rounded} MB"
}

internal fun resolveGlobalSettingsStorageSummaryState(
    historyCount: Int,
    autoSavedCount: Int?,
    autoSavedSize: Long?
): GlobalSettingsStorageSummaryState {
    val isWarning = historyCount >= GLOBAL_SETTINGS_HISTORY_WARNING_THRESHOLD
    return GlobalSettingsStorageSummaryState(
        historyText = "履歴: ${historyCount}件",
        autoSavedText = "自動保存: ${autoSavedCount ?: 0}件 / ${formatGlobalSettingsSizeMb(autoSavedSize)}",
        isHistoryWarning = isWarning,
        warningText = if (isWarning) {
            "※件数が多いと更新に時間がかかることがあります。不要な履歴は既存の削除・クリア操作をご利用ください。"
        } else {
            null
        }
    )
}

internal enum class GlobalSettingsCacheCleanupTarget {
    IMAGE_CACHE,
    TEMPORARY_CACHE
}

internal fun buildGlobalSettingsCacheCleanupMessage(
    target: GlobalSettingsCacheCleanupTarget,
    result: Result<Unit>
): String {
    return if (result.isSuccess) {
        when (target) {
            GlobalSettingsCacheCleanupTarget.IMAGE_CACHE -> "画像キャッシュを削除しました"
            GlobalSettingsCacheCleanupTarget.TEMPORARY_CACHE -> "一時キャッシュを削除しました"
        }
    } else {
        val reason = result.exceptionOrNull()?.message ?: "不明なエラー"
        "削除に失敗しました: $reason"
    }
}

internal data class CatalogMenuConfigState(
    val barEntries: List<CatalogNavEntryConfig>,
    val hiddenEntries: List<CatalogNavEntryConfig>,
    val allEntries: List<CatalogNavEntryConfig>
)

private fun <T> reorderEntriesWithinPlacement(
    entries: List<T>,
    targetEntry: T,
    delta: Int,
    sameEntry: (T, T) -> Boolean,
    copyWithOrder: (T, Int) -> T
): List<T> {
    val sorted = entries.toMutableList()
    val index = sorted.indexOfFirst { sameEntry(it, targetEntry) }
    if (index == -1) return entries
    val target = (index + delta).coerceIn(0, sorted.lastIndex)
    if (target == index) return entries
    val item = sorted.removeAt(index)
    sorted.add(target, item)

    val merged = entries.toMutableList()
    sorted.forEachIndexed { order, config ->
        val originIndex = merged.indexOfFirst { sameEntry(it, config) }
        if (originIndex >= 0) {
            merged[originIndex] = copyWithOrder(config, order)
        }
    }
    return merged
}

private fun <T> setEntryPlacement(
    entries: List<T>,
    predicate: (T) -> Boolean,
    transform: (T) -> T
): List<T> {
    return entries.map { entry -> if (predicate(entry)) transform(entry) else entry }
}

internal fun resolveCatalogMenuConfigState(
    entries: List<CatalogNavEntryConfig>
): CatalogMenuConfigState {
    val normalized = normalizeCatalogNavEntries(entries)
    return CatalogMenuConfigState(
        barEntries = normalized.filter { it.placement == CatalogNavEntryPlacement.BAR }.sortedBy { it.order },
        hiddenEntries = normalized.filter { it.placement == CatalogNavEntryPlacement.HIDDEN }.sortedBy { it.order },
        allEntries = normalized.sortedBy { it.id.name }
    )
}

internal fun moveCatalogMenuEntry(
    entries: List<CatalogNavEntryConfig>,
    id: CatalogNavEntryId,
    delta: Int
): List<CatalogNavEntryConfig> {
    val normalized = normalizeCatalogNavEntries(entries)
    val barEntries = normalized
        .filter { it.placement == CatalogNavEntryPlacement.BAR }
        .sortedBy { it.order }
    val targetEntry = barEntries.firstOrNull { it.id == id } ?: return normalized
    val reordered = reorderEntriesWithinPlacement(
        entries = barEntries,
        targetEntry = targetEntry,
        delta = delta,
        sameEntry = { left, right -> left.id == right.id },
        copyWithOrder = { entry, order -> entry.copy(order = order) }
    )
    val merged = normalized.map { existing ->
        reordered.firstOrNull { it.id == existing.id } ?: existing
    }
    return normalizeCatalogNavEntries(merged)
}

internal fun setCatalogMenuEntryPlacement(
    entries: List<CatalogNavEntryConfig>,
    id: CatalogNavEntryId,
    placement: CatalogNavEntryPlacement
): List<CatalogNavEntryConfig> {
    val normalized = normalizeCatalogNavEntries(entries)
    return normalizeCatalogNavEntries(
        setEntryPlacement(
            entries = normalized,
            predicate = { it.id == id },
            transform = { it.copy(placement = placement) }
        )
    )
}

internal data class ThreadMenuConfigState(
    val barEntries: List<ThreadMenuEntryConfig>,
    val sheetEntries: List<ThreadMenuEntryConfig>,
    val hiddenEntries: List<ThreadMenuEntryConfig>,
    val allEntries: List<ThreadMenuEntryConfig>
)

internal fun resolveThreadMenuConfigState(
    entries: List<ThreadMenuEntryConfig>
): ThreadMenuConfigState {
    val normalized = normalizeThreadMenuEntries(entries)
    return ThreadMenuConfigState(
        barEntries = normalized.filter { it.placement == ThreadMenuEntryPlacement.BAR }.sortedBy { it.order },
        sheetEntries = normalized.filter { it.placement == ThreadMenuEntryPlacement.SHEET }.sortedBy { it.order },
        hiddenEntries = normalized.filter { it.placement == ThreadMenuEntryPlacement.HIDDEN }.sortedBy { it.order },
        allEntries = normalized.sortedBy { it.id.name }
    )
}

internal fun moveThreadMenuEntryWithinPlacement(
    entries: List<ThreadMenuEntryConfig>,
    id: ThreadMenuEntryId,
    delta: Int,
    placement: ThreadMenuEntryPlacement
): List<ThreadMenuEntryConfig> {
    val normalized = normalizeThreadMenuEntries(entries)
    val placedEntries = normalized
        .filter { it.placement == placement }
        .sortedBy { it.order }
    val targetEntry = placedEntries.firstOrNull { it.id == id } ?: return normalized
    val reordered = reorderEntriesWithinPlacement(
        entries = placedEntries,
        targetEntry = targetEntry,
        delta = delta,
        sameEntry = { left, right -> left.id == right.id },
        copyWithOrder = { entry, order -> entry.copy(order = order) }
    )
    val merged = normalized.map { existing ->
        reordered.firstOrNull { it.id == existing.id } ?: existing
    }
    return normalizeThreadMenuEntries(merged)
}

internal fun setThreadMenuEntryPlacement(
    entries: List<ThreadMenuEntryConfig>,
    id: ThreadMenuEntryId,
    placement: ThreadMenuEntryPlacement
): List<ThreadMenuEntryConfig> {
    val normalized = normalizeThreadMenuEntries(entries)
    return normalizeThreadMenuEntries(
        setEntryPlacement(
            entries = normalized,
            predicate = { it.id == id },
            transform = { it.copy(placement = placement) }
        )
    )
}
