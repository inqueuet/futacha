# 文字選択クラッシュ：Composeとアプリの責任範囲の追加調査

> 本書は改修前の調査記録。後続の[改修内容・実機検証](text-selection-fix-2026-09-07.md)を参照。

追加の全48入力欄監査、新しい入力APIの実機比較5件、Android連携境界の検証6件は
[詳細監査](text-selection-detailed-audit-2026-09-07.md)を参照。
以下の環境・方法は本報の従来APIおよび本番投稿画面の再現テストを指す。

## 結論

Compose Foundation 1.12.0には、長押し開始時の選択範囲を本文変更後も使う経路がある。
Futacha固有の入力更新処理を使わない標準TextFieldでも、Androidのキーイベントで
選択文字を削除し、指を離すと報告と同じ例外になることを実機で確認した。

加えて、Futachaの互換モード投稿画面には「回線情報」の通信中に入力した文字を
古い本文で上書きする問題がある。この実際の画面でも、通信完了が長押し中に
重なると同じ例外が発生した。アプリ側にも到達可能な発火経路がある。

元のv10.3の個別クラッシュがこの操作によるものだったかは未確定。
提供ログには画面・直前の操作・要求時の本文長と選択位置が含まれていない。

## 環境と方法

- 実機: SCG33、Android 16 / API 36。
- インストール済みアプリ: v10.5、versionCode 168、Debug。
- Git: `42c3cf1`。アプリ本体は変更・再インストールしていない。
- スマート選択は既定の有効状態。無効化やTextClassifierのモックは使用しない。
- 本文はAndroidのキーイベント `sendStringSync` / `sendKeyDownUpSync` を通して編集。
  長押しはタッチイベントで行う。表示後にテストから本文のStateを直接変更していない。
- 計測用ComponentActivityで標準TextFieldと、本番の `CompatPostScreen` をそれぞれ表示。
- 投稿画面では専用の一時DBを使用し、既存のユーザーの板・履歴・下書きを変更しない。
- 投稿画面のHTTP応答はMockEngineで `example.test` とし、応答時刻だけを制御。
  画面の入力処理・回線情報取得関数・通信完了後の本文更新・Composeは本番実装を使用。
- キーイベントの注入は自動化であり、人間が端末を手で操作した検証ではない。
  投稿画面の「取得を開始→入力→長押し→通信完了」は通常の画面操作で到達可能な順序だが、
  実サーバーとの通信で偶然そのタイミングが重なる頻度は測定していない。

## 1. Compose単体での切り分け

`TextFieldValue("alpha beta gamma")` を標準TextFieldに表示し、
`onValueChange` ではComposeが渡す値をそのままStateへ格納する。
独自の整形・文字数制限・外部State同期処理は使わない。

| 操作 | 実機結果 |
| --- | --- |
| gammaを長押し→指を離す→Backspace | 成功。本文は `alpha beta ` |
| gammaを長押し→Backspace→指を離す | 報告と同じIllegalArgumentException |
| 同じ操作をrememberStableTextInputState経由で実行 | 報告と同じIllegalArgumentException |

長押し中のBackspaceは外付けキーボード相当の入力。
これは一般的な片手タッチ操作と同じとは扱わず、Compose単体の責任範囲を確認する比較とした。
削除後の本文が期待どおりであることをアサートした後に指を離している。

Composeソースの経路:

1. `TextFieldSelectionManager.kt:328` で長押し開始時の `dragBeginSelection` を保存。
2. 同ファイルの `value` setter（110行付近）は本文変更時に `latestSelection` を更新するが、
   `dragBeginSelection` はクリアしない。
3. 長押し終了処理の443行で `maybeSuggestSelection(dragBeginSelection)` を呼ぶ。
4. 559行で現在の本文を取得し、567行では空文字と空の選択だけを検査して、
   571行で古い選択範囲と新しい本文を組み合わせて渡す。
