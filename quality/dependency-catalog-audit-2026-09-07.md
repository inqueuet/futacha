# 依存カタログ集約監査（2026-09-07）

> 以下は修正前の監査スナップショット。後続の対応は [カタログ追加結果](dependency-catalog-update-2026-09-07.md) を参照。

現在の作業ツリーでは、ASMを含む明示的なバージョン付きMaven依存宣言は `gradle/libs.versions.toml` に集約されている。ただし「利用しているライブラリをすべてカタログ管理している」という意味では未完了。カタログを経由しない宣言、直接利用する推移依存、利用モジュールで参照されていない既存エイリアス、別エコシステムの依存が残る。

この監査では依存設定を変更していない。開始前から `app-android/build.gradle.kts` と `gradle/libs.versions.toml` に未コミット変更があり、`buildSrc/` も未追跡だった。結論はHEADだけでなく、それらを含む作業ツリーに対するもの。

## 調査範囲と証跡

- ルート、shared、app-android、app-wear、baselineprofile、buildSrc の全6ビルドスクリプト、およびルート・buildSrcの全2 settings。
- `libs.versions.toml` の43バージョン、71ライブラリ、11プラグイン。全エイリアスの参照先とversion.refを照合。未参照エイリアス、参照先の欠落、同一ライブラリ座標の重複登録は見つからなかった。
- 通常のimplementation/apiだけでなく、constraints、platform/BOM、追加構成へのadd、プラグインID、Gradle API、除外指定、依存解決・置換処理、buildSrcのカタログ読込を確認。
- Git無視ファイルも再走査。ビルド生成物、Gradleキャッシュ、Pods展開物と、プロジェクトで管理する入力を区別した。追加のGradle定義・依存マニフェスト・同梱JAR/AARは見つからなかった（Gradle Wrapper JARを除く）。
- Kotlin/Java/Swiftのimportを照合し、Gradleが解決したJAR/AARのクラス・公開メソッド、必要箇所のローカルsources JARでAPIの所属を確認。Kotlinのtypealias、Native専用API、SDKの型は別途分類した。単なるパッケージ名一致を追加依存の根拠にはしていない。
- CocoaPods、Xcodeプロジェクト、C interop、補助スクリプト、Wrapper、JDK設定も確認。

証跡は [dependency-catalog-audit-2026-09-07/](dependency-catalog-audit-2026-09-07/) に保存した。

| ファイル | 内容 |
|---|---|
| [declarations.tsv](dependency-catalog-audit-2026-09-07/declarations.tsv) | 119件のカタログ参照・カタログ外宣言とソース行 |
| [resolved-versions.tsv](dependency-catalog-audit-2026-09-07/resolved-versions.tsv) | 実際に解決された743組のモジュール・バージョンと適用構成。推移依存・プラットフォーム別実体を含む |
| [checked-configurations.tsv](dependency-catalog-audit-2026-09-07/checked-configurations.tsv) | コンパイル・実行・テスト・Native/metadata・buildSrcの50構成と解決件数 |
| [input-sha256.tsv](dependency-catalog-audit-2026-09-07/input-sha256.tsv) | 監査対象のGradle/CocoaPods入力のハッシュ |

743組をすべて集約漏れと判定しているわけではない。ライブラリ内部の推移依存やOS別の実体も含まれるため、以下で直接利用・プラグイン管理・別管理を分けている。

## 1. ASM周辺は集約済み

| 対象 | 定義・参照 | 実解決 |
|---|---|---|
| ASM | TOMLの `asm` → buildSrc/build.gradle.kts:16 | `org.ow2.asm:asm:9.9` |
| AGP API | `android-gradle-api` → buildSrc/build.gradle.kts:10 | `com.android.tools.build:gradle-api:9.4.0` |
| Kotlin Gradle Plugin API | `kotlin-gradle-plugin-api` → buildSrc/build.gradle.kts:15 | `org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.4.10` |
| Foundationの厳密な固定 | `composeFoundationGuard = { strictly = "1.12.0" }` と `androidx-compose-foundation-guarded` → app-android/build.gradle.kts:205 | `foundation-android:1.12.0` |

