# Futacha - ふたばちゃんねるブラウザ

> Kotlin Multiplatform × Compose Multiplatform クライアント。Android と iOS が同じ UI ツリー (`FutachaApp`) を共有し、プラットフォーム側はホスティングと依存注入だけを行います。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_MPP-1.9-green.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ Highlights

- **Compose Multiplatform UI**: `ui/board/BoardManagementScreen.kt` (~4.7k 行) に Board/Catalog/Thread の 3 画面と共通ドロワーを集約。ホスト (MainActivity / MainViewController) は `FutachaApp` をそのまま呼び出すだけです。
- **モック/本番両対応**: `FakeBoardRepository` が `example/` のキャプチャ HTML を返し、`BoardSummary.isMockBoard()` で `example.com` ドメインのときだけモックを利用。実際の板 URL を設定すると自動的に Ktor + Futaba API が使われます。
- **履歴とプライバシー**: `AppStateStore` (DataStore / NSUserDefaults) が板リスト・閲覧履歴・スクロール位置・プライバシーフラグを Flow で供給。スクロール保存は 500ms デバウンス付きでディスク I/O を削減。
- **Thread 体験**: 引用プレビュー (`QuotePreviewDialog`)、ID 別ハイライト、スレ内検索 (前/次ナビ付き)、long-press アクションシート、ギャラリーシート、画像/動画プレビュー、ヒストリードロワーがすべて共通コードで動作。
- **スレ保存 (Android)**: `ThreadSaveService` が HTML + 画像を `saved_threads` に保存し、`SaveProgressDialog` で進捗をリアルタイム表示。`SavedThreadRepository` が `index.json` を管理。iOS はホストが `FileSystem` を渡していないため snackbar で案内されます。
- **GitHub Releases チェック**: `version/VersionChecker.kt` が `releases/latest` を確認し、新バージョンを `UpdateNotificationDialog` で知らせます。プラットフォーム固有ロジックは VersionChecker actual に閉じ込めています。
- **投稿の安定化**: `HttpBoardApi` が板ごとの `chrenc` 設定をキャッシュし、新設の `TextEncoding` util で Shift_JIS/UTF-8 を切り替えつつ `ptua`/`hash` などのメタを付与して `createThread`/`replyToThread` を送信、応答からスレッドIDやエラー理由を拾って結果を伝えます。
- **ImageLoader のキャッシュ**: `LocalFutachaImageLoader` はメモリキャッシュと任意のディスクキャッシュを持つ Coil3 ImageLoader を提供し、カタログ Thumbnail の描画を安定化させます。

詳細な API / パーサー仕様は [codex.md](codex.md) を参照してください。

---

## 🖥️ Screens & UX

### Board Management (`shared/src/commonMain/kotlin/ui/board/BoardManagementScreen.kt`)
- 大きなカードで板リストを表示。ピン留め状態はアイコンで示すのみ (トグル UI は未実装)。
- メニューから **追加** (URL バリデーション + 重複チェック)、**削除モード** (カードごとの確認ダイアログ)、**並び替えモード** (上下ボタン) を切り替え。その他メニューは現在モック通知のみ。
- どの画面でも同じ `HistoryDrawerContent` を使うモーダルドロワーを持ち、履歴を開く/更新/一括削除/設定 (モック) をまとめて操作。

