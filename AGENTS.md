# Codex – Futaba Channel Browser (KMP + Compose Multiplatform)

> Futaba 専用ブラウザを Kotlin Multiplatform で実装。UI・ロジックは `shared/` に集約し、Android/iOS のホスト層は Compose を表示するだけです。

---

## 0. Entry Points & Runtime

- `app-android/src/main/java/com/valoser/futacha/MainActivity.kt`
  - `createAppStateStore(applicationContext)` / `createHttpClient()` / `createVersionChecker(context, httpClient)` / `createFileSystem(applicationContext)` を `remember`。
  - `DisposableEffect` で HttpClient を close。`FutachaApp` に stateStore / httpClient / fileSystem / versionChecker を注入。
- `shared/src/iosMain/kotlin/MainViewController.kt`
  - `stateStore` / `httpClient` / `versionChecker` / `fileSystem` を `remember` し、`DisposableEffect` で HttpClient を close。`ComposeUIViewController { FutachaApp(...) }` を返すシンプルなホストとして `fileSystem` まで渡され、Thread 保存やスナックバーの挙動も Android と同じように動作します。
- `shared/src/commonMain/kotlin/ui/FutachaApp.kt`
  - `mockBoardSummaries` / `mockThreadHistory` で初回起動をシード。
  - `createRemoteBoardRepository()` を `remember` し、`DisposableEffect` で `close()`。
  - `setScrollDebounceScope()`、version チェック (`VersionChecker`)、`LocalFutachaImageLoader` のライフサイクル管理、履歴更新 (`refreshHistoryEntries`) を担当。
  - `selectedBoardId` / `selectedThreadId` で Board → Catalog → Thread を切り替え。`BoardSummary.isMockBoard()` によりモック/リモートを選択。
  - `fileSystem` から `AUTO_SAVE_DIRECTORY` を使った `SavedThreadRepository` を `remember` し、自動セーブ用インデックスを管理。履歴エントリ削除・クリア時には関連する auto-save ディレクトリを削除し、`ThreadScreen` にオフラインフォールバックを提供する。
  - `VersionChecker` から取得した `appVersion` を `remember` し、それを global settings と各画面に渡すことでバージョン情報の表示や `GlobalSettingsScreen` の動作に利用。

---

## 1. UI Flow (commonMain / `ui/board/BoardManagementScreen.kt`)

### 1.1 BoardManagementScreen

- 1 つのファイルに Board/Catalog/Thread UI をまとめた巨大 Compose ツリー (~4.7k 行)。
- TopAppBar: 左メニューで履歴ドロワーを開くか、削除/並び替えモード時は戻るボタンに切り替え。
- メニュー (`BoardManagementMenuAction`)：
  - **ADD**: `AddBoardDialog` (URL スキーマ確認、重複チェック、`slugify` で ID 生成)。
  - **DELETE**: カード右端に削除ボタンを出し、`DeleteBoardDialog` で確認 → `onBoardDeleted` → `AppStateStore.setBoards()`。
  - **REORDER**: `BoardSummaryCardWithReorder` が上下ボタンを表示 (`onBoardsReordered` コールバック)。ドラッグ&ドロップは未実装。
  - **SETTINGS**: `GlobalSettingsScreen` を表示し、X/Email/GitHub へのリンクや `appVersion` 行を含む共通設定画面を開く。
- 履歴ドロワー (`HistoryDrawerContent`):
  - `SwipeToDismissBox` で履歴をスワイプ削除。下部バーから board 画面へ戻る / 更新 / 一括削除 / 設定 (`GlobalSettingsScreen`)。
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
- カタログカードの画像表示は OP画像(URL)を取得後、サムネイルURLからフルサイズURLへ切り替える。Coil が自動的にキャッシュを管理するため、フルサイズ画像がキャッシュに存在すればすぐに表示され、なければダウンロードして表示する。動画/サムネなしといった本来の空画像ケースでは既存のサムネイルを維持しつつブランク状態に陥らない。
- `FakeBoardRepository()` をデフォルトにし、`board.url` が `example.com` ならモックのまま、そうでなければ `FutachaApp` から渡された `BoardRepository` (リモート) を利用。
- `CatalogTopBar` の `CatalogMenuAction.Settings` で `GlobalSettingsScreen` を開き、X/Email/GitHub への導線や `appVersion` 行を再利用した共通設定画面を表示している。

