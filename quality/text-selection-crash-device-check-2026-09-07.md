# v10.3 の文字選択クラッシュ：v10.5 実機確認

> 本書は改修前の調査記録。後続の[改修内容・実機検証](text-selection-fix-2026-09-07.md)を参照。

**追加調査:** [Composeとアプリの責任範囲の切り分け](text-selection-root-cause-2026-09-07.md)で、
本文Stateの直接変更を使わず、キー入力と本番投稿画面の「回線情報」完了から
同じ例外へ到達する経路を確認した。元のv10.3の個別イベントと同じ操作かは未確定。

2026-09-07、SCG33 / Android 16（API 36）の実機で、報告と同じ
`IllegalArgumentException` を再現した。現行 v10.5 でも未解消。

## 確認環境

- Git: `42c3cf1`
- 実機にインストール済みのアプリ: `com.valoser.futacha`、v10.5 / versionCode 168、Debug
- Android Compose Foundation: 1.12.0（Gradle の Debug / Release 依存解決で確認）
- 計測テスト APK を追加インストール。アプリ本体の再インストール・データ消去は実施していない。
- アプリ本体のコード変更なし。

## 実行結果

| 実機テスト | 結果 |
| --- | --- |
| 長押し、文字列変更なし | 成功 |
| ダブルタップ、文字列変更なし | 成功 |
| 長押し中に文字列を短縮、String 版 TextField | 報告と同じ例外 |
| 長押し中に文字列を短縮、アプリの rememberStableTextInputState を使用 | 報告と同じ例外 |

4件中2件が同じ例外で失敗。ダブルタップは実際に非空の選択範囲ができる
アサーションも追加し、単独で再実行して成功した。

再現手順は `hello world selection` の `hello` をタッチで長押しし、
選択範囲が空でないことを確認した後、指を離す前に本文を `a` に更新して指を離す。
本文更新はテストから行う。選択範囲の不正値の直接注入や TextClassifier のモックは使用していない。

例外の主要部分:

```text
java.lang.IllegalArgumentException
  at android.view.textclassifier.TextClassifier$Utils.checkArgument(TextClassifier.java:735)
  at android.view.textclassifier.TextSelection$Request$Builder.<init>(TextSelection.java:398)
  at androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsImpl$suggestSelectionForLongPressOrDoubleClick$2.invokeSuspend(PlatformSelectionBehaviors.android.kt:140)
  at androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsImpl$requireTextClassificationSession$2$1.invokeSuspend(PlatformSelectionBehaviors.android.kt:325)
```

## 判定の範囲

実機上で、現行アプリに含まれる Compose とアプリの入力状態管理を使い、
実際のタッチイベントから同一例外に到達した。計測用 ComponentActivity に
TextField を表示した制御条件での再現であり、元の利用者がどの画面・操作で
本文更新を起こしたかを特定したものではない。計測ランナーが例外を捕捉して
テスト失敗として記録しているため、通常起動中のアプリ終了を撮影した検証ではない。
Release APK での端末操作は未実施。

## 証跡と再実行

- [4件の実機ログ](repro/text-selection-crash/device-results.txt)
- [ダブルタップ追加確認ログ](repro/text-selection-crash/double-tap-result.txt)
- [再現用計測テスト](repro/text-selection-crash/TextSelectionCrashInstrumentedTest.kt)

既知の不具合を再現して失敗する診断用コードのため、通常のテストソースには含めず保存した。
再実行時は対象ファイルが存在しないことを確認して、以下をプロジェクトルートで実行する。
複数端末が接続されている場合は `adb -s <serial>` で実機を指定する。

```sh
cp quality/repro/text-selection-crash/TextSelectionCrashInstrumentedTest.kt app-android/src/androidTest/java/com/valoser/futacha/
./gradlew :app-android:assembleDebugAndroidTest --offline --console=plain
adb install -r -t app-android/build/outputs/apk/androidTest/debug/app-android-debug-androidTest.apk
adb shell am instrument -w -e class com.valoser.futacha.TextSelectionCrashInstrumentedTest com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner
```

終了後はコピーした診断用 `.kt` を通常テストソースから取り除く。
この診断にアプリデータの初期化は不要。

## 解決案の追加検証

**方針更新:** ユーザー要件によりスマート選択の無効化は採用しない。
以下は原因切り分けのための比較結果であり、採用する解決策ではない。
スマート選択を有効に保ち、Compose内で古い選択要求を破棄する修正を検討する。

同じ実機・同じアプリ本体で、テスト画面を生成する前に
`ComposeFoundationFlags.isSmartSelectionEnabled = false` を設定して比較した。
通常の長押し、ダブルタップ、String入力欄の短縮、アプリの入力状態管理を使った
短縮の **4件すべて成功**（9.344秒）。設定はテスト終了時に元へ戻した。
アプリ本体への修正はまだ適用していない。

- [スマート選択無効時の実機ログ](repro/text-selection-crash/smart-selection-disabled-results.txt)
- 実行時は上記 `am instrument` に `-e smartSelection false` を追加する。
- 引数なしでは従来どおりスマート選択を有効にし、不具合を再現する。

当初検討した暫定対策は、Android の `FutachaApplication.onCreate()` の最初で
このフラグを無効化し、Release 用 R8 設定でも同じ値に固定すること。
Compose 1.12.0 のフラグの説明は、起動時の設定とReleaseでの `-assumevalues` を推奨している。
通常の範囲選択とコピー・貼り付けの経路は残り、Android TextClassifier による
選択範囲の自動拡張・分類結果による追加アクションが利用されなくなる。
今回の追加テストでは非空の範囲選択を確認したが、コピー・貼り付けの実操作と
R8適用済みReleaseの実機検証は、実装時に追加して確認する必要がある。

恒久対応は、Compose側でテキストと選択範囲の整合性を確認し、古くなった要求を
破棄する修正を取り込むこと。修正版への更新後に同じ実機テストを有効設定で通し、
暫定フラグとR8設定を取り除く。このフラグは一時的APIのため恒久利用を前提にしない。

## スマート選択を維持する修正案（未実装・未実機検証）

- Androidへ渡す本文と選択範囲を同じ時点の値として扱い、要求生成直前に
  `0 <= start < end <= text.length` を検証する。不整合な要求は実行せず破棄する。
- 選択候補の取得だけでなく、ツールバー用の `TextClassification.Request` 生成にも
  同じ検証を入れる。正常な要求は従来のTextClassifierへ渡す。
- 非同期の結果を画面へ反映するときは、要求時から本文・選択状態が変わっていないことを
  確認し、古い結果は反映しない。例外をアプリ全体で握りつぶす方式は採用しない。
- 修正対象はComposeの内部実装。アプリの `onValueChange` だけで補正しても
  ライブラリ内部が保持する古い選択位置は直せない。公式修正版の修正内容を確認して
  取り込むか、固定バージョンへ必要箇所だけを修正した依存ライブラリを使用する。
- 採用判定には、スマート選択有効で既存の再現テストを通すことに加え、正常な
  スマート選択がTextClassifierへ到達し、選択範囲の拡張・追加アクションが
  機能することを実機で確認する。今回のターンではこの修正案の実機検証は行っていない。
