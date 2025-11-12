# Codex – Futaba Channel Browser (KMP + Compose Multiplatform)

> Futaba 専用ブラウザを Kotlin Multiplatform で実装。UI・ロジックは `shared/` に集約し、Android/iOS のホスト層は Compose を表示するだけです。

---

## 0. Entry Points & Runtime

- `app-android/src/main/java/com/valoser/futacha/MainActivity.kt`
  - `createAppStateStore(applicationContext)` / `createHttpClient()` / `createVersionChecker(context, httpClient)` / `createFileSystem(applicationContext)` を `remember`。
  - `DisposableEffect` で HttpClient を close。`FutachaApp` に stateStore / httpClient / fileSystem / versionChecker を注入。
- `shared/src/iosMain/kotlin/MainViewController.kt`
  - `ComposeUIViewController { FutachaApp(...) }` を返すシンプルなホスト。
  - 現状は `createFileSystem()` を渡していないため、Thread 保存は snackbar でブロックされます (TODO)。
- `shared/src/commonMain/kotlin/ui/FutachaApp.kt`
  - `mockBoardSummaries` / `mockThreadHistory` で初回起動をシード。
  - `createRemoteBoardRepository()` を `remember` し、`DisposableEffect` で `close()`。
  - `setScrollDebounceScope()`、version チェック (`VersionChecker`)、`LocalFutachaImageLoader` のライフサイクル管理、履歴更新 (`refreshHistoryEntries`) を担当。
  - `selectedBoardId` / `selectedThreadId` で Board → Catalog → Thread を切り替え。`BoardSummary.isMockBoard()` によりモック/リモートを選択。

---

## 1. UI Flow (commonMain / `ui/board/BoardManagementScreen.kt`)

### 1.1 BoardManagementScreen

- 1 つのファイルに Board/Catalog/Thread UI をまとめた巨大 Compose ツリー (~4.7k 行)。
- TopAppBar: 左メニューで履歴ドロワーを開くか、削除/並び替えモード時は戻るボタンに切り替え。
- メニュー (`BoardManagementMenuAction`)：
  - **ADD**: `AddBoardDialog` (URL スキーマ確認、重複チェック、`slugify` で ID 生成)。
  - **DELETE**: カード右端に削除ボタンを出し、`DeleteBoardDialog` で確認 → `onBoardDeleted` → `AppStateStore.setBoards()`。
  - **REORDER**: `BoardSummaryCardWithReorder` が上下ボタンを表示 (`onBoardsReordered` コールバック)。ドラッグ&ドロップは未実装。
  - HELP/SETTINGS は snackbar でモック通知。
- 履歴ドロワー (`HistoryDrawerContent`):
  - `SwipeToDismissBox` で履歴をスワイプ削除。下部バーから board 画面へ戻る / 更新 / 一括削除 / 設定 (モック)。
  - このコンポーネントは Catalog / Thread でも再利用されます。

### 1.2 CatalogScreen

- `LazyVerticalGrid(GridCells.Fixed(5))` のみ (リスト切替なし)。`CatalogMode` に応じて `applyLocalSort()` を適用して表示順を調整。
- `PullToRefreshBox` とボトムセンチネル (vertical drag で更新) を併用。
- TopBar:
  - デフォルトは Board 名 + モードを表示。
  - 検索モード (`isSearchActive`) では `CatalogSearchTextField` に入れ替わり、back ハンドラで検索解除 → ドロワー → onBack の順に閉じる。
- Bottom Navigation (`CatalogNavDestination`):
  - **CreateThread**: `CreateThreadDialog` → `BoardRepository.createThread()` → snackbar + `performRefresh()`。
  - **Refresh**: `performRefresh()` で BoardRepository から再取得。
  - **Mode**: `AlertDialog` でモード一覧を選択し、`catalogMode` を更新。
  - **Settings**: `CatalogSettingsSheet` が 6 メニュー (監視ワード、NG管理(〇)、外部アプリ(〇)、表示の切り替え(〇)、一番上に行く(〇)、プライバシー(〇)) を提供。〇は実装済みで、NG管理は `NgManagementSheet` (ワードのみ) を開き、外部アプリは `mode=cat` URL を `rememberUrlLauncher` で開く。表示切替は `DisplayStyleDialog`、一番上は `scrollCatalogToTop()`、プライバシーは `AppStateStore.setPrivacyFilterEnabled()` を呼ぶ。監視ワードは `WatchWordsSheet` で編集可能で、登録したワードと一致するタイトルを持つスレッドはカタログ更新時に履歴へ自動追加されます。記号凡例: 〇=対応、△=基本実装、無印=未実装。
