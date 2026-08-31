# ふたちゃ

ふたば☆ちゃんねるの閲覧・投稿・保存に対応した、Kotlin Multiplatform / Compose Multiplatform 製ブラウザです。Android と iOS で UI と主要ロジックを共有し、Wear OS からはスマートフォン側の履歴や監視情報を確認・操作できます。

> [!NOTE]
> この公開版は、API キー、個人情報、認証情報などが含まれていないことと、公開上の安全性を確認したうえで提供しています。確認工程を経て更新するため、本リポジトリの機能や修正は開発版より遅れている場合があります。

## 対応プラットフォーム

- Android 8.0（API 26）以降
- iOS / iPadOS（Xcode プロジェクトと CocoaPods でホスト）
- Wear OS（Android 版との連携アプリ）

OS 固有機能を除き、画面、状態管理、通信、HTML 解析、保存処理は `shared/` に集約しています。

## 主な機能

### 板・カタログ

- 板の追加、削除、ピン留め、上下移動、長押しドラッグによる並べ替え
- グリッド / リスト表示、列数変更、カタログモードと並び順の切り替え
- 検索、NG 条件、監視ワード、板ごとの設定
- 更新、スレッド作成、履歴への自動追加
- 「ふたちゃモード」と「としあき（仮）モード」の表示プロファイル切り替え

### スレッド

- スレッドの取得・更新、タブ、履歴、既読位置とスクロール位置の復元
- 本文検索、検索結果移動、引用プレビュー、引用返信、絞り込み、並べ替え
- レス投稿、画像・動画添付、手書き添付、下書き保存
- そうだね、削除依頼、本人削除
- 本文読み上げ、バックグラウンド更新、更新通知

### メディア・保存

- 画像、GIF、WebP、APNG、動画の表示
- 添付ギャラリー、前後移動、拡大縮小、動画再生
- スレッド、画像、動画のローカル保存
- 保存済みスレッドの一覧、オフライン閲覧、個別削除、一括削除、使用容量表示
- 定期的な自動保存と、通信失敗時のローカルコピーへのフォールバック

### 設定・連携

- テーマ、表示、NG、Cookie、キャッシュ、保存先、通知などの設定と永続化
- Android のディレクトリ選択と優先ファイラー、iOS の security-scoped bookmark
- 履歴や設定の入出力、過去ログ検索、Deep Link、外部アプリ連携
- アプリロック、プライバシーフィルタ、通報・ブロック関連機能
- AI コマンド、要約、モデレーション補助
- Wear OS への履歴・監視結果・読み上げ状態の同期、Tile、スマートフォン操作

一部の機能はプラットフォーム、端末能力、接続先の仕様、権限設定によって利用可否や動作が異なります。

## プロジェクト構成

```text
.
├── app-android/   Android ホストアプリ、WorkManager、通知、端末連携
├── app-wear/      Wear OS アプリ、Tile、Data Layer 連携
├── iosApp/        SwiftUI ホスト、Xcode プロジェクト、watchOS ホスト
├── shared/        共通 UI、状態、通信、解析、保存、プラットフォーム実装
├── quality/       機能・挙動・リリース確認用の回帰契約
└── tools/         開発者環境の構築・検査スクリプト
```

`shared/src/` の主な領域は次のとおりです。

- `commonMain`: Compose UI、モデル、状態管理、通信、パーサー、Repository、Service
- `androidMain`: Android のストレージ、メディア、通知、Activity 連携など
- `iosMain`: iOS のストレージ、メディア、BGTask、UIKit / SwiftUI 連携など
- `commonTest` / `androidHostTest` / `iosTest` / `jvmTest`: 共通・プラットフォーム別テスト

主な共通 UI エントリーポイントは `shared/src/commonMain/kotlin/ui/FutachaApp.kt` です。Android は `app-android/`、iOS は `iosApp/`、Wear OS は `app-wear/` が共通層をホストします。

## 主な技術要素