### Catalog
- 常に 5 列の `LazyVerticalGrid`。`CatalogMode` をボトム `NavigationBar` から切り替え (スレ立て/更新/モード/設定)。
- Pull-to-refresh (`PullToRefreshBox`) に加え、グリッド最下部にもドラッグ判定を置いて連続更新を楽にしています。
- トップバーは検索モードとタイトル表示をトグル。検索時は back ハンドラで検索解除 → ドロワー → 画面遷移の順で戻る挙動を実装。
- `CreateThreadDialog` が `BoardRepository.createThread()` を呼び、新規スレ作成後にカタログを再取得します。
- 設定シート (`CatalogSettingsSheet`) は 6 項目 (監視ワード、NG管理(〇)、外部アプリ(〇)、表示の切り替え(〇)、一番上に行く(〇)、プライバシー(〇)) を提供。〇の項目は実装済みで、NG管理は `NgManagementSheet` (ワードのみ)、外部アプリは `mode=cat` URL を開き、表示切替は `DisplayStyleDialog`、一番上は `scrollCatalogToTop()`、プライバシーは `AppStateStore.setPrivacyFilterEnabled()` をトグル。監視ワードは `WatchWordsSheet` で編集でき、登録ワードを含むタイトルのスレッドはカタログ更新時に履歴へ自動追加されます。※凡例: 〇=対応、△=基本実装、無印=未実装。

### Thread
- `ThreadTopBar` で Board 名 / ステータス / レス数を表示しつつ、スレ内検索 UI (ヒット件数と前/次ボタン付き) を提供。
- `LazyColumn` の各投稿カードは subject/author/ID/引用/画像を表示。引用 or ID をタップすると `QuotePreviewDialog` で該当レス群をまとめて確認できます。
- long-press で開くアクションシートは **そうだね** / **del 依頼** / **本人削除**。成功時は `Snackbar` + 楽観的 UI で通知。
- `ThreadActionBar` の 7 ボタン: 返信 (`ThreadFormDialog` + ActivityResult/PHPicker)、最上部 / 最下部スクロール、再読み込み、ギャラリー (`ThreadImageGallery`)、保存 (Android で `ThreadSaveService` を起動)、設定 (`ThreadSettingsSheet` で NG管理(〇) / 外部アプリ(〇) / 読み上げ(△) / プライバシー(〇) を表示)。
- 画像はピンチズーム + スワイプ dismiss 可能な `ImagePreviewDialog` を使用し、Coil のロード状態に応じてスピナー/エラーメッセージ/外部ブラウザ導線を切り替え。動画は `VideoPreviewDialog` 経由で状態管理され、Android (ExoPlayer) / iOS (AVPlayer + WEBM は WKWebView) の再生状態を UI に反映します。
- スクロール位置は `snapshotFlow` + 500ms デバウンスで `AppStateStore.updateHistoryScrollPosition()` に保存されます。

### Saved Threads
- `SaveProgressDialog` が進捗を描画 (閉じるボタンは完了後のみ有効)。
- `SavedThreadsScreen` (一覧 + 削除確認) は実装済みですが、まだナビゲーションに接続されていません。

---

## 💾 State, Persistence & Privacy
- `AppStateStore`:
  - `boards` / `history` / `isPrivacyFilterEnabled` に加え、`catalogDisplayStyle`、板&スレ NG (`ngHeaders`, `ngWords`, `catalogNgWords`)、監視ワード (`watchWords`) を Flow で公開。
  - `setWatchWords()` は `WatchWordsSheet` から呼ばれ、DataStore/NSUserDefaults へ即保存。カタログ更新時には登録ワードに一致したスレッドを履歴へ自動追加します。
  - `setScrollDebounceScope()` で UI 側の `CoroutineScope` を受け取り、`scrollPositionJobs` + `Mutex` でスクロール保存の重複書き込みを抑制。
  - `upsertHistoryEntry` / `setHistory` / `setBoards` すべて `Mutex` で直列化。
  - 起動時に `seedIfEmpty(mockBoardSummaries, mockThreadHistory)` を実行。
- `PlatformStateStorage`:
  - **Android**: DataStore Preferences (`preferencesDataStore`) + 例外を `StorageException` にラップ。
  - **iOS**: NSUserDefaults + `MutableStateFlow`。プライバシーフラグ Flow/更新はまだ未実装なので Android 限定機能になっています。
- プライバシーオーバーレイ: カタログ/スレ設定からトグルすると、全面に半透明の白い Canvas を描画して覗き見対策 (タップは透過)。

---

