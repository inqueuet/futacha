package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

internal data class ModeOperation(val id: String, val title: String, val steps: String)

internal fun modeOperationGuide(profile: ExperienceProfile): List<ModeOperation> {
    val write = when (profile) {
        // The button names on screen (L34: ふたちゃ's is "返信", not "書き込み").
        ExperienceProfile.FUTACHA -> "スレッドの「返信」ボタンから投稿画面を開きます。スレ立てはカタログの「スレッド作成」からです。"
        ExperienceProfile.TOSHIAKI_COMPAT -> "スレッドの鉛筆の「書き込み」から投稿画面を開きます。"
        ExperienceProfile.FUTABER -> "スレッド下部の「操作メニュー」→「書き込む」、または本文を左へスワイプして開きます。開くと本文に入力できます。"
        ExperienceProfile.FTUTTER -> "スレッド下部の鉛筆の「書き込み」、または「メニュー」→「書き込む」から開きます。"
    }
    val attach = when (profile) {
        ExperienceProfile.FUTACHA -> "投稿画面の添付ボタンから画像を選びます。添付後の削除ボタンで外せます。"
        ExperienceProfile.TOSHIAKI_COMPAT -> "投稿画面の「添付画像」で選択します。選択後は「添付削除」になります。画像の貼り付けは「その他」→「画像を貼り付け」です。"
        ExperienceProfile.FUTABER -> "書き込み画面のカメラを押し、「最近の画像」「ファイル」「ライブラリ」から選びます。書き込み先の板によって添付できる形式が異なります。"
        ExperienceProfile.FTUTTER -> "投稿画面のクリップの「添付」で選択し、「添付を外す」で解除します。画像一覧はスレッドの「メニュー」からも開けます。"
    }
    val gestures = when (profile) {
        ExperienceProfile.FTUTTER -> "設定→「ジェスチャー」で軌跡と操作を確認できます。カタログ・スレッドの「メニュー」→「ジェスチャーの操作を選ぶ」なら線を描かずに実行できます。前へ・次へは、カタログではお気に入りの板、スレッドではタブの移動です。タブ帯のタブは上へ払うと保護、下へ払うと閉じます。"
        ExperienceProfile.FUTABER -> "スレッドは右へスワイプでカタログへ戻り、左端から右へで板一覧、左へで書き込み画面を開きます。カタログは右へで板一覧、左へでこの板で最後に見たスレッドです。設定→「ジェスチャー」でタップスクロールや画像を閉じる操作を確認できます。更新・戻る・画像一覧は画面のボタンからも操作できます。"
        ExperienceProfile.TOSHIAKI_COMPAT -> "ジェスチャーやツールバーの設定を変えると操作が変わります。戻る・更新などは画面のボタンでも操作できます。AndroidではOSの戻るも使えます。"
        ExperienceProfile.FUTACHA -> "スレッドは中央から右へスワイプでカタログへ戻り、左端から右へで履歴を開きます（設定の「ふたちゃ拡張」で切れます）。画像の左右スワイプで前後へ移動します。スレッドの更新・戻るは画面のボタンからも操作できます。"
    }
    return listOf(
        ModeOperation("write", "書き込む", write),
        ModeOperation("attach", "画像を添付する", attach),
        // ふたちゃ・としあき(仮) have no "書き込み設定"; the key is the post screen's own field (L34).
        ModeOperation("key", "削除キーを設定する", when (profile) {
            ExperienceProfile.FTUTTER -> "未設定の時は投稿画面の「削除キーを設定」から入力できます。設定後も本文・添付は残ります。"
            ExperienceProfile.FUTABER -> "設定の「書き込み設定」、または書き込み画面の歯車で削除キーを設定します。自分の投稿を後で削除するための値で、投稿時に必要です。"
            ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT ->
                "投稿画面の「削除キー」欄に入力します。前回使った値が次の投稿にも入ります。自分の投稿を後で削除するための値で、投稿時に必要です。"
        }),
        ModeOperation("save", "画像を保存する", "スレッドの画像をタップして大きく開き、画像ビューアの保存ボタンやメニューを使います。「スレッドを保存」は本文も保存する別の操作です。"),
        ModeOperation("back", "前の画面へ戻る", "画面の戻るボタンを使います。AndroidではOSの戻るも使えます。メニューや画像が開いている時は、まずそれを閉じて戻ります。"),
        ModeOperation("report-ng", "delとこのレスのNG", when (profile) {
            ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT ->
                "レスを長押し→削除依頼／通報の確認画面で「このレスをNGにも登録」を選びます。カタログのスレッドNGとは範囲が異なります。"
            // Each mode's own switch and the check the confirmation actually shows (L26).
            ExperienceProfile.FUTABER, ExperienceProfile.FTUTTER -> "設定→「このアプリ」→「拡張機能」の「" +
                (if (profile == ExperienceProfile.FUTABER) com.valoser.futacha.shared.ui.futaber.FUTABER_REPORT_NG_SWITCH
                else com.valoser.futacha.shared.ui.ftutter.FtutterExtension.REPORT_NG.title) +
                "」をONにすると、レスのdel／通報の確認で「$REPORT_POST_NG_CHECK_LABEL」を選べます。" +
                "このスレッドのこのレスだけが対象で、送信とNGの成否は別々に表示します。"
        }),
        ModeOperation("gesture", "ジェスチャーと代わりの操作", gestures),
        ModeOperation("mix-catalog", "まとめカタログで板をまとめて見る", "複数の板のスレッドを1つのカタログに並べます。板一覧の「まとめカタログ」から開き（ふたちゃ＝メニュー、としあき(仮)＝右上のその他、ふたばー風＝板一覧、ふたったー風＝板一覧）、本物の板が2つ以上あるときだけ出ます。スレを押すとそのスレの板で開き、戻ると前の画面へ戻ります。スレは立てられず、AIは使えません。あいもげが休止中の板は飛ばします。更新は読み込み中と終わってから10秒の間は押せません。上部の虫眼鏡で、まとめた板のスレを題名で絞り込めます（全角・半角、カタカナ・ひらがなを区別しません）。")
        ,
        ModeOperation("aimoge", "あいもげの板を使う", "ふたばと同じく「板を追加」のURL欄に、あいもげの板のURL（https://nijiurachan.net/b/板/）を入れます（Android・iOS）。名前は板一覧から取り、R18の板は追加の前に確認します。使えない版では理由を出して追加しません。読む・書く操作はふたばと同じ入口です。書き込む前に設定の「あいもげ」でブラウザの承認をします。削除キーはふたばとは別で、同じ設定で確認できます。")
    ) + when (profile) {
        ExperienceProfile.FUTABER -> listOf(
            ModeOperation("kana", "本文を半角カナにする", "書き込み画面下部の「カナ」で、本文全体のひらがな・カタカナを半角カナにします。漢字・英数字や半角の形がない文字はそのままです。"),
            ModeOperation("pasteboard", "定型文・画像をペーストボードに保存する", "書き込み画面下部の用紙アイコン「ペーストボード」から「本文を保存」「添付画像を保存」を選びます。行をタップすると本文の末尾へ改行して追加、または画像を添付します。「編集」で削除できます。設定のバックアップには含まれません。"),
            ModeOperation("gallery", "画像一覧の続きから見る", "同じスレッドを開いている間は通常の画像一覧の位置を保ちます。ビューアの「画像一覧」で戻ると表示中の画像が見えます。選択保存用の一覧は位置合わせの対象外です。"),
            ModeOperation("keyboard", "書き込み画面から戻る", "Androidでキーボードが出ている時のOSの戻るは、まずキーボードだけを閉じます。もう一度戻るか、閉じるボタンで書き込み画面を閉じます。")
        )
        ExperienceProfile.FTUTTER -> listOf(
            ModeOperation("opening", "スレッドを開く位置を選ぶ", "初めてのスレッドは先頭、開いたことがあるスレッドは前回の読み位置から表示します。設定→「このアプリ」→「拡張機能」の「スレッドを先頭から開く」（初期状態OFF）をONにすると、カタログ・履歴・リンクからは先頭へ。タブの切り替えや設定から戻った時は見ていた位置を保ちます。")
        )
        else -> emptyList()
    }
}

@Composable
internal fun ModeOperationGuideDialog(profile: ExperienceProfile, onDismiss: () -> Unit) {
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作ガイド：${profile.displayName}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()).testTag("operation-guide-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            modeOperationGuide(profile).forEach { operation ->
                Column(Modifier.fillMaxWidth().testTag("operation-guide-${operation.id}")) {
                    Text(operation.title, style = MaterialTheme.typography.titleSmall)
                    Text(operation.steps, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("ツールバーを編集している場合は、表示するボタンが変わります。操作ガイドを開いても配置や割り当ては変わりません。")
        } },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("operation-guide-close")) { Text("閉じる") } }
    ) }
}
