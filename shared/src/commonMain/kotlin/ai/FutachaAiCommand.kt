package com.valoser.futacha.shared.ai

enum class FutachaAiCommandRisk {
    Safe,
    Confirm,
    OpenOnly
}

enum class FutachaAiAction(
    val id: String,
    val label: String,
    val risk: FutachaAiCommandRisk
) {
    OpenBoardList("open_board_list", "板一覧を開く", FutachaAiCommandRisk.Safe),
    OpenBoard("open_board", "指定した板を開く", FutachaAiCommandRisk.Safe),
    SearchAndOpenBoard("search_open_board", "板名で検索して開く", FutachaAiCommandRisk.Safe),
    RefreshCurrentBoard("refresh_current_board", "現在の板を更新", FutachaAiCommandRisk.OpenOnly),
    RefreshCatalog("refresh_catalog", "カタログを更新", FutachaAiCommandRisk.OpenOnly),
    OpenHistoryDrawer("open_history_drawer", "履歴ドロワーを開く", FutachaAiCommandRisk.OpenOnly),
    RefreshHistory("refresh_history", "履歴を更新", FutachaAiCommandRisk.Safe),
    OpenThread("open_thread", "指定スレを開く", FutachaAiCommandRisk.Safe),
    OpenThreadFromUrl("open_thread_url", "スレURLからスレを開く", FutachaAiCommandRisk.Safe),
    RefreshCurrentThread("refresh_current_thread", "現在のスレを更新", FutachaAiCommandRisk.OpenOnly),
    ScrollThreadToTop("thread_top", "スレの先頭へ移動", FutachaAiCommandRisk.OpenOnly),
    ScrollThreadToBottom("thread_bottom", "スレの末尾へ移動", FutachaAiCommandRisk.OpenOnly),
    StartThreadReadAloud("thread_read_aloud_start", "スレ読み上げを開始", FutachaAiCommandRisk.OpenOnly),
    PauseThreadReadAloud("thread_read_aloud_pause", "スレ読み上げを一時停止", FutachaAiCommandRisk.OpenOnly),
    StopThreadReadAloud("thread_read_aloud_stop", "スレ読み上げを停止", FutachaAiCommandRisk.OpenOnly),
    NextThreadReadAloud("thread_read_aloud_next", "次の読み上げ位置へ移動", FutachaAiCommandRisk.OpenOnly),
    PreviousThreadReadAloud("thread_read_aloud_previous", "前の読み上げ位置へ移動", FutachaAiCommandRisk.OpenOnly),
    ScrollCatalogToTop("catalog_top", "カタログの先頭へ移動", FutachaAiCommandRisk.OpenOnly),
    StartCatalogSearch("start_catalog_search", "カタログ検索を開始", FutachaAiCommandRisk.OpenOnly),
    SearchCatalog("search_catalog", "カタログで指定語句を検索", FutachaAiCommandRisk.Safe),
    StartThreadSearch("start_thread_search", "スレ内検索を開始", FutachaAiCommandRisk.OpenOnly),
    SearchThread("search_thread", "スレ内で指定語句を検索", FutachaAiCommandRisk.OpenOnly),
    NextSearchResult("next_search_result", "次の検索結果へ移動", FutachaAiCommandRisk.OpenOnly),
    PreviousSearchResult("previous_search_result", "前の検索結果へ移動", FutachaAiCommandRisk.OpenOnly),
    OpenGallery("open_gallery", "ギャラリーを開く", FutachaAiCommandRisk.OpenOnly),
    OpenSavedThreads("open_saved_threads", "保存済みスレ一覧を開く", FutachaAiCommandRisk.Safe),
    OpenGlobalSettings("open_global_settings", "グローバル設定を開く", FutachaAiCommandRisk.Safe),
    OpenCatalogSettings("open_catalog_settings", "カタログ設定を開く", FutachaAiCommandRisk.OpenOnly),
    OpenThreadSettings("open_thread_settings", "スレ設定を開く", FutachaAiCommandRisk.OpenOnly),
    EnablePrivacyFilter("privacy_on", "プライバシーフィルタON", FutachaAiCommandRisk.Safe),
    DisablePrivacyFilter("privacy_off", "プライバシーフィルタOFF", FutachaAiCommandRisk.Safe),
    EnableBackgroundRefresh("background_refresh_on", "バックグラウンド更新ON", FutachaAiCommandRisk.Safe),
    DisableBackgroundRefresh("background_refresh_off", "バックグラウンド更新OFF", FutachaAiCommandRisk.Safe),
    EnableThreadSummaryMode("thread_summary_on", "スレ要約モードON", FutachaAiCommandRisk.Safe),
    DisableThreadSummaryMode("thread_summary_off", "スレ要約モードOFF", FutachaAiCommandRisk.Safe),
    EnableAiPostFilter("ai_post_filter_on", "荒らし非表示ON", FutachaAiCommandRisk.Safe),
    DisableAiPostFilter("ai_post_filter_off", "荒らし非表示OFF", FutachaAiCommandRisk.Safe),
    OpenCatalogDisplaySettings("open_catalog_display", "カタログ表示設定を開く", FutachaAiCommandRisk.OpenOnly),
    SetCatalogMode("set_catalog_mode", "カタログモードを変更", FutachaAiCommandRisk.Safe),
    OpenNgManagement("open_ng_management", "NG管理を開く", FutachaAiCommandRisk.OpenOnly),
    OpenWatchWords("open_watch_words", "監視ワード管理を開く", FutachaAiCommandRisk.OpenOnly),
    AddWatchWord("add_watch_word", "監視ワードを追加", FutachaAiCommandRisk.Safe),
    AddNgWord("add_ng_word", "NGワードを追加", FutachaAiCommandRisk.Safe),
    AddNgHeader("add_ng_header", "NGヘッダーを追加", FutachaAiCommandRisk.Safe),
    OpenBoardExternally("open_board_external", "外部アプリで現在の板を開く", FutachaAiCommandRisk.OpenOnly),
    OpenThreadExternally("open_thread_external", "外部アプリで現在のスレを開く", FutachaAiCommandRisk.OpenOnly),
    OpenVersionInfo("open_version_info", "アプリバージョン表示を開く", FutachaAiCommandRisk.Safe),
    OpenCookieManagement("open_cookie_management", "Cookie管理画面を開く", FutachaAiCommandRisk.Safe),
    OpenFileManagerSettings("open_file_manager_settings", "優先ファイラー設定を開く", FutachaAiCommandRisk.Safe),
    SaveCurrentThread("save_current_thread", "現在のスレを保存", FutachaAiCommandRisk.Confirm),
    SaveThread("save_thread", "指定スレを保存", FutachaAiCommandRisk.Confirm),
    DeleteHistoryEntry("delete_history_entry", "履歴エントリを削除", FutachaAiCommandRisk.Confirm),
    ClearHistory("clear_history", "履歴を全削除", FutachaAiCommandRisk.Confirm),
    DeleteSavedThread("delete_saved_thread", "保存済みスレを削除", FutachaAiCommandRisk.Confirm),
    ClearSavedThreads("clear_saved_threads", "保存済みスレを全削除", FutachaAiCommandRisk.Confirm),
    AddBoard("add_board", "板を追加", FutachaAiCommandRisk.Confirm),
    DeleteBoard("delete_board", "板を削除", FutachaAiCommandRisk.Confirm),
    DraftReply("draft_reply", "レス投稿の下書きを作成", FutachaAiCommandRisk.Confirm),
    DraftThread("draft_thread", "スレ立て下書きを作成", FutachaAiCommandRisk.Confirm);

    companion object {
        val supportedActions: List<FutachaAiAction> = entries

        fun fromId(id: String?): FutachaAiAction? {
            val normalized = id?.normalizedAiIdentifier() ?: return null
            return entries.firstOrNull { action ->
                action.id.normalizedAiIdentifier() == normalized ||
                    action.name.normalizedAiIdentifier() == normalized
            } ?: FUTACHA_AI_ACTION_ALIASES[normalized]
        }
    }
}

