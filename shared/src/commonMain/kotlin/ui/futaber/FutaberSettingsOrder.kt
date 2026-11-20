package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.audio.SPEECH_PITCH_KEY
import com.valoser.futacha.shared.audio.SPEECH_RATE_KEY
import kotlin.math.roundToInt

/*
 * L-B9: what futaber's settings show and in which order (look plan §2.9, C47・C25・C21・C23・C46・C45・C37), and this app's
 * own rows after futaber's last one. Each screen's extra rows come from one function (`futaberSettings…Extras`), so the
 * batch F-B2 can put them behind 「その他の機能」 (plan §4.1 item 1) without touching futaber's rows.
 */

/** A row of the settings top. [id] is the end of its tag (`futaber-settings-row-…` / `futaber-settings-switch-…`). */
internal enum class FutaberSettingsRow(val id: String, val label: String) {
    // futaber's 全般 (C47; 「URLスキーム」 came with L-B12).
    Theme("theme", "テーマ"),
    Catalog("catalog", "カタログ"),
    Thread("thread", "スレッド"),
    Viewer("viewer", "画像ビューア"),
    // L-B12 (C05): the fifth of futaber's 全般.
    UrlScheme("url-scheme", "URLスキーム"),
    FontSize("font-size", "フォントサイズ"),
    Posting("posting", "書き込み設定"),
    Gesture("gesture", "ジェスチャー"),
    Ng("ng", "NG"),
    // futaber's 通知 (C11 for the second).
    PatrolEnabled("patrol-enabled", "通知を有効にする"),
    NewThreadNotify("new-thread-notify", "キーワードで新規スレッドを通知"),
    // futaber's last row (C12; 「オプション」 is futaber's purchase page and is not offered).
    Eula("eula", "EULA"),
    // This app's own rows, after futaber's (the extras).
    Storage("storage", "保存先・キャッシュ"),
    Network("network", "ネットワーク"),
    Background("background", "バックグラウンド"),
    Backup("backup", "バックアップ・復元"),
    Cookies("cookies", "Cookie管理"),
    Aimoge("aimoge", "あいもげ"),
    Notifications("notifications", "キーワード通知・巡回"),
    ImageSearch("image-search", "画像検索"),
    Media("media", "メディア機能"),
    Privacy("privacy", "プライバシー表示"),
    Ai("ai", "AI・補助機能"),
    UpdateCheck("update-check", "アップデート確認"),
    BackgroundRefresh("background-refresh", "バックグラウンド更新"),
    WatchAlert("watch-alert", "監視ワード自動アラート"),
    WatchWords("watch-words", "監視ワード（アラート用）"),
    Lightweight("lightweight", "軽量モード"),
    AppLock("app-lock", "起動ロック"),
    Extras("extras", "拡張機能"),
    AppIcon("app-icon", "アプリアイコン"),
    Mode("mode", "モード"),
    FileManager("preferred-file-manager", "優先ファイルマネージャー"),
    ArchiveReport("archive-report", com.valoser.futacha.shared.ui.board.VIEWED_THREAD_ARCHIVE_REPORT_LABEL),
    Help("help", "ヘルプ"),
    ChangeLog("changelog", "更新情報"),
    License("license", "ライセンス"),
    Version("version", "バージョン")
}

/** A group of rows with its heading (null = none) and whether it is this app's own (drawn after a wider gap). */
internal data class FutaberSettingsGroup(
    val id: String,
    val title: String?,
    val rows: List<FutaberSettingsRow>,
    val extra: Boolean = false
)

/** What the settings top can offer, from what the host passed in. */
internal data class FutaberSettingsAvailability(
    /** The shared services (the shared pages, the patrol): absent in the screen-only tests. */
    val services: Boolean = true,
    /** The app's state store (update check, app lock, app icon...). */
    val stateStore: Boolean = true,
    val cookies: Boolean = true,
    val watchWordsEditor: Boolean = true,
    /** Android only: the preferred file manager. */
    val fileManager: Boolean = false,
    /** Not on the desktop (it sends nothing). */
    val archiveReport: Boolean = true
)