5. `PlatformSelectionBehaviors.android.kt:140` の `TextSelection.Request.Builder` が
   `end > text.length` を拒否して例外を投げる。

正常な本文・選択範囲を受け取って更新するだけのアプリでも発生しており、
この再現にFutacha側が不正なTextFieldValueを作る必要はない。

## 2. Futachaの本番投稿画面での発火経路

対象: `shared/src/commonMain/kotlin/ui/compat/CompatibilitySecondaryScreens.kt:3385`。

```kotlin
"network_info" to {
    val commentBeforeLookup = comment
    scope.launch {
        val info = fetchCompatPostNetworkInfo(httpClient, "Futacha/$appVersion")
        replaceComment(appendCompatPostText(commentBeforeLookup, info))
    }
}
```

通信前の本文を保存しているため、待ち時間中に入力された内容が結果に含まれない。
v10.3のコミット `02ab666` にも同じ処理がある（3382〜3385行）。

実機テスト手順:

1. 実際の `CompatPostScreen` のコメント欄に `prefix ` をキー入力する。
2. 「回線情報」を押し、HTTP要求が開始したことを確認する。
3. 応答待ちの間に `alpha beta ` を8回と `gamma` をキー入力する。
4. コメント欄の実際の本文が入力と一致することを確認する。
5. 末尾の `gamma` を長押しし、実際の選択範囲が `gamma` であることを確認する。
6. HTTP応答を返す。アプリ自身の通信完了処理によって本文が
   `prefix ` と回線情報へ戻り、待ち時間中の追加文字が失われる。
7. 本文長が長押し開始時の選択末尾より短くなったことを確認し、指を離す。
8. `TextSelection.Request.Builder` → `PlatformSelectionBehaviors.android.kt:140` で同一例外。

比較用に長押しなしで通信完了させたテストでも、後から入力した `gamma` が
失われることを確認した（この診断テストはデータ消失を確認する期待値なので成功と記録）。
投稿・削除などサーバーへの書き込み操作は行っていない。

## 修正の責任範囲

- **アプリ側:** 回線情報を通信完了時の最新本文へ追記し、取得中の入力を上書きしない。
  単にラムダ内で `comment` に変えても、再コンポーズ前の値を捕まえる可能性があるため、
  最新の入力Stateを参照する方法を明示する必要がある。
- **Compose側:** 本文更新後に古い選択要求を出さず、Android APIの呼び出し直前に
  本文長と選択範囲を検証する。結果を適用するときも本文・選択状態の整合性を確認する。
- アプリ側の回線情報だけを修正しても、外付けキーボードなどによる同時編集の経路は残る。
- スマート選択の無効化はユーザー要件により採用しない。
- 今回は調査のみ。修正の実装・リリースAPK検証は行っていない。

## 3. 回線情報以外の追加確認

同じSCG33 / v10.5で本番 `CompatPostScreen` を表示して4件を実行。
スマート選択は有効、表示後の本文Stateの直接変更は使用していない。

| 操作・条件 | 結果 |
| --- | --- |
| 長押し→指を離す→「リセット」 | 成功。起動時の下書き `prefix` に復元 |
| 長押ししたまま、別の指で「リセット」→最初の指を離す | 同じ `TextSelection.Request.Builder` の例外 |
| 下書きの読み込み中に入力→復元完了（長押しなし） | 追加した入力が起動時の下書きに上書きされることを確認 |
| 下書きの読み込み中に入力・末尾を長押し→復元完了→指を離す | 同じ `TextSelection.Request.Builder` の例外 |

リセットのケースは、事前保存した非空の下書きから入力を増やし、2本のタッチポインターで操作。
テストからリセットのコールバックを直接呼んだものではなく、UIに対するタッチで実行した。
テスト用の通信やI/O遅延は必要ない。スレ立て時のリセットは空文字へ戻す別動作なので、
今回確認した「既存スレへの返信で短い非空の下書きへ戻る」条件とは区別する。