### 1.3 ThreadScreen

- リアル板 (`!isMockBoard()`) でのみ `BoardRepository` を利用。モック板では `FakeBoardRepository` が描画を担当。
- Histories:
  - `LaunchedEffect(activeThreadId, currentBoard.id)` で `ThreadHistoryEntry` を先頭に追加 (5 秒以内の重複更新は skip)。
  - `snapshotFlow { LazyListState.firstVisibleItemIndex/Offset }` + `debounce(500)` でスクロール位置を永続化。
- `ThreadTopBar`:
  - Default: Thread title + Board name + reply count / status label。
  - Search mode: `TextField` + ヒット件数表示 + `KeyboardArrowUp/Down` ナビ。`currentSearchResultIndex` を `moveToNext/PreviousSearchMatch` で更新。
  - Actions: Search icon → `isSearchActive = true`、History icon → ドロワーを開く、MoreVert → `ThreadMenuAction.Settings` を開いて `GlobalSettingsScreen` を表示。
- `ThreadActionBarItem` (Row of IconButtons):
  1. Reply → `ThreadReplyDialog` (`ThreadFormDialog` ベース、メールプリセット/削除キー/画像or動画選択)。
  2. ScrollToTop / ScrollToBottom → `animateScrollToItem`.
  3. Refresh → `BoardRepository.getThread()` でページ更新・レス数更新。
  4. Gallery → `ThreadImageGallery` (ModalBottomSheet でサムネ一覧)。
  5. Save → `ThreadSaveService` (httpClient & fileSystem がある場合のみ)。結果を `SavedThreadRepository.addThreadToIndex()` に保存し snackbar を表示。Android / iOS ともに依存関係が注入されるようになったので保存ボタンが有効です。
  6. Filter → `ThreadFilterSheet` でスレッド内のレス絞り込み・ソート機能を提供。
  7. Settings → `ThreadSettingsSheet` で NG管理(〇) / 外部アプリ(〇) / 読み上げ(△) / プライバシー(〇) を表示。〇は即動作し、NG管理は `NgManagementSheet` でヘッダー/ワードを編集、外部アプリは `res/{threadId}.htm` を開く、プライバシーは `AppStateStore` のフラグをトグル。△の読み上げは `TextSpeaker` で投稿本文を順次再生し、再タップで停止できる基本実装。記号凡例は Catalog と同じです。
- `ThreadActionBar` は `.navigationBarsPadding()` を適用し、Androidのシステムナビゲーションバー（3点メニュー）の上に配置されるため、ボトムバーとシステムUIが重ならず正常に操作できます。
- 自動セーブ / オフラインフォールバック: `autoSavedThreadRepository` に `AUTO_SAVE_DIRECTORY` を使って `ThreadSaveService(baseDirectory = AUTO_SAVE_DIRECTORY)` で 60 秒ごとにスレッドを保存。`loadThreadWithOfflineFallback` は保存済みメタデータを `SavedThreadMetadata.toThreadPage()` で `ThreadPage` に変換して表示し、ネットワーク不通時には snackbar でローカルコピーを通知 (`isShowingOfflineCopy` フラグ)。履歴を削除またはクリアすると `FutachaApp` が該当する auto-save ディレクトリを `SavedThreadRepository` 経由で削除するので、古いローカルコピーも掃除される。
- 投稿カード (`ThreadPostCard`):
  - ID ラベル: `buildPosterIdLabels()` が ID ごとの通番と total count を付与 (複数出現なら強調)。
  - 引用 (`QuoteReference`) をタップすると `QuotePreviewDialog` に target posts をまとめて表示。
  - 長押しで `ThreadPostActionSheet` → そうだね / DEL 依頼 / 本人削除 (ダイアログ)。
  - サムネ/画像/動画リンクをタップすると `handleMediaClick` → `ImagePreviewDialog` (Coil3 の状態を監視し、読み込み中はスピナー、失敗時は外部ブラウザオプション付き) or `VideoPreviewDialog`。後者はバッファリング/エラーを UI に反映し、Android は ExoPlayer で WEBM/MP4 を再生、iOS は MP4 を AVPlayer、WEBM は WKWebView 経由で描画します。