/** futaber's groups of the settings top, in its order (C47): 全般 / 通知 and its note / (no heading) EULA. */
internal fun futaberSettingsTopGroups(available: FutaberSettingsAvailability): List<FutaberSettingsGroup> = buildList {
    add(
        FutaberSettingsGroup(
            "general", "全般",
            listOfNotNull(
                FutaberSettingsRow.Theme, FutaberSettingsRow.Catalog, FutaberSettingsRow.Thread,
                FutaberSettingsRow.Viewer.takeIf { available.services },
                FutaberSettingsRow.UrlScheme,
                FutaberSettingsRow.FontSize, FutaberSettingsRow.Posting, FutaberSettingsRow.Gesture, FutaberSettingsRow.Ng
            )
        )
    )
    if (available.services) {
        add(FutaberSettingsGroup("notify", "通知", listOf(FutaberSettingsRow.PatrolEnabled, FutaberSettingsRow.NewThreadNotify)))
    }
    add(FutaberSettingsGroup("eula", null, listOf(FutaberSettingsRow.Eula)))
}

/**
 * This app's own groups of the settings top, after futaber's EULA (look plan §2.9: データ・通信／機能／動作／このアプリ／
 * 閲覧スレ通知／情報・サポート, the order they had). Everything the settings could open before stays here; the shared patrol
 * manager (「キーワード通知・巡回」), which futaber's 通知 now leads to through its own page, is the first row of 機能.
 */
internal fun futaberSettingsTopExtras(available: FutaberSettingsAvailability): List<FutaberSettingsGroup> = buildList {
    if (available.services) {
        add(
            FutaberSettingsGroup(
                "data", "データ・通信",
                listOfNotNull(
                    FutaberSettingsRow.Storage, FutaberSettingsRow.Network, FutaberSettingsRow.Background, FutaberSettingsRow.Backup,
                    FutaberSettingsRow.Cookies.takeIf { available.cookies },
                    FutaberSettingsRow.Aimoge.takeIf { com.valoser.futacha.shared.ui.aimoge.aimogeSettingsVisible() }
                ),
                extra = true
            )
        )
        add(
            FutaberSettingsGroup(
                "features", "機能",
                listOf(
                    FutaberSettingsRow.Notifications, FutaberSettingsRow.ImageSearch, FutaberSettingsRow.Media,
                    FutaberSettingsRow.Privacy, FutaberSettingsRow.Ai
                ),
                extra = true
            )
        )
    }
    if (available.stateStore) {
        add(
            FutaberSettingsGroup(
                "behavior", "動作",
                listOfNotNull(
                    FutaberSettingsRow.UpdateCheck, FutaberSettingsRow.BackgroundRefresh, FutaberSettingsRow.WatchAlert,
                    FutaberSettingsRow.WatchWords.takeIf { available.watchWordsEditor },
                    FutaberSettingsRow.Lightweight, FutaberSettingsRow.AppLock
                ),
                extra = true
            )
        )
    }
    add(
        FutaberSettingsGroup(
            "app", "このアプリ",
            listOfNotNull(
                FutaberSettingsRow.Extras,
                FutaberSettingsRow.AppIcon.takeIf { available.stateStore },
                FutaberSettingsRow.Mode,
                FutaberSettingsRow.FileManager.takeIf { available.fileManager && available.stateStore }
            ),
            extra = true
        )
    )
    if (available.services && available.archiveReport) {
        add(FutaberSettingsGroup("archive-report", "閲覧スレ通知", listOf(FutaberSettingsRow.ArchiveReport), extra = true))
    }
    if (available.services) {
        add(
            FutaberSettingsGroup(
                "info", "情報・サポート",
                listOf(FutaberSettingsRow.Help, FutaberSettingsRow.ChangeLog, FutaberSettingsRow.License, FutaberSettingsRow.Version),
                extra = true
            )
        )
    }
}