- `stateStore?.isPrivacyFilterEnabled` を collect し、true のときは半透明の Canvas オーバーレイを描画。
- `FakeBoardRepository()` をデフォルトにし、`board.url` が `example.com` ならモックのまま、そうでなければ `FutachaApp` から渡された `BoardRepository` (リモート) を利用。

### 1.3 ThreadScreen

- リアル板 (`!isMockBoard()`) でのみ `BoardRepository` を利用。モック板では `FakeBoardRepository` が描画を担当。
- Histories:
  - `LaunchedEffect(activeThreadId, currentBoard.id)` で `ThreadHistoryEntry` を先頭に追加 (5 秒以内の重複更新は skip)。
  - `snapshotFlow { LazyListState.firstVisibleItemIndex/Offset }` + `debounce(500)` でスクロール位置を永続化。
- `ThreadTopBar`:
  - Default: Thread title + Board name + reply count / status label。
  - Search mode: `TextField` + ヒット件数表示 + `KeyboardArrowUp/Down` ナビ。`currentSearchResultIndex` を `moveToNext/PreviousSearchMatch` で更新。
  - Actions: Search icon → `isSearchActive = true`、MoreVert → 履歴ドロワー。
- `ThreadActionBarItem` (Row of IconButtons):
  1. Reply → `ThreadReplyDialog` (`ThreadFormDialog` ベース、メールプリセット/削除キー/画像or動画選択)。
  2. ScrollToTop / ScrollToBottom → `animateScrollToItem`.
  3. Refresh → `BoardRepository.getThread()` でページ更新・レス数更新。
  4. Gallery → `ThreadImageGallery` (ModalBottomSheet でサムネ一覧)。
  5. Save → `ThreadSaveService` (httpClient & fileSystem がある場合のみ)。結果を `SavedThreadRepository.addThreadToIndex()` に保存し snackbar を表示。iOS は `null` なので snackbar で機能不可を通知。
  6. Settings → `ThreadSettingsSheet` で NG管理(〇) / 外部アプリ(〇) / 読み上げ(△) / プライバシー(〇) を表示。〇は即動作し、NG管理は `NgManagementSheet` でヘッダー/ワードを編集、外部アプリは `res/{threadId}.htm` を開く、プライバシーは `AppStateStore` のフラグをトグル。△の読み上げは `TextSpeaker` で投稿本文を順次再生し、再タップで停止できる基本実装。記号凡例は Catalog と同じです。
- 投稿カード (`ThreadPostCard`):
  - ID ラベル: `buildPosterIdLabels()` が ID ごとの通番と total count を付与 (複数出現なら強調)。
  - 引用 (`QuoteReference`) をタップすると `QuotePreviewDialog` に target posts をまとめて表示。
  - 長押しで `ThreadPostActionSheet` → そうだね / DEL 依頼 / 本人削除 (ダイアログ)。
  - サムネ/画像/動画リンクをタップすると `handleMediaClick` → `ImagePreviewDialog` (ピンチズーム + swipe dismiss) or `VideoPreviewDialog` (PlatformVideoPlayer)。
- `ThreadContent` には `PullToRefreshBox` / カスタムスクロールバー / 500ms ごとの検索ハイライト更新 / Drawer BackHandler を実装。
- `SaveProgressDialog` は `saveProgress` State を監視し、FINALIZING 完了後に閉じられる。

### 1.4 SavedThreadsScreen

- `shared/src/commonMain/kotlin/ui/board/SavedThreadsScreen.kt` に Compose 実装あり。
- `SavedThreadRepository` を受け取り、`getAllThreads()` / `getTotalSize()` を `LaunchedEffect` でロード。
- `AlertDialog` で削除確認、`SnackbarHostState` で成功/失敗を表示。
- まだ `FutachaApp` から遷移する導線が無いため未使用。ナビゲーション機能を追加する必要あり。

