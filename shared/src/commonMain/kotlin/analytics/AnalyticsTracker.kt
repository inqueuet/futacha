package com.valoser.futacha.shared.analytics

import com.valoser.futacha.shared.state.generateAppLockRandomBytes
import com.valoser.futacha.shared.util.describeFailureForLog
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val TAG = "AnalyticsTracker"
private const val MAX_EVENT_NAME_LENGTH = 40
private const val MAX_PARAMETER_NAME_LENGTH = 40
private const val MAX_PARAMETER_VALUE_LENGTH = 100
private const val MAX_ANALYTICS_SOURCE_CHARS = 8 * 1024
private const val SCREEN_VIEW_EVENT = "screen_view"
private const val ESSENTIAL_USAGE_EVENT = "essential_app_active"
private const val MAX_QUEUED_ANALYTICS_EVENTS = 512

/*
 * Firebase Analytics must not receive arbitrary user-provided strings. Board URLs,
 * thread titles, and posting text can all contain personal information, even when
 * the app does not intend them to. These helpers retain useful behavioural context
 * without exporting the source string.
 *
 * The identifier is salted for this app process only. It can correlate events in a
 * single Analytics session, but is neither persistent nor usable to recover or
 * correlate a board/thread across sessions.
 */
private val analyticsSessionSalt: String = generateAppLockRandomBytes(16).toAnalyticsSalt()
private val analyticsSessionStartMark = TimeSource.Monotonic.markNow()

private fun ByteArray.toAnalyticsSalt(): String = buildString(size * 2) {
    this@toAnalyticsSalt.forEach { byte ->
        append((byte.toInt() and 0xff).toString(16).padStart(2, '0'))
    }
}

private data class AnalyticsEventChainContext(
    val sequence: Long,
    val previousEvent: String,
    val elapsedMillis: Long
)

private data class QueuedAnalyticsEvent(
    val eventName: String,
    val params: Map<String, String>,
    val screenId: String,
    val screenContext: Map<String, String>,
    val elapsedMillis: Long
)

private data class QueuedAnalyticsBatch(
    val events: List<QueuedAnalyticsEvent>
)

private data class AnalyticsScreenState(
    val id: String,
    val enteredAt: TimeMark,
    val context: Map<String, String>
)