/** The note under futaber's 通知 group, written for what this app does (futaber's tells the iOS notification state). */
internal const val FUTABER_SETTINGS_NOTIFY_NOTE =
    "・通知センターに出ない時は、端末の設定でこのアプリの通知を許可してください"

// ---- スレッド (C25・C21・C23・C26 and the 「従来の動き」 of plan §4.1 item 3) ----

/** futaber's sections of 設定→スレッド, in its order (`P/out-settings/104-02`, `D/settings-thread-lower.png`). */
internal enum class FutaberThreadSettingsSection(val title: String) {
    TitleName("題名と名前の表示"),
    ImageSize("画像表示サイズ"),
    Replies("返信"),
    ReadAloud("スレッド読み上げ"),
    TapScroll("画面をタップしてスクロール"),
    Other("その他")
}

/** futaber's sections (its 「スレッド読み込み方式」 has no counterpart here: this app has one way of reading). */
internal fun futaberThreadSettingsSections(): List<FutaberThreadSettingsSection> = FutaberThreadSettingsSection.entries

/** This app's own rows of 設定→スレッド, after futaber's 「その他」. */
internal enum class FutaberThreadSettingsExtra { SpeechVoice, DeletedPosts, PostLayout, Legacy }

internal fun futaberThreadSettingsExtras(services: Boolean): List<FutaberThreadSettingsExtra> = listOfNotNull(
    FutaberThreadSettingsExtra.SpeechVoice.takeIf { services },
    FutaberThreadSettingsExtra.DeletedPosts.takeIf { services },
    FutaberThreadSettingsExtra.PostLayout,
    FutaberThreadSettingsExtra.Legacy
)

/** One of the 「従来の動き」 switches: this app's old result of an item futaber does its own way (default off). */
internal data class FutaberLegacySwitch(val id: String, val key: String, val label: String)

/** The four switches of 「従来の動き」 (L-B5・L-B6・L-B6・L-B8), in that order; their keys and the off default are kept. */
internal val futaberLegacySwitches: List<FutaberLegacySwitch> = listOf(
    FutaberLegacySwitch("thread-instant-copy", FutaberSettingKeys.INSTANT_COPY, FUTABER_INSTANT_COPY_SWITCH),
    FutaberLegacySwitch("thread-legacy-search", FutaberSettingKeys.LEGACY_SEARCH_FILTER, FUTABER_LEGACY_SEARCH_SWITCH),
    FutaberLegacySwitch("thread-legacy-many-replies", FutaberSettingKeys.LEGACY_MANY_REPLIES_FILTER, FUTABER_LEGACY_MANY_REPLIES_SWITCH),
    FutaberLegacySwitch("thread-legacy-subject", FutaberSettingKeys.LEGACY_SUBJECT_FIELD, FUTABER_LEGACY_SUBJECT_SWITCH)
)

/** Whether a 「従来の動き」 switch is on in [settings]. */
internal fun FutaberDisplaySettings.legacySwitchOn(switch: FutaberLegacySwitch): Boolean = when (switch.key) {
    FutaberSettingKeys.INSTANT_COPY -> instantCopy
    FutaberSettingKeys.LEGACY_SEARCH_FILTER -> legacySearchFilter
    FutaberSettingKeys.LEGACY_MANY_REPLIES_FILTER -> legacyManyRepliesFilter
    FutaberSettingKeys.LEGACY_SUBJECT_FIELD -> legacySubjectField
    else -> false
}

/** C26: futaber's switch and its note in 「その他」. */
internal const val FUTABER_GOOGLE_LENS_SWITCH = "画像長押しメニューにGoogleLens検索を表示"
internal const val FUTABER_GOOGLE_LENS_NOTE = "GoogleLensはスマートフォン用サイトで表示できないため注意"

/** C25: futaber's two choices. */
internal const val FUTABER_TAP_SCROLL_ON = "タップしてスクロールする"
internal const val FUTABER_TAP_SCROLL_OFF = "タップしてスクロールしない"

// ---- C21: 画像表示サイズ ----

