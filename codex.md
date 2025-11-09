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

## 📂 Directory Structure（実際の構成）

```
project-root/
├── app-android/                    # Android専用アプリモジュール（エントリーポイントのみ）
│   ├── src/main/java/
│   │   └── com/valoser/futacha/
│   │       └── MainActivity.kt     # setContent { FutachaApp() }
│   └── build.gradle.kts            # 共通UIを依存に追加
│
├── shared/                         # Kotlin Multiplatform 共通コア
│   ├── src/commonMain/kotlin/      # 完全共通化コード（~95%）
│   │   ├── ui/                     # ✅ Compose Multiplatform UI
│   │   │   ├── FutachaApp.kt                # メインアプリUI
│   │   │   ├── theme/FutachaTheme.kt        # テーマ定義
│   │   │   └── board/
│   │   │       ├── BoardManagementScreen.kt # 板管理画面
│   │   │       └── CatalogScreen.kt         # カタログ画面
│   │   ├── state/                  # ✅ 状態管理
│   │   │   └── AppStateStore.kt             # Flow/シリアライゼーション
│   │   ├── model/                  # ✅ データモデル
│   │   │   ├── Post.kt, ThreadPage.kt
│   │   │   ├── CatalogItem.kt
│   │   │   └── BoardStateModels.kt
│   │   ├── network/                # ✅ ネットワーク層
│   │   │   └── BoardApi.kt                  # Ktor Client（Shift_JIS対応）
│   │   ├── repo/                   # ✅ リポジトリ層
│   │   │   ├── BoardRepository.kt
│   │   │   └── mock/FakeBoardRepository.kt
│   │   ├── parser/                 # ✅ パーサーIF
│   │   │   └── HtmlParser.kt                # interface定義
│   │   └── util/                   # ✅ ユーティリティ
│   │       └── BoardConfig.kt
│   │
│   ├── src/androidMain/kotlin/     # Android固有実装
│   │   ├── state/
│   │   │   └── AppStateStore.android.kt     # 🔀 DataStore実装
│   │   └── parser/
│   │       └── JsoupHtmlParser.kt           # 🔀 Jsoup実装
│   │
│   ├── src/iosMain/kotlin/         # iOS固有実装
│   │   ├── MainViewController.kt            # iOSエントリーポイント
│   │   ├── state/
│   │   │   └── AppStateStore.ios.kt         # 🔀 NSUserDefaults実装
│   │   └── parser/
│   │       └── AppleHtmlParser.kt           # 🔀 ネイティブパーサー
│   │
│   ├── src/commonTest/kotlin/      # 共通テスト
│   └── build.gradle.kts            # KMP設定（android/iOS targets）
│
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

### 📊 ファイル数の内訳

- **commonMain**: 16ファイル（UI + ロジック + モデル）
- **androidMain**: 2ファイル（永続化 + パーサー）
- **iosMain**: 3ファイル（エントリーポイント + 永続化 + パーサー）

---

## 🔄 Current Implementation Snapshot

### ✅ 実装済み機能
- **Compose Multiplatform UI**: Android/iOS共通のUI（`FutachaApp`, 板管理、カタログ画面）
- **状態管理**: `AppStateStore` + expect/actual（DataStore/NSUserDefaults）
- **永続化**: 板リスト・閲覧履歴の保存/復元
- **Mock実装**: `FakeBoardRepository` + サンプルデータ
- **テーマ**: ライト/ダークモード対応

### 🚧 進行中/TODO
- HTMLパーサー実装（現在はスタブ）
- ネットワークAPI実装（Ktor + Shift_JIS）
- スレッド詳細画面
- レス投稿機能
- 画像ビューアー

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
val doc = Jsoup.parse(html, "https://dat.2chan.net/t/")
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

### BoardApi（未実装）
- [ ] `postReply(board, fields)` - スレッド返信
- [ ] `voteSo(resNo)` - そうだね投票
- [ ] `requestDeletion(board, resNo, reason)` - del依頼
- [ ] `deleteByUser(board, resNo, pwd, onlyImg)` - 本人削除

### HtmlParser（未実装）
- [ ] `a.sod` 抽出 - そうだねボタン
- [ ] `table.deleted` 判定 - 削除済みレス
- [ ] hidden フィールド抽出（返信用）

### Repository
- [ ] 楽観更新 / 再取得フロー
- [ ] エラーハンドリング

### Compose UI
- [ ] 「そうだね」「del」「削除」メニュー
- [ ] Snackbar エラー表示
- [ ] 削除キー保持 (`pwdc` Cookie)

---

## 🧪 テスト観点

- [ ] MockWebServer で4系統APIテスト
- [ ] hidden 抽出欠落時の再試行
- [ ] SJIS ↔ UTF-8 round-trip 確認
- [ ] 削除済みレスのパース検証

---

**この codex.md は、チーム設計書兼実装仕様書として利用します。**
既存のカタログ・スレパーサ仕様および各API挙動をすべて集約しています。

---