object AnalyticsTracker {
    // Fail closed until the persisted preference has loaded. configure() reapplies
    // the latest value so an SDK initialization race cannot silently enable or
    // disable collection against the user's choice.
    private val qualityCollectionEnabled = MutableStateFlow(false)
    private val isConfigured = MutableStateFlow(false)
    private val essentialUsageLogged = MutableStateFlow(false)
    private val currentScreen = MutableStateFlow(
        AnalyticsScreenState(
            id = "unknown",
            enteredAt = TimeSource.Monotonic.markNow(),
            context = emptyMap()
        )
    )
    // Analytics must never be able to exhaust the app heap when a platform SDK
    // stalls. Recent events are more useful for diagnostics than an unbounded
    // backlog of stale gestures, so retain a bounded ordered window.
    private val eventQueue = Channel<QueuedAnalyticsBatch>(
        capacity = MAX_QUEUED_ANALYTICS_EVENTS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val eventScope = CoroutineScope(SupervisorJob() + AppDispatchers.io)

    init {
        eventScope.launch {
            var sequence = 0L
            var previousEvent = "session_start"
            for (batch in eventQueue) {
                batch.events.forEach { event ->
                    sequence = if (sequence == Long.MAX_VALUE) 1L else sequence + 1L
                    val chain = AnalyticsEventChainContext(
                        sequence = sequence,
                        previousEvent = previousEvent,
                        elapsedMillis = event.elapsedMillis
                    )
                    logSanitizedEventNow(event, chain)
                    previousEvent = event.eventName
                }
            }
        }
    }

    fun configure(platformContext: Any?) {
        runAnalyticsSdkCatching {
            PlatformAnalytics.configure(platformContext)
            isConfigured.value = true
            PlatformAnalytics.setAnalyticsCollectionEnabled(qualityCollectionEnabled.value)
            if (qualityCollectionEnabled.value) {
                logEssentialUsageIfNeeded()
            }
        }.onFailure {
            Logger.w(TAG, "Failed to configure analytics: ${describeFailureForLog(it)}")
        }
    }

    fun setQualityCollectionEnabled(enabled: Boolean) {
        qualityCollectionEnabled.value = enabled
        runAnalyticsSdkCatching {
            PlatformAnalytics.setAnalyticsCollectionEnabled(enabled)
        }.onFailure {
            Logger.w(TAG, "Failed to update analytics collection state: ${describeFailureForLog(it)}")
        }
        if (enabled && isConfigured.value) {
            logEssentialUsageIfNeeded()
        }
    }

    fun screen(name: String, params: Map<String, String> = emptyMap()) {
        val boundedScreenId = name.take(MAX_ANALYTICS_SOURCE_CHARS)
        val nextScreen = AnalyticsScreenState(
            id = boundedScreenId,
            enteredAt = TimeSource.Monotonic.markNow(),
            context = params.asSequence()
                .filter { (key, _) ->
                    key == "board_context" || key == "thread_context" || key == "board_kind"
                }
                .take(5)
                .associate { (key, value) -> key to sanitizeValue(value) }
        )
        val previousScreen = updateCurrentAnalyticsScreen(nextScreen)
        val queuedEvents = buildList(capacity = 2) {
            if (previousScreen.id != "unknown") {
                add(
                    prepareQueuedAnalyticsEvent(
                        name = "screen_transition",
                        params = previousScreen.context + mapOf(
                            "from_screen" to previousScreen.id,
                            "from_screen_label" to analyticsScreenLabel(previousScreen.id),
                            "to_screen" to boundedScreenId,
                            "to_screen_label" to analyticsScreenLabel(boundedScreenId),
                            "screen_dwell_bucket" to analyticsDurationBucket(
                                previousScreen.enteredAt.elapsedNow().inWholeMilliseconds
                            )
                        ),
                        screen = previousScreen
                    )
                )
            }
            add(
                prepareQueuedAnalyticsEvent(
                    name = SCREEN_VIEW_EVENT,
                    params = mapOf(
                        "firebase_screen" to boundedScreenId,
                        "firebase_screen_class" to boundedScreenId
                    ) + params,
                    screen = nextScreen
                )
            )
        }
        enqueueAnalyticsBatch(queuedEvents)
    }

    fun event(name: String, params: Map<String, String> = emptyMap()) {
        if (!qualityCollectionEnabled.value) return
        logSanitizedEvent(name, params)
    }

    fun essentialEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!qualityCollectionEnabled.value) return
        logSanitizedEvent(
            name = name,
            params = mapOf("telemetry_purpose" to "essential_usage_count") + params
        )
    }

    /**
     * Records every completed touch gesture at the app root. The coordinates are
     * deliberately reduced to a 3x3 area: this tells us where the interaction
     * occurred without collecting precise touch data.
     */
    fun uiGesture(
        gesture: String,
        area: String,
        xBucket: String,
        yBucket: String,
        direction: String,
        durationMillis: Long,
        pointerCount: Int
    ) {
        event(
            "ui_gesture",
            mapOf(
                "gesture" to gesture,
                "gesture_label" to when (gesture) {
                    "tap" -> "タップ"
                    "long_press" -> "長押し"
                    else -> "ドラッグ・スクロール"
                },
                "touch_area" to area,
                "touch_area_label" to analyticsTouchAreaLabel(area),
                "touch_x_bucket" to xBucket,
                "touch_y_bucket" to yBucket,
                "drag_direction" to direction,
                "drag_direction_label" to analyticsDragDirectionLabel(direction),
                "gesture_duration_bucket" to analyticsDurationBucket(durationMillis),
                "pointer_count_bucket" to analyticsCountBucket(pointerCount)
            )
        )
    }

    /** Records a named control where a screen has a semantic action beyond a tap. */
    fun uiControl(control: String, label: String, params: Map<String, String> = emptyMap()) {
        event(
            "ui_control_action",
            mapOf("control" to control, "control_label" to label) + params
        )
    }

    private fun logEssentialUsageIfNeeded() {
        if (!essentialUsageLogged.compareAndSet(expect = false, update = true)) return
        essentialEvent(ESSENTIAL_USAGE_EVENT)
    }

    private fun logSanitizedEvent(name: String, params: Map<String, String> = emptyMap()) {
        enqueueAnalyticsBatch(
            listOf(
                prepareQueuedAnalyticsEvent(
                    name = name,
                    params = params,
                    screen = currentScreen.value
                )
            )
        )
    }

    private fun prepareQueuedAnalyticsEvent(
        name: String,
        params: Map<String, String>,
        screen: AnalyticsScreenState
    ): QueuedAnalyticsEvent {
        val eventName = sanitizeName(name, fallback = "app_event", maxLength = MAX_EVENT_NAME_LENGTH)
        // Bound queued values before they enter the 512-item channel. Waiting
        // until the platform consumer runs allowed a burst of large external
        // strings to retain hundreds of megabytes despite the bounded count.
        val boundedParams = buildMap {
            params.asSequence().take(50).forEach { (key, value) ->
                val sanitizedKey = sanitizeName(
                    key,
                    fallback = "param",
                    maxLength = MAX_PARAMETER_NAME_LENGTH
                )
                if (sanitizedKey !in this) {
                    put(sanitizedKey, sanitizeValue(value))
                }
            }
        }
        return QueuedAnalyticsEvent(
            eventName = eventName,
            params = boundedParams,
            screenId = screen.id,
            screenContext = screen.context,
            elapsedMillis = analyticsSessionStartMark.elapsedNow().inWholeMilliseconds
        )
    }

    private fun enqueueAnalyticsBatch(events: List<QueuedAnalyticsEvent>) {
        if (events.isEmpty() || !qualityCollectionEnabled.value) return
        if (eventQueue.trySend(QueuedAnalyticsBatch(events)).isFailure) {
            Logger.w(TAG, "Failed to enqueue analytics event ${events.first().eventName}")
        }
    }

    private fun updateCurrentAnalyticsScreen(next: AnalyticsScreenState): AnalyticsScreenState {
        while (true) {
            val previous = currentScreen.value
            if (currentScreen.compareAndSet(previous, next)) return previous
        }
    }

    private fun logSanitizedEventNow(event: QueuedAnalyticsEvent, chain: AnalyticsEventChainContext) {
        if (!qualityCollectionEnabled.value) return
        val eventName = event.eventName
        val params = event.params
        // Event identifiers have to be ASCII for Firebase. Keep the identifier for
        // aggregation, but attach a Japanese label so the Console is readable
        // without having to infer a meaning from a model-like event name.
        val automaticContext = mapOf(
            "event_label" to analyticsEventLabel(eventName),
            "screen_id" to event.screenId,
            "screen_label" to analyticsScreenLabel(event.screenId),
            "event_sequence" to chain.sequence.toString(),
            "previous_event" to chain.previousEvent,
            "previous_event_label" to analyticsEventLabel(chain.previousEvent),
            "session_elapsed_bucket" to analyticsDurationBucket(chain.elapsedMillis)
        ) + event.screenContext + analyticsReadableParameters(eventName, params)
        val paramsWithLabel = automaticContext + params
        val sanitizedParams = paramsWithLabel
            .asSequence()
            .mapNotNull { (key, value) ->
                val sanitizedKey = sanitizeName(key, fallback = "param", maxLength = MAX_PARAMETER_NAME_LENGTH)
                val sanitizedValue = sanitizeValue(value)
                if (sanitizedValue.isBlank()) {
                    null
                } else {
                    sanitizedKey to sanitizedValue
                }
            }
            .distinctBy { it.first }
            .take(25)
            .toMap()

        runAnalyticsSdkCatching {
            PlatformAnalytics.logEvent(eventName, sanitizedParams)
        }.onFailure {
            Logger.w(TAG, "Failed to log analytics event $eventName: ${describeFailureForLog(it)}")
        }
    }
}

