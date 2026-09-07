# スマート選択クラッシュの改修方針・実装・検証

対象: v10.5 / Git `42c3cf1` からの作業差分。2026-09-07。
調査証跡は [詳細監査](text-selection-detailed-audit-2026-09-07.md) を参照。

## 改修方針

スマート選択を有効に保ち、Androidの分類要求の失敗をその要求内で処理する。
正常な分類要求・結果・メニューはComposeの元の実装を使う。
回線情報と下書き復元による入力上書きも修正する。

追加調査で以下を確認し、入力APIの全48箇所の一括移行は今回の修正に含めなかった。

- TextFieldStateによる比較5件は成功したが、全画面のIME変換、Stringとの同期、保存状態、
  パスワード、iOSの入力方式に変更が及ぶ。
- 新APIもAndroidの分類・例外処理部分を共有するため、サービス例外への防御は別途必要。
- PlatformSelectionBehaviorsを単にラップすると、Composeのスマートメニュー構築側の
  `is PlatformSelectionBehaviorsImpl` 判定から外れる。正常機能を残すには具体クラスを維持する必要がある。

## Androidの限定的なライブラリ補強

AGPの公開された[クラス計測API](https://developer.android.com/reference/tools/gradle-api/8.9/com/android/build/api/variant/Instrumentation)で、
解決されたFoundation Android 1.12.0の以下2つの呼出し箇所をビルド時に補強する。
Gradleのキャッシュ内の配布AAR自体は書き換えない。

| 対象 | 変更 |
| --- | --- |
| PlatformSelectionBehaviorsImpl.requireTextClassificationSession | withContext呼出しに要求単位の例外処理を追加 |
| suggestSelectionForLongPressOrDoubleClickのSuspendLambda | 判別器の返した範囲を、要求時の本文に対して検査してからTextRangeへ変換 |

`SafeTextClassification.withContext` は元と同じCoroutineContextで元の処理を実行する。
回復可能なRuntimeExceptionではnullを返し、既存の通常選択へ戻す。
CancellationExceptionは再送出し、Errorは捕捉しない。既存の分類タイムアウトもそのまま使う。
リクエスト本文・選択文字・例外メッセージ・スタックはログへ出さない。

要求側の不正範囲はAndroidのBuilderで拒否され、追加した例外処理でその要求だけ終了する。
結果側は `0 <= start < end <= text.length` を検査し、API31以降の分類結果同梱ルートと
追加分類ルートの両方を守る。具体クラス、内部の分類結果キャッシュ、メニュー構築処理を維持する。

実装:

- `buildSrc/src/main/java/com/valoser/futacha/instrumentation/ComposeSelectionGuardFactory.java`
- `app-android/src/main/java/com/valoser/futacha/text/SafeTextClassification.kt`
- `app-android/build.gradle.kts` の全アプリvariantへの登録

これはFoundation内部の構造に依存する保守対象の補強である。
`libs.versions.toml` の `composeFoundationGuard` をstrictly 1.12.0に固定し、
訪問した対象クラスの書き換え箇所がそれぞれ1件でなければビルドを失敗させる。
ライブラリ更新時にはこの補強と回帰テストを見直し、上流の修正版へ移行したら取り除く。
リフレクション、実行時のFactory差し替え、スマート選択フラグの無効化は使用しない。

## 入力の上書き防止

`CompatibilitySecondaryScreens.kt` の互換投稿画面を改修。

- 回線情報の応答後、Composition時点のStringではなく `commentValue.text` を読み、最新本文へ追加する。
- あぷ小アップロード完了後の追記も同様に最新本文へ追加する。
- 本文・名前・メール・題名・削除キー・添付について、入力や置換が行われたことを項目別に記録する。
- 下書き復元は未編集の項目だけ反映する。「一度入力して空に戻した」編集も守る。
- リセット・破棄は明示的な編集として扱い、遅れて戻る初期読み込みで取り消されないようにする。
- 未編集項目の初期下書き復元、初期下書きへのリセット、入力中のIME composition、既存の文字数制限は維持する。

編集記録はownerKeyに紐づけたUIスレッド上の小さな集合。本文の内容は記録しない。
元の下書き・添付はリセット用の初期値として保持する。

## 依存管理の整理

今回のGradle依存と固定条件は `gradle/libs.versions.toml` に集約した。
buildSrcも同じカタログを読み、AGP APIとKotlin Gradle APIは既存のagp/kotlinバージョンを参照する。
ASMは新しいasm項目を参照する。`gradleApi()` は実行中のGradleが提供するAPIで、外部の直書き依存ではない。

既存のiOSのFirebaseAnalytics / FirebasePerformance / FirebaseCrashlyticsはCocoaPods管理で、
`iosApp/Podfile.lock` に解決版が記録されている。これらをGradleのカタログへ移す変更は行わない。
sharedのCocoaPods `version = "1.8"` は自作shared Podの公開メタデータであり、外部ライブラリのバージョンではない。

## 検証

実機 SCG33 / Android 16 / API36。修正したDebugアプリと計測APKをインストールして検証。
専用DBと偽の通信応答を使い、既存のユーザーの板・履歴・下書きや投稿先サーバーを書き換えない。

### 実機の回帰テスト

| テストクラス | 件数 | 検証内容 |
| --- | ---: | --- |
| TextSelectionGuardInstrumentedTest | 10 | 不正要求、空・collapsed、逆向き範囲、日本語・サロゲート、同梱分類有無の不正応答、サービス例外、キャンセル、toolbar/context menu |
| TextSelectionRegressionTest | 5 | 素のTextFieldとアプリの入力Stateで長押し中の削除、通常の削除、InputConnectionによる日本語の変換・確定 |
| CompatPostNetworkSelectionRegressionTest | 2 | 通信待ち中に追加した本文の維持、長押しとの重複 |
| CompatPostRestoreSelectionRegressionTest | 6 | 通常と二本指のリセット、遅い下書き復元と編集、明示的な空欄化、未編集項目の復元 |
| TextSelectionFeatureRegressionTest | 4 | 実端末の判別器への要求、範囲拡張、分類メニュー、分類失敗後の通常選択・コピー |

最終APKで追加27件と既存のCompatSettingsSchemaInstrumentedTestの5件を一括実行し、計32件が成功した。
既存5件はIME resize方針の適用・復元、投稿入力の検証メッセージ、あぷ小ダイアログ、添付プレビュー、検索件数・巡回・IME/戻る操作。
Androidのキー・タッチ・InputConnectionを使う。
日本語変換テストはInputConnectionへの変換・確定コマンドであり、人手でIME候補を選んだ検証ではない。
分類メニューと異常応答は偽の判別器による境界テスト。正常な要求は実端末の判別器への到達も別途確認した。

### 改修前との比較と既存の制約

同じFoundation 1.12.0・同じ物理端末で、補強を含まない最小アプリを別パッケージにして比較した。
長押し中の削除2件は報告と同じBuilderの例外を再現し、分類器の例外1件も伝播した。
これは過去の個別Crashlyticsイベントの画面・操作の特定ではない。

範囲拡張と追加メニューの同時表示を一つのテストにすると、拡張が成功しても分類メニューだけ
出ないことがある。未改修の最小アプリでも同じテストを5回実行し、4回成功・1回失敗した。
失敗時は切り取り・コピーが表示され、判別アクションが見つからない点も一致した。
この5回の結果を自然発生率の推定には使わない。

そのため、恒久テストでは範囲拡張とメニューを独立して検証する。
組合せの診断コードと改修前後の失敗ログは削除せず、
[比較証跡](repro/text-selection-fix/) に残した。
メニュー表示の不安定さは改修前から再現する未解決事項で、今回解消したとは扱わない。
分類キャッシュと選択更新のタイミングが候補だが、内部競合箇所までは確定していない。

### ビルド・共通検証

- `qualityGate` 成功。品質契約、Android lint、Debug APK、共通JVM・Android Host・iOS Simulator・Androidアプリ単体テストを含む。
- テスト結果XMLはJVM 1,543件、Android Host 1,531件、iOS Simulator 1,558件、Androidアプリ単体42件で失敗・エラー0。合計4,674はプラットフォームごとの実行件数であり、独立した4,674種類のテストという意味ではない。
- Release APKのR8最適化を含むビルドも成功し、mappingで2箇所の補強が残ることを確認。API35のBaseline Profile生成4シナリオも完了。Release APKは実機へインストールしていない。
- Releaseビルドでは `-x :app-android:uploadCrashlyticsMappingFileRelease` を指定し、外部へのマッピング送信を除外した。
- 依存カタログ整理後も `qualityGate` を再実行し成功。最終のビルド用Javaパッケージ配置は、ソースが `**/build/` のGit除外に入らないよう `instrumentation` とし、APKを再ビルドし、そのAPKで上記32件を確認した。

ビルド・テストログは [repro/text-selection-fix/](repro/text-selection-fix/) に保存した。
Releaseビルド後に依存カタログの追加整理とビルド用Javaパッケージ配置を変更している。
最終Debugにはそれらを反映して実機確認済みだが、Release成果物は完全に同一のビルド入力ではない。
比較用に追加した未改修の別パッケージは検証後にアンインストールした。

## この修正の範囲

今回の例外による終了と、特定した入力上書きを修正する。
従来APIに残る「本文が同一で選択だけが変わった後に、古い正常な分類結果が到着する」競合については、
入力API移行を含めた別の検証が必要であり、今回すべての選択競合を解消したとは扱わない。
元のv10.3の個別Crashlyticsイベントの操作特定、全メーカー・全OSでの自然発生頻度の測定は行っていない。
