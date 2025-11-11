# Codex – Futaba Channel Browser (KMP + Compose Multiplatform)

> Kotlin Multiplatform project for a dedicated **Futaba Channel browser**.
> **UI層とロジック層の両方が完全に共通化**されており、AndroidとiOSで同一のCompose Multiplatform UIが動作します。
> プラットフォーム固有実装は永続化とHTMLパーサーのみに限定されています。

---

## 🔍 最新共通化チェック

- **モジュール構成**: `shared/build.gradle.kts` で Compose Multiplatform + KMP を有効化し、`androidTarget` と `iosX64/iosArm64/iosSimulatorArm64` を同一ソースセットにぶら下げているため UI/ロジックを完全共有できます。
- **エントリーポイント**: Android (`app-android/src/main/java/com/valoser/futacha/MainActivity.kt`) と iOS (`shared/src/iosMain/kotlin/MainViewController.kt`) はどちらも `createAppStateStore()` で状態永続レイヤーを注入し、`FutachaApp` をそのまま表示するだけの薄いホストになっています。
- **共通UI/ロジック**: `shared/src/commonMain/kotlin/ui/FutachaApp.kt` 配下に `BoardManagementScreen`, `CatalogScreen`, `ThreadScreen` など Compose UI があり、`repo/`, `network/`, `parser/` で HTTP クライアント/HTMLパース/リポジトリを共通実装しています。
- **プラットフォーム固有実装**:
  - `state/AppStateStore` は `createPlatformStateStorage` だけを expect/actual 化し、Android は DataStore、iOS は NSUserDefaults を利用。
  - `network/HttpClientFactory` は Android=OkHttp、iOS=Darwin のみ差し替え。
  - `parser/ParserFactory` と `ui/util/PlatformBackHandler` も expect/actual で必要最低限の差分に留めています。
- **リソース共有の実際**: UI からは `createRemoteBoardRepository()` を経由して `HttpBoardApi` + `HtmlParser`（どちらも commonMain）を利用しており、HTTP レイヤーや HTML 解析は両OSで同じ Kotlin コードを通ります。

---

## 📊 Project Overview

| Layer | Tech | 共通化率 | Purpose |
|-------|------|---------|----------|
| **UI** | **Compose Multiplatform** | ~95%+ | Android/iOS共通の宣言的UI |
| **State** | **AppStateStore** (expect/actual) | ~90% | 永続化層のみプラットフォーム固有 |
| **Network** | **Ktor Client** (KMP) | 100% | クロスプラットフォームHTTPクライアント (Shift_JIS HTML) |
| **Parser** | **HtmlParser** (expect/actual) | ~90% | ふたばHTMLからのデータ抽出 |
| **Repository** | **BoardRepository** (KMP) | 100% | ビジネスロジック・データ取得 |
| **Model** | Data classes (KMP) | 100% | 共通データモデル |
| **Testing** | **Ktor MockEngine / FakeRepository** | - | ユニット・契約・UIテスト |

---

## 🎯 コード共有の現状

### ✅ 完全共通化（commonMain）
- **UI層**: `FutachaApp`, `BoardManagementScreen`, `CatalogScreen`, テーマ等
- **ロジック層**: Repository, API, Model, ユーティリティ
- **状態管理**: AppStateStoreのコアロジック（Flow、シリアライゼーション）

### 🔀 プラットフォーム固有実装（expect/actual）

#### 1. 永続化層（AppStateStore）
- **Android**: `androidx.datastore.preferences` (Jetpack)
- **iOS**: `NSUserDefaults` (Foundation)
- **理由**: 各OSのネイティブストレージAPIに直接依存

#### 2. HTMLパーサー（HtmlParser）
- **Android**: Jsoup（JVM専用ライブラリ）
- **iOS**: ネイティブパーサー（WebKit/Foundation）
- **理由**: JsoupはJVM専用でiOSで動作しない

### 📱 エントリーポイント

**Android** (`MainActivity.kt`):
```kotlin
setContent {
    val stateStore = createAppStateStore(applicationContext)
    FutachaApp(stateStore = stateStore)  // 共通UI
}
```

**iOS** (`MainViewController.kt`):
```kotlin
fun MainViewController(): UIViewController {
    val stateStore = createAppStateStore()
    return ComposeUIViewController {
        FutachaApp(stateStore = stateStore)  // 同じ共通UI
    }
}
```

---

## 📂 Directory Structure（実際の構成・完全版）

