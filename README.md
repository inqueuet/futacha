# ふたちゃ

ふたば☆ちゃんねると、あいもげ（nijiurachan.net）を読み書きする専用ブラウザです。Kotlin Multiplatform / Compose Multiplatform で作り、Android・iOS / iPadOS・macOS・Windows で画面と主な処理を共有します。Wear OS と watchOS には、スマートフォンの履歴や監視の結果を見る連携アプリがあります。

## 今後の開発方針とサポート体制の変更について（2026年11月24日）

いつもふたちゃをご利用いただき、ありがとうございます。

ちょうど公開が始まり、1年が経過をして多くの方からご利用いただけるアプリとなりました。

これまで、ふたば専用ブラウザの選択肢を増やすことを目指し、複数のプラットフォームに対応してきました。各プラットフォームの利用状況や開発・保守の負担、今後の品質向上を考慮した結果、今後は Android と Wear OS に開発リソースを集中する方針とします。

| プラットフォーム | 今後の開発・サポート方針 |
| --- | --- |
| Android | 積極的な機能開発・最適化を継続（v2） |
| Wear OS | Android 版との連携アプリとして開発・サポートを継続（v2） |
| iOS / iPadOS | 新規機能の開発を終了し、既存版の維持・保守を継続 |
| macOS | 開発・サポート終了 |
| Windows | 開発・サポート終了 |
| watchOS | 開発・サポート終了 |

### iOS / iPadOS 版について

ふたちゃの開発を始めた頃から、ふたば専用ブラウザの選択肢が減り、利用者が限られたアプリに依存する状況を少しでも改善したいという思いがありました。

現在、iOS では既存のふたば専用ブラウザの開発が継続されているだけでなく、新規参入も盛んになっています。また、futaber の作者様が開発に復帰され、futaber2 を公開されたことも、今回の判断に至った理由の一つです。こうした動きを一人の開発者として嬉しく思うとともに、ふたちゃが iOS において目指してきた役割は一定程度果たせたと考えています。

iOS / iPadOS 版の提供を終了するわけではありません。今後は新規機能の追加を行わず、不具合修正や接続先の仕様変更への対応など、既存機能を維持するための保守を可能な範囲で継続します。

### Android / Wear OS 版とふたちゃ v2 について

今後は Android と Wear OS に特化した次世代版「ふたちゃ v2」の開発を、新たな公開リポジトリで進める予定です。

マルチプラットフォーム対応を前提としてきた設計を抜本的に見直し、Android の特性を最大限に活かせる構成へ移行します。既存機能を活かしながら、カタログやスレッドの表示、スクロール、画像・動画処理、メモリ使用量、起動速度、バッテリー消費、安定性、操作性など、アプリ全体を徹底的に最適化していきます。Wear OS との連携も引き続き維持します。

単に対応プラットフォームを減らすのではなく、これまで以上に快適で完成度の高いふたば専用ブラウザへと進化させることが、今回の方針転換の目的です。

### 本リポジトリの今後について

本リポジトリは従来のマルチプラットフォーム版（v1）として、ソースコード、開発履歴、関連資料を引き続き公開します。iOS / iPadOS 版の維持・保守も本リポジトリで行います。macOS・Windows・watchOS 版のソースコードも残し、ライセンスに従ったフォークや派生版の開発を引き続き歓迎します。

v2 の公開リポジトリについては、準備が整い次第ご案内します。

これまで各プラットフォームでふたちゃをご利用いただいた皆様、開発を支えてくださった皆様に、心より感謝申し上げます。新規機能の追加を楽しみにされていた方や、サポート終了の対象となるプラットフォームをご利用の方には、ご期待に沿えず申し訳ございません。

今後は限られた開発リソースを集中させ、Android 版をこれまで以上に良いアプリにできるよう開発を続けていきます。今後ともふたちゃをよろしくお願いいたします。

---

