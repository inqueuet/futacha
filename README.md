# ふたちゃ

ふたば☆ちゃんねるの閲覧・投稿・保存に対応した、Kotlin Multiplatform / Compose Multiplatform 製ブラウザです。Android、iOS / iPadOS、macOS、Windows で UI と主要ロジックを共有します。Wear OS と watchOS には、スマートフォン側の履歴や監視情報を確認・操作する連携アプリがあります。

## 対応プラットフォーム

| プラットフォーム | 対象 | アプリ |
| --- | --- | --- |
| Android | Android 8.0（API 26）以降 | ブラウザ本体 |
| iOS / iPadOS | 18.2 以降 | ブラウザ本体 |
| macOS | 13.0 以降・Apple Silicon（arm64） | Compose Desktop 版。詳しくは [Mac 版](docs/macos-port-2026-09-19.md) |
| Windows | Windows 10 / 11・x64 | Compose Desktop 版。詳しくは [Windows 版](docs/windows-port-2026-09-20.md) |
| Wear OS | Android API 26 以降の Wear OS 端末 | Android 版との連携アプリ |
| watchOS | 10.0 以降 | iPhone 版との連携アプリ |

ブラウザ本体の画面、状態管理、通信、HTML 解析、保存処理は `shared/` に集約し、OS 固有処理を各ターゲットで実装しています。時計用の画面はそれぞれのホストで実装しています。Intel Mac、Windows ARM64、Linux 向けのデスクトップ配布構成はありません。最低 OS はビルド設定上の値で、各 OS・端末での検証範囲は上記の移植記録と品質確認資料を参照してください。

## 主な機能

### 板・カタログ

- 板の追加、公式板一覧からの一括追加、改名、削除、ピン留め、並べ替え
- グリッド / リスト表示、列数変更、カタログモードと並び順の切り替え
- 検索、詳細 NG 条件、監視ワード、板ごとの設定
- 更新、更新前への復帰、消えたスレッドの確認、スレッド作成、履歴への自動追加
- 「ふたちゃモード」と「としあき（仮）モード」の表示プロファイル切り替え

### スレッド

- スレッドの取得・更新、タブ一覧・移動・復元、履歴、既読位置とスクロール位置の復元
- 新着の先頭に「新着レス ○件」の帯を表示、新着位置への移動
- 本文検索、検索結果移動、引用プレビュー、引用返信、絞り込み、並べ替え
- レス投稿、画像・動画添付、手書き添付、あぷ小、音声入力などの投稿ツール
- としあきモードの板別・スレッド別下書き保存（ふたちゃ側の永続保存・モード間共有は未接続）
- そうだね、削除依頼、本人削除
- 本文読み上げ、自動スクロール、手動・自動巡回、更新通知
- キャッシュ検索、落ちたスレッドの第三者アーカイブ取得・欠落補完

### メディア・保存

- 画像、GIF、WebP、APNG、動画の表示
- 添付ギャラリー、前後移動、拡大縮小、動画再生、画像検索
- スレッドの保存形式選択、画像・動画の選択一括保存
- 保存済みスレッドの一覧、オフライン閲覧、個別削除、一括削除、使用容量表示
- 定期的な自動保存と、通信失敗時のローカルコピーへのフォールバック
- 画像のモザイク・黒塗り、自動検出、輪郭の抽出・修正、編集結果の新規保存
- 動画の時間・範囲指定による編集、自動検出・追尾・輪郭、プレビュー、MP4 保存
- 対応メディアの生成情報・プロンプト表示、コピー

画像編集・動画編集・プロンプト表示は個別の設定で有効にする機能で、初期状態は OFF です。モデルを使う処理には、利用者によるモデルの取得・読み込みが必要です。プロンプト設定の入口はふたちゃモードにあります。iOS の WebM 再生は OS・端末の対応に依存し、編集入力としての WebM とは対応範囲が異なります。編集形式・解像度などの制限はアプリ内のメディア機能ヘルプと [移植範囲](docs/toshikari-media-port-remaining-plan-2026-09-18.md)、各デスクトップ版の説明を参照してください。