---

## 2. State & Persistence

- `AppStateStore` (`shared/src/commonMain/kotlin/state/AppStateStore.kt`)
  - `boards` / `history` / `isPrivacyFilterEnabled` を Flow で expose。
  - JSON シリアライゼーション (`ListSerializer`) + `Mutex` で書き込みを直列化。
  - `setScrollDebounceScope()` + `scrollPositionJobs` でスレスクロール保存のスパムを防止。500ms 待ってから `updateHistoryScrollPositionImmediate()` を実行。
  - `upsertHistoryEntry()` で差分更新し順序を維持。履歴書き込みは常に `persistHistory()` 経由。
  - `seedIfEmpty()` がモック板/履歴を DataStore/NSUserDefaults に書き込み、初回起動でも UI が埋まるようにしている。
- `PlatformStateStorage` expect/actual
  - Android: `preferencesDataStore`, `StorageException`, `seedIfEmpty` で missing key のみ初期投入。
  - iOS: `NSUserDefaults` + `MutableStateFlow`。`privacyFilterEnabled` / `updatePrivacyFilterEnabled` が未実装のため、iOS では `isPrivacyFilterEnabled` Flow が空 (TODO)。
- `ThreadHistoryEntry`:
  - `lastVisitedEpochMillis`, `lastReadItemIndex`, `lastReadItemOffset` を保持し、Thread 画面遷移時に scroll state を復元。
  - `BoardHistoryDrawer` のカードは ID/タイトル/板名/サムネ/レス数/lastVisited を表示。

---

## 3. Networking & Data

- `shared/src/commonMain/kotlin/network/HttpBoardApi.kt`
  - `fetchCatalogSetup()` で `mode=catset` フォームを POST。
  - `fetchCatalog()` / `fetchThread()` / `fetchThreadHead()` は 20MB を上限に二重チェック (headers + body length)。
  - `voteSaidane()` は `sd.php?{boardSlug}.{postId}` を GET → 応答 "1" を検証。
  - `requestDeletion()` は `del.php` にフォーム送信 (`reasonCode` 固定 110)。
  - `deleteByUser()` / `createThread()` / `replyToThread()` は `futaba.php?guid=on` にフォーム送信。Shift_JIS ではなく UTF-8 のままですが、現状は HTML 応答解析を行っていません。
- `BoardUrlResolver`
  - `resolveCatalogUrl(boardUrl, mode)` で `mode=cat&sort=x` を組み立て。
  - `resolveThreadUrl` / `resolveBoardBaseUrl` / `resolveSiteRoot` により referer を正しく付与。
  - `sanitizePostId()` が数値以外を弾く。
- `DefaultBoardRepository`
  - `ensureCookiesInitialized()` を board ごとに 1 回だけ実行 (`Mutex` + `initializedBoards` set)。
  - `fetchOpImageUrl()` は `HttpBoardApi.fetchThreadHead()` で 65 行だけ取得し、`parser.extractOpImageUrl()` に渡す。並列数は `Semaphore(4)`。
  - リモート操作 (`voteSaidane`, `requestDeletion`, `deleteByUser`, `replyToThread`, `createThread`) は cookie 初期化後に API を呼ぶだけの薄いラッパー。
- パーサー (`shared/src/commonMain/kotlin/parser`)
  - `HtmlParser` expect/actual の実装は Android/iOS ともに `CatalogHtmlParserCore` / `ThreadHtmlParserCore` を呼ぶだけ (Jsoup 依存なし)。
  - `CatalogHtmlParserCore`: table body を chunk ごとに処理し、1000 アイテム・10MB を上限に安全性を確保。
  - `ThreadHtmlParserCore`: 5 秒タイムアウト / 1500 iterations / 2050 posts 上限 / invalid range や regex 例外を検出してトランケーション理由を返す。
  - 引用 (`>>123`), `ID:xxxx`, saidane ラベル, 削除通知 (`<span id="ddel">`), 画像/動画リンク (`/src/`, `/thumb/`) を抽出し、`ThreadPage` に `isTruncated` / `truncationReason` をセット。
