package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 設定の分類（原作の設定ルート。有料・広告とベータは移さない）。 */
/** 設定トップの原作の行（M27：名前・並び・アイコンは原作どおり。原作の「有料オプション・広告」「ご意見…」「ベータテスト…」は置かない）。 */
internal enum class FtutterSettingsPage(val title: String, val tag: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DESIGN("デザイン", "design", androidx.compose.material.icons.Icons.Rounded.Palette),
    CATALOG("カタログ", "catalog", androidx.compose.material.icons.Icons.Rounded.GridOn),
    THREAD("スレッド", "thread", androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Article),
    TAB_DRAWER("タブ・ドロワー", "tab-drawer", androidx.compose.material.icons.Icons.Rounded.Tab),
    MEDIA("画像・動画ビューワー", "media", androidx.compose.material.icons.Icons.Rounded.Image),
    NG("ピックアップ・NG管理", "ng", androidx.compose.material.icons.Icons.Rounded.Block),
    NETWORK("ネットワーク・書き込み", "network", androidx.compose.material.icons.Icons.Rounded.Wifi),
    BACKUP("バックアップ・復元", "backup", androidx.compose.material.icons.Icons.Rounded.Save),
    STATS("統計情報", "stats", androidx.compose.material.icons.Icons.Rounded.QueryStats)
}

/** 原作の「ジェスチャー」「二画面モード」の行（ネットワーク・書き込みの後ろ）。 */
internal const val FTUTTER_SETTINGS_GESTURE_TITLE = "ジェスチャー"
internal const val FTUTTER_SETTINGS_TABLET_TITLE = "二画面モード"

private val HIGH_RES_OPTIONS = listOf(0 to "低画質", 1 to "WiFi時に高画質", 2 to "高画質")

@Composable
internal fun FtutterDesignSettings(
    preferences: Map<String, String>, save: (String, String) -> Unit, onBack: () -> Unit,
    onChooseFont: (() -> Unit)? = null, onOpenColors: () -> Unit = {}
) {
    val colors = LocalFtutterColors.current
    val design = parseFtutterDesign(preferences)
    val legacy = preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)
    // 原作の並び：テーマ（メインカラー・明るさ・個別設定）／フォント／プライバシーモード／アニメーション。
    FtutterSettingsPage("デザイン", "ftutter-settings-page-design", onBack) {
        ftutterSection("テーマ")
        item {
            if (legacy) Column(Modifier.padding(16.dp)) {
                FtutterMainColorCircles(design.mainColor, size = 40, columns = 7) { save(FtutterKeys.MAIN_COLOR, it.toString()) }
            } else FtutterMainColorSetting(design.mainColor, save)
        }
        item {
            FtutterChoiceSetting("明るさ", FtutterKeys.THEME_MODE,
                listOf(0 to "ライトモード", 1 to "ダークモード", 2 to "ダークモード（＋背景真っ黒）"), 0, preferences, save, plainRows = true)
        }
        item { FtutterNavRow("個別設定", "ftutter-settings-colors", trailingArrow = true, onClick = onOpenColors) }
        ftutterSection("フォント")
        item { FtutterSliderSetting("カタログのフォントサイズ", FtutterKeys.CATALOG_FONT_SIZE, 1.0, 40.0, 1.0, 15.0, preferences, save) }
        item { FtutterSliderSetting("カタログ（数字）のフォントサイズ", FtutterKeys.CATALOG_NUMBER_FONT_SIZE, 1.0, 40.0, 1.0, 14.0, preferences, save) }
        item { FtutterSliderSetting("レスヘッダーのフォントサイズ", FtutterKeys.RESU_HEADER_FONT_SIZE, 1.0, 40.0, 1.0, 13.0, preferences, save) }
        item { FtutterSliderSetting("レス本文のフォントサイズ", FtutterKeys.RESU_CONTENT_FONT_SIZE, 1.0, 40.0, 1.0, 15.0, preferences, save) }
        // M80：原作は節の最後の「フォント指定」で、値を名前の下に出す。
        if (onChooseFont != null) item {
            val font = preferences[com.valoser.futacha.shared.ui.futaber.FUTABER_CUSTOM_FONT_PREFERENCE_KEY]
                ?.takeIf { it.isNotBlank() && it != "デフォルト" } ?: "デフォルト"
            Column(Modifier.fillMaxWidth().clickable(onClick = onChooseFont).padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-settings-font")) {
                Text("フォント指定", color = colors.text, fontSize = 15.sp)
                Text(font, color = colors.text, fontSize = 13.sp)
            }
        }
        // 取り込み元のフォントの案内は、フォントを選び直すまで（L19）。「案内を消す」でも消せる。
        preferences[FtutterKeys.PREFIX + "import.userFontPath"]?.takeIf {
            ftutterImportNoticePending(it, preferences[FTUTTER_IMPORT_FONT_CHOSEN],
                preferences[com.valoser.futacha.shared.ui.futaber.FUTABER_CUSTOM_FONT_PREFERENCE_KEY])
        }?.let { original ->
            item { Text("取り込み元のフォント: $original\nフォントファイルを選択してください。", color = colors.text,
                modifier = Modifier.padding(16.dp).testTag("ftutter-import-font-notice")) }
            item {
                androidx.compose.material3.TextButton(onClick = { save(FtutterKeys.PREFIX + "import.userFontPath", "") },
                    modifier = Modifier.testTag("ftutter-import-font-dismiss")) { Text("案内を消す", color = colors.text) }
            }
        }
        ftutterSection("プライバシーモード")
        if (legacy) FTUTTER_PRIVACY_ROWS.forEach { (label, key) ->
            item(key = key) { FtutterSliderSetting(label, key, 0.0, 1.0, 0.05, 0.8, preferences, save, integer = false) }
        } else item { FtutterPrivacySetting(preferences, save) }
        ftutterSection("アニメーション")
        item { FtutterCheckSetting("スクロール時のアニメーションを無効化", FtutterKeys.DISABLE_STRETCH_ANIMATION, true, preferences, save) }
    }
}