下書きのケースは `CompatibilityStore.loadDraft()` の応答をテスト用の委譲Storeで遅らせ、
実際のDBから読み取った下書きを返した。本番の復元処理（3118行付近）が本文を更新する。
入力欄は下書き復元完了前も操作可能で、復元前のユーザー編集を守る条件がない。
実機で自然にこの長さの読み込み遅延が起きる頻度は確認していない。
添付ファイルがある場合は、本文復元の前に添付の読み込みも待つ構造になっている。

### 調べたが、同一条件と断定しない経路

- あぷ小アップロードの完了処理も本文を再構成するが、処理中は全画面の待機Dialogが
  タッチ操作を遮る（3691行付近）。回線情報と同じ入力待ち時間があるとは扱わない。
- Androidの音声入力は別Activityで行い、結果コールバックは `rememberUpdatedState` で
  最新化されている。回線情報と同じ古い本文の捕捉とは異なる。
- 通常モードのAI操作による本文・検索語の置換は外部から値を変える経路として存在するが、
  長押しと重なる実操作は未検証。追加の確定した再現経路としては数えない。
- 単純なクリアで空文字になる場合、Composeの空文字チェックで選択問い合わせが
  除外される経路がある。非空の短い本文への復元と同一視しない。

## 4. なぜ補助機能の失敗がアプリ終了につながるのか

クラッシュはAndroidの自動判別モデルの計算中ではなく、問い合わせ要求を作る段階で起きる。
`TextSelection.Request.Builder` は `0 <= start < end <= text.length` を満たさない要求を
`IllegalArgumentException` で拒否する。

Compose Foundation 1.12.0の `suggestSelectionForLongPressOrDoubleClick` は
空本文・空選択だけを除外し、選択範囲の上限を検証せずBuilderを生成する。
呼び出し元の `coroutineScope.launch` と `requireTextClassificationSession` の処理にも
この例外を捕捉して要求を破棄する処理がない。

`withTimeoutOrNull` があるのは処理の時間制限であり、`IllegalArgumentException` を
`null` に変える処理ではない。例外は `withContext` から呼び出し元へ伝わり、
未処理のコルーチン例外となる。通常のAndroidアプリでは未処理例外は致命的エラーになる。
計測時はテストランナーが捕捉してテスト失敗として記録する。

必要なのはスマート選択全体の無効化ではなく、要求境界で不整合を検出して
その要求だけを破棄し、正常な要求は実行すること。アプリ全体の未処理例外を
無差別に握りつぶす対策では、処理途中の状態や別の不具合を隠してしまう。

## 証跡

- [標準入力欄とアプリ入力状態の比較ログ](repro/text-selection-crash/keyboard-input-order-results.txt)
- [本番投稿画面のログ](repro/text-selection-crash/post-network-selection-results.txt)
- [入力順序の再現コード](repro/text-selection-crash/TextSelectionInputOrderInstrumentedTest.kt)
- [本番投稿画面の再現コード](repro/text-selection-crash/CompatPostNetworkSelectionInstrumentedTest.kt)
- [リセット・下書き復元の実機ログ](repro/text-selection-crash/post-other-selection-results.txt)
- [リセット・下書き復元の再現コード](repro/text-selection-crash/CompatPostOtherSelectionInstrumentedTest.kt)

診断コードは既知の例外で失敗するため、通常のテストソースから外して保存している。
再実行時は対象の `.kt` を `app-android/src/androidTest/java/com/valoser/futacha/` にコピーし、
`:app-android:assembleDebugAndroidTest` で生成した計測APKを実機へインストールする。
既存の同名ファイルがないことを確認し、終了後はコピーを取り除く。

```sh
adb shell am instrument -w -e class com.valoser.futacha.TextSelectionInputOrderInstrumentedTest com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.valoser.futacha.CompatPostNetworkSelectionInstrumentedTest com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.valoser.futacha.CompatPostOtherSelectionInstrumentedTest com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner
```