- `HttpBoardApi` (Ktor Core + OkHttp/Darwin):
  - `fetchCatalogSetup` で catset POST → `posttime/cxyl` を初期化。
  - `fetchCatalog` / `fetchThread` / `fetchThreadHead` は 20MB 制限 + Content-Length 検査 + Referer を設定。
  - `voteSaidane`, `requestDeletion`, `deleteByUser` を HTML フォームで実装。
  - `createThread` / `replyToThread` は板の `chrenc` input を `TextEncoding` expect で Shift_JIS/UTF-8 に変換し、`postingConfig` をキャッシュ、`ptua`/`hash` などのメタを付与して `submitFormWithBinaryData` で送信。レスポンスからスレIDやエラー理由を抜き出して呼び出し元へ返す。
- `BoardUrlResolver` が板 URL から slug/base/root を計算し、ID をサニタイズしてパストラバーサルを防止。
- `DefaultBoardRepository`:
  - 板ごとに cookie 初期化を 1 回だけ実行 (`Mutex` + `initializedBoards` セット)。
  - OP サムネ取得は `fetchThreadHead` + `Semaphore(OP_IMAGE_CONCURRENCY=4)` で限定。取得結果は TTL 15 分・最大 512 件の LRU キャッシュに入り、`clearOpImageCache()` で板/スレ単位または全体を消去可能。
  - `createRemoteBoardRepository()` が HttpClient + HtmlParser を生成し、`FutachaApp` で `remember` + `DisposableEffect` によって close。
- 共有パーサー (`CatalogHtmlParserCore`, `ThreadHtmlParserCore`):
  - サイズ/正規表現 ReDoS 対策 (10MB, chunk, 1,500 ループ, 5 秒タイムアウト)。
  - カタログ: `#cattable` からスレ ID/タイトル/サムネ/レス数を抽出、HTML エンティティをデコード。
  - スレ: canonical URL から threadId、投稿 table を parse → `QuoteReference`, `PosterIdLabel`, `saidane` ラベル、削除通知、サムネ/画像リンクを抽出。
  - `buildPostsByPosterId` / `buildReferencedPostsMap` が ID/引用の逆引きを作成し、UI のプレビュー機能に使われます。
- `FakeBoardRepository` + `example/catalog.txt` & `example/thread.txt` が Compose プレビュー / commonTest / オフライン動作を支えます。

---

## 🗂️ Project Layout

```
futacha/
├── app-android/
│   └── src/main/java/com/valoser/futacha/MainActivity.kt
│       ↳ Compose host, createAppStateStore(), createHttpClient(), createVersionChecker(Context, HttpClient), createFileSystem()
├── shared/
│   ├── src/commonMain/kotlin/ (44 files)
│   │   ├── model/ BoardSummary, ThreadHistoryEntry, Post, SavedThread(SaveStatus/Metadata/Progress)
│   │   ├── network/ BoardApi, HttpBoardApi, BoardUrlResolver, expect createHttpClient()
│   │   ├── parser/ HtmlParser expect + Catalog/Thread cores
│   │   ├── repo/ DefaultBoardRepository, BoardRepositoryFactory, mock/ (FakeBoardRepository, fixtures)
│   │   ├── repository/ SavedThreadRepository
│   │   ├── service/ ThreadSaveService
│   │   ├── state/ AppStateStore + expect PlatformStateStorage
│   │   ├── ui/
│   │   │   ├── FutachaApp, UpdateNotificationDialog, PermissionRequest expect
│   │   │   ├── image/ ImageLoaderProvider (LocalFutachaImageLoader)
│   │   │   └── board/ BoardManagementScreen, SaveProgressDialog, SavedThreadsScreen, PlatformVideoPlayer expect
│   │   ├── util/ FileSystem expect, ImagePicker expect, Logger expect, UrlLauncher expect, BoardConfig, TextEncoding expect
│   │   └── version/ VersionChecker interface + helper functions
│   ├── src/androidMain/kotlin/ (14 files) — DataStore storage, OkHttp client, ActivityResult pickers, VideoView player, Logger/UrlLauncher/PermissionHelper actuals
│   ├── src/iosMain/kotlin/ (14 files) — ComposeUIViewController host, NSUserDefaults storage (privacy flag TODO), Darwin client, PHPicker/AVPlayer actuals, NSLog logger, UrlLauncher
│   └── src/commonTest/kotlin/ — Catalog/Thread parser tests + BoardManagementScreenTest
├── example/ — Futaba HTML/スクリーンショットのキャプチャ
├── README.md / AGENTS.md / codex.md — ドキュメント
└── build.gradle.kts / shared/build.gradle.kts / settings.gradle.kts
```