/** futaber's ratio (「比率 = 70%」): 10〜100% in steps of 10, 70% at first. */
internal const val FUTABER_IMAGE_RATIO_MIN = 10
internal const val FUTABER_IMAGE_RATIO_MAX = 100
internal const val FUTABER_IMAGE_RATIO_STEP = 10
internal const val FUTABER_IMAGE_RATIO_DEFAULT = 70
/** The old 「小さく表示」 (before L-B9) is read as a ratio of 50%. */
internal const val FUTABER_IMAGE_RATIO_LEGACY_SMALL = 50

/**
 * The ratio in percent: the stored one, else 50 for a person who had chosen the old 「小さく表示」 (the same key now means
 * 「画像のサイズ比率を指定」), else 70. A damaged or out-of-range value reads as the default.
 */
internal fun futaberImageRatioPercent(preferences: Map<String, String>): Int {
    val stored = preferences[FutaberSettingKeys.IMAGE_RATIO]
    if (stored != null) {
        return stored.toIntOrNull()?.takeIf { it in FUTABER_IMAGE_RATIO_MIN..FUTABER_IMAGE_RATIO_MAX } ?: FUTABER_IMAGE_RATIO_DEFAULT
    }
    return if (preferences[FutaberSettingKeys.SMALL_IMAGES] == "ON") FUTABER_IMAGE_RATIO_LEGACY_SMALL else FUTABER_IMAGE_RATIO_DEFAULT
}

/** The next ratio after a − (−1) or ＋ (+1), kept in 10〜100. */
internal fun futaberImageRatioStep(percent: Int, direction: Int): Int =
    (percent + direction * FUTABER_IMAGE_RATIO_STEP).coerceIn(FUTABER_IMAGE_RATIO_MIN, FUTABER_IMAGE_RATIO_MAX)

/**
 * The ratio a post's picture is drawn at before the operation menu's 「画像を小さくする」: futaber's 0.7 of the thumbnail
 * for 「オリジナルサイズで表示」, and that times the ratio for 「画像のサイズ比率を指定」 ([futaberAttachmentSize]).
 */
internal fun futaberSettingsImageRatio(byRatio: Boolean, percent: Int): Float =
    if (byRatio) FUTABER_ATTACHMENT_RATIO * percent.coerceIn(FUTABER_IMAGE_RATIO_MIN, FUTABER_IMAGE_RATIO_MAX) / 100f
    else FUTABER_ATTACHMENT_RATIO

// ---- C23: スレッド読み上げ (the shared compat.speech.pitch / rate) ----

/*
 * The shared values are multiples of the platform's normal voice: 0.5〜2.0, 1.0 normal, in tenths (the shared page's
 * slider has 15 stops). futaber's 「声の高さ」 is iOS's pitch multiplier, the same scale; its 「スピード」 is iOS's speech
 * rate, where the normal voice is 0.5 (`AVSpeechUtteranceDefaultSpeechRate`), so the shared rate is shown as half of it.
 * Each −＋ moves the shared value by one tenth, so a value set here or on the shared page reads back the same in both.
 */

/** The platform's normal rate on futaber's scale (iOS `AVSpeechUtteranceDefaultSpeechRate`). */
internal const val FUTABER_SPEECH_NORMAL_SPEED = 0.5f
private const val SHARED_SPEECH_MIN_TENTHS = 5
private const val SHARED_SPEECH_MAX_TENTHS = 20

/** A shared value (0.5〜2.0) as whole tenths, the default 1.0 for anything unreadable. */
internal fun futaberSharedSpeechTenths(stored: String?): Int {
    val value = stored?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f
    return (value * 10).roundToInt().coerceIn(SHARED_SPEECH_MIN_TENTHS, SHARED_SPEECH_MAX_TENTHS)
}

/** The shared value after a − (−1) or ＋ (+1), as it is stored ("1.1"). */
internal fun futaberSharedSpeechStep(tenths: Int, direction: Int): String {
    val next = (tenths + direction).coerceIn(SHARED_SPEECH_MIN_TENTHS, SHARED_SPEECH_MAX_TENTHS)
    return "${next / 10}.${next % 10}"
}