- Mock
  - `repo/mock/MockBoardData.kt` が `example/catalog.txt` / `example/thread.txt` を parse して `catalogItems` / `threadPages` を構築。
  - `FakeBoardRepository` はそれらをそのまま返すシンプルな実装で、Compose Previews と commonTest の基盤になっています。

---

## 4. Media, Storage & Platform Utilities

- `ThreadSaveService`
  - `saveThread()` → PREPARING (dir 作成) → DOWNLOADING (chunked media download) → CONVERTING (HTML 書き換え) → FINALIZING (metadata + HTML)。
  - `MediaItem` は `THUMBNAIL` / `FULL_IMAGE` のみ。動画ダウンロードは今後の課題。
  - Ktor GET → `Content-Length` で 8MB を超えないかチェックし、`FileSystem.writeBytes()` で保存。
  - `convertHtmlPaths()` が `<img src>` / `<a href>` の URL をローカル相対パスに差し替える。
  - `SavedThread` (thumbnailPath, imageCount, videoCount, totalSize, SaveStatus) と `SavedThreadMetadata` (posts, local paths, version) を生成。
- `SavedThreadRepository`
  - `indexMutex` で `saved_threads/index.json` を守り、`addThreadToIndex()` / `removeThreadFromIndex()` / `updateThread()` でスレ一覧と `totalSize` / `lastUpdated` を集計。
  - `deleteThread()` は `FileSystem.deleteRecursively("saved_threads/$threadId")` → index 更新。
- `FileSystem` expect
  - Android (`util/FileSystem.android.kt`): `Documents/futacha` をベースにし、必要なら内部ストレージにフォールバック。`createDirectory` / `writeBytes` / `writeString` は `Dispatchers.IO` で実行。
  - iOS (`util/FileSystem.ios.kt`): `NSFileManager` + `NSData` で IO。`resolveAbsolutePath` は `NSDocumentDirectory` 配下。
- `SavedThreadsScreen`
  - `LaunchedEffect(Unit)` で一覧/サイズをロード。`AlertDialog` で削除確認。現状は未配線。
- Expect/actual components
  - `ImagePickerButton` & `rememberImagePickerLauncher`: Android は ActivityResultContracts.GetContent、iOS は PHPicker + suspend 呼び出し。
  - `PlatformVideoPlayer`: VideoView + MediaController / AVPlayerViewController。
  - `UrlLauncher`: Android Intent / iOS UIApplication。
  - `PermissionRequest`: Android ではストレージ権限 (API < 33) を要求、iOS は即 true。
  - `PlatformBackHandler`: Android Compose BackHandler / iOS no-op。
  - `LocalFutachaImageLoader`: Coil3 ImageLoader を再利用し、`Dispatchers.IO.limitedParallelism(3)` でフェッチ/デコードを制限。

---

## 5. Versioning & Misc Utilities

- `shared/src/commonMain/kotlin/version/VersionChecker.kt`
  - `interface VersionChecker`, `data class UpdateInfo`, `fun isNewerVersion()`, `suspend fun fetchLatestVersionFromGitHub()`, `expect fun createVersionChecker(HttpClient)`。
  - `AndroidVersionChecker` (Context + HttpClient) → PackageManager の versionName と Releases API を比較。`createVersionChecker(context, httpClient)` を提供。
  - `IosVersionChecker` (HttpClient) → NSBundle の `CFBundleShortVersionString` を取得し、GitHub Releases と比較。
- `UpdateNotificationDialog`: `AlertDialog` でメッセージを表示。OK/後で両方 `onDismiss`。
- `Logger` expect/actual: Android = `android.util.Log`, iOS = `NSLog`。
- `PermissionHelper` (Android): Android 13 以降は Storage 権限不要と判定し、12 以下は READ/WRITE をリクエスト。

---

## 6. Project Layout Snapshot