以下は、本リポジトリに収録されている従来のマルチプラットフォーム版（v1）の機能・技術構成・ビルド手順です。サポート終了対象のプラットフォームについても、過去の実装記録および派生版開発のために資料を残しています。記載されているビルド手順や対応 OS は、今後のサポート継続を保証するものではありません。

## 対応プラットフォーム（v1）

- Android：Android 8.0（API 26）以降。ブラウザ本体。
- iOS / iPadOS：15.1 以降。ブラウザ本体。
- macOS：13.0 以降、Apple Silicon（arm64）。Compose Desktop 版（[Mac 版](docs/macos-port-2026-09-19.md)）。
- Windows：Windows 10 / 11、x64。Compose Desktop 版（[Windows 版](docs/windows-port-2026-09-20.md)）。
- Wear OS：API 26 以降の Wear OS 端末。Android 版との連携アプリ。
- watchOS：10.0 以降。iPhone 版との連携アプリ。

あいもげは Android と iOS で使えます。デスクトップ版では使えません。Intel Mac、Windows ARM64、Linux の配布構成はありません。最低 OS はビルド設定の値です。どの OS・端末で確かめたかは、各記録と品質確認の資料を見てください。

## 4つのモード

同じデータを、見た目と操作の違う4つのモードで使えます。設定の「モード」から切り替えます。

- ふたちゃ：このアプリ独自の画面。
- としあき（仮）：参照アプリの画面と操作に合わせたモード。
- ふたばー風：iOS の futaber の見た目と操作に合わせたモード。
- ふたったー風：ふたったーの見た目と操作に合わせたモード。

ふたばー風・ふたったー風は元のアプリと同じ見た目と操作を基本にし、他のモードの機能は「設定 → このアプリ → 拡張機能」で足します。追加の項目は、長押しのシートや操作メニューの最後の「その他の機能」にまとめています。元のアプリと違う以前の動きは、設定の「従来の動き」で戻せます。

板・履歴・タブ・NG・下書きなどは4モードで共有します。どのモードで開いたスレッドも、切り替えずに他のモードの履歴とタブに出ます。

## あいもげ対応

- 板の追加：ふたばと同じく「板を追加」のURL欄にあいもげの板のURL（`https://nijiurachan.net/b/{板}/`）を入れます。スレ・カタログのURLや http も板のURLに直します。名前はあいもげの板一覧から取り、R18 の板は追加の時に確認します。ふたばの板と同じ一覧・タブ・履歴・バックアップに入ります。
- 読む：カタログ・スレッド・画像と動画（1レス3枚まで）・保存・過去ログ（あいもげの過去ログと今あるスレのレスの検索を含む）。ダイスの太字、伏せ字、アンケート、消滅予告、閉じたスレ、R18 の印も出します。
- 承認と書き込み：設定の「あいもげ」からブラウザで承認すると、返信・スレ立て・添付・手書き・そうだね・del・通報・本人削除・アンケートの投票ができます。承認の後に何かを自動で送ることはありません。
- 削除キー：ふたばとは別のキーです。設定の「あいもげ」で確認・変更でき、最初に送る時に同意を確かめます。
- 結果が分からない書き込みは自動で送り直しません。送り直す時は同じ書き込みとして扱います。
- 裏の更新・巡回・通知もあいもげのスレッドに対応し、通知からあいもげのスレッドを開けます。
- あいもげを使えない版では、あいもげの板・タブ・履歴を消さずに隠します。

## 主な機能

### 板・カタログ

- 板の追加（公式の板一覧からまとめて追加、URL欄からのあいもげの板の追加）、改名、削除、ピン留め、並べ替え
- グリッド／リスト、列数、カタログのモードと並び順
- 検索、NG、監視ワード、板ごとの設定
- 更新、更新前に戻す、消えたスレッドの確認、スレッド作成

### スレッド