`shared/src` 全体で 75 ファイル (commonMain 44 / androidMain 14 / iosMain 14 / commonTest 3) があり、共有率は ~94% です。

---

## 🧩 Media, Storage & Downloads
- `ThreadSaveService`
  - `MutableStateFlow<SaveProgress?>` を公開し、Compose から collect。
  - 50 投稿ごとに chunk 化してメモリ使用量を抑制、URL→ローカルパスの辞書を用意して HTML 内リンクを置換。
  - 8MB (`MAX_FILE_SIZE_BYTES`) 超過で即中断。現在は **サムネ / 本画像** のみを `images/` 配下に保存 (動画ダウンロードは未実装)。
  - `SaveStatus` は download 失敗数に応じて COMPLETED / PARTIAL / FAILED を返す。
- `SavedThreadRepository` は `saved_threads/index.json` を `Mutex` 付きで読み書きし、合計サイズや件数を即座に算出。
- `FileSystem` expect/actual:
  - Android: `Documents/futacha` 配下 (必要に応じて内部ストレージへフォールバック) に作成。結果として保存物は `Documents/futacha/saved_threads/...` に配置されます。
  - iOS: `NSDocumentDirectory` をベースに `saved_threads` 配下に保存。
- `SaveProgressDialog` は進捗バー/パーセンテージ/現在処理項目を表示し、完了時のみ「閉じる」が押せます。
- `SavedThreadsScreen` は `SavedThreadRepository` を直接操作して一覧/削除/snackbar を提供 (未配線)。
- `ImagePickerButton` expect:
  - Android: `rememberLauncherForActivityResult(ActivityResultContracts.GetContent)` + `readImageDataFromUri()`
  - iOS: PHPicker → `suspend fun pickImage()` → `rememberCoroutineScope()` で結果を deliver
- `PlatformVideoPlayer` expect: Android は Media3/ExoPlayer + `PlayerView` で WEBM/MP4 をサポートし、バッファリング/エラー状態を Compose 側へ通知。iOS は MP4 を `AVPlayerViewController`、WEBM は `WKWebView` ベースのプレーヤーで描画します。
- `rememberUrlLauncher()` は外部ブラウザで `futaba.php` / `res/{id}.htm` を開くために Catalog/Thread 設定から使用。

---

## 🔌 Versioning, Image Loading & Permissions
- `version/VersionChecker.kt`: `UpdateInfo`, `isNewerVersion`, `fetchLatestVersionFromGitHub` を提供。common コードから呼び出しやすいように `createVersionChecker(HttpClient)` expect を定義。
- `AndroidVersionChecker` (Context + HttpClient) / `IosVersionChecker` (HttpClient) が実装。Android では `createVersionChecker(context, httpClient)` を明示的に呼ぶ必要があります。
- `UpdateNotificationDialog`: シンプルな Material3 ダイアログで「OK / 後で」ボタンのみ。
- `LocalFutachaImageLoader`: `Dispatchers.IO.limitedParallelism(3)` を fetcher/decoder に使う Coil3 ImageLoader を `remember` し、`FutachaApp` でライフサイクル管理。32MB のメモリキャッシュと `futacha_image_cache` (最大 128MB、okio + DiskCache) を使ってカタログのサムネを安定化させる。
- `PermissionRequest` expect:
  - Android: ActivityResult で `READ/WRITE_EXTERNAL_STORAGE` を (13 未満のみ) まとめてリクエストする `PermissionHelper` 実装。
  - iOS: 即座に `onPermissionResult(true)`。
