# プレビューを含む依存更新（2026-09-07）

ユーザーの最新指示「最新版でないものは最新版にしてほしい。alpha版を認めます」に従い、alpha / beta / RCとGradle milestoneを含む公開版へ更新する。日次SNAPSHOTは対象外。先行した同日の安定版優先の確認より本記録を優先する。

全108ライブラリ、12プラグインmarker、3プラグイン実装座標の公式Mavenメタデータ123件を再取得し、確認不能0件。[全件の照合表](dependency-preview-audit-2026-09-07.tsv)と[50構成の解決結果](dependency-preview-resolution-2026-09-07.tsv)を記録した。

## 更新内容

作業開始時のコミット `9c63079` から、カタログの15バージョンキーを更新した。

| 項目 | 更新前 | 更新後 |
|---|---|---|
| Compose BOM | stable 2026.08.00 | alpha 2026.08.01 |
| Foundation Android補強対象 | 1.12.0 | 1.13.0-alpha02 |
| Activity Compose | 1.13.0 | 1.14.0-alpha01 |
| Compose Multiplatform Material3 | 1.9.0 | 1.12.0-alpha03 |
| DataStore | 1.2.1 | 1.3.0-alpha10 |
| kotlinx.serialization | 1.11.0 | 1.12.0-RC |
| Kotlin・Compose Compiler・KotlinプラグインAPI・テスト | 2.4.10 | 2.4.20 |
| Android Gradle Plugin / API | 9.4.0 | 9.5.0-alpha04 |
| WorkManager | 2.11.2 | 2.12.0-rc01 |
| Wear Compose | 1.6.2 | 1.7.0-beta02 |
| Wear Remote Interactions | 1.2.0 | 1.3.0-alpha01 |
| Benchmark | 1.4.1 | 1.5.0-rc02 |
| Lifecycle | 2.11.0 | 2.12.0-alpha02 |
| AndroidX Annotation | 1.10.0 | 1.11.0-alpha02 |
| AndroidX Test Monitor | 1.8.0 | 1.9.0-alpha01 |

Gradle Wrapperも9.6.0から公式公開プレビューの **9.8.0-milestone-2** へ更新した。公式手順でWrapperを2回生成し、JARとUnix / Windows起動スクリプトを含めて更新した。配布ZIPのSHA-256は公式チェックサムと照合し、Wrapper設定に固定した。

```text
988558592a6377d54d5730e7ee3032fcef421c4a40022d4d3605a6ac791b1087
```

iOS Firebase Analytics / Performance / Crashlyticsは12.11.0から **12.18.0** へ更新した。公式リリース一覧にこれより新しいプレビューは無かった。Podfileの共通変数で3つの直接依存を固定し、関連PodとチェックサムをPodfile.lockへ記録した。Gradleの依存は `gradle/libs.versions.toml`、Gradle本体はWrapper、PodはPodfile / Podfile.lockで管理する。

