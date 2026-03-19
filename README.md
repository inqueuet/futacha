# ふたちゃ

ふたばちゃんねる専用ブラウザです。Kotlin Multiplatform と Compose Multiplatform を使い、UI と主要ロジックは `shared/` に集約し、Android と iOS はホスト層だけを持つ構成になっています。

## 概要

- 共通 UI エントリーポイントは `shared/src/commonMain/kotlin/ui/FutachaApp.kt`
- Android ホストは `app-android/`
- iOS ホストは `iosApp/`
- 状態管理は `AppStateStore`
- 通信は Ktor、HTML 解析は shared の parser 実装
- 保存、履歴更新、Cookie 管理、動画再生、バージョン確認も共通層中心で実装

## 現在の主な機能

### 閲覧

- Board -> Catalog -> Thread の 3 段階 UI
- 板の追加、削除、並び替え
- 履歴ドロワー、閲覧位置の保存と復元
- カタログ検索、並び順切り替え、監視ワード
- スレッド内検索、引用プレビュー、NG ワード / NG ヘッダ
- 画像プレビュー、動画プレビュー、ギャラリー表示
- 読み上げ機能

### 投稿・操作

- スレ立て
- レス投稿
- そうだね、削除依頼、本人削除
- 外部アプリ / 外部 URL 起動

### 保存・オフライン

- 手動保存
- 自動保存
- 保存済みスレッド一覧画面
- オフライン時のローカル保存フォールバック表示
- 画像と動画をローカル保存し、HTML 内の参照もローカルパスへ変換

### 設定

- バックグラウンド更新トグル
- 広告表示トグル
- 軽量モードトグル
- 手動保存先の指定
- 添付ピッカーの優先動作設定
- Android の優先ファイラー設定
- カタログ下部メニューの並び / 表示制御
- スレッド下部メニューと設定シートの並び / 表示制御
- Cookie 一覧と削除
- 画像キャッシュ / 一時キャッシュ削除
- アプリバージョン表示と GitHub Releases ベースの更新通知

## プロジェクト構成

```text
.
├── app-android/   Android ホストアプリ
├── iosApp/        iOS ホストアプリ (SwiftUI)
├── shared/        共通 UI / 状態 / 通信 / パーサ / 保存 / テスト
└── AGENTS.md      実装メモと内部向け詳細ドキュメント
```

`shared/` の主な責務:

- `ui/`: Compose UI と画面遷移
- `state/`: `AppStateStore` と永続化
- `network/`: Ktor ベースの通信、Cookie 永続化
- `parser/`: カタログ / スレ HTML 解析
- `repo/`: 板取得・投稿まわり
- `repository/`: 保存済みスレッド、Cookie 管理
- `service/`: スレ保存、履歴更新

## 実行環境

- JDK 17 以上を推奨
- Android Studio 最新系
- Android SDK
- Xcode 15 以降
- CocoaPods

Android 側のビルド設定は `compileSdk 36 / minSdk 24`、iOS 側の deployment target は 15.0 です。

## セットアップ

### Android

1. リポジトリを clone
2. `local.properties` に Android SDK のパスを設定
3. 必要なら Android Studio で Gradle Sync

### iOS

1. ルートで `./gradlew :shared:generateDummyFramework`
2. `iosApp/` で `pod install`
3. `iosApp/iosApp.xcworkspace` を Xcode で開く

`shared/shared.podspec` はダミー framework が無い状態では `pod install` に失敗するため、最初に `:shared:generateDummyFramework` が必要です。

### Firebase 設定ファイル

Firebase の設定ファイルはリポジトリに含まれていません。Firebase Console からダウンロードし、以下の場所に配置してください。

| プラットフォーム | ファイル | 設置場所 |
|---|---|---|
| Android | `google-services.json` | `app-android/google-services.json` |
| iOS | `GoogleService-Info.plist` | `iosApp/iosApp/GoogleService-Info.plist` |

- iOS の `GoogleService-Info.plist` は Xcode 上で `iosApp` ターゲットに追加する必要があります。
- どちらのファイルも `.gitignore` で除外されています。ファイルが存在しない場合でもビルド・起動は可能です。

## 起動方法

### Android

```bash
./gradlew :app-android:installDebug
```

または Android Studio から `app-android` を実行します。

### iOS

Xcode で `iosApp` scheme を選び、Simulator か実機で起動します。Compose 側のエントリーポイントは `MainViewController()` です。

## バックグラウンド更新

- Android は WorkManager を使用
- iOS は `BackgroundRefreshManager` を使用
- 更新処理本体は共通の `HistoryRefresher`
- 更新時に本文・画像・動画の自動保存も行います

Android は定期 Work と即時 Work を併用します。iOS は OS のスケジューリングに従うため、実行タイミングは一定ではありません。

## 保存先まわり

- 自動保存: `AUTO_SAVE_DIRECTORY`
- 手動保存: 設定画面の保存先設定に従う
- Android はパス指定に加えてファイラー経由の選択に対応
- iOS は bookmark / security-scoped resource を使った保存先保持に対応

## テスト

共通テスト:

```bash
./gradlew :shared:check
```

Android ユニットテスト:

```bash
./gradlew :app-android:testDebugUnitTest
```

Android Instrumentation Test:

```bash
./gradlew :app-android:connectedDebugAndroidTest
```

現在のテスト対象は主に以下です。

- parser
- network
- state
- repository
- service
- UI ロジック
- Android 側の一部統合テスト

## 既知の制約

- 板の並び替えは上下移動ベースで、ドラッグアンドドロップは未対応
- iOS のバックグラウンド更新頻度は OS 依存
- 実際の投稿や削除の成功可否は板側の仕様や応答に影響される

## 関連ドキュメント

- `AGENTS.md`: 実装の詳細、主要エントリーポイント、画面仕様、保存や通信の補足