/** Human-readable name shown alongside the Firebase-safe event identifier. */
private fun analyticsEventLabel(eventName: String): String = when (eventName) {
    "screen_view" -> "画面を表示"
    "screen_transition" -> "画面を移動"
    "history_entry_deleted" -> "履歴を削除"
    "history_entry_updated" -> "履歴を更新"
    "history_cleared" -> "履歴を全削除"
    "background_refresh_started", "history_refresh_started" -> "履歴を更新開始"
    "background_refresh_result", "history_refresh_result" -> "履歴を更新完了"
    "catalog_thread_selected" -> "カタログからスレッドを開く"
    "catalog_browse_progress" -> "カタログの閲覧位置を更新"
    "thread_load_started" -> "スレッドを読み込み開始"
    "thread_load_result" -> "スレッドの読み込み結果"
    "thread_refresh_started" -> "スレッドを更新開始"
    "thread_refresh_result" -> "スレッドの更新結果"
    "thread_create_submitted" -> "スレッド作成を送信"
    "thread_reply_submitted" -> "返信を送信"
    "thread_search_submitted" -> "スレッド内を検索"
    "thread_search_navigation" -> "検索結果を移動"
    "media_fullscreen_opened" -> "画像・動画をフル表示"
    "saidane_requested" -> "そうだねを送信"
    "thread_action_started" -> "スレッド操作を開始"
    "thread_action_result" -> "スレッド操作の結果"
    "thread_action_busy" -> "スレッド操作は処理中"
    "thread_save_started" -> "スレッドを保存開始"
    "thread_save_result" -> "スレッド保存の結果"
    "read_aloud_start_result" -> "読み上げ開始の結果"
    "read_aloud_started" -> "読み上げを開始"
    "read_aloud_finished" -> "読み上げを完了"
    "read_aloud_blocked" -> "読み上げを利用不可"
    "read_aloud_error" -> "読み上げエラー"
    "read_aloud_seek" -> "読み上げ位置を移動"
    "catalog_mode_changed" -> "カタログ表示モードを変更"
    "catalog_display_style_changed" -> "カタログ表示方法を変更"
    "watch_word_changed" -> "監視ワードを変更"
    "ng_word_changed" -> "NGワードを変更"
    "ng_header_changed" -> "NGヘッダーを変更"
    "ng_filter_toggled" -> "NGフィルターを切替"
    "preference_changed" -> "設定を変更"
    "ui_gesture" -> "画面を操作"
    "ui_control_action" -> "画面の項目を操作"
    "ai_command_received" -> "AIコマンドを受信"
    "ai_command_result" -> "AIコマンドの結果"
    "board_add_submitted" -> "板を追加"
    "board_add_result" -> "板追加の結果"
    "board_deleted" -> "板を削除"
    "board_menu_action" -> "板一覧メニューを操作"
    "board_selected" -> "板を選択"
    "board_selection_cleared" -> "板の選択を解除"
    "boards_reordered" -> "板の順序を変更"
    "cache_cleanup_started" -> "キャッシュ削除を開始"
    "catalog_grid_columns_changed" -> "カタログ列数を変更"
    "catalog_mode_selected" -> "カタログモードを選択"
    "catalog_nav_action" -> "カタログ下部メニューを操作"
    "catalog_refresh_started" -> "カタログ更新を開始"
    "catalog_refresh_result" -> "カタログ更新の結果"
    "catalog_scroll_to_top" -> "カタログを先頭へ移動"
    "catalog_search_state" -> "カタログ検索を切替"
    "catalog_search_query_changed" -> "カタログ検索語を入力"
    "catalog_settings_action" -> "カタログ設定を操作"
    "catalog_top_menu_action" -> "カタログ上部メニューを操作"
    "file_manager_picker_dismissed" -> "ファイラー選択を閉じる"
    "file_manager_picker_opened" -> "ファイラー選択を開く"
    "file_manager_selected" -> "ファイラーを選択"
    "global_settings_entry_selected" -> "共通設定を操作"
    "history_drawer_opened" -> "履歴を開く"
    "history_drawer_action" -> "履歴メニューを操作"
    "history_entry_selected" -> "履歴のスレッドを選択"
    "history_visit_record" -> "スレッド閲覧を記録"
    "manual_save_directory_action" -> "手動保存先を変更"
    "manual_save_input_changed" -> "手動保存先を入力"
    "menu_config_action" -> "メニュー構成を変更"
    "menu_config_changed" -> "メニュー構成を保存"
    "past_thread_search_started" -> "過去スレッド検索を開始"
    "past_thread_search_result" -> "過去スレッド検索の結果"
    "read_aloud_control_action" -> "読み上げ操作"
    "registered_thread_url_click" -> "登録済みスレッドURLを開く"
    "saved_thread_selected" -> "保存済みスレッドを選択"
    "saved_threads_dismissed" -> "保存済みスレッド画面を閉じる"
    "storage_stats_refreshed" -> "保存容量を再計算"
    "thread_create_result" -> "スレッド作成の結果"
    "thread_dismissed" -> "スレッドを閉じる"
    "thread_read_progress" -> "スレッドの閲覧位置を更新"
    "thread_menu_action" -> "スレッド操作バーを操作"
    else -> "アプリ内操作"
}