- `ThreadContent` には `PullToRefreshBox` / カスタムスクロールバー / 500ms ごとの検索ハイライト更新 / Drawer BackHandler を実装。
- `SaveProgressDialog` は `saveProgress` State を監視し、FINALIZING 完了後に閉じられる。

### 1.4 SavedThreadsScreen

- `shared/src/commonMain/kotlin/ui/board/SavedThreadsScreen.kt` に Compose 実装あり。
- `SavedThreadRepository` を受け取り、`getAllThreads()` / `getTotalSize()` を `LaunchedEffect` でロード。
- `AlertDialog` で削除確認、`SnackbarHostState` で成功/失敗を表示。
- まだ `FutachaApp` から遷移する導線が無いため未使用。ナビゲーション機能を追加する必要あり。

### 1.5 GlobalSettingsScreen

- `GlobalSettingsScreen` は Board のメニュー `SETTINGS`、Catalog の `CatalogMenuAction.Settings`、Thread の `ThreadMenuAction.Settings`、履歴ドロワーの設定ボタンから開く共通設定ダイアログ。
- `GlobalSettingsEntry` は Email/X/GitHub へのリンクを提供し、それぞれ `rememberUrlLauncher` で外部アプリを起動する。`GlobalSettingsScreen` は `appVersion` 引数も受け取り、一覧の最後に現在のバージョンを表示する。
- `FutachaApp` は `VersionChecker` からバージョン名 (`appVersion`) を `remember` し、全画面に注入しているので、最新リリース通知 (`UpdateNotificationDialog`) と合わせて UI 側でもバージョン参照が一貫している。

---

## 2. State & Persistence

- `AppStateStore` (`shared/src/commonMain/kotlin/state/AppStateStore.kt`)
  - `boards` / `history` / `isPrivacyFilterEnabled` に加えて `catalogDisplayStyle` と `ngHeaders` / `ngWords` / `catalogNgWords` / `watchWords` を Flow で expose。
  - 監視ワード (`watchWords`) は `WatchWordsSheet` から編集され、`AppStateStore.setWatchWords()` で DataStore / NSUserDefaults に即保存。カタログ更新時はこれらのワードに一致したスレッドを履歴へ自動追加します。
  - JSON シリアライゼーション (`ListSerializer`) + `Mutex` で書き込みを直列化。
  - `setScrollDebounceScope()` + `scrollPositionJobs` でスレスクロール保存のスパムを防止。500ms 待ってから `updateHistoryScrollPositionImmediate()` を実行。
  - `upsertHistoryEntry()` で差分更新し順序を維持。履歴書き込みは常に `persistHistory()` 経由。
  - `seedIfEmpty()` がモック板/履歴を DataStore/NSUserDefaults に書き込み、初回起動でも UI が埋まるようにしている。
- `PlatformStateStorage` expect/actual
  - Android: `preferencesDataStore`, `StorageException`, `seedIfEmpty` で missing key のみ初期投入。
  - iOS: `NSUserDefaults` + `MutableStateFlow`。`privacyFilterEnabled` Flow と更新処理が実装済みで、Android と同等にプライバシーフィルタの状態を永続化できます。
- `ThreadHistoryEntry`:
  - `lastVisitedEpochMillis`, `lastReadItemIndex`, `lastReadItemOffset` を保持し、Thread 画面遷移時に scroll state を復元。
  - `BoardHistoryDrawer` のカードは ID/タイトル/板名/サムネ/レス数/lastVisited を表示。