private val FUTACHA_AI_ACTION_ALIASES: Map<String, FutachaAiAction> = mapOf(
    "boards" to FutachaAiAction.OpenBoardList,
    "boardlist" to FutachaAiAction.OpenBoardList,
    "openboards" to FutachaAiAction.OpenBoardList,
    "openboardlist" to FutachaAiAction.OpenBoardList,
    "settings" to FutachaAiAction.OpenGlobalSettings,
    "opensettings" to FutachaAiAction.OpenGlobalSettings,
    "preferences" to FutachaAiAction.OpenGlobalSettings,
    "history" to FutachaAiAction.OpenHistoryDrawer,
    "openhistory" to FutachaAiAction.OpenHistoryDrawer,
    "savedthreads" to FutachaAiAction.OpenSavedThreads,
    "catalogsearch" to FutachaAiAction.SearchCatalog,
    "threadsearch" to FutachaAiAction.SearchThread,
    "refreshboard" to FutachaAiAction.RefreshCurrentBoard,
    "refreshcatalog" to FutachaAiAction.RefreshCatalog,
    "refreshthread" to FutachaAiAction.RefreshCurrentThread,
    "readaloud" to FutachaAiAction.StartThreadReadAloud,
    "startreadaloud" to FutachaAiAction.StartThreadReadAloud,
    "ttsstart" to FutachaAiAction.StartThreadReadAloud,
    "ttspause" to FutachaAiAction.PauseThreadReadAloud,
    "ttsstop" to FutachaAiAction.StopThreadReadAloud,
    "ttsnext" to FutachaAiAction.NextThreadReadAloud,
    "ttsprevious" to FutachaAiAction.PreviousThreadReadAloud,
    "save" to FutachaAiAction.SaveCurrentThread,
    "savecurrent" to FutachaAiAction.SaveCurrentThread,
    "savethread" to FutachaAiAction.SaveThread,
    "reply" to FutachaAiAction.DraftReply,
    "draftreply" to FutachaAiAction.DraftReply,
    "newthread" to FutachaAiAction.DraftThread,
    "draftthread" to FutachaAiAction.DraftThread,
    "watchword" to FutachaAiAction.AddWatchWord,
    "addwatch" to FutachaAiAction.AddWatchWord,
    "ngword" to FutachaAiAction.AddNgWord,
    "addng" to FutachaAiAction.AddNgWord,
    "ngheader" to FutachaAiAction.AddNgHeader,
    "summaryon" to FutachaAiAction.EnableThreadSummaryMode,
    "summaryoff" to FutachaAiAction.DisableThreadSummaryMode,
    "postfilteron" to FutachaAiAction.EnableAiPostFilter,
    "postfilteroff" to FutachaAiAction.DisableAiPostFilter
)