private fun analyticsScreenLabel(screenId: String): String = when (screenId) {
    "board_management" -> "板一覧"
    "app_lock" -> "アプリロック"
    "app_loading" -> "アプリ起動中"
    "saved_threads" -> "保存済みスレッド"
    "missing_board" -> "見つからない板"
    "catalog" -> "カタログ"
    "thread" -> "スレッド"
    else -> "画面未確定"
}

private fun analyticsTouchAreaLabel(area: String): String = when (area) {
    "top_left" -> "左上"
    "top_center" -> "上部中央"
    "top_right" -> "右上"
    "middle_left" -> "左中央"
    "middle_center" -> "中央"
    "middle_right" -> "右中央"
    "bottom_left" -> "左下"
    "bottom_center" -> "下部中央"
    "bottom_right" -> "右下"
    else -> "位置不明"
}

private fun analyticsDragDirectionLabel(direction: String): String = when (direction) {
    "none" -> "移動なし"
    "up" -> "上へ"
    "down" -> "下へ"
    "left" -> "左へ"
    "right" -> "右へ"
    else -> "斜め方向"
}

private fun analyticsReadableParameters(
    eventName: String,
    params: Map<String, String>
): Map<String, String> = buildMap {
    params["action"]?.let { action ->
        put("action_label", analyticsActionLabel(eventName, action))
    }
    params["preference"]?.let { preference ->
        put("preference_label", analyticsPreferenceLabel(preference))
        params["value"]?.let { value ->
            put("value_label", analyticsPreferenceValueLabel(preference, value))
        }
    }
    params["result"]?.let { result -> put("result_label", analyticsResultLabel(result)) }
    params["state"]?.let { state -> put("state_label", analyticsStateLabel(state)) }
    params["menu"]?.let { menu -> put("menu_label", analyticsMenuLabel(menu)) }
    params["entry"]?.let { entry -> put("entry_label", analyticsEntryLabel(entry)) }
    params["source"]?.let { source -> put("source_label", analyticsSourceLabel(source)) }
}