- `autoSavedThreadRepository` は `SavedThreadRepository(baseDirectory = AUTO_SAVE_DIRECTORY)` で `FutachaApp` により `remember` され、履歴エントリを削除/クリアする際に関連する auto-save ディレクトリも `deleteThread`/`deleteAllThreads` で掃除される。`SavedThreadMetadata.toThreadPage()` はオフラインフォールバックと `ThreadScreen` での復元に使われる。

---

## 3. Networking & Data

- `shared/src/commonMain/kotlin/network/HttpBoardApi.kt`
  - `fetchCatalogSetup()` で `mode=catset` フォームを POST。
  - `fetchCatalog()` / `fetchThread()` / `fetchThreadHead()` は 20MB を上限に二重チェック (headers + body length)。
  - `voteSaidane()` は `sd.php?{boardSlug}.{postId}` を GET → 応答 "1" を検証。
  - `requestDeletion()` は `del.php` にフォーム送信 (`reasonCode` 固定 110)。
  - `deleteByUser()` は `futaba.php?guid=on` にフォーム送信。
  - `createThread()` / `replyToThread()` は `futaba.htm` から `chrenc` 入力を抜き出して `TextEncoding` expect で Shift_JIS/UTF-8 を切り替えつつ `postingConfig` をキャッシュ。`ptua`/`hash`/`pt` 系の form field を補完し、`submitFormWithBinaryData` で送信。レスポンス内の `res/1234` / JSON / 失敗キーワードを拾ってスレIDもしくはエラー詳細を返すことで応答解析が強化された。
- `BoardUrlResolver`
  - `resolveCatalogUrl(boardUrl, mode)` で `mode=cat&sort=x` を組み立て。
  - `resolveThreadUrl` / `resolveBoardBaseUrl` / `resolveSiteRoot` により referer を正しく付与。
  - `sanitizePostId()` が数値以外を弾く。
- `DefaultBoardRepository`
  - `ensureCookiesInitialized()` を board ごとに 1 回だけ実行 (`Mutex` + `initializedBoards` set)。
  - `fetchOpImageUrl()` は `HttpBoardApi.fetchThreadHead()` で 65 行だけ取得し、`parser.extractOpImageUrl()` に渡す。並列数は `Semaphore(4)`、OP 画像 URL は TTL 15 分・最大 512 件の LRU キャッシュに入り、`clearOpImageCache(board?, threadId?)` で個別/全体のキャッシュを削除できる。
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
    - `MediaItem` は `THUMBNAIL` / `FULL_IMAGE` に加え `SUPPORTED_VIDEO_EXTENSIONS` を検知して新たに `FileType.VIDEO` を扱い、`videos/` 下に動画を保存。`SavedPost` に `originalVideoUrl`/`localVideoPath` を記録し、HTML では `<video>`/`<source>` `src` をローカルパスへ差し替えて `poster` にセーブ済みサムネも当てる。
    - Ktor GET → `Content-Length` で 8MB を超えないかチェックし、`FileSystem.writeBytes()` で保存。
    - `convertHtmlPaths()` が `<img src>` / `<a href>` / `<video src>` / `<source src>` の URL をローカル相対パスに差し替える。
    - `SavedThread` (thumbnailPath, imageCount, videoCount, totalSize, SaveStatus) と `SavedThreadMetadata` (posts, local paths, version) を生成。
    - `savedAt` 値とファイル命名には `Clock.System` + `kotlinx.datetime` を使い、`formatTimestamp()` でローカルタイム (yyyy/MM/dd HH:mm:ss) の文字列を metadata に出力するようになった。
    - `baseDirectory` パラメータ (デフォルト `MANUAL_SAVE_DIRECTORY`) を受け取り、必ずディレクトリを作成したうえで手動保存・自動保存の両方で同じロジックを使えるようにした。
  - `SaveDirectories.kt`
    - `MANUAL_SAVE_DIRECTORY` / `AUTO_SAVE_DIRECTORY` の定数が切り出され、手動保存と自動保存でフォルダを分離する。
  - `SavedThreadRepository`
    - `baseDirectory` で管理対象を切り替えられるようになり、`MANUAL_SAVE_DIRECTORY` は手動保存、`AUTO_SAVE_DIRECTORY` は auto-save ジョブで使われる。`indexMutex` で `index.json` を保護しつつ `add`/`remove`/`update` を提供し、`deleteThread()` は対象のフォルダを再帰削除して index を再計算する。
    - `SavedThreadMetadata.toThreadPage()` (new extension) を使って autosave ディレクトリからロードした metadata を `ThreadPage` に変換し、`ThreadScreen` のオフラインフォールバックで再利用している。