buildSrc/settings.gradle.kts:3 は `../gradle/libs.versions.toml` を明示的に読み込んでいる。buildSrcのcompile/runtime両構成で上記3依存の解決を確認した。

`because("...1.12.0...")` やパッチのJavaコメント、調査資料内の過去バージョン表記は依存を決定しないため、バージョン直書きの残存に数えない。

## 2. 明示的に宣言されているがカタログ外の2件

| 場所 | 対象 | 判定 |
|---|---|---|
| shared/build.gradle.kts:83 | `implementation(kotlin("test"))` → `org.jetbrains.kotlin:kotlin-test` | ライブラリエイリアス未登録。独立した数値直書きではなく、Kotlinプラグインの2.4.10に追随する |
| shared/build.gradle.kts:9 | `id("org.jetbrains.kotlin.native.cocoapods")` | プラグインエイリアス未登録。バージョンは既存Kotlinプラグインのクラスパスから供給される |

「すべてlibs参照に統一」が要件なら対象になる。ただし、これらを「別バージョンが直書きされている」と説明するのは不正確。

## 3. ソースで直接利用するがカタログに登録がない依存

以下は外部APIを直接使っていることを確認できた依存。各ライブラリ群の上位APIから公開される推移依存も含む。現在のビルドが依存不足で失敗するという意味ではなく、直接利用するライブラリまで明示管理する場合の対象一覧。

### 3.1 個別に管理対象を検討すべき依存

| 未登録モジュール | 確認した解決バージョン | 直接利用の証拠 |
|---|---|---|
| `com.squareup.okhttp3:okhttp` | 5.3.2 | shared/src/androidMain/kotlin/network/HttpClientFactory.android.kt:10–13 のInterceptor / ConnectionPool / MediaType / ResponseBody |
| `com.squareup.okio:okio` | sharedでは3.18.1 | shared/src/commonMain/kotlin/ui/image/ImageLoaderProvider.kt:50–51 のFileSystem / Path。Android・iOS・テストでもBuffer等を使用 |
| `org.jetbrains.kotlinx:kotlinx-io-core` | 0.9.1 | shared/src/commonTest/kotlin/network/HttpBoardApiSupportTest.kt:6 のreadByteArray、HttpBoardApiTest.ktのIOException |
| `androidx.lifecycle:lifecycle-common` | 利用するAndroid構成では2.9.4 | shared/src/androidMain/kotlin/ui/board/PlatformBackgroundLifecycleEffect.android.kt:7–8 のLifecycle / LifecycleEventObserver、app-android/src/main/java/com/valoser/futacha/MainActivity.kt:19 のlifecycleScope |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.4 | 同PlatformBackgroundLifecycleEffect.android.kt:9 のLocalLifecycleOwner |
| `androidx.media3:media3-common` | 1.11.0 | AndroidVideoPlaybackCache.kt:5 のUnstableApi、PlatformVideoPlayer.android.ktのPlayer / MediaItem |
| `androidx.media3:media3-database` | 1.11.0 | shared/src/androidMain/kotlin/ui/board/AndroidVideoPlaybackCache.kt:6 のStandaloneDatabaseProvider |
| `androidx.media3:media3-datasource` | 1.11.0 | 同ファイル:7以降のDataSource / SimpleCache等、Android計測テスト |
| `androidx.test:core` | 1.7.0 | app-android/src/androidTest/java/com/valoser/futacha/ArchiveReportOutboxInstrumentedTest.kt:4 のApplicationProvider |
| `androidx.test:monitor` | 1.8.0 | baselineprofile/src/main/kotlin/com/valoser/futacha/baselineprofile/CriticalJourneys.kt:4 等のInstrumentationRegistry |
| `com.google.android.gms:play-services-tasks` | 18.4.0 | app-wear/src/main/java/com/valoser/futacha/wear/sync/WatchSnapshotStore.kt:7 のTasks。Android側もTask APIを使用 |
| `com.google.firebase:firebase-common` | 22.2.0 | shared/src/androidMain/kotlin/analytics/FirebaseInitialization.android.kt:4 のFirebaseApp。バージョンは既存Firebase BOMの管理下 |
| `com.google.mlkit:common` | 18.11.0 | shared/src/androidMain/kotlin/ai/OnDeviceAiService.android.kt:28 のMlKitContext |
| `com.google.mlkit:genai-common` | 1.0.0-beta4 | 同ファイル:18–21 のDownloadCallback / DownloadStatus / FeatureStatus / GenAiException |
| `androidx.annotation:annotation` | app-androidでは1.10.0 | app-android/src/main/java/com/valoser/futacha/FutachaAppFunctionService.kt:12 のRequiresApi |
| `com.github.penfeizhou.android.animation:frameanimation` | 3.0.5 | shared/src/androidMain/kotlin/ui/image/CompatApngDecoder.android.kt:12 のStreamReader |