```
project-root/
├── app-android/                    # Android専用アプリモジュール
│   ├── src/main/java/com/valoser/futacha/
│   │   └── MainActivity.kt         # エントリーポイント、VersionChecker初期化
│   └── build.gradle.kts            # versionCode: 1, versionName: "1.0"
│
├── shared/                         # Kotlin Multiplatform 共通コア（60+ファイル）
│   ├── src/commonMain/kotlin/      # 完全共通化コード（40+ファイル、~95%）
│   │   │
│   │   ├── model/                  # データモデル（8ファイル）
│   │   │   ├── Post.kt             # 投稿データ（ID、作成者、本文HTML、画像URL等）
│   │   │   ├── QuoteReference.kt   # 引用参照（>>1形式）
│   │   │   ├── CatalogItem.kt      # カタログアイテム（スレッドID、URL、サムネ等）
│   │   │   ├── ThreadPage.kt       # スレッドページ（投稿リスト、有効期限等）
│   │   │   ├── BoardStateModels.kt # BoardSummary, ThreadHistoryEntry
│   │   │   ├── CatalogMode.kt      # 7種類の表示モード（enum）
│   │   │   ├── SavedThread.kt      # 保存済みスレッド、進捗情報、保存ステータス
│   │   │   ├── MediaItem.kt        # メディアアイテム（画像・動画の抽象化）
│   │   │   └── CatalogItemExtensions.kt
│   │   │
│   │   ├── network/                # ネットワーク層（4ファイル）
│   │   │   ├── BoardApi.kt         # API interface（7メソッド）
│   │   │   ├── HttpBoardApi.kt     # Ktor実装（Cookie、全API）
│   │   │   ├── BoardUrlResolver.kt # URL解決、パストラバーサル対策
│   │   │   ├── HttpClientFactory.kt # expect/actual
│   │   │   └── NetworkException.kt
│   │   │
│   │   ├── parser/                 # HTMLパーサー（6ファイル）
│   │   │   ├── HtmlParser.kt       # interface
│   │   │   ├── CatalogHtmlParserCore.kt  # 正規表現ベース、ReDoS対策
│   │   │   ├── ThreadHtmlParserCore.kt   # 正規表現ベース、引用解析
│   │   │   ├── ParserFactory.kt    # expect/actual
│   │   │   └── ParserException.kt
│   │   │
│   │   ├── repo/                   # リポジトリ層（6ファイル + mock/）
│   │   │   ├── BoardRepository.kt  # DefaultBoardRepository
│   │   │   ├── BoardRepositoryFactory.kt
│   │   │   └── mock/
│   │   │       ├── FakeBoardRepository.kt
│   │   │       ├── MockBoardData.kt
│   │   │       ├── MockCatalogFixtures.kt
│   │   │       ├── MockThreadFixtures.kt
│   │   │       └── ExampleBoardHttpSamples.kt
│   │   │
│   │   ├── repository/             # 保存機能リポジトリ（1ファイル）
│   │   │   └── SavedThreadRepository.kt  # 保存済みスレッド管理
│   │   │
│   │   ├── service/                # ビジネスロジック（1ファイル）
│   │   │   └── ThreadSaveService.kt  # スレッド保存、進捗管理
│   │   │
│   │   ├── state/                  # 状態管理（1ファイル）
│   │   │   └── AppStateStore.kt    # Flow、JSON、Mutex、expect/actual
│   │   │
│   │   ├── ui/                     # Compose Multiplatform UI（11ファイル）
│   │   │   ├── FutachaApp.kt       # メインアプリ、画面遷移、履歴管理
│   │   │   ├── UpdateNotificationDialog.kt # バージョン通知ダイアログ
│   │   │   ├── PermissionRequest.kt    # パーミッション要求（expect/actual）
│   │   │   ├── theme/
│   │   │   │   └── FutachaTheme.kt # テーマ定義
│   │   │   ├── board/
│   │   │   │   ├── BoardManagementScreen.kt  # 3画面統合（4400行超）
│   │   │   │   │   - BoardManagementScreen（板管理、ドラッグ&ドロップ、ピン留め）
│   │   │   │   │   - CatalogScreen（カタログ、7モード、グリッド/リスト切替）
│   │   │   │   │   - ThreadScreen（スレッド詳細、投稿、画像プレビュー、動画再生）
│   │   │   │   ├── SaveProgressDialog.kt     # 保存進捗ダイアログ
│   │   │   │   ├── SavedThreadsScreen.kt     # 保存済みスレッド一覧
│   │   │   │   ├── PlatformVideoPlayer.kt    # expect/actual（動画プレーヤー）
│   │   │   │   ├── ImagePickerButton.kt      # 画像選択ボタン（expect/actual）
│   │   │   │   └── BoardManagementFixtures.kt
│   │   │   └── util/
│   │   │       └── PlatformBackHandler.kt    # expect/actual
│   │   │
│   │   ├── util/                   # ユーティリティ（4ファイル）
│   │   │   ├── ImagePicker.kt      # expect/actual（ImageData）
│   │   │   ├── FileSystem.kt       # expect/actual（ファイル操作抽象化）
│   │   │   ├── Logger.kt           # expect/actual（ログ出力）
│   │   │   └── BoardConfig.kt
│   │   │
│   │   └── version/                # バージョンチェック（1ファイル）
│   │       └── VersionChecker.kt   # interface、GitHub Releases API
│   │
│   ├── src/androidMain/kotlin/     # Android固有実装（14ファイル）
│   │   ├── parser/
│   │   │   ├── JsoupHtmlParser.kt           # CatalogHtmlParserCore使用
│   │   │   └── ParserFactory.android.kt
│   │   ├── state/
│   │   │   └── AppStateStore.android.kt     # DataStore Preferences
│   │   ├── network/
│   │   │   └── HttpClientFactory.android.kt # OkHttp
│   │   ├── util/
│   │   │   ├── ImagePicker.android.kt       # Uri→ImageData変換
│   │   │   ├── FileSystem.android.kt        # Android File API
│   │   │   ├── Logger.android.kt            # android.util.Log
│   │   │   └── PermissionHelper.android.kt  # 権限処理ヘルパー
│   │   ├── ui/
│   │   │   └── PermissionRequest.android.kt # Accompanist Permissions
│   │   ├── ui/board/
│   │   │   ├── ImagePickerButton.android.kt # ActivityResultContracts
│   │   │   └── PlatformVideoPlayer.android.kt
│   │   ├── ui/util/
│   │   │   └── PlatformBackHandler.kt       # BackHandler API
│   │   └── version/
│   │       └── VersionChecker.android.kt    # PackageManager
│   │
│   └── src/iosMain/kotlin/         # iOS固有実装（14ファイル）
│       ├── MainViewController.kt            # iOSエントリーポイント
│       ├── parser/
│       │   ├── AppleHtmlParser.kt           # CatalogHtmlParserCore使用
│       │   └── ParserFactory.ios.kt
│       ├── state/
│       │   └── AppStateStore.ios.kt         # NSUserDefaults
│       ├── network/
│       │   └── HttpClientFactory.ios.kt     # Darwin
│       ├── util/
│       │   ├── ImagePicker.ios.kt           # PHPickerViewController実装
│       │   ├── FileSystem.ios.kt            # iOS File API
│       │   └── Logger.ios.kt                # NSLog
│       ├── ui/
│       │   └── PermissionRequest.ios.kt     # iOS権限処理
│       ├── ui/board/
│       │   ├── ImagePickerButton.ios.kt     # PHPicker統合
│       │   └── PlatformVideoPlayer.ios.kt   # AVPlayer実装
│       ├── ui/util/
│       │   └── PlatformBackHandler.kt       # 空実装（ネイティブジェスチャー）
│       └── version/
│           └── VersionChecker.ios.kt        # NSBundle実装
│
├── codex.md                        # 詳細設計書（API、パーサー、実装状況）
├── README.md                       # プロジェクト概要、機能一覧
├── settings.gradle.kts
├── build.gradle.kts
└── gradle/
    └── libs.versions.toml          # 依存関係バージョン管理
```