- Kotlin 2.4 / Kotlin Multiplatform
- Compose Multiplatform
- Ktor Client
- kotlinx.coroutines / kotlinx.serialization
- Android DataStore / iOS NSUserDefaults
- Coil 3、Android Media3、iOS AVPlayer / WKWebView
- Android WorkManager / iOS BGTask
- Firebase Analytics / Crashlytics（設定ファイルがある場合のみ有効）

## 必要な環境

- JDK 17
- Android Studio と Android SDK 37
- iOS 版をビルドする場合は macOS、Xcode、CocoaPods

Gradle Wrapper を同梱しているため、システムへ Gradle を個別インストールする必要はありません。

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

接続中の端末または Emulator へインストールする場合:

```bash
./gradlew :app-android:installDebug
```

### 4. Wear OS

```bash
./gradlew :app-wear:assembleDebug
```

スマートフォン版との同期には、ペアリング済みの Wear OS 端末または Emulator が必要です。

### 5. iOS

CocoaPods の依存関係と共有 Framework を準備します。

```bash
./gradlew :shared:podInstall
open iosApp/iosApp.xcworkspace
```

Xcode で `iosApp` scheme と Simulator または実機を選択して起動します。実機署名が必要な場合は、公開されている雛形をローカル設定へコピーして値を設定してください。

```bash
cp iosApp/Configuration/Local.xcconfig.example \
  iosApp/Configuration/Local.xcconfig
```

`Local.xcconfig` は Git の追跡対象外です。

## Firebase（任意）

Firebase の設定ファイルがなくてもビルドと起動は可能です。Analytics / Crashlytics を有効にする場合だけ、Firebase Console から取得したファイルを次の場所へ配置します。

| プラットフォーム | ファイル | 配置先 |
|---|---|---|
| Android | `google-services.json` | `app-android/google-services.json` |
| iOS | `GoogleService-Info.plist` | `iosApp/iosApp/GoogleService-Info.plist` |

これらの設定ファイル、署名情報、実行時に生成されるユーザーデータは Git の追跡対象外です。値をソースコードへ直接記述しないでください。

## テストと品質確認

共通・Android の決定的テスト、Lint、品質契約検証、Debug APK 生成をまとめて実行します。

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

# 機能・挙動の回帰契約を検証
./gradlew validateQualityContracts
```

GitHub Actions は使用しません。JDK 17 の開発者環境で `qualityGate` を実行し、リリース前は `docs/device-regression-runbook.md` と `quality/release-device-matrix.tsv` に従って Android / iOS の端末テストを完了させます。

## データとネットワークについて

- 設定、履歴、Cookie、保存済みスレッドなどの実行時データは、各プラットフォームのアプリ領域またはユーザーが選択した保存先へ保存されます。
- 投稿、そうだね、削除関連の操作は接続先へ実際のリクエストを送信します。接続先のルールと利用環境を確認して使用してください。
- HTML やメディアの取得にはサイズ・件数・時間の上限を設け、異常な応答を無制限に処理しない構成です。
- iOS のバックグラウンド実行時刻は OS が決定します。Android の定期更新も省電力設定などにより遅延する場合があります。

## ライセンスとプロジェクト方針

本プロジェクトでは、コードの修正や機能追加を歓迎しています。フォーク、改変、派生版の公開・再配布、および各種アプリストアでの公開も可能です。

派生版を公開・配布する場合は、配布者自身の責任において、品質、安全性、プライバシー、接続先および配布先の規約を確認し、利用者へのサポートを含む継続的かつ責任ある開発・運用に取り組んでください。

ソースコードを公開する目的は、ふたば専用ブラウザの開発者が減少するなかで、特定のアプリに選択肢が集中する状況や、保守されていないアプリしか利用できない状況を避け、利用者が継続的に複数の選択肢を持てる環境を支えることにあります。この趣旨に賛同する開発者による改善、提案、新たな実装を歓迎します。

なお、本リポジトリで使用している第三者ライブラリ、サービス、名称、ロゴその他の素材には、それぞれの権利者が定めるライセンスや利用条件が適用されます。派生版の公開・配布時には、各条件を個別に確認してください。