internal fun futaberSharedSpeechCanStep(tenths: Int, direction: Int): Boolean =
    if (direction < 0) tenths > SHARED_SPEECH_MIN_TENTHS else tenths < SHARED_SPEECH_MAX_TENTHS

/** C24: futaber's 「引用部分の声の高さ」 begins at its lowest, 0.5. */
internal const val FUTABER_QUOTE_PITCH_DEFAULT_TENTHS = 5

/** The stored quote pitch (tenths, 5〜20), the default for anything unreadable. */
internal fun futaberQuotePitchTenths(stored: String?): Int =
    stored?.toIntOrNull()?.takeIf { it in SHARED_SPEECH_MIN_TENTHS..SHARED_SPEECH_MAX_TENTHS } ?: FUTABER_QUOTE_PITCH_DEFAULT_TENTHS

/** futaber's 「声の高さ」 for a shared pitch: the same number ("1.0"). */
internal fun futaberSpeechPitchShown(tenths: Int): String = "${tenths / 10}.${tenths % 10}"

/** futaber's 「スピード」 for a shared rate: half of it ("0.5" for the normal 1.0, "0.55" for 1.1). */
internal fun futaberSpeechSpeedShown(tenths: Int): String {
    val hundredths = tenths * 5
    val whole = hundredths / 100
    val rest = hundredths % 100
    return if (rest % 10 == 0) "$whole.${rest / 10}" else "$whole.${rest.toString().padStart(2, '0')}"
}

/** The shared rate for one of futaber's speeds (the inverse of [futaberSpeechSpeedShown]). */
internal fun futaberSharedRateForSpeed(speed: Float): Float = speed / FUTABER_SPEECH_NORMAL_SPEED

/** The shared keys the read-aloud −＋ write (every mode reads them). */
internal val futaberSpeechKeys: List<String> = listOf(SPEECH_PITCH_KEY, SPEECH_RATE_KEY)

// ---- C46: ジェスチャー ----

/**
 * The lines under 「他」 of the gestures page: futaber's four, the first written for what the left edge does in this mode
 * (the board list since 2026-10-28, as futaber's does on the device; its own text says 「直接メニュー表示」), then this app's.
 */
internal val futaberGestureNoteLines: List<String> = listOf(
    "・スレッド内で左端からスワイプで板一覧を表示",
    "・スレッド内でレス長押しで引用その他アクション",
    "・スレッド内ツールバーの最下部スクロールボタンを長押しで新着へスクロール",
    "・書き込み画面で下スワイプで引用モード"
)

internal val futaberGestureExtraNoteLines: List<String> = listOf(
    "・スレッド内で右へスワイプでカタログへ戻る",
    "・スレッド内で左へスワイプで書き込み画面",
    "・カタログで右へスワイプで板一覧、左へスワイプでこの板で最後に見たスレッド",
    "・「画面をタップしてスクロール」（設定→スレッド）：スレッドの上1/3をタップで1ページ上、下1/3で1ページ下（引用・ID・URL・画像・返信数のタップはそれぞれの操作）"
)

internal fun futaberGestureNote(): String = (listOf("他") + futaberGestureNoteLines + futaberGestureExtraNoteLines).joinToString("\n")

// ---- NG (C45・C32・C33・C35・C37・C38・C39) ----

/**
 * futaber's rows of カタログNG: NGワード / NGワード(正規表現) / NGスレッドURL. The regular expressions (C32) wait for
 * where they are kept (data plan §2.7, D-B7), so the row is not shown until then.
 */
internal fun futaberNgCatalogRows(): List<FutaberNgListKind> = listOf(FutaberNgListKind.CatalogWords, FutaberNgListKind.CatalogThreads)

/** This app's own catalog lists (the other modes' rules that hide threads here), after futaber's. */
internal fun futaberNgCatalogExtras(): List<FutaberNgListKind> = listOf(FutaberNgListKind.CatalogWordRules, FutaberNgListKind.CatalogImages)