- 取得・更新、既読位置の復元、「新着レス ○件」の帯と新着への移動
- 本文検索（右端の×で入力を消せます）、引用の吹き出し、引用返信、抽出、ツリー表示
- 返信・スレ立て、画像と動画の添付、手書き、あぷ・あぷ小（3000KB を超える写真は縮小して上げます）、音声入力
- 下書き、そうだね、del・通報、本人削除
- 読み上げ（端末の音声、または Android 10 以降の64bit・iOS 16 以降で任意に取得する「ずんだもん」）、自動スクロール
- キャッシュ検索、落ちたスレッドの過去ログ取得

### タブと履歴

- タブと履歴は4モードで共有します。タブを閉じるとすべてのモードから消え、履歴は残ります。履歴を消すとすべてのモードの履歴から消え、タブは残ります。
- としあき（仮）では「タブを閉じたら履歴も消す」「履歴を消したらタブも閉じる」を選べます（設定 → 操作、最初はOFF）。
- タブの上限は最初200件で、20$301C990件に変えられます（ふたちゃの「設定 → タブ」、ふたったー風の拡張機能）。★の付いたタブと開いているタブは整理しません。
- 閉じたタブは20回分を24時間記録し、そのモードで閉じたか表示していたタブを戻せます。
- ふたばー風のお気に入りは★とは別に持ち、タブを閉じても残ります。

### メディア・保存

- 画像・GIF・WebP・APNG・動画の表示、画像一覧、拡大、画像検索
- スレッドの保存（HTML・MHT など）、メディアだけの保存、保存済みスレッドのオフライン閲覧
- 自動保存と、通信できない時の保存したコピーの表示
- 画像・動画のモザイクと黒塗り（自動検出・追尾・輪郭）、生成情報・プロンプトの表示（どれも最初はOFF）

### 設定・連携

- テーマ・表示・操作・NG・Cookie・キャッシュ・保存先・通知などの設定。配色はモードごと
- 設定のバックアップと復元、旧版データ・ふたったーの `.ftt` の取り込み
- 起動ロック、プライバシー表示
- キーワード巡回と通知（Android の外部にじろぐ連携も）
- AI（端末内の要約、OpenAI による荒らし判定。キーの登録と同意が必要）、AI コマンド・ディープリンク
- Wear OS・watchOS への履歴・監視の結果・読み上げの同期

使い方はアプリ内の「ヘルプ」で検索でき、各モードの「操作ガイド」で投稿・添付・削除キー・ジェスチャーの代わりの操作を確かめられます。変更履歴はアプリの設定から見られます（[変更履歴データ](shared/src/commonMain/kotlin/ui/compat/CompatibilityReferenceInfoData.kt)）。

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
Android の Release ビルドでは `baselineprofile/` を使い、専用の API 35 エミュレーターで最適化プロファイルを生成します。ふたちゃ・としあき（仮）・ふたばー風・ふたったー風の4モードで、起動と板一覧からカタログ・スレッドを開く操作を収集します。ふたばー風・ふたったー風では画像一覧も対象です。

`shared/src/` の主な領域は次のとおりです。

- `commonMain`: Compose UI、モデル、状態管理、通信、パーサー、Repository、Service
- `androidMain`: Android のストレージ、メディア、通知、Activity 連携など
- `iosMain`: iOS のストレージ、メディア、BGTask、UIKit / SwiftUI 連携など
- `jvmMain`: Windows / Mac のファイル選択・永続化・動画再生・編集・推論・追尾
- `commonTest` / `androidHostTest` / `iosTest` / `jvmTest`: 共通・プラットフォーム別テスト

主な共通 UI エントリーポイントは `shared/src/commonMain/kotlin/ui/FutachaApp.kt`、としあきモードの画面は `shared/src/commonMain/kotlin/ui/compat/CompatibilityApp.kt` です。Android は `app-android/`、iOS は `iosApp/`、macOS / Windows は `app-desktop/` が共通画面をホストします。Wear OS は `app-wear/`、watchOS は `iosApp/watchApp/` に専用 UI とスマートフォンとの通信処理があります。

