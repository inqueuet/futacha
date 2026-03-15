package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WatchLater
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.BoardUrlResolver
import kotlinx.coroutines.Job

internal sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val items: List<CatalogItem>) : CatalogUiState
    data class Error(val message: String = "カタログを読み込めませんでした") : CatalogUiState
}

internal sealed interface ArchiveSearchState {
    data object Idle : ArchiveSearchState
    data object Loading : ArchiveSearchState
    data class Success(val items: List<ArchiveSearchItem>) : ArchiveSearchState
    data class Error(val message: String) : ArchiveSearchState
}

internal const val DEFAULT_CATALOG_GRID_COLUMNS = 5
internal const val MIN_CATALOG_GRID_COLUMNS = 2
internal const val MAX_CATALOG_GRID_COLUMNS = 8

internal fun nextCatalogRequestGeneration(currentGeneration: Long): Long {
    return currentGeneration + 1L
}

internal fun shouldApplyCatalogRequestResult(
    isActive: Boolean,
    currentGeneration: Long,
    requestGeneration: Long
): Boolean {
    return isActive && currentGeneration == requestGeneration
}

internal fun shouldFinalizeCatalogRefresh(
    isSameRunningJob: Boolean,
    currentGeneration: Long,
    requestGeneration: Long
): Boolean {
    return isSameRunningJob && currentGeneration == requestGeneration
}

internal fun buildCatalogRefreshFailureMessage(): String {
    return "更新に失敗しました"
}

internal enum class CatalogRefreshAvailability {
    Busy,
    Ready
}

internal fun resolveCatalogRefreshAvailability(
    isRefreshing: Boolean
): CatalogRefreshAvailability {
    return if (isRefreshing) {
        CatalogRefreshAvailability.Busy
    } else {
        CatalogRefreshAvailability.Ready
    }
}

internal fun canSubmitCreateThread(title: String, comment: String): Boolean {
    return title.isNotBlank() || comment.isNotBlank()
}

internal fun buildCreateThreadBoardMissingMessage(): String {
    return "板が選択されていません"
}

internal fun buildCreateThreadSuccessMessage(threadId: String?): String {
    return if (threadId.isNullOrBlank()) {
        "スレッドを作成しました。カタログ更新で確認してください"
    } else {
        "スレッドを作成しました (ID: $threadId)"
    }
}

internal fun buildCreateThreadFailureMessage(error: Throwable): String {
    return "スレッド作成に失敗しました: ${error.message ?: "不明なエラー"}"
}

internal fun buildCatalogExternalAppUrl(boardUrl: String, mode: CatalogMode): String {
    val resolvedMode = if (mode == CatalogMode.WatchWords) {
        CatalogMode.Catalog
    } else {
        mode
    }
    return BoardUrlResolver.resolveCatalogUrl(boardUrl, resolvedMode)
}

internal fun buildCatalogLoadErrorMessage(error: Throwable): String {
    val message = error.message
    return when {
        message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
        message?.contains("404") == true -> "板が見つかりません (404)"
        message?.contains("500") == true -> "サーバーエラー (500)"
        message?.contains("HTTP error") == true -> "ネットワークエラー: $message"
        message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
        else -> "カタログを読み込めませんでした: ${message ?: "不明なエラー"}"
    }
}

internal data class CreateThreadDraft(
    val name: String = "",
    val email: String = "",
    val title: String = "",
    val comment: String = "",
    val password: String = ""
)

internal fun saveCreateThreadDraft(draft: CreateThreadDraft): List<String> {
    return listOf(
        draft.name,
        draft.email,
        draft.title,
        draft.comment,
        draft.password
    )
}

internal fun restoreCreateThreadDraft(saved: List<Any?>): CreateThreadDraft {
    return CreateThreadDraft(
        name = saved.getOrNull(0) as? String ?: "",
        email = saved.getOrNull(1) as? String ?: "",
        title = saved.getOrNull(2) as? String ?: "",
        comment = saved.getOrNull(3) as? String ?: "",
        password = saved.getOrNull(4) as? String ?: ""
    )
}

internal val CreateThreadDraftSaver: Saver<CreateThreadDraft, Any> = listSaver(
    save = { draft -> saveCreateThreadDraft(draft) },
    restore = ::restoreCreateThreadDraft
)

internal fun emptyCreateThreadDraft(): CreateThreadDraft = CreateThreadDraft()

