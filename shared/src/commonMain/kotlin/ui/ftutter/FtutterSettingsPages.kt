package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 設定の分類（原作の設定ルート。有料・広告とベータは移さない）。 */
internal enum class FtutterSettingsPage(val title: String, val tag: String) {
    DESIGN("デザイン", "design"),
    CATALOG("カタログ", "catalog"),
    THREAD("スレッド", "thread"),
    TAB_DRAWER("タブ・ドロワー", "tab-drawer"),
    MEDIA("画像・動画", "media"),
    NG("ピックアップ・NG", "ng"),
    NETWORK("ネットワーク・投稿", "network"),
    BACKUP("バックアップ・復元", "backup"),
    STATS("統計", "stats")
}

private val HIGH_RES_OPTIONS = listOf(0 to "低画質", 1 to "WiFi時に高画質", 2 to "高画質")

@Composable
internal fun FtutterDesignSettings(preferences: Map<String, String>, save: (String, String) -> Unit, onBack: () -> Unit) {
    val colors = LocalFtutterColors.current
    val design = parseFtutterDesign(preferences)
    FtutterSettingsPage("デザイン", "ftutter-settings-page-design", onBack) {
        ftutterSection("メインカラー")
        item {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FTUTTER_SEED_COLORS.forEach { argb ->
                    val selected = argb == design.mainColor
                    Box(
                        Modifier.size(40.dp).background(Color(argb.toInt()), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) colors.text else colors.outline, CircleShape)
                            .clickable { save(FtutterKeys.MAIN_COLOR, argb.toString()) }
                            .testTag("ftutter-main-color-${argb.toString(16)}")
                    )
                }
            }
        }
        item {
            FtutterChoiceSetting("明るさ", FtutterKeys.THEME_MODE,
                listOf(0 to "ライトモード", 1 to "ダークモード", 2 to "ダークモード（背景真っ黒）"), 0, preferences, save)
        }
        ftutterSection("フォント")
        item { FtutterSliderSetting("カタログのフォントサイズ", FtutterKeys.CATALOG_FONT_SIZE, 1.0, 40.0, 1.0, 15.0, preferences, save) }
        item { FtutterSliderSetting("カタログ（数字）のフォントサイズ", FtutterKeys.CATALOG_NUMBER_FONT_SIZE, 1.0, 40.0, 1.0, 14.0, preferences, save) }
        item { FtutterSliderSetting("レスヘッダーのフォントサイズ", FtutterKeys.RESU_HEADER_FONT_SIZE, 1.0, 40.0, 1.0, 13.0, preferences, save) }
        item { FtutterSliderSetting("レス本文のフォントサイズ", FtutterKeys.RESU_CONTENT_FONT_SIZE, 1.0, 40.0, 1.0, 15.0, preferences, save) }
        ftutterSection("画像の透過度（プライバシーモード）")
        item { FtutterSliderSetting("カタログ", FtutterKeys.PRIVACY_CATALOG, 0.0, 1.0, 0.05, 0.8, preferences, save, integer = false) }
        item { FtutterSliderSetting("カタログ（長押し拡大時）", FtutterKeys.PRIVACY_CATALOG_ZOOM, 0.0, 1.0, 0.05, 0.8, preferences, save, integer = false) }
        item { FtutterSliderSetting("スレッド", FtutterKeys.PRIVACY_THREAD, 0.0, 1.0, 0.05, 0.8, preferences, save, integer = false) }
        item { FtutterSliderSetting("スレッド（拡大時）", FtutterKeys.PRIVACY_THREAD_ZOOM, 0.0, 1.0, 0.05, 0.8, preferences, save, integer = false) }
        item { FtutterSliderSetting("タブ、ドロワー、他", FtutterKeys.PRIVACY_TAB_DRAWER, 0.0, 1.0, 0.05, 0.8, preferences, save, integer = false) }
        item { FtutterCheckSetting("スクロール時のアニメーションを無効化", FtutterKeys.DISABLE_STRETCH_ANIMATION, true, preferences, save) }
        ftutterSection("色個別設定")
        val rows = listOf(
            "背景" to ("background" to colors.background), "文字" to ("text" to colors.text), "ツールバー" to ("toolBar" to colors.toolBar),
            "ツールバー上（文字、アイコン）" to ("onToolBar" to colors.onToolBar), "ツールバー上（アイコン有効時）" to ("onToolBarActive" to colors.onToolBarActive),
            "ポップアップ" to ("popUp" to colors.popUp), "ハイライト" to ("highlight" to colors.highlight),
            "ここまで読んだ、見出し" to ("dividerCard" to colors.dividerCard), "ここまで読んだ、見出し（文字）" to ("onDividerCard" to colors.onDividerCard),
            "カタログ（タブ内時）" to ("inTab" to colors.inTab), "枠線" to ("outline" to colors.outline), "既読" to ("kidoku" to colors.kidoku),
            "未読数（背景）" to ("midoku" to colors.midoku), "未読数（文字）" to ("onMidoku" to colors.onMidoku), "ヘッダー" to ("headerText" to colors.headerText),
            "レス数（背景）" to ("resuSuu" to colors.resuSuu), "レス数（文字）" to ("onResuSuu" to colors.onResuSuu),
            "ピックアップ" to ("pickUp" to colors.pickUp), "ピックアップ（文字）" to ("onPickUp" to colors.onPickUp),
            "書き込み" to ("kakikomi" to colors.kakikomi), "返信" to ("henshin" to colors.henshin)
        )
        rows.forEach { (label, pair) -> item(key = "color-${pair.first}") { FtutterColorSetting(label, pair.first, pair.second, preferences, save) } }
    }
}

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
                FtutterOrder.entries.map { it.stored to it.label } + (FtutterOrder.PREVIOUS to "前回の並び順を保存する"), FtutterOrder.PREVIOUS, preferences, save)
        }
        item { FtutterChoiceSetting("サムネイルの画質設定", FtutterKeys.CATALOG_HIGH_RES, HIGH_RES_OPTIONS, 1, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
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
        ftutterSection("スクロール・更新")
        item { FtutterCheckSetting("スクロールバーを使用する", FtutterKeys.CATALOG_SCROLLBAR, true, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
        item { FtutterCheckSetting("カタログを引っ張って更新（上部）", FtutterKeys.CATALOG_PULL_TOP, true, preferences, save) }
        item { FtutterCheckSetting("カタログを引っ張って更新（下部）", FtutterKeys.CATALOG_PULL_BOTTOM, true, preferences, save) }
        ftutterSection("ツールバー")
        item { FtutterNavRow("ボタンの並び", "ftutter-settings-catalog-toolbar", onClick = onEditToolbar) }
        item { FtutterSliderSetting("ボタンの幅", FtutterKeys.CATALOG_ICON_WIDTH, 40.0, 80.0, 1.0, 45.0, preferences, save) }
        item { FtutterSliderSetting("ボタンの高さ", FtutterKeys.CATALOG_ICON_HEIGHT, 30.0, 80.0, 1.0, 45.0, preferences, save) }
        item { FtutterSliderSetting("スペース（左端）", FtutterKeys.CATALOG_TOOLBAR_LEFT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
        item { FtutterSliderSetting("スペース（右端）", FtutterKeys.CATALOG_TOOLBAR_RIGHT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
    }
}

@Composable
internal fun FtutterThreadSettingsPage(
    preferences: Map<String, String>, save: (String, String) -> Unit, onEditToolbar: () -> Unit, onEditPostMenu: () -> Unit, onBack: () -> Unit
) {
    FtutterSettingsPage("スレッド", "ftutter-settings-page-thread", onBack) {
        ftutterSection("レス")
        item { FtutterCheckSetting("削除されたレスを常に表示する", FtutterKeys.SHOW_DELETED, false, preferences, save) }
        item { FtutterCheckSetting("連鎖削除", FtutterKeys.DELETED_CHAIN, false, preferences, save) }
        item { FtutterCheckSetting("引用省略（ツリー時に単純な引用を省略）", FtutterKeys.QUOTE_SHORTEN, false, preferences, save) }
        ftutterSection("レスのレイアウト")
        listOf(
            "余白（上）" to FtutterKeys.PADDING_TOP to 5.0, "余白（左）" to FtutterKeys.PADDING_LEFT to 5.0,
            "余白（右）" to FtutterKeys.PADDING_RIGHT to 5.0, "余白（下）" to FtutterKeys.PADDING_BOTTOM to 5.0,
            "ヘッダーと本文の間" to FtutterKeys.PADDING_HEADER_TEXT to 3.0, "本文と画像の間" to FtutterKeys.PADDING_TEXT_FILE to 8.0
        ).forEach { (pair, default) ->
            item(key = pair.second) { FtutterSliderSetting(pair.first, pair.second, 0.0, 30.0, 1.0, default, preferences, save) }
        }
        ftutterSection("画像")
        item { FtutterChoiceSetting("スレ画の画質設定", FtutterKeys.OP_IMAGE_HIGH_RES, HIGH_RES_OPTIONS, 1, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
        item { FtutterSliderSetting("スレ画の最大高さ", FtutterKeys.OP_IMAGE_MAX_HEIGHT, 0.0, 1000.0, 20.0, 500.0, preferences, save) }
        item { FtutterChoiceSetting("レス画像の画質設定", FtutterKeys.RES_IMAGE_HIGH_RES, HIGH_RES_OPTIONS, 0, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
        item {
            FtutterChoiceSetting("レス画像の位置", FtutterKeys.RES_IMAGE_POSITION,
                listOf(0 to "レスの上", 1 to "レスの左", 2 to "レスの右", 3 to "レスの下"), 3, preferences, save)
        }
        item { FtutterSliderSetting("レス画像の最大高さ", FtutterKeys.RES_IMAGE_MAX_HEIGHT, 0.0, 1000.0, 20.0, 300.0, preferences, save) }
        item { FtutterSliderSetting("レス画像の最大幅", FtutterKeys.RES_IMAGE_MAX_WIDTH, 0.0, 1000.0, 20.0, 200.0, preferences, save) }
        item { FtutterCheckSetting("レス画像の幅を常に固定する", FtutterKeys.RES_IMAGE_FIXED_WIDTH, false, preferences, save) }
        item {
            FtutterChoiceSetting("あぷ小の画像読み込み", FtutterKeys.APU_AUTO_LOAD, listOf(0 to "手動", 1 to "WiFi時に自動", 2 to "自動"), 1, preferences, save,
                note = FTUTTER_NOT_USED_NOTE)
        }
        ftutterSection("スクロール・更新")
        item { FtutterCheckSetting("スクロールバーを使用する", FtutterKeys.THREAD_SCROLLBAR, true, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
        item { FtutterCheckSetting("スレを引っ張って更新（上部）", FtutterKeys.THREAD_PULL_TOP, false, preferences, save) }
        item { FtutterCheckSetting("スレを引っ張って更新（下部）", FtutterKeys.THREAD_PULL_BOTTOM, true, preferences, save) }
        item { FtutterCheckSetting("更新時に新着レスの件数を表示する", FtutterKeys.SHOW_NEW_COUNT, false, preferences, save) }
        item { FtutterCheckSetting("更新時に返信の件数を表示する", FtutterKeys.SHOW_REPLY_COUNT, true, preferences, save) }
        ftutterSection("人気レス")
        item { FtutterSliderSetting("返信の数", FtutterKeys.POPULAR_REPLIES, 1.0, 10.0, 1.0, 3.0, preferences, save) }
        item { FtutterSliderSetting("そうだねの数", FtutterKeys.POPULAR_SOUDANE, 0.0, 100.0, 5.0, 10.0, preferences, save) }
        ftutterSection("履歴")
        item {
            FtutterSliderSetting("履歴の保存数（保護したタブは除く）", FtutterKeys.HISTORY_LIMIT, 0.0, 1000.0, 10.0, 200.0, preferences, save,
                note = "保護していないタブを、最近開いたものからこの数まで残します（起動時は履歴に無いタブも閉じます）。履歴そのものは他のモードと共有で、件数の上限は共有の側で決まります")
        }
        ftutterSection("ツールバー・メニュー")
        item { FtutterNavRow("ボタンの並び", "ftutter-settings-thread-toolbar", onClick = onEditToolbar) }
        item { FtutterNavRow("レス長押しメニューの並び", "ftutter-settings-post-menu", onClick = onEditPostMenu) }
        item { FtutterSliderSetting("ボタンの幅", FtutterKeys.THREAD_ICON_WIDTH, 40.0, 80.0, 1.0, 45.0, preferences, save) }
        item { FtutterSliderSetting("ボタンの高さ", FtutterKeys.THREAD_ICON_HEIGHT, 30.0, 80.0, 1.0, 45.0, preferences, save) }
        item { FtutterSliderSetting("スペース（左端）", FtutterKeys.THREAD_TOOLBAR_LEFT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
        item { FtutterSliderSetting("スペース（右端）", FtutterKeys.THREAD_TOOLBAR_RIGHT, 0.0, 100.0, 5.0, 5.0, preferences, save) }
    }
}

@Composable
internal fun FtutterTabDrawerSettingsPage(preferences: Map<String, String>, save: (String, String) -> Unit, onBack: () -> Unit) {
    FtutterSettingsPage("タブ・ドロワー", "ftutter-settings-page-tab-drawer", onBack) {
        ftutterSection("タブ")
        item { FtutterChoiceSetting("タブの位置", FtutterKeys.TAB_ON_TOP, listOf(1 to "上", 0 to "下"), 0, preferences.boolAsInt(FtutterKeys.TAB_ON_TOP), boolSaver(save)) }
        item { FtutterSliderSetting("タブの高さ", FtutterKeys.TAB_HEIGHT, 10.0, 200.0, 10.0, 40.0, preferences, save) }
        item { FtutterSliderSetting("タブの幅", FtutterKeys.TAB_WIDTH, 10.0, 200.0, 10.0, 60.0, preferences, save) }
        item { FtutterCheckSetting("スワイプで隣のタブへ移動する（※ジェスチャー無効時）", FtutterKeys.MOVE_TAB_BY_SWIPE, true, preferences, save) }
        item {
            FtutterCheckSetting("タブ移動時に更新する（カタログ）", FtutterKeys.REFRESH_ON_TAB_MOVE_CATALOG, false, preferences, save,
                note = "このモードはタブを開くたびにスレッドを取り直すので、常に更新されます")
        }
        item { FtutterCheckSetting("タブのドラッグでの並べ替えを無効にする", FtutterKeys.DISABLE_TAB_DRAG, false, preferences, save) }
        item { FtutterCheckSetting("タブがない時に自動でカタログまで戻る", FtutterKeys.BACK_WHEN_NO_TAB, true, preferences, save) }
        ftutterSection("ドロワー")
        item {
            FtutterChoiceSetting("ドロワーを出す場所", FtutterKeys.DRAWER_SIDE, listOf(0 to "左", 1 to "右", 2 to "両方", 3 to "無効"), 0, preferences, save,
                note = "「両方」は左に出します")
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
    FtutterSettingsPage("画像・動画", "ftutter-settings-page-media", onBack) {
        item { FtutterCheckSetting("スレ内の画像を全て繋げて表示する", FtutterKeys.CONNECT_ALL_IMAGES, true, preferences, save) }
        item { FtutterCheckSetting("削除されたレスの画像を一覧に表示しない", FtutterKeys.EXCLUDE_DELETED_IMAGES, true, preferences, save) }
        item {
            FtutterCheckSetting("動画を自動再生する", FtutterKeys.VIDEO_AUTO_PLAY, false, preferences, save,
                note = "共有の再生部品を使うため、開いた時は止まっています")
        }
        item { FtutterCheckSetting("動画をループ再生する", FtutterKeys.VIDEO_LOOP, false, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
        item { FtutterSliderSetting("ビューワー内のタブの大きさ", FtutterKeys.GALLERY_TAB_SIZE, 20.0, 300.0, 10.0, 80.0, preferences, save) }
        item { FtutterCheckSetting("サムネイルの画質を下げる", FtutterKeys.USE_MEM_SIZE, true, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
    }
}

@Composable
internal fun FtutterNgSettingsPage(
    preferences: Map<String, String>, save: (String, String) -> Unit, ngCount: Int, hiddenCount: Int,
    onOpenNgList: () -> Unit, onOpenHidden: () -> Unit, onBack: () -> Unit
) {
    FtutterSettingsPage("ピックアップ・NG", "ftutter-settings-page-ng", onBack) {
        item { FtutterNavRow("NG・ピックアップの一覧", "ftutter-settings-ng-list", detail = "$ngCount", onClick = onOpenNgList) }
        item { FtutterNavRow("非表示にしたスレ", "ftutter-settings-hidden", detail = "$hiddenCount", onClick = onOpenHidden) }
        item { FtutterCheckSetting("連鎖NG", FtutterKeys.NG_CHAIN, false, preferences, save) }
        item {
            val orders = FtutterOrder.entries
            val selected = parseFtutterPickupOrders(preferences[FtutterKeys.PICKUP_ORDER_MODES])
            FtutterMultiChoiceSetting(
                "ピックアップを行うカタログの並び順", "ftutter-settings-pickup-orders",
                orders.map { it.label }, orders.indices.filter { orders[it] in selected }.toSet()
            ) { chosen -> save(FtutterKeys.PICKUP_ORDER_MODES, encodeFtutterPickupOrders(chosen.map { orders[it] }.toSet())) }
        }
    }
}

@Composable
internal fun FtutterNetworkSettingsPage(
    preferences: Map<String, String>, save: (String, String) -> Unit, deleteKey: String,
    onOpenCookies: (() -> Unit)?, onOpenSharedNetwork: (() -> Unit)?, onBack: () -> Unit
) {
    var keyOpen by remember { mutableStateOf(false) }
    FtutterSettingsPage("ネットワーク・投稿", "ftutter-settings-page-network", onBack) {
        item {
            FtutterCheckSetting("https版のサイトにアクセスする", "compat.ftutter.useHttps", true, preferences, save,
                note = "このモードは、ふたちゃと同じ通信（https）を使います")
        }
        item { FtutterCheckSetting("URLを開く際アプリ内ブラウザを使用する", "compat.ftutter.useChromeCustomTab", true, preferences, save, note = FTUTTER_NOT_USED_NOTE) }
        item { FtutterCheckSetting("書き込み前に確認する", FtutterKeys.CONFIRM_BEFORE_POST, false, preferences, save) }
        item { FtutterNavRow("削除キー", "ftutter-settings-delete-key", detail = "●".repeat(deleteKey.length)) { keyOpen = true } }
        onOpenCookies?.let { open -> item { FtutterNavRow("クッキーの管理", "ftutter-settings-cookies", onClick = open) } }
        onOpenSharedNetwork?.let { open -> item { FtutterNavRow("通信の設定（他のモードと共有）", "ftutter-settings-shared-network", onClick = open) } }
    }
    if (keyOpen) {
        FtutterDeleteKeyDialog(deleteKey, onDismiss = { keyOpen = false }) { keyOpen = false; save(FtutterKeys.DELETE_KEY, it) }
    }
}

@Composable
internal fun FtutterStatsPage(preferences: Map<String, String>, appVersion: String, onBack: () -> Unit) {
    val colors = LocalFtutterColors.current
    val stats = parseFtutterStats(preferences)
    FtutterSettingsPage("統計", "ftutter-settings-page-stats", onBack) {
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