data class FutachaAiCommand(
    val action: FutachaAiAction,
    val parameters: Map<String, String> = emptyMap(),
    val source: String = "unknown"
) {
    fun parameter(vararg names: String): String? {
        for (name in names) {
            parameters[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val normalizedNames = names.map { it.normalizedAiParameterName() }.toSet()
        parameters.entries.firstOrNull { (key, value) ->
            key.normalizedAiParameterName() in normalizedNames && value.trim().isNotEmpty()
        }?.let { return it.value.trim() }
        return null
    }
}

private const val FUTACHA_AI_COMMAND_MAX_PARAMETER_COUNT = 32
private const val FUTACHA_AI_COMMAND_MAX_PARAMETER_KEY_CHARS = 64
private const val FUTACHA_AI_COMMAND_MAX_PARAMETER_VALUE_CHARS = 12_000

fun sanitizeFutachaAiCommandParameters(parameters: Map<String, String>): Map<String, String> {
    return parameters.entries
        .asSequence()
        .filter { (key, _) -> key.isNotBlank() }
        .take(FUTACHA_AI_COMMAND_MAX_PARAMETER_COUNT)
        .mapNotNull { (key, value) ->
            val sanitizedKey = key
                .filterNot { it == '\u0000' }
                .trim()
                .take(FUTACHA_AI_COMMAND_MAX_PARAMETER_KEY_CHARS)
            val sanitizedValue = value
                .filterNot { it == '\u0000' }
                .trim()
                .take(FUTACHA_AI_COMMAND_MAX_PARAMETER_VALUE_CHARS)
            if (sanitizedKey.isBlank() || sanitizedValue.isBlank()) {
                null
            } else {
                sanitizedKey to sanitizedValue
            }
        }
        .toMap()
}

private fun String.normalizedAiParameterName(): String {
    return normalizedAiIdentifier()
}

private fun String.normalizedAiIdentifier(): String {
    return trim()
        .lowercase()
        .filter { it != '_' && it != '-' && !it.isWhitespace() }
}

data class FutachaAiConfirmationRequest(
    val command: FutachaAiCommand,
    val title: String,
    val message: String,
    val confirmLabel: String = "実行",
    val dismissLabel: String = "キャンセル"
)

sealed interface FutachaAiCommandOutcome {
    data class Completed(val message: String) : FutachaAiCommandOutcome
    data class NeedsConfirmation(val request: FutachaAiConfirmationRequest) : FutachaAiCommandOutcome
    data class NeedsForeground(val message: String) : FutachaAiCommandOutcome
    data class Failed(val message: String) : FutachaAiCommandOutcome
}
