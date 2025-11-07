# Codex ? Futaba Channel Browser (KMP / Compose + Hilt)

> Kotlin Multiplatform project for a dedicated **Futaba Channel browser**.  
> Android UI is built with **Jetpack Compose**, dependency management via **Hilt**,  
> and shared logic (network, parser, repository) is implemented as a **KMP shared module**.

---

## ?? Project Overview

| Layer | Tech | Purpose |
|-------|------|----------|
| **UI** | Jetpack **Compose** | Declarative Android UI |
| **DI** | **Hilt (Dagger)** | Dependency injection for Android side |
| **Network** | **Ktor Client** (KMP) | Cross-platform HTTP client (Shift_JIS HTML) |
| **Parser** | **HtmlParser** abstraction (`Jsoup` for Android / `expect/actual` for iOS) | Extract data from Futaba’s HTML |
| **Shared Logic** | Kotlin Multiplatform (`shared/`) | Common repository, model, network, parser |
| **Testing** | **Ktor MockEngine / MockWebServer / Hilt replace modules** | Unit + contract + UI tests |

---

## ?? Directory Structure

project-root/
├── app-android/ # Android app (Compose + Hilt)
│ ├── src/main/java/.../App.kt # @HiltAndroidApp
│ ├── src/main/java/.../di/ # Hilt Modules (provide shared logic)
│ ├── src/main/java/.../ui/ # Compose UI (catalog, thread, components, navigation)
│ ├── src/main/assets/fixtures/ # Demo HTMLs (UTF-8, used by FakeRepository)
│ ├── src/androidTest/... # UI tests (Hilt replaces repo with Fake)
│ ├── src/test/... # JVM tests (MockWebServer, Shift_JIS contract)
│ └── build.gradle.kts
├── shared/ # Kotlin Multiplatform shared core
│ ├── src/commonMain/kotlin/
│ │ ├── model/ # CatalogItem, Post, ThreadPage
│ │ ├── network/ # Ktor BoardApi (GET /t/futaba.php, /t/res/{id}.htm)
│ │ ├── parser/ # HtmlParser IF + common/KMP impl
│ │ ├── repo/ # BoardRepository interface + impl
│ │ └── util/ # Utility / config
│ ├── src/commonTest/kotlin/ # Contract tests (Ktor MockEngine)
│ ├── src/androidMain/kotlin/ # JsoupHtmlParser (Android-specific)
│ ├── src/iosMain/kotlin/ # Future iOS parser impl or bridge
│ └── build.gradle.kts
├── app-ios/ # (Future) iOS app
│ └── Compose Multiplatform or SwiftUI launcher
└── .github/workflows/ # CI/CD pipelines (unit/common + Android UI)

yaml
コードをコピーする

---

## ? Current Implementation Snapshot

- Compose Shell (`MainActivity` → `FutachaApp`)
- Shared KMP module exposes `model/`, `network/`, `parser/`, `repo/`, `util/`
- Fixtures in `app-android/src/main/assets/fixtures/`
- Gradle wiring supports both modules
- Hilt injects real `BoardApi` + `HtmlParser`
- MockWebServer + Ktor MockEngine for tests
- Parser/Network contract tests use real Futaba HTMLs (Shift_JIS)

---

## ?? Local Testing

./gradlew :shared:check
./gradlew :app-android:testDebugUnitTest
./gradlew :app-android:connectedDebugAndroidTest

kotlin
コードをコピーする

---

## ?? Futaba Channel Parsing Targets

### Catalog Page (`/t/futaba.php?mode=cat`)
HTML sample: `catalog.html`

- Table: `#cattable td`
  - `<a href='res/{threadId}.htm'>`
  - Thumbnail: `<img src='/t/cat/...'>`
  - Replies count: `<font size=2>`
  - Empty cells may contain `<small></small>`

**Parser logic example:**
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
Thread Page (/t/res/{threadId}.htm)
Root: <div class="thre" data-res="{id}">

Replies: <table border=0>

Deleted posts: <table class="deleted">

Info:

.csb ? subject

.cnm ? name

.cnw ? date

.cno ? post number

blockquote ? message

a[href^="/t/src/"] ? full image

img[src^="/t/thumb/"] ? thumbnail

?? Architecture
css
コードをコピーする
[Compose UI] → [ViewModel] → [BoardRepository] → [BoardApi + HtmlParser]
BoardRepository
Unified API

Handles transformation HTML→Model

Impl: BoardRepositoryImpl (real) / FakeBoardRepository (mock)

HtmlParser
Interface for cross-platform parsing (expect/actual)

Android: Jsoup

iOS: Ksoup (future)

BoardApi (Ktor)
Handles GET /t/futaba.php, /t/res/{id}.htm

Configured for Shift_JIS, cookies, headers

?? レス操作の挙動（実機観測）
ふたばちゃんねるの スレッド返信, レスの「そうだね」, del 依頼, レス削除（本人削除） をキャプチャし、UI とネットワーク動作を整理しました。
KMP 側の BoardApi・HtmlParser・Repository 実装の指針にもなります。

1) スレッドへの返信（mode=regist）
POST /{board}/futaba.php?guid=on

フィールド:

mode=regist, resto, name, email, sub, com, pwd

hidden: hash, ptua, pth*, scsz, js, chrenc

responsemode=ajax

Cookie: posttime, pwdc

Shift_JIS でエンコード

実装メモ

SJIS 対応

成功/失敗は本文に依存

hidden 値は HTML から抽出

2) 「そうだね」投票
UI: <a class="sod" id="sd{resNo}">

JS: sd(resNo)

GET /sd.php?b.{resNo}

text/plain UTF-8, body=1 success

実装メモ

本文で成功判定

二重投票防止は Cookie

楽観更新推奨

3) del 依頼
POST /del.php

mode=post&b=<board>&d=<resNo>&reason=<code>&responsemode=ajax

Response: text/html SJIS

実装メモ

reason は数値コード（例 110）

成功でも本文ほぼ空

UI トースト＋再読込推奨

4) 本人削除 (mode=usrdel)
POST /board/futaba.php?guid=on

<resNo>=delete&pwd=…&onlyimgdel=&mode=usrdel&responsemode=ajax

Cookie: pwdc

Response: text/html SJIS

実装メモ

<table class="deleted"> に変化

画像削除は onlyimgdel=on

5) カタログ／スレ HTML補遺
カタログ: #cattable td <a href='res/{id}.htm'> <font size=2>

空セル: <small></small>

スレ:

<div class="thre" data-res>

<table border=0> / .deleted

.csb, .cnm, .cnw, .cno, .sod, blockquote

画像リンクあり

6) 実装チェックリスト
BoardApi:

postReply

voteSo

requestDeletion

deleteByUser

HtmlParser:

a.sod, table.deleted 抽出

Repository:

楽観更新 / 再取得

Compose UI:

「そうだね」「del」「削除」メニュー

SNACKBAR エラー

削除キー保持 (pwdc)

7) テスト観点
MockWebServer で4系統APIテスト

hidden 抽出欠落時の再試行

SJIS <-> UTF-8 round-trip 確認

この codex.md は、チーム設計書兼実装仕様書として利用します。
既存のカタログ・スレパーサ仕様および各API挙動をすべて集約しています。

yaml
コードをコピーする

---