private fun analyticsActionLabel(eventName: String, action: String): String = when (action) {
    "add" -> "追加"
    "delete" -> "削除"
    "remove" -> "削除"
    "edit" -> "編集"
    "move" -> "移動"
    "reset" -> "初期化"
    "placement" -> "配置を変更"
    "settings" -> "設定を開く"
    "refresh" -> "更新"
    "reply" -> "返信"
    "save" -> "保存"
    "filter" -> "絞り込み"
    "gallery" -> "ギャラリーを開く"
    "scrolltotop", "scroll_to_top" -> "先頭へ移動"
    "scrolltobottom", "scroll_to_bottom" -> "末尾へ移動"
    "open" -> "開く"
    "close" -> "閉じる"
    "play" -> "再生"
    "pause" -> "一時停止"
    "stop" -> "停止"
    "seek", "seek_visible" -> "再生位置を移動"
    "next" -> "次へ"
    "previous" -> "前へ"
    "create_thread", "createthread" -> "スレッド作成"
    "past_thread_search", "pastthreadsearch" -> "過去スレッドを検索"
    "mode" -> "モードを選択"
    "external_app" -> "外部アプリで開く"
    "privacy" -> "プライバシー表示を切替"
    "back_to_boards" -> "板一覧へ戻る"
    "export" -> "エクスポート"
    "export_then_clear" -> "エクスポートして削除"
    "export_selected" -> "選択分をエクスポート"
    "import_preview" -> "インポート内容を確認"
    "import" -> "インポート"
    "import_selected" -> "選択分をインポート"
    "clear" -> "全削除"
    "inherit" -> "共通設定を使用"
    else -> if (eventName == "read_aloud_control_action") "読み上げを操作" else "操作"
}