API所属について、現行2.9.4の `lifecycleScope` は `lifecycle-common` のLifecycleOwner.ktに存在する。名前だけから `lifecycle-runtime-ktx` が必要と推定してはいけない。`InstrumentationRegistry` も実体は `androidx.test:monitor` であり、`runner` と同一視しない。

### 3.2 既存ライブラリ群の下位API・Compose部品

次の部品も独立したカタログエイリアスはない。上位ライブラリと共通バージョン・BOMで供給されるものが多く、独立したバージョンキーを増やす必要があるとは限らない。

| ライブラリ群 | 直接利用される未登録モジュール | 例・既存の供給元 |
|---|---|---|
| Compose Multiplatform | `org.jetbrains.compose.ui:ui`, `ui-graphics`, `ui-text`, `ui-unit`, `ui-geometry` | commonMainのModifier / Color / TextRange / dp / Offset。foundationやmaterial3経由 |
| Compose Multiplatform | `org.jetbrains.compose.animation:animation`, `animation-core` | GlobalSettingsComponents.ktのAnimatedVisibility、CompatibilitySecondaryScreens.ktのAnimatable |
| Compose Multiplatform | `org.jetbrains.compose.foundation:foundation-layout` | Row / Column / Box。foundation経由 |
| Compose Multiplatform | `org.jetbrains.compose.material:material-icons-core` | Icons。material-icons-extended経由 |
| Compose runtime | `androidx.compose.runtime:runtime`, `runtime-saveable`, `runtime-annotation` | Composable / rememberSaveable / Immutable。現在のCMPはruntimeのAndroidX実体を解決する。`jetbrains-compose-runtime` は既登録だがsaveable/annotation個別エイリアスはない |
| Android Compose | `androidx.compose.animation:animation`, `animation-core`; `androidx.compose.foundation:foundation-layout`; `androidx.compose.material:material-icons-core` | sharedのAndroid実体やアプリUI。上記CMP部品のAndroid側も含む |
| Android Compose UI | `androidx.compose.ui:ui-text`, `ui-unit`, `ui-geometry`, `ui-test` | TextFieldValue / dp / Offset / onNodeWithContentDescription。uiやui-test-junit4経由 |
| Activity | `androidx.activity:activity` | ComponentActivity / ActivityResultContracts。activity-compose経由 |
| Core | `androidx.core:core` | WindowCompat等。core-ktxに対する基底API |
| DataStore | `androidx.datastore:datastore-core`, `datastore-preferences-core` | ReplaceFileCorruptionHandler / MutablePreferences。datastore-preferences経由 |
| WorkManager | `androidx.work:work-runtime` | WorkManager / CoroutineWorker / Constraints。work-runtime-ktx経由 |
| Benchmark | `androidx.benchmark:benchmark-macro` | BaselineProfileMode等。benchmark-macro-junit4経由 |
| Coil 3 | `io.coil-kt.coil3:coil-core`, `coil-compose-core`, `coil-network-core` | ImageLoader / AsyncImagePainter / NetworkRequest / DeDupeConcurrentRequestStrategy。coil-compose / coil-network-ktor3経由 |
| Ktor | `io.ktor:ktor-http`, `ktor-io`, `ktor-utils` | ContentType / ByteReadChannel / GMTDate。ktor-client-core等から公開 |
| kotlinx.serialization | `org.jetbrains.kotlinx:kotlinx-serialization-core` | Serializable / builtins。kotlinx-serialization-json経由 |
| Firebase Analyticsの実体 | `com.google.android.gms:play-services-measurement-api` | FirebaseAnalyticsクラスの実体。利用入口のfirebase-analyticsは登録済み。実体クラス所属だけを理由に直接宣言へ変える必要はない |
| Guavaの分割部品 | `com.google.guava:listenablefuture` | ListenableFuture。guavaやAndroidXから供給。guava本体エイリアスは登録済み |