/** M51：原作の「色個別設定」のページ。節は 全般／カタログ／スレ、右上の「リセット」は確かめてから全部を消す。 */
@Composable
internal fun FtutterColorSettingsPage(preferences: Map<String, String>, save: (String, String) -> Unit, onBack: () -> Unit) {
    val colors = LocalFtutterColors.current
    var confirmReset by remember { mutableStateOf(false) }
    FtutterSettingsPage("色個別設定", "ftutter-settings-page-colors", onBack, actions = {
        androidx.compose.material3.Button(onClick = { confirmReset = true }, modifier = Modifier.padding(end = 12.dp).testTag("ftutter-colors-reset"),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.onToolBarActive, contentColor = colors.toolBar)) {
            Text("リセット")
        }
    }) {
        ftutterColorRows(colors).forEach { (section, rows) ->
            ftutterSection(section)
            rows.forEach { (label, pair) -> item(key = "color-${pair.first}") { FtutterColorSetting(label, pair.first, pair.second, preferences, save) } }
        }
    }
    if (confirmReset) {
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text("全ての個別設定をリセットしますか？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmReset = false
                    ftutterColorRows(colors).flatMap { it.second }.forEach { (_, pair) -> save(FtutterKeys.colorOverride(pair.first), "") }
                }, modifier = Modifier.testTag("ftutter-colors-reset-ok")) { Text("OK") }
            }
        ) }
    }
}

/** 色個別設定の節と行（原作の並び。「読み込み表示」は原作に無いので最後の「その他の機能」）。 */
internal fun ftutterColorRows(colors: FtutterColors): List<Pair<String, List<Pair<String, Pair<String, Color>>>>> = listOf(
    "全般" to listOf(
        "背景" to ("background" to colors.background), "文字" to ("text" to colors.text), "ツールバー" to ("toolBar" to colors.toolBar),
        "ツールバー上（文字、アイコン）" to ("onToolBar" to colors.onToolBar), "ツールバー上（アイコン有効時）" to ("onToolBarActive" to colors.onToolBarActive),
        "ポップアップ" to ("popUp" to colors.popUp), "ハイライト" to ("highlight" to colors.highlight),
        "ここまで読んだ、見出し" to ("dividerCard" to colors.dividerCard), "ここまで読んだ、見出し（文字）" to ("onDividerCard" to colors.onDividerCard),
        "ジェスチャー" to ("gesture" to colors.gesture), "スクロールバー" to ("scrollBar" to colors.scrollBar)
    ),
    "カタログ" to listOf(
        "カタログ（タブ内時）" to ("inTab" to colors.inTab), "枠線" to ("outline" to colors.outline), "既読" to ("kidoku" to colors.kidoku),
        "レス数（背景）" to ("resuSuu" to colors.resuSuu), "レス数（文字）" to ("onResuSuu" to colors.onResuSuu),
        "未読数（背景）" to ("midoku" to colors.midoku), "未読数（文字）" to ("onMidoku" to colors.onMidoku),
        "ピックアップ" to ("pickUp" to colors.pickUp), "ピックアップ（文字）" to ("onPickUp" to colors.onPickUp)
    ),
    "スレ" to listOf(
        "ヘッダー" to ("headerText" to colors.headerText), "書き込み" to ("kakikomi" to colors.kakikomi), "返信" to ("henshin" to colors.henshin)
    ),
    "その他の機能" to listOf("読み込み表示" to ("indicator" to colors.indicator))
)