```
shared/
├── src/commonMain/kotlin/
│   ├── model/ BoardSummary, ThreadHistoryEntry, CatalogItem, Post, SavedThread(+SaveStatus/Metadata)
│   ├── network/ BoardApi, HttpBoardApi, BoardUrlResolver, HttpClientFactory expect
│   ├── parser/ HtmlParser expect, CatalogHtmlParserCore, ThreadHtmlParserCore, ParserException
│   ├── repo/ BoardRepository, DefaultBoardRepository, BoardRepositoryFactory, mock/ (FakeBoardRepository, MockBoardData, fixtures)
│   ├── repository/ SavedThreadRepository
│   ├── service/ ThreadSaveService
│   ├── state/ AppStateStore + PlatformStateStorage expect
│   ├── ui/
│   │   ├── FutachaApp, PermissionRequest expect, UpdateNotificationDialog
│   │   ├── image/ ImageLoaderProvider (LocalFutachaImageLoader)
│   │   └── board/ BoardManagementScreen, SaveProgressDialog, SavedThreadsScreen, PlatformVideoPlayer expect
│   ├── util/ FileSystem expect, ImagePicker expect, Logger expect, UrlLauncher expect, BoardConfig
│   └── version/ VersionChecker
├── src/androidMain/kotlin/ — DataStore storage, OkHttp client, ActivityResult pickers, VideoView, UrlLauncher, Logger, PermissionHelper
├── src/iosMain/kotlin/ — Compose host, NSUserDefaults storage (privacy flag TODO), Darwin client, PHPicker, AVPlayer, UrlLauncher, Logger
├── src/commonTest/kotlin/ — Catalog/Thread parser tests, BoardManagementScreenTest
└── example/ — Futaba HTML / スクリーンショット
```

File counts: commonMain 44 / androidMain 14 / iosMain 14 / commonTest 3 (計 75)。コード共有率 ~94%。

---

## 7. Testing & Commands

- 自動テスト
  - `CatalogHtmlParserCoreTest` / `ThreadHtmlParserCoreTest` (commonTest, JVM)
  - `ui/board/BoardManagementScreenTest`
- コマンド

```bash
# 共通テスト
./gradlew :shared:check

# Android Debug APK
./gradlew :app-android:assembleDebug

# iOS Framework (macOS + Xcode)
./gradlew :shared:linkDebugFrameworkIosArm64
```

Compose Preview / 手動動作では `FakeBoardRepository` と `example/` のフィクスチャが利用されます。

---

## 8. Known gaps / TODO

1. **SavedThreadsScreen 導線**: UI は存在するが遷移が無い。Board/Catalog/Thread のどこかに入口を追加する必要があります。
2. **Thread 保存 (iOS)**: MainViewController が `createFileSystem()` を注入しておらず、`ThreadActionBarItem.Save` が無効。NSUserDefaults 側の `privacyFilterEnabled` Flow も未実装。
3. **動画保存**: `ThreadSaveService` は画像のみ処理。`SUPPORTED_VIDEO_EXTENSIONS` や `videos/` ディレクトリは未使用。
4. **Board ピン & 並び替え UX**: ピン状態の編集 UI・ドラッグ並び替えが無く、上下ボタンのみ。
5. **Catalog レイアウト切替**: グリッド固定。リスト表示や列数変更、モードごとのフィルタ UI などは TODO。
6. **エラーハンドリング**: `createThread` / `reply` / `del` / `deleteByUser` は HTML 応答の詳細を解析しておらず、snackbar で汎用メッセージを出すだけ。
7. **テスト不足**: Repository/Service 層や `ThreadSaveService` のユニットテストが無い。MockEngine/ファイルシステムのフェイクが必要。
8. **iOS リソース管理**: `MainViewController` で作成した HttpClient を close していない。将来的に `remember` + `DisposableEffect` 相当の仕組みを導入する必要あり。
9. **プラットフォーム設定**: プライバシーフラグ (`AppStateStore.isPrivacyFilterEnabled`) は Android でしか永続化されないため、iOS 実装を追加する。

---

このドキュメントは `README.md` / `codex.md` と合わせて参照することで、モジュール構成・UI フロー・ネットワーク層・保存処理の全体像を把握できるようになっています。追加の質問があれば Issues でどうぞ。***