/**
 * futaber's rows of レスNG: NGワード / NGワード(正規表現) / NG画像 / NGネーム / NGメール / NG題名 / NGID/IP. The regular
 * expressions, the mail and the subject (C35・C38・C39) wait for D-B7; NGネーム and NGID/IP are one shared list shown
 * apart (C37: a word starting with 「ID:」 or 「IP:」 is an ID/IP).
 */
internal fun futaberNgResRows(): List<FutaberNgListKind> =
    listOf(FutaberNgListKind.ResWords, FutaberNgListKind.ResImages, FutaberNgListKind.ResNames, FutaberNgListKind.ResIdIp)

/** This app's own post list (the other modes' thread rules), shown to a person who has such rules. */
internal fun futaberNgResExtras(hasThreadDetails: Boolean): List<FutaberNgListKind> =
    if (hasThreadDetails) listOf(FutaberNgListKind.ThreadDetails) else emptyList()

/** Whether an entry of the shared 「NGネーム・ID等」 list is an ID or an IP (shown under NGID/IP). */
internal fun futaberNgHeaderIsIdIp(entry: String): Boolean {
    val value = entry.trim()
    return value.startsWith("ID:", ignoreCase = true) || value.startsWith("IP:", ignoreCase = true)
}

private val FUTABER_IP_LIKE = Regex("^[0-9*]+(\\.[0-9*]+)+.*$")

/**
 * What a word typed into NGID/IP is stored as: kept as typed when it starts with 「ID:」 or 「IP:」 (the prefix in capitals),
 * 「IP:」 before what looks like an address (106.73.*), else 「ID:」 before it. The header of a post holds `ID:…` and
 * `IP:…`, so the stored word matches it the same way the long press 「IPをNG」 does.
 */
internal fun futaberNgIdIpEntry(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return value
    if (futaberNgHeaderIsIdIp(value)) return value.substring(0, 3).uppercase() + value.substring(3).trim()
    return if (FUTABER_IP_LIKE.matches(value)) "IP:$value" else "ID:$value"
}

/** A thread typed into NGスレッドURL: the rule's board key and the thread's canonical address. */
internal data class FutaberNgThreadTarget(val scopeKey: String, val threadUrl: String)

/**
 * C33: the address typed into NGスレッドURL, when it is a thread of a board in [boards] that the shared store keeps
 * (an official board): https://may.2chan.net/b/res/123.htm, with or without https, a leading "//" or "www.".
 */
internal fun futaberNgThreadTarget(input: String, boards: List<com.valoser.futacha.shared.model.BoardSummary>): FutaberNgThreadTarget? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> "https://$trimmed"
    }
    val thread = com.valoser.futacha.shared.compat.canonicalizeSiteThreadUrlAsLegacy(withScheme) ?: return null
    val key = com.valoser.futacha.shared.compat.compatBoardKey(thread.canonicalBoardUrl)
    if (boards.none { futaberNgScopeKey(it) == key }) return null
    return FutaberNgThreadTarget(key, thread.canonicalUrl)
}

// ---- C12: EULA ----

/**
 * This app's own terms, the same as the first-launch screen (iOS `UserGeneratedContentEulaView` in `ContentView.swift`;
 * `FutaberSettingsOrderTest` checks every text is there). futaber's own terms are not used.
 */
internal const val FUTABER_EULA_TITLE = "利用規約（EULA）"
internal const val FUTABER_EULA_SUBTITLE = "ユーザー投稿コンテンツに関する同意"
internal const val FUTABER_EULA_INTRO =
    "本アプリは、外部サービス「ふたば☆ちゃんねる」のユーザー投稿コンテンツを表示し、投稿・通報を補助する専用ブラウザです。登録やログインはありませんが、コンテンツへアクセスする前に以下へ同意してください。"