private fun analyticsPreferenceLabel(preference: String): String = when (preference) {
    "background_refresh" -> "バックグラウンド更新"
    "watch_alert" -> "監視ワード通知"
    "lightweight_mode" -> "軽量モード"
    "thread_summary" -> "スレッド要約"
    "ai_post_filter" -> "AI投稿フィルター"
    "ai_command" -> "AIコマンド"
    "telemetry_collection" -> "利用状況データの収集"
    "privacy_filter" -> "プライバシーフィルター"
    "app_lock" -> "アプリロック"
    "manual_save_directory" -> "手動保存先"
    "attachment_picker" -> "添付ファイル選択方法"
    "save_directory_selection" -> "保存先の選択方法"
    "thread_gallery_tap" -> "ギャラリータップ時の動作"
    "theme_mode" -> "テーマ"
    "theme_palette" -> "配色"
    "app_icon" -> "アプリアイコン"
    "thread_display_mode" -> "スレッド表示方法"
    "thread_body_text_size" -> "本文文字サイズ"
    "thread_post_image_size" -> "投稿画像サイズ"
    "compact_thread_header" -> "コンパクトヘッダー"
    "catalog_fetch_rows" -> "カタログ取得件数"
    "manual_save_location" -> "手動保存場所"
    "preferred_file_manager" -> "優先ファイラー"
    else -> "設定"
}

private fun analyticsPreferenceValueLabel(preference: String, value: String): String = when (value) {
    "enabled" -> "ON"
    "disabled" -> "OFF"
    "present" -> "指定あり"
    "absent" -> "指定なし"
    "cleared" -> "解除"
    "path" -> "パス指定"
    "tree_uri" -> "フォルダ選択"
    "bookmark" -> "ブックマーク"
    else -> when (preference) {
        "catalog_fetch_rows" -> "${value}行"
        "attachment_picker" -> when (value) {
            "image" -> "画像のみ選択"
            "media" -> "画像・動画を選択"
            else -> value
        }
        "save_directory_selection" -> when (value) {
            "manual_input" -> "手入力"
            "picker" -> "フォルダ選択"
            else -> value
        }
        "thread_gallery_tap" -> when (value) {
            "openmedia" -> "添付を開く"
            "jumptopost" -> "レスに移動"
            else -> value
        }
        "theme_mode" -> when (value) {
            "system" -> "端末設定に合わせる"
            "light" -> "ライト"
            "dark" -> "ダーク"
            else -> value
        }
        "theme_palette" -> when (value) {
            "current" -> "ふたちゃ標準"
            "futabaclassic" -> "ふたばクラシック"
            "futabablack" -> "ふたばブラック"
            "midnight" -> "ミッドナイト"
            else -> value
        }
        "app_icon" -> when (value) {
            "current" -> "現在のアイコン"
            "classic" -> "クラシックアイコン"
            else -> value
        }
        "thread_display_mode" -> when (value) {
            "flat" -> "通常表示"
            "tree" -> "ツリー表示"
            else -> value
        }
        "thread_body_text_size" -> when (value) {
            "small" -> "小"
            "standard" -> "標準"
            "large" -> "大"
            "extralarge" -> "特大"
            else -> value
        }
        "thread_post_image_size" -> when (value) {
            "extrasmall" -> "最小"
            "small" -> "小"
            "medium" -> "中"
            "large" -> "大"
            else -> value
        }
        else -> value
    }
}

