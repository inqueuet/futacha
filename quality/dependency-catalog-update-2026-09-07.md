# 依存カタログへの追加（2026-09-07）

プロジェクトで使用するカタログは `gradle/libs.versions.toml`。先行監査で確認した直接利用APIについて、37件のライブラリエイリアスと1件のプラグインエイリアスを追加し、利用するsource set・モジュールへ接続した。カタログの登録だけで終わらせず、各宣言の実解決まで確認した。

追加した全エイリアス、バージョンの管理元、利用箇所は [dependency-catalog-update-2026-09-07.tsv](dependency-catalog-update-2026-09-07.tsv) に記録している。

## 追加対象

- Kotlin TestとKotlin CocoaPodsプラグイン。既存のKotlinバージョンキーを共用。
- OkHttp、Okio、kotlinx-io、Lifecycle Common / Runtime Compose、AndroidX Annotation、AndroidX Test Core / Monitor、Google Play services Tasks、Firebase Common、ML Kit Common / GenAI Common、アニメーションデコーダーのframeanimation。
- 独立した型・処理を直接使うMedia3 Common / Database / DataSource、Coil Core / Compose Core / Network Core、Ktor HTTP / IO / Utils、Serialization Core。
- 共通UIが直接使うCompose MultiplatformのUI / Graphics / Text / Unit / Geometry / Animation / Animation Core / Runtime Saveable。
- AndroidとWearが直接使うCompose Runtime / Text / Unit / Geometry、およびAndroid計測テストが使うUI Test。

sharedのGuavaとCore KTX、app-android/app-wearのCoroutines、baselineprofileとAndroid計測テストのJUnitは、既存のエイリアスを利用箇所へ追加した。WearにはAndroid Compose BOMを適用して、追加したバージョン省略のCompose依存を管理する。

## バージョンの扱い

新規バージョンキー10件は監査時に実解決された値を採用した。既存のライブラリ群は既存version.refを共用し、Firebase CommonとAndroid Compose部品はBOMからバージョンを供給する。

既存指定を適用したことで、次の実解決差が発生する。

| 対象 | 修正前 | 修正後 |
|---|---|---|
| shared / app-androidのGuava | 33.3.1-android | 33.7.1-android（既存カタログ値） |
| sharedのCore / Core KTX | 1.18.0 | 1.19.0（既存カタログ値） |
| shared Android compileのAnnotation | 1.9.1 | 1.10.0（Core側の依存整合） |
| Wear runtimeのmaterial-icons-extended | 1.7.6 | 1.7.8（既存Compose BOM） |
| Wear runtimeのmaterial-ripple | 1.9.3 | 1.12.0（既存Compose BOM） |

Guavaの内部依存もそのバージョンに追随する。Wearの未使用のandroidTest compile構成にもCompose・Coroutines等の整合が反映される。今回の作業はすべての実解決バージョンを維持する変更ではない。

## 独立した宣言を増やさない対象

- Activity Composeに対するActivity、Core KTXに対するCore、Work Runtime KTXに対するWork Runtime、DataStore Preferencesに対する内部Core、Foundationに対するLayout、Material Icons Extendedに対するIcons Core、Benchmark Macro JUnit4に対するMacroは、既に明示されている公開入口と同じバージョンの下位APIとして扱う。これらの公開入口は既存のカタログで管理している。
- FirebaseAnalyticsクラスの実体であるplay-services-measurement-api、Guavaが供給するListenableFutureの分割実体は、ライブラリの公開入口を維持する。
- Kotlin stdlib、Compose Hot Reload、Skikoなど、プラグインが供給する実装・ツール依存はプラグインの組み合わせに従う。
- iOSのPod、PythonのPillow、Gradle Wrapper、JDK、OS SDKは別エコシステムの管理対象であり、Gradleカタログのライブラリに架空のMaven座標を追加しない。

## 検証

- 全37ライブラリと1プラグインについて、version.refおよび実際の利用箇所を照合済み。
- Gradle設定評価成功。CocoaPodsプラグインのエイリアス経由での適用も成功。
- Android各構成・JVM・共通metadata・iOSを含む48構成で依存解決成功。未解決依存・外部アーティファクト取得失敗ともに0。
- `./gradlew qualityGate :app-wear:assembleDebug :app-android:assembleDebugAndroidTest :baselineprofile:compileNonMinifiedReleaseKotlin` 成功（4分32秒、192タスク）。
- 品質契約検証、sharedのJVM / Android Host / iOS Simulatorテスト、app-android単体テスト、Android lint、Android / Wear Debug APK、Android計測テストAPK、Baseline Profileコードのコンパイルを含む。
- Android計測テストはAPKのビルドまでで、端末上では実行していない。
- カタログ全108ライブラリ・12プラグインに有効な参照と利用箇所があり、追加TSVのソース行とも一致することを確認。`git diff --check` 成功。