`-android` / `-jvm` / `-desktop` / `-iosarm64` 等のプラットフォーム別実体はTSVに記録した。通常は共通の依存座標を管理するため、実体ごとにエイリアスを作る対象として重複計上しない。

## 4. カタログにあるのに利用モジュールで宣言していない依存

| 場所 | 対象 | 状態・影響 |
|---|---|---|
| shared/androidMain | Guava | OnDeviceAiService.android.kt:16–17でListenableFuture / MoreExecutorsを使用するが `libs.guava` 宣言なし。エイリアスを参照するのはapp-wearだけ |
| shared/androidMain | Core KTX | FileManagerPickerDialog.android.kt:35でCore KTXのtoBitmapを直接使用するが `libs.androidx.core.ktx` 宣言なし。app-android/app-wearの宣言はsharedのコンパイル依存にはならない |
| app-android、app-wear | kotlinx-coroutines-core | CoroutineScope / Dispatchers / flow等を直接使うが両モジュールに `libs.kotlinx.coroutines.core` 宣言なし |
| app-wear | Composeのfoundation / runtime / ui | app-wear/src/main/java/com/valoser/futacha/wear/WearMainActivity.kt:11以降で通常ComposeのAPIを直接使うがWear Compose等からの供給に依存 |
| baselineprofile、app-android/androidTest | JUnit | org.junitを直接使う。baselineprofileに `libs.junit` はなく、app-androidではtestImplementationだけに明示宣言がある。計測テストはAndroidX Test等経由 |

実際に確認したバージョン差は次のとおり。異なる構成であること自体を不具合とは判定しないが、「TOMLの値が全モジュールに適用される」という説明は成立しない。

| 対象 | カタログまたは管理元 | sharedのAndroid compile | app-androidのdebug compile/runtime | app-wearのdebug runtime |
|---|---|---|---|---|
| Guava | `guava = 33.7.1-android` | 33.3.1-android | 33.3.1-android | 33.7.1-android |
| Core KTX | `coreKtx = 1.19.0` | 1.18.0 | 1.19.0 | 1.19.0 |
| Android material-icons-extended | app-androidはCompose BOM、sharedはCMP側から供給 | 1.7.6 | 1.7.8 | 実解決一覧参照 |

カタログへの登録だけでは全構成のバージョン制約にはならない。依存を使うsource setでの参照、または意図したplatform/constraintsの適用が必要。

## 5. プラグインが自動追加する依存

- Kotlin stdlib、Kotlinテストのプラットフォーム実装・JUnit連携はKotlinプラグインが供給する。独立した数値直書きの残存ではない。
- ComposeプラグインはHot Reload 1.2.0のruntime、runtime-api、MCP構成等を追加している。ユーザーのGradleファイルにもTOMLにもHot Reloadの個別バージョン指定はない。実行時構成ではruntimeの推移依存も解決される。
- Skiko 0.150.1とNative/JVM向け実体はComposeの依存グラフから供給される。直接Skiko APIを使うimportは見つからなかった。
- コルーチンのAndroid Main dispatcher等も推移依存として存在する。単に推移依存一覧に載ることと、未登録の直接利用ライブラリとは区別する。
- `compileOnly(gradleApi())` と `plugins { java }` はGradle自身のAPI・組み込みプラグイン。通常のMaven版管理対象には含めない。

