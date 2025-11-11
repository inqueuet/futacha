# Futacha - ふたばちゃんねるブラウザ

> Kotlin Multiplatformで開発された、ふたばちゃんねる専用ブラウザアプリ
> AndroidとiOSで**完全に同一のUI/ロジック**が動作します

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.7.0-green.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ 特徴

- 🎨 **Compose Multiplatform**: Android/iOS共通のモダンUI
- 🔄 **Kotlin Multiplatform**: ビジネスロジックを完全共有
- 📱 **マルチストア対応**: Google Play、App Store、GitHub Releasesでの配布
- 🔔 **自動バージョンチェック**: 起動時に最新版をお知らせ
- 💾 **状態永続化**: 板リスト・閲覧履歴を自動保存
- 📥 **スレッド保存機能**: HTML+画像を共有ストレージに保存、オフライン閲覧可能
- 🌓 **ダークモード**: ライト/ダークテーマ対応

---

## 📥 ダウンロード

### Google Play
（準備中）

### App Store
（準備中）

### GitHub Releases
最新版は[Releases](https://github.com/inqueuet/futacha/releases/latest)からダウンロードできます。

---

## 🚀 開発

### 必要な環境

- **Android開発**:
  - JDK 11以上
  - Android Studio Ladybug以上
  - Android SDK (minSdk: 24, targetSdk: 36)

- **iOS開発** (オプション):
  - Xcode 15以上
  - macOS

### ビルド方法

```bash
# Androidデバッグビルド
./gradlew :app-android:assembleDebug

# Androidリリースビルド
./gradlew :app-android:assembleRelease

# 共通モジュールのテスト
./gradlew :shared:check

# iOS向けビルド (macOS + Xcodeが必要)
./gradlew :shared:linkDebugFrameworkIosArm64
```

---

## 📂 プロジェクト構成

```
futacha/
├── app-android/          # Androidアプリ (エントリーポイントのみ)
│   └── src/main/java/com/valoser/futacha/
│       └── MainActivity.kt
│
├── shared/               # 共通コード (UI + ロジック) - 70+ファイル
│   ├── src/commonMain/kotlin/  # 完全共通化 (~95%) - 45+ファイル
│   │   ├── model/        # データモデル (8ファイル)
│   │   │   ├── Post.kt, CatalogItem.kt, ThreadPage.kt
│   │   │   ├── BoardStateModels.kt (BoardSummary, ThreadHistoryEntry)
│   │   │   ├── CatalogMode.kt (7種類の表示モード)
│   │   │   ├── SavedThread.kt (保存済みスレッド、進捗情報)
│   │   │   ├── MediaItem.kt (メディアアイテム)
│   │   │   └── CatalogItemExtensions.kt
│   │   │
│   │   ├── network/      # HTTPクライアント (4ファイル)
│   │   │   ├── BoardApi.kt (interface)
│   │   │   ├── HttpBoardApi.kt (Ktor実装、全API機能)
│   │   │   ├── BoardUrlResolver.kt (URL解決、パストラバーサル対策)
│   │   │   └── HttpClientFactory.kt (expect/actual)
│   │   │
│   │   ├── parser/       # HTMLパーサー (6ファイル)
│   │   │   ├── HtmlParser.kt (interface)
│   │   │   ├── CatalogHtmlParserCore.kt (正規表現パーサー)
│   │   │   ├── ThreadHtmlParserCore.kt (正規表現パーサー)
│   │   │   ├── ParserFactory.kt (expect/actual)
│   │   │   └── ParserException.kt
│   │   │
│   │   ├── repo/         # リポジトリ層 (6ファイル + mock/)
│   │   │   ├── BoardRepository.kt (DefaultBoardRepository)
│   │   │   ├── BoardRepositoryFactory.kt
│   │   │   └── mock/ (FakeBoardRepository, Fixtures)
│   │   │
│   │   ├── repository/   # 保存機能リポジトリ (1ファイル)
│   │   │   └── SavedThreadRepository.kt (保存済みスレッド管理)
│   │   │
│   │   ├── service/      # ビジネスロジック (1ファイル)
│   │   │   └── ThreadSaveService.kt (スレッド保存、進捗管理)
│   │   │
│   │   ├── state/        # 状態管理 (1ファイル)
│   │   │   └── AppStateStore.kt (Flow、JSON、Mutex、expect/actual)
│   │   │
│   │   ├── ui/           # Compose Multiplatform UI (11ファイル)
│   │   │   ├── FutachaApp.kt (メインアプリ、画面遷移)
│   │   │   ├── PermissionRequest.kt (expect/actual)
│   │   │   ├── board/
│   │   │   │   ├── BoardManagementScreen.kt (3画面統合)
│   │   │   │   ├── SaveProgressDialog.kt (保存進捗表示)
│   │   │   │   ├── SavedThreadsScreen.kt (保存済み一覧)
│   │   │   │   ├── ImagePickerButton.kt (画像選択ボタン、expect/actual)
│   │   │   │   ├── PlatformVideoPlayer.kt (expect/actual)
│   │   │   │   └── BoardManagementFixtures.kt
│   │   │   ├── UpdateNotificationDialog.kt
│   │   │   ├── theme/FutachaTheme.kt
│   │   │   └── util/PlatformBackHandler.kt (expect/actual)
│   │   │
│   │   ├── util/         # ユーティリティ (3ファイル)
│   │   │   ├── ImagePicker.kt (expect/actual)
│   │   │   ├── FileSystem.kt (expect/actual、ファイル操作抽象化)
│   │   │   └── BoardConfig.kt
│   │   │
│   │   └── version/      # バージョンチェック (1ファイル)
│   │       └── VersionChecker.kt (GitHub Releases API)
│   │
│   ├── src/androidMain/kotlin/  # Android固有実装 (13ファイル)
│   │   ├── parser/       # JsoupHtmlParser.kt, ParserFactory.android.kt
│   │   ├── state/        # AppStateStore.android.kt (DataStore)
│   │   ├── network/      # HttpClientFactory.android.kt (OkHttp)
│   │   ├── util/         # ImagePicker.android.kt, FileSystem.android.kt, PermissionHelper.android.kt
│   │   ├── ui/           # PermissionRequest.android.kt
│   │   ├── ui/board/     # ImagePickerButton, PlatformVideoPlayer
│   │   ├── ui/util/      # PlatformBackHandler.android.kt
│   │   └── version/      # VersionChecker.android.kt (PackageManager)
│   │
│   └── src/iosMain/kotlin/      # iOS固有実装 (13ファイル)
│       ├── parser/       # AppleHtmlParser.kt, ParserFactory.ios.kt
│       ├── state/        # AppStateStore.ios.kt (NSUserDefaults)
│       ├── network/      # HttpClientFactory.ios.kt (Darwin)
│       ├── util/         # ImagePicker.ios.kt, FileSystem.ios.kt
│       ├── ui/           # PermissionRequest.ios.kt
│       ├── ui/board/     # ImagePickerButton, PlatformVideoPlayer
│       ├── ui/util/      # PlatformBackHandler.ios.kt
│       ├── version/      # VersionChecker.ios.kt
│       └── MainViewController.kt
│
├── codex.md              # 詳細設計書（API仕様、パーサー、実装状況）
└── README.md             # このファイル
```

詳細なアーキテクチャとAPI仕様は [codex.md](codex.md) を参照してください。

---

## 🔔 バージョン通知機能

アプリ起動時に自動的に最新バージョンをチェックし、更新がある場合はダイアログで通知します。

### 仕組み

1. **GitHub Releases API**を使用 (認証不要)
2. 起動時に`https://api.github.com/repos/inqueuet/futacha/releases/latest`を取得
3. 現在のバージョンと比較
4. 新しいバージョンがあれば通知ダイアログを表示

### 特徴

- ✅ 完全無料 (GitHub API使用)
- ✅ 認証不要
- ✅ ストアへの誘導なし (通知のみ)
- ✅ ユーザーが「後で」を選択可能
- ✅ App Store/Google Playのポリシー準拠

### 実装詳細

バージョンチェック機能は`shared/src/commonMain/kotlin/version/`に実装されています:

- `VersionChecker.kt` - 共通インターフェース
- `VersionChecker.android.kt` - Android実装 (PackageManager使用)
- `VersionChecker.ios.kt` - iOS実装 (準備中)

---

## 🛠️ 技術スタック

| 技術 | 用途 |
|-----|------|
| **Kotlin 2.1.0** | プログラミング言語 |
| **Compose Multiplatform** | 宣言的UI (Android/iOS共通) |
| **Ktor Client** | HTTPクライアント (Shift_JIS対応) |
| **Kotlinx Serialization** | JSONシリアライゼーション |
| **DataStore / NSUserDefaults** | 永続化 (プラットフォーム固有) |
| **Coil3** | 画像読み込み |

---

## 📋 実装状況

### ✅ 実装済み（Android完全対応）

#### UI・画面
- **板管理画面**: 板の追加・削除・並び替え、ピン留め機能
- **カタログ画面**: グリッド/リスト表示、7種類の表示モード切替、検索機能
- **スレッド画面**: 投稿表示、引用ハイライト、スクロール位置復元
- **ドロワーナビゲーション**: スレッド閲覧履歴、メタデータ自動更新

#### ネットワーク・API
- **Ktor HttpClient**: Cookie管理、タイムアウト設定、エラーハンドリング
- **カタログ取得**: 全7モード対応（新順、古順、レス多、勢い等）
- **スレッド取得**: 投稿一覧、画像・動画URL抽出
- **返信投稿**: 名前・メール・題名・本文・画像添付
- **スレッド作成**: 新規スレッド投稿、自動遷移
- **そうだね投票**: 楽観的UI更新
- **削除機能**: del依頼（理由コード）、本人削除（パスワード認証）

#### データ永続化・状態管理
- **DataStore** (Android) / **NSUserDefaults** (iOS)
- **板リスト**: JSON保存、Flow管理
- **閲覧履歴**: 最終閲覧時刻、レス数、スクロール位置
- **Cookie**: posttime, cxyl等の自動管理

#### パーサー・セキュリティ
- **正規表現ベースHTMLパーサー**: カタログ・スレッド対応
- **ReDoS攻撃対策**: サイズ制限（10MB）、イテレーション制限（1500回）、投稿上限（2050件）、パースタイムアウト（5秒）
- **パストラバーサル対策**: URL検証
- **XSS対策**: HTMLエンティティデコード

#### 画像・メディア
- **Coil3**: 画像読み込み、キャッシュ管理
- **画像プレビュー**: サムネイル→フルスクリーン、ピンチズーム
- **動画再生**: プラットフォーム固有実装
- **画像添付**: Android/iOS実装済み（ImagePicker）

#### スレッド保存機能
- **オフライン保存**: スレッド全体をHTML+画像で保存
- **共有ストレージ**: ファイルマネージャーからアクセス可能
  - Android: `/Documents/futacha/saved_threads/`
  - iOS: `NSDocumentDirectory/saved_threads/`
- **進捗表示**: リアルタイムパーセンテージ表示（準備1%、DL97%、変換1%、完了1%）
- **ファイルサイズ制限**: 8000KB（8MB）対応、HEADリクエストで事前チェック
- **サポート形式**: GIF, JPG, PNG, WEBP, MP4, WEBM
- **エラーハンドリング**: 一部失敗でも継続、ステータス表示（COMPLETED/PARTIAL/FAILED）
- **URL-to-Pathマッピング**: ダウンロード時にURLとローカルパスを紐付け
- **相対パス変換**: HTMLの画像URLを相対パス（`images/img_xxx.jpg`）に変換
- **保存済み一覧**: 保存したスレッドの一覧表示・削除機能

#### スレッド作成機能
- **新規スレッド作成**: 板からスレッドを新規作成
- **画像添付対応**: ImagePickerButtonで画像選択
  - Android: ActivityResultContracts使用
  - iOS: PHPickerViewController使用
- **フォーム入力**: 名前・メール・題名・本文
- **自動遷移**: 作成後、新スレッドに自動遷移

#### 板アイコン選択機能
- **カスタムアイコン**: 板ごとにカスタムアイコン設定可能
- **画像選択**: ImagePickerButtonで画像選択
- **プラットフォーム固有実装**: Android/iOS完全対応

#### その他機能
- **バージョン通知**: GitHub Releases API連携（Android/iOS）
- **ダークモード**: 自動対応
- **検索・フィルター**: カタログ検索、ローカルソート

### ✅ iOS実装状況（完全対応）
- ✅ 基本UI動作（板管理、カタログ、スレッド表示）
- ✅ ネットワーク通信（Ktor Darwin）
- ✅ 状態永続化（NSUserDefaults）
- ✅ HTMLパーサー
- ✅ **画像選択機能（PHPickerViewController実装完了）**
- ✅ **バージョンチェッカー（NSBundle実装完了）**
- ✅ **動画プレーヤー（AVPlayer実装完了）**
- ✅ **ファイルシステム（NSFileManager実装完了）**
- ✅ **権限処理（実装完了）**
- ✅ **スレッド作成（画像添付対応）**
- ✅ **板アイコン選択（ImagePickerButton）**

**Android/iOS完全対応！** コード共有率 ~95%

### 📝 今後の拡張予定
- オフラインキャッシュ
- ダークモード切替UI
- プッシュ通知
- テストカバレッジ向上

---

## 🤝 コントリビューション

Issue・Pull Requestを歓迎します！

### 開発の流れ

1. このリポジトリをFork
2. Feature Branchを作成 (`git checkout -b feature/amazing-feature`)
3. 変更をCommit (`git commit -m 'Add amazing feature'`)
4. Branchをプッシュ (`git push origin feature/amazing-feature`)
5. Pull Requestを作成

---

## 📄 ライセンス

このプロジェクトは[MITライセンス](LICENSE)の下で公開されています。

---

## 🔗 リンク

- [GitHub Repository](https://github.com/inqueuet/futacha)
- [Issue Tracker](https://github.com/inqueuet/futacha/issues)
- [Releases](https://github.com/inqueuet/futacha/releases)
- [詳細設計書 (codex.md)](codex.md)

---

## 📮 お問い合わせ

質問や提案がある場合は、[GitHub Issues](https://github.com/inqueuet/futacha/issues)に投稿してください。

---

**Made with ❤️ using Kotlin Multiplatform & Compose Multiplatform**