### 設定・連携

- テーマ、表示、操作、NG、Cookie、キャッシュ、保存先、通知などの設定と永続化
- 両モードで共通化した設定を、表示・操作・カタログ・メディア機能・保存・通信・バックアップ等の既存分類へ統合
- テーマ・文字色・システムバーの配色はモードごとに管理。ふたちゃの追加設定画面やメニューも、ふたちゃで選択したテーマに合わせて表示
- Android のディレクトリ選択と優先ファイラー、iOS の security-scoped bookmark、デスクトップのファイル・フォルダー選択
- 履歴・設定の入出力、バックアップ・復元、旧版データの取り込み、外部アプリ連携
- アプリロック、プライバシーフィルタ、通報・ブロック関連機能
- Android / iOS の AI コマンド・Deep Link、端末の対応状況に応じた要約・モデレーション補助
- Wear OS への履歴・監視結果・読み上げ状態の同期、Tile、スマートフォン操作
- watchOS への板・スレッド・未読情報の同期、iPhone 側の画面表示・更新・読み上げ操作

機能の入口や対応範囲にはモード差・OS 差があります。通常の画像一覧・プレビューへの共通設定反映、下書き共有、巡回結果の詳細表示などの残件は [モード間の機能差](docs/futacha-remaining-feature-gaps-2026-09-21.md)、共通化済みの範囲は [機能・設定の共通化記録](docs/futacha-feature-parity-2026-09-20.md) を参照してください。外部にじろぐ連携は Android のみです。デスクトップ版の端末 AI による文章生成・モデレーションには未対応で、要約は本文からの抜粋を使用します。

変更履歴はアプリ内の設定から確認できます。内容は [変更履歴データ](shared/src/commonMain/kotlin/ui/compat/CompatibilityReferenceInfoData.kt) で管理しています。

## プロジェクト構成

```text
.
├── app-android/   Android ホストアプリ、WorkManager、通知、端末連携
├── app-wear/      Wear OS アプリ、Tile、Data Layer 連携
├── app-desktop/   Windows / Apple Silicon Mac の Compose Desktop ホスト
├── baselineprofile/ Android の起動最適化プロファイル生成・性能計測
├── buildSrc/      Android の文字選択補強などのビルド処理
├── iosApp/        SwiftUI ホスト、Xcode プロジェクト、watchOS ホスト
├── shared/        共通 UI、状態、通信、解析、保存、プラットフォーム実装
├── quality/       機能・挙動・リリース確認用の回帰契約
├── tools/         ネイティブ依存の準備、配布物生成、検証スクリプト
└── docs/          実装・移植・検証範囲の記録
```

ビルド・テストに必要なコード、開発用スクリプト、品質契約、実装記録を含めています。署名鍵・ローカル設定・生成物・検証ログは Git 管理対象外です。文書に記載した `build/` 以下の証跡は、Clone したソースには含まれません。
Android の Release ビルドでは `baselineprofile/` を使い、専用の API 35 エミュレーターで最適化プロファイルを生成します。

`shared/src/` の主な領域は次のとおりです。

- `commonMain`: Compose UI、モデル、状態管理、通信、パーサー、Repository、Service
- `androidMain`: Android のストレージ、メディア、通知、Activity 連携など
- `iosMain`: iOS のストレージ、メディア、BGTask、UIKit / SwiftUI 連携など
- `jvmMain`: Windows / Mac のファイル選択・永続化・動画再生・編集・推論・追尾
- `commonTest` / `androidHostTest` / `iosTest` / `jvmTest`: 共通・プラットフォーム別テスト

主な共通 UI エントリーポイントは `shared/src/commonMain/kotlin/ui/FutachaApp.kt`、としあきモードの画面は `shared/src/commonMain/kotlin/ui/compat/CompatibilityApp.kt` です。Android は `app-android/`、iOS は `iosApp/`、macOS / Windows は `app-desktop/` が共通画面をホストします。Wear OS は `app-wear/`、watchOS は `iosApp/watchApp/` に専用 UI とスマートフォンとの通信処理があります。

