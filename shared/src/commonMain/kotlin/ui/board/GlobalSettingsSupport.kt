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

internal fun resolveSaveDirectoryPickerState(
    isAndroidPlatform: Boolean,
    hasPickerLauncher: Boolean
): SaveDirectoryPickerState {
    return SaveDirectoryPickerState(
        descriptionText = if (isAndroidPlatform) {
            "AndroidではSAFで選ぶ方法を推奨します。手入力も利用できます。"
        } else {
            "ファイラーで選んだディレクトリを保存先に使います。パスが取得できない場合は手入力に切り替えてください。"
        },
        warningText = "※ SAF のフォルダー選択 (OPEN_DOCUMENT_TREE) に非対応のファイラーでは選択できません。その場合は標準ファイラーを使うか手入力を選んでください。",
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
    val sorted = normalized
        .filter { it.placement == CatalogNavEntryPlacement.BAR }
        .sortedBy { it.order }
        .toMutableList()
    val index = sorted.indexOfFirst { it.id == id }
    if (index == -1) return normalized
    val target = (index + delta).coerceIn(0, sorted.lastIndex)
    if (target == index) return normalized
    val item = sorted.removeAt(index)
    sorted.add(target, item)
    val merged = normalized.toMutableList()
    sorted.forEachIndexed { order, config ->
        val originIndex = merged.indexOfFirst { it.id == config.id }
        if (originIndex >= 0) {
            merged[originIndex] = config.copy(order = order)
        }
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
        normalized.map { if (it.id == id) it.copy(placement = placement) else it }
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
    val sorted = normalized
        .filter { it.placement == placement }
        .sortedBy { it.order }
        .toMutableList()
    val index = sorted.indexOfFirst { it.id == id }
    if (index == -1) return normalized
    val target = (index + delta).coerceIn(0, sorted.lastIndex)
    if (target == index) return normalized
    val item = sorted.removeAt(index)
    sorted.add(target, item)
    val merged = normalized.toMutableList()
    sorted.forEachIndexed { order, config ->
        val originIndex = merged.indexOfFirst { it.id == config.id }
        if (originIndex >= 0) {
            merged[originIndex] = config.copy(order = order)
        }
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
        normalized.map { if (it.id == id) it.copy(placement = placement) else it }
    )
}