### 📊 ファイル数の内訳（最新）

- **commonMain**: 約47ファイル（UI + ロジック + モデル + ネットワーク + パーサー + サービス + リポジトリ + Logger）
- **androidMain**: 14ファイル（永続化 + パーサー + ファイルシステム + 画像選択 + 権限処理 + バージョンチェック + Logger）
- **iosMain**: 14ファイル（エントリーポイント + 永続化 + パーサー + ファイルシステム + 画像選択 + バージョンチェック + Logger）
- **commonTest**: 3ファイル（テストコード）

**合計**: 約78ファイル（テスト含む）、コード共有率 ~95%

---

## 🔄 Current Implementation Snapshot

### ✅ 完全実装済み機能（Android）

#### UI・画面（3画面完全実装）
- **BoardManagementScreen**: 板管理画面
  - 板リスト表示（カード形式）
  - 板の追加・削除・並び替え・ピン留め
  - スレッド閲覧履歴ドロワー
  - 履歴の更新・削除・クリア

- **CatalogScreen**: カタログ画面
  - グリッド/リスト表示切替
  - 7種類の表示モード（Catalog, New, Old, Many, Few, Momentum, So）
  - 検索機能（タイトルフィルター）
  - リフレッシュ機能
  - スレッド作成ダイアログ
  - 履歴ドロワー統合

- **ThreadScreen**: スレッド詳細画面
  - 投稿一覧表示（LazyColumn、仮想スクロール）
  - 引用ハイライト（>>1形式）
  - そうだね投票ボタン
  - 削除メニュー（del依頼、本人削除）
  - 返信投稿ボトムシート（名前・メール・題名・本文・画像添付）
  - 画像・動画プレビュー（フルスクリーン、ピンチズーム）
  - スクロール位置自動復元
  - 有効期限表示
  - 削除済み投稿の特殊表示

#### ネットワーク層（全API実装済み）
- **HttpBoardApi**: Ktor実装クラス
  - `getCatalog()` - カタログ取得（全7モード）
  - `getThread()` - スレッド取得
  - `postReply()` - 返信投稿（テキスト+画像）
  - `createThread()` - スレッド作成
  - `voteSo()` - そうだね投票
  - `requestDeletion()` - del依頼（理由コード選択）
  - `deleteByUser()` - 本人削除（パスワード認証）
  - Cookie管理（posttime, cxyl等）
  - カタログセットアップ（fetchCatalogSetup）
  - User-Agent、Referer設定
  - レスポンスサイズ制限（20MB）

#### パーサー層（正規表現ベース）
- **CatalogHtmlParserCore**: カタログパーサー
  - テーブル構造解析
  - スレッドID・URL抽出
  - サムネイル画像URL取得
  - レス数・有効期限パース
  - HTMLエンティティデコード
  - ReDoS対策（10MBサイズ制限、100KBチャンク）

- **ThreadHtmlParserCore**: スレッドパーサー
  - OP投稿と返信の分離解析
  - 投稿ID・作成者・タイムスタンプ抽出
  - 引用参照解析（>>1形式）
  - そうだねラベル抽出
  - 画像・動画URL取得
  - 削除済み投稿検出
  - ReDoS対策（1500イテレーション制限、2050投稿上限、5秒タイムアウト）

#### データ永続化・状態管理
- **AppStateStore**:
  - Flow ベースの状態管理
  - JSON シリアライゼーション
  - Mutex による排他制御
  - 板リスト永続化
  - スレッド履歴永続化（最終閲覧時刻、レス数）
  - スクロール位置永続化（LazyColumn index + offset）
  - プラットフォーム固有実装（DataStore/NSUserDefaults）

#### 画像・メディア
- **Coil3 for Compose Multiplatform**
  - 非同期画像読み込み
  - キャッシュ管理
  - サムネイル→フルスクリーン遷移
  - ピンチズーム（SubcomposeAsyncImage + zoomable modifier）
  - 動画再生（プラットフォーム固有VideoPlayer）
  - 画像添付（Android: ImagePicker）