## 主な技術要素

- Kotlin 2.4 / Kotlin Multiplatform
- Compose Multiplatform
- Ktor Client
- kotlinx.coroutines / kotlinx.serialization
- DataStore / NSUserDefaults、モード固有の永続ストア、SQLite
- Coil 3、Android Media3、iOS AVPlayer / WKWebView、デスクトップ libVLC
- メディア編集・検出・追尾用の ONNX Runtime、OpenCV、デスクトップ JavaCV / FFmpeg
- Android WorkManager / iOS BGTask
- Wear OS Data Layer / Apple WatchConnectivity
- Firebase Analytics / Crashlytics / Performance（Android / iOS、設定ファイルがある場合に初期化）

依存ライブラリの具体的な版数は [バージョンカタログ](gradle/libs.versions.toml) と各モジュールの `build.gradle.kts`、[Podfile](iosApp/Podfile) を参照してください。

## 必要な環境

- 共通: Git、JDK 17、Python 3、初回の依存取得用ネットワーク接続
- Gradle デーモン: JDK 21。[デーモン JVM 設定](gradle/gradle-daemon-jvm.properties) に取得先を指定しています。コンパイル・Windows アプリ実行用の JDK 17 とは別です。
- Android / Wear OS: Android SDK 37。Android 本体のネイティブ処理には NDK `29.0.14206865` と SDK の CMake `3.22.1` を使用します。
- iOS / watchOS: macOS、Xcode（iOS 18.2 以降の SDK）、CocoaPods、CMake 3.21 以降。共有 Framework は実機 arm64 と Apple Silicon の Simulator arm64 を対象にしています。
- macOS デスクトップ: Apple Silicon Mac、Xcode Command Line Tools、CMake 3.21 以降
- Windows デスクトップ: x64 Windows、Python 3.11 以降。ネイティブ依存の準備に使う LLVM MinGW・CMake・Ninja はスクリプトで取得します。

Gradle Wrapper を同梱しているため、Gradle の個別インストールは不要です。ルートのビルドは Android モジュールも読み込むので、デスクトップをビルドする場合も Android SDK の場所を設定してください。macOS 上で全体の品質ゲートを実行する場合は iOS とデスクトップの開発環境も必要です。

## セットアップ

### 1. Clone

```bash
git clone https://github.com/inqueuet/futacha.git
cd futacha
```

### 2. Android SDK

Android Studio で開くか、ルートの `local.properties` に SDK の場所を設定します。

```properties
sdk.dir=/path/to/Android/sdk
```

### 3. Android

Debug APK を生成します。

```bash
./gradlew :app-android:assembleDebug
```

出力先は `app-android/build/outputs/apk/debug/app-android-debug.apk` です。

接続中の端末または Emulator へインストールする場合:

```bash
./gradlew :app-android:installDebug
```

Release AAB を署名するには、次の値を環境変数、ローカルの Gradle プロパティ、または Git 管理外の `local.properties` に設定します。解決順は `local.properties` → Gradle プロパティ → 環境変数です。

| 設定名 | 内容 |
| --- | --- |
| `FUTACHA_RELEASE_STORE_FILE` | キーストアのパス（相対パスはリポジトリ直下基準） |
| `FUTACHA_RELEASE_STORE_PASSWORD` | キーストアのパスワード |
| `FUTACHA_RELEASE_KEY_ALIAS` | 署名鍵のエイリアス |
| `FUTACHA_RELEASE_KEY_PASSWORD` | 署名鍵のパスワード |

```bash
./gradlew :app-android:bundleRelease
```

出力先は `app-android/build/outputs/bundle/release/app-android-release.aab` です。設定した鍵での署名には上記の全項目が必要です。Release ビルドは専用の Gradle Managed Device `futachaBaselineApi35` で Baseline Profile を自動生成するため、Android Emulator と API 35 の AOSP システムイメージを利用できる環境が必要です。接続中の実機はプロファイル生成に使用しません。