private fun analyticsResultLabel(result: String): String = when (result) {
    "success" -> "成功"
    "failure", "failed" -> "失敗"
    "busy" -> "処理中"
    "blocked" -> "実行不可"
    "duplicate" -> "重複"
    "accepted" -> "受理"
    "rejected" -> "拒否"
    "unresolved" -> "未解決"
    else -> result
}

private fun analyticsStateLabel(state: String): String = when (state) {
    "active" -> "有効"
    "inactive" -> "無効"
    "enabled" -> "ON"
    "disabled" -> "OFF"
    else -> state
}

private fun analyticsMenuLabel(menu: String): String = when (menu) {
    "catalog" -> "カタログ下部メニュー"
    "thread" -> "スレッド操作バー"
    else -> "メニュー"
}

private fun analyticsEntryLabel(entry: String): String = when (entry) {
    "createthread" -> "スレッド作成"
    "scrolltotop" -> "先頭へ移動"
    "scrolltobottom" -> "末尾へ移動"
    "refreshcatalog" -> "カタログ更新"
    "pastthreadsearch" -> "過去スレッド検索"
    "mode" -> "表示モード"
    "settings" -> "設定"
    "reply" -> "返信"
    "refresh" -> "更新"
    "gallery" -> "ギャラリー"
    "save" -> "保存"
    "filter" -> "絞り込み"
    "ngmanagement" -> "NG管理"
    "externalapp" -> "外部アプリ"
    "readaloud" -> "読み上げ"
    "privacy" -> "プライバシー"
    "cookies" -> "Cookie管理"
    "email" -> "メールで問い合わせ"
    "x" -> "Xを開く"
    "developer" -> "開発者情報"
    "privacypolicy" -> "プライバシーポリシー"
    else -> "項目"
}

private fun analyticsSourceLabel(source: String): String = when (source) {
    "drawer" -> "履歴ドロワー"
    "catalog" -> "カタログ"
    "thread" -> "スレッド"
    else -> source
}

/** Japanese label for the action value used by generic thread-action events. */
fun analyticsThreadActionLabel(action: String): String = when (action) {
    "reply" -> "返信を送信"
    "delete_by_user" -> "本人削除を送信"
    "deletion_request" -> "DEL依頼を送信"
    "saidane" -> "そうだねを送信"
    "save" -> "スレッドを保存"
    "refresh" -> "スレッドを更新"
    else -> "スレッド操作"
}

expect object PlatformAnalytics {
    fun configure(platformContext: Any?)
    fun setAnalyticsCollectionEnabled(enabled: Boolean)
    fun logEvent(name: String, params: Map<String, String>)
}

fun analyticsEnabledValue(enabled: Boolean): String = if (enabled) "enabled" else "disabled"

fun analyticsPresentValue(value: Any?): String = if (value == null) "absent" else "present"

fun analyticsCountBucket(count: Int): String = when {
    count <= 0 -> "0"
    count == 1 -> "1"
    count in 2..5 -> "2_5"
    count in 6..20 -> "6_20"
    count in 21..50 -> "21_50"
    count in 51..100 -> "51_100"
    count in 101..500 -> "101_500"
    count in 501..1000 -> "501_1000"
    else -> "1001_plus"
}