Kotlin 2.4.20は [Maven上で `release` として配布](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.4.20/kotlin-gradle-plugin-2.4.20.module) されているが、確認時の [Kotlin公式サイト](https://kotlinlang.org/docs/releases.html)・GitHub最新リリースは2.4.10だった。GitHubのv2.4.20タグ・リリースAPIは404。この配布と案内の差を記録し、正式発表済みとは扱わず、配布済みの2.4.20を実際に取得して検証対象とした。

## カタログと各モジュールへの適用

カタログへの登録だけでは全構成の依存バージョンは固定されない。shared/androidMainにもカタログのCompose BOMを適用し、Multiplatformから推移的に選ばれていた旧Android Foundation / UI / Material3を更新した。

[公式のalpha BOM](https://developer.android.com/develop/ui/compose/bom) に従い、座標を `androidx.compose:compose-bom-alpha` へ切り替えた。カタログ内のBOM管理対象Compose 14座標もそれぞれの最新公開版と一致することを確認した。Androidアプリ、shared本体、shared Android HostテストでFoundation / UIは1.13.0-alpha02、Material3は1.5.0-alpha27に揃う。WearもFoundation / UIは1.13.0-alpha02。

Guavaの `-jre` と `-android`、kotlinx.datetimeの `-0.6.x-compat` と通常版は異なる提供形態なので、メタデータのlatest文字列だけで切り替えない。Android向けGuava 33.7.1-androidと通常版datetime 0.8.0は各提供形態の最新版を維持する。BOM管理とビルドツール内部の推移依存は、全体への強制上書きを行わない。

## Foundationの文字選択補強

1.12.0と1.13.0-alpha02の公式配布ソース・バイナリを確認した。[ソースのSHA-256と配布元](foundation-selection-upstream-audit-2026-09-07.tsv)を記録した。

- Androidの `PlatformSelectionBehaviors.android.kt` は公開型・import以外に差分がなく、補強対象の範囲検証・例外処理は未修正。
- 従来APIの `TextFieldSelectionManager.kt` はファイル全体が同一。
- 新APIの描画時の範囲補正等の変更は、Android判別処理の今回の補強とは別。
- alpha02のAARにも対象クラス・メソッド・捕捉フィールドが存在し、2つの置換対象呼び出しをjavapで確認した。

このためFoundation自体をalpha02へ更新しつつ、補強を維持し、strictlyと補強処理の対象バージョン説明を更新した。上流修正が入り、補強なしで回帰テストを通過した時点でASM補強と固定指定を除去する。

## 検証

- 50構成で依存解決成功、未解決・外部アーティファクト取得失敗0件。
- キャッシュ欠落後に650個の外部アーティファクトを再取得し、全ファイルの存在と依存解決を再確認した。
- Wrapper JARのSHA-256も公式の配布チェックサムと一致した。
- 次の品質ゲートと追加ビルドが成功（5分14秒、195タスク）。

```sh
./gradlew --console=plain --max-workers=2 qualityGate :app-wear:assembleDebug :app-android:assembleDebugAndroidTest :baselineprofile:compileNonMinifiedReleaseKotlin
```

- shared JVM 1,543件、shared Android Host 1,531件、iOS Simulator 1,558件、app-android単体42件、計4,674件で失敗・エラー・スキップ0件。
- 品質契約、Android lint、Android / Wear Debug APK、Android計測テストAPK、Baseline Profileコードのコンパイル成功。
- 検証途中でディスク不足、Gradleプロセスの終了、取得済みキャッシュの欠落が発生した。中間検証で生成した不要成果物を整理し、依存を再取得し、並列数2で上記を完走した。これらの中断を成功扱いには含めていない。
- iOSの既存投稿画面テストは、旧ラベル「コメント」から実在する `compat-post-comment-field` 識別子を参照するよう修正した。アサーションの対象・操作範囲は維持する。
- Android API 37エミュレーターで文字選択の5テストクラス、27件に成功（45.779秒）。範囲外要求・異常な判別結果・サービス例外・キャンセル、長押し中の編集、回線情報の追記、下書き復元・リセットを検証した。[計測ログ](dependency-preview-selection-tests-2026-09-07.txt)。
- Xcode 26.6で、Firebaseを組み込んだiOSアプリと共有Frameworkのビルド・リンクに成功。iPhone SE（第3世代）/ iOS 26.5 Simulatorで通常・互換モードのUIテスト2件に成功（通常15.160秒、互換24.333秒）。[UIテストログ](dependency-preview-ios-tests-2026-09-07.txt)。
- iOSテストは保存状態の共有を避け、新規アプリデータで1件ずつ実行した。互換モードでは板・カタログ・スレッド・設定・投稿・手書き画面まで到達した。既存の状態を引き継いだ連続実行が成功したという意味ではない。
- `git diff --check` 成功。上記を合わせて4,703テスト成功。

iOSの実行コマンド（検証専用Simulatorのアプリデータを各テストの前に初期化）:

```sh
xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp \
  -destination 'platform=iOS Simulator,id=CAB763AF-5723-45D0-9361-C5AE33E36953' \
  -derivedDataPath build/DerivedData-dependency-preview -parallel-testing-enabled NO \
  -only-testing:iosAppUITests/IosAppUITests/testToshiakiCompatibilityProfileReachesForeground test

xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp \
  -destination 'platform=iOS Simulator,id=CAB763AF-5723-45D0-9361-C5AE33E36953' \
  -derivedDataPath build/DerivedData-dependency-preview -parallel-testing-enabled NO \
  -only-testing:iosAppUITests/IosAppUITests/testFutachaProfileReachesForeground test-without-building
```

公式情報: [Gradle公開版一覧](https://services.gradle.org/versions/all)、[Compose Foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation)、[Firebase Apple SDK](https://firebase.google.com/support/release-notes/ios)。各ライブラリの公式MavenメタデータURLは照合表を参照。
