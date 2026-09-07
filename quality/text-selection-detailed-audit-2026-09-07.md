# スマート選択クラッシュ：入力欄全数・依存ライブラリ・実機比較の追加監査

> 本書は改修前の調査記録。後続の[改修内容・実機検証](text-selection-fix-2026-09-07.md)を参照。

調査対象: Git `42c3cf1`、Futacha v10.5 / 168。実機 SCG33、Android 16 / API 36。
本書は[原因調査](text-selection-root-cause-2026-09-07.md)の追加結果。
アプリ本体の修正や依存関係の変更は実施していない。

## 結論と確度

1. 現在のAndroid依存 `androidx.compose.foundation:foundation-android:1.12.0` でも、
   従来の入力APIが長押し開始時の範囲と変更後の本文を組み合わせ、同じ例外に到達する。
   標準入力欄と本番の互換投稿画面の実機計測で確認済み。
2. 回線情報だけでなく、返信のリセットと下書き復元も本番画面で再現済み。
   これらは発火経路であり、補助機能の失敗を致命的例外にするライブラリ側の欠陥と区別する。
3. 同じ依存バージョンの `TextField(state = TextFieldState(...))` は、比較した5条件を
   スマート選択を有効にしたまま通過した。依存更新や機能無効化を伴わない対処候補になる。
4. 新APIもAndroid連携部分は共有する。判別器の異常応答・例外まで防ぐ修正ではない。
   これらの境界は別の6件の実機テストで確認した。
5. v10.3で報告された個別イベントの画面・操作・タイミングは特定できていない。
   現在再現した経路のいずれかが、過去のその1件の原因だったとは断定しない。

## 1. どのライブラリの、どの処理か

| 層 | 確認した処理 | 評価 |
| --- | --- | --- |
| Material3 TextField | 従来のString / TextFieldValue入力をFoundationへ渡す | 呼出元。直接の例外箇所ではない |
| Foundation / TextFieldSelectionManager | 長押し開始時のdragBeginSelectionを保存し、本文更新時には破棄しない | 不整合な問い合わせを作る原因 |
| Foundation / PlatformSelectionBehaviorsImpl | 空文字・空選択を除外するが範囲上限を検査しない | Androidへ不正な引数が渡る |
| Android / TextSelection.Request.Builder | 引数を検査しIllegalArgumentExceptionを投げる | API契約どおりの拒否 |
| Foundation / コルーチン | 要求作成・分類の例外を通常選択へ戻す処理がない | 例外が呼出元へ伝播する |
| Androidアプリ | 未処理例外が致命的エラーになる | 補助機能の失敗がプロセス終了へ拡大 |

`withTimeoutOrNull` はタイムアウト時のキャンセルを扱う仕組みであり、
`IllegalArgumentException` や `IllegalStateException` をすべてnullに変えるものではない。
バックグラウンドスレッドで実行しても、未処理例外からアプリを隔離できない。
実機計測ではテストランナーが例外を捕捉して失敗として記録する。

このスタックは判別モデルへ要求を渡す**前**に失敗している。提示された他スレッドの
OkHttp通信・WorkManager・ログ待機は、同時点のスレッド状態だけでは原因の証拠にならない。
回線情報の関連はスレッド一覧からの推測ではなく、アプリの本文更新と実機再現で確認したもの。

## 2. 範囲のどの条件が壊れるか