@Composable
internal fun FtutterCatalogSettingsPage(preferences: Map<String, String>, save: (String, String) -> Unit, onEditToolbar: () -> Unit, onBack: () -> Unit) {
    FtutterSettingsPage("カタログ", "ftutter-settings-page-catalog", onBack) {
        ftutterSection("表示")
        item { FtutterSliderSetting("カタログの列数（縦画面）", FtutterKeys.CATALOG_ROW, 1.0, 20.0, 1.0, 5.0, preferences, save) }
        item { FtutterSliderSetting("カタログの列数（横画面）", FtutterKeys.CATALOG_ROW_LANDSCAPE, 1.0, 20.0, 1.0, 8.0, preferences, save) }
        item { FtutterSliderSetting("一度に表示するスレッドの数", FtutterKeys.MAX_CATALOG_THREAD_COUNT, 50.0, 2000.0, 50.0, 500.0, preferences, save) }
        item { FtutterCheckSetting("グリッドの高さを揃える", FtutterKeys.SAME_HEIGHT_GRID, true, preferences, save) }
        item {
            FtutterChoiceSetting("デフォルトの並び順", FtutterKeys.DEFAULT_ORDER_MODE,
                FtutterOrder.entries.map { it.stored to it.label } + (FtutterOrder.PREVIOUS to "前回の並び順を保存する"), FtutterOrder.PREVIOUS, preferences, save,
                dialogOptions = FtutterOrder.entries.map { it.stored to it.dialogLabel } + (FtutterOrder.PREVIOUS to "前回の並び順を保存する"))
        }
        item { FtutterChoiceSetting("サムネイルの画質設定", FtutterKeys.CATALOG_HIGH_RES, HIGH_RES_OPTIONS, 1, preferences, save) }
        item { FtutterSliderSetting("サムネイルの最大高さ", FtutterKeys.MAX_THUMB_HEIGHT, 1.0, 5.0, 0.1, 1.2, preferences, save, integer = false) }
        item { FtutterSliderSetting("サムネイルの最小高さ", FtutterKeys.MIN_THUMB_HEIGHT, 0.0, 1.0, 0.1, 0.8, preferences, save, integer = false) }
        item { FtutterCheckSetting("長押しで拡大表示する", FtutterKeys.USE_ZOOM, true, preferences, save) }
        item {
            FtutterChoiceSetting("レス数・未読数の表示", FtutterKeys.NUMBER_ON_THUMB,
                listOf(0 to "サムネイルの上", 1 to "サムネイルに重ねる", 2 to "サムネイルの下", 3 to "スレッドタイトルの下", 4 to "表示しない"), 1, preferences, save)
        }
        item { FtutterCheckSetting("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!を短くする", FtutterKeys.KITA_SHORTEN, true, preferences, save) }
        item { FtutterSliderSetting("タイトルの行数", FtutterKeys.MAX_TITLE_LINES, 0.0, 10.0, 1.0, 2.0, preferences, save) }
        item { FtutterCheckSetting("文字数を制限する", FtutterKeys.LIMIT_TITLE_LETTERS, false, preferences, save) }
        item { FtutterSliderSetting("文字数", FtutterKeys.MAX_TITLE_LETTERS, 0.0, 20.0, 1.0, 4.0, preferences, save) }
        // M79・M80：原作の節（スクロールバー／引っ張って更新／ツールバー編集）。寸法はツールバー編集の画面の中。
        ftutterSection("スクロールバー")
        item { FtutterCheckSetting("スクロールバーを使用する", FtutterKeys.CATALOG_SCROLLBAR, true, preferences, save) }
        item { FtutterSliderSetting("スクロールバーの幅", FtutterKeys.CATALOG_SCROLLBAR_THICKNESS, 1.0, 100.0, 1.0, 8.0, preferences, save) }
        item { FtutterCheckSetting("スクロールバーを常に表示する", FtutterKeys.CATALOG_SCROLLBAR_ALWAYS, false, preferences, save) }
        item { FtutterCheckSetting("スクロールバーを左側に表示する", FtutterKeys.CATALOG_SCROLLBAR_LEFT, false, preferences, save) }
        ftutterSection("引っ張って更新")
        item { FtutterCheckSetting("カタログを引っ張って更新（上部）", FtutterKeys.CATALOG_PULL_TOP, true, preferences, save) }
        item { FtutterCheckSetting("カタログを引っ張って更新（下部）", FtutterKeys.CATALOG_PULL_BOTTOM, true, preferences, save) }
        ftutterSection("ツールバー編集")
        item { FtutterNavRow("ツールバー編集", "ftutter-settings-catalog-toolbar", onClick = onEditToolbar) }
        if (preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)) {
            item { FtutterSliderSetting("ボタンの幅", FtutterKeys.CATALOG_ICON_WIDTH, 40.0, 80.0, 1.0, 45.0, preferences, save) }
            item { FtutterSliderSetting("ボタンの高さ", FtutterKeys.CATALOG_ICON_HEIGHT, 30.0, 80.0, 1.0, 45.0, preferences, save) }
            item { FtutterSliderSetting("スペース（左端）", FtutterKeys.CATALOG_TOOLBAR_LEFT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
            item { FtutterSliderSetting("スペース（右端）", FtutterKeys.CATALOG_TOOLBAR_RIGHT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
        }
    }
}

@Composable
internal fun FtutterThreadSettingsPage(
    preferences: Map<String, String>, save: (String, String) -> Unit, onEditToolbar: () -> Unit, onEditPostMenu: () -> Unit, onBack: () -> Unit
) {
    FtutterSettingsPage("スレッド", "ftutter-settings-page-thread", onBack) {
        // M52・M80：原作の並びと名前（スレッド／レス／画像／あぷ小／スクロールバー／引っ張って更新／更新時の表示／人気レスの閾値／履歴）。
        val legacy = preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)
        ftutterSection("スレッド")
        item { FtutterCheckSetting("削除されたレスを常に表示する", FtutterKeys.SHOW_DELETED, false, preferences, save) }
        item { FtutterCheckSetting("連鎖削除", FtutterKeys.DELETED_CHAIN, false, preferences, save, note = "削除されたレスを引用するレスを全て連鎖非表示にする") }
        item { FtutterCheckSetting("引用省略", FtutterKeys.QUOTE_SHORTEN, false, preferences, save, note = "ツリー時に単純な引用を省略して表示する") }
        ftutterSection("レス")
        if (legacy) {
            FTUTTER_POST_LAYOUT_ROWS.forEach { row ->
                val label = if (row.dialogLabel == row.rowLabel) "余白（${row.rowLabel}）" else row.rowLabel
                item(key = row.key) { FtutterSliderSetting(label, row.key, 0.0, 30.0, 1.0, row.default.toDouble(), preferences, save) }
            }
        } else {
            item(key = "post-layout") { FtutterPostLayoutSetting(preferences, save) }
        }
        ftutterSection("画像")
        item { FtutterChoiceSetting("スレ画の画質設定", FtutterKeys.OP_IMAGE_HIGH_RES, HIGH_RES_OPTIONS, 1, preferences, save) }
        item { FtutterSliderSetting("スレ画の最大高さ", FtutterKeys.OP_IMAGE_MAX_HEIGHT, 0.0, 1000.0, 20.0, 500.0, preferences, save) }
        item { FtutterChoiceSetting("レス画像の画質設定", FtutterKeys.RES_IMAGE_HIGH_RES, HIGH_RES_OPTIONS, 0, preferences, save) }
        item {
            FtutterChoiceSetting("レス画像の位置", FtutterKeys.RES_IMAGE_POSITION,
                listOf(0 to "レスの上", 1 to "レスの左", 2 to "レスの右", 3 to "レスの下"), 3, preferences, save)
        }
        item { FtutterSliderSetting("レス画像の最大高さ", FtutterKeys.RES_IMAGE_MAX_HEIGHT, 0.0, 1000.0, 20.0, 300.0, preferences, save) }
        item { FtutterSliderSetting("レス画像の最大幅", FtutterKeys.RES_IMAGE_MAX_WIDTH, 0.0, 1000.0, 20.0, 200.0, preferences, save) }
        item { FtutterCheckSetting("レス画像の幅を常に固定する", FtutterKeys.RES_IMAGE_FIXED_WIDTH, false, preferences, save) }
        ftutterSection("あぷ小")
        item {
            FtutterChoiceSetting("あぷ小の画像読み込み", FtutterKeys.APU_AUTO_LOAD, listOf(0 to "手動", 1 to "WiFi時に自動", 2 to "自動"), 1, preferences, save)
        }
        item { FtutterSliderSetting("あぷ小のサムネイルサイズ", FtutterKeys.APU_THUMB_SIZE, 1.0, 1000.0, 1.0, 120.0, preferences, save) }
        ftutterSection("スクロールバー")
        item { FtutterCheckSetting("スクロールバーを使用する", FtutterKeys.THREAD_SCROLLBAR, true, preferences, save) }
        item { FtutterSliderSetting("スクロールバーの幅", FtutterKeys.THREAD_SCROLLBAR_THICKNESS, 1.0, 100.0, 1.0, 8.0, preferences, save) }
        item { FtutterCheckSetting("スクロールバーを常に表示する", FtutterKeys.THREAD_SCROLLBAR_ALWAYS, false, preferences, save) }
        item { FtutterCheckSetting("スクロールバーを左側に表示する", FtutterKeys.THREAD_SCROLLBAR_LEFT, false, preferences, save) }
        ftutterSection("引っ張って更新")
        item { FtutterCheckSetting("スレを引っ張って更新（上部）", FtutterKeys.THREAD_PULL_TOP, false, preferences, save) }
        item { FtutterCheckSetting("スレを引っ張って更新（下部）", FtutterKeys.THREAD_PULL_BOTTOM, true, preferences, save) }
        ftutterSection("更新時の表示")
        item { FtutterCheckSetting("更新時に新着レスの件数を表示する", FtutterKeys.SHOW_NEW_COUNT, false, preferences, save) }
        item { FtutterCheckSetting("更新時に返信の件数を表示する", FtutterKeys.SHOW_REPLY_COUNT, true, preferences, save) }
        ftutterSection("人気レスの閾値")
        item { FtutterSliderSetting("返信の数", FtutterKeys.POPULAR_REPLIES, 1.0, 10.0, 1.0, 3.0, preferences, save) }
        item { FtutterSliderSetting("そうだねの数", FtutterKeys.POPULAR_SOUDANE, 0.0, 100.0, 5.0, 10.0, preferences, save) }
        ftutterSection("履歴")
        item {
            // 下げてタブが閉じられる時は、件数を示して確かめる（L130：以前は確認なしで閉じられ、戻せなかった）。
            FtutterSliderSetting("履歴の保存数", FtutterKeys.HISTORY_LIMIT, 0.0, 1000.0, 10.0, 200.0, preferences, save,
                note = "このモードの履歴とタブを、保護したスレッドを除いて最近開いた順にこの数まで残します。超えた古いタブ（保護していないもの）は閉じます。他のモードの履歴は残します。",
                confirm = { limit ->
                    ftutterTabsClosedByLimit(parseFtutterTabs(preferences), parseFtutterProtected(preferences), limit.toInt())
                        .takeIf { it > 0 }?.let { "保護していないタブが${it}件閉じられます。閉じたタブは戻せません。" }
                })
        }
        ftutterSection("ツールバー・メニュー編集")
        item { FtutterNavRow("ツールバー編集", "ftutter-settings-thread-toolbar", onClick = onEditToolbar) }
        item { FtutterNavRow("レス長押しメニュー編集", "ftutter-settings-post-menu", onClick = onEditPostMenu) }
        if (legacy) {
            item { FtutterSliderSetting("ボタンの幅", FtutterKeys.THREAD_ICON_WIDTH, 40.0, 80.0, 1.0, 45.0, preferences, save) }
            item { FtutterSliderSetting("ボタンの高さ", FtutterKeys.THREAD_ICON_HEIGHT, 30.0, 80.0, 1.0, 45.0, preferences, save) }
            item { FtutterSliderSetting("スペース（左端）", FtutterKeys.THREAD_TOOLBAR_LEFT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
            item { FtutterSliderSetting("スペース（右端）", FtutterKeys.THREAD_TOOLBAR_RIGHT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
        }
    }
}

@Composable
internal fun FtutterTabDrawerSettingsPage(preferences: Map<String, String>, save: (String, String) -> Unit, onBack: () -> Unit) {
    FtutterSettingsPage("タブ・ドロワー", "ftutter-settings-page-tab-drawer", onBack) {
        ftutterSection("タブ")
        item { FtutterChoiceSetting("タブの位置", FtutterKeys.TAB_ON_TOP, listOf(1 to "上", 0 to "下"), 0, preferences.boolAsInt(FtutterKeys.TAB_ON_TOP), boolSaver(save)) }
        item { FtutterSliderSetting("タブの高さ", FtutterKeys.TAB_HEIGHT, 10.0, 200.0, 10.0, 40.0, preferences, save) }
        item { FtutterSliderSetting("タブの幅", FtutterKeys.TAB_WIDTH, 10.0, 200.0, 10.0, 60.0, preferences, save) }
        item { FtutterCheckSetting("スワイプで隣のタブへ移動する", FtutterKeys.MOVE_TAB_BY_SWIPE, true, preferences, save, note = "※ジェスチャー無効時") }
        item {
            FtutterCheckSetting("タブ移動時に更新する（カタログ）", FtutterKeys.REFRESH_ON_TAB_MOVE_CATALOG, false, preferences, save)
        }
        item { FtutterCheckSetting("タブ移動時に更新する（スレ）", FtutterKeys.REFRESH_ON_TAB_MOVE_THREAD, false, preferences, save) }
        item { FtutterCheckSetting("タブのドラッグを無効化", FtutterKeys.DISABLE_TAB_DRAG, false, preferences, save) }
        item { FtutterCheckSetting("タブがない時に自動でカタログまで戻る", FtutterKeys.BACK_WHEN_NO_TAB, true, preferences, save) }
        ftutterSection("ドロワー")
        item {
            FtutterChoiceSetting("ドロワーを出す場所", FtutterKeys.DRAWER_SIDE, listOf(0 to "左", 1 to "右", 2 to "両方", 3 to "無効"), 0, preferences, save)
        }
        item { FtutterCheckSetting("端を引っ張ってドロワーを開く", FtutterKeys.DRAWER_OPEN_BY_DRAG, true, preferences, save) }
        item {
            FtutterChoiceSetting("タブをスワイプで閉じる方向", FtutterKeys.TAB_DISMISS_DIRECTION,
                FtutterDismissDirection.entries.map { it.stored to it.label }, 0, preferences, save)
        }
        item { FtutterCheckSetting("左上にドロワーを開くボタンを表示", FtutterKeys.DRAWER_BUTTON_TOP_LEFT, false, preferences, save) }
    }
}

@Composable
internal fun FtutterMediaSettingsPage(preferences: Map<String, String>, save: (String, String) -> Unit, onBack: () -> Unit) {
    // M80：原作の節（画像ビューワー／動画ビューワー／タブ／あぷ小）と名前。
    FtutterSettingsPage(FtutterSettingsPage.MEDIA.title, "ftutter-settings-page-media", onBack) {
        ftutterSection("画像ビューワー")
        item { FtutterCheckSetting("スレ内の画像を全て繋げて表示する", FtutterKeys.CONNECT_ALL_IMAGES, true, preferences, save) }
        item { FtutterCheckSetting("削除されたレスの画像を一覧に表示しない", FtutterKeys.EXCLUDE_DELETED_IMAGES, true, preferences, save) }
        ftutterSection("動画ビューワー")
        item {
            FtutterCheckSetting("動画を自動再生する", FtutterKeys.VIDEO_AUTO_PLAY, false, preferences, save)
        }
        item { FtutterCheckSetting("デフォルトでループ再生", FtutterKeys.VIDEO_LOOP, false, preferences, save) }
        ftutterSection("タブ")
        item { FtutterSliderSetting("ビューワー内のタブの大きさ", FtutterKeys.GALLERY_TAB_SIZE, 20.0, 300.0, 10.0, 80.0, preferences, save) }
        ftutterSection("あぷ小")
        item { FtutterCheckSetting("サムネイルの画質を下げる", FtutterKeys.USE_MEM_SIZE, true, preferences, save, note = "あぷ小のサムネイルを低画質にしてメモリを節約します") }
        item { FtutterSliderSetting("サムネイルのサイズ", FtutterKeys.MEM_SIZE, 1.0, 4096.0, 1.0, 200.0, preferences, save) }
        // こちらだけの行（M81）。
        ftutterSection(FTUTTER_MORE_LABEL)
        item { FtutterCheckSetting("画像検索にascii2dを使用する", FtutterKeys.IMAGE_SEARCH_ASCII2D, false, preferences, save) }
    }
}

@Composable
internal fun FtutterNgSettingsPage(
    preferences: Map<String, String>, save: (String, String) -> Unit, ngCount: Int, hiddenCount: Int,
    onOpenNgList: () -> Unit, onOpenHidden: () -> Unit, onBack: () -> Unit,
    /** M56：原作の種類ごとの頁（行に件数「N 個」）。null なら従来の1つの一覧だけ。 */
    ngCounts: Map<FtutterNgType, Int>? = null,
    onOpenNgType: ((FtutterNgType) -> Unit)? = null
) {
    val pickupOrders: @Composable () -> Unit = {
        val orders = FtutterOrder.entries
        val selected = parseFtutterPickupOrders(preferences[FtutterKeys.PICKUP_ORDER_MODES])
        FtutterMultiChoiceSetting(
            "ピックアップを行うカタログの並び順", "ftutter-settings-pickup-orders",
            orders.map { it.label }, orders.indices.filter { orders[it] in selected }.toSet()
        ) { chosen -> save(FtutterKeys.PICKUP_ORDER_MODES, encodeFtutterPickupOrders(chosen.map { orders[it] }.toSet())) }
    }
    FtutterSettingsPage(FtutterSettingsPage.NG.title, "ftutter-settings-page-ng", onBack) {
        if (ngCounts == null || onOpenNgType == null || preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)) {
            item { FtutterNavRow("NG・ピックアップの一覧", "ftutter-settings-ng-list", detail = "$ngCount", onClick = onOpenNgList) }
            item { FtutterNavRow("非表示にしたスレ", "ftutter-settings-hidden", detail = "$hiddenCount", onClick = onOpenHidden) }
            item { FtutterCheckSetting("連鎖NG", FtutterKeys.NG_CHAIN, false, preferences, save) }
            item { pickupOrders() }
            return@FtutterSettingsPage
        }
        // 原作の節（M56）：NG／NGの設定／ピックアップ／ピックアップの設定。
        val row: (FtutterNgType) -> Unit = { type ->
            item(key = "ng-${type.name}") {
                FtutterNavRow(type.label, "ftutter-settings-ng-${type.name}", detail = "${ngCounts[type] ?: 0} 個") { onOpenNgType(type) }
            }
        }
        ftutterSection("NG")
        row(FtutterNgType.TITLE); row(FtutterNgType.WORD); row(FtutterNgType.ID)
        ftutterSection("NGの設定")
        item { FtutterCheckSetting("連鎖NG", FtutterKeys.NG_CHAIN, false, preferences, save, note = "NGされたレスを引用するレスを全て連鎖非表示にする") }
        ftutterSection("ピックアップ")
        row(FtutterNgType.PICKUP)
        ftutterSection("ピックアップの設定")
        item { pickupOrders() }
        // こちらだけの行（M81）。
        ftutterSection(FTUTTER_MORE_LABEL)
        item { FtutterNavRow("非表示にしたスレ", "ftutter-settings-hidden", detail = "$hiddenCount", onClick = onOpenHidden) }
        item { FtutterNavRow("NG・ピックアップの一覧（まとめて見る）", "ftutter-settings-ng-list", detail = "$ngCount", onClick = onOpenNgList) }
    }
}

@Composable
internal fun FtutterNetworkSettingsPage(
    preferences: Map<String, String>, save: (String, String) -> Unit, deleteKey: String,
    onOpenCookies: (() -> Unit)?, onOpenSharedNetwork: (() -> Unit)?, onBack: () -> Unit,
    /** M77：原作の「クッキーの再取得」（確かめた後に呼ぶ）。Cookieを扱えない時は null で行を出さない。 */
    onReacquireCookies: (() -> Unit)? = null
) {
    var keyOpen by remember { mutableStateOf(false) }
    var cookieConfirm by remember { mutableStateOf(false) }
    // M48：原作は設定の行でも削除キーを伏せずに見せる。従来の書き込みのダイアログの時は伏せたまま。
    val keyDetail = if (ftutterLegacyPostDialog(preferences)) "●".repeat(deleteKey.length) else deleteKey
    FtutterSettingsPage(FtutterSettingsPage.NETWORK.title, "ftutter-settings-page-network", onBack) {
        // 原作の節（M77）：ネットワーク／書き込み。原作の「https版のサイトにアクセスする」は置かない（常にHTTPS、その他の機能に書く）。
        ftutterSection("ネットワーク")
        item { FtutterCheckSetting("URLを開く際アプリ内ブラウザを使用する", FtutterKeys.USE_CUSTOM_TAB, true, preferences, save) }
        ftutterSection("書き込み")
        item { FtutterCheckSetting("書き込み前に確認する", FtutterKeys.CONFIRM_BEFORE_POST, false, preferences, save) }
        item { FtutterNavRow("削除キー", "ftutter-settings-delete-key", detail = keyDetail) { keyOpen = true } }
        onReacquireCookies?.let { item { FtutterNavRow("クッキーの再取得", "ftutter-settings-cookie-reacquire") { cookieConfirm = true } } }
        // こちらだけの行（M81）。
        ftutterSection(FTUTTER_MORE_LABEL)
        item { Text("ふたばへの通信には常にHTTPSを使用します", color = LocalFtutterColors.current.text, modifier = Modifier.padding(16.dp)) }
        onOpenCookies?.let { open -> item { FtutterNavRow("クッキーの管理", "ftutter-settings-cookies", onClick = open) } }
        onOpenSharedNetwork?.let { open ->
            item { FtutterNavRow("通信の設定（他のモードと共有）", "ftutter-settings-shared-network", onClick = open) }
        }
    }
    if (cookieConfirm) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { cookieConfirm = false },
            text = { Text(FTUTTER_COOKIE_REACQUIRE_TEXT) },
            confirmButton = {
                TextButton(onClick = { cookieConfirm = false; onReacquireCookies?.invoke() }, modifier = Modifier.testTag("ftutter-cookie-reacquire-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { cookieConfirm = false }) { Text("キャンセル") } }
        ) }
    }
    if (keyOpen) {
        FtutterDeleteKeyDialog(deleteKey, original = !ftutterLegacyPostDialog(preferences), onDismiss = { keyOpen = false }) { keyOpen = false; save(FtutterKeys.DELETE_KEY, it) }
    }
}

@Composable
internal fun FtutterStatsPage(preferences: Map<String, String>, appVersion: String, onBack: () -> Unit) {
    val colors = LocalFtutterColors.current
    val stats = parseFtutterStats(preferences)
    FtutterSettingsPage(FtutterSettingsPage.STATS.title, "ftutter-settings-page-stats", onBack) {
        item {
            listOf(
                "バージョン" to appVersion,
                "開いたスレ" to stats.opened.toString(),
                "スレ立て" to stats.created.toString(),
                "レス" to stats.replied.toString(),
                "そうだね" to stats.soudane.toString()
            ).forEach { (label, value) ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(value, color = colors.text, fontSize = 15.sp, modifier = Modifier.testTag("ftutter-stats-$label"))
                }
                HorizontalDivider(color = colors.outline)
            }
            Text("端末の中だけに数えます（外へは送りません）", color = colors.text, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }
    }
}

/** 真偽の設定を選択肢（0/1）として見せるため。 */
private fun Map<String, String>.boolAsInt(key: String): Map<String, String> =
    this + (key to if (ftutterBool(key, false)) "1" else "0")

private fun boolSaver(save: (String, String) -> Unit): (String, String) -> Unit = { key, value -> save(key, (value == "1").toString()) }