internal fun updateCreateThreadDraftName(draft: CreateThreadDraft, value: String): CreateThreadDraft {
    return draft.copy(name = value)
}

internal fun updateCreateThreadDraftEmail(draft: CreateThreadDraft, value: String): CreateThreadDraft {
    return draft.copy(email = value)
}

internal fun updateCreateThreadDraftTitle(draft: CreateThreadDraft, value: String): CreateThreadDraft {
    return draft.copy(title = value)
}

internal fun updateCreateThreadDraftComment(draft: CreateThreadDraft, value: String): CreateThreadDraft {
    return draft.copy(comment = value)
}

internal fun updateCreateThreadDraftPassword(draft: CreateThreadDraft, value: String): CreateThreadDraft {
    return draft.copy(password = value)
}

internal fun resolveCreateThreadDialogOpenPassword(
    currentPassword: String,
    lastUsedDeleteKey: String
): String {
    return resolveDeleteKeyAutofill(currentPassword, lastUsedDeleteKey)
}

internal fun normalizeCreateThreadPasswordForSubmit(password: String): String {
    return normalizeDeleteKeyForSubmit(password)
}

internal data class CatalogPastSearchRuntimeState(
    val state: ArchiveSearchState = ArchiveSearchState.Idle,
    val job: Job? = null,
    val generation: Long = 0L,
    val lastArchiveSearchScope: ArchiveSearchScope? = null
)

internal fun resetCatalogPastSearchRuntimeState(
    scope: ArchiveSearchScope?
): CatalogPastSearchRuntimeState {
    return CatalogPastSearchRuntimeState(lastArchiveSearchScope = scope)
}

internal data class WatchWordMutationState(
    val updatedWords: List<String>,
    val message: String,
    val shouldPersist: Boolean
)

internal data class CatalogNgMutationState(
    val updatedWords: List<String>,
    val message: String,
    val shouldPersist: Boolean
)

internal data class CatalogNgFilterToggleState(
    val isEnabled: Boolean,
    val message: String
)

internal data class CatalogNgManagementSheetState(
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val ngFilteringEnabled: Boolean,
    val includeHeaderSection: Boolean
)

internal fun addWatchWord(
    existingWords: List<String>,
    input: String
): WatchWordMutationState {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> WatchWordMutationState(
            updatedWords = existingWords,
            message = "監視ワードを入力してください",
            shouldPersist = false
        )
        existingWords.any { it.equals(trimmed, ignoreCase = true) } -> WatchWordMutationState(
            updatedWords = existingWords,
            message = "そのワードはすでに登録されています",
            shouldPersist = false
        )
        else -> WatchWordMutationState(
            updatedWords = existingWords + trimmed,
            message = "監視ワードを追加しました",
            shouldPersist = true
        )
    }
}

internal fun removeWatchWord(
    existingWords: List<String>,
    entry: String
): WatchWordMutationState {
    return WatchWordMutationState(
        updatedWords = existingWords.filterNot { it == entry },
        message = "監視ワードを削除しました",
        shouldPersist = true
    )
}

internal fun addCatalogNgWord(
    existingWords: List<String>,
    input: String
): CatalogNgMutationState {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> CatalogNgMutationState(
            updatedWords = existingWords,
            message = "NGワードに含める文字を入力してください",
            shouldPersist = false
        )
        existingWords.any { it.equals(trimmed, ignoreCase = true) } -> CatalogNgMutationState(
            updatedWords = existingWords,
            message = "そのNGワードはすでに登録されています",
            shouldPersist = false
        )
        else -> CatalogNgMutationState(
            updatedWords = existingWords + trimmed,
            message = "NGワードを追加しました",
            shouldPersist = true
        )
    }
}

internal fun removeCatalogNgWord(
    existingWords: List<String>,
    entry: String
): CatalogNgMutationState {
    return CatalogNgMutationState(
        updatedWords = existingWords.filterNot { it == entry },
        message = "NGワードを削除しました",
        shouldPersist = true
    )
}

internal fun toggleCatalogNgFiltering(
    currentEnabled: Boolean
): CatalogNgFilterToggleState {
    val nextEnabled = !currentEnabled
    return CatalogNgFilterToggleState(
        isEnabled = nextEnabled,
        message = if (nextEnabled) "NG表示を有効にしました" else "NG表示を無効にしました"
    )
}

