package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.valoser.futacha.shared.ui.compat.CompatLicenseScreen

internal val mediaHelpSections = listOf(
    "使い始めるには" to "設定にある「プロンプト・AIラベルを表示」「画像編集を有効にする」「動画編集を有効にする」は、それぞれ独立しており、初期状態はすべてOFFです。使いたい機能だけONにしてください。",
    "AIラベルとプロンプト" to "取得済みの原本に生成情報が見つかると、一覧にAIラベル、画像や動画の下にプロンプトを表示します。設定で表示位置を選べます。ラベルだけのために一覧の全原本をダウンロードすることはありません。ラベルがないことは、AI生成ではないという判定ではありません。",
    "詳細とコピー" to "生成情報の詳細から、プロンプト本文や候補ごとの情報をコピーできます。本文の長押しで範囲選択もできます。改行・空白・seedは元の表記を保ちます。PNG・JPEG・WebP・MP4・MOV・WebMの対応するタグを読み取り、保存済み原本も表示できます。未知の形式やタグは読み取れない場合があります。",
    "画像を編集する" to "設定の画像編集からスマホ内の画像を選びます。性器候補の自動検出、範囲の手動指定、輪郭の調整を使い、モザイク・黒塗りを確認してJPEGとして新規保存します。ズーム、元に戻す・やり直す操作ができます。顔検出は任意で、初期状態はOFFです。",
    "動画を編集する" to "設定の動画編集からスマホ内の動画を選びます。対象の時間と範囲を指定し、自動検出・前後への追尾・輪郭の調整を行えます。指摘された区間を見直し、必要なら手修正して確認済みにしてください。編集途中の再生で確認した後、MP4として新規保存します。未確認の区間があると保存できません。",
    "解析モデル" to "自動検出・輪郭には解析モデルの導入が必要です。編集画面のモデル管理で、配布元とライセンスを確認してからダウンロードするか、指定されたONNXファイルを選択してください。モデルは自動ダウンロードされません。解析は端末内で行い、画像や動画を解析サービスへ送信しません。NudeNetはAGPL-3.0、Anime CensorとMobileSAMは配布元にMITの表示があります。",
    "保存と対応範囲" to "元の画像・動画は上書きしません。保存前に隠したい部分を確認してください。動画編集は対応するSDR映像とAAC音声、または音声なしの入力を扱います。iOSのWebM編集、HDR変換、非対応音声の変換は未対応です。扱えない入力は理由を表示します。",
    "WebMが再生できないとき" to "iOS 17.4以降でも、端末や動画内部の形式により再生できない場合があります。読み込みが失敗したときはエラーから再試行するか、外部で開いてください。ふたばにはMP4の代替URLがないため、MP4への自動切替や自動変換は行いません。"
)

internal fun mediaHelpHtmlSection(): String = buildString {
    append("<label for=\"media-help\" class=\"index\">プロンプト・画像編集・動画編集</label>")
    append("<input type=\"checkbox\" id=\"media-help\" class=\"on-off\" />")
    append("<div class=\"explain\">")
    for ((title, body) in mediaHelpSections) append("<p class=\"title\">$title</p><p class=\"explain\">$body</p>")
    append("</div>")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaHelpButton() {
    var open by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("media-help-open"),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text("メディア機能の使い方・ライセンス")
    }
    if (open) Dialog(onDismissRequest = { if (licenses) licenses = false else open = false }, properties = mediaEditorDialogProperties()) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (licenses) CompatLicenseScreen(onBack = { licenses = false })
            else Scaffold(topBar = {
                TopAppBar(title = { Text("メディア機能の使い方") }, actions = {
                    TextButton(onClick = { open = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("閉じる") }
                })
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mediaHelpSections) { (title, body) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item { TextButton(onClick = { licenses = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("オープンソースライセンスを読む") } }
                }
            }
        }
    }
}