### 4. Wear OS

```bash
./gradlew :app-wear:assembleDebug
```

スマートフォン版との同期には、ペアリング済みの Wear OS 端末または Emulator が必要です。

### 5. iOS / watchOS

CocoaPods の依存関係を準備し、Xcode の workspace を開きます。共有 Framework は Xcode のビルド時に生成します。

```bash
./gradlew :shared:podInstall
open iosApp/iosApp.xcworkspace
```

Xcode で `iosApp` scheme と Simulator または実機を選択して起動します。Apple Watch 版は `watchApp` scheme を使用し、iPhone とペアリングした Watch / Simulator で確認します。実機署名が必要な場合は、雛形をローカル設定へコピーして値を設定してください。

```bash
cp iosApp/Configuration/Local.xcconfig.example \
  iosApp/Configuration/Local.xcconfig
```

`Local.xcconfig` は Git の追跡対象外です。

### 6. macOS

Apple Silicon Mac 上で起動・DMG 生成を行います。

```bash
./gradlew :app-desktop:run
./gradlew :app-desktop:packageReleaseDmg
```

DMG の出力先は `app-desktop/build/compose/binaries/main-release/dmg/`、アプリは `app-desktop/build/compose/binaries/main-release/app/Futacha.app` です。Java ランタイムとメディア用ライブラリを同梱します。ローカルの配布設定には Developer ID 署名・Apple 公証を含みません。OS 連携・保存先・検証範囲は [Mac 版の説明](docs/macos-port-2026-09-19.md) を参照してください。

### 7. Windows

x64 Windows 上の PowerShell で実行します。

```powershell
.\gradlew.bat :app-desktop:run
.\gradlew.bat :app-desktop:packageWindowsZip
.\gradlew.bat :app-desktop:packageReleaseMsi
.\gradlew.bat :app-desktop:packageWindowsStoreMsix
```

ZIP は `app-desktop/release/`、MSI は `app-desktop/build/compose/binaries/main-release/msi/` に出力します。ZIP を展開したフォルダー全体に Java ランタイムとネイティブ依存を同梱するため、`Futacha.exe` だけを取り出さずに使用します。MSI 用の WiX は Compose の配布タスクが取得します。詳しくは [Windows 版の説明](docs/windows-port-2026-09-20.md) を参照してください。

Microsoft Store 提出用の未署名 MSIX は `app-desktop/build/compose/binaries/main-release/msix/` に出力します。Windows SDK の `MakeAppx.exe` が必要です。製品ID、ローカル確認、Partner Centerでの提出手順は [Microsoft Store公開手順](docs/windows-store-2026-09-22.md) を参照してください。

両デスクトップ版とも初回は `prepareDesktopResources` が依存ライブラリを準備し、OpenCV の追尾ブリッジをビルドします。Android・iOS / watchOS・Wear OS・デスクトップの版数は各ビルド設定で個別に管理しています。

## Firebase（任意）

Android / iOS は Firebase の設定ファイルがなくてもビルドと起動が可能です。Analytics / Crashlytics / Performance を利用する場合は、Firebase Console から取得したファイルを次の場所へ配置します。

| プラットフォーム | ファイル | 配置先 |
|---|---|---|
| Android | `google-services.json` | `app-android/google-services.json` |
| iOS | `GoogleService-Info.plist` | `iosApp/iosApp/GoogleService-Info.plist` |

これらの設定ファイル、署名情報、実行時に生成されるユーザーデータは Git の追跡対象外です。値をソースコードへ直接記述しないでください。

## テストと品質確認

品質契約検証、`:shared:check`、Android アプリのユニットテスト・Lint・Debug APK 生成をまとめて実行します。`:shared:check` には共通・Android ホスト・JVM のテストと、macOS で実行可能な iOS Native テスト、AI コマンドの契約検証が含まれます。デスクトップのネイティブ依存や iOS Simulator も使用するため、Android だけの検査ではありません。