fun analyticsDurationBucket(millis: Long): String = when {
    millis < 0L -> "unknown"
    millis < 250L -> "lt_250ms"
    millis < 1_000L -> "250ms_1s"
    millis < 3_000L -> "1s_3s"
    millis < 10_000L -> "3s_10s"
    millis < 30_000L -> "10s_30s"
    millis < 60_000L -> "30s_60s"
    else -> "60s_plus"
}

fun analyticsBoardKind(boardUrl: String): String {
    return if (boardUrl.contains("example.com", ignoreCase = true)) "mock" else "remote"
}

fun analyticsResult(success: Boolean): String = if (success) "success" else "failure"

/** Returns a session-scoped, non-content identifier for a board or thread. */
fun analyticsSessionContextId(kind: String, vararg values: String?): String {
    var hash = -3750763034362895579L // FNV-1a offset basis
    fun appendToHash(value: String) {
        value.forEach { character ->
            hash = hash xor character.code.toLong()
            hash *= 1099511628211L // FNV-1a prime; overflow is intentional.
        }
    }
    appendToHash(analyticsSessionSalt)
    appendToHash("|")
    appendToHash(kind.take(MAX_ANALYTICS_SOURCE_CHARS))
    values.asSequence().take(MAX_ANALYTICS_CONTEXT_VALUES).forEach { value ->
        appendToHash("|")
        appendToHash(value.orEmpty().take(MAX_ANALYTICS_SOURCE_CHARS).trim())
    }
    return hash.toULong().toString(36)
}

private const val MAX_ANALYTICS_CONTEXT_VALUES = 16

fun analyticsTextLengthBucket(value: String?): String = analyticsCountBucket(value.orEmpty().trim().length)

fun analyticsTextLineCountBucket(value: String?): String {
    val normalized = value.orEmpty().trim()
    val lineCount = if (normalized.isEmpty()) 0 else normalized.count { it == '\n' } + 1
    return analyticsCountBucket(lineCount)
}

fun analyticsTextHasUrl(value: String?): String {
    return if (URL_PATTERN.containsMatchIn(value.orEmpty().take(MAX_ANALYTICS_SOURCE_CHARS))) "yes" else "no"
}

private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

fun analyticsFailureCategory(error: Throwable): String {
    val type = error::class.simpleName.orEmpty().take(256).lowercase()
    val message = error.message.orEmpty().take(MAX_ANALYTICS_SOURCE_CHARS).lowercase()
    return when {
        "timeout" in type || "timeout" in message -> "timeout"
        "cancel" in type || "cancel" in message -> "cancelled"
        "404" in message -> "http_404"
        "410" in message -> "http_410"
        Regex("""\b5\d\d\b""").containsMatchIn(message) -> "http_5xx"
        "too large" in message || "content-length" in message -> "body_too_large"
        "parse" in type || "parse" in message || "parser" in message -> "parse_failed"
        "dns" in message || "resolve" in message || "host" in message -> "dns_error"
        "network" in type || "network" in message || "http" in message -> "network_error"
        else -> "unknown"
    }
}

private fun sanitizeName(raw: String, fallback: String, maxLength: Int): String {
    val normalized = raw
        .take(MAX_ANALYTICS_SOURCE_CHARS)
        .trim()
        .lowercase()
        .map { char ->
            when {
                char in 'a'..'z' -> char
                char in '0'..'9' -> char
                char == '_' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .let { if (it.firstOrNull()?.isLetter() == true) it else "${fallback}_$it" }
        .trimEnd('_')
        .ifBlank { fallback }
    return normalized.take(maxLength).trimEnd('_').ifBlank { fallback }
}

private fun sanitizeValue(raw: String): String {
    return raw
        .take(MAX_ANALYTICS_SOURCE_CHARS)
        .trim()
        .replace(Regex("\\s+"), "_")
        .take(MAX_PARAMETER_VALUE_LENGTH)
}