自動追加分まで全てTOMLへ強制固定するとプラグインとの組み合わせを変更するため、この監査では集約漏れの修正とは扱わない。

## 6. iOS・補助ツールの別管理

| 対象 | 現在の管理 | 判定 |
|---|---|---|
| FirebaseAnalytics / FirebasePerformance / FirebaseCrashlytics（iOS） | iosApp/Podfile:6–8でバージョン省略、Podfile.lockでは各12.11.0 | TOMLのAndroid Firebase BOM 34.18.0とは別管理。Gradleの直書き残存ではないが、全プラットフォーム一元管理は未達 |
| FirebaseCore（iOS） | iosApp/iosApp/iOSApp.swift:4で直接import、Podfileには直接宣言なし。lockでは12.11.0 | CocoaPods側でも直接利用を推移依存に任せている |
| その他のPod | Podfile.lock | FirebaseABTesting/CoreExtension/CoreInternal/Installations/RemoteConfig/RemoteConfigInterop/Sessions/SharedSwift、GoogleAdsOnDeviceConversion/AppMeasurement/DataTransport/Utilities、nanopb、PromisesObjC/Swift。推移依存として管理済み |
| Pillow | tools/compare_compat_golden.py:10の `from PIL import ...` | requirements.txt / pyproject.toml等の宣言が見つからず、補助Pythonツールの依存は未管理。Gradleカタログとは別の対象 |
| SQLite、AppleのFileProvider / StoreKit等、Android/Apple SDK API | cinteropのsqlite3.def、linkerOpts、OS SDK | OS提供ライブラリ。Mavenカタログ集約漏れではない |
| shared Podの1.8 | shared/build.gradle.kts:59 / shared.podspec | 自プロジェクトの成果物バージョン |
| Gradle 9.6.0 / Daemon JDK 21 | Wrapper properties / gradle-daemon-jvm.properties | ビルド起動前に必要なツール設定 |
| compileSdk / targetSdk / minSdk / iOS deployment target / app version | 各プラットフォーム設定 | ライブラリの依存バージョンではない |

Xcodeプロジェクトに外部Swift Packageの宣言は見つからなかった。CocoaPodsの `pod install` / `pod update` は実行していない。

## 7. 検証結果と限界

- Gradleが評価した全プロジェクトの依存宣言・制約と、選定した50構成の実解決を調査用init scriptで取得した。ビルド本体のスクリプトは編集していない。
- 最終実行はすべて解決成功。未解決依存0、外部アーティファクト取得失敗0。初回オフライン実行で未キャッシュだったHot Reloadは通常の依存解決を再実行し解消した。
- 50構成にはAndroid各ビルド種別のcompile/runtime、Android単体・計測テスト、sharedのAndroid/JVM/metadata、iOS Arm64 main・Simulator Arm64 test、buildSrc compile/runtimeを含む。全てのプラグイン内部ツール構成、全Nativeターゲットの全組み合わせを解決したという意味ではない。
- プロジェクト間の成果物はアーティファクト取得から除外し、外部ライブラリを検査した。依存グラフ上のプロジェクト間の辺は取得している。APKビルド、アプリテスト、実機動作確認は本調査の検証対象ではない。
- importと公開APIの所属確認は補助的な静的監査であり、コンパイラによる「使用する直接依存の最小集合」の証明ではない。上位ライブラリの公開APIとして利用してよい下位モジュールを一律に不具合とは判定しない。
- AGP等のプラグイン内部にある全推移依存の固定、最新バージョン調査、脆弱性・ライセンス監査は行っていない。

修正に進む場合は、(1) 明示宣言2件の表記統一、(2) 直接利用する独立ライブラリの登録と利用source setでの参照、(3) Guava/Core等の意図するバージョンの統一、(4) 同一ライブラリ群の公開下位APIをどこまで明示するかの規則化、(5) CocoaPods/Pythonの別管理の整備、の順に扱える。BOMやプラグインが管理する依存に独立バージョンを重複追加するだけでは、一元管理の改善にならない。
