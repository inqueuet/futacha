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
        ExperienceProfile.FUTACHA -> "スレッドの書き込みボタンから投稿画面を開きます。"
        ExperienceProfile.TOSHIAKI_COMPAT -> "スレッドの鉛筆の「書き込み」から投稿画面を開きます。"
        ExperienceProfile.FUTABER -> "スレッド下部の「操作メニュー」→「書き込む」から開きます。"
        ExperienceProfile.FTUTTER -> "スレッド下部の鉛筆の「書き込み」、または「メニュー」→「書き込む」から開きます。"
    }
    val attach = when (profile) {
        ExperienceProfile.FUTACHA -> "投稿画面の添付ボタンから画像を選びます。添付後の削除ボタンで外せます。"
        ExperienceProfile.TOSHIAKI_COMPAT -> "投稿画面の「添付画像」で選択します。選択後は「添付削除」になります。画像の貼り付けは「その他」→「画像を貼り付け」です。"
        ExperienceProfile.FUTABER -> "投稿画面の添付ボタンから選びます。書き込み先の板によって添付できる形式が異なります。"
        ExperienceProfile.FTUTTER -> "投稿画面のクリップの「添付」で選択し、「添付を外す」で解除します。画像一覧はスレッドの「メニュー」からも開けます。"
    }
    val gestures = when (profile) {
        ExperienceProfile.FTUTTER -> "設定→「ジェスチャー」で軌跡と操作を確認できます。カタログ・スレッドの「メニュー」→「ジェスチャーの操作を選ぶ」なら線を描かずに実行できます。前へ・次へは、カタログではお気に入りの板、スレッドではタブの移動です。"
        ExperienceProfile.FUTABER -> "設定→「ジェスチャー」でタップスクロールや画像を閉じる操作を確認できます。更新・戻る・画像一覧は画面のボタンからも操作できます。"
        ExperienceProfile.TOSHIAKI_COMPAT -> "ジェスチャーやツールバーの設定を変えると操作が変わります。戻る・更新などは画面のボタンでも操作できます。AndroidではOSの戻るも使えます。"
        ExperienceProfile.FUTACHA -> "画像の左右スワイプで前後へ移動します。スレッドの更新・戻るは画面のボタンからも操作できます。"
    }
    return listOf(
        ModeOperation("write", "書き込む", write),
        ModeOperation("attach", "画像を添付する", attach),
        ModeOperation("key", "削除キーを設定する", if (profile == ExperienceProfile.FTUTTER)
            "未設定の時は投稿画面の「削除キーを設定」から入力できます。設定後も本文・添付は残ります。"
            else "投稿画面や書き込み設定で削除キーを設定します。自分の投稿を後で削除するための値で、投稿時に必要です。"),
        ModeOperation("save", "画像を保存する", "スレッドの画像をタップして大きく開き、画像ビューアの保存ボタンやメニューを使います。「スレッドを保存」は本文も保存する別の操作です。"),
        ModeOperation("back", "前の画面へ戻る", "画面の戻るボタンを使います。AndroidではOSの戻るも使えます。メニューや画像が開いている時は、まずそれを閉じて戻ります。"),
        ModeOperation("report-ng", "delとこのレスのNG", when (profile) {
            ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT ->
                "レスを長押し→削除依頼／通報の確認画面で「このレスをNGにも登録」を選びます。カタログのスレッドNGとは範囲が異なります。"
            else -> "設定→拡張機能で「delとこのレスのNGをまとめる」をONにすると、レスのdel／通報確認で「このレスをNGにも登録」を選べます。このスレッドのこのレスだけが対象で、送信とNGの成否は別々に表示します。"
        }),
        ModeOperation("gesture", "ジェスチャーと代わりの操作", gestures)
    )
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