#### セキュリティ対策
- **パストラバーサル対策**: `BoardUrlResolver.sanitizeThreadId()`
- **ReDoS対策**: パーサーでサイズ・イテレーション制限
- **XSS対策**: HTMLエンティティデコード
- **HTTPS優先**: デフォルトでHTTPS使用

#### その他機能
- **バージョン通知**: GitHub Releases API連携（Android/iOS）
- **ダークモード**: 自動対応
- **検索・フィルター**: カタログ検索、ローカルソート
- **履歴管理**: メタデータ自動更新、履歴から削除
- **ログ出力**: クロスプラットフォームLogger（Android: android.util.Log、iOS: NSLog）

### ✅ iOS実装状況（完全実装完了）
- ✅ UI層完全動作（3画面すべて）
- ✅ ネットワーク通信（Ktor Darwin）
- ✅ 状態永続化（NSUserDefaults）
- ✅ HTMLパーサー（CatalogHtmlParserCore, ThreadHtmlParserCore）
- ✅ **画像選択機能（PHPickerViewController実装完了）**
  - `ImagePicker.ios.kt`: PHPickerViewController使用
  - `ImagePickerButton.ios.kt`: Compose統合完了
  - 画像データ読み込み・変換実装
- ✅ **バージョンチェッカー（NSBundle実装完了）**
  - `VersionChecker.ios.kt`: CFBundleShortVersionString取得
  - `MainViewController.kt`: VersionChecker統合完了
  - GitHub Releases API連携
- ✅ **動画プレーヤー（AVPlayer実装完了）**
  - `PlatformVideoPlayer.ios.kt`: AVPlayerViewController使用
  - 再生コントロール実装
  - 自動再生・クリーンアップ実装
- ✅ **ファイルシステム（NSFileManager実装完了）**
  - `FileSystem.ios.kt`: NSFileManager使用
  - NSDocumentDirectory対応
  - ファイル操作完全実装
- ✅ **権限処理（iOS実装完了）**
  - `PermissionRequest.ios.kt`: iOS権限処理実装
- ✅ **Logger（NSLog実装完了）**
  - `Logger.ios.kt`: NSLog使用
  - デバッグ・エラー・警告・情報ログ出力

**🎉 Android/iOS完全対応達成！コード共有率 ~95%**

### 📝 今後の拡張予定
- オフラインキャッシュ
- ダークモード切替UI
- プッシュ通知
- テストカバレッジ向上

### 🧪 テスト戦略
- **共通テスト**: Ktor MockEngine でネットワークテスト
- **契約テスト**: 実際のふたばHTML（Shift_JIS）でパーサー検証
- **UIテスト**: Compose Test APIで共通UIテスト

---

## 🧪 Local Testing

```bash
# 共通モジュールのテスト
./gradlew :shared:check

# Androidユニットテスト
./gradlew :app-android:testDebugUnitTest

# Androidインストゥルメントテスト
./gradlew :app-android:connectedDebugAndroidTest

# iOS向けテスト（要Xcode）
./gradlew :shared:iosSimulatorArm64Test
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│   Compose Multiplatform UI (共通)      │
│   FutachaApp / BoardManagementScreen    │
│   CatalogScreen / (未来: ThreadScreen)  │
└────────────────┬────────────────────────┘
                 │
        ┌────────▼───────────┐
        │  AppStateStore     │  ← Flow / Coroutines
        │  (共通ロジック)      │
        └────────┬───────────┘
                 │
        ┌────────▼───────────┐
        │ PlatformStorage    │  ← expect/actual
        │ Android: DataStore │
        │ iOS: NSUserDefaults│
        └────────────────────┘

┌─────────────────────────────────────────┐
│        BoardRepository (共通)           │
│     getCatalog / getThread / post       │
└────────┬────────────────────────────────┘
         │
    ┌────▼─────┐       ┌──────────┐
    │ BoardApi │       │HtmlParser│  ← expect/actual
    │ (Ktor)   │       │ (Jsoup/  │
    │ (共通)   │       │  Native) │
    └──────────┘       └──────────┘
```

### 設計原則
- **依存性逆転**: 共通ロジックはインターフェースに依存、実装は注入
- **expect/actual**: プラットフォームAPIが必須の箇所のみ分離
- **Single Source of Truth**: UI状態はAppStateStoreで一元管理

---

## 📋 Futaba Channel Parsing Targets

### Catalog Page (`/t/futaba.php?mode=cat`)

**HTML構造:**
- Table: `#cattable td`
  - `<a href='res/{threadId}.htm'>` - スレッドへのリンク
  - Thumbnail: `<img src='/t/cat/...'>` - サムネイル画像
  - Replies count: `<font size=2>` - レス数
  - Empty cells: `<small></small>` - 空セル

**パーサーロジック例（Jsoup）:**
```kotlin
val doc = Jsoup.parse(html, "https://www.example.com/t/")
doc.select("#cattable td").mapNotNull { td ->
    val a = td.selectFirst("a[href*=res/]") ?: return@mapNotNull null
    val href = a.absUrl("href")
    val id = Regex("""res/(\d+)\.htm""").find(href)?.groupValues?.get(1)
    val thumb = a.selectFirst("img")?.absUrl("src")
    val replies = td.selectFirst("font[size=2]")?.text()?.toIntOrNull() ?: 0
    CatalogItem(id!!, href, thumb, replies)
}
```

### Thread Page (`/t/res/{threadId}.htm`)

**HTML構造:**
- Root: `<div class="thre" data-res="{id}">`
- Replies: `<table border=0>`
- Deleted posts: `<table class="deleted">`