internal fun resolveCatalogNgManagementSheetState(
    ngWords: List<String>,
    ngFilteringEnabled: Boolean
): CatalogNgManagementSheetState {
    return CatalogNgManagementSheetState(
        ngHeaders = emptyList(),
        ngWords = ngWords,
        ngFilteringEnabled = ngFilteringEnabled,
        includeHeaderSection = false
    )
}

internal enum class CatalogBackAction {
    CloseDrawer,
    ExitSearch,
    NavigateBack
}

internal fun resolveCatalogBackAction(
    isDrawerOpen: Boolean,
    isSearchActive: Boolean
): CatalogBackAction {
    return when {
        isDrawerOpen -> CatalogBackAction.CloseDrawer
        isSearchActive -> CatalogBackAction.ExitSearch
        else -> CatalogBackAction.NavigateBack
    }
}

internal data class CatalogSettingsActionState(
    val closeSettingsMenu: Boolean = true,
    val scrollToTop: Boolean = false,
    val showDisplayStyleDialog: Boolean = false,
    val showNgManagement: Boolean = false,
    val showWatchWords: Boolean = false,
    val openExternalApp: Boolean = false,
    val togglePrivacy: Boolean = false
)

internal fun resolveCatalogSettingsActionState(
    menuItem: CatalogSettingsMenuItem
): CatalogSettingsActionState {
    return when (menuItem) {
        CatalogSettingsMenuItem.ScrollToTop -> CatalogSettingsActionState(scrollToTop = true)
        CatalogSettingsMenuItem.DisplayStyle -> CatalogSettingsActionState(showDisplayStyleDialog = true)
        CatalogSettingsMenuItem.NgManagement -> CatalogSettingsActionState(showNgManagement = true)
        CatalogSettingsMenuItem.WatchWords -> CatalogSettingsActionState(showWatchWords = true)
        CatalogSettingsMenuItem.ExternalApp -> CatalogSettingsActionState(openExternalApp = true)
        CatalogSettingsMenuItem.Privacy -> CatalogSettingsActionState(togglePrivacy = true)
    }
}

internal data class CatalogNavEntryMeta(
    val label: String,
    val icon: ImageVector
)

internal fun CatalogNavEntryId.toMeta(): CatalogNavEntryMeta {
    return when (this) {
        CatalogNavEntryId.CreateThread -> CatalogNavEntryMeta("スレッド作成", Icons.Rounded.Add)
        CatalogNavEntryId.ScrollToTop -> CatalogNavEntryMeta("一番上に行く", Icons.Rounded.VerticalAlignTop)
        CatalogNavEntryId.RefreshCatalog -> CatalogNavEntryMeta("カタログ更新", Icons.Rounded.Refresh)
        CatalogNavEntryId.PastThreadSearch -> CatalogNavEntryMeta("過去スレ検索", Icons.Rounded.History)
        CatalogNavEntryId.Mode -> CatalogNavEntryMeta("モード", Icons.AutoMirrored.Rounded.Sort)
        CatalogNavEntryId.Settings -> CatalogNavEntryMeta("設定", Icons.Rounded.Settings)
    }
}

internal fun resolveCatalogNavBarEntries(menuEntries: List<CatalogNavEntryConfig>): List<CatalogNavEntryConfig> {
    return normalizeCatalogNavEntries(menuEntries)
        .filter { it.placement == CatalogNavEntryPlacement.BAR }
        .sortedWith(compareBy<CatalogNavEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
}

internal enum class CatalogMenuAction(val label: String) {
    Settings("設定")
}

internal enum class CatalogSettingsMenuItem(
    val label: String,
    val icon: ImageVector,
    val description: String?
) {
    WatchWords("監視ワード", Icons.Rounded.WatchLater, "監視中のワードを編集"),
    NgManagement("NG管理", Icons.Rounded.Block, "NGワードとIDを管理"),
    ExternalApp("外部アプリ", Icons.AutoMirrored.Rounded.OpenInNew, "外部アプリ連携を設定"),
    DisplayStyle("表示の切り替え", Icons.Rounded.ViewModule, "カタログ表示方法を変更"),
    ScrollToTop("一番上に行く", Icons.Rounded.VerticalAlignTop, "グリッドの先頭へ移動"),
    Privacy("プライバシー", Icons.Rounded.Lock, "プライバシー設定を確認")
}