- `PlatformBackHandler`: Android では Compose `BackHandler`、iOS では no-op で Compose 側の onBack ロジックだけを実行。

---

## 🔄 Build & Run

```bash
# Android デバッグビルド
./gradlew :app-android:assembleDebug

# 共有モジュールのテスト (commonTest)
./gradlew :shared:check

# iOS フレームワーク (macOS + Xcode)
./gradlew :shared:linkDebugFrameworkIosArm64
```

モック板 (`example.com`) は常に `FakeBoardRepository` が応答するため、テスト用に安全です。本物の板 URL を追加すると `DefaultBoardRepository` + `HttpBoardApi` が使用されます。

---

## 🧪 Testing

| Test | 内容 |
|------|------|
| `CatalogHtmlParserCoreTest` | カタログ HTML から ID/タイトル/サムネ/レス数を抽出 |
| `ThreadHtmlParserCoreTest` | スレ HTML の正規化、引用解析、OP 画像抽出、削除通知など |
| `BoardManagementScreenTest` | Compose ツリーの smoke test (モックリポジトリ) |

`./gradlew :shared:check` で実行できます。現状は parser + 1 画面のみで、ネットワーク/保存系のテストは未整備です。

---

## ⚠️ Known gaps / next steps

1. **SavedThreadsScreen** は UI こそ完成済みですが、どこからも遷移できません。ナビゲーションルート/ボタンの追加が必要です。
2. **スレ保存 (iOS)**: `MainViewController` が `createFileSystem()` を渡していないため、保存ボタンは Android 専用です。NSUserDefaults 側の `privacyFilterEnabled` Flow も未実装で、プライバシーフラグは Android 限定。
3. **動画ダウンロード**: `ThreadSaveService` は THUMBNAIL/FULL_IMAGE しか処理しておらず、`SUPPORTED_VIDEO_EXTENSIONS` は未使用です。
4. **ピン留め / 並び替え**: BoardManagementScreen はピン状態を表示するだけで、トグルやドラッグ＆ドロップ並び替えは未対応 (上下ボタンのみ)。
5. **カタログ表示モード**: グリッド固定でリスト/列数変更 UI はありません。
6. **テストカバレッジ**: ネットワーク/Repository/ThreadSaveService/Compose UI の多くが未テスト。FakeBoardRepository/MockWeb 層の拡充が必要です。
7. **iOS HttpClient/ファイル解放**: `MainViewController` は HttpClient を close せず、プラットフォーム側でのリソース管理が未整備です。

---

## 📥 ダウンロード

- **Google Play**: 準備中
- **App Store**: 準備中
- **GitHub Releases**: 最新版は [Releases](https://github.com/inqueuet/futacha/releases/latest) を参照

---

## 🤝 コントリビューション

1. リポジトリを Fork
2. ブランチ作成 `git checkout -b feature/awesome`
3. 変更をコミット `git commit -m "Add awesome feature"`
4. Push `git push origin feature/awesome`
5. Pull Request を作成

---

## 📄 ライセンス

MIT — [LICENSE](LICENSE)

---

## 🔗 リンク

- [GitHub Repository](https://github.com/inqueuet/futacha)
- [Issue Tracker](https://github.com/inqueuet/futacha/issues)
- [Releases](https://github.com/inqueuet/futacha/releases)
- [詳細設計 (codex.md)](codex.md)

---

## 📮 お問い合わせ

質問や提案は [GitHub Issues](https://github.com/inqueuet/futacha/issues) までどうぞ。

---

**Made with ❤️ using Kotlin Multiplatform & Compose Multiplatform**