**要素セレクタ:**
- `.csb` → subject（題名）
- `.cnm` → name（名前）
- `.cnw` → date（日時）
- `.cno` → post number（レス番号）
- `blockquote` → message（本文）
- `a[href^="/t/src/"]` → full image（画像フルサイズ）
- `img[src^="/t/thumb/"]` → thumbnail（サムネイル）
- `.sod` → 「そうだね」ボタン

---

## 📦 依存関係の責務

### BoardRepository
- **役割**: 統合API、HTML→Modelの変換
- **実装**:
  - `BoardRepositoryImpl` - 本番実装（API + Parser使用）
  - `FakeBoardRepository` - モック実装（テスト・開発用）

### HtmlParser
- **役割**: クロスプラットフォームHTML解析（expect/actual）
- **実装**:
  - Android: `JsoupHtmlParser` - Jsoup使用
  - iOS: `AppleHtmlParser` - ネイティブパーサー

### BoardApi (Ktor)
- **役割**: HTTP通信（`GET /t/futaba.php`, `/t/res/{id}.htm`）
- **設定**: Shift_JIS、Cookie、カスタムヘッダー対応

---

## 🔧 レス操作の挙動（実機観測）

ふたばちゃんねるの実際の動作をキャプチャし、API仕様を整理しました。

### 1) スレッドへの返信（mode=regist）

**リクエスト:**
```
POST /{board}/futaba.php?guid=on
Content-Type: application/x-www-form-urlencoded; charset=Shift_JIS
Cookie: posttime, pwdc
```

**フィールド:**
- `mode=regist`, `resto`, `name`, `email`, `sub`, `com`, `pwd`
- hidden: `hash`, `ptua`, `pth*`, `scsz`, `js`, `chrenc`
- `responsemode=ajax`

**実装メモ:**
- Shift_JIS エンコード必須
- 成功/失敗は本文に依存（HTMLパース必要）
- hidden 値は HTML から抽出

### 2) 「そうだね」投票

**UI要素:** `<a class="sod" id="sd{resNo}">`

**リクエスト:**
```
GET /sd.php?b.{resNo}
Response: text/plain UTF-8, body="1" (success)
```

**実装メモ:**
- レスポンス本文で成功判定
- 二重投票防止は Cookie
- 楽観更新推奨（UIを先に更新）

### 3) del 依頼

**リクエスト:**
```
POST /del.php
mode=post&b=<board>&d=<resNo>&reason=<code>&responsemode=ajax
Response: text/html SJIS
```

**実装メモ:**
- `reason` は数値コード（例: 110）
- 成功でも本文ほぼ空
- UIトースト＋再読込推奨

### 4) 本人削除 (mode=usrdel)

**リクエスト:**
```
POST /{board}/futaba.php?guid=on
<resNo>=delete&pwd=…&onlyimgdel=&mode=usrdel&responsemode=ajax
Cookie: pwdc
Response: text/html SJIS
```

**実装メモ:**
- 成功後、`<table class="deleted">` に変化
- 画像のみ削除: `onlyimgdel=on`

---

## ✅ 実装チェックリスト

### BoardApi（✅ 全実装済み）
- [x] `getCatalog()` - カタログ取得
- [x] `getThread()` - スレッド取得
- [x] `postReply(board, fields)` - スレッド返信
- [x] `createThread(board, fields)` - スレッド作成
- [x] `voteSo(resNo)` - そうだね投票
- [x] `requestDeletion(board, resNo, reason)` - del依頼
- [x] `deleteByUser(board, resNo, pwd, onlyImg)` - 本人削除
- [x] Cookie管理（posttime, cxyl等）
- [x] カタログセットアップ（fetchCatalogSetup）

### HtmlParser（✅ 全実装済み）
- [x] カタログパース（CatalogHtmlParserCore）
- [x] スレッドパース（ThreadHtmlParserCore）
- [x] `a.sod` 抽出 - そうだねボタン（正規表現）
- [x] `table.deleted` 判定 - 削除済みレス
- [x] hidden フィールド抽出（カタログセットアップ経由）
- [x] 引用参照解析（>>1形式）
- [x] HTMLエンティティデコード

### Repository（✅ 全実装済み）
- [x] DefaultBoardRepository - 標準実装
- [x] FakeBoardRepository - モック実装
- [x] 楽観更新 / 再取得フロー
- [x] エラーハンドリング（try-catch + Result型）
- [x] Cookie初期化管理