- `FileSystem` expect
  - Android (`util/FileSystem.android.kt`): `Documents/futacha` をベースにし、必要なら内部ストレージにフォールバック。`createDirectory` / `writeBytes` / `writeString` は `Dispatchers.IO` で実行。
  - iOS (`util/FileSystem.ios.kt`): `NSFileManager` + `NSData` で IO。`resolveAbsolutePath` は `NSDocumentDirectory` 配下。
- `SavedThreadsScreen`
  - `LaunchedEffect(Unit)` で一覧/サイズをロード。`AlertDialog` で削除確認。現状は未配線。
- Expect/actual components
  - `ImagePickerButton` & `rememberImagePickerLauncher`: Android は ActivityResultContracts.GetContent、iOS は PHPicker + suspend 呼び出し。
  - `PlatformVideoPlayer`: Android は Media3/ExoPlayer + `PlayerView` で WEBM/MP4 をサポートし、バッファリング/エラー状態を callback へ通知。iOS は MP4 を `AVPlayerViewController`、WEBM は `WKWebView` ベースの簡易プレーヤーで描画し、双方とも状態を Compose 側に返します。
  - `UrlLauncher`: Android Intent / iOS UIApplication。
  - `PermissionRequest`: Android ではストレージ権限 (API < 33) を要求、iOS はアプリの保存先 (Documents/futacha) を案内するダイアログを表示したうえで即 true。
  - `PlatformBackHandler`: Android Compose BackHandler / iOS no-op。
- `LocalFutachaImageLoader`: Coil3 ImageLoader を再利用し、`Dispatchers.IO.limitedParallelism(3)` でフェッチ/デコードを制限。32MB のメモリキャッシュと `futacha_image_cache`（最大 128MB、okio + DiskCache）を作ってカタログのサムネ描画を安定化させている。

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
│   ├── util/ FileSystem expect, ImagePicker expect, Logger expect, UrlLauncher expect, BoardConfig, TextEncoding expect
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

1. **SavedThreadsScreen 導線**: UI は存在するが遷移が無いため、Board/Catalog/Thread のどこかに入口を追加する必要があります。
2. **動画保存**: `ThreadSaveService` は画像／動画を両方処理し、`videos/` 配下へ媒体別に保存、HTML 内 `<video>`/`<source>` もローカルパスへ差し替えるようになったので保存後の HTML が動画を再生できます。
3. **Board ピン & 並び替え UX**: ピン状態の編集 UI・ドラッグ＆ドロップ並び替えが未対応で、上下ボタンしかありません。
4. **Catalog レイアウト切替**: グリッド固定。リスト表示や列数変更、モードごとのフィルタ UI などが TODO。
5. **エラーハンドリング**: `createThread` / `reply` / `del` / `deleteByUser` は HTML 応答の詳細を解析しておらず、snackbar で汎用メッセージを出すだけ。
6. **テスト不足**: Repository/Service 層や `ThreadSaveService` のユニットテストがないため、MockEngine/ファイルシステムのフェイクが必要です。

---

このドキュメントは `README.md` / `codex.md` と合わせて参照することで、モジュール構成・UI フロー・ネットワーク層・保存処理の全体像を把握できるようになっています。追加の質問があれば Issues でどうぞ。***