## 主な技術要素

- Kotlin 2.5.0-Beta2 / Kotlin Multiplatform
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
- Android / Wear OS: Android SDK 37.2、Build Tools 37.0.0。Android 本体のネイティブ処理には NDK `30.0.16248370` と SDK の CMake `4.1.2` を使用します。
- iOS / watchOS: macOS、Swift 6.2以上に対応するXcode、Ruby 3.2以上・CocoaPods 1.17.0、Swift Package Manager、CMake 3.21 以降。共有 Framework は実機 arm64 と Apple Silicon の Simulator arm64 を対象にしています。
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

共有FrameworkとONNX RuntimeはCocoaPods、Firebase 13.1.1はSwift Package Managerで導入します。通常のビルドもArchiveも `iosApp/iosApp.xcworkspace` を使います。XcodeはGit管理された `Package.resolved` に従ってFirebaseなどを取得します。共有FrameworkはXcodeのビルド時に生成します。

```bash
./gradlew :shared:generateDummyFramework :shared:podspec
(cd iosApp && bundle install && bundle exec pod install)
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

検証結果は実施日ごとの記録で確認してください。依存ライブラリの更新内容とその時点の確認範囲は [2026年11月8日の依存更新記録](docs/dependency-update-2026-11-08.md) にまとめています。現在の採用版はバージョンカタログと各ビルド設定を参照してください。Macのキーチェーンや実機など、実行環境に依存する検査もあります。

## データとネットワークについて

- 設定、履歴、Cookie、保存済みスレッドなどの実行時データは、各プラットフォームのアプリ領域またはユーザーが選択した保存先へ保存されます。
- macOS の既定データ領域は `~/Library/Application Support/Futacha/`、キャッシュは `~/Library/Caches/Futacha/` です。Windows は `%LOCALAPPDATA%\Futacha\data` と `%LOCALAPPDATA%\Futacha\cache` です。
- Android の SAF URI、iOS の bookmark、デスクトップのフォルダーパスは共通の保存先として持ち運べません。別 OS へ設定を移す場合は、その OS 上で保存先を選び直してください。
- 投稿、そうだね、削除関連の操作は接続先（ふたば・あいもげ）へ実際のリクエストを送信します。
- あいもげの資格と削除キーは端末の安全な保存領域に置き、画面やログには出しません。接続先のルールと利用環境を確認して使用してください。
- HTML やメディアの取得にはサイズ・件数・時間の上限を設け、異常な応答を無制限に処理しない構成です。
- iOS のバックグラウンド実行時刻は OS が決定します。Android の定期更新も省電力設定などにより遅延する場合があります。
- macOS / Windows の巡回・通知はアプリ起動中に動作します。アプリ終了中や PC のスリープ中に更新する常駐サービスはありません。

## ライセンスとプロジェクト方針

本プロジェクトでは、コードの修正や機能追加を歓迎しています。フォーク、改変、派生版の公開・再配布、および各種アプリストアでの公開も可能です。

派生版を公開・配布する場合は、配布者自身の責任において、品質、安全性、プライバシー、接続先および配布先の規約を確認し、利用者へのサポートを含む継続的かつ責任ある開発・運用に取り組んでください。

ソースコードを公開してきた目的は、ふたば専用ブラウザの選択肢が減少することへの懸念から、特定のアプリに選択肢が集中する状況や、保守されていないアプリしか利用できない状況を避け、利用者が継続的に複数の選択肢を持てる環境を支えることにあります。今後も本リポジトリの公開を継続することで、この趣旨を大切にしていきます。この趣旨に賛同する開発者による改善、提案、新たな実装を歓迎します。

なお、本リポジトリで使用している第三者ライブラリ、サービス、名称、ロゴその他の素材には、それぞれの権利者が定めるライセンスや利用条件が適用されます。派生版の公開・配布時には、各条件を個別に確認してください。