internal val FUTABER_EULA_SECTIONS: List<Pair<String, String>> = listOf(
    "不適切なコンテンツ・迷惑行為を一切容認しません" to
        "違法な内容、わいせつ・暴力的な内容、差別、嫌がらせ、脅迫、個人情報の暴露、その他他者を害する投稿や行為は禁止します。利用者はこれらを投稿せず、他の利用者を攻撃・虐待しないことに同意します。",
    "通報とブロック" to
        "投稿の右上のメニューボタン「通報・ブロック」（または投稿の長押し）から操作できます。不適切な投稿は「不適切な投稿を通報」で掲示板管理者へ報告できます。迷惑な利用者は「この利用者をブロック」からID・IP・名前などを端末内のNGへ登録し、その投稿を非表示にできます。",
    "違反時の対応" to
        "違反コンテンツは掲示板管理者により削除され、悪質な利用者はサービス側で利用を制限されることがあります。本アプリでもNGフィルターを利用して表示を制限できます。",
    "お問い合わせ" to
        "安全上の問題や本アプリについては admin@valoser.com へ連絡できます。投稿そのものの削除依頼は、各投稿の通報機能を利用してください。"
)
internal const val FUTABER_EULA_FOOTER =
    "同意しない場合、本アプリでユーザー投稿コンテンツを閲覧・投稿することはできません。"

// ---- C11: 新規スレッド通知 ----

/** The note under the boards of 新規スレッド通知, written for this app (futaber's says 「通知する板は３つまで」; there is no such limit here). */
internal val futaberNewThreadNotifyNoteLines: List<String> = listOf(
    "注意",
    "・キーワードを題名に含む新しいスレッドを通知します。「すべての板」のキーワードはどの板でも探します",
    "・アプリがバックグラウンドの時に通知するには、設定の「バックグラウンド更新」をオンにしてください（端末の省電力や強制停止で遅れたり止まったりします）",
    "・通知が出ない時は、端末の設定でこのアプリの通知を許可してください",
    "・自動巡回・従量制でない回線のみ・見つかったスレッドの一覧は「キーワード通知・巡回」で設定します"
)

/** The sections of 新規スレッド通知: every board first (the rules without a board), then the boards of the shared store. */
internal data class FutaberWatchSection(val boardKey: String?, val title: String, val words: List<FutaberWatchWord>)

/** One keyword of a section and where it is in the stored rules (removing goes by it). */
internal data class FutaberWatchWord(val index: Int, val word: String, val enabled: Boolean)

internal const val FUTABER_WATCH_ALL_BOARDS = "すべての板"

internal fun futaberWatchSections(
    rules: List<com.valoser.futacha.shared.compat.CompatWatchRule>,
    boards: List<com.valoser.futacha.shared.compat.CompatBoard>
): List<FutaberWatchSection> {
    fun words(key: String?) = rules.mapIndexedNotNull { index, rule ->
        if (rule.boardKey == key) FutaberWatchWord(index, rule.word, rule.enabled) else null
    }
    val known = boards.map { it.key }.toSet()
    // A rule of a board no longer in the list stays visible (and removable) under its key.
    val orphans = rules.mapNotNull { it.boardKey }.filter { it !in known }.distinct()
    return listOf(FutaberWatchSection(null, FUTABER_WATCH_ALL_BOARDS, words(null))) +
        boards.sortedBy { it.sortOrder }.map { FutaberWatchSection(it.key, "${it.name} ${it.originalUrl}", words(it.key)) } +
        orphans.map { FutaberWatchSection(it, "削除した板", words(it)) }
}

/** The rules after adding [word] to the section of [boardKey] (a word already there for the board is not added twice). */
internal fun futaberWatchAdd(
    rules: List<com.valoser.futacha.shared.compat.CompatWatchRule>,
    boardKey: String?,
    word: String
): List<com.valoser.futacha.shared.compat.CompatWatchRule> {
    val value = word.trim().take(com.valoser.futacha.shared.compat.MAX_COMPAT_WATCH_RULE_WORD_CHARS)
    if (value.isEmpty() || rules.any { it.boardKey == boardKey && it.word == value }) return rules
    return rules + com.valoser.futacha.shared.compat.CompatWatchRule(value, boardKey)
}