要求の条件は `0 <= start < end <= text.length`。
[AndroidのBuilder仕様](https://developer.android.com/reference/android/view/textclassifier/TextSelection.Request.Builder)でも開始・終了位置の制約が定義されている。

| 条件 | 現在のComposeとアプリの扱い |
| --- | --- |
| textがnull | 現経路は非null。報告もNullPointerExceptionではない |
| startが負 | ComposeのTextRange生成時に負の端点を拒否する |
| start > end | AndroidへはTextRange.min/maxを渡すため順序は正規化される |
| start == end | collapsedチェックで問い合わせを省略する |
| textが空 | 空文字チェックで問い合わせを省略する |
| end > text.length | 事前検査がなく、今回の実機再現で破れた条件 |

具体例は `alpha beta gamma`（16 UTF-16コード単位）のgammaを選んだ `[11,16)`。
指を離す前に削除すると本文は `alpha beta `（11）になる。
アプリが保持する現在の選択範囲は正常でも、Compose内部に残った `[11,16)` が使われる。
`replaceComment` の既存のcoerceInでは、この内部の古い範囲を直せない。

日本語や絵文字を含む有効なUTF-16範囲は、直接の境界テストを通過した。
ただし日本語IMEの変換中・絵文字全種類のUI操作を網羅したテストではない。
現在のアプリには独自OffsetMappingがなく、通常入力と同じ長さのPasswordVisualTransformationが
5箇所にある。マスク欄だけは安全、とする根拠はないため全数一覧に含めた。

## 3. 入力欄48箇所の静的監査

[入力欄一覧TSV](repro/text-selection-crash/text-field-inventory.tsv)に、ファイル・行・値の型・更新経路・個別の検証状況を記録した。

- 19ファイル、直接のTextField / OutlinedTextField呼出し48箇所。
- 通常UI19箇所、互換UI29箇所。
- TextFieldValue入力19箇所、String入力29箇所。すべて従来API。
- 監視ワードは1つの入力部品が全体用・板別用に2回使われるため、48は画面上の延べ個数ではない。
- 本番Kotlinの全体検索を併用し、別名import、独自BasicTextField、SelectionContainer、
  TextFieldState、ネイティブEditText入力への別経路は見つからなかった。
- 48画面操作を個別に実機再現した意味ではない。実機確認済みと静的な候補を分けている。

### 実機で確認済みの発火経路

| 経路 | 実行条件と証跡 |
| --- | --- |
| 標準TextField / アプリのStableTextInputState | 長押し中にAndroidの削除キーイベント。指を離す前に削除すると同じ例外。外付けキーボード相当の操作 |
| 互換投稿・回線情報 | 通信開始後に追加入力し、末尾を長押ししている間に応答完了。HTTPの応答タイミングを制御 |
| 互換返信・リセット | 長押し中に別ポインターでリセットをタップ。短い非空の初期下書きへ戻す。通信・I/O遅延の注入なし |
| 互換投稿・下書き復元 | 読み込み中に入力して末尾を長押し、短い下書きの復元が完了。実DBの読み出し結果の返却を遅延 |

上記は計測ツールによる実機のタッチ・キー操作。人間による手動検証や、
本番サーバー・ストレージで偶然同じ待ち時間になったことを確認したものではない。
詳細とログは[前報](text-selection-root-cause-2026-09-07.md)を参照。

### 追加の更新経路と、同一視しない理由

| 経路 | 静的調査の結果 |
| --- | --- |
| 投稿の名前・メール・題名・削除キー | 本文と同じ下書き復元・リセットが存在する。個別欄の長押し再現は未実施 |
| メールプリセット・検索履歴候補・巡回ワード編集 | 操作で非空の別文字列へ置換する。2本指操作との重なりは個別未実施 |
| 通常/互換のAI検索、通常のAI返信案 | 外部コマンドから入力文字列を変更できる。イベント重複の実機再現は未実施 |
| 保存先の既定値復元・NGのsection/initialInput変更 | 入力中の文字列を別の非空値へ変更し得る。個別再現は未実施 |
| ptmtの非同期取得 | ダイアログ表示中かつ入力が空のときだけ反映する条件あり。入力済みの文字を無条件で上書きする経路ではない |
| 遅れて届く保存済み削除キー | 下書き復元後、現入力が空の場合だけ補完。下書き全体の無条件復元とは別 |
| 音声入力 | Androidは別Activity。rememberUpdatedStateにより最新コールバックを利用。回線情報と同じ古い本文の捕捉ではない |
| あぷ小アップロード完了 | 古い本文を捕捉する箇所はあるが、待機中は全画面Dialogがタッチを遮る。回線情報と同じ操作窓とは断定しない |
| 空文字へのクリア・破棄 | 空文字チェックが効く条件がある。短い非空の本文への変更とは区別する |
| 文字数上限 | 設定された上限へのtake処理があることだけでは、このクラッシュの証明にならない |

## 4. 新しい入力APIの実機比較

現在のMaterial3 Android 1.4.0にある `TextField(state = ...)` を使用。
追加依存・バージョン更新・スマート選択フラグ変更は行っていない。

| 比較テスト | 結果 |
| --- | --- |
| 通常の長押し→指を離す | 成功。実際の端末判別器へ本文と[11,16)の問い合わせが届くことを記録 |
| 長押し中に削除キー→指を離す | 成功。従来APIでは同条件で例外 |
| 長押し中に削除キー→タッチキャンセル | 成功 |
| 長押し中に非空の短いprefixへ置換→指を離す | 成功 |
| 長押し中に空文字へ置換→指を離す | 成功 |

計5件成功。[コード](repro/text-selection-crash/TextSelectionStateApiInstrumentedTest.kt) / [実機ログ](repro/text-selection-crash/state-api-device-results.txt)。
通常問い合わせの記録用TextClassifierは元の実機判別器へ委譲する。偽の判定結果は返していない。
置換2件は原因を分離するための明示的なState更新であり、本番投稿画面を新APIへ移行して
回線情報・リセット・下書き復元を再テストした結果ではない。

実装の違いは `TextFieldSelectionState.kt:1116` 以降にある。
新APIは問い合わせ時のvisualTextから本文と現在の選択を読み、結果適用前に
本文と選択の**両方**が問い合わせ時の状態のままか確認する。
従来APIは開始時の範囲を使い、結果適用前は本文とOffsetMappingを比較するが、
現在の選択が変わっていないかは比較しない。
したがって、範囲が本文内に収まる変更でも「古い選択結果が現在の操作に割り込む」余地が
従来APIのソースに残る。この別の選択上書き現象は今回の実機テストでは再現確認していない。

[公式の移行手順](https://developer.android.com/develop/ui/compose/text/migrate-state-based)も存在する。
ただし移行時はIME変換、入力上限、外部状態との同期、画面復元、パスワード欄、iOS側の
動作を合わせて確認する必要がある。今回の5件だけで全48箇所の移行完了を意味しない。

## 5. Android連携境界の実機テスト

解決済みと過大評価しないため、新旧APIが共通利用するPlatformSelectionBehaviorsImplを
実機で直接呼び出した。判別器をテスト専用実装へ差し替える**境界・障害注入テスト**。
異常応答をSCG33の実判別器が返したという証拠ではない。

| 入力・障害 | 実際の挙動 |
| --- | --- |
| abcに[1,4) | BuilderでIllegalArgumentException。判別器は一度も呼ばれない |
| 空本文、またはcollapsed選択 | nullで終了。問い合わせなし |
| 逆向きの有効範囲[3,1) | [1,3)に正規化して問い合わせ成功 |
| 日本語と絵文字の有効UTF-16範囲 | 問い合わせ成功 |
| 判別器が本文より大きい結果[0,99)を返す | 後続のTextClassification.Request.BuilderでIllegalArgumentException |
| 判別器がIllegalStateExceptionを投げる | 同じ例外が呼出元へ伝播。nullへのフォールバックなし |

計6件、すべて想定した挙動のアサーションが成功。
異常入力のテスト成功は「ライブラリが安全に処理した」意味ではなく、例外伝播の確認を含む。
[コード](repro/text-selection-crash/TextSelectionBoundaryInstrumentedTest.kt) / [実機ログ](repro/text-selection-crash/boundary-device-results.txt)。

元の報告はTextSelection.Request.Builderで失敗している。
異常な分類結果のテストはTextClassification.Request.Builderで失敗する別経路であり、
元イベントの原因へ混ぜない。

## 6. スマート選択を維持する解決案

### 今回の発火経路に対する対処

1. 入力欄をTextFieldStateベースへ移行し、長押し開始時の範囲を使う従来経路を避ける。
   まず実機再現した投稿欄を含め、残る入力欄も一覧に沿って扱う。
2. 回線情報は完了時点の最新本文へ追加する。現状の `val comment = commentValue.text` は
   Composition時点のローカル値なので、コルーチン内の変数名をcommentへ変えるだけでは不十分。
   最新のState読み出し、またはrememberUpdatedStateを使った設計が必要。
3. 下書き復元は初期ロード完了後に編集を始めるか、入力が始まった項目は復元で上書きしない。
   本文だけでなく名前・メール・題名・削除キーも対象。添付読み込みを待ってから全項目を
   書き戻す現在の処理順も見直す。
4. 本番画面の回線情報・リセット・下書き復元の再現テストを、クラッシュしない期待値と
   入力を失わない期待値で再実行する。現時点では移行・修正をまだ行っていない。

### ライブラリ側で必要な防御

- 要求作成直前に本文と選択範囲を検証し、不整合な要求だけ省略する。
- 判別器の結果も本文内の範囲か検証してから再分類・UI適用する。
- 判別処理の回復可能な失敗は、その問い合わせだけ通常選択へ戻す。
  コルーチンのキャンセルは保持し、アプリ全体の未処理例外を握りつぶす対処は使わない。
- 本文と選択の変更を検出して、古い応答を適用しない。

この境界修正はFoundation内部にあるため、アプリのTextFieldの周囲をtry/catchで囲むだけでは
非同期コルーチンの例外を捕捉できない。TextClassifierのラッパーだけでも、
その呼出し前に失敗するRequest.Builderの範囲エラーは防げない。
ライブラリへの修正、修正版依存、または保守するパッチが必要になる。

調査時点の[公式リリース一覧](https://developer.android.com/jetpack/androidx/releases/compose-foundation)の安定版は1.12.0で、現在の解決済み依存と同じ。
「最新版へ上げれば直る」とは言えない。Alpha版への移行による解消は検証していない。
v10.3と現在はAndroid BOMがともに2026.08.00で、Multiplatformのバージョン変更だけから
Androidのこの不具合が修正されたと判断することもできない。

## 7. 証跡・再実行・残る範囲

調査した依存ソースのSHA-256は[依存ソース一覧](repro/text-selection-crash/dependency-sources.tsv)に保存。
新旧の差を確認した主な行は、Foundationソースの以下。

- TextFieldSelectionManager.kt:110、328、424、443、550、555、578、831
- PlatformSelectionBehaviors.android.kt:95、131、140、167、189、201、303、324
- TextFieldSelectionState.kt:1116、1134
- TextRange.kt:117（ui-text）

今回追加した実機テストは5+6=11件。テスト用APKのみインストールした。
診断ソースは通常のandroidTestソースから除き、quality/repro配下に保存した。
再実行は対象ファイルを一時コピーし、Android計測APKをビルド・インストールして以下を実行。

```sh
adb shell am instrument -w -e class com.valoser.futacha.TextSelectionStateApiInstrumentedTest com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.valoser.futacha.TextSelectionBoundaryInstrumentedTest com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner
```

未確定・未検証の範囲:

- v10.3の元イベントの操作。Crashlyticsの該当セッションの操作履歴・画面情報が必要。
- SCG33/API36以外の物理端末、Release APK上での同じ再現。API28未満はこのCompose経路を使わないが、API28以上の全機種を実測したものではない。
- 実サーバーや実ストレージでの自然発生頻度。
- 新API移行後の本番画面全体、日本語IME変換・iOS・各パスワード欄の回帰検証。
- 本文不変で選択だけ変わる間に遅れた分類結果が返るケースのUI再現。

このため、到達経路と対処候補は確認済みだが、アプリが修正済み・全条件で安全とは報告しない。
