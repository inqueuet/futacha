# 依存ライブラリの最新版確認・更新（2026-09-07）

同日の追加確認とalpha許可後の更新は [プレビューを含む依存更新](dependency-preview-update-2026-09-07.md) を参照。以下は先行した更新時点の記録。

`libs.versions.toml` の全108ライブラリ、12プラグインのmarker、3プラグインの実装座標、計123件のMavenメタデータを公式配布リポジトリから取得した。全53バージョンキーの管理対象を網羅し、確認不能の座標は0件。

照合結果と個別の取得元は [dependency-latest-audit-2026-09-07.tsv](dependency-latest-audit-2026-09-07.tsv) に記録した。安定版を使用する依存は最新安定版に更新し、既にプレビュー版を使用するML Kit GenAIとBaseline Profileプラグインは現在の系列の最新公開版であることを確認した。

## 更新内容

| バージョンキー | 修正前 | 修正後 | 公式情報 |
|---|---|---|---|
| asm | 9.9 | 9.10.1 | [ASMリリース履歴](https://asm.ow2.io/versions.html) |
| okhttp | 5.3.2 | 5.5.0 | [OkHttpリリース履歴](https://lysine.dev/okhttp/changelogs/changelog/#version-550) |
| okio | 3.18.1 | 3.18.2 | [Okioリリース履歴](https://github.com/lysine-dev/okio/blob/main/CHANGELOG.md#version-3182) |
| lifecycle | 2.9.4 | 2.11.0 | [AndroidX Lifecycleリリース履歴](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0) |
| playServicesTasks | 18.4.0 | 18.4.1 | [Google Maven配布メタデータ](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-tasks/maven-metadata.xml) |

Lifecycleの2つのカタログ項目は同じversion.refで更新する。Kotlin 2.4.20-RC3、AGP 9.5.0-alpha04、Lifecycle 2.12.0-alpha02等への新たなプレビュー移行は行わない。現在のKotlin 2.4.10、AGP 9.4.0、Compose Multiplatform 1.12.0などは最新安定版。

## 構成間の適用漏れ対策

バージョンキーの更新後に実際の依存グラフを比較し、次の宣言も調整した。

- sharedのLifecycle Common / Runtime ComposeをcommonMainで宣言してAndroid・JVM・iOSの管理元を揃える。
- shared/jvmMainでOkHttpのカタログを明示し、Ktor経由の旧5.3.2へ戻らないようにする。
- shared/androidMainでTasksのカタログを明示し、ML Kit等から旧18.4.0が入る構成も18.4.1へ揃える。

アプリ・テストで使う直接依存の更新であり、Gradle・AGP・Benchmark等の内部ツール依存をすべて最新へ強制する変更ではない。BOM管理のFirebaseとAndroid Composeは既存の最新BOMを維持する。Kotlin stdlib、Hot Reload、Skikoなどの自動依存、CocoaPodsやPython等の別管理は今回の更新対象に含めない。

## 既存修正との関係

文字選択修正（作業中に別コミット `cbbe532` として確定）で使うFoundationのstrictly 1.12.0は、最新安定版と一致するため維持した。ASMはそのビルド時処理でも利用されるため、buildSrcの解決とアプリAPKのビルドを検証に含める。

Coil 2の `io.coil-kt:coil-compose` は同じ座標では2.7.0が最新。Coil 3は別の座標・APIであり、このアプリでは既に別項目で3.6.2を使用している。カタログを全て最新版と呼ぶために別APIへの置換や推移依存の強制上書きは行わない。

## 検証

- 公式メタデータ123件を照合。全53バージョンキーが最新安定版または既存系列の最新プレビュー版と一致することを確認。
- Android / JVM / iOS / metadataの48構成とbuildSrcの2構成、計50構成で依存解決成功。未解決依存・外部アーティファクト取得失敗0件。
- 初回のバージョン更新後と、構成別の宣言調整後にそれぞれ品質ゲートを実行。最終状態で以下のコマンドが成功（1分52秒、192タスク）。

```sh
./gradlew qualityGate :app-wear:assembleDebug :app-android:assembleDebugAndroidTest :baselineprofile:compileNonMinifiedReleaseKotlin
```

- shared JVM 1,543件、shared Android Host 1,531件、iOS Simulator 1,558件、app-android単体42件、計4,674件のテストで失敗・エラー0件。
- 品質契約検証、Android lint、Android / Wear Debug APK、Android計測テストAPK、Baseline Profileコードのコンパイル成功。
- ASM 9.10.1でbuildSrcを再コンパイルし、既存の文字選択補強を適用したAPKのビルド成功。Foundationの固定値は1.12.0を維持。
- Android計測テストはAPKのビルドまで。端末上での計測テスト実行は行っていない。
- `git diff --check` 成功。