```bash
./gradlew qualityGate
```

用途別の主なコマンド:

```bash
# 共通・各ターゲットのチェック
./gradlew :shared:check

# Android ユニットテスト
./gradlew :app-android:testDebugUnitTest

# Android 端末 / Emulator の UI テスト
./gradlew :app-android:connectedDebugAndroidTest

# iOS Simulator 向け Native テスト
./gradlew :shared:iosSimulatorArm64Test

# デスクトップの共通 / JVM・画面・配布処理の検査
./gradlew :app-desktop:desktopCheck

# 機能・挙動の回帰契約を検証
./gradlew validateQualityContracts
```

Windows では `./gradlew` を `.\gradlew.bat` に読み替えます。iOS のビルド・テストは macOS が必要です。

GitHub Actions は使用せず、開発者環境で検査します。リリース前は [リリース確認マトリクス](quality/release-device-matrix.tsv) に従って Android / iOS の Emulator・Simulator・実機証跡をそろえ、次のゲートで現在のコミットとの一致を確認します。

```bash
./gradlew verifyReleaseReadiness -PreleaseEvidenceFile=/path/to/results.tsv
```

証跡ファイルは `matrix_id`、`build_sha`、`result`、`evidence` のタブ区切りです。対象の全セルが現在の Git SHA に対して PASS であり、作業ツリーがクリーンである必要があります。実機手順書・実行証跡は Git 管理外です。`qualityGate` や Release パッケージ生成の成功だけでは、この配布ゲートの合格を意味しません。

直近の機能共通化では、既存のデスクトップ動画テストが VLC の停止処理で止まったため、全体の `qualityGate` は未完了です。完了済みの検査と制約は [検証記録](docs/futacha-feature-parity-2026-09-20.md) に記載しています。

## データとネットワークについて

- 設定、履歴、Cookie、保存済みスレッドなどの実行時データは、各プラットフォームのアプリ領域またはユーザーが選択した保存先へ保存されます。
- macOS の既定データ領域は `~/Library/Application Support/Futacha/`、キャッシュは `~/Library/Caches/Futacha/` です。Windows は `%LOCALAPPDATA%\Futacha\data` と `%LOCALAPPDATA%\Futacha\cache` です。
- Android の SAF URI、iOS の bookmark、デスクトップのフォルダーパスは共通の保存先として持ち運べません。別 OS へ設定を移す場合は、その OS 上で保存先を選び直してください。
- 投稿、そうだね、削除関連の操作は接続先へ実際のリクエストを送信します。接続先のルールと利用環境を確認して使用してください。
- HTML やメディアの取得にはサイズ・件数・時間の上限を設け、異常な応答を無制限に処理しない構成です。
- iOS のバックグラウンド実行時刻は OS が決定します。Android の定期更新も省電力設定などにより遅延する場合があります。
- macOS / Windows の巡回・通知はアプリ起動中に動作します。アプリ終了中や PC のスリープ中に更新する常駐サービスはありません。

## ライセンスとプロジェクト方針

本プロジェクトでは、コードの修正や機能追加を歓迎しています。フォーク、改変、派生版の公開・再配布、および各種アプリストアでの公開も可能です。

派生版を公開・配布する場合は、配布者自身の責任において、品質、安全性、プライバシー、接続先および配布先の規約を確認し、利用者へのサポートを含む継続的かつ責任ある開発・運用に取り組んでください。

ソースコードを公開する目的は、ふたば専用ブラウザの開発者が減少するなかで、特定のアプリに選択肢が集中する状況や、保守されていないアプリしか利用できない状況を避け、利用者が継続的に複数の選択肢を持てる環境を支えることにあります。この趣旨に賛同する開発者による改善、提案、新たな実装を歓迎します。

なお、本リポジトリで使用している第三者ライブラリ、サービス、名称、ロゴその他の素材には、それぞれの権利者が定めるライセンスや利用条件が適用されます。派生版の公開・配布時には、各条件を個別に確認してください。