### Compose UI（✅ 全実装済み）
- [x] 3画面完全実装（BoardManagement, Catalog, Thread）
- [x] 「そうだね」「del」「削除」メニュー
- [x] 返信投稿ボトムシート（画像添付含む）
- [x] スレッド作成ダイアログ
- [x] エラー表示（Snackbar）
- [x] 削除キー保持 (`pwdc` Cookie自動管理）
- [x] スクロール位置復元
- [x] 履歴ドロワー

### iOS実装完了項目（2025-11-11）
- [x] 画像選択機能（ImagePicker.ios.kt - PHPickerViewController）
- [x] バージョンチェッカー（VersionChecker.ios.kt - NSBundle）
- [x] 動画プレーヤー（PlatformVideoPlayer.ios.kt - AVPlayer）
- [x] ファイルシステム（FileSystem.ios.kt - NSFileManager）
- [x] 権限処理（PermissionRequest.ios.kt）

### 最新の実装追加（2025-11-11）
- [x] スレッド作成機能（画像添付対応）
- [x] 板アイコン選択機能（ImagePickerButton）
  - Android: ActivityResultContracts使用
  - iOS: PHPickerViewController使用

**すべてのiOS未実装項目が完了しました！**

---

## 🧪 テスト観点

### 実装済みテスト
- [x] モックリポジトリ（FakeBoardRepository）
- [x] モックデータ（MockBoardData, Fixtures）
- [x] パーサーのReDoS対策テスト

### 今後のテスト項目
- [ ] MockWebServer で全API系統テスト
- [ ] hidden 抽出欠落時の再試行
- [ ] SJIS ↔ UTF-8 round-trip 確認
- [ ] 削除済みレスのパース検証
- [ ] Cookie永続化テスト
- [ ] スクロール位置復元テスト
- [ ] エラーハンドリングテスト

---

## 🔔 バージョン通知機能

### 概要
アプリ起動時に自動的にGitHub Releases APIを使用して最新バージョンをチェックし、更新がある場合は通知ダイアログを表示する機能。

### アーキテクチャ

```
起動時 (MainActivity.onCreate / MainViewController)
  ↓
createVersionChecker(context, httpClient)
  ↓
FutachaApp(versionChecker = checker)
  ↓ LaunchedEffect
checkForUpdate()
  ↓
GitHub Releases API
  GET https://api.github.com/repos/inqueuet/futacha/releases/latest
  ↓
バージョン比較 (isNewerVersion)
  ↓
UpdateNotificationDialog 表示
  ├─ 「OK」→ ダイアログを閉じる
  └─ 「後で」→ ダイアログを閉じる
```

### ファイル構成

```
shared/src/
├── commonMain/kotlin/version/
│   ├── VersionChecker.kt              # Interface + 共通ロジック
│   │   - VersionChecker interface
│   │   - UpdateInfo data class
│   │   - isNewerVersion() 関数
│   │   - fetchLatestVersionFromGitHub() 関数
│   │
│   └── ui/UpdateNotificationDialog.kt # 通知ダイアログUI
│
├── androidMain/kotlin/version/
│   └── VersionChecker.android.kt      # Android実装
│       - PackageManagerから現在のバージョンを取得
│       - createVersionChecker(Context, HttpClient)
│
└── iosMain/kotlin/version/
    └── VersionChecker.ios.kt          # iOS実装 (準備中)
        - Bundle.main.infoDictionaryから取得予定
```

### 実装詳細

#### 1. VersionChecker Interface (commonMain)

```kotlin
interface VersionChecker {
    fun getCurrentVersion(): String
    suspend fun checkForUpdate(): UpdateInfo?
}

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val message: String
)
```

#### 2. バージョン比較ロジック

```kotlin
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    // "v1.0.0" → "1.0.0"
    val current = currentVersion.removePrefix("v").split(".")
    val latest = latestVersion.removePrefix("v").split(".")

    // メジャー.マイナー.パッチの順に比較
    for (i in 0 until maxOf(current.size, latest.size)) {
        val c = current.getOrNull(i)?.toIntOrNull() ?: 0
        val l = latest.getOrNull(i)?.toIntOrNull() ?: 0
        if (l > c) return true
        if (l < c) return false
    }
    return false
}
```

#### 3. GitHub Releases API

**エンドポイント:**
```
GET https://api.github.com/repos/inqueuet/futacha/releases/latest
```

**認証:** 不要（パブリックリポジトリ）

**レスポンス例:**
```json
{
  "tag_name": "v1.1.0",
  "name": "Release 1.1.0",
  "body": "新機能を追加しました",
  "published_at": "2025-11-11T00:00:00Z"
}
```

**Rate Limit:**
- 認証なし: 60リクエスト/時間
- 認証あり: 5000リクエスト/時間

アプリ起動時のみチェックするため、Rate Limitは問題なし。

#### 4. Android実装

```kotlin
class AndroidVersionChecker(
    private val context: Context,
    private val httpClient: HttpClient
) : VersionChecker {

    override fun getCurrentVersion(): String {
        return context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "1.0"
    }

    override suspend fun checkForUpdate(): UpdateInfo? {
        val release = fetchLatestVersionFromGitHub(
            httpClient, "inqueuet", "futacha"
        ) ?: return null

        val current = getCurrentVersion()
        val latest = release.tag_name.removePrefix("v")

        if (!isNewerVersion(current, latest)) {
            return null
        }

        return UpdateInfo(
            currentVersion = current,
            latestVersion = latest,
            message = buildUpdateMessage(current, latest, release.name)
        )
    }
}
```

#### 5. UI統合 (FutachaApp.kt)

```kotlin
@Composable
fun FutachaApp(
    stateStore: AppStateStore,
    versionChecker: VersionChecker? = null
) {
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(versionChecker) {
        versionChecker?.let { checker ->
            try {
                updateInfo = checker.checkForUpdate()
            } catch (e: Exception) {
                println("Version check failed: ${e.message}")
            }
        }
    }

    updateInfo?.let { info ->
        UpdateNotificationDialog(
            updateInfo = info,
            onDismiss = { updateInfo = null }
        )
    }

    // ... 既存のUI
}
```

### セキュリティとプライバシー

- ✅ **認証情報不要**: GitHub APIは公開データのみ使用
- ✅ **個人情報なし**: デバイス情報やユーザー情報を送信しない
- ✅ **オプトアウト不要**: 通知のみで強制しない
- ✅ **App Store/Google Playポリシー準拠**: ストアへの誘導なし

### マルチストア対応

この実装は以下の配布方法に対応:

1. **Google Play Store** - 通知のみ、ストア更新は手動
2. **Apple App Store** - 通知のみ、ストア更新は手動
3. **GitHub Releases** - 通知のみ、手動ダウンロード

**注意:** ストアへのディープリンクは審査ポリシーに違反する可能性があるため、通知のみに留める。

### 今後の拡張

- [x] iOS版の完全実装（NSBundle使用）✅
- [ ] 更新頻度制限（1日1回のみチェック）
- [ ] リリースノートの表示
- [ ] ユーザー設定で通知ON/OFF切り替え

---

## 📥 スレッド保存機能

### 概要

スレッド全体（HTML + 画像/動画）をオフラインで閲覧可能な形式で保存する機能です。

### 特徴

- ✅ **オフライン閲覧**: ネットワーク不要で保存済みスレッドを表示
- ✅ **共有ストレージ**: ファイルマネージャー/ファイルアプリからアクセス可能
- ✅ **進捗表示**: リアルタイムパーセンテージ表示
- ✅ **エラーハンドリング**: 一部失敗でも保存継続
- ✅ **ポータビリティ**: 相対パスでディレクトリ移動に対応

### アーキテクチャ

```
┌─────────────────────────────────────────────────────┐
│ ThreadScreen (UI)                                   │
│  └─ Save Button → ThreadSaveService                │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│ ThreadSaveService                                   │
│  - saveThread(): スレッド保存処理                    │
│  - saveProgress: StateFlow<SaveProgress>            │
│  - downloadMedia(): メディアダウンロード（HEADチェック）│
│  - convertHtmlPaths(): HTML相対パス変換              │
│  - generateHtml(): スタンドアロンHTML生成             │
│  - urlToPathMap: URL→ローカルパスマッピング           │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│ FileSystem (expect/actual)                          │
│  - Android: 共有Documents (Environment)             │
│  - iOS: NSDocumentDirectory                         │
└─────────────────────────────────────────────────────┘
```

### データモデル

#### SavedThread
```kotlin
data class SavedThread(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val title: String,
    val thumbnailPath: String?,
    val savedAt: Long,
    val postCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val totalSize: Long,
    val status: SaveStatus  // DOWNLOADING, COMPLETED, FAILED, PARTIAL
)
```

#### SaveProgress
```kotlin
data class SaveProgress(
    val phase: SavePhase,    // PREPARING, DOWNLOADING, CONVERTING, FINALIZING
    val current: Int,
    val total: Int,
    val currentItem: String
) {
    // 全体進捗パーセンテージ計算（重み付き）
    fun getOverallProgressPercentage(): Int
}
```

### ディレクトリ構造

```
Android: /storage/emulated/0/Documents/futacha/saved_threads/
iOS: NSDocumentDirectory/saved_threads/

saved_threads/
├── index.json                    # 保存済みスレッド一覧
└── {threadId}/                   # スレッドごとのディレクトリ
    ├── metadata.json             # メタデータ
    ├── thread.html               # スタンドアロンHTML
    ├── images/                   # 画像ディレクトリ
    │   ├── thumb_1_xxx.jpg
    │   ├── img_1_xxx.jpg
    │   └── ...
    └── videos/                   # 動画ディレクトリ
        ├── vid_1_xxx.mp4
        └── ...
```

### 進捗管理

#### フェーズ別重み配分
- **準備**: 1%（ディレクトリ作成）
- **ダウンロード**: 97%（メディアファイル取得）
- **変換**: 1%（HTML相対パス変換）
- **完了**: 1%（メタデータ保存）

#### 計算例
```
ダウンロード 50/100 の場合:
  → 1% + (97% × 0.5) = 49.5% ≒ 49%

変換 80/100 の場合:
  → 1% + 97% + (1% × 0.8) = 98.8% ≒ 98%
```

### ファイルサイズ制限

- **最大サイズ**: 8000KB (8MB)
- **サポート形式**:
  - 画像: GIF, JPG, JPEG, PNG, WEBP
  - 動画: MP4, WEBM
- **チェックタイミング**: HEADリクエストでContent-Length確認後ダウンロード

### HTML変換

#### URL-to-Pathマッピング
ダウンロード時に各メディアURLと保存先ローカルパスをマッピングすることで、HTML変換時に正確な相対パスを使用します。

```kotlin
// ダウンロード時
val urlToPathMap = mutableMapOf<String, String>()
downloadResult.onSuccess { fileInfo ->
    urlToPathMap[mediaItem.url] = fileInfo.relativePath
}

// HTML変換時
val convertedHtml = convertHtmlPaths(post.messageHtml, urlToPathMap)
```

#### 相対パス変換例
```html
<!-- 元のHTML -->
<img src="https://example.com/thumb/12345.jpg">

<!-- 変換後 -->
<img src="images/thumb_1_12345.jpg">
```

変換処理は`convertHtmlPaths()`で実行され、`<img>`タグと`<a>`タグのURLを相対パスに置換します。

#### スタンドアロンHTML
```html
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>{スレッドタイトル}</title>
    <style>
        /* インラインCSS */
    </style>
</head>
<body>
    <div class="metadata">
        <h1>{タイトル}</h1>
        <p>板: {板名}</p>
        <p>保存日時: {日時}</p>
    </div>
    <div class="post">
        <!-- 各投稿 -->
        <img src="images/img_xxx.jpg" />  <!-- 相対パス -->
    </div>
</body>
</html>
```

### エラーハンドリング

#### ダウンロード失敗時の挙動
1. **HEADリクエストでファイルサイズチェック**
   - Content-Lengthを取得
   - 8000KB超過 → 例外をスローしてスキップ

2. **拡張子チェック**
   - URLまたはContent-Typeから拡張子を取得
   - サポート外の形式 → 例外をスローしてスキップ

3. **ダウンロード失敗時の処理**
   ```kotlin
   downloadResult
       .onSuccess { fileInfo ->
           urlToPathMap[mediaItem.url] = fileInfo.relativePath
           totalSize += fileSystem.getFileSize(...)
       }
       .onFailure { error ->
           downloadFailureCount++
           println("Failed to download ${mediaItem.url}: ${error.message}")
           // 処理継続
       }
   ```

4. **最終ステータス決定**:
   ```kotlin
   val status = when {
       downloadFailureCount == 0 -> SaveStatus.COMPLETED
       downloadFailureCount < mediaItems.size -> SaveStatus.PARTIAL
       else -> SaveStatus.FAILED
   }
   ```

### UI実装

#### SaveProgressDialog
```kotlin
@Composable
fun SaveProgressDialog(
    progress: SaveProgress?,
    onDismissRequest: () -> Unit
) {
    // 全体進捗パーセンテージ表示
    val overallPercentage = progress.getOverallProgressPercentage()

    Text("$overallPercentage%")  // 大きく表示
    LinearProgressIndicator(progress = overallPercentage / 100f)
    Text("${progress.current} / ${progress.total}")  // 詳細
    Text(progress.currentItem)  // 現在処理中のアイテム
}
```

#### SavedThreadsScreen
- 保存済みスレッド一覧表示
- ステータスバッジ（完了/一部/失敗/DL中）
- 統計情報（投稿数、画像数、容量）
- 削除機能

### プラットフォーム固有実装

#### Android
```kotlin
class AndroidFileSystem(private val context: Context) : FileSystem {
    override fun getAppDataDirectory(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || isExternalStorageWritable()) {
            getPublicDocumentsDirectory()  // /Documents/futacha/
        } else {
            context.filesDir.absolutePath  // フォールバック
        }
    }
}
```

**パーミッション**:
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

#### iOS
```kotlin
class IosFileSystem : FileSystem {
    override fun getAppDataDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        return (paths.firstOrNull() as? String) ?: ""
    }
}
```

### メモリ効率化

#### チャンク処理
大量の投稿を一度に処理するとメモリ不足になる可能性があるため、チャンク単位で処理：

```kotlin
val chunkSize = 50 // 50投稿ずつ処理
posts.chunked(chunkSize).forEach { postChunk ->
    val mediaItems = postChunk.flatMap { post ->
        buildList {
            post.thumbnailUrl?.let { add(MediaItem(it, MediaType.THUMBNAIL, post)) }
            post.imageUrl?.let { add(MediaItem(it, MediaType.FULL_IMAGE, post)) }
        }
    }
    // ダウンロード処理
}
```

#### ストリーミングHTML生成
HTMLを文字列として一度にメモリに展開せず、StringBuilder で直接ファイルに書き込み：

```kotlin
val estimatedSize = metadata.posts.size * 500 // 容量予測でreallocation削減
val htmlBuilder = StringBuilder(estimatedSize)

// ヘッダー書き込み
htmlBuilder.appendLine("<!DOCTYPE html>")
// ...

// 投稿を逐次追加（中間リスト作成なし）
metadata.posts.forEach { post ->
    htmlBuilder.apply {
        appendLine("    <div class=\"post\" id=\"post-${post.id}\">")
        // ...
    }
}

// 単一write操作
fileSystem.writeString(filePath, htmlBuilder.toString()).getOrThrow()
```

### 実装状況

- ✅ 保存済みスレッド一覧画面（SavedThreadsScreen）
- ✅ 保存済みスレッドの削除機能
- ✅ URL-to-Pathマッピングによる正確な相対パス変換
- ✅ ファイルサイズ・形式チェック（8000KB, GIF/JPG/PNG/WEBP/MP4/WEBM）
- ✅ 進捗パーセンテージ表示（重み付き計算）
- ✅ 共有ストレージ保存（Android: Documents、iOS: NSDocumentDirectory）
- ✅ **メモリ効率化**（チャンク処理、ストリーミングHTML生成）
- ✅ **SavedThreadRepository**（インデックス管理、Mutex排他制御）

### 今後の拡張

- [ ] ストレージ容量管理（自動削除、古いスレッド自動クリーンアップ）
- [ ] ZIP形式でのエクスポート機能
- [ ] スレッド更新機能（差分ダウンロード）
- [ ] 保存済みスレッドの検索・フィルタリング機能

---

---

## 🧩 クロスプラットフォーム共通化戦略

### Logger実装（expect/actual）

#### 共通インターフェース（commonMain）
```kotlin
expect object Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String)
    fun i(tag: String, message: String)
}
```

#### Android実装（androidMain）
```kotlin
actual object Logger {
    actual fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.e(tag, message, throwable)
    }
    // ...
}
```

#### iOS実装（iosMain）
```kotlin
actual object Logger {
    actual fun d(tag: String, message: String) {
        platform.Foundation.NSLog("[$tag] DEBUG: $message")
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        platform.Foundation.NSLog("[$tag] ERROR: $message${throwable?.let { "\n$it" } ?: ""}")
    }
    // ...
}
```

### 使用例
```kotlin
Logger.d("ThreadSaveService", "Starting download for thread $threadId")
Logger.e("NetworkError", "Failed to fetch catalog", exception)
```

---

**この codex.md は、チーム設計書兼実装仕様書として利用します。**
既存のカタログ・スレパーサ仕様および各API挙動をすべて集約しています。

---
